package kr.youngil.acousticvision.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kr.youngil.acousticvision.audio.AudioEngine
import kr.youngil.acousticvision.calibration.SmartCalibrationScreen
import kr.youngil.acousticvision.core.model.ImprovementStage
import kr.youngil.acousticvision.core.model.Measurement
import kr.youngil.acousticvision.core.model.Project
import kr.youngil.acousticvision.data.Repository
import java.util.UUID

sealed interface Route {
    data object Home : Route
    data class EditProject(val projectId: String?) : Route
    data class ProjectDetail(val projectId: String) : Route
    data class Measure(val projectId: String) : Route
    data class MeasurementView(val projectId: String, val measurementId: String) : Route
    data object Calibration : Route
    data object SmartCalibration : Route
    data object CalibratorCalibration : Route
    data object Diagnostics : Route
}

class Navigator {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack.last()
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replace(r: Route) { stack[stack.lastIndex] = r }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val repo = remember { Repository(context) }
    val engine = remember { AudioEngine(context) }
    val nav = remember { Navigator() }
    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
    Box(Modifier.fillMaxSize().background(AvColors.Navy).safeDrawingPadding()) {
        when (val r = nav.current) {
            Route.Home -> HomeScreen(repo, nav)
            is Route.EditProject -> ProjectEditScreen(repo, nav, r.projectId)
            is Route.ProjectDetail -> ProjectDetailScreen(repo, nav, r.projectId)
            is Route.Measure -> MeasureScreen(repo, engine, nav, r.projectId)
            is Route.MeasurementView -> MeasurementViewScreen(repo, nav, r.projectId, r.measurementId)
            Route.Calibration -> CalibrationScreen(repo, nav)
            Route.SmartCalibration -> SmartCalibrationScreen(repo, engine, nav)
            Route.CalibratorCalibration -> CalibratorScreen(repo, engine, nav)
            Route.Diagnostics -> DiagnosticsScreen(repo, nav)
        }
    }
}

// ---------------- 공통 ----------------

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) TextButton(onClick = onBack) { Text("‹ 뒤로", color = AvColors.Text) }
        Text(
            title, color = AvColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = if (onBack == null) 8.dp else 0.dp),
            maxLines = 1,
        )
        trailing()
    }
}

fun hasPermission(context: Context, p: String) =
    ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** 권한 상태와 요청 함수 */
class PermissionState(val audio: Boolean, val camera: Boolean, val asked: Boolean, val request: () -> Unit)

@Composable
fun rememberAvPermissions(): PermissionState {
    val context = LocalContext.current
    var audio by remember { mutableStateOf(hasPermission(context, android.Manifest.permission.RECORD_AUDIO)) }
    var camera by remember { mutableStateOf(hasPermission(context, android.Manifest.permission.CAMERA)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        audio = res[android.Manifest.permission.RECORD_AUDIO] ?: audio
        camera = res[android.Manifest.permission.CAMERA] ?: camera
        asked = true
    }
    val request = {
        launcher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA))
    }
    LaunchedEffect(Unit) { if (!audio || !camera) request() }
    return PermissionState(audio, camera, asked, request)
}

@Composable
fun AudioPermissionMissing(perm: PermissionState) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("마이크 권한이 필요합니다", color = AvColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("소음도와 주파수 분석은 마이크 입력 없이는 수행할 수 없습니다. 측정값을 임의로 생성하지 않습니다.", color = AvColors.TextDim)
        Button(onClick = perm.request) { Text("권한 다시 요청") }
        if (perm.asked) OutlinedButton(onClick = { openAppSettings(context) }) { Text("앱 설정에서 권한 허용") }
    }
}

// ---------------- 홈 ----------------

