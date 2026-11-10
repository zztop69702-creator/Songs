package kr.youngil.acousticvision.core.calibration

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** OCR 엔진이 돌려준 텍스트 조각 (줄 또는 단어). heightPx는 글자 높이(주 표시값 판별용). */
data class OcrToken(val text: String, val heightPx: Int, val confidence: Float? = null)

@Serializable
data class OcrCandidate(
    val valueDb: Double,
    val raw: String,
    val heightPx: Int,
    /** 소수점이 인식되지 않아 추정함 (예: "725" → 72.5) */
    val decimalInferred: Boolean,
    val confidence: Float? = null,
)

@Serializable
data class OcrParseResult(
    val rawText: String,
    val candidates: List<OcrCandidate>,
    val best: OcrCandidate?,
    val leqLabel: Boolean,
    val maxLabel: Boolean,
    val instantLabel: Boolean,
    /** true=A, false=A 외(C/Z), null=미검출 */
    val weightingA: Boolean?,
    val uncertain: Boolean,
    val warnings: List<String>,
)

/**
 * 소음계 결과 화면 OCR 판독문 해석기.
 * OCR은 입력 보조일 뿐이며 결과는 반드시 사용자 확인 후 채택한다 (V2 §5).
 * 7세그먼트 LCD 오인식 가능성이 있어 불확실 조건을 보수적으로 판정한다.
 */
object MeterOcrParser {
    private val number = Regex("""(?<![\d.:])(\d{2,4})(?:\.(\d))?(?![\d:])""")
    private val unitAfter = Regex("""^\s*(s|sec|min|h|%|hz|khz|m\b)""", RegexOption.IGNORE_CASE)

    /** 숫자 문맥의 흔한 OCR 혼동 보정 (숫자 위주 토큰에서만) */
    private fun normalizeDigits(t: String): String {
        val digitLike = t.count { it.isDigit() || it in "OoIl|SB.," }
        if (digitLike < t.length * 0.6) return t.replace(',', '.')
        return t.map {
            when (it) {
                'O', 'o' -> '0'
                'I', 'l', '|' -> '1'
                'S' -> '5'
                'B' -> '8'
                ',' -> '.'
                else -> it
            }
        }.joinToString("")
    }

    fun parse(tokens: List<OcrToken>, refMin: Double = 20.0, refMax: Double = 140.0): OcrParseResult {
        val raw = tokens.joinToString("\n") { it.text }
        val upper = raw.uppercase().replace(" ", "")
        val leq = listOf("LAEQ", "LEQ", "LAEQ,T", "EQ").any { upper.contains(it) }
        val max = upper.contains("MAX")
        val instant = listOf("LAF", "FAST", "SPL", "LAS").any { upper.contains(it) } && !leq
        val weighting: Boolean? = when {
            listOf("DBA", "DB(A)", "DB[A]", "LAEQ", "LAF", "LAS").any { upper.contains(it) } -> true
            listOf("DBC", "DB(C)", "DBZ", "DB(Z)", "LCEQ", "LZEQ").any { upper.contains(it) } -> false
            else -> null
        }

        val candidates = ArrayList<OcrCandidate>()
        for (tok in tokens) {
            val text = normalizeDigits(tok.text)
            for (m in number.findAll(text)) {
                val after = text.substring(m.range.last + 1)
                if (unitAfter.containsMatchIn(after)) continue // 시간·%·Hz 등 배제
                val intPart = m.groupValues[1]
                val dec = m.groupValues[2]
                val (value, inferred) = when {
                    dec.isNotEmpty() -> "$intPart.$dec".toDouble() to false
                    intPart.length == 3 && intPart.toDouble() / 10 in refMin..refMax && intPart.toDouble() !in refMin..refMax ->
                        intPart.toDouble() / 10 to true
                    else -> intPart.toDouble() to false
                }
                if (value !in refMin..refMax) continue
                candidates += OcrCandidate(value, m.value, tok.heightPx, inferred, tok.confidence)
            }
        }
        val best = candidates.maxByOrNull { it.heightPx }
        val warnings = ArrayList<String>()
        if (best == null) warnings += "유효 범위(${refMin.toInt()}~${refMax.toInt()} dB)의 숫자를 찾지 못함"
        if (best?.decimalInferred == true) warnings += "소수점 미인식 — ${best.raw}를 ${best.valueDb}로 추정"
        if (best != null && !best.raw.contains('.')) warnings += "소수점 없는 값 — 표시 자릿수 확인 필요"
        val rivals = candidates.filter { it !== best && best != null && abs(it.heightPx - best.heightPx) <= best.heightPx * 0.1 && it.valueDb != best.valueDb }
        if (rivals.isNotEmpty()) warnings += "비슷한 크기의 다른 숫자 ${rivals.joinToString { it.raw }} — 주 표시값 확인 필요"
        if (!leq) warnings += "LAeq/Leq 표시 미검출"
        if (max) warnings += "MAX 표시 검출 — 최대값을 LAeq로 오인하지 않도록 확인"
        if (instant) warnings += "순간값(Fast/SPL) 표시 검출"
        if (weighting == null) warnings += "가중특성(A) 표시 미검출"
        if (weighting == false) warnings += "A 이외 가중특성 표시 검출"
        val lowConf = best?.confidence?.let { it < 0.7f } ?: false
        if (lowConf) warnings += "OCR 신뢰도 낮음 (${best?.confidence})"
        val uncertain = best == null || best.decimalInferred || rivals.isNotEmpty() || max || weighting == false || lowConf
        return OcrParseResult(raw, candidates, best, leq, max, instant, weighting, uncertain, warnings)
    }
}
