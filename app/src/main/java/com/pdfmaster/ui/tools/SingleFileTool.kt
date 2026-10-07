package com.pdfmaster.ui.tools

import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pdfmaster.R
import com.pdfmaster.billing.ToolId
import com.pdfmaster.core.PageRange
import com.pdfmaster.core.formatBytes
import com.pdfmaster.pdf.CompressPreset
import com.pdfmaster.pdf.NumberPosition
import com.pdfmaster.pdf.OcrPageResult
import com.pdfmaster.pdf.PageRenderer
import com.pdfmaster.pdf.Permissions
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.common.PdfInputCard
import com.pdfmaster.ui.common.PdfInputState
import com.pdfmaster.ui.common.SectionLabel
import com.pdfmaster.ui.common.rememberPdfInput
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SingleFileTool(tool: ToolId, path: String?) {
    val input = rememberPdfInput(path)
    val notProtected = stringResource(R.string.not_protected)
    when (tool) {
        ToolId.COMPRESS -> CompressTool(input, grayscaleOnly = false)
        ToolId.GRAYSCALE -> CompressTool(input, grayscaleOnly = true)
        ToolId.PROTECT -> ProtectTool(input)
        ToolId.UNLOCK -> SimpleRunTool(tool, input, R.string.action_unlock) { container, file, pw, _ ->
            if (pw == null) throw IllegalStateException(notProtected)
            val out = container.documents.newOutputFile(file.nameWithoutExtension + " (unlocked)")
            container.pdfOps.unlock(file, out, pw)
            listOf(out)
        }
        ToolId.REMOVE_HIDDEN_DATA -> SimpleRunTool(tool, input, R.string.action_clean) { container, file, pw, _ ->
            val out = container.documents.newOutputFile(file.nameWithoutExtension + " (clean)")
            container.pdfOps.removeHiddenData(file, out, pw)
            listOf(out)
        }
        ToolId.PDF_TO_TEXT -> SimpleRunTool(tool, input, R.string.action_convert) { container, file, pw, _ ->
            val out = container.documents.cacheFile(file.nameWithoutExtension + ".txt")
            container.pdfOps.extractText(file, out, pw)
            listOf(out)
        }
        ToolId.MAKE_SEARCHABLE -> SimpleRunTool(tool, input, R.string.action_recognise) { container, file, pw, progress ->
            makeSearchable(container, file, pw, progress)
        }
        ToolId.WATERMARK -> WatermarkTool(input)
        ToolId.PAGE_NUMBERS -> PageNumbersTool(input)
        ToolId.PDF_TO_IMAGES -> PdfToImagesTool(input)
        else -> Text(stringResource(R.string.coming_soon_body, tool.phase), Modifier.padding(24.dp))
    }
}

@Composable
private fun SimpleRunTool(
    tool: ToolId,
    input: PdfInputState,
    actionLabel: Int,
    block: suspend (com.pdfmaster.AppContainer, File, String?, (Float) -> Unit) -> List<File>,
) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val state = rememberRunState()
    val err = errorMessage()
    ToolLayout(tool, state, stringResource(actionLabel), input.ready, onAction = {
        val file = input.file ?: return@ToolLayout
        scope.runTool(state, container, tool, err) { progress -> block(container, file, input.password, progress) }
    }) {
        PdfInputCard(input)
    }
}

/** OCRs pages that have no text yet and adds an invisible text layer. */
private suspend fun makeSearchable(container: com.pdfmaster.AppContainer, file: File, password: String?, progress: (Float) -> Unit): List<File> {
    val source = if (password != null) container.documents.cacheFile("ocr_src.pdf").also { container.pdfOps.decryptCopy(file, it, password) } else file
    val textless = container.pdfOps.open(source).use { doc ->
        (0 until doc.numberOfPages).filter { container.pdfOps.pageText(doc, it).isBlank() }
    }
    val results = mutableMapOf<Int, OcrPageResult>()
    PageRenderer(source).use { renderer ->
        textless.forEachIndexed { i, page ->
            val bmp = renderer.render(page, 1800) ?: return@forEachIndexed
            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
            results[page] = OcrPageResult(container.ocr.recognize(copy), copy.width, copy.height)
            copy.recycle()
            progress((i + 1f) / textless.size)
        }
    }
    val out = container.documents.newOutputFile(file.nameWithoutExtension + " (searchable)")
    container.pdfOps.addOcrLayer(source, out, results)
    if (source != file) source.delete()
    return listOf(out)
}

