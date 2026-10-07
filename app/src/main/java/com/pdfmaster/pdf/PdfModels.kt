package com.pdfmaster.pdf

import java.io.File

data class PdfInfo(
    val pageCount: Int,
    val needsPassword: Boolean,
    val hasForm: Boolean,
    val title: String?,
)

data class OutlineEntry(val title: String, val pageIndex: Int, val level: Int)

/** A normalised rectangle in display coordinates. */
data class NRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class SearchHit(val pageIndex: Int, val snippet: String, val rects: List<NRect>)

/** One page in an organiser edit list: either a page from a source PDF, or a blank page. */
data class PageSpec(
    val source: File?,
    val sourceIndex: Int,
    val extraRotation: Int = 0,
    val blankWidth: Float = 595f,
    val blankHeight: Float = 842f,
)

enum class PageSizeOption { FIT, A4, LETTER }

data class ImagePageOptions(
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val marginPt: Float = 0f,
    val jpegQuality: Float = 0.85f,
)

/** A recognised line of text with its bounding box in source-image pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

enum class CompressPreset(val maxDimension: Int, val quality: Float) {
    LIGHT(2200, 0.85f),
    BALANCED(1600, 0.7f),
    STRONG(1100, 0.55f),
    EXTREME(800, 0.4f),
}

data class Permissions(val print: Boolean = true, val copy: Boolean = true, val modify: Boolean = true)

enum class NumberPosition { BOTTOM_CENTER, BOTTOM_RIGHT, TOP_RIGHT, BOTTOM_LEFT }

class PasswordRequiredException : Exception("Password required")

data class OcrPageResult(val lines: List<OcrLine>, val width: Int, val height: Int)