@Composable
fun HomeScreen(repo: Repository, nav: Navigator) {
    var projects by remember { mutableStateOf<List<Project>?>(null) }
    LaunchedEffect(Unit) { projects = repo.listProjects() }
    Column(Modifier.fillMaxSize()) {
        TopBar("YOUNGIL Acoustic Vision", onBack = null)
        Text(
            DISCLAIMER_SHORT, color = AvColors.Warn, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { nav.push(Route.EditProject(null)) }, modifier = Modifier.weight(1f)) { Text("+ 새 현장") }
            OutlinedButton(onClick = { nav.push(Route.Calibration) }, modifier = Modifier.weight(1f)) { Text("보정") }
            OutlinedButton(onClick = { nav.push(Route.Diagnostics) }, modifier = Modifier.weight(1f)) { Text("기기 진단") }
        }
        val list = projects
        when {
            list == null -> Text("불러오는 중…", color = AvColors.TextDim, modifier = Modifier.padding(16.dp))
            list.isEmpty() -> Text(
                "등록된 현장이 없습니다. '+ 새 현장'으로 학교·설비 정보를 등록하세요.",
                color = AvColors.TextDim, modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { p ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AvColors.NavyLight),
                        modifier = Modifier.fillMaxWidth().clickable { nav.push(Route.ProjectDetail(p.id)) },
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(p.siteName, color = AvColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${p.equipmentName} · ${Fmt.stage(p.stage)}", color = AvColors.TextDim)
                            Text(Fmt.date(p.createdAtEpochMs), color = AvColors.TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 현장 등록/수정 ----------------

@Composable
fun ProjectEditScreen(repo: Repository, nav: Navigator, projectId: String?) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(projectId == null) }
    var existing by remember { mutableStateOf<Project?>(null) }
    var site by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("") }
    var rpm by remember { mutableStateOf("") }
    var blades by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(ImprovementStage.NOT_APPLICABLE) }
    var approval by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) {
        if (projectId != null) {
            repo.loadProject(projectId)?.let { p ->
                existing = p
                site = p.siteName; equipment = p.equipmentName; condition = p.operatingCondition
                rpm = p.rpm?.let { Fmt.num(it, 0) } ?: ""; blades = p.bladeCount?.toString() ?: ""
                position = p.defaultPosition; stage = p.stage; approval = p.filmingApprovalConfirmed; notes = p.notes
            }
            loaded = true
        }
    }
    if (!loaded) return

    Column(Modifier.fillMaxSize()) {
        TopBar(if (projectId == null) "새 현장 등록" else "현장 정보 수정", onBack = { nav.pop() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(site, { site = it }, label = { Text("학교/현장명 *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(equipment, { equipment = it }, label = { Text("설비명 * (예: 급식실 배기팬 EF-1)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(condition, { condition = it }, label = { Text("운전조건 (예: 정격, 인버터 45 Hz)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    rpm, { rpm = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("회전수 RPM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true,
                )
                OutlinedTextField(
                    blades, { blades = it.filter(Char::isDigit) }, label = { Text("날개 수 Z") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true,
                )
            }
            Text("RPM은 명판값과 실제 운전값이 다를 수 있습니다 (슬립·인버터·벨트비).", color = AvColors.TextDim, fontSize = 12.sp)
            OutlinedTextField(position, { position = it }, label = { Text("기본 측정 위치 (예: 팬 흡입구 1 m)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("개선 단계", color = AvColors.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImprovementStage.entries.forEach { s ->
                    FilterChip(selected = stage == s, onClick = { stage = s }, label = { Text(Fmt.stage(s)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { approval = !approval }) {
                Checkbox(checked = approval, onCheckedChange = { approval = it })
                Text("학교(기관)의 촬영 승인을 받았고, 종사자·학생 얼굴 촬영을 피합니다.", color = AvColors.Text, fontSize = 14.sp)
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("메모") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = AvColors.Red) }
            Button(
                onClick = {
                    try {
                        val p = Project(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            siteName = site.trim(), equipmentName = equipment.trim(), operatingCondition = condition.trim(),
                            rpm = rpm.toDoubleOrNull(), bladeCount = blades.toIntOrNull(), defaultPosition = position.trim(),
                            stage = stage, filmingApprovalConfirmed = approval, notes = notes.trim(),
                            createdAtEpochMs = existing?.createdAtEpochMs ?: System.currentTimeMillis(),
                        )
                        scope.launch {
                            repo.saveProject(p)
                            if (projectId == null) nav.replace(Route.ProjectDetail(p.id)) else nav.pop()
                        }
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
        }
    }
}

// ---------------- 현장 상세 ----------------

@Composable
fun ProjectDetailScreen(repo: Repository, nav: Navigator, projectId: String) {
    var project by remember { mutableStateOf<Project?>(null) }
    var measurements by remember { mutableStateOf<List<Measurement>>(emptyList()) }
    LaunchedEffect(projectId, nav.stack.size) {
        project = repo.loadProject(projectId)
        measurements = repo.listMeasurements(projectId)
    }
    val p = project ?: return
    Column(Modifier.fillMaxSize()) {
        TopBar(p.siteName, onBack = { nav.pop() }) {
            TextButton(onClick = { nav.push(Route.EditProject(p.id)) }) { Text("수정") }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${p.equipmentName} · ${Fmt.stage(p.stage)}", color = AvColors.Text, fontWeight = FontWeight.Bold)
            if (p.operatingCondition.isNotBlank()) Text("운전조건: ${p.operatingCondition}", color = AvColors.TextDim)
            val rpmText = p.rpm?.let { "RPM ${Fmt.num(it, 0)}" } ?: "RPM 미입력"
            val zText = p.bladeCount?.let { "날개 $it" } ?: "날개 수 미입력"
            Text("$rpmText · $zText", color = AvColors.TextDim)
            if (!p.filmingApprovalConfirmed) Text("⚠ 촬영 승인 미확인 — 영상 촬영 전 학교 승인 필요", color = AvColors.Warn, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { nav.push(Route.Measure(p.id)) }, modifier = Modifier.fillMaxWidth()) { Text("측정 시작") }
            Spacer(Modifier.height(8.dp))
            Text("측정 기록 (${measurements.size})", color = AvColors.Text, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(measurements, key = { it.id }) { m ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AvColors.NavyLight),
                    modifier = Modifier.fillMaxWidth().clickable { nav.push(Route.MeasurementView(p.id, m.id)) },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.positionLabel.ifBlank { "(위치 미입력)" }, color = AvColors.Text, fontWeight = FontWeight.SemiBold)
                            Text("${Fmt.date(m.createdAtEpochMs)} · ${Fmt.stage(m.stage)} · 마커 ${m.markers.size}", color = AvColors.TextDim, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "LAeq " + Fmt.levelA(m.level.laeqDbFs, m.level.calibrationOffsetDb),
                            color = AvColors.Text, fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}
