package kr.youngil.acousticvision.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kr.youngil.acousticvision.core.model.AppJson
import kr.youngil.acousticvision.data.Repository
import kr.youngil.acousticvision.diag.Diagnostics

@Composable
fun DiagnosticsScreen(repo: Repository, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val perm = rememberAvPermissions()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var savedPath by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar("기기 진단", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "이 기기의 오디오 소스(UNPROCESSED) 지원, AGC/잡음억제 해제 결과, 입력 장치의 채널 수·샘플레이트, 오디오 타임스탬프, 카메라 타임스탬프 기준을 기록합니다. " +
                    "결과 JSON을 TEST_LOG에 첨부하세요. API 보고값이므로 실제 동작은 별도 시험으로 확인해야 합니다.",
                color = AvColors.TextDim, fontSize = 13.sp,
            )
            if (!perm.audio) Text("마이크 권한이 없으면 녹음 시험은 건너뜁니다.", color = AvColors.Warn, fontSize = 13.sp)
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    scope.launch {
                        val json: JsonObject = Diagnostics.collect(context, perm.audio)
                        val text = AppJson.encodeToString(JsonObject.serializer(), json)
                        result = text
                        savedPath = repo.saveDiagnostics("diag-${System.currentTimeMillis()}.json", text).absolutePath
                        running = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (running) "진단 중… (약 1초 녹음)" else "진단 실행") }
            result?.let { text ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("application/json")
                            .putExtra(Intent.EXTRA_SUBJECT, "Acoustic Vision 기기 진단").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, "진단 결과 공유"))
                    }) { Text("결과 공유") }
                }
                savedPath?.let { Text("저장: $it", color = AvColors.TextDim, fontSize = 11.sp) }
                SelectionContainer {
                    Text(text, color = AvColors.Text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}
