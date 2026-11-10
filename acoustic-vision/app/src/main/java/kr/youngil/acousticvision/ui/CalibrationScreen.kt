package kr.youngil.acousticvision.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kr.youngil.acousticvision.audio.AudioEngine
import kr.youngil.acousticvision.core.calibration.CalibrationProfile
import kr.youngil.acousticvision.core.calibration.ProfileStatus
import kr.youngil.acousticvision.core.model.Calibration
import kr.youngil.acousticvision.core.model.CalibrationState
import kr.youngil.acousticvision.data.Repository

/** 보정 허브: 스마트 비교보정(V2) 프로파일 목록 + 음향교정기 보정 진입 */
@Composable
fun CalibrationScreen(repo: Repository, nav: Navigator) {
    var profiles by remember { mutableStateOf<List<CalibrationProfile>>(emptyList()) }
    LaunchedEffect(nav.stack.size) { profiles = repo.listProfiles() }
    Column(Modifier.fillMaxSize()) {
        TopBar("보정", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "앱의 보정은 특정 입력 경로·조건에서의 레벨 오프셋 보정이며, 공인 교정이나 주파수응답 교정이 아닙니다. 보정 후에도 값은 ‘참고값’입니다.",
                color = AvColors.TextDim, fontSize = 12.sp,
            )
            Button(onClick = { nav.push(Route.SmartCalibration) }, modifier = Modifier.fillMaxWidth()) { Text("스마트 비교보정 (기준 소음계 LAeq)") }
            OutlinedButton(onClick = { nav.push(Route.CalibratorCalibration) }, modifier = Modifier.fillMaxWidth()) { Text("음향교정기 보정 (외장 측정 마이크)") }
            Section("보정 프로파일 (${profiles.size})")
            if (profiles.isEmpty()) Text("저장된 프로파일 없음", color = AvColors.TextDim)
            profiles.forEach { p ->
                Card(colors = CardDefaults.cardColors(containerColor = AvColors.NavyLight), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        val (label, color) = when (p.status) {
                            ProfileStatus.ACTIVE -> "활성" to AvColors.Ok
                            ProfileStatus.PENDING_VALIDATION -> if (p.validation?.passed == true) "검증 합격·승인 대기" to AvColors.Warn else "검증 대기" to AvColors.Warn
                            ProfileStatus.REJECTED -> "보류(검증 불합격)" to AvColors.Red
                            ProfileStatus.SUPERSEDED -> "대체됨" to AvColors.TextDim
                            ProfileStatus.INVALIDATED -> "무효" to AvColors.TextDim
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tag(label, color)
                            Text(p.profileId, color = AvColors.TextDim, fontSize = 12.sp)
                        }
                        Text("오프셋 ${Fmt.num(p.offsetDb, 2)} dB · ${p.inputDeviceName} · ${p.audioSource}", color = AvColors.Text, fontWeight = FontWeight.SemiBold)
                        Text("기준: ${p.instrument.model} S/N ${p.instrument.serial} (교정일 ${p.instrument.calibrationDate})", color = AvColors.TextDim, fontSize = 12.sp)
                        p.validation?.let { Text("검증 오차 ${Fmt.num(it.errorDb, 2)} dB (허용 ±${it.toleranceDb})", color = AvColors.TextDim, fontSize = 12.sp) }
                        Text("${Fmt.date(p.createdAtEpochMs)} · ${p.phoneModel} · 음원: ${p.noiseSourceDescription.ifBlank { "미기재" }}", color = AvColors.TextDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private enum class CalMethod(val label: String, val fixedDb: Double) {
    CAL94("음향교정기 94 dB", 94.0),
    CAL114("음향교정기 114 dB", 114.0),
}

/**
 * 음향교정기 보정 (외장 측정 마이크 전용): 오프셋 = 교정기 기준값 − 앱 LAeq.
 * 기준 소음계 비교는 V2 스마트 비교보정(검증구간 포함)으로 대체되어 이 화면에서 제거됨.
 */
@Composable
fun CalibratorScreen(repo: Repository, engine: AudioEngine, nav: Navigator) {
    val perm = rememberAvPermissions()
    Column(Modifier.fillMaxSize()) {
        TopBar("음향교정기 보정", onBack = { nav.pop() })
        if (!perm.audio) { AudioPermissionMissing(perm); return }
        CalibratorContent(repo, engine)
    }
}

@Composable
private fun CalibratorContent(repo: Repository, engine: AudioEngine) {
    val scope = rememberCoroutineScope()
    val ui by engine.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        engine.start()
        onDispose { engine.stop() }
    }
    val cfg = ui.config
    var current by remember { mutableStateOf<Calibration?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(cfg?.inputDeviceKey, reload) { current = cfg?.let { repo.loadCalibration(it.inputDeviceKey) } }

    var method by remember { mutableStateOf(CalMethod.CAL94) }
    var instrument by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val lv = ui.levels

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("입력 경로: ${cfg?.inputDeviceName ?: "—"} · 소스 ${cfg?.audioSource ?: "—"}", color = AvColors.Text)
        val valid = current.validFor(cfg?.inputDeviceKey ?: "", cfg?.audioSource ?: "")?.takeIf { it.state == CalibrationState.CALIBRATOR_VERIFIED }
        CalibrationTag(valid)
        valid?.let {
            Text(
                "현재 오프셋 ${Fmt.num(it.offsetDb!!, 2)} dB · ${it.referenceInstrument ?: ""} · ${it.calibratedAtEpochMs?.let(Fmt::date) ?: ""}",
                color = AvColors.TextDim, fontSize = 13.sp,
            )
        }
        Text(
            "음향교정기(1 kHz)를 정확히 장착할 수 있는 외장 측정 마이크에서만 사용하세요. 내장 마이크에는 정확히 장착되지 않습니다.",
            color = AvColors.Warn, fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalMethod.entries.forEach { m -> FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m.label, fontSize = 12.sp) }) }
        }
        Text("교정기를 켜고 측정 초기화 후 10초 이상 측정하세요.", color = AvColors.TextDim, fontSize = 13.sp)
        Text(
            "앱 LAeq: ${lv?.laeqDbFs?.let { Fmt.num(it, 2) + " dBFS(A)" } ?: "—"} · 경과 ${lv?.let { Fmt.num(it.durationSec, 0) } ?: "0"} s",
            color = AvColors.Text, fontSize = 18.sp,
        )
        if ((lv?.clippedSamples ?: 0) > 0) Text("⚠ 클리핑 발생 — 보정할 수 없습니다", color = AvColors.Red)
        OutlinedButton(onClick = { engine.resetMeasurement(); message = null }) { Text("측정 초기화") }
        OutlinedTextField(
            instrument, { instrument = it }, label = { Text("교정기 모델/일련번호 *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        Button(
            onClick = {
                val ref = method.fixedDb
                val laeq = lv?.laeqDbFs
                message = when {
                    cfg == null || lv == null || laeq == null -> "마이크 입력 대기 중"
                    lv.durationSec < 10 -> "10초 이상 측정 후 저장하세요"
                    lv.clippedSamples > 0 -> "클리핑이 있어 보정할 수 없습니다"
                    ui.routingChanges > 0 -> "측정 중 입력 경로가 변경되었습니다"
                    instrument.isBlank() -> "교정기 정보를 입력하세요"
                    else -> {
                        val cal = Calibration(
                            state = CalibrationState.CALIBRATOR_VERIFIED, offsetDb = ref - laeq,
                            inputDeviceKey = cfg.inputDeviceKey, inputDeviceName = cfg.inputDeviceName, audioSource = cfg.audioSource,
                            referenceInstrument = instrument.trim(), referenceValueDb = ref, measuredDbFs = laeq,
                            calibratedAtEpochMs = System.currentTimeMillis(), note = method.label,
                        )
                        scope.launch { repo.saveCalibration(cal); reload++ }
                        "보정 저장: 오프셋 ${Fmt.num(ref - laeq, 2)} dB"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("보정 저장") }
        if (current != null) {
            OutlinedButton(
                onClick = { cfg?.let { c -> scope.launch { repo.clearCalibration(c.inputDeviceKey); reload++ } } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("이 입력 경로의 교정기 보정 해제") }
        }
        message?.let { Text(it, color = AvColors.Warn) }
        Text(
            "단일 레벨·1 kHz 조건의 오프셋 보정입니다. 주파수 응답·선형성·저주파 감도는 보정되지 않습니다.",
            color = AvColors.TextDim, fontSize = 12.sp,
        )
    }
}
