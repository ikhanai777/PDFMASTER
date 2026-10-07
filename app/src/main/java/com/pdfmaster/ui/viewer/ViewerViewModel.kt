package com.pdfmaster.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.AppContainer
import com.pdfmaster.data.ReadingMode
import com.pdfmaster.pdf.OutlineEntry
import com.pdfmaster.pdf.Overlay
import com.pdfmaster.pdf.PageRenderer
import com.pdfmaster.pdf.PasswordRequiredException
import com.pdfmaster.pdf.SearchHit
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class ViewerMode { READ, ANNOTATE, SIGN }

enum class AnnotTool { HAND, PEN, HIGHLIGHTER, HIGHLIGHT_BOX, UNDERLINE, STRIKE, TEXT, RECTANGLE, ELLIPSE, ARROW, ERASER }

/** One undoable change to the overlays on a page. */
private sealed interface Edit {
    val page: Int
    data class Add(override val page: Int, val item: Overlay) : Edit
    data class Remove(override val page: Int, val item: Overlay, val index: Int) : Edit
    data class Replace(override val page: Int, val before: Overlay, val after: Overlay) : Edit
}

class ViewerViewModel(
    private val app: Application,
    private val container: AppContainer,
    val file: File,
    initialMode: ViewerMode,
) : ViewModel() {

    // ---- document
    var renderer by mutableStateOf<PageRenderer?>(null); private set
    var pageCount by mutableStateOf(0); private set
    var needsPassword by mutableStateOf(false); private set
    var wrongPassword by mutableStateOf(false); private set
    var loadError by mutableStateOf<String?>(null); private set
    var password: String? = null; private set
    val isEncrypted get() = password != null
    private var decryptedCopy: File? = null

    /** The file the on-screen renderer reads (a temporary decrypted copy for locked PDFs). */
    fun renderSource(): File = decryptedCopy ?: file

    // ---- reading
    var currentPage by mutableStateOf(0)
    var readingMode by mutableStateOf(container.prefs.readingMode)
    var singlePage by mutableStateOf(false)
    var bookmarks by mutableStateOf(container.prefs.bookmarks(file.absolutePath)); private set
    var outline by mutableStateOf<List<OutlineEntry>>(emptyList()); private set
    val initialPage = container.prefs.lastPage(file.absolutePath)

    // ---- search
    var searchQuery by mutableStateOf("")
    val hits = mutableStateListOf<SearchHit>()
    var searching by mutableStateOf(false); private set
    var activeHit by mutableStateOf(-1)
    private var searchJob: Job? = null

    // ---- annotate / sign
    var mode by mutableStateOf(initialMode)
    var tool by mutableStateOf(if (initialMode == ViewerMode.ANNOTATE) AnnotTool.PEN else AnnotTool.HAND)
    var color by mutableStateOf(0xFFD32F2F.toInt())
    var strokeWidth by mutableStateOf(0.006f)
    var textSize by mutableStateOf(0.018f)
    val overlays = mutableStateMapOf<Int, List<Overlay>>()
    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    val dirty get() = overlays.values.any { it.isNotEmpty() }

    var selectedSignature by mutableStateOf<Bitmap?>(null)
    var selectedOverlayId by mutableStateOf<Long?>(null)
    var dateStamp by mutableStateOf(false)
    private var nextId = 1L
    fun newId() = nextId++

    // ---- busy / results
    var busy by mutableStateOf(false); private set
    var savedFile by mutableStateOf<File?>(null)
    var error by mutableStateOf<String?>(null)

    // ---- PdfBox document for text features, opened lazily
    private val docMutex = Mutex()
    private var doc: PDDocument? = null

    private suspend fun <T> withDoc(block: (PDDocument) -> T): T = withContext(Dispatchers.IO) {
        docMutex.withLock {
            val d = doc ?: container.pdfOps.open(file, password).also { doc = it }
            block(d)
        }
    }

    init {
        viewModelScope.launch { container.documents.touch(file.absolutePath) }
        openRenderer(file)
    }

    private fun openRenderer(source: File) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { PageRenderer(source) } }
            result.onSuccess {
                renderer = it
                pageCount = it.pageCount
                loadOutline()
            }.onFailure {
                // The platform renderer can't open encrypted files: ask for the password.
                val info = withContext(Dispatchers.IO) { runCatching { container.pdfOps.inspect(file) }.getOrNull() }
                when {
                    info?.needsPassword == true -> needsPassword = true
                    info != null && source == file -> unlockWith("")
                    else -> loadError = it.message ?: it.javaClass.simpleName
                }
            }
        }
    }

    fun unlockWith(pw: String) {
        viewModelScope.launch {
            busy = true
            val copy = File(app.cacheDir, "view_${file.absolutePath.hashCode()}.pdf")
            val ok = withContext(Dispatchers.IO) {
                runCatching { container.pdfOps.decryptCopy(file, copy, pw) }.isSuccess
            }
            busy = false
            if (ok) {
                password = pw
                decryptedCopy = copy
                needsPassword = false
                wrongPassword = false
                openRenderer(copy)
            } else wrongPassword = true
        }
    }

    private fun loadOutline() {
        viewModelScope.launch {
            outline = runCatching { withDoc { container.pdfOps.outline(it) } }.getOrDefault(emptyList())
        }
    }

    fun onPageVisible(page: Int) {
        currentPage = page
        container.prefs.setLastPage(file.absolutePath, page)
    }

    fun toggleBookmark(page: Int = currentPage) {
        bookmarks = if (page in bookmarks) bookmarks - page else bookmarks + page
        container.prefs.setBookmarks(file.absolutePath, bookmarks)
    }

    fun setReading(mode: ReadingMode) {
        readingMode = mode
        container.prefs.readingMode = mode
    }

    // ---------------------------------------------------------------- search

    fun search(query: String) {
        searchQuery = query
        searchJob?.cancel()
        hits.clear()
        activeHit = -1
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            searching = true
            try {
                withDoc { d ->
                    container.pdfOps.search(d, query, isActive = { isActive }) { hit ->
                        viewModelScope.launch(Dispatchers.Main) {
                            hits += hit
                            if (activeHit < 0) activeHit = 0
                        }
                    }
                }
            } catch (e: PasswordRequiredException) {
                error = e.message
            } finally {
                searching = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchQuery = ""
        hits.clear()
        activeHit = -1
    }

    // ---------------------------------------------------------------- overlays + undo

    fun add(page: Int, item: Overlay) = apply(Edit.Add(page, item), record = true)

    fun removeAt(page: Int, id: Long) {
        val list = overlays[page] ?: return
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) apply(Edit.Remove(page, list[index], index), record = true)
        if (selectedOverlayId == id) selectedOverlayId = null
    }

    fun replace(page: Int, before: Overlay, after: Overlay, record: Boolean = true) =
        apply(Edit.Replace(page, before, after), record)

    /** Live update during a drag; the whole drag is recorded once via [replace] at the end. */
    fun replaceLive(page: Int, after: Overlay) {
        val list = overlays[page] ?: return
        overlays[page] = list.map { if (it.id == after.id) after else it }
    }

    fun find(page: Int, id: Long): Overlay? = overlays[page]?.firstOrNull { it.id == id }

    private fun apply(edit: Edit, record: Boolean) {
        val list = overlays[edit.page].orEmpty()
        overlays[edit.page] = when (edit) {
            is Edit.Add -> list + edit.item
            is Edit.Remove -> list.filterNot { it.id == edit.item.id }
            is Edit.Replace -> list.map { if (it.id == edit.before.id) edit.after else it }
        }
        if (record) {
            undoStack.addLast(edit)
            redoStack.clear()
        }
        updateUndo()
    }

    fun undo() {
        val edit = undoStack.removeLastOrNull() ?: return
        val list = overlays[edit.page].orEmpty()
        overlays[edit.page] = when (edit) {
            is Edit.Add -> list.filterNot { it.id == edit.item.id }
            is Edit.Remove -> list.toMutableList().apply { add(edit.index.coerceAtMost(size), edit.item) }
            is Edit.Replace -> list.map { if (it.id == edit.after.id) edit.before else it }
        }
        redoStack.addLast(edit)
        updateUndo()
    }

    fun redo() {
        val edit = redoStack.removeLastOrNull() ?: return
        apply(edit, record = false)
        undoStack.addLast(edit)
        updateUndo()
    }

    private fun updateUndo() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    /** Places the selected signature at (u, v) on [page]; the signature is 30% of the page width. */
    fun placeSignature(page: Int, u: Float, v: Float, pageAspect: Float, allPages: Boolean = false) {
        val bmp = selectedSignature ?: return
        val w = 0.3f
        val h = w * bmp.height / bmp.width * pageAspect
        val caption = if (dateStamp) java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault()).format(java.util.Date()) else null
        val pages = if (allPages) 0 until pageCount else page..page
        var lastId = 0L
        val u0 = (u - w / 2).coerceIn(0f, 1f - w)
        val v0 = (v - h / 2).coerceIn(0f, (1f - h).coerceAtLeast(0f))
        for (p in pages) {
            val placed = Overlay.Image(newId(), u0, v0, u0 + w, v0 + h, bmp, caption)
            add(p, placed)
            lastId = placed.id
        }
        selectedOverlayId = lastId
    }

    // ---------------------------------------------------------------- save

    /** Burns overlays into a new file. If [overwrite], the original is replaced and versioned. */
    fun save(overwrite: Boolean, onDone: (File) -> Unit = {}) {
        if (!dirty) return
        viewModelScope.launch {
            busy = true
            val snapshot = overlays.toMap()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (overwrite && !isEncrypted) {
                        val temp = File(app.cacheDir, "edit_${System.nanoTime()}.pdf")
                        container.pdfOps.applyOverlays(file, temp, snapshot, password)
                        closeDoc()
                        container.documents.overwriteWithVersion(file, temp)
                        file
                    } else {
                        val out = container.documents.newOutputFile(file.nameWithoutExtension + " (edited)")
                        container.pdfOps.applyOverlays(file, out, snapshot, password)
                        container.documents.register(out)
                        out
                    }
                }
            }
            busy = false
            result.onSuccess { out ->
                overlays.clear(); undoStack.clear(); redoStack.clear(); updateUndo()
                selectedOverlayId = null
                if (out == file) reopen()
                savedFile = out
                onDone(out)
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
        }
    }

    private fun reopen() {
        val old = renderer
        renderer = null
        old?.close()
        openRenderer(file)
    }

    private fun closeDoc() {
        kotlinx.coroutines.runBlocking { docMutex.withLock { doc?.close(); doc = null } }
    }

    // ---------------------------------------------------------------- read aloud

    var speaking by mutableStateOf(false); private set
    var speechRate by mutableStateOf(1f)
    private var tts: TextToSpeech? = null
    private var ttsPage = 0

    fun toggleReadAloud() {
        if (speaking) { stopReading(); return }
        speaking = true
        ttsPage = currentPage
        val engine = tts
        if (engine == null) {
            tts = TextToSpeech(app) { status ->
                if (status == TextToSpeech.SUCCESS) speakPage(ttsPage) else speaking = false
            }.apply {
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onError(utteranceId: String?) { speaking = false }
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.startsWith("end:") == true && speaking && ttsPage + 1 < pageCount) {
                            ttsPage += 1
                            viewModelScope.launch { currentPage = ttsPage; speakPage(ttsPage) }
                        } else if (utteranceId?.startsWith("end:") == true) speaking = false
                    }
                })
            }
        } else speakPage(ttsPage)
    }

    private fun speakPage(page: Int) {
        viewModelScope.launch {
            val text = runCatching { withDoc { container.pdfOps.pageText(it, page) } }.getOrDefault("")
            val engine = tts ?: return@launch
            engine.setSpeechRate(speechRate)
            val sentences = text.replace(Regex("\\s+"), " ").split(Regex("(?<=[.!?؟])\\s")).filter { it.isNotBlank() }
            if (sentences.isEmpty()) {
                engine.speak(" ", TextToSpeech.QUEUE_FLUSH, null, "end:$page")
                return@launch
            }
            sentences.forEachIndexed { i, s ->
                val id = if (i == sentences.lastIndex) "end:$page" else "p$page-$i"
                engine.speak(s, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
            }
        }
    }

    fun stopReading() {
        speaking = false
        tts?.stop()
    }

    override fun onCleared() {
        tts?.shutdown()
        renderer?.close()
        runCatching { doc?.close() }
        decryptedCopy?.delete()
    }
}
