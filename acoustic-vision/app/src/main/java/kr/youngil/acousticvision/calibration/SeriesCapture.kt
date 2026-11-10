package kr.youngil.acousticvision.calibration

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as CSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.youngil.acousticvision.core.calibration.MeterOcrParser
import kr.youngil.acousticvision.core.calibration.MeterSample
import kr.youngil.acousticvision.core.calibration.OcrToken
import kr.youngil.acousticvision.ui.AvColors
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs

/** 가이드 상자(정규화 좌표): 소음계 표시부를 이 안에 맞춘다. 분석은 이 영역만 잘라 OCR한다. */
private const val GUIDE_L = 0.1f
private const val GUIDE_T = 0.3f
private const val GUIDE_W = 0.8f
private const val GUIDE_H = 0.4f

/**
 * 소음계 화면 연속 판독기. 카메라 프레임을 약 0.4 s 간격으로 OCR하고,
 * 측정 구간(window) 동안의 판독값을 (시각, 값) 시계열과 프레임 사진으로 보관한다.
 * 시각 기준: SystemClock.elapsedRealtimeNanos (카메라 센서 타임스탬프가 같은 기준이면 그 값을 사용).
 */
class SeriesCaptureController(private val evidenceDir: File, private val filePrefix: String) {
    private val _live = MutableStateFlow("판독 대기")
    val live: StateFlow<String> = _live

    @Volatile private var windowStartNs: Long? = null
    @Volatile private var windowSec: Double = 0.0
    private val samples = ArrayList<MeterSample>()
    @Volatile private var lastProcessedNs = 0L
    @Volatile var sensorTimestampUsed: Boolean? = null
        private set

    fun startWindow(startNs: Long, seconds: Double) {
        synchronized(samples) { samples.clear() }
        windowSec = seconds
        windowStartNs = startNs
    }

    fun stopWindow(): List<MeterSample> {
        windowStartNs = null
        return synchronized(samples) { ArrayList(samples) }
    }

    private val recognizerLazy = lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val recognizer by recognizerLazy

    fun close() { if (recognizerLazy.isInitialized()) recognizer.close() }

    fun analyze(image: ImageProxy) {
        try {
            val nowNs = SystemClock.elapsedRealtimeNanos()
            if (nowNs - lastProcessedNs < 400_000_000L) return
            lastProcessedNs = nowNs
            val ts = image.imageInfo.timestamp
            val useSensor = abs(nowNs - ts) < 2_000_000_000L
            sensorTimestampUsed = useSensor
            val frameNs = if (useSensor) ts else nowNs

            val full = image.toBitmap()
            val rotated = if (image.imageInfo.rotationDegrees != 0) {
                val m = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                Bitmap.createBitmap(full, 0, 0, full.width, full.height, m, true)
            } else full
            val crop = Bitmap.createBitmap(
                rotated,
                (rotated.width * GUIDE_L).toInt(), (rotated.height * GUIDE_T).toInt(),
                (rotated.width * GUIDE_W).toInt(), (rotated.height * GUIDE_H).toInt(),
            )
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0)))
            val tokens = text.textBlocks.flatMap { b -> b.lines.map { l -> OcrToken(l.text, l.boundingBox?.height() ?: 0, l.confidence) } }
            val parsed = MeterOcrParser.parse(tokens)
            val best = parsed.best
            _live.value = when {
                best == null -> "판독 실패 — 표시부를 가이드 상자에 맞추세요"
                parsed.uncertain -> "불확실: ${best.raw} (${parsed.warnings.firstOrNull() ?: ""})"
                else -> "판독: ${best.raw} dB"
            }
            val start = windowStartNs ?: return
            val t = (frameNs - start) / 1e9
            if (t < 0 || t > windowSec) return
            val idx = synchronized(samples) { samples.size }
            val file = File(evidenceDir, "$filePrefix-${System.currentTimeMillis()}-$idx.jpg")
            runCatching {
                evidenceDir.mkdirs()
                file.outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            }
            val sample = MeterSample(
                tSec = t, valueDb = best?.valueDb, raw = parsed.rawText.replace("\n", " ").take(60),
                uncertain = parsed.uncertain || best == null, confidence = best?.confidence,
                frameFile = if (file.exists()) file.name else null,
            )
            synchronized(samples) { samples.add(sample) }
        } catch (e: Exception) {
            _live.value = ocrErrorMessage(e)
        } finally {
            image.close()
        }
    }
}

/** 소음계 표시부 연속 촬영 카메라 (Preview + ImageAnalysis, 4:3, 가이드 상자 표시) */
@Composable
fun MeterSeriesCamera(controller: SeriesCaptureController, modifier: Modifier = Modifier, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val selector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
                val preview = Preview.Builder().setResolutionSelector(selector).build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor) { controller.analyze(it) }
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                onError("카메라를 시작할 수 없습니다: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
        }
    }
    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                AvColors.Yellow,
                topLeft = Offset(size.width * GUIDE_L, size.height * GUIDE_T),
                size = CSize(size.width * GUIDE_W, size.height * GUIDE_H),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