// ------------------------------------------------------------------ compress

@Composable
private fun CompressTool(input: PdfInputState, grayscaleOnly: Boolean) {
    val tool = if (grayscaleOnly) ToolId.GRAYSCALE else ToolId.COMPRESS
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val state = rememberRunState()
    val err = errorMessage()
    var preset by remember { mutableStateOf(CompressPreset.BALANCED) }
    var useTarget by remember { mutableStateOf(false) }
    var targetMb by remember { mutableFloatStateOf(3f) }
    var grayscale by remember { mutableStateOf(grayscaleOnly) }
    val savedText = stringResource(R.string.compress_result)

    ToolLayout(tool, state, stringResource(if (grayscaleOnly) R.string.action_convert else R.string.compress), input.ready, onAction = {
        val file = input.file ?: return@ToolLayout
        val before = file.length()
        scope.runTool(state, container, tool, err) {
            val out = container.documents.newOutputFile(file.nameWithoutExtension + if (grayscaleOnly) " (greyscale)" else " (compressed)")
            when {
                grayscaleOnly -> container.pdfOps.compress(file, out, CompressPreset.LIGHT, true, input.password)
                useTarget -> container.pdfOps.compressToTarget(file, out, (targetMb * 1024 * 1024).toLong(), grayscale, input.password)
                else -> container.pdfOps.compress(file, out, preset, grayscale, input.password)
            }
            val after = out.length()
            val pct = if (before > 0) ((1 - after.toDouble() / before) * 100).roundToInt() else 0
            state.summary = String.format(savedText, formatBytes(before), formatBytes(after), pct)
            listOf(out)
        }
    }) {
        PdfInputCard(input)
        if (grayscaleOnly) {
            Text(stringResource(R.string.grayscale_note), style = MaterialTheme.typography.bodySmall)
            return@ToolLayout
        }
        SectionLabel(stringResource(R.string.compression_level))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!useTarget, { useTarget = false }, label = { Text(stringResource(R.string.presets)) })
            FilterChip(useTarget, { useTarget = true }, label = { Text(stringResource(R.string.target_size)) })
        }
        if (!useTarget) {
            CompressPreset.entries.forEach { p ->
                val (title, desc) = when (p) {
                    CompressPreset.LIGHT -> R.string.preset_light to R.string.preset_light_desc
                    CompressPreset.BALANCED -> R.string.preset_balanced to R.string.preset_balanced_desc
                    CompressPreset.STRONG -> R.string.preset_strong to R.string.preset_strong_desc
                    CompressPreset.EXTREME -> R.string.preset_extreme to R.string.preset_extreme_desc
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = preset == p, onClick = { preset = p })
                    Column {
                        Text(stringResource(title))
                        Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            val current = input.file?.length() ?: 0L
            Text(stringResource(R.string.target_size_value, formatBytes((targetMb * 1024 * 1024).toLong()), formatBytes(current)))
            Slider(value = targetMb, onValueChange = { targetMb = it }, valueRange = 0.5f..25f)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(grayscale, { grayscale = it })
            Text(stringResource(R.string.also_grayscale))
        }
    }
}

// ------------------------------------------------------------------ protect

