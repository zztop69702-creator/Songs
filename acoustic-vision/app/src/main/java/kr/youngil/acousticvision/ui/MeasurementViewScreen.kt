package kr.youngil.acousticvision.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.youngil.acousticvision.core.model.AppJson
import kr.youngil.acousticvision.core.model.Measurement
import kr.youngil.acousticvision.data.Repository

@Composable
fun MeasurementViewScreen(repo: Repository, nav: Navigator, projectId: String, measurementId: String) {
    val context = LocalContext.current
    var m by remember { mutableStateOf<Measurement?>(null) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(measurementId) {
        val loaded = repo.loadMeasurement(projectId, measurementId)
        m = loaded
        loadFailed = loaded == null
        bmp = loaded?.snapshotFile?.let { repo.loadSnapshot(projectId, it) }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("측정 기록", onBack = { nav.pop() }) {
            m?.let { mm ->
                TextButton(onClick = {
                    val json = AppJson.encodeToString(Measurement.serializer(), mm)
                    val send = Intent(Intent.ACTION_SEND).setType("application/json")
                        .putExtra(Intent.EXTRA_SUBJECT, "측정 ${mm.id}").putExtra(Intent.EXTRA_TEXT, json)
                    context.startActivity(Intent.createChooser(send, "측정 JSON 공유"))
                }) { Text("JSON 공유") }
            }
        }
        if (loadFailed) { Text("측정 파일을 읽을 수 없습니다", color = AvColors.Red, modifier = Modifier.padding(16.dp)); return }
        val mm = m ?: return
        val off = mm.level.calibrationOffsetDb
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val b = bmp
            if (b != null) {
                // 스냅샷과 같은 비율의 상자 안에서 정규화 좌표로 마커 재표시
                Box(Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height)) {
                    Image(b.asImageBitmap(), contentDescription = "측정 화면 스냅샷", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    MarkerLayer(mm.markers, selectedId = null)
                }
            } else {
                Text("저장된 화면 스냅샷 없음 (카메라 미사용)", color = AvColors.TextDim)
                if (mm.markers.isNotEmpty()) Text("마커 ${mm.markers.size}개 (좌표만 저장됨)", color = AvColors.TextDim)
            }
            CalibrationTag(mm.calibration)
            Text(mm.positionLabel.ifBlank { "(위치 미입력)" }, color = AvColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("${Fmt.date(mm.createdAtEpochMs)} · ${Fmt.stage(mm.stage)} · 측정 ${Fmt.num(mm.level.durationSec, 0)} s", color = AvColors.TextDim)
            Section("소음도 (${if (off != null) "참고값" else "dBFS 상대값"})")
            Line("LAeq", Fmt.levelA(mm.level.laeqDbFs, off))
            Line("LAFmax", Fmt.levelA(mm.level.lafMaxDbFs, off))
            Line("LAFmin", Fmt.levelA(mm.level.lafMinDbFs, off))
            Line("LZeq", Fmt.levelZ(mm.level.lzeqDbFs, off))
            Line("샘플 피크", "${Fmt.num(mm.level.samplePeakDbFs)} dBFS")
            if (mm.level.clippedSamples > 0) Text("⚠ 클리핑 ${mm.level.clippedSamples} 샘플 — 레벨 신뢰도 낮음", color = AvColors.Red, fontSize = 13.sp)

            Section("주요 피크 (평균 스펙트럼, dBFS)")
            if (mm.peaks.isEmpty()) Text("검출된 피크 없음", color = AvColors.TextDim)
            mm.peaks.forEach { Line(Fmt.hz(it.frequencyHz), "${Fmt.num(it.levelDbFs)} dBFS") }

            if (mm.toneMatches.isNotEmpty()) {
                Section("송풍기 주파수 후보 (RPM ${mm.rpm?.let { Fmt.num(it, 0) }} · 날개 ${mm.bladeCount ?: "-"})")
                mm.toneMatches.forEach { t ->
                    Line("${t.label} ${Fmt.hz(t.expectedHz)}", t.matchedHz?.let { "피크 ${Fmt.hz(it)}" } ?: "일치 피크 없음")
                }
                Text("명판 RPM 기준 계산값이며 실제 운전 RPM과 다를 수 있음", color = AvColors.TextDim, fontSize = 11.sp)
            }

            Section("1/3 옥타브 A가중 (FFT 합산 근사, 회색=bin 부족)")
            ThirdOctaveChart(mm.thirdOctave, off, Modifier.fillMaxWidth().height(140.dp).background(AvColors.NavyLight))

            Section("거리")
            val d = mm.distance
            if (d == null) Text("거리 없음 (입력하지 않음)", color = AvColors.TextDim)
            else Line("${d.meters?.let { Fmt.num(it, 2) + " m" } ?: "측정 실패"} (${d.source})", d.note)

            Section("마커")
            if (mm.markers.isEmpty()) Text("마커 없음", color = AvColors.TextDim)
            mm.markers.forEach { Line("${it.label} · ${it.source}", "x ${Fmt.num(it.x.toDouble(), 2)}, y ${Fmt.num(it.y.toDouble(), 2)}") }

            Section("측정 조건")
            val a = mm.audio
            Line("기기", "${mm.device.manufacturer} ${mm.device.model} (Android ${mm.device.androidRelease})")
            Line("입력", "${a.inputDeviceName} · ${a.audioSource} · ${a.encoding}")
            Line("FFT", "fs ${a.sampleRate} Hz · N ${a.fftSize} · ${a.window} · Δf ${Fmt.num(a.binHz, 2)} Hz")
            val pp = a.preprocessing
            Line("전처리", "AGC ${state(pp.agcAvailable, pp.agcDisabled)} · NS ${state(pp.noiseSuppressorAvailable, pp.noiseSuppressorDisabled)} · AEC ${state(pp.echoCancelerAvailable, pp.echoCancelerDisabled)}")
            Line("전처리 실측 확인", if (pp.verifiedByStepTest) "확인됨" else "미확인 (레벨 스텝 시험 필요)")
            mm.calibration.offsetDb?.let { Line("교정 오프셋", "${Fmt.num(it, 2)} dB · ${mm.calibration.referenceInstrument ?: ""}") }
            if (mm.notes.isNotBlank()) Line("메모", mm.notes)
            HorizontalDivider()
            Text(mm.disclaimer, color = AvColors.Warn, fontSize = 12.sp)
        }
    }
}

private fun state(available: Boolean, disabled: Boolean?) = when {
    !available -> "없음"
    disabled == true -> "해제"
    else -> "해제 실패"
}

@Composable
fun Section(title: String) {
    Text(title, color = AvColors.Blue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
}

@Composable
fun Line(label: String, value: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(label, color = AvColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(0.42f))
        Text(value, color = AvColors.Text, fontSize = 13.sp, modifier = Modifier.weight(0.58f))
    }
}
