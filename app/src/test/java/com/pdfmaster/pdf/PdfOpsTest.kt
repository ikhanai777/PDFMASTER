package com.pdfmaster.pdf

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceCharacteristicsDictionary
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Exercises the real PdfBox-Android engine on generated documents. These are the operations
 * behind the MVP acceptance checklist: merge, extract, reorder, protect/unlock, forms, search,
 * annotations, stamping, OCR text layers and compression.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfOpsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var ops: PdfOps

    @Before fun setUp() {
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
        ops = PdfOps(tmp.newFolder("pdfbox"))
    }

    /** A PDF whose page N says "Page N of <name>". */
    private fun textPdf(name: String, pages: Int, rotation: Int = 0, withOutline: Boolean = false): File {
        val file = File(tmp.root, "$name.pdf")
        PDDocument().use { doc ->
            repeat(pages) { i ->
                val page = PDPage(PDRectangle.A4).apply { this.rotation = rotation }
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 18f); cs.newLineAtOffset(72f, 700f)
                    cs.showText("Page ${i + 1} of $name"); cs.endText()
                }
            }
            if (withOutline) {
                val outline = PDDocumentOutline()
                listOf(0, 3, 7).forEachIndexed { k, p ->
                    outline.addLast(PDOutlineItem().apply { title = "Chapter ${k + 1}"; setDestination(doc.getPage(p)) })
                }
                doc.documentCatalog.documentOutline = outline
            }
            doc.save(file)
        }
        return file
    }

    private fun text(file: File, password: String = ""): String =
        PDDocument.load(file, password).use { PDFTextStripper().getText(it) }

    private fun pages(file: File, password: String = ""): Int = PDDocument.load(file, password).use { it.numberOfPages }

    private fun out(name: String) = File(tmp.root, "$name.pdf")

    @Test fun mergeFiveFilesKeepsOrder() {
        val inputs = (1..5).map { textPdf("f$it", it) }
        val merged = out("merged")
        ops.merge(inputs, merged)
        assertEquals(15, pages(merged))
        val t = text(merged)
        assertTrue(t.indexOf("of f1") < t.indexOf("of f3") && t.indexOf("of f3") < t.indexOf("of f5"))
    }

    @Test fun extractPagesThreeToSeven() {
        val src = textPdf("src", 10)
        val result = out("extract")
        ops.extract(src, listOf(2, 3, 4, 5, 6), result)
        assertEquals(5, pages(result))
        val t = text(result)
        assertTrue(t.contains("Page 3 of src") && t.contains("Page 7 of src") && !t.contains("Page 8 of src"))
        assertEquals(10, pages(src)) // original untouched
    }

    @Test fun splitByGroups() {
        val src = textPdf("big", 7)
        val parts = ops.split(src, listOf(listOf(0, 1), listOf(2, 3, 4), listOf(5, 6)), tmp.newFolder("parts"), "big")
        assertEquals(listOf(2, 3, 2), parts.map { pages(it) })
    }

    @Test fun organiseReorderRotateDuplicateInsert() {
        val a = textPdf("a", 3)
        val b = textPdf("b", 2)
        val result = out("organised")
        ops.buildFromPages(
            listOf(
                PageSpec(a, 2, extraRotation = 90),
                PageSpec(a, 0),
                PageSpec(a, 0), // duplicate
                PageSpec(null, 0), // blank
                PageSpec(b, 1),
            ),
            result,
        )
        PDDocument.load(result).use { doc ->
            assertEquals(5, doc.numberOfPages)
            assertEquals(90, doc.getPage(0).rotation)
            val stripper = PDFTextStripper()
            // Text extraction on rotated pages can break lines mid-word, so compare without whitespace.
            fun pageText(i: Int) = stripper.apply { startPage = i + 1; endPage = i + 1 }.getText(doc).replace(Regex("\\s"), "")
            assertTrue(pageText(0).contains("Page3ofa"))
            assertTrue(pageText(1).contains("Page1ofa"))
            assertTrue(pageText(2).contains("Page1ofa"))
            assertTrue(pageText(3).isBlank())
            assertTrue(pageText(4).contains("Page2ofb"))
        }
    }

    @Test fun protectThenOpenWithPasswordThenUnlock() {
        val src = textPdf("secret", 2)
        val locked = out("locked")
        ops.protect(src, locked, "s3cret", "", Permissions(print = true, copy = false, modify = false))
        assertTrue(ops.inspect(locked).needsPassword)
        val info = ops.inspect(locked, "s3cret")
        assertFalse(info.needsPassword)
        assertEquals(2, info.pageCount)
        PDDocument.load(locked, "s3cret").use { doc ->
            assertTrue(doc.isEncrypted)
            assertEquals(256, doc.encryption.length)
        }
        val unlocked = out("unlocked")
        ops.unlock(locked, unlocked, "s3cret")
        assertFalse(ops.inspect(unlocked).needsPassword)
        assertTrue(text(unlocked).contains("Page 1 of secret"))
    }

    @Test(expected = PasswordRequiredException::class) fun wrongPasswordIsReported() {
        val locked = out("locked2")
        ops.protect(textPdf("x", 1), locked, "right", "", Permissions())
        ops.open(locked, "wrong")
    }

    @Test fun searchFindsHitsWithHighlightRects() {
        val src = textPdf("needle", 5)
        PDDocument.load(src).use { doc ->
            val hits = mutableListOf<SearchHit>()
            ops.search(doc, "page 4", { true }) { hits += it }
            assertEquals(listOf(3), hits.map { it.pageIndex })
            val r = hits.single().rects.single()
            assertTrue(r.left in 0f..1f && r.right in 0f..1f && r.left < r.right && r.top < r.bottom)
            // Text was drawn ~142pt from the top of an 842pt page.
            assertEquals(142f / 842f, (r.top + r.bottom) / 2, 0.03f)
        }
    }

    @Test fun outlineAndBookmarkSplitPoints() {
        val src = textPdf("book", 10, withOutline = true)
        PDDocument.load(src).use { doc ->
            assertEquals(listOf(0, 3, 7), ops.outline(doc).map { it.pageIndex })
        }
    }

    @Test fun pageNumbersAndWatermarkAreStamped() {
        val src = textPdf("doc", 3, rotation = 90)
        val numbered = out("numbered")
        ops.pageNumbers(src, numbered, "Page {n} of {total}", NumberPosition.BOTTOM_CENTER, 1, 10f)
        val t = text(numbered)
        assertTrue(t.contains("Page 1 of 3") && t.contains("Page 3 of 3"))
        // Angled stamp on rotated pages: must produce a valid file.
        val marked = out("marked")
        ops.watermark(numbered, marked, "CONFIDENTIAL", 0.3f, 45f, 48f, 0x999999, null)
        assertEquals(3, pages(marked))
        // Straight stamp on chosen pages: the text must be there and only there.
        val straight = out("straight")
        ops.watermark(textPdf("plain", 3), straight, "DRAFT", 0.3f, 0f, 48f, 0x999999, listOf(1))
        PDDocument.load(straight).use { doc ->
            val s = PDFTextStripper()
            fun pageText(i: Int) = s.apply { startPage = i + 1; endPage = i + 1 }.getText(doc)
            assertFalse(pageText(0).contains("DRAFT"))
            assertTrue(pageText(1).contains("DRAFT"))
        }
    }

    @Test fun overlaysAreBurnedIn() {
        val src = textPdf("annot", 2)
        val sig = Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT); setPixel(10, 10, Color.BLUE) }
        val result = out("annotated")
        ops.applyOverlays(
            src, result,
            mapOf(
                0 to listOf(
                    Overlay.Ink(1, listOf(0.1f, 0.1f, 0.2f, 0.2f, 0.3f, 0.15f), 0xFFD32F2F.toInt(), 0.005f, highlighter = false),
                    Overlay.Shape(2, Overlay.Shape.Kind.HIGHLIGHT_BOX, 0.1f, 0.15f, 0.5f, 0.2f, 0xFFFFEB3B.toInt(), 0.005f),
                    Overlay.Text(3, 0.1f, 0.5f, "Approved by Sara", 0xFF1565C0.toInt(), 0.02f),
                ),
                1 to listOf(Overlay.Image(4, 0.6f, 0.8f, 0.9f, 0.9f, sig, caption = "7 Oct 2026")),
            ),
        )
        assertEquals(2, pages(result))
        val t = text(result)
        assertTrue(t.contains("Approved by Sara"))
        assertTrue(t.contains("7 Oct 2026"))
        PDDocument.load(result).use { doc ->
            val res: PDResources = doc.getPage(1).resources
            assertTrue(res.xObjectNames.any { res.isImageXObject(it) })
        }
    }

    @Test fun imagesToPdfWithOcrLayerIsSearchable() {
        val bmp = Bitmap.createBitmap(1000, 1400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val result = out("scan")
        ops.imagesToPdf(
            listOf({ bmp }),
            ImagePageOptions(PageSizeOption.A4),
            result,
            listOf(listOf(OcrLine("TAX INVOICE 2026", 100, 100, 700, 160), OcrLine("Total AED 1,250.00", 100, 900, 600, 950))),
        )
        assertEquals(1, pages(result))
        val t = text(result)
        assertTrue(t, t.contains("TAX INVOICE 2026"))
        assertTrue(t.contains("Total AED 1,250.00"))
    }

    @Test fun ocrLayerOnExistingPage() {
        val blank = out("blank")
        PDDocument().use { it.addPage(PDPage(PDRectangle.A4)); it.save(blank) }
        val result = out("searchable")
        ops.addOcrLayer(blank, result, mapOf(0 to OcrPageResult(listOf(OcrLine("Hello world", 100, 100, 500, 140)), 1000, 1414)))
        assertTrue(text(result).contains("Hello world"))
    }

    @Test fun compressShrinksLargeImages() {
        val src = out("photo")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4); doc.addPage(page)
            // A noisy image so lossless storage is genuinely large.
            val bmp = Bitmap.createBitmap(2400, 3200, Bitmap.Config.ARGB_8888)
            val rnd = java.util.Random(1)
            val row = IntArray(2400)
            for (y in 0 until 3200) { for (x in 0 until 2400) row[x] = Color.rgb(rnd.nextInt(256), (x / 10) % 256, (y / 12) % 256); bmp.setPixels(row, 0, 2400, 0, y, 2400, 1) }
            val img = LosslessFactory.createFromImage(doc, bmp)
            PDPageContentStream(doc, page).use { it.drawImage(img, 0f, 0f, PDRectangle.A4.width, PDRectangle.A4.height) }
            doc.save(src)
        }
        val result = out("small")
        val size = ops.compress(src, result, CompressPreset.BALANCED, grayscale = false)
        assertTrue("compressed $size vs ${src.length()}", size < src.length() / 3)
        assertEquals(1, pages(result))
    }

    @Test fun formFillSetsValuesAndDropsXfa() {
        val src = out("form")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4); doc.addPage(page)
            val form = PDAcroForm(doc)
            doc.documentCatalog.acroForm = form
            val dr = PDResources(); dr.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA); form.defaultResources = dr
            form.defaultAppearance = "/Helv 0 Tf 0 g"
            val name = PDTextField(form).apply { partialName = "FirstName"; defaultAppearance = "/Helv 12 Tf 0 g" }
            name.widgets.first().apply { rectangle = PDRectangle(50f, 700f, 200f, 20f); this.page = page }
            page.annotations.add(name.widgets.first())
            val agree = PDCheckBox(form).apply { partialName = "Agree" }
            agree.widgets.first().apply {
                rectangle = PDRectangle(50f, 650f, 15f, 15f); this.page = page
                appearanceCharacteristics = PDAppearanceCharacteristicsDictionary(com.tom_roush.pdfbox.cos.COSDictionary())
            }
            page.annotations.add(agree.widgets.first())
            form.fields.addAll(listOf(name, agree))
            form.cosObject.setItem(COSName.XFA, com.tom_roush.pdfbox.cos.COSString("<xdp/>"))
            doc.save(src)
        }
        val formOps = FormOps(ops)
        val fields = formOps.readFields(src)
        assertEquals(setOf("FirstName", "Agree"), fields.map { it.name }.toSet())
        assertEquals(FieldType.TEXT, fields.first { it.name == "FirstName" }.type)
        val filled = out("filled")
        formOps.fill(src, filled, mapOf("FirstName" to "Sara"), flatten = false)
        assertEquals("Sara", formOps.readFields(filled).first { it.name == "FirstName" }.value)
        PDDocument.load(filled).use { doc -> assertFalse(doc.documentCatalog.acroForm.cosObject.containsKey(COSName.XFA)) }
    }

    @Test fun removeHiddenDataClearsMetadata() {
        val src = textPdf("meta", 1)
        PDDocument.load(src).use { it.documentInformation.title = "Secret project"; it.documentInformation.author = "Someone"; it.save(src) }
        val result = out("clean")
        ops.removeHiddenData(src, result)
        PDDocument.load(result).use { doc ->
            assertEquals(null, doc.documentInformation.title)
            assertEquals(null, doc.documentInformation.author)
        }
    }

    @Test fun inspectReportsFormsAndPages() {
        val info = ops.inspect(textPdf("plain", 4))
        assertEquals(4, info.pageCount)
        assertFalse(info.hasForm)
        assertFalse(info.needsPassword)
    }
}
