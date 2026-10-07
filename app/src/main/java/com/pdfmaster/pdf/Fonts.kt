package com.pdfmaster.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

/**
 * Picks a font for user text. Latin text uses the standard Helvetica (no embedding,
 * smallest output); anything else embeds a subset of a system font that covers it.
 */
object Fonts {

    private val latinCandidates = listOf(
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/RobotoStatic-Regular.ttf",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
    )
    private val arabicCandidates = listOf(
        "/system/fonts/NotoNaskhArabic-Regular.ttf",
        "/system/fonts/NotoNaskhArabicUI-Regular.ttf",
        "/system/fonts/NotoSansArabic-Regular.ttf",
    )

    fun isWinAnsi(text: String): Boolean = text.all { canEncode(it) }

    private val badChars = HashSet<Char>()

    private fun canEncode(c: Char): Boolean {
        if (c in badChars) return false
        if (c.code in 32..126) return true
        return try {
            PDType1Font.HELVETICA.encode(c.toString()); true
        } catch (e: Exception) {
            badChars += c; false
        }
    }

    /** Keeps only characters Helvetica can encode; others become spaces. */
    fun winAnsiOnly(text: String): String =
        buildString { text.forEach { append(if (it == '\n' || it == '\t') ' ' else if (canEncode(it)) it else ' ') } }

    private fun hasArabic(text: String) = text.any { it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F || it.code in 0xFB50..0xFEFF }

    fun forText(doc: PDDocument, text: String, bold: Boolean): PDFont {
        if (isWinAnsi(text)) return if (bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
        val candidates = if (hasArabic(text)) arabicCandidates + latinCandidates else latinCandidates + arabicCandidates
        for (path in candidates) {
            val file = File(path)
            if (!file.exists()) continue
            val font = runCatching { PDType0Font.load(doc, file) }.getOrNull() ?: continue
            if (runCatching { font.encode(text) }.isSuccess) return font
        }
        return PDType1Font.HELVETICA
    }

    /**
     * Scripts whose letters change shape or run right-to-left. PDF text drawing does no
     * shaping, so these are rendered by Android's text engine instead (see [TextBitmap]).
     */
    fun needsShaping(text: String): Boolean = text.any {
        val c = it.code
        c in 0x0590..0x0DFF || c in 0x0E00..0x0EFF || c in 0x0F00..0x109F || c in 0xFB1D..0xFDFF || c in 0xFE70..0xFEFF
    }
}
