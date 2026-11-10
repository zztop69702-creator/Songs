package kr.youngil.acousticvision.calibration

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.youngil.acousticvision.audio.AudioEngine
import kr.youngil.acousticvision.core.calibration.ApprovalEvent
import kr.youngil.acousticvision.core.calibration.BlockReason
import kr.youngil.acousticvision.core.calibration.CalibrationMode
import kr.youngil.acousticvision.core.calibration.CalibrationProfile
import kr.youngil.acousticvision.core.calibration.CalibrationThresholds
import kr.youngil.acousticvision.core.calibration.ComparisonRecord
import kr.youngil.acousticvision.core.calibration.MeterOcrParser
import kr.youngil.acousticvision.core.calibration.OcrParseResult
import kr.youngil.acousticvision.core.calibration.OffsetCalculator
import kr.youngil.acousticvision.core.calibration.ProfileStatus
import kr.youngil.acousticvision.core.calibration.ReadingType
import kr.youngil.acousticvision.core.calibration.ReferenceInstrument
import kr.youngil.acousticvision.core.calibration.ReferenceReading
import kr.youngil.acousticvision.core.calibration.SafetyGate
import kr.youngil.acousticvision.core.calibration.ValueSource
import kr.youngil.acousticvision.core.calibration.WindowAnalysis
import kr.youngil.acousticvision.core.calibration.WindowAnalyzer
import kr.youngil.acousticvision.core.calibration.WindowDecision
import kr.youngil.acousticvision.core.model.AudioConfig
import kr.youngil.acousticvision.data.Repository
import kr.youngil.acousticvision.ui.AudioPermissionMissing
import kr.youngil.acousticvision.ui.AvColors
import kr.youngil.acousticvision.ui.Fmt
import kr.youngil.acousticvision.ui.Line
import kr.youngil.acousticvision.ui.Navigator
import kr.youngil.acousticvision.ui.Section
import kr.youngil.acousticvision.ui.TopBar
import kr.youngil.acousticvision.ui.rememberAvPermissions
import java.io.File
import java.util.UUID

private enum class Phase(val label: String) { CALIBRATION("① 보정 측정"), VALIDATION("② 독립 검증 측정") }
private enum class Step { SETUP, MEASURE, CAPTURE, CONFIRM, RESULT }

/** 측정 1회분 결과 */
private class MeasureOutcome(
    val analysis: WindowAnalysis,
    val decision: WindowDecision,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val routingChanged: Boolean,
    val config: AudioConfig,
)

