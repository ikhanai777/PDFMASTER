package com.pdfmaster.ui.me

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdfmaster.R
import com.pdfmaster.ui.LocalActions
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.common.ScreenScaffold
import com.pdfmaster.ui.tools.decodeScaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private val inks = listOf(0xFF0D1B6E.toInt(), 0xFF000000.toInt(), 0xFF1565C0.toInt())

@Composable
fun SignaturesScreen() {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by container.signatures.items.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var initials by remember { mutableStateOf(false) }
    var ink by remember { mutableIntStateOf(inks[0]) }

    // Draw tab state: strokes as lists of points in pad pixels.
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    var typed by remember { mutableStateOf("") }
    var typeStyle by remember { mutableIntStateOf(0) }

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            photo = withContext(Dispatchers.Default) { runCatching { cleanPhotoSignature(decodeScaled(context, uri, 1600), ink) }.getOrNull() }
        }
    }

    fun saveBitmap(bmp: Bitmap?) {
        bmp ?: return
        scope.launch {
            container.signatures.save(bmp, initials)
            strokes.clear(); typed = ""; photo = null
        }
    }

    ScreenScaffold(title = stringResource(R.string.signatures), onBack = actions::back) { modifier ->
        Column(modifier.fillMaxSize().navigationBarsPadding()) {
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.sig_draw)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.sig_type)) })
                Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.sig_photo)) })
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(!initials, { initials = false }, label = { Text(stringResource(R.string.signature)) })
                    FilterChip(initials, { initials = true }, label = { Text(stringResource(R.string.initials)) })
                    inks.forEach { c ->
                        Box(
                            Modifier.size(26.dp).background(Color(c), RoundedCornerShape(13.dp))
                                .border(if (ink == c) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(13.dp))
                                .pointerInput(c) { awaitEachGesture { awaitFirstDown(); ink = c } },
                        )
                    }
                }
                when (tab) {
                    0 -> {
                        androidx.compose.foundation.Canvas(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .pointerInput(Unit) {
                                    padSize = size
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        down.consume()
                                        val pts = mutableListOf(down.position)
                                        while (true) {
                                            val e = awaitPointerEvent()
                                            val ch = e.changes.first()
                                            if (!ch.pressed) break
                                            pts += ch.position; ch.consume()
                                            current = pts.toList()
                                        }
                                        strokes += pts.toList(); current = emptyList()
                                    }
                                },
                        ) {
                            (strokes + listOf(current)).filter { it.isNotEmpty() }.forEach { s ->
                                val path = Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }
                                drawPath(path, Color(ink), style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { strokes.clear() }) { Text(stringResource(R.string.clear)) }
                            Button(enabled = strokes.isNotEmpty(), onClick = { saveBitmap(renderStrokes(strokes, padSize, ink)) }) { Text(stringResource(R.string.save)) }
                        }
                    }
                    1 -> {
                        OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.type_your_name)) }, singleLine = true)
                        val families = listOf(FontFamily.Cursive, FontFamily.Serif, FontFamily.SansSerif)
                        families.forEachIndexed { i, family ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(typeStyle == i, { typeStyle = i })
                                Text(typed.ifBlank { "Your Name" }, fontFamily = family, fontStyle = if (i == 1) FontStyle.Italic else FontStyle.Normal, fontSize = 28.sp, color = Color(ink))
                            }
                        }
                        Button(enabled = typed.isNotBlank(), onClick = { saveBitmap(renderTyped(typed, typeStyle, ink)) }) { Text(stringResource(R.string.save)) }
                    }
                    else -> {
                        Text(stringResource(R.string.photo_signature_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text(stringResource(R.string.choose_photo))
                        }
                        photo?.let {
                            Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(140.dp).background(Color.White))
                            Button(onClick = { saveBitmap(it) }) { Text(stringResource(R.string.save)) }
                        }
                    }
                }
                Text(stringResource(R.string.saved_signatures), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(saved, key = { it.id }) { sig ->
                    val bmp by produceState<Bitmap?>(null, sig.id) { value = container.signatures.load(sig) }
                    ListItem(
                        leadingContent = {
                            Box(Modifier.size(width = 120.dp, height = 56.dp).background(Color.White, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                bmp?.let { Image(it.asImageBitmap(), null, Modifier.padding(4.dp)) }
                            }
                        },
                        headlineContent = { Text(stringResource(if (sig.isInitials) R.string.initials else R.string.signature)) },
                        trailingContent = { IconButton(onClick = { scope.launch { container.signatures.delete(sig) } }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
                    )
                }
            }
        }
    }
}

/** Renders drawn strokes to a transparent bitmap cropped to the ink. */
private fun renderStrokes(strokes: List<List<Offset>>, size: IntSize, color: Int): Bitmap? {
    val pts = strokes.flatten()
    if (pts.isEmpty() || size.width == 0) return null
    val pad = 8f
    val l = max(0f, pts.minOf { it.x } - pad); val t = max(0f, pts.minOf { it.y } - pad)
    val r = min(size.width.toFloat(), pts.maxOf { it.x } + pad); val b = min(size.height.toFloat(), pts.maxOf { it.y } + pad)
    val scale = 2f
    val bmp = Bitmap.createBitmap(((r - l) * scale).toInt().coerceAtLeast(1), ((b - t) * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 6f * scale; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    strokes.filter { it.isNotEmpty() }.forEach { s ->
        val path = android.graphics.Path().apply {
            moveTo((s[0].x - l) * scale, (s[0].y - t) * scale)
            s.drop(1).forEach { lineTo((it.x - l) * scale, (it.y - t) * scale) }
            if (s.size == 1) lineTo((s[0].x - l) * scale + 1, (s[0].y - t) * scale)
        }
        canvas.drawPath(path, paint)
    }
    return bmp
}

private fun renderTyped(text: String, style: Int, color: Int): Bitmap {
    val typeface = when (style) {
        0 -> Typeface.create("cursive", Typeface.NORMAL)
        1 -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        else -> Typeface.SANS_SERIF
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = 120f; this.typeface = typeface }
    val w = paint.measureText(text).toInt() + 24
    val fm = paint.fontMetrics
    val h = (fm.descent - fm.ascent).toInt() + 16
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).drawText(text, 12f, -fm.ascent + 8, paint) }
}

/** Turns a photo of a signature on paper into ink-coloured strokes on transparency. */
private fun cleanPhotoSignature(src: Bitmap, ink: Int): Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (i in px.indices) {
        val p = px[i]
        val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        val alpha = ((150 - lum) * 255 / 70).coerceIn(0, 255)
        px[i] = if (alpha < 30) 0 else (alpha shl 24) or (ink and 0xFFFFFF)
        if (alpha >= 30) {
            val x = i % w; val y = i / w
            if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
        }
    }
    src.recycle()
    val full = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    if (maxX < 0) return full
    return Bitmap.createBitmap(full, minX, minY, maxX - minX + 1, maxY - minY + 1)
}
