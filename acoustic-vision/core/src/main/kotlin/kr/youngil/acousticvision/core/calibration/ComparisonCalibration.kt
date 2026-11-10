package kr.youngil.acousticvision.core.calibration

import kotlinx.serialization.Serializable
import kr.youngil.acousticvision.core.dsp.DbFs
import kr.youngil.acousticvision.core.model.AudioConfig
import kr.youngil.acousticvision.core.model.Calibration
import kr.youngil.acousticvision.core.model.CalibrationState
import kr.youngil.acousticvision.core.model.PreprocessingStatus
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 스마트 비교보정 V2 (docs/spec/YOUNGIL_Acoustic_Vision_Smart_Calibration_V2.md).
 * 기준 소음계 LAeq와 앱의 동시간 구간 LAeq를 비교해 특정 입력 경로·조건의 오프셋을 산출한다.
 * 공인 교정·주파수응답 교정을 대체하지 않는다.
 */

/** 모든 값은 잠정치. 기준장비 시험 후 확정 (V2 §5). */
@Serializable
data class CalibrationThresholds(
    val maxOneSecondStdDb: Double = 1.0,
    val maxTimingSensitivityDb: Double = 0.3,
    val simpleModeMaxStdDb: Double = 0.5,
    val validationToleranceDb: Double = 1.0,
    val intervalMatchToleranceSec: Double = 0.5,
    val referenceMinDb: Double = 20.0,
    val referenceMaxDb: Double = 140.0,
    val provisional: Boolean = true,
)

@Serializable
enum class CalibrationMode {
    /** 소음계의 구간 LAeq 결과와 비교 (기본) */
    LAEQ_COMPARISON,
    /** 소음계가 LAeq 미지원: 매우 안정된 소음에서 순간값 비교 — 정확도 한계 명시 */
    SIMPLE_INSTANT,
}

@Serializable
enum class ReadingType { LAEQ_INTERVAL, INSTANTANEOUS, MAX, UNKNOWN }

@Serializable
enum class ValueSource { OCR, OCR_EDITED, MANUAL }

@Serializable
enum class WindowDecision { OK, EXTEND_TO_30S, REMEASURE }

/** 100 ms 블록 단위 구간 분석 결과 (앱 측, dBFS(A)). */
@Serializable
data class WindowAnalysis(
    val intervalSec: Double,
    val laeqDbFs: Double,
    val oneSecondLevelsDbFs: List<Double>,
    val oneSecondStdDb: Double,
    val oneSecondSpreadDb: Double,
    /** 구간을 ±1 s 이동했을 때 LAeq 최대 변화량. 여유 블록이 부족하면 가능한 범위로 계산 */
    val timingSensitivityDb: Double,
    val timingShiftAvailableSec: Double,
    val clipped: Boolean,
)

object WindowAnalyzer {
    const val BLOCK_SEC = 0.1
    private const val BLOCKS_PER_SEC = 10

    private fun laeq(blocks: List<Double>, from: Int, count: Int): Double {
        var s = 0.0
        for (i in from until from + count) s += blocks[i]
        return DbFs.fromMeanSquare(s / count)
    }

    /**
     * @param blocks 측정 세션 전체의 100 ms A 가중 평균제곱 (앞뒤 여유 포함)
     * @param startBlock 비교 구간 시작 블록 인덱스
     * @param windowBlocks 비교 구간 길이(블록 수). 15 s = 150
     */
    fun analyze(blocks: List<Double>, startBlock: Int, windowBlocks: Int, clipped: Boolean): WindowAnalysis {
        require(windowBlocks >= BLOCKS_PER_SEC) { "구간은 1초 이상이어야 합니다" }
        require(startBlock >= 0 && startBlock + windowBlocks <= blocks.size) { "블록 범위 오류" }
        val main = laeq(blocks, startBlock, windowBlocks)
        val seconds = (0 until windowBlocks / BLOCKS_PER_SEC).map { laeq(blocks, startBlock + it * BLOCKS_PER_SEC, BLOCKS_PER_SEC) }
        val mean = seconds.average()
        val std = sqrt(seconds.sumOf { (it - mean) * (it - mean) } / seconds.size)
        val pre = startBlock
        val post = blocks.size - (startBlock + windowBlocks)
        val shift = min(BLOCKS_PER_SEC, min(pre, post))
        var sens = 0.0
        if (shift > 0) {
            sens = max(abs(laeq(blocks, startBlock - shift, windowBlocks) - main), abs(laeq(blocks, startBlock + shift, windowBlocks) - main))
        }
        return WindowAnalysis(
            intervalSec = windowBlocks * BLOCK_SEC,
            laeqDbFs = main,
            oneSecondLevelsDbFs = seconds,
            oneSecondStdDb = std,
            oneSecondSpreadDb = seconds.max() - seconds.min(),
            timingSensitivityDb = sens,
            timingShiftAvailableSec = shift * BLOCK_SEC,
            clipped = clipped,
        )
    }

