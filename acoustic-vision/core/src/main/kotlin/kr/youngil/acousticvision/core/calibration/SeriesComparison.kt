package kr.youngil.acousticvision.core.calibration

import kotlinx.serialization.Serializable
import kr.youngil.acousticvision.core.dsp.DbFs
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 비적분형(순간값) 소음계용 "영상 연속판독 비교" (예: TES-1350A — LAeq 기능 없음).
 *
 * 원리: 소음계 화면을 촬영하며 일정 간격으로 OCR → (시각, 표시값) 시계열.
 * 앱은 같은 시간가중(Slow τ=1 s 또는 Fast τ=0.125 s)으로 계산한 레벨을 같은 시각에서 읽어 1:1 비교한다.
 * 오프셋 = median(소음계값 − 앱값). 순간값의 산술평균을 LAeq로 간주하지 않는다 (V2 §4).
 * 소음계 표시 지연은 0 ~ maxLag 범위에서 잔차가 최소가 되는 값으로 추정한다.
 */

@Serializable
enum class TimeWeighting(val tauSec: Double, val label: String) { SLOW(1.0, "S (1 s)"), FAST(0.125, "F (125 ms)") }

/** 소음계 화면 판독 1건. tSec = 비교 구간 시작 기준 시각(앱 시계). */
@Serializable
data class MeterSample(
    val tSec: Double,
    val valueDb: Double?,
    val raw: String,
    val uncertain: Boolean,
    val confidence: Float? = null,
    val frameFile: String? = null,
    /** 사용자가 검토 화면에서 제외한 경우 */
    val excludedByUser: Boolean = false,
) {
    val usable: Boolean get() = valueDb != null && !uncertain && !excludedByUser
}

@Serializable
data class SeriesResult(
    val weighting: TimeWeighting,
    val totalSamples: Int,
    val usedSamples: Int,
    val bestLagSec: Double,
    /** 소음계 표시 변화가 작아 지연 추정이 불확실한지 */
    val lagUncertain: Boolean,
    val offsetDb: Double,
    val residualStdDb: Double,
    val residualMadDb: Double,
    val meterRangeDb: Double,
    /** 참고: 판독값 에너지 평균 (LAeq 아님) */
    val meterEnergyMeanDb: Double?,
    val appMedianDbFs: Double,
    val pairs: List<SeriesPair>,
)

@Serializable
data class SeriesPair(val tSec: Double, val meterDb: Double, val appDbFs: Double)

object SeriesComparator {
    /** 100 ms 블록 평균제곱에 지수 시간가중 적용 → 블록 끝 시점의 레벨(dBFS) */
    fun timeWeighted(blocks: List<Double>, w: TimeWeighting): DoubleArray {
        val a = 1.0 - exp(-WindowAnalyzer.BLOCK_SEC / w.tauSec)
        val out = DoubleArray(blocks.size)
        var e = blocks.firstOrNull() ?: 0.0
        for (i in blocks.indices) {
            e += a * (blocks[i] - e)
            out[i] = DbFs.fromMeanSquare(e)
        }
        return out
    }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun std(v: List<Double>): Double {
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
    }

    /**
     * @param levels timeWeighted() 결과 (세션 전체)
     * @param startBlock 비교 구간 시작 블록 (tSec=0에 해당)
     */
    fun compare(
        levels: DoubleArray,
        startBlock: Int,
        samples: List<MeterSample>,
        weighting: TimeWeighting,
        maxLagSec: Double = 1.5,
        /** 오디오 버퍼 지연 때문에 추정 지연이 약간 음수가 될 수 있음 */
        minLagSec: Double = -0.3,
    ): SeriesResult? {
        val usable = samples.filter { it.usable }
        if (usable.isEmpty()) return null
        fun appAt(t: Double): Double? {
            val idx = startBlock + (t / WindowAnalyzer.BLOCK_SEC).roundToInt() - 1
            return if (idx in levels.indices) levels[idx] else null
        }
        var best: Pair<Double, List<SeriesPair>>? = null
        var bestStd = Double.MAX_VALUE
        var lag = minLagSec
        while (lag <= maxLagSec + 1e-9) {
            val pairs = usable.mapNotNull { s -> appAt(s.tSec - lag)?.let { SeriesPair(s.tSec, s.valueDb!!, it) } }
            if (pairs.size >= 3) {
                val sd = std(pairs.map { it.meterDb - it.appDbFs })
                if (sd < bestStd - 1e-6) { bestStd = sd; best = lag to pairs }
            }
            lag = ((lag + 0.1) * 10).roundToInt() / 10.0
        }
        val (bestLag, pairs) = best ?: return null
        val diffs = pairs.map { it.meterDb - it.appDbFs }
        val off = median(diffs)
        val mad = median(diffs.map { abs(it - off) }) * 1.4826
        val meterVals = pairs.map { it.meterDb }
        val range = meterVals.max() - meterVals.min()
        val energy = 10 * log10(meterVals.map { 10.0.pow(it / 10) }.average())
        return SeriesResult(
            weighting = weighting,
            totalSamples = samples.size,
            usedSamples = pairs.size,
            bestLagSec = bestLag,
            lagUncertain = range < 2.0,
            offsetDb = off,
            residualStdDb = bestStd,
            residualMadDb = mad,
            meterRangeDb = range,
            meterEnergyMeanDb = energy,
            appMedianDbFs = median(pairs.map { it.appDbFs }),
            pairs = pairs,
        )
    }

    /** 15 s → 30 s 재측정 판정 (잠정 기준) */
    fun decide(r: SeriesResult?, intervalSec: Double, t: CalibrationThresholds = CalibrationThresholds()): WindowDecision {
        val ok = r != null &&
            r.usedSamples >= t.seriesMinSamples &&
            r.usedSamples >= r.totalSamples * t.seriesMinValidRatio &&
            r.residualStdDb <= t.seriesMaxResidualStdDb
        return when {
            ok -> WindowDecision.OK
            intervalSec < 30.0 - 1e-6 -> WindowDecision.EXTEND_TO_30S
            else -> WindowDecision.REMEASURE
        }
    }

    /** 검증: 검증 구간에서 필요한 오프셋과 보정 오프셋의 차이 */
    fun validationError(calibrationOffsetDb: Double, validation: SeriesResult): Double = calibrationOffsetDb - validation.offsetDb
}
