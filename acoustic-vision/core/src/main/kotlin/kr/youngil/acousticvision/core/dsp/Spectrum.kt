package kr.youngil.acousticvision.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * dBFS 정의 (AES17): 풀스케일 사인파(진폭 1.0)의 RMS = 0 dBFS.
 * 따라서 평균제곱 ms에 대해 dBFS = 10·log10(ms) + 3.0103.
 * dBFS는 디지털 상대값이며 음압레벨(dB SPL, dB(A))이 아니다.
 */
object DbFs {
    const val SINE_RMS_OFFSET_DB = 3.0103
    /** 로그 계산 하한 (−200 dBFS 부근). NaN/−∞ 저장을 막기 위함. */
    const val FLOOR_MEAN_SQUARE = 1e-20

    fun fromMeanSquare(ms: Double): Double = 10.0 * log10(max(ms, FLOOR_MEAN_SQUARE)) + SINE_RMS_OFFSET_DB
    fun fromAmplitude(a: Double): Double = 20.0 * log10(max(a, 1e-10))
}

enum class WindowType(val label: String) { HANN("Hann") }

/** 창함수와 그 이득 특성. */
class Window(val type: WindowType, val size: Int) {
    val coefficients: DoubleArray = when (type) {
        // 스펙트럼 분석용 periodic Hann
        WindowType.HANN -> DoubleArray(size) { 0.5 - 0.5 * cos(2.0 * PI * it / size) }
    }
    val sum: Double = coefficients.sum()
    val sumSquares: Double = coefficients.sumOf { it * it }
    /** 등가잡음대역폭(bin 단위). Hann = 1.5 */
    val enbwBins: Double = size * sumSquares / (sum * sum)
}

/**
 * 단측 스펙트럼 결과.
 * @property amplitudeDbFs 각 bin의 사인 진폭 추정값(dBFS). bin 중앙 사인파에서 정확, bin 사이면 창함수 스캘럽 손실(Hann 최대 약 1.42 dB) 발생.
 * @property meanSquare 각 bin의 평균제곱 기여분(파서발 정규화). 대역 합산에 사용.
 */
class Spectrum(
    val sampleRate: Int,
    val fftSize: Int,
    val window: WindowType,
    val amplitudeDbFs: DoubleArray,
    val meanSquare: DoubleArray,
) {
    val binHz: Double get() = sampleRate.toDouble() / fftSize
    val binCount: Int get() = amplitudeDbFs.size
    fun frequencyOf(bin: Int): Double = bin * binHz

    /** 주어진 주파수 범위의 평균제곱 합 (Z 가중). */
    fun bandMeanSquare(loHz: Double, hiHz: Double, weightDb: ((Double) -> Double)? = null): Pair<Double, Int> {
        var sum = 0.0
        var count = 0
        for (k in 1 until binCount) {
            val f = frequencyOf(k)
            if (f >= loHz && f < hiHz) {
                val w = weightDb?.let { Math.pow(10.0, it(f) / 10.0) } ?: 1.0
                sum += meanSquare[k] * w
                count++
            }
        }
        return sum to count
    }
}

class SpectrumAnalyzer(val sampleRate: Int, val fftSize: Int, val windowType: WindowType = WindowType.HANN) {
    private val fft = Fft(fftSize)
    private val window = Window(windowType, fftSize)
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)

    val binHz: Double get() = sampleRate.toDouble() / fftSize
    val enbwHz: Double get() = window.enbwBins * binHz

    /** samples 길이는 fftSize 이상이어야 하며 앞에서부터 fftSize개를 사용한다. */
    fun analyze(samples: FloatArray, offset: Int = 0): Spectrum {
        require(samples.size - offset >= fftSize) { "샘플 수 부족" }
        val w = window.coefficients
        for (i in 0 until fftSize) {
            re[i] = samples[offset + i] * w[i]
            im[i] = 0.0
        }
        fft.transform(re, im)
        val bins = fftSize / 2 + 1
        val amp = DoubleArray(bins)
        val ms = DoubleArray(bins)
        val msNorm = fftSize * window.sumSquares
        for (k in 0 until bins) {
            val mag2 = re[k] * re[k] + im[k] * im[k]
            val oneSided = if (k == 0 || k == fftSize / 2) 1.0 else 2.0
            amp[k] = DbFs.fromAmplitude(oneSided * sqrt(mag2) / window.sum)
            ms[k] = oneSided * mag2 / msNorm
        }
        return Spectrum(sampleRate, fftSize, windowType, amp, ms)
    }
}

/**
 * 프레임 스펙트럼의 전력(선형) 평균. 측정 구간 전체를 대표하는 스펙트럼을 저장할 때 사용한다.
 */
class SpectrumAverager {
    private var ampPower: DoubleArray? = null
    private var ms: DoubleArray? = null
    private var template: Spectrum? = null
    var frames: Int = 0
        private set

    fun add(s: Spectrum) {
        val t = template
        if (t == null || t.binCount != s.binCount || t.sampleRate != s.sampleRate) {
            ampPower = DoubleArray(s.binCount); ms = DoubleArray(s.binCount); frames = 0
        }
        template = s
        val ap = ampPower!!
        val m = ms!!
        for (k in 0 until s.binCount) {
            ap[k] += Math.pow(10.0, s.amplitudeDbFs[k] / 10.0)
            m[k] += s.meanSquare[k]
        }
        frames++
    }

    fun reset() { ampPower = null; ms = null; template = null; frames = 0 }

    fun result(): Spectrum? {
        val t = template ?: return null
        val n = frames.toDouble()
        val ap = ampPower!!
        val m = ms!!
        return Spectrum(
            t.sampleRate, t.fftSize, t.window,
            DoubleArray(t.binCount) { 10.0 * log10(max(ap[it] / n, 1e-20)) },
            DoubleArray(t.binCount) { m[it] / n },
        )
    }
}
