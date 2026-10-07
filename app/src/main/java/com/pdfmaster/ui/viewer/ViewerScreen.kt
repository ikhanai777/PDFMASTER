package com.pdfmaster.ui.viewer

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfmaster.R
import com.pdfmaster.billing.ToolId
import com.pdfmaster.data.ReadingMode
import com.pdfmaster.pdf.Overlay
import com.pdfmaster.pdf.PageRenderer
import com.pdfmaster.ui.LocalActions
import com.pdfmaster.ui.Routes
import com.pdfmaster.ui.common.BusyDialog
import com.pdfmaster.ui.common.ErrorDialog
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.common.PasswordDialog
import com.pdfmaster.ui.common.PdfThumbnail
import com.pdfmaster.ui.common.appViewModel
import com.pdfmaster.ui.common.shareFile
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.io.FileOutputStream

private val palette = listOf(0xFFD32F2F, 0xFF1565C0, 0xFF212121, 0xFF2E7D32, 0xFFFFEB3B, 0xFFFF9800, 0xFF8E24AA).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(path: String, initialMode: ViewerMode) {
    val app = LocalContext.current.applicationContext as Application
    val vm = appViewModel(key = path) { ViewerViewModel(app, it, File(path), initialMode) }
    val actions = LocalActions.current
    val context = LocalContext.current
    val container = LocalContainer.current
    val snackbar = remember { SnackbarHostState() }

    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var navSheet by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    var askSave by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var textRequest by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var jumpRequest by remember { mutableIntStateOf(-1) }

    BackHandler(enabled = vm.dirty) { confirmLeave = true }

    LaunchedEffect(vm.savedFile) {
        val saved = vm.savedFile ?: return@LaunchedEffect
        vm.savedFile = null
        val result = snackbar.showSnackbar(
            context.getString(R.string.saved_as, saved.nameWithoutExtension),
            actionLabel = if (saved != vm.file) context.getString(R.string.open) else null,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            actions.nav.popBackStack()
            actions.openViewer(saved.absolutePath)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (searchOpen) {
                SearchBar(vm, onClose = { searchOpen = false; vm.clearSearch() }, onJump = { jumpRequest = it })
            } else {
                TopAppBar(
                    title = { Text(vm.file.nameWithoutExtension, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { if (vm.dirty) confirmLeave = true else actions.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
                        IconButton(onClick = { navSheet = true }) { Icon(Icons.AutoMirrored.Filled.Toc, stringResource(R.string.contents)) }
                        IconButton(onClick = { vm.toggleBookmark() }) {
                            Icon(
                                if (vm.currentPage in vm.bookmarks) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                stringResource(R.string.bookmark),
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                            ViewerMenu(vm, expanded = menuOpen, onDismiss = { menuOpen = false }, onPrint = { printFile(context, vm.file, vm.password) }, onShare = {
                                shareFile(context, container.documents.shareUri(vm.file), vm.file.name)
                            })
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (vm.renderer != null) ModeBar(vm, onSave = { askSave = true })
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
            val renderer = vm.renderer
            when {
                vm.loadError != null -> Text(stringResource(R.string.cannot_open, vm.loadError!!), Modifier.align(Alignment.Center).padding(24.dp))
                renderer == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> DocumentPages(vm, renderer, jumpRequest, onJumped = { jumpRequest = -1 }, onTextRequest = { p, u, v -> textRequest = Triple(p, u, v) })
            }
            if (renderer != null) {
                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clickable { goTo = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
                ) {
                    Text(
                        stringResource(R.string.page_x_of_y, vm.currentPage + 1, vm.pageCount),
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (vm.speaking) ReadAloudBar(vm, Modifier.align(Alignment.TopCenter))
        }
    }

    if (vm.needsPassword) PasswordDialog(vm.file.nameWithoutExtension, vm.wrongPassword, onSubmit = vm::unlockWith, onDismiss = { actions.back() })
    if (vm.busy) BusyDialog(stringResource(R.string.working))
    vm.error?.let { ErrorDialog(it) { vm.error = null } }

    if (navSheet) NavigationSheet(vm, onDismiss = { navSheet = false }, onJump = { jumpRequest = it; navSheet = false })
    if (goTo) GoToPageDialog(vm.pageCount, onDismiss = { goTo = false }) { jumpRequest = it; goTo = false }

    textRequest?.let { (page, u, v) ->
        TextEntryDialog(onDismiss = { textRequest = null }) { text ->
            vm.add(page, Overlay.Text(vm.newId(), u, v, text, vm.color, vm.textSize))
            textRequest = null
        }
    }

    if (askSave) AlertDialog(
        onDismissRequest = { askSave = false },
        title = { Text(stringResource(R.string.save_changes)) },
        text = { Text(stringResource(if (vm.isEncrypted) R.string.save_encrypted_note else R.string.save_choice_body)) },
        confirmButton = { TextButton(onClick = { askSave = false; vm.save(overwrite = false) }) { Text(stringResource(R.string.save_as_copy)) } },
        dismissButton = {
            if (!vm.isEncrypted) TextButton(onClick = { askSave = false; vm.save(overwrite = true) }) { Text(stringResource(R.string.overwrite_original)) }
        },
    )

    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(stringResource(R.string.unsaved_changes)) },
        text = { Text(stringResource(R.string.unsaved_changes_body)) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; vm.save(overwrite = false) { actions.back() } }) { Text(stringResource(R.string.save_as_copy)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = { confirmLeave = false; vm.overlays.clear(); actions.back() }) { Text(stringResource(R.string.discard)) }
            }
        },
    )
}

@Composable
private fun DocumentPages(vm: ViewerViewModel, renderer: PageRenderer, jumpRequest: Int, onJumped: () -> Unit, onTextRequest: (Int, Float, Float) -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    val pageLabel = stringResource(R.string.page)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Two-finger pinch zoom, intercepted before the list sees it.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size > 1) {
                            zoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                            val maxPan = (zoom - 1f) * size.width / 2f
                            panX = (panX + event.calculatePan().x).coerceIn(-maxPan, maxPan)
                            if (zoom == 1f) panX = 0f
                            event.changes.forEach { it.consume() }
                        } else if (zoom > 1f && vm.mode == ViewerMode.READ) {
                            val change = event.changes.first()
                            val maxPan = (zoom - 1f) * size.width / 2f
                            panX = (panX + (change.position.x - change.previousPosition.x)).coerceIn(-maxPan, maxPan)
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val density = LocalDensity.current
        val baseWidth = with(density) { maxWidth.toPx() }.toInt()
        // Re-render sharper as the user zooms, in coarse steps to limit re-renders.
        val quant = when { zoom < 1.4f -> 1f; zoom < 2.2f -> 2f; else -> 3f }
        val renderWidth = (baseWidth * quant).toInt()
        val layer = Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f) }
        val scrollEnabled = vm.mode != ViewerMode.ANNOTATE || vm.tool == AnnotTool.HAND

        if (vm.singlePage) {
            val pager = rememberPagerState(initialPage = vm.initialPage.coerceIn(0, (vm.pageCount - 1).coerceAtLeast(0))) { vm.pageCount }
            LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { vm.onPageVisible(it) } }
            LaunchedEffect(jumpRequest) { if (jumpRequest >= 0) { pager.scrollToPage(jumpRequest); onJumped() } }
            LaunchedEffect(vm.currentPage) { if (vm.speaking && pager.currentPage != vm.currentPage) pager.animateScrollToPage(vm.currentPage) }
            HorizontalPager(pager, Modifier.fillMaxSize().then(layer), userScrollEnabled = scrollEnabled && zoom == 1f) { page ->
                Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                    PageView(vm, renderer, page, renderWidth, "$pageLabel ${page + 1}", onTextRequest)
                }
            }
        } else {
            val list = rememberLazyListState(initialFirstVisibleItemIndex = vm.initialPage.coerceIn(0, (vm.pageCount - 1).coerceAtLeast(0)))
            LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { vm.onPageVisible(it) } }
            LaunchedEffect(jumpRequest) { if (jumpRequest >= 0) { list.scrollToItem(jumpRequest); onJumped() } }
            LaunchedEffect(vm.activeHit) { vm.hits.getOrNull(vm.activeHit)?.let { list.animateScrollToItem(it.pageIndex) } }
            LaunchedEffect(vm.currentPage) { if (vm.speaking && list.firstVisibleItemIndex != vm.currentPage) list.animateScrollToItem(vm.currentPage) }
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().then(layer),
                userScrollEnabled = scrollEnabled,
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.pageCount, key = { it }) { page ->
                    PageView(vm, renderer, page, renderWidth, "$pageLabel ${page + 1}", onTextRequest)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(vm: ViewerViewModel, onClose: () -> Unit, onJump: (Int) -> Unit) {
    var text by remember { mutableStateOf(vm.searchQuery) }
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close)) } },
        title = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_in_document)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(text) }),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                supportingText = {
                    when {
                        vm.searching -> Text(stringResource(R.string.searching_n, vm.hits.size))
                        vm.searchQuery.isNotEmpty() -> Text(stringResource(R.string.found_on_pages, vm.hits.size))
                    }
                },
            )
        },
        actions = {
            IconButton(enabled = vm.hits.isNotEmpty(), onClick = {
                vm.activeHit = if (vm.activeHit <= 0) vm.hits.lastIndex else vm.activeHit - 1
                vm.hits.getOrNull(vm.activeHit)?.let { onJump(it.pageIndex) }
            }) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.previous)) }
            IconButton(enabled = vm.hits.isNotEmpty(), onClick = {
                vm.activeHit = if (vm.activeHit >= vm.hits.lastIndex) 0 else vm.activeHit + 1
                vm.hits.getOrNull(vm.activeHit)?.let { onJump(it.pageIndex) }
            }) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.next)) }
        },
    )
}

