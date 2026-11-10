package kr.youngil.acousticvision.core

import kr.youngil.acousticvision.core.model.AppJson
import kr.youngil.acousticvision.core.model.AudioConfig
import kr.youngil.acousticvision.core.model.Calibration
import kr.youngil.acousticvision.core.model.CalibrationState
import kr.youngil.acousticvision.core.model.DeviceInfo
import kr.youngil.acousticvision.core.model.DistanceReading
import kr.youngil.acousticvision.core.model.DistanceSource
import kr.youngil.acousticvision.core.model.ImprovementStage
import kr.youngil.acousticvision.core.model.LevelResult
import kr.youngil.acousticvision.core.model.MarkerSource
import kr.youngil.acousticvision.core.model.Measurement
import kr.youngil.acousticvision.core.model.PreprocessingStatus
import kr.youngil.acousticvision.core.model.Project
import kr.youngil.acousticvision.core.model.SourceMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    private fun measurement(cal: Calibration) = Measurement(
        id = "m1", projectId = "p1", createdAtEpochMs = 1L, positionLabel = "팬 흡입구 1 m",
        stage = ImprovementStage.BEFORE, operatingCondition = "정격", rpm = 1450.0, bladeCount = 40,
        device = DeviceInfo("Test", "Model", "16", 36),
        audio = AudioConfig(
            "UNPROCESSED", 48000, "PCM_FLOAT", 1, 8192, "Hann", 5.86, 8.79, "builtin", "내장 마이크",
            PreprocessingStatus(true, false, null, false, null, false, null),
        ),
        calibration = cal,
        level = LevelResult(
            laeqDbFs = -40.0, lafMaxDbFs = -35.0, lafMinDbFs = -45.0, lzeqDbFs = -30.0,
            samplePeakDbFs = -20.0, durationSec = 10.0, clippedSamples = 0, calibrationOffsetDb = cal.offsetDb,
        ),
        peaks = emptyList(), thirdOctave = emptyList(), toneMatches = emptyList(),
        markers = listOf(SourceMarker("k1", MarkerSource.MANUAL, 0.5f, 0.4f, "수동 M1", 1L)),
        distance = DistanceReading(DistanceSource.MANUAL, 1.0),
        snapshotFile = "m1.jpg",
    )

    @Test fun jsonRoundTripKeepsRequiredProvenanceFields() {
        val m = measurement(Calibration.uncalibrated("builtin", "내장 마이크", "UNPROCESSED"))
        val text = AppJson.encodeToString(Measurement.serializer(), m)
        assertTrue(text.contains("\"source\": \"MANUAL\""))
        assertTrue(text.contains("\"state\": \"UNCALIBRATED\""))
        assertTrue(text.contains("\"disclaimer\""))
        val back = AppJson.decodeFromString(Measurement.serializer(), text)
        assertEquals(m, back)
        assertNull(back.level.laeqRefDb) // 미교정이면 참고 dB 없음
    }

    @Test fun calibratedReferenceLevel() {
        val cal = Calibration(state = CalibrationState.REFERENCE_COMPARED, offsetDb = 110.0, referenceInstrument = "소음계")
        assertEquals(70.0, measurement(cal).level.laeqRefDb!!, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calibratedStateRequiresOffset() {
        Calibration(state = CalibrationState.CALIBRATOR_VERIFIED, offsetDb = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun autoMarkerRequiresOriginalEstimate() {
        SourceMarker("a", MarkerSource.AUTO, 0.1f, 0.1f, "자동", 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingDistanceRequiresFailureReason() {
        DistanceReading(DistanceSource.ARCORE_DEPTH, null)
    }

    @Test fun failedDistanceIsStoredWithoutValue() {
        val d = DistanceReading(DistanceSource.ARCORE_DEPTH, null, failureReason = "Depth 미지원 기기")
        assertNull(d.meters)
    }

    @Test(expected = IllegalArgumentException::class)
    fun projectRequiresSiteName() {
        Project(id = "p", siteName = " ", equipmentName = "배기팬", createdAtEpochMs = 0)
    }
}
