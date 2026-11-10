package kr.youngil.acousticvision.calibration

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kr.youngil.acousticvision.core.calibration.OcrToken
import java.io.File
import kotlin.coroutines.resume

/** 소음계 결과 화면 촬영용 카메라 (Preview + ImageCapture). */
class CaptureHolder {
    var imageCapture: ImageCapture? = null

    suspend fun takePicture(context: Context, file: File): Result<File> = suspendCancellableCoroutine { cont ->
        val ic = imageCapture
        if (ic == null) { cont.resume(Result.failure(IllegalStateException("카메라 준비 안 됨"))); return@suspendCancellableCoroutine }
        ic.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { cont.resume(Result.success(file)) }
                override fun onError(exception: ImageCaptureException) { cont.resume(Result.failure(exception)) }
            },
        )
    }
}

@Composable
fun MeterCaptureCamera(holder: CaptureHolder, modifier: Modifier = Modifier, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                holder.imageCapture = capture
            } catch (e: Exception) {
                onError("카메라를 시작할 수 없습니다: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit) {
        onDispose {
            holder.imageCapture = null
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

/** OCR 실패 원인을 사용자 안내 문구로 변환 (Play 서비스 OCR 모델 미설치 등) */
fun ocrErrorMessage(e: Throwable?): String {
    val m = e?.message ?: ""
    return if (m.contains("download", ignoreCase = true) || m.contains("UNAVAILABLE", ignoreCase = true) || m.contains("module", ignoreCase = true)) {
        "OCR 모델 준비 중 — 첫 사용 시 인터넷 연결 후 잠시 기다리세요(구글 Play 서비스가 내려받음). 지금은 직접 입력하세요"
    } else {
        "OCR 실패 — 직접 입력하세요 ($m)"
    }
}

/** ML Kit 문자 인식 → 줄 단위 토큰(글자 높이·신뢰도 포함). 실패 시 빈 목록. */
object MeterOcr {
    suspend fun recognize(context: Context, file: File): Result<List<OcrToken>> = suspendCancellableCoroutine { cont ->
        val image = try {
            InputImage.fromFilePath(context, Uri.fromFile(file))
        } catch (e: Exception) {
            cont.resume(Result.failure(e)); return@suspendCancellableCoroutine
        }
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        client.process(image)
            .addOnSuccessListener { text ->
                val tokens = text.textBlocks.flatMap { b ->
                    b.lines.map { l -> OcrToken(l.text, l.boundingBox?.height() ?: 0, l.confidence) }
                }
                client.close()
                cont.resume(Result.success(tokens))
            }
            .addOnFailureListener { e ->
                client.close()
                cont.resume(Result.failure(e))
            }
    }
}
