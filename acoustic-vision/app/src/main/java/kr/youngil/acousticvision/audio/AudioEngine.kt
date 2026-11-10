package kr.youngil.acousticvision.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioDeviceCallback
import android.media.AudioRecordingConfiguration
import android.media.AudioRouting
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.youngil.acousticvision.core.dsp.LevelSnapshot
import kr.youngil.acousticvision.core.dsp.PeakDetector
import kr.youngil.acousticvision.core.dsp.SoundLevelMeter
import kr.youngil.acousticvision.core.dsp.SpectralPeak
import kr.youngil.acousticvision.core.dsp.Spectrum
import kr.youngil.acousticvision.core.dsp.SpectrumAnalyzer
import kr.youngil.acousticvision.core.dsp.SpectrumAverager
import kr.youngil.acousticvision.core.model.AudioConfig
import kr.youngil.acousticvision.core.model.PreprocessingStatus
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 화면 표시용 상태 (약 10 Hz 갱신). */
data class AudioUiState(
    val running: Boolean = false,
    val error: String? = null,
    val config: AudioConfig? = null,
    val levels: LevelSnapshot? = null,
    val displayFreqs: FloatArray = FloatArray(0),
    val displayDb: FloatArray = FloatArray(0),
    val peaks: List<SpectralPeak> = emptyList(),
    /** 다른 앱/통화 등으로 시스템이 이 앱의 마이크 입력을 무음 처리 중 */
    val silenced: Boolean = false,
    /** 최근 1초 내 클리핑 발생 */
    val clippingRecent: Boolean = false,
    val timestampOk: Boolean? = null,
    /** 녹음 시작 후 입력 경로 변경 횟수 (외장 마이크 탈착 등) — 0이 아니면 보정 무효 */
    val routingChanges: Int = 0,
)

/** 저장 시점의 분석 결과. */
class AnalysisSnapshot(
    val config: AudioConfig,
    val levels: LevelSnapshot,
    val averagedSpectrum: Spectrum?,
    val averagedFrames: Int,
)

/**
 * 마이크 입력 → A 가중 소음도 + FFT.
 * - 오디오 소스: UNPROCESSED 지원 시 UNPROCESSED, 아니면 VOICE_RECOGNITION
 * - AGC/NS/AEC 효과가 있으면 해제 시도 후 결과를 기록 (실제 해제 여부는 레벨 스텝 시험으로 확인 필요)
 */
