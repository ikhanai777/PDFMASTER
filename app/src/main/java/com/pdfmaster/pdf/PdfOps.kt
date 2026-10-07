package com.pdfmaster.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.pdfmaster.core.PageGeometry
import com.pdfmaster.core.SafeFile
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Every Phase 1 PDF operation. All methods block and must be called off the main thread.
 * Nothing here touches the network.
 */
class PdfOps(private val tempDir: File) {

    private fun memory(): MemoryUsageSetting =
        MemoryUsageSetting.setupMixed(64L * 1024 * 1024).setTempDir(tempDir.apply { mkdirs() })

    /** Opens a document; throws [PasswordRequiredException] if it needs a password we don't have. */
    fun open(file: File, password: String? = null): PDDocument = try {
        PDDocument.load(file, password ?: "", memory())
    } catch (e: InvalidPasswordException) {
        throw PasswordRequiredException()
    }

    fun inspect(file: File, password: String? = null): PdfInfo = try {
        open(file, password).use { doc ->
            PdfInfo(
                pageCount = doc.numberOfPages,
                needsPassword = false,
                hasForm = doc.documentCatalog.acroForm?.fields?.isNotEmpty() == true,
                title = doc.documentInformation?.title?.takeIf { it.isNotBlank() },
            )
        }
    } catch (e: PasswordRequiredException) {
        PdfInfo(pageCount = 0, needsPassword = true, hasForm = false, title = null)
    }

    private fun save(doc: PDDocument, out: File) = SafeFile.write(out) { tmp -> doc.save(tmp) }

    // ---------------------------------------------------------------- organise

    /** Merges PDFs in order, keeping bookmarks and form fields. */
    fun merge(inputs: List<File>, out: File) {
        val merger = PDFMergerUtility()
        inputs.forEach { merger.addSource(it) }
        SafeFile.write(out) { tmp ->
            merger.destinationFileName = tmp.absolutePath
            merger.mergeDocuments(memory())
        }
    }

    /** Writes the given zero-based pages of [src] to [out]. */
    fun extract(src: File, pages: List<Int>, out: File, password: String? = null) {
        open(src, password).use { source ->
            PDDocument(memory()).use { target ->
                pages.forEach { target.addPage(copyPage(source.getPage(it))) }
                save(target, out)
            }
        }
    }

    /** Writes each group of zero-based pages to its own file, named `<base> (part N).pdf`. */
    fun split(src: File, groups: List<List<Int>>, outDir: File, baseName: String, password: String? = null): List<File> {
        val outputs = mutableListOf<File>()
        open(src, password).use { source ->
            groups.forEachIndexed { i, group ->
                val out = SafeFile.uniqueFile(outDir, "$baseName (part ${i + 1})", "pdf")
                PDDocument(memory()).use { target ->
                    group.forEach { target.addPage(copyPage(source.getPage(it))) }
                    save(target, out)
                }
                outputs += out
            }
        }
        return outputs
    }

    /** Builds a new PDF from an organiser edit list (reorder, rotate, delete, duplicate, insert). */
    fun buildFromPages(specs: List<PageSpec>, out: File, passwords: Map<File, String> = emptyMap()) {
        val sources = mutableMapOf<File, PDDocument>()
        try {
            PDDocument(memory()).use { target ->
                specs.forEach { spec ->
                    val page = if (spec.source == null) {
                        PDPage(PDRectangle(spec.blankWidth, spec.blankHeight))
                    } else {
                        val src = sources.getOrPut(spec.source) { open(spec.source, passwords[spec.source]) }
                        copyPage(src.getPage(spec.sourceIndex))
                    }
                    page.rotation = ((page.rotation + spec.extraRotation) % 360 + 360) % 360
                    target.addPage(page)
                }
                save(target, out)
            }
        } finally {
            sources.values.forEach { runCatching { it.close() } }
        }
    }

