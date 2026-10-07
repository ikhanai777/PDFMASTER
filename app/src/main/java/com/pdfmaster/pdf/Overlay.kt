package com.pdfmaster.pdf

import android.graphics.Bitmap

/**
 * Something the user placed on a page in the viewer. All coordinates are normalised
 * display coordinates (0..1, origin top-left of the upright page as shown on screen),
 * so they survive zoom, screen size and page rotation.
 */
sealed interface Overlay {
    val id: Long

    data class Ink(
        override val id: Long,
        val points: List<Float>, // x0, y0, x1, y1, ...
        val color: Int,
        val widthFraction: Float,
        val highlighter: Boolean,
    ) : Overlay

    data class Shape(
        override val id: Long,
        val kind: Kind,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
        val color: Int,
        val widthFraction: Float,
    ) : Overlay {
        enum class Kind { RECTANGLE, ELLIPSE, LINE, ARROW, HIGHLIGHT_BOX, STRIKE, UNDERLINE }
    }

    data class Text(
        override val id: Long,
        val u: Float, val v: Float,
        val text: String,
        val color: Int,
        val sizeFraction: Float, // font size as a fraction of display height
    ) : Overlay

    data class Image(
        override val id: Long,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
        val bitmap: Bitmap,
        val caption: String? = null, // e.g. the date stamp under a signature
    ) : Overlay
}
