package com.pdfmaster.ocr

import com.pdfmaster.pdf.OcrLine
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Names a scan from what's on its first page: document type, a title-like line, and the date. */
object AutoNamer {

    private val typeKeywords = listOf(
        "invoice" to "Invoice",
        "receipt" to "Receipt",
        "contract" to "Contract",
        "agreement" to "Agreement",
        "statement" to "Statement",
        "certificate" to "Certificate",
        "passport" to "Passport",
        "prescription" to "Prescription",
    )

    fun name(firstPage: List<OcrLine>, imageHeight: Int, now: LocalDateTime = LocalDateTime.now()): String {
        val date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val all = firstPage.joinToString(" ") { it.text }.lowercase()
        val type = typeKeywords.firstOrNull { (k, _) -> k in all }?.second
        val title = titleLine(firstPage, imageHeight)
        return when {
            type != null && title != null && !title.lowercase().contains(type.lowercase()) -> "$type - $title $date"
            title != null -> "$title $date"
            type != null -> "$type $date"
            else -> "Scan " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm"))
        }
    }

    /** The tallest reasonably-sized line in the top 40% of the page, if it looks like words. */
    internal fun titleLine(lines: List<OcrLine>, imageHeight: Int): String? =
        lines.asSequence()
            .filter { imageHeight <= 0 || it.top < imageHeight * 0.4 }
            .map { it.text.trim() }
            .zip(lines.asSequence().filter { imageHeight <= 0 || it.top < imageHeight * 0.4 }.map { it.bottom - it.top })
            .filter { (t, _) -> t.length in 3..48 && t.count { it.isLetter() } >= t.length / 2 }
            .maxByOrNull { it.second }
            ?.first
            ?.replace(Regex("[\\\\/:*?\"<>|]"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.split(' ')
            ?.joinToString(" ") { w -> if (w.length > 3 && w.all { it.isUpperCase() || !it.isLetter() }) w.lowercase().replaceFirstChar { it.uppercase() } else w }
}