    /** A shallow copy so the same source page can appear more than once in the output. */
    private fun copyPage(page: PDPage): PDPage {
        val dict = COSDictionary(page.cosObject)
        dict.removeItem(COSName.PARENT)
        // Inheritable attributes may live on the source page tree; pin them on the copy.
        val copy = PDPage(dict)
        copy.mediaBox = page.mediaBox
        page.cropBox.let { copy.cropBox = it }
        copy.resources = page.resources
        copy.rotation = page.rotation
        return copy
    }

    private fun stripSecurity(doc: PDDocument) {
        if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
    }

    // ---------------------------------------------------------------- create

    /**
     * Creates a PDF with one page per image. When [ocr] is given (same order as images),
     * an invisible text layer is added so the PDF is searchable and selectable.
     */
    fun imagesToPdf(
        images: List<() -> Bitmap>,
        options: ImagePageOptions,
        out: File,
        ocr: List<List<OcrLine>>? = null,
        onProgress: (Int) -> Unit = {},
    ) {
        PDDocument(memory()).use { doc ->
            images.forEachIndexed { index, load ->
                val bitmap = load()
                try {
                    val (pw, ph) = when (options.pageSize) {
                        PageSizeOption.A4 -> PDRectangle.A4.width to PDRectangle.A4.height
                        PageSizeOption.LETTER -> PDRectangle.LETTER.width to PDRectangle.LETTER.height
                        PageSizeOption.FIT -> {
                            val w = PDRectangle.A4.width
                            w + 2 * options.marginPt to w * bitmap.height / bitmap.width + 2 * options.marginPt
                        }
                    }
                    // Landscape images get a landscape page.
                    val landscape = options.pageSize != PageSizeOption.FIT && bitmap.width > bitmap.height
                    val pageW = if (landscape) ph else pw
                    val pageH = if (landscape) pw else ph
                    val page = PDPage(PDRectangle(pageW, pageH))
                    doc.addPage(page)

                    val boxW = pageW - 2 * options.marginPt
                    val boxH = pageH - 2 * options.marginPt
                    val scale = min(boxW / bitmap.width, boxH / bitmap.height)
                    val iw = bitmap.width * scale
                    val ih = bitmap.height * scale
                    val ix = (pageW - iw) / 2
                    val iy = (pageH - ih) / 2

                    val image = if (bitmap.hasAlpha()) LosslessFactory.createFromImage(doc, bitmap)
                    else JPEGFactory.createFromImage(doc, bitmap, options.jpegQuality)
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(image, ix, iy, iw, ih)
                        ocr?.getOrNull(index)?.let { lines ->
                            writeInvisibleText(cs, lines, bitmap.width, bitmap.height, ix, iy, iw, ih)
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
                onProgress(index + 1)
            }
            save(doc, out)
        }
    }

    private fun writeInvisibleText(
        cs: PDPageContentStream,
        lines: List<OcrLine>,
        bmpW: Int, bmpH: Int,
        ix: Float, iy: Float, iw: Float, ih: Float,
    ) {
        val font = PDType1Font.HELVETICA
        cs.beginText()
        cs.setRenderingMode(RenderingMode.NEITHER)
        for (line in lines) {
            val text = Fonts.winAnsiOnly(line.text).trim()
            if (text.isEmpty()) continue
            val boxW = (line.right - line.left).toFloat() / bmpW * iw
            val boxH = (line.bottom - line.top).toFloat() / bmpH * ih
            if (boxW <= 1f || boxH <= 1f) continue
            val x = ix + line.left.toFloat() / bmpW * iw
            val yBottom = iy + (1f - line.bottom.toFloat() / bmpH) * ih
            val fontSize = boxH * 0.85f
            val natural = font.getStringWidth(text) / 1000f * fontSize
            if (natural <= 0f) continue
            cs.setFont(font, fontSize)
            cs.setHorizontalScaling(boxW / natural * 100f)
            cs.setTextMatrix(Matrix.getTranslateInstance(x, yBottom + boxH * 0.18f))
            cs.showText(text)
        }
        cs.endText()
    }

    /**
     * Adds an invisible, selectable text layer to existing pages from OCR results that were
     * recognised on upright page renders of [OcrPageResult.width] x [OcrPageResult.height] px.
     */
    fun addOcrLayer(src: File, out: File, results: Map<Int, OcrPageResult>, password: String? = null) {
        open(src, password).use { doc ->
            val font = PDType1Font.HELVETICA
            for ((index, result) in results) {
                if (index !in 0 until doc.numberOfPages || result.lines.isEmpty()) continue
                val page = doc.getPage(index)
                val geom = geometry(page)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.beginText()
                    cs.setRenderingMode(RenderingMode.NEITHER)
                    for (line in result.lines) {
                        val text = Fonts.winAnsiOnly(line.text).trim()
                        if (text.isEmpty()) continue
                        val boxW = (line.right - line.left).toFloat() / result.width * geom.displayWidth
                        val boxH = (line.bottom - line.top).toFloat() / result.height * geom.displayHeight
                        if (boxW <= 1f || boxH <= 1f) continue
                        val fontSize = boxH * 0.85f
                        val natural = font.getStringWidth(text) / 1000f * fontSize
                        if (natural <= 0f) continue
                        val u = line.left.toFloat() / result.width
                        val v = (line.bottom.toFloat() - (line.bottom - line.top) * 0.18f) / result.height
                        val (x, y) = geom.toPdf(u, v)
                        cs.setFont(font, fontSize)
                        cs.setHorizontalScaling(boxW / natural * 100f)
                        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(geom.rotation.toDouble()), x, y))
                        cs.showText(text)
                    }
                    cs.endText()
                }
            }
            stripSecurity(doc)
            save(doc, out)
        }
    }

    /** Deletes document info, XMP metadata, JavaScript, embedded files and page thumbnails. */
    fun removeHiddenData(src: File, out: File, password: String? = null) {
        open(src, password).use { doc ->
            doc.documentInformation = com.tom_roush.pdfbox.pdmodel.PDDocumentInformation()
            val catalog = doc.documentCatalog
            catalog.metadata = null
            catalog.cosObject.removeItem(COSName.OPEN_ACTION)
            catalog.cosObject.removeItem(COSName.AA)
            catalog.cosObject.getCOSDictionary(COSName.NAMES)?.let { names ->
                names.removeItem(COSName.JAVA_SCRIPT)
                names.removeItem(COSName.EMBEDDED_FILES)
            }
            doc.pages.forEach { page ->
                page.metadata = null
                page.cosObject.removeItem(COSName.THUMB)
                page.cosObject.removeItem(COSName.AA)
            }
            stripSecurity(doc)
            save(doc, out)
        }
    }

    fun extractText(src: File, out: File, password: String? = null) {
        open(src, password).use { doc ->
            val text = PDFTextStripper().getText(doc)
            SafeFile.write(out) { it.writeText(text) }
        }
    }

    // ---------------------------------------------------------------- optimise

    /**
     * Re-encodes embedded images at a lower resolution and JPEG quality. Text and vector
     * content are untouched, so text stays sharp. Returns the output size in bytes.
     */
    fun compress(src: File, out: File, preset: CompressPreset, grayscale: Boolean, password: String? = null): Long {
        open(src, password).use { doc ->
            val replaced = HashMap<COSStream, PDImageXObject>()
            val visited = HashSet<COSStream>()
            doc.pages.forEach { page -> page.resources?.let { compressResources(doc, it, preset, grayscale, replaced, visited) } }
            stripSecurity(doc)
            save(doc, out)
        }
        return out.length()
    }

    /** Tries presets from lightest to strongest until the output is at or under [targetBytes]. */
    fun compressToTarget(src: File, out: File, targetBytes: Long, grayscale: Boolean, password: String? = null): Pair<Long, CompressPreset> {
        var last: Pair<Long, CompressPreset>? = null
        for (preset in CompressPreset.entries) {
            val size = compress(src, out, preset, grayscale, password)
            last = size to preset
            if (size <= targetBytes) break
        }
        return last!!
    }

    private fun compressResources(
        doc: PDDocument,
        resources: PDResources,
        preset: CompressPreset,
        grayscale: Boolean,
        replaced: HashMap<COSStream, PDImageXObject>,
        visited: HashSet<COSStream>,
    ) {
        for (name in resources.xObjectNames.toList()) {
            val xo = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
            when (xo) {
                is PDImageXObject -> {
                    val key = xo.cosObject
                    val already = replaced[key]
                    if (already != null) { resources.put(name, already); continue }
                    if (!visited.add(key)) continue
                    val newImage = recompressImage(doc, xo, preset, grayscale) ?: continue
                    replaced[key] = newImage
                    resources.put(name, newImage)
                }
                is PDFormXObject -> {
                    if (visited.add(xo.cosObject)) xo.resources?.let { compressResources(doc, it, preset, grayscale, replaced, visited) }
                }
            }
        }
    }

    private fun recompressImage(doc: PDDocument, xo: PDImageXObject, preset: CompressPreset, grayscale: Boolean): PDImageXObject? {
        if (xo.isStencil || xo.bitsPerComponent == 1) return null
        if (xo.width < 64 && xo.height < 64) return null
        val original = runCatching { xo.image }.getOrNull() ?: return null
        val longest = max(original.width, original.height)
        val scale = min(1f, preset.maxDimension.toFloat() / longest)
        val w = max(1, (original.width * scale).toInt())
        val h = max(1, (original.height * scale).toInt())
        val target = Bitmap.createBitmap(w, h, if (original.hasAlpha()) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
        Canvas(target).drawBitmap(
            original,
            null,
            android.graphics.Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                if (grayscale) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            },
        )
        if (target !== original) original.recycle()
        val newImage = runCatching { JPEGFactory.createFromImage(doc, target, preset.quality) }.getOrNull()
        target.recycle()
        newImage ?: return null
        val oldMask = xo.cosObject.getDictionaryObject(COSName.SMASK)
        if (oldMask != null && newImage.cosObject.getDictionaryObject(COSName.SMASK) == null) {
            newImage.cosObject.setItem(COSName.SMASK, oldMask)
        }
        // Only swap when it actually saves space; already-tight images stay as they were.
        return if (newImage.cosObject.length < xo.cosObject.length || grayscale) newImage else null
    }

    // ---------------------------------------------------------------- security

    fun protect(src: File, out: File, userPassword: String, ownerPassword: String, permissions: Permissions, currentPassword: String? = null) {
        open(src, currentPassword).use { doc ->
            stripSecurity(doc)
            val ap = AccessPermission().apply {
                setCanPrint(permissions.print)
                setCanPrintFaithful(permissions.print)
                setCanExtractContent(permissions.copy)
                setCanModify(permissions.modify)
                setCanModifyAnnotations(permissions.modify)
                setCanFillInForm(true)
            }
            val policy = StandardProtectionPolicy(ownerPassword.ifEmpty { userPassword }, userPassword, ap)
            policy.encryptionKeyLength = 256
            doc.protect(policy)
            save(doc, out)
        }
    }

    fun unlock(src: File, out: File, password: String) {
        open(src, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            save(doc, out)
        }
    }

    /** Writes a decrypted copy for the on-screen renderer, which cannot open encrypted files. */
    fun decryptCopy(src: File, out: File, password: String) = unlock(src, out, password)

    // ---------------------------------------------------------------- stamp

    fun watermark(src: File, out: File, text: String, opacity: Float, angleDegrees: Float, fontSize: Float, colorRgb: Int, pages: List<Int>?, password: String? = null) {
        open(src, password).use { doc ->
            val asImage = Fonts.needsShaping(text)
            val font = Fonts.forText(doc, text, bold = true)
            val drawn = text
            val textImage = if (asImage) LosslessFactory.createFromImage(doc, TextBitmap.render(text, 0xFF000000.toInt() or colorRgb, fontSize, bold = true)) else null
            pageIndices(doc, pages).forEach { i ->
                val page = doc.getPage(i)
                val geom = geometry(page)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setGraphicsStateParameters(alpha(opacity))
                    setFill(cs, colorRgb)
                    val (cx, cy) = geom.toPdf(0.5f, 0.5f)
                    if (textImage != null) {
                        val w = textImage.width / TextBitmap.SCALE
                        val h = textImage.height / TextBitmap.SCALE
                        val m = Matrix.getRotateInstance(Math.toRadians((geom.rotation + angleDegrees).toDouble()), cx, cy)
                        m.translate(-w / 2f, -h / 2f)
                        m.scale(w, h)
                        cs.drawImage(textImage, m)
                        return@use
                    }
                    val width = font.getStringWidth(drawn) / 1000f * fontSize
                    val m = Matrix.getRotateInstance(Math.toRadians((geom.rotation + angleDegrees).toDouble()), cx, cy)
                    m.translate(-width / 2f, -fontSize / 3f)
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.setTextMatrix(m)
                    cs.showText(drawn)
                    cs.endText()
                }
            }
            stripSecurity(doc)
            save(doc, out)
        }
    }

    fun pageNumbers(src: File, out: File, template: String, position: NumberPosition, startAt: Int, fontSize: Float, password: String? = null) {
        open(src, password).use { doc ->
            val total = doc.numberOfPages
            val font = PDType1Font.HELVETICA
            for (i in 0 until total) {
                val page = doc.getPage(i)
                val geom = geometry(page)
                val label = Fonts.winAnsiOnly(
                    template.replace("{n}", (i + startAt).toString()).replace("{total}", (total + startAt - 1).toString())
                )
                val marginU = 28f / geom.displayWidth
                val marginV = 24f / geom.displayHeight
                val (u, v, align) = when (position) {
                    NumberPosition.BOTTOM_CENTER -> Triple(0.5f, 1f - marginV, Align.CENTER)
                    NumberPosition.BOTTOM_RIGHT -> Triple(1f - marginU, 1f - marginV, Align.END)
                    NumberPosition.BOTTOM_LEFT -> Triple(marginU, 1f - marginV, Align.START)
                    NumberPosition.TOP_RIGHT -> Triple(1f - marginU, marginV + fontSize / geom.displayHeight, Align.END)
                }
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    setFill(cs, 0x333333)
                    drawText(cs, font, geom, label, u, v, fontSize, align)
                }
            }
            stripSecurity(doc)
            save(doc, out)
        }
    }

    /** Burns viewer annotations and signatures into the page content. */
    fun applyOverlays(src: File, out: File, overlays: Map<Int, List<Overlay>>, password: String? = null) {
        open(src, password).use { doc ->
            for ((pageIndex, items) in overlays) {
                if (items.isEmpty() || pageIndex !in 0 until doc.numberOfPages) continue
                val page = doc.getPage(pageIndex)
                val geom = geometry(page)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    items.forEach { drawOverlay(doc, cs, geom, it) }
                }
            }
            stripSecurity(doc)
            save(doc, out)
        }
    }

    private fun drawOverlay(doc: PDDocument, cs: PDPageContentStream, geom: PageGeometry, item: Overlay) {
        cs.saveGraphicsState()
        when (item) {
            is Overlay.Ink -> {
                if (item.points.size >= 2) {
                    if (item.highlighter) cs.setGraphicsStateParameters(alpha(0.35f, multiply = true))
                    else cs.setGraphicsStateParameters(alpha(Color.alpha(item.color)))
                    setStroke(cs, item.color)
                    cs.setLineWidth(item.widthFraction * geom.displayWidth)
                    cs.setLineCapStyle(1)
                    cs.setLineJoinStyle(1)
                    val (x0, y0) = geom.toPdf(item.points[0], item.points[1])
                    cs.moveTo(x0, y0)
                    if (item.points.size == 2) cs.lineTo(x0 + 0.01f, y0)
                    var i = 2
                    while (i + 1 < item.points.size) {
                        val (x, y) = geom.toPdf(item.points[i], item.points[i + 1])
                        cs.lineTo(x, y)
                        i += 2
                    }
                    cs.stroke()
                }
            }
            is Overlay.Shape -> drawShape(cs, geom, item)
            is Overlay.Text -> {
                val size = item.sizeFraction * geom.displayHeight
                if (Fonts.needsShaping(item.text)) {
                    // Arabic, Hebrew, Indic…: let Android shape it, then place it as an image.
                    val bmp = TextBitmap.render(item.text, item.color, size, bold = false)
                    val w = bmp.width / TextBitmap.SCALE
                    val h = bmp.height / TextBitmap.SCALE
                    val image = LosslessFactory.createFromImage(doc, bmp)
                    bmp.recycle()
                    val (ax, ay) = geom.toPdf(item.u, item.v + h / geom.displayHeight)
                    val m = Matrix.getRotateInstance(Math.toRadians(geom.rotation.toDouble()), ax, ay)
                    m.scale(w, h)
                    cs.drawImage(image, m)
                } else {
                    val font = Fonts.forText(doc, item.text, bold = false)
                    setFill(cs, item.color)
                    item.text.lines().forEachIndexed { i, line ->
                        if (line.isNotEmpty()) {
                            val v = item.v + (size * 1.2f * i + size) / geom.displayHeight
                            drawText(cs, font, geom, line, item.u, v, size, Align.START)
                        }
                    }
                }
            }
            is Overlay.Image -> {
                val image = LosslessFactory.createFromImage(doc, item.bitmap)
                val w = (item.u1 - item.u0) * geom.displayWidth
                val h = (item.v1 - item.v0) * geom.displayHeight
                val (ax, ay) = geom.toPdf(item.u0, item.v1)
                val m = Matrix.getRotateInstance(Math.toRadians(geom.rotation.toDouble()), ax, ay)
                m.scale(w, h)
                cs.drawImage(image, m)
                item.caption?.let { caption ->
                    val size = max(7f, h * 0.16f)
                    setFill(cs, 0x1A237E)
                    drawText(cs, PDType1Font.HELVETICA, geom, Fonts.winAnsiOnly(caption), item.u0, item.v1 + size * 1.1f / geom.displayHeight, size, Align.START)
                }
            }
        }
        cs.restoreGraphicsState()
    }

    private fun drawShape(cs: PDPageContentStream, geom: PageGeometry, s: Overlay.Shape) {
        val l = min(s.u0, s.u1); val r = max(s.u0, s.u1)
        val t = min(s.v0, s.v1); val b = max(s.v0, s.v1)
        val lw = s.widthFraction * geom.displayWidth
        fun path(vararg uv: Float) {
            val (x0, y0) = geom.toPdf(uv[0], uv[1]); cs.moveTo(x0, y0)
            var i = 2
            while (i < uv.size) { val (x, y) = geom.toPdf(uv[i], uv[i + 1]); cs.lineTo(x, y); i += 2 }
        }
        when (s.kind) {
            Overlay.Shape.Kind.HIGHLIGHT_BOX -> {
                cs.setGraphicsStateParameters(alpha(0.35f, multiply = true))
                setFill(cs, s.color)
                path(l, t, r, t, r, b, l, b); cs.closePath(); cs.fill()
            }
            Overlay.Shape.Kind.RECTANGLE -> {
                setStroke(cs, s.color); cs.setLineWidth(lw)
                path(l, t, r, t, r, b, l, b); cs.closePath(); cs.stroke()
            }
            Overlay.Shape.Kind.ELLIPSE -> {
                setStroke(cs, s.color); cs.setLineWidth(lw)
                val steps = 48
                val pts = FloatArray((steps + 1) * 2)
                for (k in 0..steps) {
                    val a = 2 * Math.PI * k / steps
                    pts[2 * k] = ((l + r) / 2 + (r - l) / 2 * Math.cos(a)).toFloat()
                    pts[2 * k + 1] = ((t + b) / 2 + (b - t) / 2 * Math.sin(a)).toFloat()
                }
                path(*pts); cs.stroke()
            }
            Overlay.Shape.Kind.LINE, Overlay.Shape.Kind.ARROW -> {
                setStroke(cs, s.color); cs.setLineWidth(lw); cs.setLineCapStyle(1)
                path(s.u0, s.v0, s.u1, s.v1); cs.stroke()
                if (s.kind == Overlay.Shape.Kind.ARROW) {
                    val (x0, y0) = geom.toPdf(s.u0, s.v0)
                    val (x1, y1) = geom.toPdf(s.u1, s.v1)
                    val angle = Math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())
                    val head = lw * 5
                    for (side in listOf(-1, 1)) {
                        val a = angle + Math.PI + side * Math.PI / 7
                        cs.moveTo(x1, y1)
                        cs.lineTo((x1 + head * Math.cos(a)).toFloat(), (y1 + head * Math.sin(a)).toFloat())
                    }
                    cs.stroke()
                }
            }
            Overlay.Shape.Kind.STRIKE -> {
                setStroke(cs, s.color); cs.setLineWidth(max(1f, (b - t) * geom.displayHeight * 0.08f))
                val mid = (t + b) / 2; path(l, mid, r, mid); cs.stroke()
            }
            Overlay.Shape.Kind.UNDERLINE -> {
                setStroke(cs, s.color); cs.setLineWidth(max(1f, (b - t) * geom.displayHeight * 0.08f))
                path(l, b, r, b); cs.stroke()
            }
        }
    }

    // ---------------------------------------------------------------- read

    fun outline(doc: PDDocument): List<OutlineEntry> {
        val result = mutableListOf<OutlineEntry>()
        val pages = doc.pages
        fun walk(node: PDOutlineNode, level: Int) {
            var item = node.firstChild
            while (item != null && result.size < 2000) {
                val page = runCatching { item.findDestinationPage(doc) }.getOrNull()
                val index = page?.let { pages.indexOf(it) } ?: -1
                result += OutlineEntry(item.title ?: "", index, level)
                walk(item, level + 1)
                item = item.nextSibling
            }
        }
        doc.documentCatalog.documentOutline?.let { walk(it, 0) }
        return result
    }

    fun pageText(doc: PDDocument, pageIndex: Int): String =
        PDFTextStripper().apply { startPage = pageIndex + 1; endPage = pageIndex + 1 }.getText(doc)

    /** All text, capped so very large files do not stall indexing. */
    fun documentText(file: File, maxPages: Int = 300, maxChars: Int = 400_000): String =
        open(file).use { doc ->
            val text = PDFTextStripper().apply { startPage = 1; endPage = min(doc.numberOfPages, maxPages) }.getText(doc)
            text.take(maxChars)
        }

    /** Finds [query] page by page, reporting hits as it goes. */
    fun search(doc: PDDocument, query: String, isActive: () -> Boolean, onHit: (SearchHit) -> Unit) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return
        for (i in 0 until doc.numberOfPages) {
            if (!isActive()) return
            val page = doc.getPage(i)
            val stripper = HitStripper().apply { startPage = i + 1; endPage = i + 1 }
            if (runCatching { stripper.getText(doc) }.isFailure) continue
            val hay = stripper.chars.toString().lowercase()
            var from = 0
            val rects = mutableListOf<NRect>()
            var snippet: String? = null
            while (true) {
                val at = hay.indexOf(needle, from)
                if (at < 0) break
                if (snippet == null) {
                    val s = max(0, at - 30)
                    val e = min(hay.length, at + needle.length + 30)
                    snippet = stripper.chars.substring(s, e).replace(Regex("\\s+"), " ").trim()
                }
                if (page.rotation == 0) stripper.rectFor(at, at + needle.length)?.let { rects += it }
                from = at + needle.length
            }
            if (snippet != null) onHit(SearchHit(i, snippet, rects))
        }
    }

    private class HitStripper : PDFTextStripper() {
        val chars = StringBuilder()
        private val positions = ArrayList<TextPosition?>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            for (tp in textPositions) {
                val u = tp.unicode ?: continue
                for (c in u) { chars.append(c); positions.add(tp) }
            }
            chars.append(' '); positions.add(null)
        }

        fun rectFor(start: Int, end: Int): NRect? {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -1f; var b = -1f
            var pw = 0f; var ph = 0f
            for (k in start until end) {
                val tp = positions.getOrNull(k) ?: continue
                pw = tp.pageWidth; ph = tp.pageHeight
                val h = tp.heightDir * 1.3f
                l = min(l, tp.xDirAdj); r = max(r, tp.xDirAdj + tp.widthDirAdj)
                t = min(t, tp.yDirAdj - h); b = max(b, tp.yDirAdj + h * 0.15f)
            }
            if (r < 0 || pw <= 0 || ph <= 0) return null
            return NRect(l / pw, t / ph, r / pw, b / ph)
        }
    }

    // ---------------------------------------------------------------- helpers

    private enum class Align { START, CENTER, END }

    private fun drawText(cs: PDPageContentStream, font: com.tom_roush.pdfbox.pdmodel.font.PDFont, geom: PageGeometry, text: String, u: Float, vBaseline: Float, size: Float, align: Align) {
        val width = font.getStringWidth(text) / 1000f * size
        val shiftU = when (align) {
            Align.START -> 0f
            Align.CENTER -> width / 2f / geom.displayWidth
            Align.END -> width / geom.displayWidth
        }
        val (x, y) = geom.toPdf(u - shiftU, vBaseline)
        cs.beginText()
        cs.setFont(font, size)
        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(geom.rotation.toDouble()), x, y))
        cs.showText(text)
        cs.endText()
    }

    private fun geometry(page: PDPage): PageGeometry {
        val box = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        return PageGeometry(box.lowerLeftX, box.lowerLeftY, box.width, box.height, if (rotation % 90 == 0) rotation else 0)
    }

    private fun pageIndices(doc: PDDocument, pages: List<Int>?): List<Int> =
        pages?.filter { it in 0 until doc.numberOfPages } ?: (0 until doc.numberOfPages).toList()

    private fun alpha(value: Float, multiply: Boolean = false) = PDExtendedGraphicsState().apply {
        strokingAlphaConstant = value
        nonStrokingAlphaConstant = value
        if (multiply) cosObject.setItem(COSName.BM, COSName.getPDFName("Multiply"))
    }

    private fun setStroke(cs: PDPageContentStream, argb: Int) =
        cs.setStrokingColor(Color.red(argb) / 255f, Color.green(argb) / 255f, Color.blue(argb) / 255f)

    private fun setFill(cs: PDPageContentStream, argb: Int) =
        cs.setNonStrokingColor(Color.red(argb) / 255f, Color.green(argb) / 255f, Color.blue(argb) / 255f)

    private object Color {
        fun alpha(c: Int) = ((c ushr 24) and 0xFF).let { if (it == 0) 1f else it / 255f }
        fun red(c: Int) = (c shr 16) and 0xFF
        fun green(c: Int) = (c shr 8) and 0xFF
        fun blue(c: Int) = c and 0xFF
    }
}
