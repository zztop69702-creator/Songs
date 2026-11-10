package kr.youngil.acousticvision.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.youngil.acousticvision.core.dsp.FanTone
import kr.youngil.acousticvision.core.dsp.SpectralPeak
import kr.youngil.acousticvision.core.model.BandRecord
import kr.youngil.acousticvision.core.model.MarkerSource
import kr.youngil.acousticvision.core.model.SourceMarker
import kotlin.math.hypot
import kotlin.math.ln

private val gridFreqs = listOf(31.5, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
private fun gridLabel(f: Double) = if (f >= 1000) "${(f / 1000).toInt()}k" else if (f == 31.5) "31.5" else f.toInt().toString()

/**
 * FFT 스펙트럼 (로그 주파수축, dBFS 상대값). 음압레벨이 아님을 축 제목에 명시.
 */
@Composable
fun SpectrumChart(
    freqs: FloatArray,
    db: FloatArray,
    nyquistHz: Double,
    peaks: List<SpectralPeak>,
    tones: List<FanTone>,
    modifier: Modifier = Modifier,
    dbMin: Float = -130f,
    dbMax: Float = 0f,
) {
    val tm = rememberTextMeasurer()
    val fMin = 20.0
    val fMax = minOf(20_000.0, nyquistHz)
    Canvas(modifier) {
        val left = 34.dp.toPx()
        val bottom = size.height - 16.dp.toPx()
        val top = 14.dp.toPx()
        val right = size.width - 4.dp.toPx()
        fun x(f: Double) = (left + (right - left) * ln(f / fMin) / ln(fMax / fMin)).toFloat()
        fun y(d: Float) = top + (bottom - top) * (dbMax - d) / (dbMax - dbMin)
        val small = TextStyle(color = AvColors.TextDim, fontSize = 9.sp)

        // 격자
        for (d in -120..0 step 20) {
            val yy = y(d.toFloat())
            drawLine(Color(0x33FFFFFF), Offset(left, yy), Offset(right, yy), 1f)
            label(tm, "$d", Offset(2f, yy - 6.dp.toPx()), small)
        }
        for (f in gridFreqs) if (f in fMin..fMax) {
            val xx = x(f)
            drawLine(Color(0x22FFFFFF), Offset(xx, top), Offset(xx, bottom), 1f)
            label(tm, gridLabel(f), Offset(xx - 6.dp.toPx(), bottom + 2.dp.toPx()), small)
        }
        label(tm, "dBFS (디지털 상대값, 음압레벨 아님)", Offset(left + 4.dp.toPx(), 0f), small)

        // 송풍기 회전/BPF 예상 주파수 (파란 점선)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        for (t in tones) if (t.frequencyHz in fMin..fMax) {
            val xx = x(t.frequencyHz)
            drawLine(AvColors.Blue, Offset(xx, top), Offset(xx, bottom), 2f, pathEffect = dash)
            label(tm, t.label, Offset(xx + 2f, top + 2f), TextStyle(color = AvColors.Blue, fontSize = 9.sp))
        }

        // 스펙트럼
        if (freqs.isNotEmpty()) {
            val path = Path()
            var started = false
            for (i in freqs.indices) {
                val f = freqs[i].toDouble()
                if (f < fMin || f > fMax) continue
                val p = Offset(x(f), y(db[i].coerceIn(dbMin, dbMax)))
                if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
            }
            drawPath(path, Color.White, style = Stroke(width = 1.5.dp.toPx()))
        }

        // 피크
        for (p in peaks) if (p.frequencyHz in fMin..fMax) {
            val c = Offset(x(p.frequencyHz), y(p.levelDbFs.toFloat().coerceIn(dbMin, dbMax)))
            drawCircle(AvColors.Yellow, 3.dp.toPx(), c)
            label(tm, Fmt.hz(p.frequencyHz), Offset(c.x + 3f, c.y - 14.dp.toPx()), TextStyle(color = AvColors.Yellow, fontSize = 9.sp))
        }
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    val safeX = at.x.coerceIn(0f, (size.width - 10f).coerceAtLeast(0f))
    val safeY = at.y.coerceIn(0f, (size.height - 10f).coerceAtLeast(0f))
    drawText(tm, text, Offset(safeX, safeY), style)
}

/** 1/3 옥타브 막대그래프 (FFT 합산 근사). 신뢰도 낮은 대역은 회색. */
@Composable
fun ThirdOctaveChart(bands: List<BandRecord>, offsetDb: Double?, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        if (bands.isEmpty()) return@Canvas
        val values = bands.map { it.levelADbFs + (offsetDb ?: 0.0) }
        val hi = (values.max() + 5).toFloat()
        val lo = hi - 80f
        val bottom = size.height - 14.dp.toPx()
        val w = size.width / bands.size
        bands.forEachIndexed { i, b ->
            val v = (values[i].toFloat()).coerceIn(lo, hi)
            val h = (bottom) * (v - lo) / (hi - lo)
            drawRect(
                if (b.reliable) AvColors.Blue else Color.Gray,
                topLeft = Offset(i * w + 1f, bottom - h),
                size = androidx.compose.ui.geometry.Size(w - 2f, h),
            )
            if (i % 3 == 0) label(tm, b.label, Offset(i * w, bottom + 1f), TextStyle(color = AvColors.TextDim, fontSize = 8.sp))
        }
    }
}

