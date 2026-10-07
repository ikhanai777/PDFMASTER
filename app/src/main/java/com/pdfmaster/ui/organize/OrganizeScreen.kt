package com.pdfmaster.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.AppContainer
import com.pdfmaster.R
import com.pdfmaster.billing.ToolId
import com.pdfmaster.pdf.PageSpec
import com.pdfmaster.ui.LocalActions
import com.pdfmaster.ui.common.BusyDialog
import com.pdfmaster.ui.common.DocumentPickerSheet
import com.pdfmaster.ui.common.ErrorDialog
import com.pdfmaster.ui.common.PdfInputCard
import com.pdfmaster.ui.common.PdfThumbnail
import com.pdfmaster.ui.common.ScreenScaffold
import com.pdfmaster.ui.common.appViewModel
import com.pdfmaster.ui.common.rememberPdfInput
import com.pdfmaster.ui.tools.toolTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.io.File

data class PageItem(val uid: Long, val spec: PageSpec)

class OrganizeViewModel(private val container: AppContainer) : ViewModel() {
    var source by mutableStateOf<File?>(null); private set
    private var password: String? = null
    private val passwords = mutableMapOf<File, String>()
    var pages by mutableStateOf<List<PageItem>>(emptyList()); private set
    var selected by mutableStateOf<Set<Long>>(emptySet())
    private val undo = ArrayDeque<List<PageItem>>()
    private val redo = ArrayDeque<List<PageItem>>()
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    var dirty by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var saved by mutableStateOf<File?>(null)
    private var nextUid = 1L

    fun load(file: File, pw: String?, pageCount: Int) {
        if (source == file) return
        source = file
        password = pw
        pw?.let { passwords[file] = it }
        pages = (0 until pageCount).map { PageItem(nextUid++, PageSpec(file, it)) }
        undo.clear(); redo.clear(); selected = emptySet(); dirty = false; refresh()
    }

    private fun edit(newPages: List<PageItem>) {
        undo.addLast(pages); redo.clear()
        pages = newPages
        dirty = true
        refresh()
    }

    private fun refresh() { canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty() }

    fun undo() { undo.removeLastOrNull()?.let { redo.addLast(pages); pages = it; refresh() } }
    fun redo() { redo.removeLastOrNull()?.let { undo.addLast(pages); pages = it; refresh() } }

    fun move(from: Int, to: Int) {
        if (from == to) return
        edit(pages.toMutableList().apply { add(to, removeAt(from)) })
    }

    private fun targets() = if (selected.isEmpty()) pages.map { it.uid }.toSet() else selected

    fun rotate(delta: Int) {
        val t = targets()
        edit(pages.map { if (it.uid in t) it.copy(spec = it.spec.copy(extraRotation = (it.spec.extraRotation + delta + 360) % 360)) else it })
    }

    fun deleteSelected() {
        if (selected.isEmpty() || selected.size == pages.size) return
        edit(pages.filterNot { it.uid in selected }); selected = emptySet()
    }

    fun duplicateSelected() {
        if (selected.isEmpty()) return
        edit(pages.flatMap { if (it.uid in selected) listOf(it, it.copy(uid = nextUid++)) else listOf(it) })
    }

    fun insertBlank() {
        val at = pages.indexOfLast { it.uid in selected }.let { if (it < 0) pages.size else it + 1 }
        edit(pages.toMutableList().apply { add(at, PageItem(nextUid++, PageSpec(null, 0))) })
    }

    fun insertFrom(file: File) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { runCatching { container.pdfOps.inspect(file) }.getOrNull() }
            if (info == null || info.needsPassword) { error = "locked"; return@launch }
            val at = pages.indexOfLast { it.uid in selected }.let { if (it < 0) pages.size else it + 1 }
            edit(pages.toMutableList().apply { addAll(at, (0 until info.pageCount).map { PageItem(nextUid++, PageSpec(file, it)) }) })
        }
    }

    fun toggle(uid: Long) { selected = if (uid in selected) selected - uid else selected + uid }
    fun selectAll() { selected = if (selected.size == pages.size) emptySet() else pages.map { it.uid }.toSet() }

    /** Writes the current arrangement. [onlySelected] extracts the selected pages to a new file. */
    fun save(overwrite: Boolean, onlySelected: Boolean = false) {
        val src = source ?: return
        val specs = (if (onlySelected) pages.filter { it.uid in selected } else pages).map { it.spec }
        if (specs.isEmpty()) return
        viewModelScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (overwrite && password == null) {
                        val temp = container.documents.cacheFile("organize_${System.nanoTime()}.pdf")
                        container.pdfOps.buildFromPages(specs, temp, passwords)
                        container.documents.overwriteWithVersion(src, temp)
                        src
                    } else {
                        val suffix = if (onlySelected) " (extract)" else " (organised)"
                        val out = container.documents.newOutputFile(src.nameWithoutExtension + suffix)
                        container.pdfOps.buildFromPages(specs, out, passwords)
                        container.documents.register(out)
                        out
                    }
                }
            }
            busy = false
            result.onSuccess {
                if (!onlySelected) dirty = false
                saved = it
                container.entitlements.recordCompletion(ToolId.ORGANIZE_PAGES)
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
        }
    }
}