@Composable
private fun ViewerMenu(vm: ViewerViewModel, expanded: Boolean, onDismiss: () -> Unit, onPrint: () -> Unit, onShare: () -> Unit) {
    val actions = LocalActions.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.read_aloud)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) }, onClick = { onDismiss(); vm.toggleReadAloud() })
        HorizontalDivider()
        ReadingMode.entries.forEach { mode ->
            val label = when (mode) { ReadingMode.NORMAL -> R.string.mode_normal; ReadingMode.NIGHT -> R.string.mode_night; ReadingMode.SEPIA -> R.string.mode_sepia }
            DropdownMenuItem(
                text = { Text(stringResource(label) + if (vm.readingMode == mode) "  ✓" else "") },
                onClick = { onDismiss(); vm.setReading(mode) },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(if (vm.singlePage) R.string.continuous_scroll else R.string.single_page)) },
            onClick = { onDismiss(); vm.singlePage = !vm.singlePage },
        )
        HorizontalDivider()
        DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = { onDismiss(); onShare() })
        DropdownMenuItem(text = { Text(stringResource(R.string.print)) }, onClick = { onDismiss(); onPrint() })
        HorizontalDivider()
        listOf(ToolId.ORGANIZE_PAGES, ToolId.FILL_FORM, ToolId.COMPRESS, ToolId.SPLIT, ToolId.MAKE_SEARCHABLE, ToolId.PROTECT).forEach { tool ->
            DropdownMenuItem(
                text = { Text(com.pdfmaster.ui.tools.toolTitle(tool)) },
                onClick = { onDismiss(); actions.launchTool(tool, vm.file.absolutePath) },
            )
        }
    }
}

