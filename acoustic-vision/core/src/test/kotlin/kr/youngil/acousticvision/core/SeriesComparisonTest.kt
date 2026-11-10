package kr.youngil.acousticvision.core

import kr.youngil.acousticvision.core.calibration.BlockReason
import kr.youngil.acousticvision.core.calibration.CalibrationMode
import kr.youngil.acousticvision.core.calibration.MeterSample
import kr.youngil.acousticvision.core.calibration.ReadingType
import kr.youngil.acousticvision.core.calibration.ReferenceInstrument
import kr.youngil.acousticvision.core.calibration.ReferenceReading
import kr.youngil.acousticvision.core.calibration.SafetyGate
import kr.youngil.acousticvision.core.calibration.SeriesComparator
import kr.youngil.acousticvision.core.calibration.TimeWeighting
import kr.youngil.acousticvision.core.calibration.ValueSource
import kr.youngil.acousticvision.core.calibration.WindowAnalyzer
import kr.youngil.acousticvision.core.calibration.WindowDecision
import kr.youngil.acousticvision.core.dsp.DbFs
import kr.youngil.acousticvision.core.model.PreprocessingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** 합성 데이터(SIM): 가상의 비적분형 소음계(표시 지연·0.1 dB 표시) — 실제 TES-1350A 검증 아님 */
class SeriesComparisonTest {
    private fun msOf(db: Double) = 10.0.pow((db - DbFs.SINE_RMS_OFFSET_DB) / 10.0)
    private val pre = 30 // 3 s 앞 여유

    /** 레벨이 천천히 오르내리는 소음 (−40 dBFS ± ampDb, 주기 4 s) */
    private fun blocks(seconds: Int, ampDb: Double, seed: Long = 1): List<Double> {
        val rnd = Random(seed)
        return List(pre + seconds * 10 + 20) { i -> msOf(-40.0 + ampDb * sin(2 * PI * i / 40.0) + rnd.nextGaussian() * 0.3) }
    }

    /** 가상 소음계: 앱과 같은 Slow 레벨 + 오프셋, lag만큼 늦게 표시, 0.1 dB 반올림, 0.4 s 간격 판독 */
    private fun meter(levels: DoubleArray, seconds: Int, offset: Double, lagSec: Double): List<MeterSample> =
        (1..(seconds / 0.4).toInt()).map { k ->
            val t = k * 0.4
            val idx = pre + ((t - lagSec) / 0.1).roundToInt() - 1
            val v = ((levels[idx] + offset) * 10).roundToInt() / 10.0
            MeterSample(t, v, v.toString(), uncertain = false)
        }

    @Test fun recoversOffsetAndDisplayLag() {
        val b = blocks(15, 4.0)
        val lv = SeriesComparator.timeWeighted(b, TimeWeighting.SLOW)
        val r = SeriesComparator.compare(lv, pre, meter(lv, 15, 112.3, 0.6), TimeWeighting.SLOW)!!
        assertEquals(112.3, r.offsetDb, 0.1)
        assertEquals(0.6, r.bestLagSec, 0.15)
        assertFalse(r.lagUncertain)
        assertTrue("residual=${r.residualStdDb}", r.residualStdDb < 0.1)
        assertEquals(WindowDecision.OK, SeriesComparator.decide(r, 15.0))
    }

    @Test fun steadyNoiseOffsetCorrectButLagUncertain() {
        val b = blocks(15, 0.0)
        val lv = SeriesComparator.timeWeighted(b, TimeWeighting.SLOW)
        val r = SeriesComparator.compare(lv, pre, meter(lv, 15, 100.0, 0.5), TimeWeighting.SLOW)!!
        assertEquals(100.0, r.offsetDb, 0.15)
        assertTrue(r.lagUncertain)
    }

    @Test fun unreadableFramesAreDroppedAndTooFewTriggersRemeasure() {
        val b = blocks(15, 4.0)
        val lv = SeriesComparator.timeWeighted(b, TimeWeighting.SLOW)
        val good = meter(lv, 15, 100.0, 0.5)
        // 80 %를 판독 불가 처리
        val bad = good.mapIndexed { i, s -> if (i % 5 != 0) s.copy(valueDb = null, uncertain = true) else s }
        val r = SeriesComparator.compare(lv, pre, bad, TimeWeighting.SLOW)
        assertNotNull(r)
        assertEquals(WindowDecision.EXTEND_TO_30S, SeriesComparator.decide(r, 15.0))
        assertEquals(WindowDecision.REMEASURE, SeriesComparator.decide(r, 30.0))
        // 사용자 제외 표시도 반영
        val excluded = good.map { it.copy(excludedByUser = true) }
        assertEquals(null, SeriesComparator.compare(lv, pre, excluded, TimeWeighting.SLOW))
    }

    @Test fun inconsistentReadingsFailResidualGate() {
        val b = blocks(15, 4.0)
        val lv = SeriesComparator.timeWeighted(b, TimeWeighting.SLOW)
        val rnd = Random(9)
        val noisy = meter(lv, 15, 100.0, 0.5).map { it.copy(valueDb = it.valueDb!! + rnd.nextGaussian() * 2.0) }
        val r = SeriesComparator.compare(lv, pre, noisy, TimeWeighting.SLOW)!!
        assertTrue(r.residualStdDb > 0.7)
        assertEquals(WindowDecision.EXTEND_TO_30S, SeriesComparator.decide(r, 15.0))
    }

    @Test fun validationErrorIsOffsetDifference() {
        val b1 = blocks(15, 4.0, seed = 1)
        val b2 = blocks(15, 3.0, seed = 2)
        val l1 = SeriesComparator.timeWeighted(b1, TimeWeighting.SLOW)
        val l2 = SeriesComparator.timeWeighted(b2, TimeWeighting.SLOW)
        val cal = SeriesComparator.compare(l1, pre, meter(l1, 15, 105.0, 0.5), TimeWeighting.SLOW)!!
        val ver = SeriesComparator.compare(l2, pre, meter(l2, 15, 105.4, 0.5), TimeWeighting.SLOW)!!
        assertEquals(-0.4, SeriesComparator.validationError(cal.offsetDb, ver), 0.1)
    }

    @Test fun safetyGateExpectsInstantaneousForVideoSeries() {
        val a = WindowAnalyzer.analyze(List(170) { msOf(-40.0) }, 10, 150, false)
        val pp = PreprocessingStatus(true, true, true, true, true, false, null)
        val inst = ReferenceInstrument("TES-1350A", "123", "미확인")
        val r = ReferenceReading(72.0, ValueSource.OCR_EDITED, ReadingType.INSTANTANEOUS, "A", 15.0, userConfirmed = true)
        assertTrue(SafetyGate.check(CalibrationMode.VIDEO_SERIES, r, inst, a, WindowDecision.OK, pp, false).isEmpty())
        assertTrue(BlockReason.READING_TYPE_MISMATCH in SafetyGate.check(CalibrationMode.VIDEO_SERIES, r.copy(readingType = ReadingType.MAX), inst, a, WindowDecision.OK, pp, false))
    }
}