    /** 15 s 기본, 변동 시 30 s 재측정, 30 s에서도 변동 시 재측정 요청 (V2 §5) */
    fun decide(a: WindowAnalysis, mode: CalibrationMode, t: CalibrationThresholds = CalibrationThresholds()): WindowDecision {
        val stdLimit = if (mode == CalibrationMode.SIMPLE_INSTANT) t.simpleModeMaxStdDb else t.maxOneSecondStdDb
        val unstable = a.oneSecondStdDb > stdLimit || a.timingSensitivityDb > t.maxTimingSensitivityDb || a.timingShiftAvailableSec < 1.0
        return when {
            !unstable -> WindowDecision.OK
            a.intervalSec < 30.0 - 1e-6 -> WindowDecision.EXTEND_TO_30S
            else -> WindowDecision.REMEASURE
        }
    }
}

@Serializable
data class ReferenceInstrument(
    val model: String,
    val serial: String,
    /** 최근 교정(정도검사) 일자 — 사용자 입력 */
    val calibrationDate: String,
    val weighting: String = "A",
    val measurementMode: String = "",
)

/** 기준 소음계 판독값 (OCR 또는 수동) — 반드시 사용자 확인 후 채택 (V2 §5) */
@Serializable
data class ReferenceReading(
    val valueDb: Double,
    val source: ValueSource,
    val readingType: ReadingType,
    val weightingConfirmed: String,
    /** 소음계에서 실제 적분한 시간(s) */
    val meterIntervalSec: Double,
    val userConfirmed: Boolean,
    val ocrUncertain: Boolean = false,
    val ocrConfidence: Double? = null,
    val ocrRawText: String? = null,
    val photoFile: String? = null,
    /** 촬영 시점 — 적분구간과 별도 기록 (V2 §5) */
    val photoCapturedAtEpochMs: Long? = null,
)

@Serializable
enum class BlockReason(val message: String) {
    WEIGHTING_MISMATCH("가중특성이 A가 아님"),
    READING_TYPE_MISMATCH("LAeq와 순간값/최대값 혼용"),
    INTERVAL_MISMATCH("소음계와 앱의 측정구간 길이 불일치"),
    VALUE_UNCONFIRMED("판독값 사용자 미확인"),
    OCR_UNCERTAIN("화면 판독 불확실 — 값을 직접 입력해 확인 필요"),
    INPUT_PATH_CHANGED("측정 중 입력 경로 변경(외장 마이크 탈착 등)"),
    PREPROCESSING_ACTIVE("AGC/잡음억제 해제 실패"),
    CLIPPING("입력 포화(클리핑)"),
    REFERENCE_INFO_MISSING("기준장비 모델·일련번호·교정일 누락"),
    VALUE_OUT_OF_RANGE("기준값이 유효 범위를 벗어남"),
    UNSTABLE("소음 변동 또는 시간 불일치 영향 큼"),
}

/** 자동 확정 금지 조건 검사 (V2 §7). 빈 목록이어야 오프셋 산출·확정 가능. */
object SafetyGate {
    fun check(
        mode: CalibrationMode,
        reading: ReferenceReading,
        instrument: ReferenceInstrument,
        analysis: WindowAnalysis,
        decision: WindowDecision,
        preprocessing: PreprocessingStatus,
        routingChanged: Boolean,
        t: CalibrationThresholds = CalibrationThresholds(),
    ): List<BlockReason> {
        val r = ArrayList<BlockReason>()
        if (!reading.weightingConfirmed.equals("A", ignoreCase = true) || !instrument.weighting.equals("A", ignoreCase = true)) {
            r += BlockReason.WEIGHTING_MISMATCH
        }
        val expectedType = if (mode == CalibrationMode.LAEQ_COMPARISON) ReadingType.LAEQ_INTERVAL else ReadingType.INSTANTANEOUS
        if (reading.readingType != expectedType) r += BlockReason.READING_TYPE_MISMATCH
        if (mode == CalibrationMode.LAEQ_COMPARISON && abs(reading.meterIntervalSec - analysis.intervalSec) > t.intervalMatchToleranceSec) {
            r += BlockReason.INTERVAL_MISMATCH
        }
        if (!reading.userConfirmed) r += BlockReason.VALUE_UNCONFIRMED
        if (reading.source == ValueSource.OCR && reading.ocrUncertain) r += BlockReason.OCR_UNCERTAIN
        if (routingChanged) r += BlockReason.INPUT_PATH_CHANGED
        if (preprocessing.agcDisabled == false || preprocessing.noiseSuppressorDisabled == false) r += BlockReason.PREPROCESSING_ACTIVE
        if (analysis.clipped) r += BlockReason.CLIPPING
        if (instrument.model.isBlank() || instrument.serial.isBlank() || instrument.calibrationDate.isBlank()) r += BlockReason.REFERENCE_INFO_MISSING
        if (reading.valueDb < t.referenceMinDb || reading.valueDb > t.referenceMaxDb) r += BlockReason.VALUE_OUT_OF_RANGE
        if (decision != WindowDecision.OK) r += BlockReason.UNSTABLE
        return r
    }
}

@Serializable
data class ComparisonRecord(
    val analysis: WindowAnalysis,
    val decision: WindowDecision,
    val reading: ReferenceReading,
    val windowStartEpochMs: Long,
    val windowEndEpochMs: Long,
    val blockReasons: List<BlockReason>,
)