@Composable
private fun ModeBar(vm: ViewerViewModel, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding()) {
            when (vm.mode) {
                ViewerMode.ANNOTATE -> AnnotateTools(vm)
                ViewerMode.SIGN -> SignTools(vm)
                ViewerMode.READ -> Unit
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(vm.mode == ViewerMode.READ, { vm.mode = ViewerMode.READ }, label = { Text(stringResource(R.string.mode_read)) })
                Spacer(Modifier.width(6.dp))
                FilterChip(vm.mode == ViewerMode.ANNOTATE, { vm.mode = ViewerMode.ANNOTATE; if (vm.tool == AnnotTool.HAND) vm.tool = AnnotTool.PEN }, label = { Text(stringResource(R.string.mode_annotate)) })
                Spacer(Modifier.width(6.dp))
                FilterChip(vm.mode == ViewerMode.SIGN, { vm.mode = ViewerMode.SIGN }, label = { Text(stringResource(R.string.mode_sign)) })
                Spacer(Modifier.weight(1f))
                IconButton(enabled = vm.canUndo, onClick = vm::undo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo)) }
                IconButton(enabled = vm.canRedo, onClick = vm::redo) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo)) }
                IconButton(enabled = vm.dirty, onClick = onSave) { Icon(Icons.Default.Save, stringResource(R.string.save)) }
            }
        }
    }
}

private data class ToolButton(val tool: AnnotTool, val icon: ImageVector, val label: Int)

