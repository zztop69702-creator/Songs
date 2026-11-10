package kr.youngil.acousticvision.core

import kr.youngil.acousticvision.core.calibration.BlockReason
import kr.youngil.acousticvision.core.calibration.CalibrationMode
import kr.youngil.acousticvision.core.calibration.CalibrationProfile
import kr.youngil.acousticvision.core.calibration.CalibrationThresholds
import kr.youngil.acousticvision.core.calibration.ComparisonRecord
import kr.youngil.acousticvision.core.calibration.InvalidReason
import kr.youngil.acousticvision.core.calibration.MeterOcrParser
import kr.youngil.acousticvision.core.calibration.OcrToken
import kr.youngil.acousticvision.core.calibration.OffsetCalculator
import kr.youngil.acousticvision.core.calibration.ProfileStatus
import kr.youngil.acousticvision.core.calibration.ProfileValidity
import kr.youngil.acousticvision.core.calibration.ReadingType
import kr.youngil.acousticvision.core.calibration.ReferenceInstrument
import kr.youngil.acousticvision.core.calibration.ReferenceReading
import kr.youngil.acousticvision.core.calibration.SafetyGate
import kr.youngil.acousticvision.core.calibration.ValueSource
import kr.youngil.acousticvision.core.calibration.WindowAnalysis
import kr.youngil.acousticvision.core.calibration.WindowAnalyzer
import kr.youngil.acousticvision.core.calibration.WindowDecision
import kr.youngil.acousticvision.core.dsp.DbFs
import kr.youngil.acousticvision.core.dsp.SoundLevelMeter
import kr.youngil.acousticvision.core.model.AppJson
import kr.youngil.acousticvision.core.model.AudioConfig
import kr.youngil.acousticvision.core.model.CalibrationState
import kr.youngil.acousticvision.core.model.PreprocessingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** 합성 신호·합성 텍스트 기반 (SIM). 실제 소음계·OCR 화면 검증 아님. */
class CalibrationTest {
    private val fs = 48_000
    private val pp = PreprocessingStatus(true, true, true, true, true, false, null)

    private fun msOf(dbfs: Double) = 10.0.pow((dbfs - DbFs.SINE_RMS_OFFSET_DB) / 10.0)
    private fun steady(db: Double, n: Int) = List(n) { msOf(db) }

    // V2-U01
    @Test fun offsetExampleFromSpec() {
        assertEquals(3.2, OffsetCalculator.offset(72.5, 69.3), 1e-9)
    }

    // V2-U02
    @Test fun blockLaeqMatchesSoundLevelMeter() {
        val m = SoundLevelMeter(fs)
        val blocks = ArrayList<Double>()
        m.blockListener = { blocks += it }
        val rnd = Random(3)
        val x = FloatArray(fs * 17) { (0.2 * sin(2 * PI * 500 * it / fs) * (1 + 0.3 * rnd.nextGaussian())).toFloat() }
        m.process(x)
        assertEquals(170, blocks.size)
        val a = WindowAnalyzer.analyze(blocks, 0, 170, clipped = false)
        assertEquals(m.snapshot().laeqDbFs!!, a.laeqDbFs, 0.01)
    }

    // V2-U03
    @Test fun steadyIsOkFluctuatingExtendsThenRemeasures() {
        val ok = WindowAnalyzer.analyze(steady(-40.0, 170), 10, 150, false)
        assertEquals(WindowDecision.OK, WindowAnalyzer.decide(ok, CalibrationMode.LAEQ_COMPARISON))

        val rnd = Random(5)
        val fluct = List(320) { msOf(-40.0 + if ((it / 10) % 2 == 0) 3.0 else -3.0 + rnd.nextDouble() * 0.1) }
        val a15 = WindowAnalyzer.analyze(fluct, 10, 150, false)
        assertTrue(a15.oneSecondStdDb > 1.0)
        assertEquals(WindowDecision.EXTEND_TO_30S, WindowAnalyzer.decide(a15, CalibrationMode.LAEQ_COMPARISON))
        val a30 = WindowAnalyzer.analyze(fluct, 10, 300, false)
        assertEquals(WindowDecision.REMEASURE, WindowAnalyzer.decide(a30, CalibrationMode.LAEQ_COMPARISON))
    }

    // V2-U04
    @Test fun timingSensitivityDetectsLevelJumpAtWindowEdge() {
        // 구간 시작 직전 1 s 동안 +15 dB 충격 → 시작 시점 어긋남에 민감
        val blocks = steady(-40.0, 10).map { it * 10.0.pow(1.5) } + steady(-40.0, 160)
        val a = WindowAnalyzer.analyze(blocks, 10, 150, false)
        assertTrue("sens=${a.timingSensitivityDb}", a.timingSensitivityDb > 0.3)
        assertEquals(WindowDecision.EXTEND_TO_30S, WindowAnalyzer.decide(a, CalibrationMode.LAEQ_COMPARISON))
    }

