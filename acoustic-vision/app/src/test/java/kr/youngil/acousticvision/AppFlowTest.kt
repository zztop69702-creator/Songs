package kr.youngil.acousticvision

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kr.youngil.acousticvision.core.model.AudioConfig
import kr.youngil.acousticvision.core.model.Calibration
import kr.youngil.acousticvision.core.model.DeviceInfo
import kr.youngil.acousticvision.core.model.ImprovementStage
import kr.youngil.acousticvision.core.model.LevelResult
import kr.youngil.acousticvision.core.model.MarkerSource
import kr.youngil.acousticvision.core.model.Measurement
import kr.youngil.acousticvision.core.model.PreprocessingStatus
import kr.youngil.acousticvision.core.model.Project
import kr.youngil.acousticvision.core.model.SourceMarker
import kr.youngil.acousticvision.data.Repository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric(JVM) 화면 흐름 스모크 시험.
 * 실제 카메라·마이크·화면 렌더링은 검증하지 않는다 (실기기 시험 대체 아님).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    /** 파일 IO가 비동기라 화면 표시를 기다린다 (Compose 시험 도구는 Dispatchers.IO를 대기하지 않음) */
    private fun waitText(text: String, substring: Boolean = false) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun grantAll() = shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)

    @Test fun homeShowsDisclaimerAndEmptyState() {
        grantAll()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("YOUNGIL Acoustic Vision").assertIsDisplayed()
            compose.onNodeWithText("공인 소음계 측정을 대체하지 않음", substring = true).assertIsDisplayed()
            waitText("등록된 현장이 없습니다", substring = true)
            compose.onNodeWithText("등록된 현장이 없습니다", substring = true).assertIsDisplayed()
        }
    }

    @Test fun createProjectValidatesAndSaves() {
        grantAll()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("+ 새 현장").performClick()
            compose.onNodeWithText("저장").performScrollTo().performClick()
            compose.onNodeWithText("현장명은 필수입니다").assertIsDisplayed()

            compose.onNode(hasSetTextAction() and hasText("학교/현장명 *")).performTextInput("시험초등학교")
            compose.onNode(hasSetTextAction() and hasText("설비명 * (예: 급식실 배기팬 EF-1)")).performTextInput("급식실 배기팬 EF-1")
            compose.onNode(hasSetTextAction() and hasText("회전수 RPM")).performTextInput("1450")
            compose.onNode(hasSetTextAction() and hasText("날개 수 Z")).performTextInput("40")
            compose.onNodeWithText("저장").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("측정 시작")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("RPM 1450 · 날개 40").assertIsDisplayed()
            compose.onNodeWithText("촬영 승인 미확인", substring = true).assertIsDisplayed()
        }
        val saved = runBlocking { Repository(app).listProjects() }
        assertEquals(1, saved.size)
        assertEquals(40, saved.first().bladeCount)
    }

    @Test fun savedMeasurementAndManualMarkerAreRedisplayed() {
        grantAll()
        val repo = Repository(app)
        val p = Project(id = "p1", siteName = "시험중학교", equipmentName = "배기팬", rpm = 1750.0, bladeCount = 6, createdAtEpochMs = 1L)
        val m = Measurement(
            id = "m1", projectId = "p1", createdAtEpochMs = 2L, positionLabel = "흡입구 1 m", stage = ImprovementStage.BEFORE,
            operatingCondition = "", rpm = 1750.0, bladeCount = 6, device = DeviceInfo("T", "M", "15", 35),
            audio = AudioConfig(
                "VOICE_RECOGNITION", 48000, "PCM_FLOAT", 1, 8192, "Hann", 5.86, 8.79, "builtin", "내장 마이크",
                PreprocessingStatus(false, false, null, false, null, false, null),
            ),
            calibration = Calibration.uncalibrated("builtin", "내장 마이크", "VOICE_RECOGNITION"),
            level = LevelResult(
                laeqDbFs = -40.0, lafMaxDbFs = -35.0, lafMinDbFs = -45.0, lzeqDbFs = -30.0, samplePeakDbFs = -20.0,
                durationSec = 10.0, clippedSamples = 0, calibrationOffsetDb = null,
            ),
            peaks = emptyList(), thirdOctave = emptyList(), toneMatches = emptyList(),
            markers = listOf(SourceMarker("k1", MarkerSource.MANUAL, 0.3f, 0.6f, "M1", 3L)),
            distance = null, snapshotFile = null,
        )
        runBlocking { repo.saveProject(p); repo.saveMeasurement(m, null) }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("시험중학교")
            compose.onNodeWithText("시험중학교").performClick()
            waitText("LAeq -40.0 dBFS(A)")
            compose.onNodeWithText("LAeq -40.0 dBFS(A)").assertIsDisplayed()
            compose.onNodeWithText("흡입구 1 m").performClick()
            waitText("미보정 · 상대값(dBFS)")
            compose.onNodeWithText("미보정 · 상대값(dBFS)").assertIsDisplayed()
            compose.onNodeWithText("M1 · MANUAL").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("x 0.30, y 0.60").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("거리 없음 (입력하지 않음)").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun duplicateMeasurementIdIsRejected() {
        val repo = Repository(app)
        val m = Measurement(
            id = "dup", projectId = "p", createdAtEpochMs = 0, positionLabel = "", stage = ImprovementStage.NOT_APPLICABLE,
            operatingCondition = "", rpm = null, bladeCount = null, device = DeviceInfo("", "", "", 0),
            audio = AudioConfig("", 48000, "", 1, 8192, "Hann", 1.0, 1.0, "builtin", "", PreprocessingStatus(null, false, null, false, null, false, null)),
            calibration = Calibration(),
            level = LevelResult(laeqDbFs = null, lafMaxDbFs = null, lafMinDbFs = null, lzeqDbFs = null, samplePeakDbFs = 0.0, durationSec = 0.0, clippedSamples = 0, calibrationOffsetDb = null),
            peaks = emptyList(), thirdOctave = emptyList(), toneMatches = emptyList(), markers = emptyList(), distance = null, snapshotFile = null,
        )
        runBlocking { repo.saveMeasurement(m, null) }
        val failed = runCatching { runBlocking { repo.saveMeasurement(m, null) } }.isFailure
        assertTrue("원본 측정 파일은 덮어쓰지 않아야 함", failed)
    }

    @Test fun diagnosticsProducesJson() {
        grantAll()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("기기 진단").performClick()
            compose.onNodeWithText("진단 실행").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("결과 공유")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("youngil-acoustic-vision-diagnostics/1", substring = true).assertIsDisplayed()
        }
    }

    @Test fun measureScreenOpensWithoutCrashWhenHardwareUnavailable() {
        grantAll()
        val repo = Repository(app)
        runBlocking { repo.saveProject(Project(id = "p2", siteName = "시험고등학교", equipmentName = "급기팬", createdAtEpochMs = 1L)) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("시험고등학교")
            compose.onNodeWithText("시험고등학교").performClick()
            waitText("측정 시작")
            compose.onNodeWithText("측정 시작").performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("LAeq")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("LAeq").assertIsDisplayed()
            compose.onNodeWithText("화면을 눌러 수동 마커(빨간 점선) 추가").assertIsDisplayed()
        }
    }

    // V2-R01: 보정 허브 → 스마트 비교보정 설정 단계. 필수정보·배치 확인 전에는 측정으로 넘어갈 수 없음
    @Test fun smartCalibrationSetupRequiresInstrumentInfoAndPlacement() {
        grantAll()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("보정").performClick()
            waitText("저장된 프로파일 없음")
            compose.onNodeWithText("저장된 프로파일 없음").assertIsDisplayed()
            compose.onNodeWithText("스마트 비교보정 (기준 소음계 LAeq)").performClick()
            compose.onNodeWithText("다음: 측정").performScrollTo().assertIsNotEnabled()
            compose.onNode(hasSetTextAction() and hasText("모델 *")).performTextInput("NL-42")
            compose.onNode(hasSetTextAction() and hasText("일련번호 *")).performTextInput("00123")
            compose.onNode(hasSetTextAction() and hasText("최근 교정(정도검사)일 * 예: 2026-03-15")).performTextInput("2026-03-15")
            compose.onNodeWithText("다음: 측정").performScrollTo().assertIsNotEnabled()
            // 기본 방식: 비적분형 화면 연속판독 (TES-1350A 등)
            compose.onNodeWithText("음원에서 같은 거리", substring = true).performScrollTo().performClick()
            compose.onNodeWithText("다음: 측정").performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithText("① 보정 측정").assertIsDisplayed()
            compose.onNodeWithText("측정 시작").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("노란 상자", substring = true).assertExists()
        }
    }

    // V3: 앱 정보·백업 화면 (버전·서명·내보내기 버튼)
    @Test fun appInfoShowsVersionAndBackupActions() {
        grantAll()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("정보·백업").performClick()
            compose.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
            compose.onNodeWithText("debug (설치 시험용 — 현장 사용 금지)").assertIsDisplayed()
            compose.onNodeWithText("전체 데이터 내보내기 (ZIP)").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("백업에서 복원 (기존 데이터 유지)").performScrollTo().assertIsDisplayed()
        }
    }

    // V3: Galaxy Z Fold5 펼친 화면 크기(추정 약 841×673 dp) 가정 — 주요 화면 진입 시 비정상 종료 없음
    @Test
    @Config(sdk = [35], qualifiers = "w841dp-h673dp-land-xhdpi")
    fun largeUnfoldedScreenMainFlowsDoNotCrash() {
        grantAll()
        runBlocking { Repository(app).saveProject(Project(id = "pf", siteName = "폴드시험학교", equipmentName = "배기팬", createdAtEpochMs = 1L)) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("폴드시험학교")
            compose.onNodeWithText("폴드시험학교").performClick()
            waitText("측정 시작")
            compose.onNodeWithText("측정 시작").performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("LAeq")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("LAeq").assertExists()
            compose.onNodeWithText("‹ 뒤로").performClick()
            compose.onNodeWithText("‹ 뒤로").performClick()
            compose.onNodeWithText("보정").performClick()
            compose.onNodeWithText("스마트 비교보정 (기준 소음계 LAeq)").assertExists()
        }
    }
}
