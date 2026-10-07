package com.pdfmaster.ui.viewer

import android.graphics.Bitmap
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.pdfmaster.data.ReadingMode
import com.pdfmaster.pdf.NRect
import com.pdfmaster.pdf.Overlay
import com.pdfmaster.pdf.PageRenderer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val NightMatrix = ColorMatrix(
    floatArrayOf(
        -0.9f, 0f, 0f, 0f, 235f,
        0f, -0.9f, 0f, 0f, 235f,
        0f, 0f, -0.9f, 0f, 235f,
        0f, 0f, 0f, 1f, 0f,
    )
)
private val SepiaMatrix = ColorMatrix(
    floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
)

@Composable
fun PageView(
    vm: ViewerViewModel,
    renderer: PageRenderer,
    page: Int,
    renderWidthPx: Int,
    label: String,
    onTextRequest: (page: Int, u: Float, v: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = renderer.cachedSize(page) ?: renderer.cachedSize(0)
    val aspect = size?.let { it[0].toFloat() / it[1] } ?: (1f / 1.414f)
    val bitmap by produceState<Bitmap?>(renderer.cached(page, renderWidthPx), page, renderWidthPx, renderer) {
        value = renderer.render(page, renderWidthPx)
    }
    val items = vm.overlays[page].orEmpty()
    val hitRects = vm.hits.firstOrNull { it.pageIndex == page }?.rects.orEmpty()
    var live by remember { mutableStateOf<Overlay?>(null) }

    val filter = when (vm.readingMode) {
        ReadingMode.NORMAL -> null
        ReadingMode.NIGHT -> ColorFilter.colorMatrix(NightMatrix)
        ReadingMode.SEPIA -> ColorFilter.colorMatrix(SepiaMatrix)
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(if (vm.readingMode == ReadingMode.NIGHT) Color(0xFF1E1E1E) else Color.White)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, colorFilter = filter)
        } else {
            CircularProgressIndicator(Modifier.aspectRatio(1f).fillMaxWidth(0.1f))
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .then(Modifier.pageGestures(vm, page, aspect, onTextRequest) { live = it }),
        ) {
            hitRects.forEach { r -> drawHit(r) }
            items.forEach { drawOverlay(it, selected = it.id == vm.selectedOverlayId) }
            live?.let { drawOverlay(it, selected = false) }
        }
    }
}

private fun DrawScope.drawHit(r: NRect) {
    drawRect(
        Color(0x66FFEB3B),
        topLeft = Offset(r.left * size.width, r.top * size.height),
        size = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height),
        blendMode = BlendMode.Multiply,
    )
}

