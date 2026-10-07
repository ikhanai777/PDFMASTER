package com.pdfmaster.ui.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.AppContainer
import com.pdfmaster.R
import com.pdfmaster.billing.ToolId
import com.pdfmaster.ocr.AutoNamer
import com.pdfmaster.pdf.ImagePageOptions
import com.pdfmaster.pdf.OcrLine
import com.pdfmaster.pdf.PageSizeOption
import com.pdfmaster.ui.LocalActions
import com.pdfmaster.ui.common.BusyDialog
import com.pdfmaster.ui.common.ErrorDialog
import com.pdfmaster.ui.common.ScreenScaffold
import com.pdfmaster.ui.common.SectionLabel
import com.pdfmaster.ui.common.appViewModel
import com.pdfmaster.ui.tools.decodeScaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.io.File

enum class ScanFilter { ORIGINAL, MAGIC, GREY, BLACK_WHITE, TEXT }

data class ReviewPage(val uid: Long, val uri: Uri, val rotation: Int = 0)

class PagesReviewViewModel(private val app: android.app.Application, private val container: AppContainer) : ViewModel() {
    var pages by mutableStateOf(container.pendingScan.value.mapIndexed { i, uri -> ReviewPage(i.toLong(), uri) }); private set
    var filter by mutableStateOf(ScanFilter.ORIGINAL)
    var ocr by mutableStateOf(true)
    var pageSize by mutableStateOf(PageSizeOption.FIT)
    var name by mutableStateOf("")
    private var nameEdited = false
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf<Float?>(null); private set
    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<File?>(null)

    init {
        container.pendingScan.value = emptyList()
        name = AutoNamer.name(emptyList(), 0)
        // Suggest a name from the first page's text while the user reviews.
        pages.firstOrNull()?.let { first ->
            viewModelScope.launch {
                val suggestion = withContext(Dispatchers.Default) {
                    runCatching {
                        val bmp = decodeScaled(app, first.uri, 1600)
                        AutoNamer.name(container.ocr.recognize(bmp), bmp.height).also { bmp.recycle() }
                    }.getOrNull()
                }
                if (suggestion != null && !nameEdited) name = suggestion
            }
        }
    }

    fun rename(value: String) { name = value; nameEdited = true }
    fun move(from: Int, to: Int) { pages = pages.toMutableList().apply { add(to, removeAt(from)) } }
    fun remove(uid: Long) { pages = pages.filterNot { it.uid == uid } }
    fun rotate(uid: Long) { pages = pages.map { if (it.uid == uid) it.copy(rotation = (it.rotation + 90) % 360) else it } }

    fun save() {
        val snapshot = pages
        if (snapshot.isEmpty()) return
        viewModelScope.launch {
            busy = true
            progress = 0f
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    // Prepare each page once (rotate + filter), OCR it, and keep it on disk to bound memory.
                    val prepared = mutableListOf<File>()
                    val ocrLines = mutableListOf<List<OcrLine>>()
                    snapshot.forEachIndexed { i, page ->
                        val bmp = applyFilter(rotate(decodeScaled(app, page.uri, 2400), page.rotation), filter)
                        ocrLines += if (ocr) container.ocr.recognize(bmp) else emptyList()
                        val f = File(app.cacheDir, "scan_$i.jpg")
                        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        bmp.recycle()
                        prepared += f
                        progress = (i + 1f) / (snapshot.size + 1)
                    }
                    val out = container.documents.newOutputFile(name.ifBlank { "Scan" })
                    container.pdfOps.imagesToPdf(
                        prepared.map { f -> { android.graphics.BitmapFactory.decodeFile(f.absolutePath) } },
                        ImagePageOptions(pageSize, if (pageSize == PageSizeOption.FIT) 0f else 18f, 0.82f),
                        out,
                        if (ocr) ocrLines else null,
                    )
                    prepared.forEach { it.delete() }
                    container.documents.register(out)
                    out
                }
            }
            busy = false
            outcome.onSuccess {
                container.entitlements.recordCompletion(ToolId.SCAN)
                result = it
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
        }
    }

    private fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bmp
        val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
    }
}

fun filterMatrix(filter: ScanFilter): ColorMatrix? = when (filter) {
    ScanFilter.ORIGINAL, ScanFilter.BLACK_WHITE -> null
    ScanFilter.GREY -> ColorMatrix().apply { setSaturation(0f) }
    ScanFilter.MAGIC -> ColorMatrix(floatArrayOf(1.35f, 0f, 0f, 0f, -20f, 0f, 1.35f, 0f, 0f, -20f, 0f, 0f, 1.35f, 0f, -20f, 0f, 0f, 0f, 1f, 0f)).apply {
        postConcat(ColorMatrix().apply { setSaturation(1.25f) })
    }
    ScanFilter.TEXT -> ColorMatrix().apply {
        setSaturation(0f)
        postConcat(ColorMatrix(floatArrayOf(1.9f, 0f, 0f, 0f, -90f, 0f, 1.9f, 0f, 0f, -90f, 0f, 0f, 1.9f, 0f, -90f, 0f, 0f, 0f, 1f, 0f)))
    }
}