@Serializable
data class ValidationRecord(
    val comparison: ComparisonRecord,
    val predictedDb: Double,
    val errorDb: Double,
    val toleranceDb: Double,
    val passed: Boolean,
)

object OffsetCalculator {
    /** C = LAeq,reference − LAeq,app  (앱 값은 dBFS(A)이므로 C는 dBFS→dB 환산 오프셋) */
    fun offset(referenceDb: Double, appDbFs: Double): Double = referenceDb - appDbFs

    fun validate(offsetDb: Double, comparison: ComparisonRecord, t: CalibrationThresholds = CalibrationThresholds()): ValidationRecord {
        val predicted = comparison.analysis.laeqDbFs + offsetDb
        val err = predicted - comparison.reading.valueDb
        val passed = comparison.blockReasons.isEmpty() && abs(err) <= t.validationToleranceDb
        return ValidationRecord(comparison, predicted, err, t.validationToleranceDb, passed)
    }
}

@Serializable
enum class ProfileStatus { PENDING_VALIDATION, ACTIVE, REJECTED, SUPERSEDED, INVALIDATED }

@Serializable
data class ApprovalEvent(val atEpochMs: Long, val action: String, val note: String = "")

/** 보정 프로파일 (V2 §6 필드). 원본값(dBFS)과 보정값을 모두 보존한다. */
@Serializable
data class CalibrationProfile(
    val schemaVersion: Int = 1,
    val profileId: String,
    val createdAtEpochMs: Long,
    val phoneManufacturer: String,
    val phoneModel: String,
    val osRelease: String,
    val sdkInt: Int,
    val buildFingerprint: String,
    val micType: String,
    val inputDeviceKey: String,
    val inputDeviceName: String,
    val audioSource: String,
    val sampleRate: Int,
    val preprocessing: PreprocessingStatus,
    val instrument: ReferenceInstrument,
    val mode: CalibrationMode,
    val noiseSourceDescription: String,
    val comparison: ComparisonRecord,
    val offsetDb: Double,
    val validation: ValidationRecord? = null,
    val status: ProfileStatus,
    val thresholds: CalibrationThresholds,
    val approvals: List<ApprovalEvent> = emptyList(),
    val dataFiles: List<String> = emptyList(),
    val limitations: List<String> = DEFAULT_LIMITATIONS,
) {
    fun toCalibration(): Calibration = Calibration(
        state = CalibrationState.REFERENCE_COMPARED,
        offsetDb = offsetDb,
        inputDeviceKey = inputDeviceKey,
        inputDeviceName = inputDeviceName,
        audioSource = audioSource,
        referenceInstrument = "${instrument.model} (S/N ${instrument.serial}, 교정일 ${instrument.calibrationDate})",
        referenceValueDb = comparison.reading.valueDb,
        measuredDbFs = comparison.analysis.laeqDbFs,
        calibratedAtEpochMs = createdAtEpochMs,
        note = "스마트 비교보정 V2 · ${if (mode == CalibrationMode.SIMPLE_INSTANT) "간편 비교모드(정확도 제한)" else "LAeq 구간 비교"}",
        profileId = profileId,
    )

    companion object {
        val DEFAULT_LIMITATIONS = listOf(
            "공인 교정 및 마이크 주파수응답 교정을 대체하지 않음",
            "보정 시의 소음 레벨·스펙트럼 조건 근처에서만 유효",
            "입력 장치·오디오 소스·샘플레이트·OS 변경 시 무효",
        )
    }
}

@Serializable
enum class InvalidReason(val message: String) {
    NOT_ACTIVE("프로파일이 활성 상태가 아님"),
    DEVICE_CHANGED("입력 장치 변경(외장 마이크 탈착 포함)"),
    SOURCE_CHANGED("오디오 소스 변경"),
    SAMPLE_RATE_CHANGED("샘플레이트 변경"),
    OS_CHANGED("OS 빌드 변경"),
    PREPROCESSING_CHANGED("전처리(AGC/NS) 상태 변경"),
}

object ProfileValidity {
    fun check(p: CalibrationProfile, current: AudioConfig, buildFingerprint: String): List<InvalidReason> {
        val r = ArrayList<InvalidReason>()
        if (p.status != ProfileStatus.ACTIVE) r += InvalidReason.NOT_ACTIVE
        if (p.inputDeviceKey != current.inputDeviceKey) r += InvalidReason.DEVICE_CHANGED
        if (p.audioSource != current.audioSource) r += InvalidReason.SOURCE_CHANGED
        if (p.sampleRate != current.sampleRate) r += InvalidReason.SAMPLE_RATE_CHANGED
        if (p.buildFingerprint != buildFingerprint) r += InvalidReason.OS_CHANGED
        val a = p.preprocessing
        val b = current.preprocessing
        if (a.agcDisabled != b.agcDisabled || a.noiseSuppressorDisabled != b.noiseSuppressorDisabled) r += InvalidReason.PREPROCESSING_CHANGED
        return r
    }
}
