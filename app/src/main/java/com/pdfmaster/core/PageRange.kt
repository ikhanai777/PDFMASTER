package com.pdfmaster.core

/**
 * Parses human page-range text such as `1-3, 5, 8-` into zero-based page indices.
 * Pages are 1-based in the input; an open end (`8-`) runs to the last page and an
 * open start (`-3`) begins at page 1.
 */
object PageRange {

    class ParseException(message: String) : IllegalArgumentException(message)

    /** Returns each comma-separated group as its own list of zero-based indices, in input order. */
    fun parseGroups(text: String, pageCount: Int): List<List<Int>> {
        require(pageCount > 0) { "Document has no pages" }
        val parts = text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw ParseException("Enter at least one page or range")
        return parts.map { part -> parsePart(part, pageCount) }
    }

    /** Flattens all groups into one ordered list, keeping duplicates out. */
    fun parse(text: String, pageCount: Int): List<Int> =
        parseGroups(text, pageCount).flatten().distinct()

    private fun parsePart(part: String, pageCount: Int): List<Int> {
        val dash = part.indexOf('-')
        if (dash < 0) {
            val page = parsePage(part, pageCount)
            return listOf(page - 1)
        }
        val startText = part.substring(0, dash).trim()
        val endText = part.substring(dash + 1).trim()
        val start = if (startText.isEmpty()) 1 else parsePage(startText, pageCount)
        val end = if (endText.isEmpty()) pageCount else parsePage(endText, pageCount)
        if (start > end) throw ParseException("Range $part runs backwards")
        return (start..end).map { it - 1 }
    }

    private fun parsePage(text: String, pageCount: Int): Int {
        val value = text.toIntOrNull() ?: throw ParseException("'$text' is not a page number")
        if (value < 1 || value > pageCount) throw ParseException("Page $value is outside 1–$pageCount")
        return value
    }

    /** Splits `0 until pageCount` into consecutive chunks of [size] pages. */
    fun everyN(pageCount: Int, size: Int): List<List<Int>> {
        require(size > 0) { "Chunk size must be positive" }
        return (0 until pageCount).chunked(size)
    }

    /** Builds consecutive groups that start at each of [starts] (zero-based, any order). */
    fun fromStartPoints(pageCount: Int, starts: Collection<Int>): List<List<Int>> {
        val sorted = (starts + 0).filter { it in 0 until pageCount }.distinct().sorted()
        return sorted.mapIndexed { i, s ->
            val end = if (i + 1 < sorted.size) sorted[i + 1] else pageCount
            (s until end).toList()
        }
    }
}