@Composable
private fun ProtectTool(input: PdfInputState) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val state = rememberRunState()
    val err = errorMessage()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var print by remember { mutableStateOf(true) }
    var copy by remember { mutableStateOf(false) }
    var modify by remember { mutableStateOf(false) }
    val valid = input.ready && password.length >= 4 && password == confirm

    ToolLayout(ToolId.PROTECT, state, stringResource(R.string.action_protect), valid, onAction = {
        val file = input.file ?: return@ToolLayout
        scope.runTool(state, container, ToolId.PROTECT, err) {
            val out = container.documents.newOutputFile(file.nameWithoutExtension + " (protected)")
            container.pdfOps.protect(file, out, password, owner, Permissions(print, copy, modify), input.password)
            listOf(out)
        }
    }) {
        PdfInputCard(input)
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.password)) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text(stringResource(R.string.password_min)) })
        OutlinedTextField(confirm, { confirm = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.confirm_password)) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), isError = confirm.isNotEmpty() && confirm != password,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        OutlinedTextField(owner, { owner = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.owner_password)) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), supportingText = { Text(stringResource(R.string.owner_password_help)) })
        SectionLabel(stringResource(R.string.permissions))
        SwitchRow(stringResource(R.string.allow_print), print) { print = it }
        SwitchRow(stringResource(R.string.allow_copy), copy) { copy = it }
        SwitchRow(stringResource(R.string.allow_modify), modify) { modify = it }
        Text(stringResource(R.string.aes_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

// ------------------------------------------------------------------ watermark

@Composable
private fun WatermarkTool(input: PdfInputState) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val state = rememberRunState()
    val err = errorMessage()
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var opacity by remember { mutableFloatStateOf(0.25f) }
    var angle by remember { mutableFloatStateOf(45f) }
    var size by remember { mutableFloatStateOf(60f) }
    var color by remember { mutableIntStateOf(0x9E9E9E) }
    var pages by remember { mutableStateOf("") }
    val pageError = remember(pages, input.info) {
        if (pages.isBlank()) null else runCatching { PageRange.parse(pages, input.info?.pageCount ?: 1) }.exceptionOrNull()?.message
    }

    ToolLayout(ToolId.WATERMARK, state, stringResource(R.string.action_apply), input.ready && text.isNotBlank() && pageError == null, onAction = {
        val file = input.file ?: return@ToolLayout
        val count = input.info?.pageCount ?: 0
        scope.runTool(state, container, ToolId.WATERMARK, err) {
            val out = container.documents.newOutputFile(file.nameWithoutExtension + " (watermarked)")
            val range = if (pages.isBlank()) null else PageRange.parse(pages, count)
            container.pdfOps.watermark(file, out, text, opacity, angle, size, color, range, input.password)
            listOf(out)
        }
    }) {
        PdfInputCard(input)
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.watermark_text)) }, singleLine = true)
        Text(stringResource(R.string.opacity_value, (opacity * 100).roundToInt()))
        Slider(opacity, { opacity = it }, valueRange = 0.05f..1f)
        Text(stringResource(R.string.font_size_value, size.roundToInt()))
        Slider(size, { size = it }, valueRange = 12f..140f)
        SectionLabel(stringResource(R.string.angle))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0f, 45f, -45f, 90f).forEach { a -> FilterChip(angle == a, { angle = a }, label = { Text("${a.roundToInt()}°") }) }
        }
        SectionLabel(stringResource(R.string.colour))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0x9E9E9E to R.string.grey, 0xD32F2F to R.string.red, 0x1565C0 to R.string.blue).forEach { (c, label) ->
                FilterChip(color == c, { color = c }, label = { Text(stringResource(label)) })
            }
        }
        PagesField(pages, { pages = it }, pageError)
    }
}

@Composable
private fun PagesField(value: String, onChange: (String) -> Unit, error: String?) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.pages_optional)) },
        placeholder = { Text("1-3, 5, 8-") },
        singleLine = true,
        isError = error != null,
        supportingText = { Text(error ?: stringResource(R.string.pages_all_hint)) },
    )
}

// ------------------------------------------------------------------ page numbers