fun DrawScope.drawOverlay(item: Overlay, selected: Boolean) {
    val w = size.width
    val h = size.height
    when (item) {
        is Overlay.Ink -> {
            val path = Path()
            item.points.chunked(2).forEachIndexed { i, (u, v) ->
                if (i == 0) path.moveTo(u * w, v * h) else path.lineTo(u * w, v * h)
            }
            if (item.points.size == 2) path.lineTo(item.points[0] * w + 0.5f, item.points[1] * h)
            drawPath(
                path,
                Color(item.color).copy(alpha = if (item.highlighter) 0.35f else 1f),
                style = Stroke(width = item.widthFraction * w, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = if (item.highlighter) BlendMode.Multiply else BlendMode.SrcOver,
            )
        }
        is Overlay.Shape -> {
            val l = min(item.u0, item.u1) * w; val r = max(item.u0, item.u1) * w
            val t = min(item.v0, item.v1) * h; val b = max(item.v0, item.v1) * h
            val c = Color(item.color)
            val stroke = Stroke(width = item.widthFraction * w, cap = StrokeCap.Round)
            when (item.kind) {
                Overlay.Shape.Kind.HIGHLIGHT_BOX -> drawRect(c.copy(alpha = 0.35f), Offset(l, t), Size(r - l, b - t), blendMode = BlendMode.Multiply)
                Overlay.Shape.Kind.RECTANGLE -> drawRect(c, Offset(l, t), Size(r - l, b - t), style = stroke)
                Overlay.Shape.Kind.ELLIPSE -> drawOval(c, Offset(l, t), Size(r - l, b - t), style = stroke)
                Overlay.Shape.Kind.LINE -> drawLine(c, Offset(item.u0 * w, item.v0 * h), Offset(item.u1 * w, item.v1 * h), stroke.width, StrokeCap.Round)
                Overlay.Shape.Kind.ARROW -> {
                    val a = Offset(item.u0 * w, item.v0 * h); val bb = Offset(item.u1 * w, item.v1 * h)
                    drawLine(c, a, bb, stroke.width, StrokeCap.Round)
                    val angle = Math.atan2((bb.y - a.y).toDouble(), (bb.x - a.x).toDouble())
                    val head = stroke.width * 5
                    for (side in listOf(-1, 1)) {
                        val ang = angle + Math.PI + side * Math.PI / 7
                        drawLine(c, bb, Offset((bb.x + head * Math.cos(ang)).toFloat(), (bb.y + head * Math.sin(ang)).toFloat()), stroke.width, StrokeCap.Round)
                    }
                }
                Overlay.Shape.Kind.STRIKE -> drawLine(c, Offset(l, (t + b) / 2), Offset(r, (t + b) / 2), max(1f, (b - t) * 0.08f))
                Overlay.Shape.Kind.UNDERLINE -> drawLine(c, Offset(l, b), Offset(r, b), max(1f, (b - t) * 0.08f))
            }
        }
        is Overlay.Text -> drawIntoCanvas { canvas ->
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = item.color or (0xFF shl 24)
                textSize = item.sizeFraction * h
            }
            val width = item.text.lines().maxOf { paint.measureText(it) }.toInt().coerceAtLeast(1) + 2
            val layout = StaticLayout.Builder.obtain(item.text, 0, item.text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.translate(item.u * w, item.v * h)
            layout.draw(canvas.nativeCanvas)
            canvas.nativeCanvas.restore()
            if (selected) drawRect(Color(0xFF1E88E5), Offset(item.u * w, item.v * h), Size(width.toFloat(), layout.height.toFloat()), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
        }
        is Overlay.Image -> {
            val tl = Offset(item.u0 * w, item.v0 * h)
            val sz = IntSize(((item.u1 - item.u0) * w).toInt().coerceAtLeast(1), ((item.v1 - item.v0) * h).toInt().coerceAtLeast(1))
            drawImage(item.bitmap.asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(tl.x.toInt(), tl.y.toInt()), dstSize = sz)
            item.caption?.let { caption ->
                drawIntoCanvas { canvas ->
                    val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A237E.toInt(); textSize = max(10f, sz.height * 0.16f) }
                    canvas.nativeCanvas.drawText(caption, tl.x, tl.y + sz.height + paint.textSize * 1.1f, paint)
                }
            }
            if (selected) drawRect(Color(0xFF1E88E5), tl, Size(sz.width.toFloat(), sz.height.toFloat()), style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
        }
    }
}

/** Approximate bounds of an overlay in normalised page coordinates, for hit testing. */
private fun Overlay.bounds(): NRect = when (this) {
    is Overlay.Ink -> {
        val xs = points.filterIndexed { i, _ -> i % 2 == 0 }; val ys = points.filterIndexed { i, _ -> i % 2 == 1 }
        val pad = widthFraction
        NRect(xs.min() - pad, ys.min() - pad, xs.max() + pad, ys.max() + pad)
    }
    is Overlay.Shape -> NRect(min(u0, u1) - 0.01f, min(v0, v1) - 0.01f, max(u0, u1) + 0.01f, max(v0, v1) + 0.01f)
    // Width is approximate: ~0.6 em per character on an A4-shaped page.
    is Overlay.Text -> NRect(u, v, u + sizeFraction * 0.6f * 1.41f * text.lines().maxOf { it.length }, v + sizeFraction * 1.3f * text.lines().size)
    is Overlay.Image -> NRect(u0, v0, u1, v1)
}

private fun NRect.contains(u: Float, v: Float) = u in left..right && v in top..bottom

private fun Modifier.pageGestures(
    vm: ViewerViewModel,
    page: Int,
    aspect: Float,
    onTextRequest: (Int, Float, Float) -> Unit,
    setLive: (Overlay?) -> Unit,
): Modifier = when (vm.mode) {
    ViewerMode.READ -> this
    ViewerMode.ANNOTATE -> if (vm.tool == AnnotTool.HAND) this else pointerInput(vm.tool, vm.color, vm.strokeWidth, page) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val w = size.width.toFloat(); val h = size.height.toFloat()
            val u0 = down.position.x / w; val v0 = down.position.y / h
            when (vm.tool) {
                AnnotTool.PEN, AnnotTool.HIGHLIGHTER -> {
                    val pts = mutableListOf(u0, v0)
                    val highlighter = vm.tool == AnnotTool.HIGHLIGHTER
                    val width = if (highlighter) vm.strokeWidth * 4f else vm.strokeWidth
                    val color = vm.color
                    down.consume()
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) { cancelled = true; break } // second finger: it's a zoom
                        val change = event.changes.first()
                        if (!change.pressed) break
                        pts += change.position.x / w; pts += change.position.y / h
                        change.consume()
                        setLive(Overlay.Ink(-1, pts.toList(), color, width, highlighter))
                    }
                    setLive(null)
                    if (!cancelled) vm.add(page, Overlay.Ink(vm.newId(), smooth(pts), color, width, highlighter))
                }
                AnnotTool.HIGHLIGHT_BOX, AnnotTool.UNDERLINE, AnnotTool.STRIKE, AnnotTool.RECTANGLE, AnnotTool.ELLIPSE, AnnotTool.ARROW -> {
                    val kind = when (vm.tool) {
                        AnnotTool.HIGHLIGHT_BOX -> Overlay.Shape.Kind.HIGHLIGHT_BOX
                        AnnotTool.UNDERLINE -> Overlay.Shape.Kind.UNDERLINE
                        AnnotTool.STRIKE -> Overlay.Shape.Kind.STRIKE
                        AnnotTool.RECTANGLE -> Overlay.Shape.Kind.RECTANGLE
                        AnnotTool.ELLIPSE -> Overlay.Shape.Kind.ELLIPSE
                        else -> Overlay.Shape.Kind.ARROW
                    }
                    val color = vm.color
                    down.consume()
                    var u1 = u0; var v1 = v0
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) { cancelled = true; break }
                        val change = event.changes.first()
                        if (!change.pressed) break
                        u1 = (change.position.x / w).coerceIn(0f, 1f); v1 = (change.position.y / h).coerceIn(0f, 1f)
                        change.consume()
                        setLive(Overlay.Shape(-1, kind, u0, v0, u1, v1, color, vm.strokeWidth))
                    }
                    setLive(null)
                    if (!cancelled && (abs(u1 - u0) > 0.01f || abs(v1 - v0) > 0.005f)) {
                        vm.add(page, Overlay.Shape(vm.newId(), kind, u0, v0, u1, v1, color, vm.strokeWidth))
                    }
                }
                AnnotTool.TEXT -> {
                    val up = waitForTap(down)
                    if (up) onTextRequest(page, u0, v0)
                }
                AnnotTool.ERASER -> {
                    down.consume()
                    vm.overlays[page].orEmpty().lastOrNull { it.bounds().contains(u0, v0) }?.let { vm.removeAt(page, it.id) }
                }
                AnnotTool.HAND -> Unit
            }
        }
    }
    ViewerMode.SIGN -> pointerInput(vm.selectedSignature, page, vm.dateStamp) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val w = size.width.toFloat(); val h = size.height.toFloat()
            val u0 = down.position.x / w; val v0 = down.position.y / h
            val hit = vm.overlays[page].orEmpty().lastOrNull { it is Overlay.Image && it.bounds().contains(u0, v0) } as Overlay.Image?
            if (hit != null) {
                // Drag a placed signature.
                vm.selectedOverlayId = hit.id
                down.consume()
                var current: Overlay.Image = hit
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val d = change.positionChange()
                    val du = d.x / w; val dv = d.y / h
                    val bw = current.u1 - current.u0; val bh = current.v1 - current.v0
                    val nu = (current.u0 + du).coerceIn(0f, 1f - bw); val nv = (current.v0 + dv).coerceIn(0f, 1f - bh)
                    current = current.copy(u0 = nu, v0 = nv, u1 = nu + bw, v1 = nv + bh)
                    vm.replaceLive(page, current)
                    change.consume()
                }
                if (current != hit) vm.replace(page, hit, current)
            } else if (waitForTap(down)) {
                if (vm.selectedSignature != null) vm.placeSignature(page, u0, v0, aspect) else vm.selectedOverlayId = null
            }
        }
    }
}

