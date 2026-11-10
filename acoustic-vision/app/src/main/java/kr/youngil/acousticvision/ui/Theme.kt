package kr.youngil.acousticvision.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.youngil.acousticvision.core.model.Calibration
import kr.youngil.acousticvision.core.model.CalibrationState
import kr.youngil.acousticvision.core.model.ImprovementStage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AvColors {
    val Navy = Color(0xFF101E31)
    val NavyLight = Color(0xFF1B2D47)
    val Blue = Color(0xFF3478F6)
    val Red = Color(0xFFE53935)       // 음원 마커
    val Yellow = Color(0xFFFFC107)    // 전문가 수정 마커
    val Text = Color(0xFFEAF0F8)
    val TextDim = Color(0xFF9FB0C8)
    val Warn = Color(0xFFFFB74D)
    val Ok = Color(0xFF66BB6A)
}

@Composable
fun AvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AvColors.Blue,
            onPrimary = Color.White,
            background = AvColors.Navy,
            onBackground = AvColors.Text,
            surface = AvColors.NavyLight,
            onSurface = AvColors.Text,
            surfaceVariant = AvColors.NavyLight,
            onSurfaceVariant = AvColors.TextDim,
            error = AvColors.Red,
        ),
        content = content,
    )
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.Black,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 교정 상태를 문자로 병기 (색상만으로 구분하지 않음) */
@Composable
fun CalibrationTag(cal: Calibration?) {
    when (cal?.state ?: CalibrationState.UNCALIBRATED) {
        CalibrationState.UNCALIBRATED -> Tag("미보정 · 상대값(dBFS)", AvColors.Warn)
        CalibrationState.REFERENCE_COMPARED -> Tag("참고값 · 기준소음계 비교보정", AvColors.Ok)
        CalibrationState.CALIBRATOR_VERIFIED -> Tag("참고값 · 음향교정기 보정", AvColors.Ok)
    }
}

object Fmt {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
    fun date(ms: Long): String = dateFmt.format(Date(ms))

    /** 보정 오프셋이 있으면 참고 dB(A), 없으면 dBFS(A) 상대값 */
    fun levelA(dbfs: Double?, offset: Double?): String = when {
        dbfs == null -> "—"
        offset != null -> String.format(Locale.US, "%.1f dB(A)", dbfs + offset)
        else -> String.format(Locale.US, "%.1f dBFS(A)", dbfs)
    }

    fun levelZ(dbfs: Double?, offset: Double?): String = when {
        dbfs == null -> "—"
        offset != null -> String.format(Locale.US, "%.1f dB(Z)", dbfs + offset)
        else -> String.format(Locale.US, "%.1f dBFS(Z)", dbfs)
    }

    fun hz(f: Double): String = if (f >= 1000) String.format(Locale.US, "%.2f kHz", f / 1000) else String.format(Locale.US, "%.1f Hz", f)
    fun num(v: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f", v)

    fun stage(s: ImprovementStage): String = when (s) {
        ImprovementStage.BEFORE -> "개선 전"
        ImprovementStage.AFTER -> "개선 후"
        ImprovementStage.NOT_APPLICABLE -> "구분 없음"
    }
}

const val DISCLAIMER_SHORT = "스마트폰 기반 참고값 · 공인 소음계 측정을 대체하지 않음"
