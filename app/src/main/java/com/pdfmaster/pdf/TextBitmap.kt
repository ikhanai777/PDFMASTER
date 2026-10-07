package com.pdfmaster.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import kotlin.math.ceil

/** Renders text with Android's full shaping and bidi support into a transparent bitmap. */
object TextBitmap {
    /** Pixels per PDF point in the rendered bitmap. */
    const val SCALE = 4f

    fun render(text: String, color: Int, sizePt: Float, bold: Boolean): Bitmap {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.color = if ((color ushr 24) == 0) color or (0xFF shl 24) else color
            textSize = sizePt * SCALE
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val width = text.lines().maxOf { ceil(paint.measureText(it)).toInt() }.coerceAtLeast(1) + 2
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(true)
            .build()
        val bmp = Bitmap.createBitmap(width, layout.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        layout.draw(Canvas(bmp))
        return bmp
    }
}