private val annotTools = listOf(
    ToolButton(AnnotTool.HAND, Icons.Default.PanTool, R.string.annot_hand),
    ToolButton(AnnotTool.PEN, Icons.Default.Brush, R.string.annot_pen),
    ToolButton(AnnotTool.HIGHLIGHTER, Icons.Default.Highlight, R.string.annot_highlighter),
    ToolButton(AnnotTool.HIGHLIGHT_BOX, Icons.Default.FormatColorFill, R.string.annot_highlight_area),
    ToolButton(AnnotTool.UNDERLINE, Icons.Default.FormatUnderlined, R.string.annot_underline),
    ToolButton(AnnotTool.STRIKE, Icons.Default.FormatStrikethrough, R.string.annot_strike),
    ToolButton(AnnotTool.TEXT, Icons.Default.TextFields, R.string.annot_text),
    ToolButton(AnnotTool.RECTANGLE, Icons.Default.CheckBoxOutlineBlank, R.string.annot_rectangle),
    ToolButton(AnnotTool.ELLIPSE, Icons.Default.RadioButtonUnchecked, R.string.annot_ellipse),
    ToolButton(AnnotTool.ARROW, Icons.Default.NorthEast, R.string.annot_arrow),
    ToolButton(AnnotTool.ERASER, Icons.Default.AutoFixNormal, R.string.annot_eraser),
)

