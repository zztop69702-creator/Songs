package kr.youngil.acousticvision.core

import kr.youngil.acousticvision.core.dsp.AWeighting
import kr.youngil.acousticvision.core.dsp.AWeightingFilter
import kr.youngil.acousticvision.core.dsp.DbFs
import kr.youngil.acousticvision.core.dsp.FanFrequencies
import kr.youngil.acousticvision.core.dsp.PeakDetector
import kr.youngil.acousticvision.core.dsp.SoundLevelMeter
import kr.youngil.acousticvision.core.dsp.SpectrumAnalyzer
import kr.youngil.acousticvision.core.dsp.SpectrumAverager
import kr.youngil.acousticvision.core.dsp.ThirdOctave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** 모든 시험은 합성 신호(SIM) 기반이며 실측 검증이 아니다. */
class DspTest {
    private val fs = 48_000

    private fun sine(freq: Double, amp: Double, seconds: Double, rate: Int = fs) =
        FloatArray((rate * seconds).toInt()) { (amp * sin(2 * PI * freq * it / rate)).toFloat() }

    // ---------- FFT ----------
    @Test fun fftBinCenteredSineReadsZeroDbFs() {
        val n = 8192
        val an = SpectrumAnalyzer(fs, n)
        val bin = 171 // 171 * 5.859375 = 1001.95 Hz
        val f = bin * an.binHz
        val s = an.analyze(sine(f, 1.0, 1.0))
        assertEquals(0.0, s.amplitudeDbFs[bin], 0.01)
    }

    @Test fun fftPeakFrequencyWithinOneBin() {
        val n = 8192
        val an = SpectrumAnalyzer(fs, n)
        for (f in listOf(100.0, 1000.0, 5000.0, 123.4)) {
            val s = an.analyze(sine(f, 0.5, 1.0))
            val peaks = PeakDetector.find(s, maxPeaks = 1)
            assertEquals("f=$f", f, peaks.first().frequencyHz, an.binHz)
            // -6.02 dBFS 진폭, 보간 후 스캘럽 오차 0.5 dB 이내
            assertEquals("f=$f", DbFs.fromAmplitude(0.5), peaks.first().levelDbFs, 0.5)
        }
    }

    @Test fun parsevalTotalMeanSquareMatchesTimeDomain() {
        val n = 8192
        val rnd = Random(1)
        val x = FloatArray(n) { (rnd.nextGaussian() * 0.1).toFloat() }
        val s = SpectrumAnalyzer(fs, n).analyze(x)
        val spectral = s.meanSquare.sum()
        val time = x.sumOf { it.toDouble() * it } / n
        // Hann 창은 신호 양 끝을 감쇠시키므로 단일 프레임에서는 통계적 오차가 남음
        assertEquals(1.0, spectral / time, 0.1)
    }

    @Test fun sineMeanSquareConservedInBand() {
        val n = 8192
        val an = SpectrumAnalyzer(fs, n)
        val s = an.analyze(sine(1000.0, 1.0, 1.0))
        val (ms, _) = s.bandMeanSquare(891.0, 1122.0)
        assertEquals(0.0, DbFs.fromMeanSquare(ms), 0.05)
    }

    // ---------- A 가중 ----------
    private val iecTable = mapOf( // IEC 61672-1 공칭값(dB), 정확 주파수 = 1000·10^(n/10)
        -20 to -70.4, -17 to -50.5, -15 to -39.4, -13 to -30.2, -12 to -26.2, -10 to -19.1, -7 to -10.9,
        -5 to -6.6, -3 to -3.2, 0 to 0.0, 3 to 1.2, 6 to 1.0, 9 to -1.1, 10 to -2.5, 12 to -6.6,
    )

    @Test fun aWeightingFormulaMatchesIecTable() {
        for ((n, expected) in iecTable) {
            val f = 1000.0 * 10.0.pow(n / 10.0)
            assertEquals("f=$f", expected, AWeighting.gainDb(f), 0.1)
        }
    }

    @Test fun aWeightingIirDeviationFromFormula() {
        val filt = AWeightingFilter(fs)
        val report = StringBuilder("A-weighting IIR vs 해석식 (fs=48 kHz)\n")
        for (n in -20..13) {
            val f = 1000.0 * 10.0.pow(n / 10.0)
            if (f >= fs / 2.0) continue
            val d = filt.responseDb(f) - AWeighting.gainDb(f)
            report.append(String.format("%8.1f Hz  %+6.2f dB\n", f, d))
            when {
                f <= 4_000 -> assertTrue("f=$f d=$d", abs(d) <= 0.3)
                f <= 10_500 -> assertTrue("f=$f d=$d", abs(d) <= 0.8)
            }
        }
        println(report)
    }

