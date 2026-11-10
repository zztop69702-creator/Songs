package kr.youngil.acousticvision.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 값의 출처. UI·저장·보고서에서 항상 구분한다. */
@Serializable
enum class DataOrigin { MEASURED, ESTIMATED, MANUAL, SIMULATED }

/** 음원 마커 출처: AUTO=빨간 실선, MANUAL=빨간 점선, EXPERT_EDITED=노란색 */
@Serializable
enum class MarkerSource { AUTO, MANUAL, EXPERT_EDITED }

@Serializable
enum class DistanceSource { ARCORE_DEPTH, EXTERNAL_LASER, MANUAL }

@Serializable
enum class CalibrationState { UNCALIBRATED, REFERENCE_COMPARED, CALIBRATOR_VERIFIED }

@Serializable
enum class ImprovementStage { BEFORE, AFTER, NOT_APPLICABLE }

@Serializable
data class Project(
    val id: String,
    val siteName: String,
    val equipmentName: String,
    val operatingCondition: String = "",
    val rpm: Double? = null,
    val bladeCount: Int? = null,
    val defaultPosition: String = "",
    val stage: ImprovementStage = ImprovementStage.NOT_APPLICABLE,
    /** 학교(기관)의 촬영 승인 확인 여부 — 개인정보 보호 */
    val filmingApprovalConfirmed: Boolean = false,
    val notes: String = "",
    val createdAtEpochMs: Long,
) {
    init {
        require(siteName.isNotBlank()) { "현장명은 필수입니다" }
        require(equipmentName.isNotBlank()) { "설비명은 필수입니다" }
        require(rpm == null || rpm > 0) { "rpm은 0보다 커야 합니다" }
        require(bladeCount == null || bladeCount > 0) { "날개 수는 0보다 커야 합니다" }
    }
}

@Serializable
data class Calibration(
    val state: CalibrationState = CalibrationState.UNCALIBRATED,
    /** dB 참고값 = dBFS + offsetDb. 미교정이면 null */
    val offsetDb: Double? = null,
    /** 교정이 유효한 입력 경로 식별자 (예: "builtin", "usb:장치명") */
    val inputDeviceKey: String = "builtin",
    val inputDeviceName: String = "",
    val audioSource: String = "",
    val referenceInstrument: String? = null,
    val referenceValueDb: Double? = null,
    val measuredDbFs: Double? = null,
    val calibratedAtEpochMs: Long? = null,
    val note: String = "",
    /** 스마트 비교보정(V2) 프로파일에서 온 경우 해당 profileId */
    val profileId: String? = null,
) {
    init {
        require((state == CalibrationState.UNCALIBRATED) == (offsetDb == null)) {
            "미교정 상태에는 오프셋이 없어야 하고, 교정 상태에는 오프셋이 있어야 합니다"
        }
    }

    val isCalibrated: Boolean get() = state != CalibrationState.UNCALIBRATED

    companion object {
        fun uncalibrated(deviceKey: String, deviceName: String, source: String) =
            Calibration(inputDeviceKey = deviceKey, inputDeviceName = deviceName, audioSource = source)
    }
}

@Serializable
data class PreprocessingStatus(
    val unprocessedSourceSupported: Boolean?,
    val agcAvailable: Boolean,
    val agcDisabled: Boolean?,
    val noiseSuppressorAvailable: Boolean,
    val noiseSuppressorDisabled: Boolean?,
    val echoCancelerAvailable: Boolean,
    val echoCancelerDisabled: Boolean?,
    /** API 응답만으로는 실제 처리 해제를 보장할 수 없음 — 레벨 스텝 시험(P0-04) 통과 시에만 true */
    val verifiedByStepTest: Boolean = false,
)

@Serializable
data class AudioConfig(
    val audioSource: String,
    val sampleRate: Int,
    val encoding: String,
    val channelCount: Int,
    val fftSize: Int,
    val window: String,
    val binHz: Double,
    val enbwHz: Double,
    val inputDeviceKey: String,
    val inputDeviceName: String,
    val preprocessing: PreprocessingStatus,
)

/**
 * 소음도 결과. raw 값은 항상 dBFS(AES17 상대값)로 저장하고,
 * 교정 오프셋이 있을 때만 참고 dB 값을 함께 계산해 둔다.
 */
