package com.pdfmaster.ocr

import com.pdfmaster.pdf.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AutoNamerTest {
    private val now = LocalDateTime.of(2026, 10, 7, 9, 30)

    @Test fun noTextFallsBackToDate() {
        assertEquals("Scan 2026-10-07 09.30", AutoNamer.name(emptyList(), 1000, now))
    }

    @Test fun usesTallestTitleLineNearTop() {
        val lines = listOf(
            OcrLine("ACME TRADING LLC", 50, 40, 600, 120),
            OcrLine("small print here", 50, 150, 400, 170),
            OcrLine("Footer text very tall", 50, 900, 600, 990),
        )
        assertEquals("Acme Trading LLC 2026-10-07", AutoNamer.name(lines, 1000, now))
    }

    @Test fun detectsDocumentType() {
        val lines = listOf(
            OcrLine("Carrefour", 50, 40, 600, 120),
            OcrLine("Receipt no. 12345", 50, 150, 400, 170),
        )
        assertEquals("Receipt - Carrefour 2026-10-07", AutoNamer.name(lines, 1000, now))
    }
}