    // ---------- 소음도 ----------
    @Test fun laeqOfFullScale1kSineIsZero() {
        val m = SoundLevelMeter(fs)
        m.process(sine(1000.0, 1.0, 2.0))
        val r = m.snapshot()
        assertEquals(0.0, r.laeqDbFs!!, 0.1)
        assertEquals(0.0, r.lzeqDbFs!!, 0.05)
    }

    @Test fun laeqOf100HzSineIsAttenuated() {
        val m = SoundLevelMeter(fs)
        m.process(sine(100.0, 1.0, 3.0))
        assertEquals(AWeighting.gainDb(100.0), m.snapshot().laeqDbFs!!, 0.2)
    }

    @Test fun maxMinAndLaeqForLevelStep() {
        val m = SoundLevelMeter(fs)
        m.process(sine(1000.0, 0.1, 2.0))   // -20 dBFS
        m.process(sine(1000.0, 0.01, 2.0))  // -40 dBFS
        val r = m.snapshot()
        assertEquals(-20.0, r.lafMaxDbFs!!, 0.3)
        assertEquals(-40.0, r.lafMinDbFs!!, 0.3)
        // 에너지 평균: 10log10((0.01 + 0.0001)/2) + 3.01 ≈ -22.97
        assertEquals(-22.97, r.laeqDbFs!!, 0.2)
        assertEquals(4.0, r.durationSec, 1e-6)
    }

    @Test fun fastTimeWeightingDecayRate() {
        // Fast 감쇠율 이론값: 10·log10(e)/0.125 ≈ 34.7 dB/s
        val m = SoundLevelMeter(fs)
        m.process(sine(1000.0, 1.0, 1.0))
        val before = m.snapshot().lafDbFs
        m.process(FloatArray(fs / 10)) // 0.1 s 무음
        val after = m.snapshot().lafDbFs
        assertEquals(3.47, before - after, 0.3)
    }

    @Test fun clippingIsCounted() {
        val m = SoundLevelMeter(fs)
        m.process(FloatArray(100) { if (it % 2 == 0) 1f else -1f })
        assertEquals(100L, m.snapshot().clippedSamples)
    }

    @Test fun minIsNullBeforeSettling() {
        val m = SoundLevelMeter(fs)
        m.process(sine(1000.0, 0.1, 0.2))
        assertNull(m.snapshot().lafMinDbFs)
    }

    // ---------- 1/3 옥타브 ----------
    @Test fun thirdOctaveBandContainsSine() {
        val s = SpectrumAnalyzer(fs, 16384).analyze(sine(1000.0, 1.0, 1.0))
        val bands = ThirdOctave.bands(s)
        val b1k = bands.first { it.nominalLabel == "1k" }
        assertEquals(0.0, b1k.levelZDbFs, 0.1)
        assertTrue(bands.first { it.nominalLabel == "20" }.binCount < 3) // 저주파 대역 신뢰도 낮음 표기
        assertTrue(bands.none { it.upperHz > fs / 2.0 })
    }

    // ---------- 송풍기 주파수 ----------
    @Test fun fanFrequencyFormulas() {
        assertEquals(24.1667, FanFrequencies.shaftHz(1450.0), 1e-3)
        assertEquals(966.667, FanFrequencies.bladePassHz(1450.0, 40), 1e-3)
        val tones = FanFrequencies.tones(1750.0, 6)
        assertEquals(175.0, tones.first { it.label == "BPF" }.frequencyHz, 1e-9)
        assertEquals(350.0, tones.first { it.label == "BPF 2X" }.frequencyHz, 1e-9)
    }

    @Test fun fanToneMatchedToSpectralPeak() {
        val an = SpectrumAnalyzer(fs, 16384)
        val x = sine(178.0, 0.3, 1.0) // 명판 175 Hz 대비 +3 Hz (허용 3 %)
        val s = an.analyze(x)
        val peaks = PeakDetector.find(s)
        val m = FanFrequencies.match(FanFrequencies.tones(1750.0, 6), peaks, s.binHz)
        val bpf = m.first { it.tone.label == "BPF" }
        assertNotNull(bpf.peak)
        assertEquals(3.0, bpf.deltaHz!!, 0.5)
        assertNull(m.first { it.tone.label == "BPF 3X" }.peak)
    }

    // ---------- 평균 ----------
    @Test fun averagerKeepsStationarySineLevel() {
        val an = SpectrumAnalyzer(fs, 8192)
        val x = sine(171 * an.binHz, 0.5, 2.0)
        val avg = SpectrumAverager()
        for (i in 0 until 5) avg.add(an.analyze(x, i * 8192))
        val r = avg.result()!!
        assertEquals(5, avg.frames)
        assertEquals(DbFs.fromAmplitude(0.5), r.amplitudeDbFs[171], 0.01)
    }
}
