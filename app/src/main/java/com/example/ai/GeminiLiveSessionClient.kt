package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.model.AppLanguage
import com.example.model.PetCharacter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Real-Time Bidirectional Gemini Live API Client powered by Firebase AI Logic (`Firebase.ai.liveModel`).
 * Connects securely via App Check without requiring any manual API key!
 *
 * Supports:
 * - Real-time 16kHz PCM audio streaming input (`sendAudioRealtime`)
 * - Real-time camera frame streaming (`image/jpeg` via `sendVideoRealtime`)
 * - Real-time text/turn streaming (`send(content, turnComplete = true)`)
 * - Real-time 24kHz PCM audio streaming output played through low-latency `AudioTrack` (`MODE_STREAM`)
 * - Output audio transcription for live UI subtitles + chat history logging
 * - Instant barge-in / interruption handling
 */
@OptIn(PublicPreviewAPI::class)
class GeminiLiveSessionClient(
    private val context: Context,
    private val onConnectionStateChanged: (Boolean, String) -> Unit,
    private val onSpeakingStateChanged: (Boolean) -> Unit,
    private val onLiveTranscriptUpdate: (String) -> Unit,
    private val onTurnCompleted: (String) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit = {}
) {
    private val TAG = "GeminiLiveSession"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var liveSession: LiveSession? = null
    private var connectJob: Job? = null
    private var receiveJob: Job? = null
    private var audioTrack: AudioTrack? = null

    val isConnected = AtomicBoolean(false)
    val isSetupComplete = AtomicBoolean(false)
    private val isPlayingAudio = AtomicBoolean(false)
    private val isDisconnecting = AtomicBoolean(false)
    private val playbackGeneration = AtomicLong(0L)
    private val sessionGeneration = AtomicLong(0L)
    private var audioQueue: Channel<Pair<Long, ByteArray>> = Channel(Channel.UNLIMITED)
    private var audioConsumerJob: Job? = null

    @Volatile
    private var liveFirstWriteMs: Long = 0L

    @Volatile
    private var liveBytesWritten: Long = 0L

    private val liveModelsToTry = listOf(
        "gemini-3.1-flash-live-preview",
        "gemini-2.5-flash-native-audio-preview-12-2025"
    )

    private var activeCharacter: PetCharacter? = null
    private var activeSystemInstruction: String = ""
    private val currentTurnTranscript = StringBuilder()

    fun connect(
        apiKey: String = "",
        character: PetCharacter,
        language: AppLanguage,
        systemInstruction: String
    ) {
        disconnectInternal(notify = false, emitSpeakingEnd = false)
        isDisconnecting.set(false)
        activeCharacter = character
        activeSystemInstruction = systemInstruction
        val mySessionGen = sessionGeneration.incrementAndGet()

        val voiceName = when (character.id) {
            "zayn" -> "Puck"
            "siti" -> "Aoede"
            "rizky" -> "Fenrir"
            "jamal" -> "Charon"
            else -> "Orus"
        }

        connectJob = scope.launch {
            var connectedSession: LiveSession? = null
            var connectedModelName = ""

            for (modelName in liveModelsToTry) {
                if (isDisconnecting.get() || mySessionGen != sessionGeneration.get()) return@launch
                try {
                    Log.d(TAG, "Connecting to Firebase AI Logic Live API ($modelName / $voiceName)...")
                    val config = liveGenerationConfig {
                        responseModality = ResponseModality.AUDIO
                        speechConfig = SpeechConfig(voice = Voice(voiceName))
                        outputAudioTranscription = AudioTranscriptionConfig()
                    }
                    val sysContent = if (activeSystemInstruction.isNotBlank()) {
                        content { text(activeSystemInstruction) }
                    } else {
                        null
                    }
                    val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
                        modelName = modelName,
                        generationConfig = config,
                        systemInstruction = sysContent
                    )
                    val session = withTimeoutOrNull(14_000L) {
                        liveModel.connect()
                    }
                    if (session != null) {
                        connectedSession = session
                        connectedModelName = modelName
                        break
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Gemini Live connect failed on $modelName: ${e.message}")
                }
            }

            if (connectedSession == null || isDisconnecting.get() || mySessionGen != sessionGeneration.get()) {
                try {
                    connectedSession?.close()
                } catch (_: Exception) {}
                isConnected.set(false)
                isSetupComplete.set(false)
                onConnectionStateChanged(false, "")
                return@launch
            }

            liveSession = connectedSession
            isConnected.set(true)
            isSetupComplete.set(true)
            startAudioConsumerIfNeeded()
            Log.d(TAG, "Gemini Live session active ($connectedModelName)")
            onConnectionStateChanged(true, connectedModelName)

            receiveJob = scope.launch {
                collectLiveResponses(connectedSession, mySessionGen, connectedModelName)
            }
        }
    }

    private suspend fun collectLiveResponses(
        session: LiveSession,
        expectedGen: Long,
        modelName: String
    ) {
        try {
            session.receive().collect { message ->
                if (isDisconnecting.get() || expectedGen != sessionGeneration.get()) return@collect

                if (message is LiveServerContent) {
                    if (message.interrupted) {
                        interruptPlayback()
                        return@collect
                    }

                    val outTrans = message.outputTranscription?.text
                    if (!outTrans.isNullOrBlank()) {
                        currentTurnTranscript.append(outTrans)
                        onLiveTranscriptUpdate(currentTurnTranscript.toString().trim())
                    }

                    val parts = message.content?.parts.orEmpty()
                    for (part in parts) {
                        when (part) {
                            is TextPart -> {
                                val partText = part.text
                                if (partText.isNotBlank() && outTrans.isNullOrBlank()) {
                                    currentTurnTranscript.append(partText)
                                    onLiveTranscriptUpdate(currentTurnTranscript.toString().trim())
                                }
                            }
                            is InlineDataPart -> {
                                val bytes = part.inlineData
                                if (bytes.isNotEmpty() &&
                                    (part.mimeType.isBlank() || part.mimeType.contains("audio", ignoreCase = true))
                                ) {
                                    playStreamingPcmChunk(stripWavHeaderIfPresent(bytes))
                                }
                            }
                        }
                    }

                    if (message.turnComplete) {
                        val finalTranscript = currentTurnTranscript.toString().trim()
                        currentTurnTranscript.clear()
                        val turnGen = playbackGeneration.get()
                        scope.launch {
                            val startMs = liveFirstWriteMs
                            val totalBytes = liveBytesWritten
                            val drainDelayMs = if (startMs > 0L && totalBytes > 0L) {
                                val expectedMs = (totalBytes * 1000L) / (24000L * 2L)
                                val elapsedMs = (android.os.SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0L)
                                (expectedMs - elapsedMs + 220L).coerceIn(220L, 6500L)
                            } else {
                                280L
                            }
                            var waited = 0L
                            while (waited < drainDelayMs && !isDisconnecting.get() && turnGen == playbackGeneration.get()) {
                                val progress = (waited.toFloat() / drainDelayMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                onAmplitudeChanged((0.26f * (1f - progress * 0.5f)).coerceAtLeast(0.14f))
                                val step = (drainDelayMs - waited).coerceAtMost(50L)
                                delay(step)
                                waited += step
                            }
                            if (isDisconnecting.get() || turnGen != playbackGeneration.get()) return@launch
                            liveFirstWriteMs = 0L
                            liveBytesWritten = 0L
                            onAmplitudeChanged(0f)
                            if (isPlayingAudio.getAndSet(false)) {
                                onSpeakingStateChanged(false)
                            }
                            if (finalTranscript.isNotBlank()) {
                                onTurnCompleted(finalTranscript)
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Live receive loop ended ($modelName): ${e.message}")
        } finally {
            if (!isDisconnecting.get() && expectedGen == sessionGeneration.get()) {
                isSetupComplete.set(false)
                isConnected.set(false)
                onConnectionStateChanged(false, "")
            }
        }
    }

    /**
     * Streams a 16kHz 16-bit mono PCM audio chunk directly to the active Firebase AI Live session.
     * Automatically strips the 44-byte WAV header if a WAV buffer is passed in.
     */
    fun sendRealtimeAudioChunk(audioBytes: ByteArray): Boolean {
        val session = liveSession ?: return false
        if (!isConnected.get() || !isSetupComplete.get() || session.isClosed()) return false

        val rawPcm = stripWavHeaderIfPresent(audioBytes)
        if (rawPcm.isEmpty()) return false

        scope.launch {
            try {
                session.sendAudioRealtime(InlineData(rawPcm, "audio/pcm;rate=16000"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Failed to stream realtime audio chunk: ${e.message}")
            }
        }
        return true
    }

    /**
     * Sends a live camera frame (`image/jpeg`) over the Firebase AI Live session.
     */
    fun sendRealtimeCameraFrame(bitmap: Bitmap): Boolean {
        val session = liveSession ?: return false
        if (!isConnected.get() || !isSetupComplete.get() || session.isClosed()) return false

        val jpegBytes = encodeBitmapJpeg(bitmap) ?: return false
        scope.launch {
            try {
                session.sendVideoRealtime(InlineData(jpegBytes, "image/jpeg"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Failed to send camera frame to Live API: ${e.message}")
            }
        }
        return true
    }

    /**
     * Sends a complete user turn (text + optional camera frame) to the active Firebase AI Live session
     * so it streams back real-time voice audio immediately.
     */
    fun sendUserTurn(text: String, cameraBitmap: Bitmap? = null): Boolean {
        val session = liveSession ?: return false
        if (!isConnected.get() || !isSetupComplete.get() || session.isClosed()) return false

        val jpegBytes = cameraBitmap?.let { encodeBitmapJpeg(it) }
        currentTurnTranscript.clear()
        scope.launch {
            try {
                if (jpegBytes != null) {
                    session.sendVideoRealtime(InlineData(jpegBytes, "image/jpeg"))
                }
                session.send(content("user") { text(text) }, turnComplete = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send Live user turn: ${e.message}")
            }
        }
        return true
    }

    private fun encodeBitmapJpeg(bitmap: Bitmap): ByteArray? {
        return try {
            val scaled = if (bitmap.width > 640 || bitmap.height > 640) {
                val scale = 640f / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            baos.toByteArray()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to compress camera frame: ${e.message}")
            null
        }
    }

    private fun startAudioConsumerIfNeeded() {
        if (audioConsumerJob?.isActive == true) return
        audioConsumerJob = scope.launch {
            for ((gen, pcm24kBytes) in audioQueue) {
                if (gen != playbackGeneration.get() || isDisconnecting.get()) continue
                val alignedSize = pcm24kBytes.size and 0x7FFFFFFE
                if (alignedSize <= 0) continue
                try {
                    val track = ensureAudioTrack()
                    if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                        Log.w(TAG, "AudioTrack unavailable in GeminiLiveSessionClient")
                        continue
                    }
                    if (liveFirstWriteMs == 0L) {
                        liveFirstWriteMs = android.os.SystemClock.elapsedRealtime()
                        liveBytesWritten = 0L
                    }
                    if (isPlayingAudio.compareAndSet(false, true)) {
                        onSpeakingStateChanged(true)
                    }
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                    onAmplitudeChanged(calculateNormalizedPcmRms(pcm24kBytes, alignedSize))
                    val written = track.write(pcm24kBytes, 0, alignedSize, AudioTrack.WRITE_BLOCKING)
                    if (written > 0) {
                        liveBytesWritten += written
                    } else if (written < 0) {
                        Log.w(TAG, "GeminiLiveSessionClient AudioTrack write error: $written")
                        synchronized(this) {
                            try { audioTrack?.release() } catch (_: Exception) {}
                            audioTrack = null
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing PCM chunk to AudioTrack: ${e.message}")
                }
            }
        }
    }

    private fun playStreamingPcmChunk(pcm24kBytes: ByteArray) {
        if (pcm24kBytes.isEmpty() || isDisconnecting.get()) return
        startAudioConsumerIfNeeded()
        if (isPlayingAudio.compareAndSet(false, true)) {
            onSpeakingStateChanged(true)
        }
        audioQueue.trySend(playbackGeneration.get() to pcm24kBytes)
    }

    private fun calculateNormalizedPcmRms(pcmBytes: ByteArray, alignedSize: Int = pcmBytes.size and 0x7FFFFFFE): Float {
        val sampleCount = alignedSize / 2
        if (sampleCount <= 0) return 0f
        var sumSquares = 0.0
        var i = 0
        val limit = sampleCount * 2
        while (i < limit) {
            val low = pcmBytes[i].toInt() and 0xFF
            val high = pcmBytes[i + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toDouble()
            sumSquares += sample * sample
            i += 2
        }
        val rms = sqrt(sumSquares / sampleCount)
        return (rms / 7500.0).toFloat().coerceIn(0f, 1f)
    }

    @Synchronized
    private fun ensureAudioTrack(): AudioTrack? {
        val existing = audioTrack
        if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED) {
            return existing
        }
        try {
            existing?.release()
        } catch (_: Exception) {}
        audioTrack = null

        val sampleRate = 24000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4800)

        for (bufSize in listOf(minBuf * 2, minBuf)) {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    audioTrack = track
                    return track
                } else {
                    track.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack init failed (buf=$bufSize): ${e.message}")
            }
        }
        return null
    }

    fun interruptPlayback(emitSpeakingEnd: Boolean = true) {
        playbackGeneration.incrementAndGet()
        liveFirstWriteMs = 0L
        liveBytesWritten = 0L
        currentTurnTranscript.clear()
        while (audioQueue.tryReceive().isSuccess) {
            // Drain queued chunks immediately
        }
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                }
            }
        } catch (_: Exception) {}
        onAmplitudeChanged(0f)
        if (isPlayingAudio.getAndSet(false) && emitSpeakingEnd) {
            onSpeakingStateChanged(false)
        }
    }

    fun disconnect() {
        disconnectInternal(notify = true, emitSpeakingEnd = false)
    }

    private fun disconnectInternal(notify: Boolean, emitSpeakingEnd: Boolean = false) {
        sessionGeneration.incrementAndGet()
        isDisconnecting.set(true)
        isSetupComplete.set(false)
        isConnected.set(false)
        connectJob?.cancel()
        connectJob = null
        receiveJob?.cancel()
        receiveJob = null
        interruptPlayback(emitSpeakingEnd = emitSpeakingEnd)
        audioConsumerJob?.cancel()
        audioConsumerJob = null

        val sessionToClose = liveSession
        liveSession = null
        if (sessionToClose != null) {
            scope.launch {
                try {
                    sessionToClose.close()
                } catch (_: Exception) {}
            }
        }

        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        if (notify) {
            onConnectionStateChanged(false, "")
        }
    }

    private fun stripWavHeaderIfPresent(bytes: ByteArray): ByteArray {
        if (bytes.size > 44 &&
            bytes[0].toInt() == 'R'.code &&
            bytes[1].toInt() == 'I'.code &&
            bytes[2].toInt() == 'F'.code &&
            bytes[3].toInt() == 'F'.code
        ) {
            return bytes.copyOfRange(44, bytes.size)
        }
        return bytes
    }
}