/** Waits for the pointer to lift; true if it was a tap (no drag), without consuming scrolls. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForTap(down: PointerInputChange): Boolean {
    var moved = 0f
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return false
        if (event.changes.size > 1) return false
        moved += change.positionChange().getDistance()
        if (moved > viewConfiguration.touchSlop) return false
        if (!change.pressed) return true
    }
}

/** Light smoothing and decimation so strokes look natural and stay small in the PDF. */
private fun smooth(points: List<Float>): List<Float> {
    if (points.size < 8) return points
    val out = ArrayList<Float>(points.size)
    out += points[0]; out += points[1]
    var i = 2
    while (i + 3 < points.size) {
        out += (points[i - 2] + points[i] * 2 + points[i + 2]) / 4f
        out += (points[i - 1] + points[i + 1] * 2 + points[i + 3]) / 4f
        i += 2
    }
    out += points[points.size - 2]; out += points[points.size - 1]
    // Drop points closer than 0.1% of the page to their predecessor.
    val dec = ArrayList<Float>(out.size)
    dec += out[0]; dec += out[1]
    var k = 2
    while (k + 1 < out.size) {
        val dx = out[k] - dec[dec.size - 2]; val dy = out[k + 1] - dec[dec.size - 1]
        if (dx * dx + dy * dy > 1e-6f || k + 2 >= out.size) { dec += out[k]; dec += out[k + 1] }
        k += 2
    }
    return dec
}
