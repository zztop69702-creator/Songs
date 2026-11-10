package kr.youngil.acousticvision.diag

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kr.youngil.acousticvision.audio.AudioEngine

/**
 * Phase 0 기기 진단 (TEST_PLAN P0-02, P0-04 일부, P0-05 사전정보, P0-08 사전정보).
 * API가 보고하는 값만 기록한다. 실제 신호 처리 여부·동기 오차는 별도 시험으로 확인해야 한다.
 */
object Diagnostics {

    suspend fun collect(context: Context, hasAudioPermission: Boolean): JsonObject = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        buildJsonObject {
            put("schema", "youngil-acoustic-vision-diagnostics/1")
            put("collectedAtEpochMs", System.currentTimeMillis())
            put("note", "API 보고값 기반. 전처리 실제 해제, 동기 오차, 다채널 독립성은 TEST_PLAN 시험으로 별도 확인 필요")
            putJsonObject("device") {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("device", Build.DEVICE)
                put("androidRelease", Build.VERSION.RELEASE)
                put("sdkInt", Build.VERSION.SDK_INT)
            }
            putJsonObject("audio") {
                put("unprocessedSupported", am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) ?: "null")
                put("nativeSampleRate", am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "null")
                put("agcAvailable", AutomaticGainControl.isAvailable())
                put("noiseSuppressorAvailable", NoiseSuppressor.isAvailable())
                put("echoCancelerAvailable", AcousticEchoCanceler.isAvailable())
                putJsonArray("inputDevices") {
                    for (d in am.getDevices(AudioManager.GET_DEVICES_INPUTS)) add(deviceJson(d))
                }
            }
            put("recordTest", if (hasAudioPermission) recordTest(am) else buildJsonObject { put("skipped", "마이크 권한 없음") })
            put("cameras", cameras(context))
            putJsonObject("notYetChecked") {
                put("arcoreDepth", "Phase 2에서 ARCore 의존성 도입 후 확인")
                put("multichannelIndexMaskRecord", "외장 다채널 장치 연결 후 Phase 3 시험에서 확인")
            }
        }
    }

    private fun deviceJson(d: AudioDeviceInfo) = buildJsonObject {
        put("id", d.id)
        put("type", d.type)
        put("typeKey", AudioEngine.deviceKeyOf(d))
        put("productName", d.productName.toString())
        put("channelCounts", buildJsonArray { d.channelCounts.forEach { add(it) } })
        put("channelIndexMasks", buildJsonArray { d.channelIndexMasks.forEach { add(it) } })
        put("sampleRates", buildJsonArray { d.sampleRates.forEach { add(it) } })
        put("encodings", buildJsonArray { d.encodings.forEach { add(it) } })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) put("address", d.address)
    }

    @SuppressLint("MissingPermission")
    private fun recordTest(am: AudioManager): JsonObject {
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val rate = 48_000
        val enc = AudioFormat.ENCODING_PCM_FLOAT
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, enc)
        return buildJsonObject {
            put("source", if (unprocessed) "UNPROCESSED" else "VOICE_RECOGNITION")
            put("sampleRate", rate)
            put("encoding", "PCM_FLOAT")
            put("minBufferBytes", minBuf)
            if (minBuf <= 0) { put("result", "getMinBufferSize 실패"); return@buildJsonObject }
            val rec = try {
                AudioRecord.Builder().setAudioSource(source)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(enc).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(minBuf * 4).build()
            } catch (e: Exception) {
                put("result", "생성 실패: ${e.message}"); return@buildJsonObject
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { put("result", "초기화 실패"); rec.release(); return@buildJsonObject }
            val agc = runCatching { AutomaticGainControl.create(rec.audioSessionId)?.also { it.enabled = false } }.getOrNull()
            val ns = runCatching { NoiseSuppressor.create(rec.audioSessionId)?.also { it.enabled = false } }.getOrNull()
            put("agcEnabledAfterDisable", agc?.enabled?.toString() ?: "n/a")
            put("nsEnabledAfterDisable", ns?.enabled?.toString() ?: "n/a")
            try {
                rec.startRecording()
                val buf = FloatArray(rate / 2)
                var read = 0
                val t0 = SystemClock.elapsedRealtimeNanos()
                while (read < buf.size) {
                    val n = rec.read(buf, read, buf.size - read, AudioRecord.READ_BLOCKING)
                    if (n <= 0) break
                    read += n
                }
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
                put("framesRead", read)
                put("readElapsedMs", elapsedMs)
                var peak = 0f
                for (i in 0 until read) peak = maxOf(peak, kotlin.math.abs(buf[i]))
                put("samplePeak", peak.toDouble())
                val ts = AudioTimestamp()
                val mono = rec.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC)
                put("timestampMonotonicResult", mono)
                if (mono == AudioRecord.SUCCESS) {
                    put("timestampFramePosition", ts.framePosition)
                    put("timestampNanoTime", ts.nanoTime)
                    put("systemNanoTimeNow", System.nanoTime())
                }
                val boot = rec.getTimestamp(AudioTimestamp(), AudioTimestamp.TIMEBASE_BOOTTIME)
                put("timestampBoottimeResult", boot)
                rec.routedDevice?.let { put("routedDevice", deviceJson(it)) }
                put("result", "ok")
            } catch (e: Exception) {
                put("result", "녹음 실패: ${e.message}")
            } finally {
                runCatching { rec.stop() }
                rec.release(); agc?.release(); ns?.release()
            }
        }
    }

    private fun cameras(context: Context) = buildJsonArray {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        for (id in ids) {
            val ch = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: continue
            add(
                buildJsonObject {
                    put("id", id)
                    put(
                        "facing",
                        when (ch.get(CameraCharacteristics.LENS_FACING)) {
                            CameraCharacteristics.LENS_FACING_BACK -> "back"
                            CameraCharacteristics.LENS_FACING_FRONT -> "front"
                            else -> "external/other"
                        },
                    )
                    put(
                        "timestampSource",
                        when (ch.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)) {
                            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME -> "REALTIME"
                            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN -> "UNKNOWN"
                            else -> "null"
                        },
                    )
                    put("hardwareLevel", ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1)
                    put(
                        "depthOutputCapability",
                        ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) ?: false,
                    )
                },
            )
        }
    }
}
