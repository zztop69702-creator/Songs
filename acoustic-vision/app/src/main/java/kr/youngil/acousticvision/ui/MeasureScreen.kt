package kr.youngil.acousticvision.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kr.youngil.acousticvision.audio.AudioEngine
import kr.youngil.acousticvision.camera.CameraPreview
import kr.youngil.acousticvision.camera.PreviewHolder
import kr.youngil.acousticvision.core.dsp.FanFrequencies
import kr.youngil.acousticvision.core.dsp.PeakDetector
import kr.youngil.acousticvision.core.dsp.ThirdOctave
import kr.youngil.acousticvision.core.model.BandRecord
import kr.youngil.acousticvision.core.model.Calibration
import kr.youngil.acousticvision.core.model.CalibrationState
import kr.youngil.acousticvision.core.model.DeviceInfo
import kr.youngil.acousticvision.core.model.DistanceReading
import kr.youngil.acousticvision.core.model.DistanceSource
import kr.youngil.acousticvision.core.model.LevelResult
import kr.youngil.acousticvision.core.model.MarkerSource
import kr.youngil.acousticvision.core.model.Measurement
import kr.youngil.acousticvision.core.model.PeakRecord
import kr.youngil.acousticvision.core.model.Project
import kr.youngil.acousticvision.core.model.SourceMarker
import kr.youngil.acousticvision.core.model.ToneMatchRecord
import kr.youngil.acousticvision.data.Repository
import java.util.UUID

/** 교정은 같은 입력 경로 + 같은 오디오 소스일 때만 유효 */
fun Calibration?.validFor(deviceKey: String, audioSource: String): Calibration? =
    this?.takeIf { it.isCalibrated && it.inputDeviceKey == deviceKey && it.audioSource == audioSource }

@Composable
fun MeasureScreen(repo: Repository, engine: AudioEngine, nav: Navigator, projectId: String) {
    val perm = rememberAvPermissions()
    var project by remember { mutableStateOf<Project?>(null) }
    LaunchedEffect(projectId) { project = repo.loadProject(projectId) }
    val p = project ?: return

    Column(Modifier.fillMaxSize()) {
        TopBar("${p.siteName} · ${p.equipmentName}", onBack = { nav.pop() })
        if (!perm.audio) {
            AudioPermissionMissing(perm)
        } else {
            MeasureContent(repo, engine, nav, p, cameraAllowed = perm.camera)
        }
    }
}

