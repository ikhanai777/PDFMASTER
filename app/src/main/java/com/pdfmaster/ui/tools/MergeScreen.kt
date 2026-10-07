package com.pdfmaster.ui.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pdfmaster.R
import com.pdfmaster.billing.ToolId
import com.pdfmaster.pdf.ImagePageOptions
import com.pdfmaster.pdf.PageSizeOption
import com.pdfmaster.ui.LocalActions
import com.pdfmaster.ui.common.BusyDialog
import com.pdfmaster.ui.common.DocumentPickerSheet
import com.pdfmaster.ui.common.ErrorDialog
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.common.PdfThumbnail
import com.pdfmaster.ui.common.ResultCard
import com.pdfmaster.ui.common.ScreenScaffold
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

/** PDFs shared into the app in one go, waiting for the merge screen. */
object MergeInbox {
    var files: List<File> = emptyList()
}

private sealed interface MergeItem {
    val key: String
    data class Pdf(val file: File) : MergeItem { override val key get() = "pdf:" + file.absolutePath }
    data class Img(val uri: Uri, val name: String) : MergeItem { override val key get() = "img:$uri" }
}

@Composable
fun MergeScreen() {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberRunState()
    val err = errorMessage()
    var items by remember { mutableStateOf<List<MergeItem>>(MergeInbox.files.map { MergeItem.Pdf(it) }.also { MergeInbox.files = emptyList() }) }
    var picking by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Merged") }
    val lockedText = stringResource(R.string.merge_locked)

    val multiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            val files = uris.mapNotNull { runCatching { container.documents.importPdf(it) }.getOrNull() }
            items = items + files.map { MergeItem.Pdf(it) }
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        items = items + uris.map { MergeItem.Img(it, container.documents.displayName(it) ?: "image") }
    }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    ScreenScaffold(title = toolTitle(ToolId.MERGE), onBack = actions::back) { modifier ->
        Column(modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.merge_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.add_from_library)) }
                OutlinedButton(onClick = { multiLauncher.launch(arrayOf("application/pdf")) }) { Text(stringResource(R.string.add_pdfs)) }
                OutlinedButton(onClick = { imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.add_images)) }
            }
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(items, key = { it.key }) { item ->
                    ReorderableItem(reorder, key = item.key) { dragging ->
                        Surface(tonalElevation = if (dragging) 8.dp else 0.dp) {
                            ListItem(
                                leadingContent = {
                                    when (item) {
                                        is MergeItem.Pdf -> PdfThumbnail(item.file, Modifier.size(width = 36.dp, height = 48.dp), widthPx = 100)
                                        is MergeItem.Img -> Icon(Icons.Default.Image, null)
                                    }
                                },
                                headlineContent = { Text(if (item is MergeItem.Pdf) item.file.nameWithoutExtension else (item as MergeItem.Img).name, maxLines = 1) },
                                supportingContent = { Icon(if (item is MergeItem.Pdf) Icons.Default.PictureAsPdf else Icons.Default.Image, null, Modifier.size(14.dp)) },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { items = items - item }) { Icon(Icons.Default.Close, stringResource(R.string.remove)) }
                                        IconButton(modifier = Modifier.draggableHandle(), onClick = {}) { Icon(Icons.Default.DragHandle, stringResource(R.string.drag_to_reorder)) }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.file_name)) }, singleLine = true)
            Button(
                enabled = items.size >= 2 && name.isNotBlank() && !state.running,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
                onClick = {
                    scope.runTool(state, container, ToolId.MERGE, err) { progress ->
                        val temps = mutableListOf<File>()
                        try {
                            val inputs = items.mapIndexed { i, item ->
                                progress(i.toFloat() / items.size)
                                when (item) {
                                    is MergeItem.Pdf -> {
                                        if (container.pdfOps.inspect(item.file).needsPassword) throw IllegalStateException(String.format(lockedText, item.file.nameWithoutExtension))
                                        item.file
                                    }
                                    is MergeItem.Img -> container.documents.cacheFile("merge_img_$i.pdf").also { out ->
                                        temps += out
                                        container.pdfOps.imagesToPdf(listOf { decodeScaled(context, item.uri) }, ImagePageOptions(PageSizeOption.A4, 24f), out)
                                    }
                                }
                            }
                            val out = container.documents.newOutputFile(name)
                            container.pdfOps.merge(inputs, out)
                            listOf(out)
                        } finally {
                            temps.forEach { it.delete() }
                        }
                    }
                },
            ) { Text(stringResource(R.string.merge_n_files, items.size)) }
            if (state.results.isNotEmpty()) ResultCard(state.results, onOpen = { actions.openViewer(it.absolutePath) })
        }
    }

    if (picking) DocumentPickerSheet(onPicked = { items = items + MergeItem.Pdf(it); picking = false }, onDismiss = { picking = false })
    if (state.running) BusyDialog(stringResource(R.string.working), state.progress)
    state.error?.let { ErrorDialog(it) { state.error = null } }
}

/** Decodes an image no larger than [maxDim] on its longest side, honouring EXIF rotation. */
fun decodeScaled(context: android.content.Context, uri: Uri, maxDim: Int = 2400): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: throw IllegalArgumentException("Cannot decode image")
    val rotation = resolver.openInputStream(uri)?.use { stream ->
        when (androidx.exifinterface.media.ExifInterface(stream).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val scale = minOf(1f, maxDim.toFloat() / maxOf(decoded.width, decoded.height))
    if (rotation == 0f && scale == 1f) return decoded
    val m = android.graphics.Matrix().apply { postScale(scale, scale); postRotate(rotation) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also { if (it !== decoded) decoded.recycle() }
}
