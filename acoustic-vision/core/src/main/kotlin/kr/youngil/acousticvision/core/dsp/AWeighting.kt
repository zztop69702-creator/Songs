package kr.youngil.acousticvision.core.dsp

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A 가중 특성 (IEC 61672-1 해석식).
 * 극점 주파수 f1=20.598997, f2=107.65265, f3=737.86223, f4=12194.217 Hz, 1 kHz에서 0 dB로 정규화.
 */
object AWeighting {
    const val F1 = 20.598997
    const val F2 = 107.65265
    const val F3 = 737.86223
    const val F4 = 12194.217

    private fun ra(f: Double): Double {
        val f2 = f * f
        return (F4 * F4 * f2 * f2) /
            ((f2 + F1 * F1) * sqrt((f2 + F2 * F2) * (f2 + F3 * F3)) * (f2 + F4 * F4))
    }

    private val norm = -20.0 * log10(ra(1000.0))

    /** 주파수 f(Hz)에서의 A 가중값(dB). */
    fun gainDb(f: Double): Double = if (f <= 0.0) -200.0 else 20.0 * log10(ra(f)) + norm
}

/** 2차 IIR 섹션 (Direct Form II Transposed, double 정밀도). */
class Biquad(
    private var b0: Double, private var b1: Double, private var b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() { z1 = 0.0; z2 = 0.0 }

    fun scale(g: Double) { b0 *= g; b1 *= g; b2 *= g }

    /** 정규화 각주파수 w(rad/sample)에서의 복소 응답 (re, im). */
    fun response(w: Double): Pair<Double, Double> {
        // H = (b0 + b1 e^-jw + b2 e^-2jw) / (1 + a1 e^-jw + a2 e^-2jw)
        val nr = b0 + b1 * cos(w) + b2 * cos(2 * w)
        val ni = -b1 * sin(w) - b2 * sin(2 * w)
        val dr = 1 + a1 * cos(w) + a2 * cos(2 * w)
        val di = -a1 * sin(w) - a2 * sin(2 * w)
        val den = dr * dr + di * di
        return ((nr * dr + ni * di) / den) to ((ni * dr - nr * di) / den)
    }
}

/**
 * 시간영역 A 가중 필터: 아날로그 원형을 극점 사전왜곡(pre-warp) 쌍선형 변환으로 디지털화.
 * 고주파(특히 fs/2 근처)에서 해석식과 편차가 생기며, 편차는 단위시험(AWeightingTest)에서 수치로 기록한다.
 */
class AWeightingFilter(val sampleRate: Int) {
    private val sections: List<Biquad>

    init {
        val fs = sampleRate.toDouble()
        fun zPole(fHz: Double): Double {
            val wa = 2.0 * fs * tan(PI * fHz / fs) // pre-warp
            val k = wa / (2.0 * fs)
            return (1.0 - k) / (1.0 + k)
        }
        fun section(z1: Double, z2: Double, p1: Double, p2: Double) =
            Biquad(1.0, -(z1 + z2), z1 * z2, -(p1 + p2), p1 * p2)

        val p1 = zPole(AWeighting.F1)
        val p2 = zPole(AWeighting.F2)
        val p3 = zPole(AWeighting.F3)
        val p4 = zPole(AWeighting.F4)
        sections = listOf(
            section(1.0, 1.0, p1, p1),
            section(1.0, 1.0, p2, p3),
            section(-1.0, -1.0, p4, p4),
        )
        // 1 kHz에서 0 dB 정규화
        val g = 1.0 / magnitude(1000.0)
        sections[0].scale(g)
    }

    fun magnitude(fHz: Double): Double {
        val w = 2.0 * PI * fHz / sampleRate
        var re = 1.0
        var im = 0.0
        for (s in sections) {
            val (r, i) = s.response(w)
            val nr = re * r - im * i
            val ni = re * i + im * r
            re = nr; im = ni
        }
        return hypot(re, im)
    }

    fun responseDb(fHz: Double): Double = 20.0 * log10(magnitude(fHz))

    @Suppress("unused")
    fun phase(fHz: Double): Double {
        val w = 2.0 * PI * fHz / sampleRate
        var phase = 0.0
        for (s in sections) {
            val (r, i) = s.response(w)
            phase += atan2(i, r)
        }
        return phase
    }

    fun process(x: Double): Double {
        var y = x
        for (s in sections) y = s.process(y)
        return y
    }

    fun reset() = sections.forEach { it.reset() }
}
