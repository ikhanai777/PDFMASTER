package com.pdfmaster.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Crash-safe saving: content is written to a sibling temp file, flushed to disk,
 * and only then swapped over the target. A crash mid-write leaves the original intact.
 */
object SafeFile {

    fun write(target: File, block: (File) -> Unit) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            block(temp)
            if (!temp.exists() || temp.length() == 0L) throw IOException("Nothing was written")
            FileOutputStream(temp, true).use { it.fd.sync() }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /** Returns a file in [dir] named [baseName].[ext] that does not exist yet. */
    fun uniqueFile(dir: File, baseName: String, ext: String): File {
        dir.mkdirs()
        val clean = sanitize(baseName).ifBlank { "document" }
        var candidate = File(dir, "$clean.$ext")
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "$clean ($n).$ext")
            n++
        }
        return candidate
    }

    fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().take(120)
}