class AudioEngine(context: Context, val fftSize: Int = 8192) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(AudioUiState())
    val state: StateFlow<AudioUiState> = _state.asStateFlow()

    @Volatile private var thread: Thread? = null
    @Volatile private var stopRequested = false
    @Volatile private var resetRequested = false
    @Volatile private var silenced = false

    private val lock = Any()
    private var meter: SoundLevelMeter? = null
    private val averager = SpectrumAverager()
    private var config: AudioConfig? = null

    /** 100 ms A 가중 평균제곱 블록 (스마트 비교보정용). 녹음 시작 시 초기화, resetMeasurement와 무관 */
    private val blocks = ArrayList<Double>()
    @Volatile var recordingStartEpochMs: Long = 0L
        private set
    @Volatile private var routingChanges = 0

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            val session = currentSessionId
            if (session == 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            silenced = configs.any { it.clientAudioSessionId == session && it.isClientSilenced }
        }
    }
    @Volatile private var currentSessionId = 0

    val isUnprocessedSupported: Boolean
        get() = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    fun start() {
        if (thread != null) return
        stopRequested = false
        val t = Thread({ runLoop() }, "AudioEngine")
        t.priority = Thread.MAX_PRIORITY
        thread = t
        t.start()
    }

    fun stop() {
        stopRequested = true
        thread?.join(1500)
        thread = null
    }

    /** LAeq/Lmax/Lmin 및 평균 스펙트럼 초기화 */
    fun resetMeasurement() { resetRequested = true }

    /** 현재까지 기록된 블록 수 (= 다음 블록 인덱스) */
    fun blockCount(): Int = synchronized(lock) { blocks.size }

    fun blocksCopy(): List<Double> = synchronized(lock) { ArrayList(blocks) }

    fun routingChangeCount(): Int = routingChanges

    fun currentConfig(): AudioConfig? = synchronized(lock) { config }

    fun snapshot(): AnalysisSnapshot? = synchronized(lock) {
        val m = meter ?: return null
        val c = config ?: return null
        AnalysisSnapshot(c, m.snapshot(), averager.result(), averager.frames)
    }

    @SuppressLint("MissingPermission") // 권한은 호출 화면에서 확인
    private fun runLoop() {
        val source = if (isUnprocessedSupported) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val sourceName = if (source == MediaRecorder.AudioSource.UNPROCESSED) "UNPROCESSED" else "VOICE_RECOGNITION"
        val opened = openRecord(source)
        if (opened == null) {
            _state.value = AudioUiState(error = "마이크를 열 수 없습니다 (다른 앱 사용 중이거나 기기 미지원)")
            thread = null
            return
        }
        val (record, rate, floatEncoding) = opened
        currentSessionId = record.audioSessionId
        val effects = disableEffects(record.audioSessionId)
        val mainHandler = Handler(Looper.getMainLooper())
        audioManager.registerAudioRecordingCallback(recordingCallback, mainHandler)

        val analyzer = SpectrumAnalyzer(rate, fftSize)
        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            _state.value = AudioUiState(error = "녹음 시작 실패: ${e.message}")
            cleanup(record, effects)
            return
        }
        val routed: AudioDeviceInfo? = record.routedDevice
        val deviceKey = routed?.let { deviceKeyOf(it) } ?: "builtin"
        val deviceName = routed?.let { describe(it) } ?: "내장 마이크"
        var cfg = AudioConfig(
            audioSource = sourceName,
            sampleRate = rate,
            encoding = if (floatEncoding) "PCM_FLOAT" else "PCM_16BIT",
            channelCount = 1,
            fftSize = fftSize,
            window = analyzer.windowType.label,
            binHz = analyzer.binHz,
            enbwHz = analyzer.enbwHz,
            inputDeviceKey = deviceKey,
            inputDeviceName = deviceName,
            preprocessing = effects.status(isUnprocessedSupported),
        )
        routingChanges = 0
        recordingStartEpochMs = System.currentTimeMillis()
        synchronized(lock) {
            blocks.clear()
            meter = SoundLevelMeter(rate).also { m -> m.blockListener = { ms -> blocks.add(ms) } }
            averager.reset()
            config = cfg
        }
        // 입력 경로 변경 감지: 라우팅 변경 + 장치 분리
        val routingListener = AudioRouting.OnRoutingChangedListener { router ->
            val d = router.routedDevice
            val key = d?.let { deviceKeyOf(it) } ?: "unknown"
            synchronized(lock) {
                val c = config
                if (c != null && c.inputDeviceKey != key) {
                    routingChanges++
                    config = c.copy(inputDeviceKey = key, inputDeviceName = d?.let { describe(it) } ?: "알 수 없음")
                }
            }
        }
        record.addOnRoutingChangedListener(routingListener, mainHandler)
        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                if (routed != null && removed.any { it.id == routed.id }) routingChanges++
            }
        }
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)

        val chunk = 1024
        val floatBuf = FloatArray(chunk)
        val shortBuf = ShortArray(chunk)
        val ring = FloatArray(fftSize)
        var ringPos = 0
        var filled = 0
        val frame = FloatArray(fftSize)
        val hop = fftSize / 2 // 50 % 중첩
        var sinceFft = 0
        var lastUi = 0L
        var lastClipCount = 0L
        var lastClipTime = 0L
        val ts = AudioTimestamp()
        var tsOk: Boolean? = null
        var lastSpectrum: Spectrum? = null
        var peaks: List<SpectralPeak> = emptyList()

        while (!stopRequested) {
            val n = if (floatEncoding) {
                record.read(floatBuf, 0, chunk, AudioRecord.READ_BLOCKING)
            } else {
                val r = record.read(shortBuf, 0, chunk)
                for (i in 0 until max(r, 0)) floatBuf[i] = shortBuf[i] / 32768f
                r
            }
            if (n < 0) {
                _state.value = _state.value.copy(running = false, error = "오디오 읽기 오류 (코드 $n)")
                break
            }
            if (resetRequested) {
                synchronized(lock) { meter?.reset(); averager.reset() }
                resetRequested = false
            }
            synchronized(lock) { meter?.process(floatBuf, n) }
            for (i in 0 until n) {
                ring[ringPos] = floatBuf[i]
                ringPos = (ringPos + 1) % fftSize
            }
            filled = min(fftSize, filled + n)
            sinceFft += n
            if (filled == fftSize && sinceFft >= hop) {
                sinceFft = 0
                for (i in 0 until fftSize) frame[i] = ring[(ringPos + i) % fftSize]
                val s = analyzer.analyze(frame)
                synchronized(lock) { averager.add(s) }
                lastSpectrum = s
                peaks = PeakDetector.find(s, maxPeaks = 6)
            }
            val now = System.nanoTime()
            if (now - lastUi > 100_000_000L) {
                lastUi = now
                synchronized(lock) { config?.let { cfg = it } }
                if (tsOk == null || tsOk == false) {
                    tsOk = record.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS
                }
                val lv = synchronized(lock) { meter?.snapshot() }
                if (lv != null && lv.clippedSamples > lastClipCount) {
                    lastClipCount = lv.clippedSamples
                    lastClipTime = now
                }
                val (fx, dy) = lastSpectrum?.let { downsampleLog(it) } ?: (FloatArray(0) to FloatArray(0))
                _state.value = AudioUiState(
                    running = true,
                    config = cfg,
                    levels = lv,
                    displayFreqs = fx,
                    displayDb = dy,
                    peaks = peaks,
                    silenced = silenced,
                    clippingRecent = now - lastClipTime < 1_000_000_000L && lastClipCount > 0,
                    timestampOk = tsOk,
                    routingChanges = routingChanges,
                )
            }
        }
        record.removeOnRoutingChangedListener(routingListener)
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        cleanup(record, effects)
        _state.value = _state.value.copy(running = false)
    }

    private fun cleanup(record: AudioRecord, effects: EffectSet) {
        try { record.stop() } catch (_: IllegalStateException) {}
        record.release()
        effects.release()
        audioManager.unregisterAudioRecordingCallback(recordingCallback)
        currentSessionId = 0
        silenced = false
    }

    @SuppressLint("MissingPermission")
    private fun openRecord(source: Int): Triple<AudioRecord, Int, Boolean>? {
        for (rate in intArrayOf(48_000, 44_100)) {
            for (floatEnc in booleanArrayOf(true, false)) {
                val enc = if (floatEnc) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT
                val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, enc)
                if (minBuf <= 0) continue
                val rec = try {
                    AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(
                            AudioFormat.Builder().setSampleRate(rate).setEncoding(enc)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build(),
                        )
                        .setBufferSizeInBytes(max(minBuf * 4, 32_768))
                        .build()
                } catch (e: Exception) {
                    null
                }
                if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) return Triple(rec, rate, floatEnc)
                rec?.release()
            }
        }
        return null
    }

    private class EffectSet(
        val agc: AudioEffect?, val ns: AudioEffect?, val aec: AudioEffect?,
        val agcAvail: Boolean, val nsAvail: Boolean, val aecAvail: Boolean,
    ) {
        fun status(unprocessed: Boolean) = PreprocessingStatus(
            unprocessedSourceSupported = unprocessed,
            agcAvailable = agcAvail, agcDisabled = agc?.let { !it.enabled },
            noiseSuppressorAvailable = nsAvail, noiseSuppressorDisabled = ns?.let { !it.enabled },
            echoCancelerAvailable = aecAvail, echoCancelerDisabled = aec?.let { !it.enabled },
            verifiedByStepTest = false,
        )
        fun release() { agc?.release(); ns?.release(); aec?.release() }
    }

    private fun disableEffects(session: Int): EffectSet {
        fun <T : AudioEffect> tryDisable(available: Boolean, create: () -> T?): T? {
            if (!available) return null
            return try { create()?.also { it.enabled = false } } catch (e: Exception) { null }
        }
        val agcA = AutomaticGainControl.isAvailable()
        val nsA = NoiseSuppressor.isAvailable()
        val aecA = AcousticEchoCanceler.isAvailable()
        return EffectSet(
            tryDisable(agcA) { AutomaticGainControl.create(session) },
            tryDisable(nsA) { NoiseSuppressor.create(session) },
            tryDisable(aecA) { AcousticEchoCanceler.create(session) },
            agcA, nsA, aecA,
        )
    }

    companion object {
        fun deviceKeyOf(d: AudioDeviceInfo): String = when (d.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "builtin"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY ->
                "usb:" + d.productName.toString()
            else -> "type${d.type}:" + d.productName.toString()
        }

        fun describe(d: AudioDeviceInfo): String {
            val kind = when (d.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "내장 마이크"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "유선 헤드셋"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "블루투스(SCO)"
                else -> "입력(type ${d.type})"
            }
            return if (d.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) kind else "$kind ${d.productName}"
        }

        /** 로그 주파수축(20 Hz ~ Nyquist) 약 360점으로 축약, 각 구간 최대값 사용. */
        fun downsampleLog(s: Spectrum, points: Int = 360): Pair<FloatArray, FloatArray> {
            val fMin = 20.0
            val fMax = s.sampleRate / 2.0
            val fx = FloatArray(points)
            val dy = FloatArray(points)
            val ratio = ln(fMax / fMin)
            var prevBin = max(1, (fMin / s.binHz).toInt())
            for (i in 0 until points) {
                val fHi = fMin * Math.E.pow(ratio * (i + 1) / points)
                val hiBin = min(s.binCount - 1, max(prevBin, (fHi / s.binHz).toInt()))
                var m = -200.0
                for (k in prevBin..hiBin) m = max(m, s.amplitudeDbFs[k])
                fx[i] = (fMin * Math.E.pow(ratio * (i + 0.5) / points)).toFloat()
                dy[i] = m.toFloat()
                prevBin = hiBin + 1
                if (prevBin >= s.binCount) prevBin = s.binCount - 1
            }
            return fx to dy
        }
    }
}
