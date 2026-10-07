package com.pdfmaster.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders pages with the platform's PDFium-based [PdfRenderer]. Only one page is open
 * at a time and pages are rendered on demand, so even 1,000-page files open instantly
 * and are never loaded whole into memory.
 */
class PageRenderer(file: File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(pfd)
    } catch (e: Exception) {
        pfd.close()
        throw e
    }
    private val mutex = Mutex()
    private val sizes = arrayOfNulls<IntArray>(renderer.pageCount)
    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    @Volatile private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Page size in points as displayed (rotation applied). */
    suspend fun pageSize(index: Int): IntArray = sizes[index] ?: withContext(Dispatchers.IO) {
        mutex.withLock {
            sizes[index] ?: renderer.openPage(index).use { intArrayOf(it.width, it.height) }.also { sizes[index] = it }
        }
    }

    fun cachedSize(index: Int): IntArray? = sizes.getOrNull(index)

    fun cached(index: Int, widthPx: Int): Bitmap? = cache.get("$index@$widthPx")?.takeIf { !it.isRecycled }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        cached(index, widthPx)?.let { return@withContext it }
        mutex.withLock {
            if (closed) return@withLock null
            renderer.openPage(index).use { page ->
                sizes[index] = intArrayOf(page.width, page.height)
                val w = min(widthPx, MAX_WIDTH).coerceAtLeast(1)
                var h = (w.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                var width = w
                if (width.toLong() * h > MAX_PIXELS) {
                    val s = Math.sqrt(MAX_PIXELS.toDouble() / (width.toLong() * h))
                    width = (width * s).toInt(); h = (h * s).toInt()
                }
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put("$index@$widthPx", bmp)
                bmp
            }
        }
    }

    override fun close() {
        closed = true
        // Wait for any in-flight render before closing the native document.
        kotlinx.coroutines.runBlocking { mutex.withLock { runCatching { renderer.close() }; runCatching { pfd.close() } } }
        cache.evictAll()
    }

    private companion object {
        const val MAX_WIDTH = 2400
        const val MAX_PIXELS = 2400L * 3400L
        fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 6).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