/**
 * 음원 마커 레이어. MANUAL=빨간 점선, AUTO=빨간 실선, EXPERT_EDITED=노란색. 라벨 텍스트로 출처 병기.
 * onTap이 null이면 표시 전용.
 */
@Composable
fun MarkerLayer(
    markers: List<SourceMarker>,
    selectedId: String?,
    modifier: Modifier = Modifier,
    onTap: ((x: Float, y: Float, hitId: String?) -> Unit)? = null,
) {
    val tm = rememberTextMeasurer()
    val base = modifier.fillMaxSize()
    val m = if (onTap != null) {
        base.pointerInput(markers) {
            detectTapGestures { pos ->
                val hitRadius = 28.dp.toPx()
                val hit = markers.minByOrNull { hypot(it.x * size.width - pos.x, it.y * size.height - pos.y) }
                    ?.takeIf { hypot(it.x * size.width - pos.x, it.y * size.height - pos.y) <= hitRadius }
                onTap((pos.x / size.width).coerceIn(0f, 1f), (pos.y / size.height).coerceIn(0f, 1f), hit?.id)
            }
        }
    } else base
    Canvas(m) {
        for (mk in markers) {
            val c = Offset(mk.x * size.width, mk.y * size.height)
            val color = if (mk.source == MarkerSource.EXPERT_EDITED) AvColors.Yellow else AvColors.Red
            val effect = if (mk.source == MarkerSource.MANUAL) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null
            val width = if (mk.id == selectedId) 5.dp.toPx() else 3.dp.toPx()
            val r = 24.dp.toPx()
            drawCircle(color, r, c, style = Stroke(width, pathEffect = effect))
            drawLine(color, Offset(c.x - 8.dp.toPx(), c.y), Offset(c.x + 8.dp.toPx(), c.y), 2f)
            drawLine(color, Offset(c.x, c.y - 8.dp.toPx()), Offset(c.x, c.y + 8.dp.toPx()), 2f)
            val srcText = when (mk.source) {
                MarkerSource.MANUAL -> "수동"
                MarkerSource.AUTO -> "자동"
                MarkerSource.EXPERT_EDITED -> "전문가수정"
            }
            label(
                tm, "${mk.label} ($srcText)", Offset(c.x - r, c.y + r + 2f),
                TextStyle(color = color, fontSize = 12.sp, background = Color(0x99000000)),
            )
        }
    }
}