private fun vibrate(context: Context, ms: Long) {
    val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    runCatching { v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
}

/**
 * 스마트 비교보정 V2 마법사.
 * 설정 → ①보정 측정 → 결과화면 촬영/OCR → 사용자 확인 → ②독립 검증 측정 → 촬영/확인 → 합격 시 사용자 승인으로 활성화.
 * 시작 신호는 진동+화면(무음): 비프음은 폰 마이크에만 크게 들어가 보정을 오염시킴.
 */
@Composable
fun SmartCalibrationScreen(repo: Repository, engine: AudioEngine, nav: Navigator) {
    val perm = rememberAvPermissions()
    Column(Modifier.fillMaxSize()) {
        TopBar("스마트 비교보정", onBack = { nav.pop() })
        if (!perm.audio) { AudioPermissionMissing(perm); return }
        DisposableEffect(Unit) {
            engine.start()
            onDispose { engine.stop() }
        }
        Wizard(repo, engine, nav, cameraAllowed = perm.camera)
    }
}

@Composable
private fun Wizard(repo: Repository, engine: AudioEngine, nav: Navigator, cameraAllowed: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by engine.state.collectAsStateWithLifecycle()
    val thresholds = remember { CalibrationThresholds() }

    var step by remember { mutableStateOf(Step.SETUP) }
    var phase by remember { mutableStateOf(Phase.CALIBRATION) }
    var message by remember { mutableStateOf<String?>(null) }

    // 설정
    var model by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var calDate by remember { mutableStateOf("") }
    var meterMode by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(CalibrationMode.LAEQ_COMPARISON) }
    var noise by remember { mutableStateOf("") }
    var placed by remember { mutableStateOf(false) }
    var intervalSec by remember { mutableIntStateOf(15) }

    // 측정
    var countdown by remember { mutableStateOf<Int?>(null) }
    var elapsed by remember { mutableStateOf<Double?>(null) }
    var measuring by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<MeasureOutcome?>(null) }

    // 촬영·확인
    val capture = remember { CaptureHolder() }
    var photo by remember { mutableStateOf<File?>(null) }
    var photoTime by remember { mutableStateOf<Long?>(null) }
    var ocr by remember { mutableStateOf<OcrParseResult?>(null) }
    var ocrBusy by remember { mutableStateOf(false) }
    var valueText by remember { mutableStateOf("") }
    var userTyped by remember { mutableStateOf(false) }
    var readingType by remember { mutableStateOf(ReadingType.UNKNOWN) }
    var weighting by remember { mutableStateOf("") }
    var meterInterval by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var reasons by remember { mutableStateOf<List<BlockReason>>(emptyList()) }

    // 결과
    var profile by remember { mutableStateOf<CalibrationProfile?>(null) }
    val photos = remember { mutableListOf<String>() }

    fun resetCaptureAndConfirm() {
        photo = null; photoTime = null; ocr = null; valueText = ""; userTyped = false
        readingType = ReadingType.UNKNOWN; weighting = ""; meterInterval = intervalSec.toString(); confirmed = false; reasons = emptyList()
    }

    fun instrument() = ReferenceInstrument(model.trim(), serial.trim(), calDate.trim(), "A", meterMode.trim())

    fun startMeasurement() {
        if (measuring) return
        measuring = true; outcome = null; message = null
        scope.launch {
            // 앞쪽 여유(pre-roll) 1.5 s 이상 확보
            var waited = 0
            while ((engine.blockCount() < 15 || engine.currentConfig() == null) && waited < 50) { delay(100); waited++ }
            if (engine.currentConfig() == null) { message = "마이크 입력이 없습니다"; measuring = false; return@launch }
            for (n in 3 downTo 1) { countdown = n; vibrate(context, 80); delay(1000) }
            countdown = 0
            vibrate(context, 400)
            val startBlock = engine.blockCount()
            val startEpoch = System.currentTimeMillis()
            val clip0 = engine.snapshot()?.levels?.clippedSamples ?: 0
            val routing0 = engine.routingChangeCount()
            val windowBlocks = intervalSec * 10
            countdown = null
            var last = startBlock
            var stall = 0
            while (engine.blockCount() < startBlock + windowBlocks) {
                delay(100)
                val now = engine.blockCount()
                elapsed = (now - startBlock) / 10.0
                stall = if (now == last) stall + 1 else 0
                last = now
                if (stall > 20) { message = "측정이 중단되었습니다 (마이크 입력 정지)"; measuring = false; elapsed = null; return@launch }
            }
            val endEpoch = System.currentTimeMillis()
            vibrate(context, 400) // 종료 신호 (수동 정지형 소음계)
            elapsed = null
            while (engine.blockCount() < startBlock + windowBlocks + 10) delay(100) // 뒤쪽 여유 1 s
            val clip1 = engine.snapshot()?.levels?.clippedSamples ?: 0
            val cfg = engine.currentConfig()!!
            val a = WindowAnalyzer.analyze(engine.blocksCopy(), startBlock, windowBlocks, clipped = clip1 > clip0)
            outcome = MeasureOutcome(a, WindowAnalyzer.decide(a, mode, thresholds), startEpoch, endEpoch, engine.routingChangeCount() > routing0, cfg)
            measuring = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "기준 소음계 LAeq와 앱의 동시간 LAeq를 비교해 이 입력 경로 전용 오프셋을 산출합니다. 공인 교정·주파수응답 교정을 대체하지 않습니다.",
            color = AvColors.TextDim, fontSize = 12.sp,
        )
        if (step != Step.SETUP) Text(phase.label, color = AvColors.Blue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        ui.config?.let { Text("입력: ${it.inputDeviceName} · ${it.audioSource} · ${it.sampleRate} Hz", color = AvColors.TextDim, fontSize = 12.sp) }
        if (ui.routingChanges > 0) Text("⚠ 입력 경로 변경 감지 — 이 세션의 보정은 확정할 수 없습니다", color = AvColors.Red)

        when (step) {
            Step.SETUP -> {
                Section("1. 기준 소음계 확인")
                OutlinedTextField(model, { model = it }, label = { Text("모델 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(serial, { serial = it }, label = { Text("일련번호 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(calDate, { calDate = it }, label = { Text("최근 교정(정도검사)일 * 예: 2026-03-15") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(meterMode, { meterMode = it }, label = { Text("소음계 설정 (예: A, Fast, Leq 15 s)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("비교 방식", color = AvColors.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(mode == CalibrationMode.LAEQ_COMPARISON, { mode = CalibrationMode.LAEQ_COMPARISON }, { Text("LAeq 구간 비교", fontSize = 12.sp) })
                    FilterChip(mode == CalibrationMode.SIMPLE_INSTANT, { mode = CalibrationMode.SIMPLE_INSTANT }, { Text("간편(LAeq 미지원)", fontSize = 12.sp) })
                }
                if (mode == CalibrationMode.SIMPLE_INSTANT) Text(
                    "간편 비교모드: 소음계 순간값(A, Fast)과 비교. 변동이 매우 작은(1초값 표준편차 ≤ ${thresholds.simpleModeMaxStdDb} dB) 소음에서만 허용되며 정확도가 제한됩니다.",
                    color = AvColors.Warn, fontSize = 12.sp,
                )
                OutlinedTextField(noise, { noise = it }, label = { Text("보정 음원 (예: 급식실 배기팬 정격운전)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Section("2. 배치")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { placed = !placed }) {
                    Checkbox(placed, { placed = it })
                    Text("두 마이크를 같은 음장·같은 방향에 나란히 고정했고 서로 가리지 않습니다", color = AvColors.Text, fontSize = 13.sp)
                }
                Text("권장: 측정 중에는 휴대폰을 고정해 두고, 측정 종료 후 휴대폰을 들어 소음계의 결과 화면을 촬영하세요.", color = AvColors.TextDim, fontSize = 12.sp)
                val ready = model.isNotBlank() && serial.isNotBlank() && calDate.isNotBlank() && placed
                Button(onClick = { resetCaptureAndConfirm(); step = Step.MEASURE }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("다음: 측정") }
            }

            Step.MEASURE -> {
                Section("3. 동시 측정 (${intervalSec} s)")
                Text(
                    "‘측정 시작’을 누르면 3초 카운트다운(진동) 후 긴 진동이 울립니다. 긴 진동에 맞춰 소음계의 Leq 측정을 시작하세요. " +
                        "${intervalSec}초 후 다시 긴 진동이 울리면 (수동 정지형이면) 소음계를 정지하세요.",
                    color = AvColors.TextDim, fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(intervalSec == 15, { if (!measuring) intervalSec = 15 }, { Text("15 s (기본)") })
                    FilterChip(intervalSec == 30, { if (!measuring) intervalSec = 30 }, { Text("30 s (연장)") })
                }
                Box(Modifier.fillMaxWidth().height(110.dp).background(if (countdown == 0) Color.White else AvColors.NavyLight), contentAlignment = Alignment.Center) {
                    when {
                        countdown != null && countdown!! > 0 -> Text("$countdown", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                        countdown == 0 -> Text("시작!", color = Color.Black, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        elapsed != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("측정 중 ${Fmt.num(elapsed!!, 1)} / $intervalSec s", color = Color.White, fontSize = 22.sp)
                            LinearProgressIndicator(progress = { (elapsed!! / intervalSec).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.padding(8.dp))
                        }
                        measuring -> Text("준비 중…", color = Color.White)
                        else -> Text(Fmt.levelA(ui.levels?.lafDbFs, null) + " (실시간)", color = Color.White, fontSize = 20.sp)
                    }
                }
                Button(onClick = { startMeasurement() }, enabled = !measuring, modifier = Modifier.fillMaxWidth()) { Text(if (outcome == null) "측정 시작" else "다시 측정") }
                outcome?.let { o ->
                    Section("측정 결과 (앱, 원본 dBFS)")
                    Line("앱 LAeq", "${Fmt.num(o.analysis.laeqDbFs, 2)} dBFS(A)")
                    Line("1초값 표준편차", "${Fmt.num(o.analysis.oneSecondStdDb, 2)} dB")
                    Line("시간민감도(±1 s)", "${Fmt.num(o.analysis.timingSensitivityDb, 2)} dB")
                    if (o.analysis.clipped) Text("⚠ 클리핑 발생", color = AvColors.Red)
                    if (o.routingChanged) Text("⚠ 측정 중 입력 경로 변경", color = AvColors.Red)
                    when (o.decision) {
                        WindowDecision.OK -> Button(onClick = { meterInterval = intervalSec.toString(); step = if (cameraAllowed) Step.CAPTURE else Step.CONFIRM }, modifier = Modifier.fillMaxWidth()) {
                            Text("다음: 소음계 결과 입력")
                        }
                        WindowDecision.EXTEND_TO_30S -> {
                            Text("소음 변동/시간 불일치 영향이 큽니다. 소음계와 앱 모두 30 s로 다시 측정하세요. (이미 끝난 15 s 적분은 연장할 수 없음)", color = AvColors.Warn)
                            OutlinedButton(onClick = { intervalSec = 30; outcome = null }) { Text("30 s로 전환") }
                        }
                        WindowDecision.REMEASURE -> Text("30 s에서도 불안정합니다. 소음원이 안정된 상태에서 재측정하세요.", color = AvColors.Red)
                    }
                }
                message?.let { Text(it, color = if (it.startsWith("보정 오프셋")) AvColors.Ok else AvColors.Red) }
            }

            Step.CAPTURE -> {
                Section("4. 소음계 결과 화면 촬영")
                Text("소음계에 표시된 ‘해당 구간 LAeq 결과’를 정면에서 촬영하세요. 촬영 시각은 적분구간과 별도로 기록됩니다. OCR 결과는 반드시 직접 확인합니다.", color = AvColors.TextDim, fontSize = 13.sp)
                var camError by remember { mutableStateOf<String?>(null) }
                Box(Modifier.fillMaxWidth().height(300.dp).background(Color.Black)) {
                    MeterCaptureCamera(capture, Modifier.fillMaxSize(), onError = { camError = it })
                    camError?.let { Text(it, color = AvColors.Red, modifier = Modifier.align(Alignment.Center)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !ocrBusy, onClick = {
                        ocrBusy = true
                        scope.launch {
                            val f = repo.newEvidenceFile("meter-${System.currentTimeMillis()}.jpg")
                            val res = capture.takePicture(context, f)
                            if (res.isFailure) { message = "촬영 실패: ${res.exceptionOrNull()?.message}"; ocrBusy = false; return@launch }
                            photo = f; photoTime = System.currentTimeMillis(); photos += f.name
                            val tokens = MeterOcr.recognize(context, f)
                            ocr = tokens.getOrNull()?.let { MeterOcrParser.parse(it, thresholds.referenceMinDb, thresholds.referenceMaxDb) }
                            if (tokens.isFailure) message = "OCR 실패 — 직접 입력하세요 (${tokens.exceptionOrNull()?.message})"
                            ocr?.best?.let { valueText = Fmt.num(it.valueDb, 1) }
                            ocr?.let { r ->
                                if (r.weightingA == true) weighting = "A"
                                readingType = when {
                                    r.maxLabel -> ReadingType.MAX
                                    r.leqLabel -> ReadingType.LAEQ_INTERVAL
                                    r.instantLabel -> ReadingType.INSTANTANEOUS
                                    else -> ReadingType.UNKNOWN
                                }
                            }
                            ocrBusy = false
                            step = Step.CONFIRM
                        }
                    }) { Text(if (ocrBusy) "인식 중…" else "촬영 + OCR") }
                    OutlinedButton(onClick = { ocr = null; step = Step.CONFIRM }) { Text("촬영 없이 직접 입력") }
                }
                message?.let { Text(it, color = AvColors.Red) }
            }

            Step.CONFIRM -> {
                Section("5. 판독값 확인 (사용자 승인 필수)")
                photo?.let { f ->
                    val bmp = remember(f) { BitmapFactory.decodeFile(f.absolutePath) }
                    bmp?.let { Image(it.asImageBitmap(), "소음계 결과 화면", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) }
                }
                ocr?.let { r ->
                    Text("OCR 원문: ${r.rawText.replace("\n", " | ").take(160)}", color = AvColors.TextDim, fontSize = 11.sp)
                    if (r.candidates.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.candidates.take(4).forEach { c -> FilterChip(valueText == Fmt.num(c.valueDb, 1), { valueText = Fmt.num(c.valueDb, 1); userTyped = true }, { Text(c.raw) }) }
                    }
                    r.warnings.forEach { Text("· $it", color = AvColors.Warn, fontSize = 12.sp) }
                    if (r.uncertain) Text("판독이 불확실합니다. 값을 직접 입력(수정)해야 진행할 수 있습니다.", color = AvColors.Red, fontSize = 12.sp)
                }
                OutlinedTextField(
                    valueText, { valueText = it.filter { c -> c.isDigit() || c == '.' }; userTyped = true },
                    label = { Text("기준 소음계 값 dB") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Text("표시값 종류", color = AvColors.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(ReadingType.LAEQ_INTERVAL to "구간 LAeq", ReadingType.INSTANTANEOUS to "순간값", ReadingType.MAX to "최대값", ReadingType.UNKNOWN to "모름").forEach { (t, l) ->
                        FilterChip(readingType == t, { readingType = t }, { Text(l, fontSize = 12.sp) })
                    }
                }
                Text("가중특성", color = AvColors.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("A", "C", "Z").forEach { w -> FilterChip(weighting == w, { weighting = w }, { Text(w) }) }
                }
                if (mode == CalibrationMode.LAEQ_COMPARISON) OutlinedTextField(
                    meterInterval, { meterInterval = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("소음계 적분시간 s (앱 ${intervalSec} s)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { confirmed = !confirmed }) {
                    Checkbox(confirmed, { confirmed = it })
                    Text("값·단위(dB)·가중특성·표시 종류·측정구간을 소음계 화면과 대조해 확인했습니다", color = AvColors.Text, fontSize = 13.sp)
                }
                if (reasons.isNotEmpty()) {
                    Text("확정 불가 사유:", color = AvColors.Red, fontWeight = FontWeight.Bold)
                    reasons.forEach { Text("· ${it.message}", color = AvColors.Red, fontSize = 13.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { resetCaptureAndConfirm(); outcome = null; step = Step.MEASURE }) { Text("재측정") }
                    Button(onClick = {
                        val o = outcome ?: return@Button
                        val value = valueText.toDoubleOrNull()
                        if (value == null) { reasons = listOf(BlockReason.VALUE_UNCONFIRMED); return@Button }
                        val r = ocr
                        val source = when {
                            r == null -> ValueSource.MANUAL
                            !userTyped && r.best != null && Fmt.num(r.best!!.valueDb, 1) == valueText -> ValueSource.OCR
                            else -> ValueSource.OCR_EDITED
                        }
                        val reading = ReferenceReading(
                            valueDb = value, source = source, readingType = readingType, weightingConfirmed = weighting,
                            meterIntervalSec = if (mode == CalibrationMode.LAEQ_COMPARISON) meterInterval.toDoubleOrNull() ?: -1.0 else o.analysis.intervalSec,
                            userConfirmed = confirmed, ocrUncertain = r?.uncertain ?: false, ocrConfidence = r?.best?.confidence?.toDouble(),
                            ocrRawText = r?.rawText, photoFile = photo?.name, photoCapturedAtEpochMs = photoTime,
                        )
                        val gate = SafetyGate.check(mode, reading, instrument(), o.analysis, o.decision, o.config.preprocessing, o.routingChanged || ui.routingChanges > 0, thresholds)
                        val record = ComparisonRecord(o.analysis, o.decision, reading, o.startEpochMs, o.endEpochMs, gate)
                        val now = System.currentTimeMillis()
                        // 두 단계 모두: 차단 사유가 있으면 진행하지 않고 수정/재측정 요청 (자동 확정 금지)
                        reasons = gate
                        if (gate.isNotEmpty()) return@Button
                        if (phase == Phase.CALIBRATION) {
                            val offset = OffsetCalculator.offset(value, o.analysis.laeqDbFs)
                            val p = CalibrationProfile(
                                profileId = "cp-" + UUID.randomUUID().toString().take(8), createdAtEpochMs = now,
                                phoneManufacturer = Build.MANUFACTURER, phoneModel = Build.MODEL, osRelease = Build.VERSION.RELEASE,
                                sdkInt = Build.VERSION.SDK_INT, buildFingerprint = Build.FINGERPRINT,
                                micType = o.config.inputDeviceKey.substringBefore(':'), inputDeviceKey = o.config.inputDeviceKey,
                                inputDeviceName = o.config.inputDeviceName, audioSource = o.config.audioSource, sampleRate = o.config.sampleRate,
                                preprocessing = o.config.preprocessing, instrument = instrument(), mode = mode, noiseSourceDescription = noise.trim(),
                                comparison = record, offsetDb = offset, status = ProfileStatus.PENDING_VALIDATION, thresholds = thresholds,
                                approvals = listOf(ApprovalEvent(now, "READING_CONFIRMED", "보정 측정 판독값 사용자 확인 (${source})")),
                                dataFiles = photos.toList(),
                            )
                            profile = p
                            scope.launch { repo.saveProfile(p) }
                            phase = Phase.VALIDATION
                            resetCaptureAndConfirm(); outcome = null
                            message = "보정 오프셋 ${Fmt.num(offset, 2)} dB 산출. 소음계를 초기화하고 독립 검증 측정을 진행하세요."
                            step = Step.MEASURE
                        } else {
                            val p = profile ?: return@Button
                            val v = OffsetCalculator.validate(p.offsetDb, record, thresholds)
                            val updated = p.copy(
                                validation = v,
                                status = if (v.passed) ProfileStatus.PENDING_VALIDATION else ProfileStatus.REJECTED,
                                approvals = p.approvals + ApprovalEvent(now, if (v.passed) "VALIDATION_PASSED" else "VALIDATION_FAILED", "검증 오차 ${Fmt.num(v.errorDb, 2)} dB"),
                                dataFiles = photos.toList(),
                            )
                            profile = updated
                            scope.launch { repo.saveProfile(updated) }
                            step = Step.RESULT
                        }
                    }) { Text(if (phase == Phase.CALIBRATION) "확인 · 오프셋 산출" else "확인 · 검증") }
                }
            }

            Step.RESULT -> {
                val p = profile ?: return@Column
                val v = p.validation
                Section("6. 결과")
                Line("오프셋 C", "${Fmt.num(p.offsetDb, 2)} dB")
                Line("보정 측정", "기준 ${Fmt.num(p.comparison.reading.valueDb, 1)} dB / 앱 ${Fmt.num(p.comparison.analysis.laeqDbFs, 2)} dBFS(A)")
                v?.let {
                    Line("검증 측정", "기준 ${Fmt.num(it.comparison.reading.valueDb, 1)} dB / 앱+C ${Fmt.num(it.predictedDb, 1)} dB")
                    Line("검증 오차", "${Fmt.num(it.errorDb, 2)} dB (허용 ±${it.toleranceDb} dB, 잠정)")
                    if (it.comparison.blockReasons.isNotEmpty()) it.comparison.blockReasons.forEach { r -> Text("· ${r.message}", color = AvColors.Red, fontSize = 13.sp) }
                }
                if (v?.passed == true && p.status != ProfileStatus.ACTIVE) {
                    Text("검증 합격. 승인하면 이 입력 경로의 측정에 오프셋이 적용됩니다(값은 계속 '참고값'으로 표기).", color = AvColors.Ok)
                    Button(onClick = {
                        val now = System.currentTimeMillis()
                        val active = p.copy(status = ProfileStatus.ACTIVE, approvals = p.approvals + ApprovalEvent(now, "ACTIVATED", "사용자 승인"))
                        profile = active
                        scope.launch { repo.activateProfile(active, now); message = "프로파일 활성화됨: ${active.profileId}" }
                    }, modifier = Modifier.fillMaxWidth()) { Text("프로파일 활성화 승인") }
                } else if (p.status == ProfileStatus.ACTIVE) {
                    Text("활성화됨 (${p.profileId})", color = AvColors.Ok, fontWeight = FontWeight.Bold)
                    Button(onClick = { nav.pop() }, modifier = Modifier.fillMaxWidth()) { Text("완료") }
                } else {
                    Text("검증 불합격 — 프로파일은 보류(REJECTED)로 저장되었고 적용되지 않습니다.", color = AvColors.Red)
                    OutlinedButton(onClick = {
                        profile = null; phase = Phase.CALIBRATION; resetCaptureAndConfirm(); outcome = null; photos.clear(); step = Step.MEASURE
                    }, modifier = Modifier.fillMaxWidth()) { Text("처음부터 재시험") }
                }
                message?.let { Text(it, color = AvColors.Ok) }
            }
        }
    }
}