@Serializable
data class LevelResult(
    val origin: DataOrigin = DataOrigin.MEASURED,
    val laeqDbFs: Double?,
    val lafMaxDbFs: Double?,
    val lafMinDbFs: Double?,
    val lzeqDbFs: Double?,
    val samplePeakDbFs: Double,
    val durationSec: Double,
    val clippedSamples: Long,
    val calibrationOffsetDb: Double?,
) {
    val laeqRefDb: Double? get() = calibrationOffsetDb?.let { off -> laeqDbFs?.plus(off) }
    val lafMaxRefDb: Double? get() = calibrationOffsetDb?.let { off -> lafMaxDbFs?.plus(off) }
    val lafMinRefDb: Double? get() = calibrationOffsetDb?.let { off -> lafMinDbFs?.plus(off) }
}

@Serializable
data class PeakRecord(val frequencyHz: Double, val levelDbFs: Double)

@Serializable
data class BandRecord(val label: String, val centerHz: Double, val levelZDbFs: Double, val levelADbFs: Double, val reliable: Boolean)

@Serializable
data class ToneMatchRecord(val label: String, val expectedHz: Double, val matchedHz: Double?, val levelDbFs: Double?)

/** AUTO/HYBRID 원래 추정값 (Phase 3, 동기화 마이크 배열 연결 시에만 생성) */
@Serializable
data class AutoEstimate(
    val azimuthDeg: Double,
    val elevationDeg: Double,
    val bandLowHz: Double,
    val bandHighHz: Double,
    val method: String,
    val confidence: Double,
    val arrayId: String,
)

@Serializable
data class MarkerEdit(
    val atEpochMs: Long,
    val editor: String,
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val note: String = "",
)

@Serializable
data class SourceMarker(
    val id: String,
    val source: MarkerSource,
    /** 화면(스냅샷) 기준 정규화 좌표 0..1 */
    val x: Float,
    val y: Float,
    val label: String,
    val createdAtEpochMs: Long,
    val originalEstimate: AutoEstimate? = null,
    val edits: List<MarkerEdit> = emptyList(),
    val limitations: List<String> = emptyList(),
) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "마커 좌표는 0..1 범위여야 합니다" }
        require(source != MarkerSource.AUTO || originalEstimate != null) { "AUTO 마커는 원래 추정값이 필수입니다" }
    }
}

@Serializable
data class DistanceReading(
    val source: DistanceSource,
    /** 측정 실패 시 null — 값 생성 금지 */
    val meters: Double?,
    val targetX: Float? = null,
    val targetY: Float? = null,
    val confidence: Double? = null,
    val failureReason: String? = null,
    val note: String = "표면까지의 거리이며 실제 음원 거리와 다를 수 있음",
) {
    init {
        require(meters == null || meters > 0) { "거리는 0보다 커야 합니다" }
        require(meters != null || !failureReason.isNullOrBlank()) { "거리값이 없으면 실패 사유가 필요합니다" }
    }
}

@Serializable
data class DeviceInfo(val manufacturer: String, val model: String, val androidRelease: String, val sdkInt: Int)

@Serializable
data class Measurement(
    val schemaVersion: Int = 1,
    val id: String,
    val projectId: String,
    val createdAtEpochMs: Long,
    val positionLabel: String,
    val stage: ImprovementStage,
    val operatingCondition: String,
    val rpm: Double?,
    val bladeCount: Int?,
    val device: DeviceInfo,
    val audio: AudioConfig,
    val calibration: Calibration,
    val level: LevelResult,
    val peaks: List<PeakRecord>,
    val thirdOctave: List<BandRecord>,
    val toneMatches: List<ToneMatchRecord>,
    val markers: List<SourceMarker>,
    val distance: DistanceReading?,
    val snapshotFile: String?,
    val notes: String = "",
    val disclaimer: String = DISCLAIMER,
) {
    companion object {
        const val DISCLAIMER =
            "본 값은 스마트폰 기반 참고값이며 KS C IEC 61672-1 소음계에 의한 공인 측정을 대체하지 않습니다. " +
                "FFT 레벨은 dBFS(디지털 상대값)입니다."
    }
}

val AppJson: Json = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}
