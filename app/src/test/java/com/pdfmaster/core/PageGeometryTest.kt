package com.pdfmaster.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PageGeometryTest {
    private val eps = 1e-3f

    private fun assertPoint(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals(expected.first, actual.first, eps)
        assertEquals(expected.second, actual.second, eps)
    }

    @Test fun unrotatedTopLeftIsPdfTopLeft() {
        val g = PageGeometry(0f, 0f, 600f, 800f, 0)
        assertPoint(0f to 800f, g.toPdf(0f, 0f))
        assertPoint(600f to 0f, g.toPdf(1f, 1f))
    }

    @Test fun cropBoxOffsetIsApplied() {
        val g = PageGeometry(10f, 20f, 600f, 800f, 0)
        assertPoint(10f to 820f, g.toPdf(0f, 0f))
    }

    @Test fun rotated90DisplaysPageClockwise() {
        // A /Rotate 90 page shows the PDF's bottom-left corner at the display's top-left.
        val g = PageGeometry(0f, 0f, 600f, 800f, 90)
        assertEquals(800f, g.displayWidth, eps)
        assertEquals(600f, g.displayHeight, eps)
        assertPoint(0f to 0f, g.toPdf(0f, 0f))
        assertPoint(0f to 800f, g.toPdf(1f, 0f))
        assertPoint(600f to 0f, g.toPdf(0f, 1f))
    }

    @Test fun rotated180() {
        val g = PageGeometry(0f, 0f, 600f, 800f, 180)
        assertPoint(600f to 0f, g.toPdf(0f, 0f))
        assertPoint(0f to 800f, g.toPdf(1f, 1f))
    }

    @Test fun rotated270() {
        val g = PageGeometry(0f, 0f, 600f, 800f, 270)
        assertPoint(600f to 800f, g.toPdf(0f, 0f))
        assertPoint(0f to 0f, g.toPdf(1f, 1f))
    }

    @Test fun toDisplayInvertsToPdfForAllRotations() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val g = PageGeometry(5f, 7f, 612f, 792f, rotation)
            for ((u, v) in listOf(0.1f to 0.2f, 0.5f to 0.5f, 0.9f to 0.3f)) {
                val (x, y) = g.toPdf(u, v)
                assertPoint(u to v, g.toDisplay(x, y))
            }
        }
    }
}