@Composable
fun OrganizeScreen(path: String?) {
    val actions = LocalActions.current
    val input = rememberPdfInput(path)
    val vm = appViewModel(key = "organize:$path") { OrganizeViewModel(it) }
    var askSave by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(input.file, input.info) {
        val f = input.file; val info = input.info
        if (f != null && info != null && !info.needsPassword) vm.load(f, input.password, info.pageCount)
    }
    LaunchedEffect(vm.saved) {
        vm.saved?.let { vm.saved = null; actions.openViewer(it.absolutePath) }
    }

    val gridState = rememberLazyGridState()
    val reorder = rememberReorderableLazyGridState(gridState) { from, to -> vm.move(from.index, to.index) }

    ScreenScaffold(
        title = toolTitle(ToolId.ORGANIZE_PAGES),
        onBack = actions::back,
        actions = {
            IconButton(enabled = vm.canUndo, onClick = vm::undo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo)) }
            IconButton(enabled = vm.canRedo, onClick = vm::redo) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo)) }
            IconButton(enabled = vm.dirty, onClick = { askSave = true }) { Icon(Icons.Default.Save, stringResource(R.string.save)) }
        },
        bottomBar = {
            if (vm.pages.isNotEmpty()) BottomAppBar {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    IconButton(onClick = vm::selectAll) { Icon(Icons.Default.SelectAll, stringResource(R.string.select_all)) }
                    IconButton(onClick = { vm.rotate(-90) }) { Icon(Icons.AutoMirrored.Filled.RotateLeft, stringResource(R.string.rotate_left)) }
                    IconButton(onClick = { vm.rotate(90) }) { Icon(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.rotate_right)) }
                    IconButton(enabled = vm.selected.isNotEmpty() && vm.selected.size < vm.pages.size, onClick = vm::deleteSelected) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                    IconButton(enabled = vm.selected.isNotEmpty(), onClick = vm::duplicateSelected) { Icon(Icons.Default.ContentCopy, stringResource(R.string.duplicate)) }
                    IconButton(onClick = vm::insertBlank) { Icon(Icons.AutoMirrored.Filled.NoteAdd, stringResource(R.string.insert_blank)) }
                    IconButton(onClick = { picking = true }) { Icon(Icons.Default.PostAdd, stringResource(R.string.insert_from_pdf)) }
                    IconButton(enabled = vm.selected.isNotEmpty(), onClick = { vm.save(overwrite = false, onlySelected = true) }) { Icon(Icons.Default.ContentCut, stringResource(R.string.extract_selected)) }
                }
            }
        },
    ) { modifier ->
        Column(modifier.fillMaxSize().navigationBarsPadding()) {
            if (vm.pages.isEmpty()) {
                Column(Modifier.padding(16.dp)) { PdfInputCard(input) }
            } else {
                Text(
                    if (vm.selected.isEmpty()) stringResource(R.string.organize_hint) else stringResource(R.string.n_selected, vm.selected.size),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(vm.pages, key = { it.uid }) { item ->
                        ReorderableItem(reorder, key = item.uid) { dragging ->
                            val index = vm.pages.indexOf(item)
                            val isSelected = item.uid in vm.selected
                            Column(
                                Modifier
                                    .longPressDraggableHandle()
                                    .clickable { vm.toggle(item.uid) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.72f)
                                        .border(
                                            if (isSelected || dragging) 3.dp else 1.dp,
                                            if (isSelected || dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(6.dp),
                                        )
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val src = item.spec.source
                                    if (src == null) Box(Modifier.fillMaxSize(0.9f).aspectRatio(0.707f).background(Color.White).border(1.dp, Color.LightGray))
                                    else PdfThumbnail(src, Modifier.fillMaxSize().rotate(item.spec.extraRotation.toFloat()), page = item.spec.sourceIndex, widthPx = 220)
                                    if (isSelected) Icon(Icons.Default.CheckCircle, null, Modifier.align(Alignment.TopEnd), tint = MaterialTheme.colorScheme.primary)
                                }
                                Text("${index + 1}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (picking) DocumentPickerSheet(onPicked = { picking = false; vm.insertFrom(it) }, onDismiss = { picking = false })
    if (vm.busy) BusyDialog(stringResource(R.string.working))
    vm.error?.let { ErrorDialog(it) { vm.error = null } }
    if (askSave) AlertDialog(
        onDismissRequest = { askSave = false },
        title = { Text(stringResource(R.string.save_changes)) },
        text = { Text(stringResource(if (input.password != null) R.string.save_encrypted_note else R.string.save_choice_body)) },
        confirmButton = { TextButton(onClick = { askSave = false; vm.save(overwrite = false) }) { Text(stringResource(R.string.save_as_copy)) } },
        dismissButton = {
            if (input.password == null) TextButton(onClick = { askSave = false; vm.save(overwrite = true) }) { Text(stringResource(R.string.overwrite_original)) }
        },
    )
}