@Composable
private fun PageNumbersTool(input: PdfInputState) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val state = rememberRunState()
    val err = errorMessage()
    val templates = listOf("{n}", "Page {n}", "Page {n} of {total}", "{n} / {total}")
    var template by remember { mutableStateOf(templates[2]) }
    var position by remember { mutableStateOf(NumberPosition.BOTTOM_CENTER) }
    var start by remember { mutableStateOf("1") }

    ToolLayout(ToolId.PAGE_NUMBERS, state, stringResource(R.string.action_apply), input.ready && start.toIntOrNull() != null, onAction = {
        val file = input.file ?: return@ToolLayout
        scope.runTool(state, container, ToolId.PAGE_NUMBERS, err) {
            val out = container.documents.newOutputFile(file.nameWithoutExtension + " (numbered)")
            container.pdfOps.pageNumbers(file, out, template, position, start.toInt(), 10f, input.password)
            listOf(out)
        }
    }) {
        PdfInputCard(input)
        SectionLabel(stringResource(R.string.format))
        templates.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.RadioButton(template == t, { template = t })
                Text(t.replace("{n}", "3").replace("{total}", "12"))
            }
        }
        SectionLabel(stringResource(R.string.position))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                NumberPosition.BOTTOM_LEFT to R.string.pos_bottom_left,
                NumberPosition.BOTTOM_CENTER to R.string.pos_bottom_center,
                NumberPosition.BOTTOM_RIGHT to R.string.pos_bottom_right,
                NumberPosition.TOP_RIGHT to R.string.pos_top_right,
            ).forEach { (p, label) -> FilterChip(position == p, { position = p }, label = { Text(stringResource(label)) }) }
        }
        OutlinedTextField(start, { start = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.start_at)) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

// ------------------------------------------------------------------ PDF -> images

@Composable
private fun PdfToImagesTool(input: PdfInputState) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberRunState()
    val err = errorMessage()
    var dpi by remember { mutableIntStateOf(150) }
    var png by remember { mutableStateOf(false) }
    var pages by remember { mutableStateOf("") }
    val pageError = remember(pages, input.info) {
        if (pages.isBlank()) null else runCatching { PageRange.parse(pages, input.info?.pageCount ?: 1) }.exceptionOrNull()?.message
    }
    val doneText = stringResource(R.string.images_saved)

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        val file = input.file
        if (tree == null || file == null) return@rememberLauncherForActivityResult
        scope.runTool(state, container, ToolId.PDF_TO_IMAGES, err) { progress ->
            val source = if (input.password != null) container.documents.cacheFile("img_src.pdf").also { container.pdfOps.decryptCopy(file, it, input.password!!) } else file
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            var written = 0
            PageRenderer(source).use { renderer ->
                val indices = if (pages.isBlank()) (0 until renderer.pageCount).toList() else PageRange.parse(pages, renderer.pageCount)
                indices.forEachIndexed { i, page ->
                    val pts = renderer.pageSize(page)
                    val bmp = renderer.render(page, (pts[0] * dpi / 72f).roundToInt()) ?: return@forEachIndexed
                    val ext = if (png) "png" else "jpg"
                    val name = "${file.nameWithoutExtension}-${page + 1}.$ext"
                    val uri = DocumentsContract.createDocument(context.contentResolver, parent, if (png) "image/png" else "image/jpeg", name)
                    if (uri != null) context.contentResolver.openOutputStream(uri)?.use { out ->
                        bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, out)
                        written++
                    }
                    progress((i + 1f) / indices.size)
                }
            }
            if (source != file) source.delete()
            state.summary = String.format(doneText, written)
            emptyList()
        }
    }

    ToolLayout(ToolId.PDF_TO_IMAGES, state, stringResource(R.string.choose_folder_and_export), input.ready && pageError == null, onAction = { folderLauncher.launch(null) }) {
        PdfInputCard(input)
        SectionLabel(stringResource(R.string.resolution))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(72, 150, 300).forEach { d -> FilterChip(dpi == d, { dpi = d }, label = { Text("$d DPI") }) }
        }
        SectionLabel(stringResource(R.string.format))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!png, { png = false }, label = { Text("JPG") })
            FilterChip(png, { png = true }, label = { Text("PNG") })
        }
        PagesField(pages, { pages = it }, pageError)
        state.summary?.takeIf { state.results.isEmpty() && !state.running }?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
