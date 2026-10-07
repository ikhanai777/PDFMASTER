package com.pdfmaster.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SafeFileTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writesAtomically() {
        val target = File(tmp.root, "a.pdf")
        SafeFile.write(target) { it.writeText("hello") }
        assertEquals("hello", target.readText())
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun failedWriteKeepsOriginal() {
        val target = File(tmp.root, "a.pdf").apply { writeText("original") }
        runCatching { SafeFile.write(target) { it.writeText("partial"); error("crash") } }
        assertEquals("original", target.readText())
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun uniqueFileAvoidsCollisions() {
        File(tmp.root, "Doc.pdf").writeText("x")
        File(tmp.root, "Doc (2).pdf").writeText("x")
        assertEquals("Doc (3).pdf", SafeFile.uniqueFile(tmp.root, "Doc", "pdf").name)
    }

    @Test fun sanitizeStripsPathCharacters() {
        val clean = SafeFile.sanitize("a/b:c*?\"<>|d")
        assertFalse(clean.contains('/'))
        assertEquals("a_b_c______d", clean)
    }
}
