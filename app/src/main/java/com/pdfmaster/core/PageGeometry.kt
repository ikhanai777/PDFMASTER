package com.pdfmaster.core

/**
 * Maps between what the user sees (a page rendered upright, with normalised
 * coordinates u,v in 0..1 and v pointing down) and PDF user space (points, y up)
 * for a page with the given crop box and /Rotate value.
 */
data class PageGeometry(
    val left: Float,
    val bottom: Float,
    val width: Float,
    val height: Float,
    val rotation: Int,
) {
    init {
        require(rotation in setOf(0, 90, 180, 270)) { "Unsupported rotation $rotation" }
    }

    /** Width of the page as displayed, in points. */
    val displayWidth: Float get() = if (rotation % 180 == 0) width else height

    /** Height of the page as displayed, in points. */
    val displayHeight: Float get() = if (rotation % 180 == 0) height else width

    /** Display (u, v) → PDF user-space point. */
    fun toPdf(u: Float, v: Float): Pair<Float, Float> {
        val (px, py) = when (rotation) {
            0 -> u * width to (1f - v) * height
            90 -> v * width to u * height
            180 -> (1f - u) * width to v * height
            else -> (1f - v) * width to (1f - u) * height
        }
        return (left + px) to (bottom + py)
    }

    /** PDF user-space point → display (u, v). Inverse of [toPdf]. */
    fun toDisplay(x: Float, y: Float): Pair<Float, Float> {
        val px = (x - left) / width
        val py = (y - bottom) / height
        return when (rotation) {
            0 -> px to 1f - py
            90 -> py to px
            180 -> 1f - px to py
            else -> 1f - py to 1f - px
        }
    }
}
