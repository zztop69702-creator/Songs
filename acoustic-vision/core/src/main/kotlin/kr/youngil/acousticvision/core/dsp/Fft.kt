package kr.youngil.acousticvision.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 반복형 radix-2 복소 FFT (제자리 연산). n은 2의 거듭제곱이어야 한다. */
class Fft(val size: Int) {
    init {
        require(size >= 2 && (size and (size - 1)) == 0) { "FFT 크기는 2의 거듭제곱이어야 합니다: $size" }
    }

    private val cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { -sin(2.0 * PI * it / size) }
    private val bitReverse = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) table[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun transform(re: DoubleArray, im: DoubleArray) {
        require(re.size == size && im.size == size)
        for (i in 0 until size) {
            val j = bitReverse[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sinTable[k]
                    val l = j + half
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += len
            }
            len = len shl 1
        }
    }
}