    @Test fun missingPreRollIsNotOk() {
        val a = WindowAnalyzer.analyze(steady(-40.0, 150), 0, 150, false)
        assertEquals(0.0, a.timingShiftAvailableSec, 1e-9)
        assertTrue(WindowAnalyzer.decide(a, CalibrationMode.LAEQ_COMPARISON) != WindowDecision.OK)
    }

    private fun comparison(app: Double, ref: Double, reasons: List<BlockReason> = emptyList()): ComparisonRecord {
        val a = WindowAnalyzer.analyze(steady(app, 170), 10, 150, false)
        return ComparisonRecord(
            a, WindowDecision.OK,
            ReferenceReading(ref, ValueSource.MANUAL, ReadingType.LAEQ_INTERVAL, "A", 15.0, userConfirmed = true),
            0L, 15_000L, reasons,
        )
    }

    // V2-U05
    @Test fun validationPassAndFailAtTolerance() {
        val c = OffsetCalculator.offset(72.5, -30.0) // 102.5
        assertTrue(OffsetCalculator.validate(c, comparison(-35.0, 66.6)).passed)   // 예측 67.5, 오차 +0.9
        assertFalse(OffsetCalculator.validate(c, comparison(-35.0, 66.4)).passed)  // 오차 +1.1
        assertFalse("검증구간에 차단 사유가 있으면 불합격", OffsetCalculator.validate(c, comparison(-35.0, 67.5, listOf(BlockReason.CLIPPING))).passed)
    }

    // V2-U06
    @Test fun safetyGateBlocksEachCondition() {
        val inst = ReferenceInstrument("NL-42", "123", "2026-03-01")
        val okA = WindowAnalyzer.analyze(steady(-40.0, 170), 10, 150, false)
        val good = ReferenceReading(72.5, ValueSource.OCR_EDITED, ReadingType.LAEQ_INTERVAL, "A", 15.0, userConfirmed = true)
        fun gate(r: ReferenceReading = good, i: ReferenceInstrument = inst, a: WindowAnalysis = okA, d: WindowDecision = WindowDecision.OK,
                 p: PreprocessingStatus = pp, routing: Boolean = false, mode: CalibrationMode = CalibrationMode.LAEQ_COMPARISON) =
            SafetyGate.check(mode, r, i, a, d, p, routing)

        assertTrue(gate().isEmpty())
        assertTrue(BlockReason.WEIGHTING_MISMATCH in gate(r = good.copy(weightingConfirmed = "C")))
        assertTrue(BlockReason.READING_TYPE_MISMATCH in gate(r = good.copy(readingType = ReadingType.INSTANTANEOUS)))
        assertTrue(BlockReason.READING_TYPE_MISMATCH in gate(r = good.copy(readingType = ReadingType.MAX)))
        assertTrue(BlockReason.INTERVAL_MISMATCH in gate(r = good.copy(meterIntervalSec = 10.0)))
        assertTrue(BlockReason.VALUE_UNCONFIRMED in gate(r = good.copy(userConfirmed = false)))
        assertTrue(BlockReason.OCR_UNCERTAIN in gate(r = good.copy(source = ValueSource.OCR, ocrUncertain = true)))
        assertTrue(BlockReason.INPUT_PATH_CHANGED in gate(routing = true))
        assertTrue(BlockReason.PREPROCESSING_ACTIVE in gate(p = pp.copy(agcDisabled = false)))
        assertTrue(BlockReason.CLIPPING in gate(a = okA.copy(clipped = true)))
        assertTrue(BlockReason.REFERENCE_INFO_MISSING in gate(i = inst.copy(calibrationDate = "")))
        assertTrue(BlockReason.VALUE_OUT_OF_RANGE in gate(r = good.copy(valueDb = 725.0)))
        assertTrue(BlockReason.UNSTABLE in gate(d = WindowDecision.EXTEND_TO_30S))
        // 간편 모드는 순간값이어야 함
        assertTrue(gate(r = good.copy(readingType = ReadingType.INSTANTANEOUS), mode = CalibrationMode.SIMPLE_INSTANT).isEmpty())
    }