@Composable
private fun AnnotateTools(vm: ViewerViewModel) {
    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(annotTools) { b ->
                IconToggleButton(checked = vm.tool == b.tool, onCheckedChange = {
                    vm.tool = b.tool
                    val highlighting = b.tool == AnnotTool.HIGHLIGHTER || b.tool == AnnotTool.HIGHLIGHT_BOX
                    if (highlighting && vm.color == palette[0]) vm.color = palette[4]
                    if (!highlighting && vm.color == palette[4]) vm.color = palette[0]
                }) {
                    Icon(b.icon, stringResource(b.label), tint = if (vm.tool == b.tool) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            palette.forEach { c ->
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Color(c), CircleShape)
                        .border(if (vm.color == c) 3.dp else 1.dp, if (vm.color == c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { vm.color = c },
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                if (vm.tool == AnnotTool.TEXT) vm.textSize = (vm.textSize / 1.25f).coerceAtLeast(0.008f) else vm.strokeWidth = (vm.strokeWidth / 1.4f).coerceAtLeast(0.001f)
            }) { Icon(Icons.Default.Remove, stringResource(R.string.thinner)) }
            IconButton(onClick = {
                if (vm.tool == AnnotTool.TEXT) vm.textSize = (vm.textSize * 1.25f).coerceAtMost(0.08f) else vm.strokeWidth = (vm.strokeWidth * 1.4f).coerceAtMost(0.04f)
            }) { Icon(Icons.Default.Add, stringResource(R.string.thicker)) }
        }
    }
}

@Composable
private fun SignTools(vm: ViewerViewModel) {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val signatures by container.signatures.items.collectAsState()
    val selectedId = vm.selectedOverlayId
    val selectedPage = vm.overlays.entries.firstOrNull { e -> e.value.any { it.id == selectedId } }?.key

    Column(Modifier.padding(horizontal = 8.dp)) {
        Text(
            stringResource(if (vm.selectedSignature == null) R.string.sign_pick_hint else R.string.sign_place_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(signatures, key = { it.id }) { sig ->
                val bmp by produceState<android.graphics.Bitmap?>(null, sig.id) { value = container.signatures.load(sig) }
                val selected = bmp != null && vm.selectedSignature === bmp
                Box(
                    Modifier
                        .size(width = 96.dp, height = 48.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { vm.selectedSignature = if (selected) null else bmp },
                    contentAlignment = Alignment.Center,
                ) {
                    bmp?.let { Image(it.asImageBitmap(), stringResource(R.string.signature), Modifier.padding(4.dp)) }
                }
            }
            item {
                AssistChip(onClick = { actions.nav.navigate(Routes.SIGNATURES) }, label = { Text(stringResource(R.string.new_signature)) }, leadingIcon = { Icon(Icons.Default.Draw, null) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(vm.dateStamp, { vm.dateStamp = !vm.dateStamp }, label = { Text(stringResource(R.string.add_date)) })
            if (selectedId != null && selectedPage != null) {
                val item = vm.find(selectedPage, selectedId) as? Overlay.Image
                if (item != null) {
                    IconButton(onClick = { vm.replace(selectedPage, item, scaled(item, 0.85f)) }) { Icon(Icons.Default.Remove, stringResource(R.string.smaller)) }
                    IconButton(onClick = { vm.replace(selectedPage, item, scaled(item, 1.18f)) }) { Icon(Icons.Default.Add, stringResource(R.string.larger)) }
                    TextButton(onClick = {
                        // Same spot on every other page.
                        for (p in 0 until vm.pageCount) if (p != selectedPage) vm.add(p, item.copy(id = vm.newId()))
                    }) { Text(stringResource(R.string.all_pages)) }
                    IconButton(onClick = { vm.removeAt(selectedPage, selectedId) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                }
            }
        }
    }
}

private fun scaled(item: Overlay.Image, factor: Float): Overlay.Image {
    val cx = (item.u0 + item.u1) / 2; val cy = (item.v0 + item.v1) / 2
    val hw = (item.u1 - item.u0) / 2 * factor; val hh = (item.v1 - item.v0) / 2 * factor
    if (hw * 2 > 1f || hh * 2 > 1f || hw < 0.02f) return item
    return item.copy(u0 = cx - hw, v0 = cy - hh, u1 = cx + hw, v1 = cy + hh)
}

@Composable
private fun ReadAloudBar(vm: ViewerViewModel, modifier: Modifier) {
    Surface(modifier.padding(8.dp), shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp, shadowElevation = 4.dp) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.padding(4.dp))
            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                TextButton(onClick = { vm.speechRate = rate }) {
                    Text("${rate}x", color = if (vm.speechRate == rate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = vm::stopReading) { Icon(Icons.Default.Stop, stringResource(R.string.stop)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationSheet(vm: ViewerViewModel, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    var tab by remember { mutableIntStateOf(if (vm.outline.isEmpty()) 2 else 0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.contents)) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.bookmarks)) })
            Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.pages)) })
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 520.dp)) {
            when (tab) {
                0 -> if (vm.outline.isEmpty()) Text(stringResource(R.string.no_outline), Modifier.padding(24.dp)) else LazyColumn {
                    itemsIndexed(vm.outline) { _, entry ->
                        ListItem(
                            modifier = Modifier.clickable(enabled = entry.pageIndex >= 0) { onJump(entry.pageIndex) }.padding(start = (entry.level * 16).dp),
                            headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { if (entry.pageIndex >= 0) Text("${entry.pageIndex + 1}") },
                        )
                    }
                }
                1 -> if (vm.bookmarks.isEmpty()) Text(stringResource(R.string.no_bookmarks), Modifier.padding(24.dp)) else LazyColumn {
                    items(vm.bookmarks.sorted()) { p ->
                        ListItem(
                            modifier = Modifier.clickable { onJump(p) },
                            leadingContent = { Icon(Icons.Default.Bookmark, null) },
                            headlineContent = { Text(stringResource(R.string.page_n, p + 1)) },
                            trailingContent = { IconButton(onClick = { vm.toggleBookmark(p) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
                        )
                    }
                }
                else -> LazyVerticalGrid(GridCells.Adaptive(96.dp), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.pageCount) { p ->
                        Column(Modifier.clickable { onJump(p) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            PdfThumbnail(vm.renderSource(), Modifier.fillMaxWidth().height(128.dp), page = p, widthPx = 200)
                            Text("${p + 1}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GoToPageDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val n = text.toIntOrNull()
    val valid = n != null && n in 1..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.go_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                label = { Text("1 – $pageCount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid) onGo(n!! - 1) }),
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onGo(n!! - 1) }) { Text(stringResource(R.string.go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TextEntryDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_text)) },
        text = { OutlinedTextField(text, { text = it }, minLines = 2) },
        confirmButton = { Button(enabled = text.isNotBlank(), onClick = { onDone(text.trimEnd()) }) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Sends the PDF to Android printing (also gives "Save as PDF" and cast-to-printer targets). */
private fun printFile(context: Context, file: File, password: String?) {
    val source = if (password != null) File(context.cacheDir, "view_${file.absolutePath.hashCode()}.pdf").takeIf { it.exists() } ?: file else file
    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    manager.print(file.nameWithoutExtension, object : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
            if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
            callback.onLayoutFinished(PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
        }

        override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) {
            runCatching {
                source.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
            }.onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                .onFailure { callback.onWriteFailed(it.message) }
        }
    }, null)
}