@Composable
private fun MeasureContent(repo: Repository, engine: AudioEngine, nav: Navigator, p: Project, cameraAllowed: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val ui by engine.state.collectAsStateWithLifecycle()
    val holder = remember { PreviewHolder() }
    val markers = remember { mutableStateListOf<SourceMarker>() }
    var selected by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var calibration by remember { mutableStateOf<Calibration?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // 화면이 보일 때만 마이크 동작 (백그라운드·중단 시 정지)
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> engine.start()
                Lifecycle.Event.ON_STOP -> engine.stop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) engine.start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            engine.stop()
        }
    }
    val cfg = ui.config
    // 보정 적용: 유효한 V2 활성 프로파일 또는 음향교정기 보정 중 최신. 입력 경로가 바뀌면 다시 판정(무효화)
    LaunchedEffect(cfg?.inputDeviceKey, cfg?.audioSource, ui.routingChanges) {
        calibration = cfg?.takeIf { ui.routingChanges == 0 }?.let { c ->
            val profile = repo.activeProfileFor(c, Build.FINGERPRINT)?.toCalibration()
            val calibrator = repo.loadCalibration(c.inputDeviceKey).validFor(c.inputDeviceKey, c.audioSource)
                ?.takeIf { it.state == CalibrationState.CALIBRATOR_VERIFIED }
            listOfNotNull(profile, calibrator).maxByOrNull { it.calibratedAtEpochMs ?: 0L }
        }
    }
    val offset = calibration?.offsetDb
    val tones = remember(p.rpm, p.bladeCount) { p.rpm?.let { FanFrequencies.tones(it, p.bladeCount) } ?: emptyList() }

    Column(Modifier.fillMaxSize()) {
        // ---- 카메라 + 마커 ----
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            if (cameraAllowed) {
                CameraPreview(holder, Modifier.fillMaxSize(), onError = { cameraError = it })
            } else {
                Text("카메라 권한 없음 — 영상 없이 측정합니다", color = AvColors.TextDim, modifier = Modifier.align(Alignment.Center))
            }
            cameraError?.let { Text(it, color = AvColors.Red, modifier = Modifier.align(Alignment.Center)) }
            MarkerLayer(markers, selected, onTap = { x, y, hit ->
                if (hit != null) {
                    selected = if (selected == hit) null else hit
                } else {
                    val n = markers.size + 1
                    val mk = SourceMarker(
                        id = UUID.randomUUID().toString(), source = MarkerSource.MANUAL, x = x, y = y,
                        label = "M$n", createdAtEpochMs = System.currentTimeMillis(),
                        limitations = listOf("수동 지정 위치 — 사용자의 판단이며 측정으로 확인된 음원 위치가 아님"),
                    )
                    markers.add(mk)
                    selected = mk.id
                }
            })
            // 레벨 오버레이
            Column(
                Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(8.dp),
            ) {
                Text(Fmt.levelA(ui.levels?.lafDbFs, offset), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("LAF (Fast)", color = AvColors.TextDim, fontSize = 11.sp)
                CalibrationTag(calibration)
            }
            Text(
                "화면을 눌러 수동 마커(빨간 점선) 추가",
                color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp).background(Color(0x88000000)).padding(4.dp),
            )
        }

        // ---- 스펙트럼 ----
        SpectrumChart(
            ui.displayFreqs, ui.displayDb, (cfg?.sampleRate ?: 48000) / 2.0, ui.peaks, tones,
            Modifier.fillMaxWidth().height(170.dp).background(AvColors.NavyLight),
        )

        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
            cfg?.let {
                Text(
                    "fs ${it.sampleRate} Hz · FFT ${it.fftSize} · ${it.window} · Δf ${Fmt.num(it.binHz, 2)} Hz · ${it.audioSource} · ${it.inputDeviceName}",
                    color = AvColors.TextDim, fontSize = 10.sp,
                )
            }
            val lv = ui.levels
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LevelCell("LAeq", Fmt.levelA(lv?.laeqDbFs, offset))
                LevelCell("LAFmax", Fmt.levelA(lv?.lafMaxDbFs, offset))
                LevelCell("LAFmin", Fmt.levelA(lv?.lafMinDbFs, offset))
                LevelCell("시간", lv?.let { "${Fmt.num(it.durationSec, 0)} s" } ?: "—")
            }
            if (tones.isNotEmpty() && ui.peaks.isNotEmpty() && cfg != null) {
                val m = FanFrequencies.match(tones, ui.peaks, cfg.binHz).filter { it.peak != null }
                if (m.isNotEmpty()) Text(
                    "후보 일치: " + m.joinToString { "${it.tone.label} ${Fmt.hz(it.tone.frequencyHz)}→${Fmt.hz(it.peak!!.frequencyHz)}" },
                    color = AvColors.Blue, fontSize = 11.sp,
                )
            }
            // 경고
            ui.error?.let { Text("⚠ $it", color = AvColors.Red, fontSize = 12.sp) }
            if (ui.routingChanges > 0) Text("⚠ 측정 중 입력 경로 변경(외장 마이크 탈착 등) — 보정 무효. 화면을 다시 열어 재측정하세요", color = AvColors.Red, fontSize = 12.sp)
            if (ui.silenced) Text("⚠ 다른 앱/통화가 마이크를 사용 중 — 입력이 무음 처리됨. 측정값 무효", color = AvColors.Red, fontSize = 12.sp)
            if (ui.clippingRecent) Text("⚠ 입력 포화(클리핑) — 레벨이 실제보다 낮게 계산될 수 있음", color = AvColors.Red, fontSize = 12.sp)
            cfg?.preprocessing?.let { pp ->
                if (pp.agcDisabled == false || pp.noiseSuppressorDisabled == false) {
                    Text("⚠ 기기 전처리(AGC/NS) 해제 실패 — 레벨·스펙트럼 왜곡 가능", color = AvColors.Warn, fontSize = 12.sp)
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) Text("Android 10 미만: 마이크 무음 처리 감지 불가", color = AvColors.TextDim, fontSize = 10.sp)
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { engine.resetMeasurement() }, modifier = Modifier.weight(1f)) { Text("초기화") }
            OutlinedButton(
                onClick = { selected?.let { id -> markers.removeAll { it.id == id } }; selected = null },
                enabled = selected != null, modifier = Modifier.weight(1f),
            ) { Text("마커 삭제") }
            Button(onClick = { showSave = true }, enabled = ui.running && ui.levels != null, modifier = Modifier.weight(1f)) { Text("저장") }
        }
    }

    if (showSave) {
        SaveDialog(
            defaultPosition = p.defaultPosition,
            error = saveError,
            onDismiss = { showSave = false; saveError = null },
            onSave = { position, distanceText, notes ->
                val snap = engine.snapshot()
                if (snap == null) { saveError = "분석 데이터가 없습니다"; return@SaveDialog }
                if (snap.levels.durationSec < 3.0) { saveError = "최소 3초 이상 측정 후 저장하세요"; return@SaveDialog }
                if (repo.freeBytes() < 50L * 1024 * 1024) { saveError = "저장공간 부족(50 MB 미만) — 공간 확보 후 다시 저장하세요"; return@SaveDialog }
                val spec = snap.averagedSpectrum
                val peaks = spec?.let { PeakDetector.find(it, maxPeaks = 10) } ?: emptyList()
                val bands = spec?.let { ThirdOctave.bands(it) } ?: emptyList()
                val matches = if (spec != null && tones.isNotEmpty()) FanFrequencies.match(tones, peaks, spec.binHz) else emptyList()
                val distance = distanceText.trim().takeIf { it.isNotEmpty() }?.let {
                    val v = it.toDoubleOrNull()
                    if (v == null || v <= 0) { saveError = "거리는 0보다 큰 숫자(m)로 입력하세요"; return@SaveDialog }
                    DistanceReading(DistanceSource.MANUAL, v, note = "사용자 수동 입력 · 표면까지의 거리이며 실제 음원 거리와 다를 수 있음")
                }
                val cal = calibration ?: Calibration.uncalibrated(snap.config.inputDeviceKey, snap.config.inputDeviceName, snap.config.audioSource)
                val id = "m" + System.currentTimeMillis()
                val bitmap = holder.snapshot()
                val m = Measurement(
                    id = id, projectId = p.id, createdAtEpochMs = System.currentTimeMillis(),
                    positionLabel = position.trim(), stage = p.stage, operatingCondition = p.operatingCondition,
                    rpm = p.rpm, bladeCount = p.bladeCount,
                    device = DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                    audio = snap.config,
                    calibration = cal,
                    level = LevelResult(
                        laeqDbFs = snap.levels.laeqDbFs, lafMaxDbFs = snap.levels.lafMaxDbFs, lafMinDbFs = snap.levels.lafMinDbFs,
                        lzeqDbFs = snap.levels.lzeqDbFs, samplePeakDbFs = snap.levels.samplePeakDbFs,
                        durationSec = snap.levels.durationSec, clippedSamples = snap.levels.clippedSamples,
                        calibrationOffsetDb = cal.offsetDb,
                    ),
                    peaks = peaks.map { PeakRecord(it.frequencyHz, it.levelDbFs) },
                    thirdOctave = bands.map { BandRecord(it.nominalLabel, it.centerHz, it.levelZDbFs, it.levelADbFs, it.reliable) },
                    toneMatches = matches.map { ToneMatchRecord(it.tone.label, it.tone.frequencyHz, it.peak?.frequencyHz, it.peak?.levelDbFs) },
                    markers = markers.toList(),
                    distance = distance,
                    snapshotFile = bitmap?.let { "$id.jpg" },
                    notes = notes.trim() + (if (ui.silenced) " [경고: 저장 시점 마이크 무음 처리 감지]" else "") +
                        (if (ui.routingChanges > 0) " [경고: 측정 중 입력 경로 변경]" else ""),
                )
                scope.launch {
                    try {
                        repo.saveMeasurement(m, bitmap)
                        showSave = false
                        markers.clear(); selected = null
                        engine.resetMeasurement()
                        nav.push(Route.MeasurementView(p.id, id))
                    } catch (e: Exception) {
                        saveError = "저장 실패: ${e.message}"
                    }
                }
            },
        )
    }
}

@Composable
private fun LevelCell(label: String, value: String) {
    Column {
        Text(label, color = AvColors.TextDim, fontSize = 10.sp)
        Text(value, color = AvColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SaveDialog(
    defaultPosition: String,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (position: String, distance: String, notes: String) -> Unit,
) {
    var position by remember { mutableStateOf(defaultPosition) }
    var distance by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("측정 저장") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(position, { position = it }, label = { Text("측정 위치") }, singleLine = true)
                OutlinedTextField(
                    distance, { distance = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("측정 거리 m (수동 입력, 선택)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("거리 출처: 수동 입력. 비워두면 거리 없음으로 저장(값을 생성하지 않음).", fontSize = 12.sp)
                OutlinedTextField(notes, { notes = it }, label = { Text("메모") })
                Text("저장 내용: 측정 시작(초기화) 이후 LAeq·Lmax·Lmin, 평균 스펙트럼 피크, 1/3 옥타브, 마커, 화면 스냅샷", fontSize = 12.sp)
                error?.let { Text(it, color = AvColors.Red) }
            }
        },
        confirmButton = { Button(onClick = { onSave(position, distance, notes) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