/** Applies a scan filter. Black & white uses Otsu's threshold for crisp text. */
fun applyFilter(src: Bitmap, filter: ScanFilter): Bitmap {
    if (filter == ScanFilter.ORIGINAL) return src
    if (filter == ScanFilter.BLACK_WHITE) return threshold(src)
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(filterMatrix(filter)!!) })
    src.recycle()
    return out
}

private fun threshold(src: Bitmap): Bitmap {
    val w = src.width; val h = src.height
    val row = IntArray(w)
    val hist = IntArray(256)
    for (y in 0 until h) {
        src.getPixels(row, 0, w, 0, y, w, 1)
        for (p in row) hist[luma(p)]++
    }
    val total = w.toLong() * h
    var sum = 0.0
    for (i in 0..255) sum += i * hist[i].toDouble()
    var sumB = 0.0; var wB = 0L; var best = 0.0; var t = 128
    for (i in 0..255) {
        wB += hist[i]; if (wB == 0L) continue
        val wF = total - wB; if (wF == 0L) break
        sumB += i * hist[i].toDouble()
        val mB = sumB / wB; val mF = (sum - sumB) / wF
        val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
        if (between > best) { best = between; t = i }
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    for (y in 0 until h) {
        src.getPixels(row, 0, w, 0, y, w, 1)
        for (x in 0 until w) row[x] = if (luma(row[x]) > t) -0x1 else -0x1000000
        out.setPixels(row, 0, w, 0, y, w, 1)
    }
    src.recycle()
    return out
}

private fun luma(p: Int): Int = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000

@Composable
fun PagesReviewScreen() {
    val app = LocalContext.current.applicationContext as android.app.Application
    val actions = LocalActions.current
    val vm = appViewModel(key = "review") { PagesReviewViewModel(app, it) }
    val gridState = rememberLazyGridState()
    val reorder = rememberReorderableLazyGridState(gridState) { from, to -> vm.move(from.index, to.index) }

    LaunchedEffect(vm.result) {
        val file = vm.result ?: return@LaunchedEffect
        vm.result = null
        actions.nav.popBackStack()
        actions.openViewer(file.absolutePath)
    }

    ScreenScaffold(title = stringResource(R.string.review_pages, vm.pages.size), onBack = actions::back) { modifier ->
        Column(modifier.fillMaxSize().navigationBarsPadding()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                state = gridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.pages, key = { it.uid }) { page ->
                    ReorderableItem(reorder, key = page.uid) { dragging ->
                        val thumb by produceState<Bitmap?>(null, page.uri) {
                            value = withContext(Dispatchers.IO) { runCatching { decodeScaled(app, page.uri, 360) }.getOrNull() }
                        }
                        Column(Modifier.longPressDraggableHandle(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .border(if (dragging) 3.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                            ) {
                                thumb?.let {
                                    Image(
                                        it.asImageBitmap(), null,
                                        Modifier.fillMaxSize().padding(4.dp).rotate(page.rotation.toFloat()),
                                        contentScale = ContentScale.Fit,
                                        colorFilter = filterMatrix(vm.filter)?.let { m -> ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(m.array)) }
                                            ?: if (vm.filter == ScanFilter.BLACK_WHITE) ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null,
                                    )
                                }
                                Row(Modifier.align(Alignment.TopEnd)) {
                                    IconButton(onClick = { vm.rotate(page.uid) }) { Icon(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.rotate_right)) }
                                    IconButton(onClick = { vm.remove(page.uid) }) { Icon(Icons.Default.Close, stringResource(R.string.remove)) }
                                }
                            }
                            Text("${vm.pages.indexOf(page) + 1}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionLabel(stringResource(R.string.filter))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ScanFilter.ORIGINAL to R.string.filter_original,
                        ScanFilter.MAGIC to R.string.filter_magic,
                        ScanFilter.GREY to R.string.filter_grey,
                        ScanFilter.BLACK_WHITE to R.string.filter_bw,
                        ScanFilter.TEXT to R.string.filter_text,
                    ).forEach { (f, label) -> FilterChip(vm.filter == f, { vm.filter = f }, label = { Text(stringResource(label)) }) }
                }
                SectionLabel(stringResource(R.string.page_size))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(vm.pageSize == PageSizeOption.FIT, { vm.pageSize = PageSizeOption.FIT }, label = { Text(stringResource(R.string.size_fit)) })
                    FilterChip(vm.pageSize == PageSizeOption.A4, { vm.pageSize = PageSizeOption.A4 }, label = { Text("A4") })
                    FilterChip(vm.pageSize == PageSizeOption.LETTER, { vm.pageSize = PageSizeOption.LETTER }, label = { Text(stringResource(R.string.size_letter)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.ocr = !vm.ocr }) {
                    Checkbox(vm.ocr, { vm.ocr = it })
                    Text(stringResource(R.string.make_searchable_ocr))
                }
                OutlinedTextField(vm.name, vm::rename, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.file_name)) }, singleLine = true)
                Button(
                    onClick = vm::save,
                    enabled = vm.pages.isNotEmpty() && !vm.busy,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
                ) { Text(stringResource(R.string.save_pdf)) }
            }
        }
    }
    if (vm.busy) BusyDialog(stringResource(if (vm.ocr) R.string.recognising_text else R.string.working), vm.progress)
    vm.error?.let { ErrorDialog(it) { vm.error = null } }
}
