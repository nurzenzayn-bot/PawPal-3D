package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Unified Google Gemini Live Microphone Capture Engine built on [AudioRecord].
 *
 * Capabilities:
 * - Hardware Acoustic Echo Cancellation (AEC) & Noise Suppression (NS) via VOICE_COMMUNICATION
 *   so speaker playback does not echo back into the microphone.
 * - Smart Voice Barge-In: while the AI character is speaking ([isAiSpeaking] == true),
 *   monitors mic energy and invokes [onBargeInTriggered] as soon as the user begins speaking,
 *   seamlessly interrupting character playback and capturing the new utterance from frame 1.
 * - High-sensitivity Adaptive VAD with a 400ms pre-roll ring buffer and 750ms auto-endpointing,
 *   plus manual instant flush ([finishUtteranceNow]).
 */
class ContinuousMicWakeDetector(
    private val context: Context,
    private val onRmsChanged: (Float) -> Unit,
    private val onSpeechActivityChanged: (Boolean) -> Unit,
    private val onVoiceSegmentCaptured: (wavBytes: ByteArray, durationMs: Long, peakRms: Float) -> Unit,
    private val onLiveSessionSilenceTimeout: () -> Unit,
    private val onBargeInTriggered: () -> Unit = {}
) {
    companion object {
        private const val TAG = "ContinuousMicWake"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_DURATION_MS = 50 // 50ms per frame (20 fps smooth UI & fast endpointing)
        private const val SAMPLES_PER_FRAME = (SAMPLE_RATE * FRAME_DURATION_MS) / 1000
        private const val PRE_ROLL_FRAMES = 8 // 400ms pre-roll buffer
        private const val MIN_SPEECH_MS = 220L
        private const val MAX_SPEECH_MS = 12000L
        private const val END_SILENCE_MS = 750L
        private const val LIVE_SESSION_SILENCE_TIMEOUT_MS = 10000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    private var isHardPaused: Boolean = false

    @Volatile
    private var isAiSpeakingNow: Boolean = false

    @Volatile
    private var aiSpeechStartedMs: Long = 0L

    @Volatile
    private var aiPlaybackCooldownUntilMs: Long = 0L

    @Volatile
    private var isLiveSessionMode: Boolean = false

    @Volatile
    private var isHighSensitivity: Boolean = false

    @Volatile
    private var clearBuffersRequested: Boolean = false

    @Volatile
    private var lastLiveActivityElapsedMs: Long = SystemClock.elapsedRealtime()

    @Volatile
    private var lastAiPeakAmplitude: Float = 0f

    @Volatile
    private var lastAiActiveMs: Long = 0L

    @Volatile
    private var firstAudibleAiMs: Long = 0L

    @Volatile
    private var lastAiSurgeMs: Long = 0L

    @Volatile
    private var observedAiEchoRms: Double = 0.0

    @Volatile
    private var echoLockedOutForUtterance: Boolean = false

    @Volatile
    private var unpausedSettleUntilMs: Long = 0L

    @Volatile
    private var allowBargeIn: Boolean = true

    private val forceFlushRequested = AtomicBoolean(false)

    private val audioManager: AudioManager? by lazy {
        try {
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns true only when speaker output is physically isolated from the microphone
     * (wired headphones/headset, USB headset, Bluetooth SCO/BLE headset — excluding A2DP speakers
     * which play loudly into the room).
     */
    private fun hasAcousticIsolatedOutput(): Boolean {
        val am = audioManager ?: return false
        return try {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { dev ->
                when (dev.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
                    else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Pushes a short mic cooldown when UI chimes or touch reaction SFX play out of speakers
     * so sound effects never trigger `"Mendengarkanmu..."`.
     */
    fun suppressAudioForMs(durationMs: Long) {
        val target = SystemClock.elapsedRealtime() + durationMs.coerceIn(100L, 2500L)
        if (target > aiPlaybackCooldownUntilMs) {
            aiPlaybackCooldownUntilMs = target
        }
        clearBuffersRequested = true
    }

    fun setHighSensitivity(high: Boolean) {
        isHighSensitivity = high
    }

    fun setAllowBargeIn(allowed: Boolean) {
        allowBargeIn = allowed
        clearBuffersRequested = true
    }

    /**
     * Feeds real-time AI speaker PCM envelope (0f..1f) so Smart Barge-In suppresses
     * speaker reverberation and correlates loudspeaker attacks/sustains with mic energy.
     */
    fun updateAiPlaybackAmplitude(amplitude: Float) {
        val clamped = amplitude.coerceIn(0f, 1f)
        val now = SystemClock.elapsedRealtime()
        if (clamped > 0.05f) {
            if (firstAudibleAiMs == 0L && isAiSpeakingNow) {
                firstAudibleAiMs = now
            }
            if (clamped >= 0.35f || clamped - lastAiPeakAmplitude > 0.08f) {
                lastAiSurgeMs = now
            }
            lastAiActiveMs = now
            // Fast attack, slower decay so AudioTrack buffer lag (150-400ms) & room reverb tail are covered
            lastAiPeakAmplitude = max(clamped, lastAiPeakAmplitude * 0.94f)
        } else {
            lastAiPeakAmplitude *= 0.90f
        }
    }

    /**
     * Hard pause (e.g., while synthesizing AI reply or when user mutes mic in Video Call).
     */
    fun setPaused(paused: Boolean) {
        val wasPaused = isHardPaused
        isHardPaused = paused
        clearBuffersRequested = true
        if (!paused) {
            val now = SystemClock.elapsedRealtime()
            lastLiveActivityElapsedMs = now
            if (wasPaused) {
                unpausedSettleUntilMs = now + 160L
            }
            if (isAiSpeakingNow) {
                aiSpeechStartedMs = now
                if (firstAudibleAiMs == 0L) {
                    firstAudibleAiMs = now
                }
            }
        } else {
            forceFlushRequested.set(false)
            onSpeechActivityChanged(false)
            onRmsChanged(0f)
        }
    }

    /**
     * Notifies the mic engine whether the AI character is currently playing audio over the speakers.
     * Enables Echo-Envelope Smart Barge-In while guarding speakerphone / emulator loopback from
     * falsely cutting off the character mid-sentence.
     */
    fun setAiSpeaking(speaking: Boolean) {
        val wasSpeaking = isAiSpeakingNow
        isAiSpeakingNow = speaking
        val now = SystemClock.elapsedRealtime()
        if (speaking) {
            if (!wasSpeaking) {
                aiSpeechStartedMs = now
                firstAudibleAiMs = 0L
                lastAiSurgeMs = now
                lastAiActiveMs = now
                lastAiPeakAmplitude = 0.28f
                observedAiEchoRms = 0.0
                echoLockedOutForUtterance = false
                clearBuffersRequested = true
            }
            lastLiveActivityElapsedMs = now
        } else if (wasSpeaking) {
            // Tail guard so speaker reverb doesn't falsely trigger right when TTS ends
            aiPlaybackCooldownUntilMs = now + 900L
            lastLiveActivityElapsedMs = now
            firstAudibleAiMs = 0L
            lastAiSurgeMs = 0L
            lastAiPeakAmplitude = 0f
            observedAiEchoRms = 0.0
            echoLockedOutForUtterance = false
            clearBuffersRequested = true
        }
    }

    fun setLiveSessionMode(active: Boolean) {
        isLiveSessionMode = active
        lastLiveActivityElapsedMs = SystemClock.elapsedRealtime()
    }

    fun finishUtteranceNow() {
        forceFlushRequested.set(true)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            Log.w(TAG, "RECORD_AUDIO permission not granted yet; waiting to start ContinuousMicWakeDetector")
            return
        }

        isRunning = true
        captureJob?.cancel()
        captureJob = scope.launch {
            var audioRecord: AudioRecord? = null
            var echoCanceler: AcousticEchoCanceler? = null
            var noiseSuppressor: NoiseSuppressor? = null
            try {
                val minBufSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBufSize <= 0) {
                    Log.w(TAG, "AudioRecord minBufferSize invalid: $minBufSize")
                    isRunning = false
                    return@launch
                }

                val bufferSizeInBytes = max(minBufSize * 2, SAMPLES_PER_FRAME * 2 * 8)
                // Prefer VOICE_RECOGNITION / MIC so Android doesn't activate VoIP call AGC that pumps speaker echo or ducks USAGE_MEDIA audio
                audioRecord = tryOpenAudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, bufferSizeInBytes)
                    ?: tryOpenAudioRecord(MediaRecorder.AudioSource.MIC, bufferSizeInBytes)
                    ?: tryOpenAudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, bufferSizeInBytes)

                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord failed to initialize")
                    isRunning = false
                    return@launch
                }

                // Attach hardware Acoustic Echo Canceler & Noise Suppressor if supported by device
                val sessionId = audioRecord.audioSessionId
                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                            enabled = true
                        }
                        Log.d(TAG, "AcousticEchoCanceler attached (enabled=${echoCanceler?.enabled})")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AcousticEchoCanceler unavailable: ${e.message}")
                }

                try {
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                            enabled = true
                        }
                        Log.d(TAG, "NoiseSuppressor attached (enabled=${noiseSuppressor?.enabled})")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "NoiseSuppressor unavailable: ${e.message}")
                }

                audioRecord.startRecording()
                Log.d(TAG, "Unified Gemini Mic Engine started (16kHz Mono PCM + AEC/Barge-In)")

                val frameBuffer = ShortArray(SAMPLES_PER_FRAME)
                val preRollQueue = ArrayDeque<ShortArray>(PRE_ROLL_FRAMES)
                val recentRollingQueue = ArrayDeque<ShortArray>(50)
                val capturedPcmOut = ByteArrayOutputStream()

                var adaptiveNoiseFloorRms = 95.0
                var isSpeaking = false
                var consecutiveVoiceFrames = 0
                var speechDurationMs = 0L
                var silenceAfterSpeechMs = 0L
                var peakUiRms = 0f
                var lastRmsUiEmitMs = 0L
                var lastEmittedUiRms = 0f
                var bargeInFrames = 0
                var lastBargeInTriggerMs = 0L

                lastLiveActivityElapsedMs = SystemClock.elapsedRealtime()

                while (isActive && isRunning) {
                    if (clearBuffersRequested) {
                        clearBuffersRequested = false
                        val wasSpeaking = isSpeaking
                        isSpeaking = false
                        consecutiveVoiceFrames = 0
                        bargeInFrames = 0
                        speechDurationMs = 0L
                        silenceAfterSpeechMs = 0L
                        capturedPcmOut.reset()
                        preRollQueue.clear()
                        recentRollingQueue.clear()
                        if (wasSpeaking) {
                            onSpeechActivityChanged(false)
                            onRmsChanged(0f)
                        }
                    }

                    if (isHardPaused) {
                        if (isSpeaking) {
                            isSpeaking = false
                            consecutiveVoiceFrames = 0
                            bargeInFrames = 0
                            speechDurationMs = 0L
                            silenceAfterSpeechMs = 0L
                            capturedPcmOut.reset()
                            preRollQueue.clear()
                            recentRollingQueue.clear()
                            onSpeechActivityChanged(false)
                            onRmsChanged(0f)
                        }
                        // Actively drain & discard kernel AudioRecord buffer while paused so unpausing never replays stale audio bursts
                        val drained = audioRecord.read(frameBuffer, 0, SAMPLES_PER_FRAME)
                        if (drained <= 0) {
                            delay(50)
                        }
                        continue
                    }

                    val readCount = audioRecord.read(frameBuffer, 0, SAMPLES_PER_FRAME)
                    if (readCount <= 0) {
                        delay(30)
                        continue
                    }

                    // Re-check pause/reset flags immediately after blocking AudioRecord.read returns
                    if (isHardPaused || clearBuffersRequested) {
                        if (isSpeaking) {
                            isSpeaking = false
                            consecutiveVoiceFrames = 0
                            bargeInFrames = 0
                            speechDurationMs = 0L
                            silenceAfterSpeechMs = 0L
                            capturedPcmOut.reset()
                            preRollQueue.clear()
                            recentRollingQueue.clear()
                            onSpeechActivityChanged(false)
                            onRmsChanged(0f)
                        }
                        continue
                    }

                    val now = SystemClock.elapsedRealtime()

                    // Calculate frame RMS amplitude (0..32767)
                    var sumSquares = 0.0
                    for (i in 0 until readCount) {
                        val s = frameBuffer[i].toDouble()
                        sumSquares += s * s
                    }
                    val rmsAmplitude = sqrt(sumSquares / readCount)

                    val normalizedUiRms = if (rmsAmplitude > 24.0) {
                        ((20.0 * log10(rmsAmplitude / 55.0)).coerceIn(0.4, 10.0)).toFloat()
                    } else {
                        0f
                    }

                    val frameCopy = frameBuffer.copyOf(readCount)

                    // Discard transient kernel buffer audio briefly right after unpausing
                    if (!isAiSpeakingNow && now < unpausedSettleUntilMs) {
                        bargeInFrames = 0
                        consecutiveVoiceFrames = 0
                        continue
                    }

                    // Suppress post-speech speaker reverb tail without falsely firing barge-in
                    if (!isAiSpeakingNow && now < aiPlaybackCooldownUntilMs) {
                        bargeInFrames = 0
                        consecutiveVoiceFrames = 0
                        if (preRollQueue.size >= PRE_ROLL_FRAMES) {
                            preRollQueue.removeFirst()
                        }
                        preRollQueue.addLast(frameCopy)
                        continue
                    }

                    // Smart Barge-In detection strictly while AI is actively playing audio over speakers
                    if (isAiSpeakingNow) {
                        if (!allowBargeIn || echoLockedOutForUtterance) {
                            bargeInFrames = 0
                            consecutiveVoiceFrames = 0
                            continue
                        }

                        val isSpeakerActive = (now - lastAiActiveMs) < 1600L && lastAiPeakAmplitude > 0.04f

                        // Continuously measure speaker-to-mic bleed whenever AI speaker is emitting sound
                        if (isSpeakerActive) {
                            observedAiEchoRms = max(observedAiEchoRms * 0.996, rmsAmplitude)
                        }

                        // Only treat as true headset if an isolated headset is connected AND measured echo bleed is low
                        val isTrulyIsolatedHeadset = hasAcousticIsolatedOutput() && observedAiEchoRms < 1600.0

                        if (isTrulyIsolatedHeadset) {
                            // Headphones/BT isolate speaker sound from the mic -> responsive barge-in
                            if ((now - aiSpeechStartedMs) < 450L) {
                                bargeInFrames = 0
                                continue
                            }
                            val headsetThreshold = if (isHighSensitivity) {
                                max(2600.0, adaptiveNoiseFloorRms * 4.5)
                            } else {
                                max(3400.0, adaptiveNoiseFloorRms * 5.5)
                            }
                            if (preRollQueue.size >= PRE_ROLL_FRAMES) {
                                preRollQueue.removeFirst()
                            }
                            preRollQueue.addLast(frameCopy)

                            val requiredHeadsetFrames = if (isHighSensitivity) 6 else 8
                            if (rmsAmplitude >= headsetThreshold) {
                                bargeInFrames++
                                if (bargeInFrames >= requiredHeadsetFrames && (now - lastBargeInTriggerMs) > 1800L && isAiSpeakingNow && !isHardPaused) {
                                    lastBargeInTriggerMs = now
                                    bargeInFrames = 0
                                    isAiSpeakingNow = false
                                    aiPlaybackCooldownUntilMs = 0L
                                    lastAiPeakAmplitude = 0f
                                    observedAiEchoRms = 0.0
                                    echoLockedOutForUtterance = false
                                    Log.d(TAG, "Smart Barge-In triggered (headset) at RMS=$rmsAmplitude (threshold=$headsetThreshold)")
                                    onBargeInTriggered()

                                    isSpeaking = true
                                    consecutiveVoiceFrames = 2
                                    speechDurationMs = (preRollQueue.size * FRAME_DURATION_MS).toLong()
                                    silenceAfterSpeechMs = 0L
                                    peakUiRms = normalizedUiRms
                                    capturedPcmOut.reset()
                                    for (preFrame in preRollQueue) {
                                        writeShortsLittleEndian(capturedPcmOut, preFrame, preFrame.size)
                                    }
                                    preRollQueue.clear()
                                    lastLiveActivityElapsedMs = now
                                    onSpeechActivityChanged(true)
                                }
                            } else {
                                bargeInFrames = 0
                            }
                            continue
                        }

                        // Built-in Speaker / Bluetooth Speaker / Emulator Loopback path:
                        // Note: VOICE_RECOGNITION + USAGE_MEDIA does NOT engage hardware AEC on most devices/emulators.
                        val audibleAnchorMs = if (firstAudibleAiMs > 0L) firstAudibleAiMs else aiSpeechStartedMs

                        // If speaker loopback bleeds strongly into the mic on this device/emulator,
                        // lock out automatic RMS barge-in for this utterance so the character never cuts itself off.
                        if (observedAiEchoRms >= 4800.0) {
                            echoLockedOutForUtterance = true
                            bargeInFrames = 0
                            continue
                        }

                        // Onset guard: wait until first audible speaker PCM + 2200ms before allowing speakerphone barge-in
                        if (firstAudibleAiMs == 0L || (now - audibleAnchorMs) < 2200L) {
                            bargeInFrames = 0
                            continue
                        }

                        // Reject mic bursts correlated with active/loud AI speaker output or surges (within 700ms)
                        if ((now - lastAiSurgeMs) < 700L && isSpeakerActive) {
                            bargeInFrames = 0
                            continue
                        }

                        val bargeInThreshold = if (isSpeakerActive) {
                            val echoAdaptiveFloor = observedAiEchoRms * 3.8 + 5500.0
                            val echoBoost = lastAiPeakAmplitude * 15000.0
                            val speakerBase = if (isHighSensitivity) 14000.0 else 16000.0
                            max(speakerBase, echoAdaptiveFloor) + echoBoost
                        } else {
                            // Clear silent pause between AI sentences
                            val pauseFloor = observedAiEchoRms * 2.5 + 2600.0
                            val pauseBase = if (isHighSensitivity) 6200.0 else 7600.0
                            max(pauseBase, pauseFloor)
                        }

                        if (preRollQueue.size >= PRE_ROLL_FRAMES) {
                            preRollQueue.removeFirst()
                        }
                        preRollQueue.addLast(frameCopy)

                        val requiredFrames = if (isSpeakerActive) {
                            if (isHighSensitivity) 14 else 16
                        } else {
                            if (isHighSensitivity) 9 else 11
                        }
                        if (rmsAmplitude >= bargeInThreshold) {
                            bargeInFrames++
                            if (bargeInFrames >= requiredFrames && (now - lastBargeInTriggerMs) > 2000L && isAiSpeakingNow && !isHardPaused) {
                                // User intentionally interrupted the character!
                                lastBargeInTriggerMs = now
                                bargeInFrames = 0
                                isAiSpeakingNow = false
                                aiPlaybackCooldownUntilMs = 0L
                                lastAiPeakAmplitude = 0f
                                observedAiEchoRms = 0.0
                                echoLockedOutForUtterance = false
                                Log.d(TAG, "Smart Barge-In triggered (speaker) at RMS=$rmsAmplitude (threshold=$bargeInThreshold)")
                                onBargeInTriggered()

                                // Immediately transition into active recording using pre-roll frames
                                isSpeaking = true
                                consecutiveVoiceFrames = 2
                                speechDurationMs = (preRollQueue.size * FRAME_DURATION_MS).toLong()
                                silenceAfterSpeechMs = 0L
                                peakUiRms = normalizedUiRms
                                capturedPcmOut.reset()
                                for (preFrame in preRollQueue) {
                                    writeShortsLittleEndian(capturedPcmOut, preFrame, preFrame.size)
                                }
                                preRollQueue.clear()
                                lastLiveActivityElapsedMs = now
                                onSpeechActivityChanged(true)
                            }
                        } else {
                            bargeInFrames = 0
                        }
                        continue
                    }

                    bargeInFrames = 0

                    if (recentRollingQueue.size >= 50) {
                        recentRollingQueue.removeFirst()
                    }
                    recentRollingQueue.addLast(frameCopy)

                    if (now - lastRmsUiEmitMs >= 50L && abs(normalizedUiRms - lastEmittedUiRms) >= 0.25f) {
                        lastRmsUiEmitMs = now
                        lastEmittedUiRms = normalizedUiRms
                        onRmsChanged(normalizedUiRms)
                    }

                    val voiceThreshold = if (isHighSensitivity) {
                        max(150.0, adaptiveNoiseFloorRms * 1.65)
                    } else {
                        max(230.0, adaptiveNoiseFloorRms * 2.0)
                    }
                    val isVoiceFrame = rmsAmplitude >= voiceThreshold

                    if (forceFlushRequested.compareAndSet(true, false)) {
                        val pcmBytes = if (isSpeaking && capturedPcmOut.size() > 0) {
                            capturedPcmOut.toByteArray()
                        } else {
                            val fallbackOut = ByteArrayOutputStream()
                            for (f in recentRollingQueue) {
                                writeShortsLittleEndian(fallbackOut, f, f.size)
                            }
                            fallbackOut.toByteArray()
                        }
                        val totalDuration = if (speechDurationMs > 0L) {
                            speechDurationMs
                        } else {
                            (recentRollingQueue.size * FRAME_DURATION_MS).toLong()
                        }

                        isSpeaking = false
                        consecutiveVoiceFrames = 0
                        speechDurationMs = 0L
                        silenceAfterSpeechMs = 0L
                        capturedPcmOut.reset()
                        preRollQueue.clear()
                        recentRollingQueue.clear()
                        onSpeechActivityChanged(false)
                        onRmsChanged(0f)

                        if (pcmBytes.isNotEmpty()) {
                            val wavBytes = wrapPcmInWavHeader(pcmBytes, SAMPLE_RATE)
                            onVoiceSegmentCaptured(wavBytes, totalDuration.coerceAtLeast(400L), max(peakUiRms, 3f))
                        }
                        continue
                    }

                    if (!isSpeaking) {
                        if (!isVoiceFrame || rmsAmplitude < 650.0) {
                            val maxFloor = if (isHighSensitivity) 320.0 else 450.0
                            val alpha = if (!isVoiceFrame) 0.06 else 0.025
                            adaptiveNoiseFloorRms = (adaptiveNoiseFloorRms * (1.0 - alpha) + rmsAmplitude * alpha)
                                .coerceIn(45.0, maxFloor)
                        }

                        if (preRollQueue.size >= PRE_ROLL_FRAMES) {
                            preRollQueue.removeFirst()
                        }
                        preRollQueue.addLast(frameCopy)

                        val requiredVoiceFrames = if (isHighSensitivity) 2 else 3
                        if (isVoiceFrame) {
                            consecutiveVoiceFrames++
                            if (consecutiveVoiceFrames >= requiredVoiceFrames && !isHardPaused && !clearBuffersRequested && !isAiSpeakingNow) {
                                isSpeaking = true
                                speechDurationMs = (preRollQueue.size * FRAME_DURATION_MS).toLong()
                                silenceAfterSpeechMs = 0L
                                peakUiRms = normalizedUiRms
                                capturedPcmOut.reset()
                                for (preFrame in preRollQueue) {
                                    writeShortsLittleEndian(capturedPcmOut, preFrame, preFrame.size)
                                }
                                preRollQueue.clear()
                                lastLiveActivityElapsedMs = now
                                onSpeechActivityChanged(true)
                            }
                        } else {
                            consecutiveVoiceFrames = 0
                            if (isLiveSessionMode && (now - lastLiveActivityElapsedMs) >= LIVE_SESSION_SILENCE_TIMEOUT_MS) {
                                isLiveSessionMode = false
                                lastLiveActivityElapsedMs = now
                                onLiveSessionSilenceTimeout()
                            }
                        }
                    } else {
                        writeShortsLittleEndian(capturedPcmOut, frameBuffer, readCount)
                        speechDurationMs += FRAME_DURATION_MS
                        peakUiRms = max(peakUiRms, normalizedUiRms)
                        lastLiveActivityElapsedMs = now

                        if (isVoiceFrame) {
                            silenceAfterSpeechMs = 0L
                        } else {
                            silenceAfterSpeechMs += FRAME_DURATION_MS
                        }

                        val shouldFinish = silenceAfterSpeechMs >= END_SILENCE_MS || speechDurationMs >= MAX_SPEECH_MS
                        if (shouldFinish) {
                            val totalDuration = speechDurationMs
                            val pcmBytes = capturedPcmOut.toByteArray()
                            val capturedPeak = peakUiRms

                            isSpeaking = false
                            consecutiveVoiceFrames = 0
                            speechDurationMs = 0L
                            silenceAfterSpeechMs = 0L
                            capturedPcmOut.reset()
                            recentRollingQueue.clear()
                            onSpeechActivityChanged(false)
                            onRmsChanged(0f)

                            if (totalDuration >= MIN_SPEECH_MS && pcmBytes.isNotEmpty() && !isHardPaused && !clearBuffersRequested) {
                                val wavBytes = wrapPcmInWavHeader(pcmBytes, SAMPLE_RATE)
                                onVoiceSegmentCaptured(wavBytes, totalDuration, capturedPeak)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in ContinuousMicWakeDetector loop: ${e.message}", e)
            } finally {
                isRunning = false
                try {
                    echoCanceler?.release()
                } catch (_: Exception) {}
                try {
                    noiseSuppressor?.release()
                } catch (_: Exception) {}
                try {
                    audioRecord?.stop()
                } catch (_: Exception) {}
                try {
                    audioRecord?.release()
                } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryOpenAudioRecord(source: Int, bufferSizeInBytes: Int): AudioRecord? {
        return try {
            AudioRecord(
                source,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeInBytes
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (_: Exception) {
            null
        }
    }

    fun stop() {
        isRunning = false
        captureJob?.cancel()
        captureJob = null
    }

    private fun writeShortsLittleEndian(out: ByteArrayOutputStream, shorts: ShortArray, count: Int) {
        val bytes = ByteArray(count * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts, 0, count)
        out.write(bytes)
    }

    private fun wrapPcmInWavHeader(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(chunkSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        val wavBytes = ByteArray(44 + dataSize)
        System.arraycopy(header.array(), 0, wavBytes, 0, 44)
        System.arraycopy(pcmData, 0, wavBytes, 44, dataSize)
        return wavBytes
    }
}
