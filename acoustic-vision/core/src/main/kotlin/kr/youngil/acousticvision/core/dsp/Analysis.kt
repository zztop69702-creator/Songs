package kr.youngil.acousticvision.core.dsp

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class SpectralPeak(val frequencyHz: Double, val levelDbFs: Double, val bin: Int)

/** 스펙트럼 피크 검출: 국부 최대 + 주변 중앙값 대비 돌출량 + 포물선 보간. */
object PeakDetector {
    fun find(
        spectrum: Spectrum,
        minHz: Double = 20.0,
        maxHz: Double = spectrum.sampleRate / 2.0,
        maxPeaks: Int = 8,
        minProminenceDb: Double = 10.0,
        floorDbFs: Double = -110.0,
        neighborhoodBins: Int = 16,
    ): List<SpectralPeak> {
        val db = spectrum.amplitudeDbFs
        val lo = max(2, (minHz / spectrum.binHz).toInt())
        val hi = minOf(db.size - 3, (maxHz / spectrum.binHz).toInt())
        val candidates = ArrayList<SpectralPeak>()
        for (k in lo..hi) {
            val b = db[k]
            if (b < floorDbFs || b <= db[k - 1] || b < db[k + 1]) continue
            val from = max(1, k - neighborhoodBins)
            val to = minOf(db.size - 1, k + neighborhoodBins)
            val window = (from..to).map { db[it] }.sorted()
            val median = window[window.size / 2]
            if (b - median < minProminenceDb) continue
            val a = db[k - 1]
            val c = db[k + 1]
            val denom = a - 2 * b + c
            val delta = if (denom != 0.0) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0
            val level = b - 0.25 * (a - c) * delta
            candidates += SpectralPeak((k + delta) * spectrum.binHz, level, k)
        }
        val result = ArrayList<SpectralPeak>()
        for (p in candidates.sortedByDescending { it.levelDbFs }) {
            if (result.none { abs(it.bin - p.bin) < 3 }) result += p
            if (result.size >= maxPeaks) break
        }
        return result
    }
}

data class ThirdOctaveBand(
    val nominalLabel: String,
    val centerHz: Double,
    val lowerHz: Double,
    val upperHz: Double,
    val levelZDbFs: Double,
    val levelADbFs: Double,
    val binCount: Int,
    /** FFT bin이 3개 미만이면 대역 레벨 신뢰도 낮음 (저주파 대역에서 흔함) */
    val reliable: Boolean,
)

/**
 * FFT bin 합산 방식의 1/3 옥타브 근사 (base-10, IEC 61260 필터뱅크 아님).
 */
object ThirdOctave {
    private val nominal = listOf(
        "20", "25", "31.5", "40", "50", "63", "80", "100", "125", "160", "200", "250", "315", "400",
        "500", "630", "800", "1k", "1.25k", "1.6k", "2k", "2.5k", "3.15k", "4k", "5k", "6.3k", "8k",
        "10k", "12.5k", "16k", "20k",
    )

    fun bands(spectrum: Spectrum): List<ThirdOctaveBand> {
        val nyquist = spectrum.sampleRate / 2.0
        return nominal.mapIndexedNotNull { i, label ->
            val n = i - 17
            val fc = 1000.0 * 10.0.pow(n / 10.0)
            val lo = fc * 10.0.pow(-1.0 / 20.0)
            val hi = fc * 10.0.pow(1.0 / 20.0)
            if (hi > nyquist) return@mapIndexedNotNull null
            val (z, count) = spectrum.bandMeanSquare(lo, hi)
            val (a, _) = spectrum.bandMeanSquare(lo, hi, AWeighting::gainDb)
            ThirdOctaveBand(label, fc, lo, hi, DbFs.fromMeanSquare(z), DbFs.fromMeanSquare(a), count, count >= 3)
        }
    }
}

data class FanTone(val label: String, val frequencyHz: Double)
data class ToneMatchResult(val tone: FanTone, val peak: SpectralPeak?, val deltaHz: Double?)

/**
 * 회전주파수 f = N/60, 날개통과주파수 f_BPF = N×Z/60 및 고조파.
 * 명판 rpm은 실제 운전 rpm(슬립, 인버터, 벨트비)과 다를 수 있으므로 매칭 결과는 "후보"로만 표시한다.
 */
object FanFrequencies {
    fun shaftHz(rpm: Double): Double = rpm / 60.0
    fun bladePassHz(rpm: Double, blades: Int): Double = rpm * blades / 60.0

    fun tones(rpm: Double, blades: Int?, harmonics: Int = 3): List<FanTone> {
        require(rpm > 0) { "rpm은 0보다 커야 합니다" }
        val list = ArrayList<FanTone>()
        val f0 = shaftHz(rpm)
        for (h in 1..2) list += FanTone(if (h == 1) "회전 1X" else "회전 ${h}X", f0 * h)
        if (blades != null && blades > 0) {
            val bpf = bladePassHz(rpm, blades)
            for (h in 1..harmonics) list += FanTone(if (h == 1) "BPF" else "BPF ${h}X", bpf * h)
        }
        return list
    }

    /** 허용오차: max(2 bin, 3 %) 이내의 가장 가까운 피크. */
    fun match(tones: List<FanTone>, peaks: List<SpectralPeak>, binHz: Double): List<ToneMatchResult> =
        tones.map { tone ->
            val tol = max(2 * binHz, 0.03 * tone.frequencyHz)
            val best = peaks.filter { abs(it.frequencyHz - tone.frequencyHz) <= tol }
                .minByOrNull { abs(it.frequencyHz - tone.frequencyHz) }
            ToneMatchResult(tone, best, best?.let { ((it.frequencyHz - tone.frequencyHz) * 10).roundToInt() / 10.0 })
        }
}
