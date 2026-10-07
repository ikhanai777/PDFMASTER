package com.pdfmaster.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfmaster.AppContainer
import com.pdfmaster.R
import com.pdfmaster.core.formatBytes
import com.pdfmaster.core.formatDate
import com.pdfmaster.data.db.DocumentEntity
import com.pdfmaster.pdf.PageRenderer
import com.pdfmaster.pdf.PdfInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline factory: (AppContainer) -> VM): VM {
    val container = LocalContainer.current
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = factory(container) as T
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = actions,
            )
        },
        bottomBar = bottomBar,
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WorkspacePremium, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(2.dp))
            Text(stringResource(R.string.pro), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
fun BusyDialog(message: String, progress: Float? = null) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(message) },
        text = {
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        },
    )
}

@Composable
fun PasswordDialog(fileName: String, wrongPassword: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text(stringResource(R.string.password_required)) },
        text = {
            Column {
                Text(stringResource(R.string.password_required_body, fileName))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.password)) },
                    isError = wrongPassword,
                    supportingText = { if (wrongPassword) Text(stringResource(R.string.wrong_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text(stringResource(R.string.open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.something_went_wrong)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

// ------------------------------------------------------------------ thumbnails

object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun get(file: File, widthPx: Int, page: Int = 0): Bitmap? {
        val key = "${file.absolutePath}:${file.lastModified()}:$page:$widthPx"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                PageRenderer(file).use { r -> if (page < r.pageCount) r.render(page, widthPx)?.copy(Bitmap.Config.RGB_565, false) else null }
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }
}

@Composable
fun PdfThumbnail(file: File, modifier: Modifier = Modifier, page: Int = 0, widthPx: Int = 240) {
    val bitmap by produceState<Bitmap?>(null, file.absolutePath, file.lastModified(), page) {
        value = ThumbnailCache.get(file, widthPx, page)
    }
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize().padding(1.dp), contentScale = ContentScale.Fit)
        } else {
            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DocumentRow(doc: DocumentEntity, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { PdfThumbnail(File(doc.path), Modifier.size(width = 40.dp, height = 52.dp), widthPx = 120) },
        headlineContent = { Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val pages = if (doc.encrypted) stringResource(R.string.locked) else LocalContext.current.resources.getQuantityString(R.plurals.pages, doc.pageCount, doc.pageCount)
            Text("$pages · ${formatBytes(doc.sizeBytes)} · ${formatDate(doc.modifiedAt)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = trailing,
    )
}

// ------------------------------------------------------------------ picking files

/**
 * A bottom sheet to pick a PDF from the library or from anywhere on the device (copied in,
 * so the original is never touched).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPickerSheet(onPicked: (File) -> Unit, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val docs by container.documents.documents.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { container.documents.importPdf(uri) }
                .onSuccess(onPicked)
                .onFailure { Toast.makeText(context, R.string.import_failed, Toast.LENGTH_LONG).show() }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.choose_pdf), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { launcher.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.browse_device))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.from_library), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp))
        }
        LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
            if (docs.isEmpty()) item {
                Text(stringResource(R.string.library_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(docs, key = { it.path }) { doc -> DocumentRow(doc, onClick = { onPicked(File(doc.path)) }) }
        }
    }
}

/** State for a tool that works on one PDF, including its password if it has one. */
class PdfInputState {
    var file by mutableStateOf<File?>(null)
    var info by mutableStateOf<PdfInfo?>(null)
    var password by mutableStateOf<String?>(null)
    val ready: Boolean get() = file != null && info != null && (info?.needsPassword == false)
}

@Composable
fun rememberPdfInput(initialPath: String?): PdfInputState {
    val state = remember { PdfInputState() }
    val container = LocalContainer.current
    LaunchedEffect(initialPath) {
        if (initialPath != null && state.file == null) {
            val f = File(initialPath)
            state.file = f
            state.info = withContext(Dispatchers.IO) { runCatching { container.pdfOps.inspect(f) }.getOrNull() }
        }
    }
    return state
}

/** Card showing the chosen PDF (or a button to choose one) and handling passwords. */
@Composable
fun PdfInputCard(state: PdfInputState, modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var wrongPassword by remember { mutableStateOf(false) }
    val file = state.file

    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (file != null) {
                PdfThumbnail(file, Modifier.size(width = 48.dp, height = 64.dp), widthPx = 140)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.nameWithoutExtension, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val info = state.info
                    val detail = when {
                        info == null -> stringResource(R.string.reading)
                        info.needsPassword -> stringResource(R.string.locked)
                        else -> LocalContext.current.resources.getQuantityString(R.plurals.pages, info.pageCount, info.pageCount)
                    }
                    Text("$detail · ${formatBytes(file.length())}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { picking = true }) { Text(stringResource(R.string.change)) }
            } else {
                Text(stringResource(R.string.no_file_selected), Modifier.weight(1f))
                FilledTonalButton(onClick = { picking = true }) { Text(stringResource(R.string.choose_pdf)) }
            }
        }
    }

    if (picking) DocumentPickerSheet(
        onPicked = { picked ->
            picking = false
            state.file = picked
            state.info = null
            state.password = null
            scope.launch { state.info = withContext(Dispatchers.IO) { runCatching { container.pdfOps.inspect(picked) }.getOrNull() } }
        },
        onDismiss = { picking = false },
    )

    val info = state.info
    if (file != null && info != null && info.needsPassword) {
        PasswordDialog(
            fileName = file.nameWithoutExtension,
            wrongPassword = wrongPassword,
            onSubmit = { pw ->
                scope.launch {
                    val unlocked = withContext(Dispatchers.IO) { runCatching { container.pdfOps.inspect(file, pw) }.getOrNull() }
                    if (unlocked != null && !unlocked.needsPassword) {
                        wrongPassword = false
                        state.password = pw
                        state.info = unlocked
                    } else wrongPassword = true
                }
            },
            onDismiss = { state.file = null; state.info = null },
        )
    }
}

// ------------------------------------------------------------------ results

fun shareFile(context: Context, uri: Uri, name: String) {
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "pdf")) ?: "application/pdf"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, name))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_to_share, Toast.LENGTH_SHORT).show()
    }
}

/** Shown when a tool finishes: what was produced and what to do with it next. */
@Composable
fun ResultCard(files: List<File>, onOpen: ((File) -> Unit)?, extra: String? = null) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf<File?>(null) }
    val mime = if (exporting?.extension == "txt") "text/plain" else "application/pdf"
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val file = exporting
        if (uri != null && file != null) scope.launch {
            runCatching { container.documents.exportTo(file, uri) }
                .onSuccess { Toast.makeText(context, R.string.saved_to_device, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, R.string.save_failed, Toast.LENGTH_LONG).show() }
        }
        exporting = null
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.done_title), style = MaterialTheme.typography.titleMedium)
            extra?.let { Text(it) }
            files.forEach { file ->
                Text("${file.name} · ${formatBytes(file.length())}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onOpen != null && file.extension == "pdf") FilledTonalButton(onClick = { onOpen(file) }) {
                        Icon(Icons.Default.Visibility, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.open))
                    }
                    OutlinedButton(onClick = { shareFile(context, container.documents.shareUri(file), file.name) }) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.share))
                    }
                    OutlinedButton(onClick = { exporting = file; saveLauncher.launch(file.name) }) {
                        Icon(Icons.Default.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
