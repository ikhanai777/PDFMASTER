package com.pdfmaster.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.pdfmaster.core.SafeFile
import com.pdfmaster.data.db.DocumentDao
import com.pdfmaster.data.db.DocumentEntity
import com.pdfmaster.data.db.DocumentTextEntity
import com.pdfmaster.pdf.PdfOps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The on-device document library. Files live in app storage; anything imported from the
 * device is copied in so the user's original is never modified.
 */
class DocumentRepository(
    private val context: Context,
    private val dao: DocumentDao,
    private val ops: PdfOps,
    private val scope: CoroutineScope,
) {
    val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val versionsDir = File(context.filesDir, "versions").apply { mkdirs() }
    private val shareDir = File(context.cacheDir, "share").apply { mkdirs() }

    val documents: Flow<List<DocumentEntity>> = dao.observeAll()

    fun recent(limit: Int = 20): Flow<List<DocumentEntity>> = dao.observeRecent(limit)

    suspend fun get(path: String): DocumentEntity? = dao.get(path)

    /** Reconciles the index with what is actually on disk (e.g. after a crash mid-save). */
    suspend fun syncWithDisk() = withContext(Dispatchers.IO) {
        docsDir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
        val onDisk = docsDir.listFiles { f -> f.isFile && f.extension.equals("pdf", true) }.orEmpty().associateBy { it.absolutePath }
        val indexed = dao.all()
        indexed.filter { it.path !in onDisk }.forEach { dao.delete(it.path); dao.deleteText(it.path) }
        val known = indexed.map { it.path }.toSet()
        onDisk.values.filter { it.absolutePath !in known }.forEach { register(it) }
    }

    fun displayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    /** Copies a PDF from anywhere on the device into the library and returns the copy. */
    suspend fun importPdf(uri: Uri): File = withContext(Dispatchers.IO) {
        val name = displayName(uri)?.removeSuffix(".pdf")?.removeSuffix(".PDF") ?: "Document"
        val target = SafeFile.uniqueFile(docsDir, name, "pdf")
        SafeFile.write(target) { tmp ->
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: throw IOException("Cannot read $uri")
        }
        register(target)
        target
    }

    /** A fresh, not-yet-existing file in the library for a tool's output. */
    fun newOutputFile(baseName: String): File = SafeFile.uniqueFile(docsDir, baseName, "pdf")

    fun cacheFile(name: String): File = File(shareDir, SafeFile.sanitize(name))

    suspend fun register(file: File): DocumentEntity = withContext(Dispatchers.IO) {
        val info = runCatching { ops.inspect(file) }.getOrNull()
        val existing = dao.get(file.absolutePath)
        val now = System.currentTimeMillis()
        val entity = DocumentEntity(
            path = file.absolutePath,
            name = file.nameWithoutExtension,
            sizeBytes = file.length(),
            pageCount = info?.pageCount ?: 0,
            createdAt = existing?.createdAt ?: now,
            modifiedAt = file.lastModified(),
            lastOpenedAt = existing?.lastOpenedAt ?: now,
            starred = existing?.starred ?: false,
            encrypted = info?.needsPassword ?: false,
            tags = existing?.tags ?: "",
        )
        dao.upsert(entity)
        if (info != null && !info.needsPassword) indexText(file)
        entity
    }

    private fun indexText(file: File) = scope.launch(Dispatchers.IO) {
        val text = runCatching { ops.documentText(file) }.getOrNull() ?: return@launch
        dao.deleteText(file.absolutePath)
        if (text.isNotBlank()) dao.insertText(DocumentTextEntity(file.absolutePath, text))
    }

    suspend fun touch(path: String) = dao.touch(path, System.currentTimeMillis())
    suspend fun setStarred(path: String, starred: Boolean) = dao.setStarred(path, starred)
    suspend fun setTags(path: String, tags: String) = dao.setTags(path, tags)

    suspend fun rename(path: String, newName: String): File = withContext(Dispatchers.IO) {
        val file = File(path)
        val target = SafeFile.uniqueFile(docsDir, newName, "pdf")
        if (!file.renameTo(target)) throw IOException("Rename failed")
        val old = dao.get(path)
        dao.delete(path); dao.deleteText(path)
        val entity = register(target)
        old?.let { dao.upsert(entity.copy(starred = it.starred, tags = it.tags, createdAt = it.createdAt)) }
        target
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        File(path).delete()
        File(versionsDir, File(path).name).deleteRecursively()
        dao.delete(path); dao.deleteText(path)
    }

    /**
     * Replaces [original] with [edited], keeping the previous content as a restorable version.
     * The original is untouched until this is called, i.e. until the user confirms overwrite.
     */
    suspend fun overwriteWithVersion(original: File, edited: File) = withContext(Dispatchers.IO) {
        val dir = File(versionsDir, original.name).apply { mkdirs() }
        original.copyTo(File(dir, "${System.currentTimeMillis()}.pdf"), overwrite = true)
        dir.listFiles().orEmpty().sortedByDescending { it.name }.drop(MAX_VERSIONS).forEach { it.delete() }
        SafeFile.write(original) { tmp -> edited.copyTo(tmp, overwrite = true) }
        edited.delete()
        register(original)
    }

    fun versions(file: File): List<File> =
        File(versionsDir, file.name).listFiles().orEmpty().sortedByDescending { it.name }

    suspend fun restoreVersion(file: File, version: File) = withContext(Dispatchers.IO) {
        val copy = File(shareDir, "restore.pdf")
        version.copyTo(copy, overwrite = true)
        overwriteWithVersion(file, copy)
    }

    /** Full-text search across every indexed PDF. Returns matching paths. */
    suspend fun searchContent(query: String): Set<String> {
        val terms = query.split(Regex("\\s+")).map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }.filter { it.length >= 2 }
        if (terms.isEmpty()) return emptySet()
        return runCatching { dao.searchText(terms.joinToString(" ") { "$it*" }).toSet() }.getOrDefault(emptySet())
    }

    suspend fun exportTo(file: File, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: throw IOException("Cannot write $uri")
    }

    fun shareUri(file: File): Uri {
        val shareable = if (file.absolutePath.startsWith(docsDir.absolutePath) || file.absolutePath.startsWith(shareDir.absolutePath)) file
        else File(shareDir, file.name).also { file.copyTo(it, overwrite = true) }
        return FileProvider.getUriForFile(context, context.packageName + ".files", shareable)
    }

    private companion object {
        const val MAX_VERSIONS = 5
    }
}
