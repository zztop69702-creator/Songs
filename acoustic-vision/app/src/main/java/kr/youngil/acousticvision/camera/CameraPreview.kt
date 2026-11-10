package kr.youngil.acousticvision.camera

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/** PreviewView 참조를 보관해 저장 시 화면 스냅샷(마커 좌표와 동일 기준)을 얻는다. */
class PreviewHolder {
    var view: PreviewView? = null
    /** 미리보기 화면 그대로의 비트맵 (마커 정규화 좌표와 동일한 영역) */
    fun snapshot(): Bitmap? = view?.bitmap
}

@Composable
fun CameraPreview(holder: PreviewHolder, modifier: Modifier = Modifier, onError: (String) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView 기반: getBitmap() 안정성 및 Compose 오버레이 겹침 호환
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                provider = p
            } catch (e: Exception) {
                onError("카메라를 시작할 수 없습니다: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit) {
        holder.view = previewView
        onDispose {
            holder.view = null
            provider?.unbindAll()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}