    private fun profile(status: ProfileStatus = ProfileStatus.ACTIVE) = CalibrationProfile(
        profileId = "cp1", createdAtEpochMs = 1, phoneManufacturer = "T", phoneModel = "M", osRelease = "16", sdkInt = 36,
        buildFingerprint = "fp1", micType = "builtin", inputDeviceKey = "builtin", inputDeviceName = "내장 마이크",
        audioSource = "UNPROCESSED", sampleRate = 48000, preprocessing = pp,
        instrument = ReferenceInstrument("NL-42", "123", "2026-03-01"), mode = CalibrationMode.LAEQ_COMPARISON,
        noiseSourceDescription = "급식실 배기팬 정격", comparison = comparison(-30.0, 72.5), offsetDb = 102.5,
        status = status, thresholds = CalibrationThresholds(),
    )

    private fun config(key: String = "builtin", source: String = "UNPROCESSED", rate: Int = 48000) =
        AudioConfig(source, rate, "PCM_FLOAT", 1, 8192, "Hann", 5.86, 8.79, key, "", pp)

    // V2-U07
    @Test fun profileInvalidatedByChanges() {
        assertTrue(ProfileValidity.check(profile(), config(), "fp1").isEmpty())
        assertTrue(InvalidReason.DEVICE_CHANGED in ProfileValidity.check(profile(), config(key = "usb:UMIK"), "fp1"))
        assertTrue(InvalidReason.SOURCE_CHANGED in ProfileValidity.check(profile(), config(source = "VOICE_RECOGNITION"), "fp1"))
        assertTrue(InvalidReason.SAMPLE_RATE_CHANGED in ProfileValidity.check(profile(), config(rate = 44100), "fp1"))
        assertTrue(InvalidReason.OS_CHANGED in ProfileValidity.check(profile(), config(), "fp2"))
        assertTrue(InvalidReason.NOT_ACTIVE in ProfileValidity.check(profile(ProfileStatus.PENDING_VALIDATION), config(), "fp1"))
        val changedPp = config().copy(preprocessing = pp.copy(noiseSuppressorDisabled = false))
        assertTrue(InvalidReason.PREPROCESSING_CHANGED in ProfileValidity.check(profile(), changedPp, "fp1"))
    }

    // V2-U08
    @Test fun ocrParsesTypicalLaeqScreen() {
        val r = MeterOcrParser.parse(listOf(OcrToken("LAeq", 30), OcrToken("72.5", 120, 0.95f), OcrToken("dB(A)", 30), OcrToken("00:15", 40)))
        assertEquals(72.5, r.best!!.valueDb, 1e-9)
        assertTrue(r.leqLabel)
        assertEquals(true, r.weightingA)
        assertFalse(r.warnings.joinToString(), r.uncertain)
    }

    @Test fun ocrMissingDecimalIsUncertain() {
        val r = MeterOcrParser.parse(listOf(OcrToken("LAeq 725 dBA", 100)))
        assertEquals(72.5, r.best!!.valueDb, 1e-9)
        assertTrue(r.best!!.decimalInferred)
        assertTrue(r.uncertain)
    }

    @Test fun ocrMaxLabelAndTimeAreHandled() {
        val r = MeterOcrParser.parse(listOf(OcrToken("MAX", 30), OcrToken("81.3", 110), OcrToken("15s", 40), OcrToken("dB(A)", 30)))
        assertEquals(81.3, r.best!!.valueDb, 1e-9)
        assertTrue(r.maxLabel)
        assertTrue(r.uncertain)
        assertTrue(r.candidates.none { it.raw == "15" })
    }

    @Test fun ocrNoValidNumber() {
        val r = MeterOcrParser.parse(listOf(OcrToken("CAL 1kHz", 50), OcrToken("12:30", 60)))
        assertNull(r.best)
        assertTrue(r.uncertain)
    }

    @Test fun ocrLetterConfusionInDigits() {
        val r = MeterOcrParser.parse(listOf(OcrToken("7O.5", 100), OcrToken("LAeq dB(A)", 20)))
        assertEquals(70.5, r.best!!.valueDb, 1e-9)
    }

    // V2-U09
    @Test fun profileJsonRoundTripKeepsRawAndOffset() {
        val p = profile()
        val text = AppJson.encodeToString(CalibrationProfile.serializer(), p)
        val back = AppJson.decodeFromString(CalibrationProfile.serializer(), text)
        assertEquals(p, back)
        val cal = back.toCalibration()
        assertEquals(CalibrationState.REFERENCE_COMPARED, cal.state)
        assertEquals("cp1", cal.profileId)
        assertEquals(-30.0, cal.measuredDbFs!!, 0.01)   // 원본(앱 dBFS) 보존
        assertEquals(102.5, cal.offsetDb!!, 1e-9)       // 보정 오프셋
    }
}
