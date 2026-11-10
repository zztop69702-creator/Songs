package kr.youngil.acousticvision.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.youngil.acousticvision.BuildConfig
import kr.youngil.acousticvision.data.Backup
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 설치된 APK의 서명 인증서 SHA-256 (업데이트 시 같은 서명인지 확인용) */
fun signingCertSha256(context: Context): String? = runCatching {
    val pm = context.packageManager
    val certs: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
    } else {
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION")
        info.signatures?.map { it.toByteArray() } ?: emptyList()
    }
    // GitHub Actions 빌드 요약(apksigner)과 같은 형식: 소문자 16진수 연속
    certs.firstOrNull()?.let { c -> MessageDigest.getInstance("SHA-256").digest(c).joinToString("") { "%02x".format(it) } }
}.getOrNull()

/** 구글 드라이브 앱(또는 브라우저)으로 폴더 열기. 앱 자체는 인터넷 권한 없이 다른 앱에 위임만 한다. */
fun openUrl(context: Context, url: String, onError: (String) -> Unit) {
    try {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: android.content.ActivityNotFoundException) {
        onError("구글 드라이브 앱이나 브라우저를 찾을 수 없습니다")
    }
}

/** 앱 정보·데이터 백업 (V3: 버전·서명 확인, 업데이트 전 내보내기, 재설치 후 복원) */
@Composable
fun AppInfoScreen(nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = context.filesDir
    var fileCount by remember { mutableStateOf(0) }
    var freeMb by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    val cert = remember { signingCertSha256(context) }

    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) {
            fileCount = Backup.listFiles(root).size
            freeMb = StatFs(root.absolutePath).availableBytes / (1024 * 1024)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { out ->
                        Backup.export(root, out, BuildConfig.VERSION_NAME, System.currentTimeMillis())
                    }
                }.fold(
                    { "내보내기 완료: 파일 ${it.files}개, ${it.bytes / 1024} KB" },
                    { "내보내기 실패: ${it.message}" },
                )
            }
            busy = false
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { Backup.import(root, it) } }.fold(
                    { r -> r.error?.let { "복원 중단: $it (변경된 데이터 없음)" } ?: "복원 완료: ${r.restored}개 복원, 기존 ${r.skippedExisting}개 유지(덮어쓰지 않음)" },
                    { "복원 실패: ${it.message}" },
                )
            }
            busy = false
            refresh++
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("앱 정보·백업", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("버전")
            Line("버전", BuildConfig.VERSION_NAME)
            Line("versionCode", BuildConfig.VERSION_CODE.toString())
            Line("빌드 번호", if (BuildConfig.CI_BUILD_NUMBER > 0) "GitHub Actions #${BuildConfig.CI_BUILD_NUMBER}" else "로컬 빌드")
            Line("앱 ID", context.packageName)
            Line("빌드 종류", if (BuildConfig.DEBUG) "debug (설치 시험용 — 현장 사용 금지)" else "release")
            Line("기기", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Section("서명 인증서 SHA-256")
            SelectionContainer { Text(cert ?: "확인 불가", color = AvColors.Text, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            Text("업데이트 후에도 이 값이 같아야 같은 서명키로 설치된 것입니다.", color = AvColors.TextDim, fontSize = 12.sp)

            Section("업데이트 (구글 드라이브)")
            Text(
                "① 아래 '데이터 내보내기'로 백업 → ② '새 버전 받기'에서 가장 큰 번호(r) APK 설치 → ③ '업데이트' 누르기. 앱을 삭제하지 마세요(데이터가 지워짐).",
                color = AvColors.Text, fontSize = 13.sp,
            )
            Button(onClick = { openUrl(context, BuildConfig.DRIVE_INSTALL_FOLDER_URL) { message = it } }, modifier = Modifier.fillMaxWidth()) {
                Text("새 버전 받기 (구글 드라이브 열기)")
            }
            OutlinedButton(onClick = { openUrl(context, BuildConfig.DRIVE_BACKUP_FOLDER_URL) { message = it } }, modifier = Modifier.fillMaxWidth()) {
                Text("백업 폴더 열기 (구글 드라이브)")
            }

            Section("데이터")
            Line("저장 파일", "${fileCount}개 (현장·측정·보정·진단)")
            Line("남은 저장공간", "$freeMb MB")
            if (freeMb in 0..500) Text("⚠ 저장공간이 부족합니다. 영상·사진 저장 전에 공간을 확보하세요.", color = AvColors.Warn, fontSize = 13.sp)
            Text(
                "데이터는 앱 전용 저장소에 있으며 같은 서명의 업데이트에서는 유지되지만, 앱을 삭제하면 함께 삭제됩니다. 업데이트·재설치 전에 내보내기 하세요.",
                color = AvColors.TextDim, fontSize = 12.sp,
            )
            Button(enabled = !busy, onClick = {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.KOREA).format(Date())
                exportLauncher.launch("acoustic-vision-backup-$stamp.zip")
            }, modifier = Modifier.fillMaxWidth()) { Text("전체 데이터 내보내기 (ZIP)") }
            Text("저장 위치를 고르는 화면에서 ‘Drive’ → ‘02_데이터백업’ 폴더를 선택하면 구글 드라이브에 바로 저장됩니다.", color = AvColors.TextDim, fontSize = 12.sp)
            OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                Text("백업에서 복원 (기존 데이터 유지)")
            }
            message?.let { Text(it, color = if (it.contains("실패") || it.contains("중단")) AvColors.Red else AvColors.Ok) }
            Text("복원은 백업 파일의 무결성(SHA-256)을 검사하고, 이미 있는 파일은 덮어쓰지 않습니다.", color = AvColors.TextDim, fontSize = 12.sp)
        }
    }
}
