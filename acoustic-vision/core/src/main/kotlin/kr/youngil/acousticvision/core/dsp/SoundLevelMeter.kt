package kr.youngil.acousticvision.core.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * 소음도 계산기 (디지털 상대값 dBFS 기준).
 * - LAF: A 가중 + Fast 시간가중(τ = 125 ms) 지수 평균
 * - LAeq: 측정 시작 이후 A 가중 에너지 평균
 * - LAFmax / LAFmin: LAF의 최대/최소 (최소는 초기 안정화 시간 이후부터)
 * - LZeq: 무가중 에너지 평균
 * 음압레벨(dB SPL)로의 환산은 교정 오프셋을 별도로 더해야 하며 이 클래스는 수행하지 않는다.
 */
class SoundLevelMeter(val sampleRate: Int, timeConstantSec: Double = FAST_TAU_SEC, private val settleSec: Double = 0.5) {
    companion object {
        const val FAST_TAU_SEC = 0.125
        const val CLIP_THRESHOLD = 0.999f
    }

    private val filter = AWeightingFilter(sampleRate)
    private val alpha = 1.0 - exp(-1.0 / (sampleRate * timeConstantSec))
    private val settleSamples = (sampleRate * settleSec).toLong()

    private var expMs = 0.0
    private var sumA = 0.0
    private var sumZ = 0.0
    private var n = 0L
    private var maxMs = 0.0
    private var minMs = Double.MAX_VALUE
    private var peakAbs = 0.0
    private var clipped = 0L

    /** 블록 길이(샘플). 100 ms 단위 A 가중 평균제곱을 블록 콜백으로 전달 (비교보정용). */
    val blockSamples: Int = (sampleRate * 0.1).toInt()
    /** 선택: 100 ms마다 A 가중 평균제곱을 받는 콜백. 측정 계산에는 영향 없음. */
    var blockListener: ((meanSquareA: Double) -> Unit)? = null
    private var blockSum = 0.0
    private var blockCount = 0

    fun process(samples: FloatArray, count: Int = samples.size) {
        for (i in 0 until count) {
            val x = samples[i]
            if (abs(x) >= CLIP_THRESHOLD) clipped++
            val xd = x.toDouble()
            peakAbs = max(peakAbs, abs(xd))
            val y = filter.process(xd)
            val y2 = y * y
            expMs += alpha * (y2 - expMs)
            sumA += y2
            sumZ += xd * xd
            n++
            if (expMs > maxMs) maxMs = expMs
            if (n > settleSamples && expMs < minMs) minMs = expMs
            val listener = blockListener
            if (listener != null) {
                blockSum += y2
                if (++blockCount == blockSamples) {
                    listener(blockSum / blockSamples)
                    blockSum = 0.0
                    blockCount = 0
                }
            }
        }
    }

    fun reset() {
        filter.reset()
        expMs = 0.0; sumA = 0.0; sumZ = 0.0; n = 0
        maxMs = 0.0; minMs = Double.MAX_VALUE; peakAbs = 0.0; clipped = 0
        blockSum = 0.0; blockCount = 0
    }

    fun snapshot(): LevelSnapshot = LevelSnapshot(
        lafDbFs = DbFs.fromMeanSquare(expMs),
        laeqDbFs = if (n > 0) DbFs.fromMeanSquare(sumA / n) else null,
        lafMaxDbFs = if (n > 0) DbFs.fromMeanSquare(maxMs) else null,
        lafMinDbFs = if (n > settleSamples) DbFs.fromMeanSquare(minMs) else null,
        lzeqDbFs = if (n > 0) DbFs.fromMeanSquare(sumZ / n) else null,
        samplePeakDbFs = DbFs.fromAmplitude(peakAbs),
        durationSec = n.toDouble() / sampleRate,
        clippedSamples = clipped,
    )
}

/** 모든 값은 dBFS(AES17) 상대값. */
data class LevelSnapshot(
    val lafDbFs: Double,
    val laeqDbFs: Double?,
    val lafMaxDbFs: Double?,
    val lafMinDbFs: Double?,
    val lzeqDbFs: Double?,
    val samplePeakDbFs: Double,
    val durationSec: Double,
    val clippedSamples: Long,
)
