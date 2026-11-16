package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.AppLanguage
import com.example.model.AvatarEmotion
import com.example.model.PetCharacter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

enum class VoiceEngineMode(val displayName: String, val icon: String, val description: String) {
    GEMINI_FLASH_TTS(
        displayName = "Gemini Native Audio Stream (100% AI)",
        icon = "🎙️",
        description = "Streaming PCM langsung dari Gemini AI ke AudioTrack dengan lip-sync & emosi dinamis tanpa fallback TTS lokal."
    ),
    CARTOON_MIMIC(
        displayName = "Gemini AI Expressive (Gaya Kartun)",
        icon = "🐱",
        description = "Streaming suara Gemini AI dengan intonasi karakter animasi yang hidup dan ekspresif."
    ),
    GOOGLE_CLOUD_NEURAL(
        displayName = "Gemini Native Audio Stream",
        icon = "🎙️",
        description = "Streaming suara murni Gemini AI penuh ekspresi emosi."
    ),
    ELEVENLABS_NEURAL(
        displayName = "Gemini Native Audio Stream",
        icon = "🎙️",
        description = "Streaming suara murni Gemini AI penuh ekspresi emosi."
    )
}

/**
 * Pure Gemini Native Audio Pipeline powered by Firebase AI Logic (`Firebase.ai`).
 *
 * Synthesizes character speech natively via Gemini AI models (zero Android TextToSpeech fallback)
 * and streams 24kHz 16-bit mono PCM audio to device speakers via `AudioTrack` (`MODE_STREAM`):
 * 1. Gemini TTS models (`gemini-3.1-flash-tts-preview`, `gemini-2.5-flash-preview-tts`) via `generateContent` + stream
 * 2. Gemini Live Native Audio fallback (`gemini-3.1-flash-live-preview`, `gemini-2.5-flash-native-audio-preview-12-2025`)
 * 3. Optional custom Gemini REST SSE if user manually supplies a custom API key
 */
@OptIn(PublicPreviewAPI::class)
class NeuralTtsClient(
    private val context: Context,
    private val onSpeakingStateChanged: (Boolean) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit = {},
    private val onEmotionalToneChanged: (String) -> Unit = {}
) {
    private val TAG = "GeminiAudioStream"

    private var audioTrack: AudioTrack? = null
    private var currentTrackSampleRate: Int = 24000
    private val isStreamingStopped = AtomicBoolean(false)
    private val isPlayingAudio = AtomicBoolean(false)
    private val streamGeneration = AtomicLong(0L)

    @Volatile
    private var streamFirstWriteMs: Long = 0L

    @Volatile
    private var streamTotalBytesWritten: Long = 0L

    @Volatile
    private var streamActiveSampleRate: Int = 24000

    @Volatile
    private var lastWrittenAmplitude: Float = 0f

    @Volatile
    private var failedRestAuthKey: String? = null

    @Volatile
    private var lastFailureReason: String = ""

    private val streamHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // In-memory LRU raw PCM cache (cacheKey -> Pair(rawPcmBytes, sampleRate))
    private val pcmStreamCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<ByteArray, Int>>(24, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Pair<ByteArray, Int>>?
            ): Boolean = size > 20
        }
    )

    fun stop(notifyIdle: Boolean = true) {
        streamGeneration.incrementAndGet()
        isStreamingStopped.set(true)
        streamFirstWriteMs = 0L
        streamTotalBytesWritten = 0L
        lastWrittenAmplitude = 0f
        try {
            synchronized(this) {
                audioTrack?.let { track ->
                    if (track.state == AudioTrack.STATE_INITIALIZED) {
                        try {
                            track.pause()
                            track.flush()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack", e)
        } finally {
            onAmplitudeChanged(0f)
            val wasPlaying = isPlayingAudio.getAndSet(false)
            if (notifyIdle && wasPlaying) {
                onSpeakingStateChanged(false)
            }
        }
    }

    fun release() {
        stop()
        synchronized(this) {
            try {
                audioTrack?.release()
            } catch (_: Exception) {}
            audioTrack = null
        }
    }

    private fun resolveOptionalCustomGeminiKey(providedKey: String): String {
        val candidateProvided = providedKey.trim()
        if (candidateProvided.isNotBlank() &&
            !candidateProvided.contains("MY_GEMINI_API_KEY") &&
            !candidateProvided.contains("YOUR_API_KEY")
        ) {
            return candidateProvided
        }

        val prefs = context.getSharedPreferences("pawpal_ai_prefs", Context.MODE_PRIVATE)
        val savedCustom = prefs.getString("custom_gemini_api_key", "")?.trim().orEmpty()
        if (savedCustom.isNotBlank()) return savedCustom

        val buildConfigKey = try {
            val key = BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
            key?.trim()?.takeIf {
                it.isNotBlank() &&
                    !it.contains("MY_GEMINI_API_KEY") &&
                    !it.contains("YOUR_API_KEY") &&
                    it != "null"
            }
        } catch (_: Exception) {
            null
        }
        return buildConfigKey.orEmpty()
    }

    /**
     * Synthesizes & streams Gemini AI native audio to device speakers via `AudioTrack` (`MODE_STREAM`).
     */
    suspend fun speakWithGeminiTts(
        text: String,
        apiKey: String,
        character: PetCharacter,
        language: AppLanguage,
        emotion: AvatarEmotion = AvatarEmotion.TALKING,
        overrideLanguageName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanText = sanitizeTextForSpeech(text)
        if (cleanText.isBlank()) return@withContext false

        stop(notifyIdle = false)
        isStreamingStopped.set(false)
        lastFailureReason = ""
        val myGen = streamGeneration.get()

        val voiceName = resolvePrebuiltVoiceName(character)
        val bcp47Lang = resolveBcp47LanguageCode(language, overrideLanguageName)
        val directive = buildDynamicEmotionalDirective(
            text = cleanText,
            character = character,
            language = language,
            emotion = emotion,
            overrideLanguageName = overrideLanguageName
        )
        onEmotionalToneChanged(directive.emotionTagLabel)

        val cacheKey = "${character.id}_${language.code}_${overrideLanguageName.orEmpty()}_${emotion.name}_${cleanText.hashCode()}"
        pcmStreamCache[cacheKey]?.let { (cachedPcm, cachedRate) ->
            if (cachedPcm.isNotEmpty()) {
                val replayed = streamPcmBufferToSpeakerWithLipSync(cachedPcm, cachedRate, myGen)
                if (replayed) return@withContext true
            }
        }

        // 1. Primary path: Dedicated Gemini TTS Models via Firebase AI Logic (generateContent unary first, then stream)
        val rawTtsModels = listOf(
            "gemini-3.1-flash-tts-preview",
            "gemini-2.5-flash-preview-tts"
        )
        val ttsModels = rawTtsModels.filter { !unavailableAudioModels.contains(it) }.ifEmpty { rawTtsModels }
        for (modelName in ttsModels) {
            currentCoroutineContext().ensureActive()
            if (isStreamingStopped.get() || myGen != streamGeneration.get()) return@withContext false

            val played = tryFirebaseTtsModel(
                modelName = modelName,
                voiceName = voiceName,
                styledPrompt = directive.styledTtsPrompt,
                plainText = cleanText,
                cacheKey = cacheKey,
                expectedGen = myGen
            )
            if (played) {
                Log.d(TAG, "Played Gemini AI Native TTS via Firebase AI Logic ($modelName / $voiceName)")
                return@withContext true
            }
        }

        // 2. Resilient Native Audio Fallback: Firebase AI Logic Live API single-turn recitation
        val rawLiveFallbackModels = listOf(
            "gemini-3.1-flash-live-preview",
            "gemini-2.5-flash-native-audio-preview-12-2025"
        )
        val liveFallbackModels = rawLiveFallbackModels.filter { !unavailableAudioModels.contains(it) }.ifEmpty { rawLiveFallbackModels }
        for (liveModelName in liveFallbackModels) {
            currentCoroutineContext().ensureActive()
            if (isStreamingStopped.get() || myGen != streamGeneration.get()) return@withContext false

            val livePlayed = tryOneShotLiveRecitation(
                modelName = liveModelName,
                voiceName = voiceName,
                character = character,
                actingCueEnglish = directive.englishActingCue,
                cleanText = cleanText,
                cacheKey = cacheKey,
                expectedGen = myGen
            )
            if (livePlayed) {
                Log.d(TAG, "Played Gemini AI Native Live Recitation ($liveModelName / $voiceName)")
                return@withContext true
            }
        }

        // 3. Optional secondary path: Only if user manually provided a custom Gemini API Key
        val customRestKey = resolveOptionalCustomGeminiKey(apiKey)
        if (customRestKey.isNotBlank() && failedRestAuthKey != customRestKey) {
            for (restModel in ttsModels) {
                currentCoroutineContext().ensureActive()
                if (isStreamingStopped.get() || myGen != streamGeneration.get()) return@withContext false

                val sseOutcome = tryStreamGeminiAudioSse(
                    modelName = restModel,
                    apiKey = customRestKey,
                    voiceName = voiceName,
                    expressivePrompt = directive.styledTtsPrompt,
                    cacheKey = cacheKey,
                    expectedGen = myGen
                )
                if (sseOutcome == AttemptOutcome.SUCCESS) {
                    Log.d(TAG, "Streamed Gemini AI Native Audio via custom key SSE ($restModel / $voiceName)")
                    return@withContext true
                } else if (sseOutcome == AttemptOutcome.AUTH_ERROR) {
                    failedRestAuthKey = customRestKey
                    break
                }
            }
        }

        if (myGen == streamGeneration.get()) {
            val detail = summarizeFailureReason(lastFailureReason)
            Log.e(TAG, "All Gemini AI audio paths failed. Detail: $lastFailureReason")
            onEmotionalToneChanged(
                if (detail.isNotBlank()) "Audio Gemini gagal: $detail ⚠️"
                else "Audio Gemini Gagal Terhubung ⚠️"
            )
            onAmplitudeChanged(0f)
            if (isPlayingAudio.getAndSet(false)) {
                onSpeakingStateChanged(false)
            }
        }
        return@withContext false
    }

    private val unavailableAudioModels: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun isModelNotFoundError(msg: String): Boolean {
        val lower = msg.lowercase()
        return lower.contains("404") ||
            lower.contains("not found") ||
            lower.contains("not_found") ||
            lower.contains("does not exist")
    }

    private suspend fun tryFirebaseTtsModel(
        modelName: String,
        voiceName: String,
        styledPrompt: String,
        plainText: String,
        cacheKey: String,
        expectedGen: Long
    ): Boolean {
        // Per Firebase AI Logic guidelines, TTS models auto-detect language from prompt text
        // and only take SpeechConfig(voice = Voice(voiceName)) without languageCode.
        val configsToTry = listOf(
            generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(voice = Voice(voiceName))
            } to styledPrompt,
            generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(voice = Voice(voiceName))
            } to plainText
        )

        val aiClient = Firebase.ai(backend = GenerativeBackend.googleAI())

        // Attempt A: Official Unary `generateContent` (recommended for Gemini TTS models)
        for ((cfg, promptToSend) in configsToTry) {
            if (isStreamingStopped.get() || expectedGen != streamGeneration.get()) return false
            try {
                val model = aiClient.generativeModel(modelName = modelName, generationConfig = cfg)
                val response = withTimeoutOrNull(22_000L) {
                    model.generateContent(promptToSend)
                }
                if (response == null) {
                    recordFailure("$modelName unary timeout")
                    continue
                }
                val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
                val unaryBuffer = ByteArrayOutputStream()
                var sampleRate = 24000
                for (part in parts) {
                    if (part is InlineDataPart && part.inlineData.isNotEmpty()) {
                        sampleRate = parseSampleRateFromMime(part.mimeType)
                        unaryBuffer.write(stripWavHeaderIfPresent(part.inlineData))
                    }
                }
                val finalBytes = unaryBuffer.toByteArray()
                if (finalBytes.isNotEmpty() && !isStreamingStopped.get() && expectedGen == streamGeneration.get()) {
                    pcmStreamCache[cacheKey] = finalBytes to sampleRate
                    return streamPcmBufferToSpeakerWithLipSync(finalBytes, sampleRate, expectedGen)
                } else {
                    recordFailure("$modelName returned no audio data")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                recordFailure("$modelName unary: $msg")
                Log.w(TAG, "Firebase AI generateContent audio ($modelName) failed: $msg")
                if (isModelNotFoundError(msg)) {
                    unavailableAudioModels.add(modelName)
                    return false
                }
            }
        }

        if (isStreamingStopped.get() || expectedGen != streamGeneration.get()) return false

        // Attempt B: Streaming `generateContentStream` with auto-detect SpeechConfig
        try {
            val streamCfg = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(voice = Voice(voiceName))
            }
            val model = aiClient.generativeModel(modelName = modelName, generationConfig = streamCfg)
            val accumulatedPcm = ByteArrayOutputStream()
            var detectedSampleRate = 24000
            var wroteAnyPcm = false

            withTimeoutOrNull(22_000L) {
                model.generateContentStream(styledPrompt).collect { chunk ->
                    currentCoroutineContext().ensureActive()
                    if (isStreamingStopped.get() || expectedGen != streamGeneration.get()) return@collect

                    val parts = chunk.candidates.firstOrNull()?.content?.parts.orEmpty()
                    for (part in parts) {
                        if (part is InlineDataPart && part.inlineData.isNotEmpty()) {
                            val rate = parseSampleRateFromMime(part.mimeType)
                            val cleanPcm = stripWavHeaderIfPresent(part.inlineData)
                            if (cleanPcm.isNotEmpty()) {
                                detectedSampleRate = rate
                                accumulatedPcm.write(cleanPcm)
                                wroteAnyPcm = true
                                writePcmFramesWithLipSync(cleanPcm, rate, expectedGen)
                            }
                        }
                    }
                }
            }

            if (wroteAnyPcm && !isStreamingStopped.get() && expectedGen == streamGeneration.get()) {
                val fullPcm = accumulatedPcm.toByteArray()
                if (fullPcm.isNotEmpty()) {
                    pcmStreamCache[cacheKey] = fullPcm to detectedSampleRate
                }
                finishActiveAudioStream(expectedGen)
                return true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            recordFailure("$modelName stream: $msg")
            if (isModelNotFoundError(msg)) {
                unavailableAudioModels.add(modelName)
            }
            Log.w(TAG, "Firebase AI generateContentStream audio ($modelName) failed: $msg")
        }

        return false
    }

    /**
     * Single-turn Live API native audio recitation fallback using `Firebase.ai.liveModel(...)`.
     * Guarantees native Gemini AI voice even if TTS-preview models are unavailable.
     */
    private suspend fun tryOneShotLiveRecitation(
        modelName: String,
        voiceName: String,
        character: PetCharacter,
        actingCueEnglish: String,
        cleanText: String,
        cacheKey: String,
        expectedGen: Long
    ): Boolean {
        var session: LiveSession? = null
        val accumulatedPcm = ByteArrayOutputStream()
        var detectedSampleRate = 24000
        var wroteAnyPcm = false

        return try {
            val liveConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice(voiceName))
            }
            val sysInstruction = content {
                text(
                    "You are the voice actor for ${character.name}. " +
                        "Read aloud verbatim the user's exact text $actingCueEnglish. " +
                        "Speak only those words clearly and expressively without adding extra commentary."
                )
            }
            val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
                modelName = modelName,
                generationConfig = liveConfig,
                systemInstruction = sysInstruction
            )

            session = withTimeoutOrNull(14_000L) {
                liveModel.connect()
            }
            if (session == null) {
                recordFailure("$modelName live connect timeout")
                return false
            }
            if (isStreamingStopped.get() || expectedGen != streamGeneration.get()) return false

            session.send(content("user") { text(cleanText) }, turnComplete = true)

            withTimeoutOrNull(25_000L) {
                session.receive().collect { message ->
                    currentCoroutineContext().ensureActive()
                    if (isStreamingStopped.get() || expectedGen != streamGeneration.get()) {
                        return@collect
                    }
                    if (message is LiveServerContent) {
                        val parts = message.content?.parts.orEmpty()
                        for (part in parts) {
                            if (part is InlineDataPart && part.inlineData.isNotEmpty() &&
                                (part.mimeType.isBlank() || part.mimeType.contains("audio", ignoreCase = true))
                            ) {
                                val rate = parseSampleRateFromMime(part.mimeType)
                                val cleanPcm = stripWavHeaderIfPresent(part.inlineData)
                                if (cleanPcm.isNotEmpty()) {
                                    detectedSampleRate = rate
                                    accumulatedPcm.write(cleanPcm)
                                    wroteAnyPcm = true
                                    writePcmFramesWithLipSync(cleanPcm, rate, expectedGen)
                                }
                            }
                        }
                        if (message.turnComplete) {
                            throw TurnFinishedSignal()
                        }
                    }
                }
            }

            if (wroteAnyPcm && !isStreamingStopped.get() && expectedGen == streamGeneration.get()) {
                val fullPcm = accumulatedPcm.toByteArray()
                if (fullPcm.isNotEmpty()) {
                    pcmStreamCache[cacheKey] = fullPcm to detectedSampleRate
                }
                finishActiveAudioStream(expectedGen)
                true
            } else {
                recordFailure("$modelName live yielded no audio")
                false
            }
        } catch (_: TurnFinishedSignal) {
            if (wroteAnyPcm && !isStreamingStopped.get() && expectedGen == streamGeneration.get()) {
                val fullPcm = accumulatedPcm.toByteArray()
                if (fullPcm.isNotEmpty()) {
                    pcmStreamCache[cacheKey] = fullPcm to detectedSampleRate
                }
                finishActiveAudioStream(expectedGen)
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            recordFailure("$modelName live: $msg")
            Log.w(TAG, "Firebase AI Live recitation ($modelName) failed: $msg")
            false
        } finally {
            try {
                session?.close()
            } catch (_: Exception) {}
        }
    }

    private class TurnFinishedSignal : Exception()

    private enum class AttemptOutcome {
        SUCCESS,
        RETRYABLE_FAILURE,
        AUTH_ERROR
    }

    private suspend fun tryStreamGeminiAudioSse(
        modelName: String,
        apiKey: String,
        voiceName: String,
        expressivePrompt: String,
        cacheKey: String,
        expectedGen: Long
    ): AttemptOutcome {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?alt=sse&key=$apiKey"
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", expressivePrompt))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", voiceName)
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "text/event-stream")
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val accumulatedPcm = ByteArrayOutputStream()
        var detectedSampleRate = 24000
        var wroteAnyPcm = false

        try {
            streamHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    recordFailure("REST $modelName HTTP ${response.code}")
                    Log.d(TAG, "Gemini SSE stream ($modelName) HTTP ${response.code}")
                    return if (response.code == 400 || response.code == 401 || response.code == 403) {
                        AttemptOutcome.AUTH_ERROR
                    } else {
                        AttemptOutcome.RETRYABLE_FAILURE
                    }
                }

                val source = response.body?.source() ?: return AttemptOutcome.RETRYABLE_FAILURE
                while (!source.exhausted() && !isStreamingStopped.get() && expectedGen == streamGeneration.get()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue

                    val payloadStr = line.removePrefix("data:").trim()
                    if (payloadStr.isBlank() || payloadStr == "[DONE]") continue

                    val chunkPair = extractRawPcmFromCandidateJson(payloadStr)
                    if (chunkPair != null) {
                        val (rawPcmBytes, sampleRate) = chunkPair
                        if (rawPcmBytes.isNotEmpty()) {
                            detectedSampleRate = sampleRate
                            accumulatedPcm.write(rawPcmBytes)
                            wroteAnyPcm = true
                            writePcmFramesWithLipSync(rawPcmBytes, sampleRate, expectedGen)
                        }
                    }
                }
            }

            if (wroteAnyPcm && !isStreamingStopped.get() && expectedGen == streamGeneration.get()) {
                val fullPcm = accumulatedPcm.toByteArray()
                if (fullPcm.isNotEmpty()) {
                    pcmStreamCache[cacheKey] = fullPcm to detectedSampleRate
                }
                finishActiveAudioStream(expectedGen)
                return AttemptOutcome.SUCCESS
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure("REST SSE: ${e.message}")
            Log.d(TAG, "Gemini SSE audio stream ($modelName) interrupted: ${e.message}")
        }
        return AttemptOutcome.RETRYABLE_FAILURE
    }

    private fun extractRawPcmFromCandidateJson(jsonStr: String): Pair<ByteArray, Int>? {
        val respJson = JSONObject(jsonStr)
        val candidates = respJson.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts") ?: return null

        val combinedPcm = ByteArrayOutputStream()
        var sampleRate = 24000

        for (i in 0 until parts.length()) {
            val inlineData = parts.getJSONObject(i).optJSONObject("inlineData") ?: continue
            val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
            val dataBase64 = inlineData.optString("data", "")
            if (dataBase64.isNotBlank()) {
                val decoded = Base64.decode(dataBase64, Base64.DEFAULT)
                sampleRate = parseSampleRateFromMime(mimeType)
                val cleanPcm = stripWavHeaderIfPresent(decoded)
                combinedPcm.write(cleanPcm)
            }
        }

        val finalBytes = combinedPcm.toByteArray()
        return if (finalBytes.isNotEmpty()) finalBytes to sampleRate else null
    }

    private suspend fun streamPcmBufferToSpeakerWithLipSync(
        rawPcmBytes: ByteArray,
        sampleRate: Int,
        expectedGen: Long
    ): Boolean {
        if (rawPcmBytes.isEmpty() || isStreamingStopped.get() || expectedGen != streamGeneration.get()) return false
        return try {
            val wrote = writePcmFramesWithLipSync(rawPcmBytes, sampleRate, expectedGen)
            if (wrote && !isStreamingStopped.get() && expectedGen == streamGeneration.get()) {
                finishActiveAudioStream(expectedGen)
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure("AudioTrack playback: ${e.message}")
            Log.e(TAG, "Error streaming PCM buffer to AudioTrack", e)
            if (expectedGen == streamGeneration.get()) {
                onAmplitudeChanged(0f)
                isPlayingAudio.set(false)
            }
            false
        }
    }

    private suspend fun writePcmFramesWithLipSync(
        pcmBytes: ByteArray,
        sampleRate: Int,
        expectedGen: Long
    ): Boolean {
        val alignedTotalBytes = pcmBytes.size and 0x7FFFFFFE
        if (alignedTotalBytes <= 0 || isStreamingStopped.get() || expectedGen != streamGeneration.get()) return false

        val track = ensureAudioTrack(sampleRate)
        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            recordFailure("AudioTrack gagal inisialisasi")
            return false
        }
        if (expectedGen != streamGeneration.get()) return false

        if (streamFirstWriteMs == 0L) {
            streamFirstWriteMs = SystemClock.elapsedRealtime()
            streamTotalBytesWritten = 0L
            streamActiveSampleRate = sampleRate
        }

        if (isPlayingAudio.compareAndSet(false, true)) {
            onSpeakingStateChanged(true)
        }
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            track.play()
        }

        // 40ms frame size in bytes: sampleRate * 0.040 sec * 2 bytes/sample (even-aligned)
        val frameBytesSize = ((sampleRate * 40) / 1000 * 2).coerceAtLeast(960) and 0x7FFFFFFE
        var offset = 0
        var wroteAny = false

        while (offset < alignedTotalBytes && !isStreamingStopped.get() && expectedGen == streamGeneration.get()) {
            currentCoroutineContext().ensureActive()
            val end = (offset + frameBytesSize).coerceAtMost(alignedTotalBytes)
            val length = (end - offset) and 0x7FFFFFFE
            if (length <= 0) break

            val rmsAmplitude = calculateNormalizedPcmRms(pcmBytes, offset, length)
            lastWrittenAmplitude = rmsAmplitude
            onAmplitudeChanged(rmsAmplitude)

            val written = track.write(pcmBytes, offset, length, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                recordFailure("AudioTrack write error $written")
                Log.w(TAG, "AudioTrack write returned error code: $written")
                resetAudioTrack()
                break
            }
            wroteAny = true
            streamTotalBytesWritten += written
            offset += length
        }
        return wroteAny
    }

    @Synchronized
    private fun resetAudioTrack() {
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    private suspend fun finishActiveAudioStream(expectedGen: Long) {
        val startedAt = streamFirstWriteMs
        val totalBytes = streamTotalBytesWritten
        val rate = streamActiveSampleRate.coerceAtLeast(8000)

        if (startedAt > 0L && totalBytes > 0L) {
            val expectedDurationMs = (totalBytes * 1000L) / (rate * 2L)
            val elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            val remainingDrainMs = (expectedDurationMs - elapsedMs + 220L).coerceIn(180L, 6500L)
            val initialTailAmp = lastWrittenAmplitude.coerceAtLeast(0.28f)

            var waited = 0L
            while (waited < remainingDrainMs && !isStreamingStopped.get() && expectedGen == streamGeneration.get()) {
                currentCoroutineContext().ensureActive()
                val progress = (waited.toFloat() / remainingDrainMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                onAmplitudeChanged((initialTailAmp * (1f - progress * 0.45f)).coerceAtLeast(0.18f))
                val step = (remainingDrainMs - waited).coerceAtMost(45L)
                delay(step)
                waited += step
            }
        } else {
            delay(180L)
        }

        streamFirstWriteMs = 0L
        streamTotalBytesWritten = 0L
        lastWrittenAmplitude = 0f

        if (expectedGen == streamGeneration.get()) {
            onAmplitudeChanged(0f)
            if (isPlayingAudio.getAndSet(false)) {
                onSpeakingStateChanged(false)
            }
        }
    }

    @Synchronized
    private fun ensureAudioTrack(sampleRate: Int): AudioTrack? {
        val existing = audioTrack
        if (existing != null &&
            existing.state == AudioTrack.STATE_INITIALIZED &&
            currentTrackSampleRate == sampleRate
        ) {
            return existing
        }

        try {
            existing?.release()
        } catch (_: Exception) {}
        audioTrack = null

        currentTrackSampleRate = sampleRate
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4800)

        val candidateSizes = listOf(minBuf * 2, minBuf)
        for (bufSize in candidateSizes) {
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
                Log.w(TAG, "AudioTrack init attempt (buf=$bufSize) failed: ${e.message}")
            }
        }
        return null
    }

    private fun calculateNormalizedPcmRms(
        pcmBytes: ByteArray,
        offset: Int,
        length: Int
    ): Float {
        val sampleCount = length / 2
        if (sampleCount <= 0) return 0f

        var sumSquares = 0.0
        var i = offset
        val limit = offset + (sampleCount * 2)
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

    private fun resolvePrebuiltVoiceName(character: PetCharacter): String {
        return when (character.id) {
            "zayn" -> "Puck"
            "siti" -> "Aoede"
            "rizky" -> "Fenrir"
            "jamal" -> "Charon"
            else -> "Orus"
        }
    }

    private fun resolveBcp47LanguageCode(
        language: AppLanguage,
        overrideLanguageName: String?
    ): String? {
        val overrideLower = overrideLanguageName?.trim()?.lowercase().orEmpty()
        if (overrideLower.isNotBlank()) {
            return when {
                overrideLower.contains("inggris") || overrideLower.contains("english") -> "en-US"
                overrideLower.contains("jepang") || overrideLower.contains("japanese") -> "ja-JP"
                overrideLower.contains("arab") || overrideLower.contains("arabic") -> "ar-XA"
                overrideLower.contains("spanyol") || overrideLower.contains("spanish") -> "es-ES"
                overrideLower.contains("thai") -> "th-TH"
                overrideLower.contains("indonesia") -> "id-ID"
                else -> null
            }
        }
        return when (language) {
            AppLanguage.INDONESIAN -> "id-ID"
            AppLanguage.ENGLISH -> "en-US"
            AppLanguage.JAPANESE -> "ja-JP"
            AppLanguage.SPANISH -> "es-ES"
        }
    }

    private data class EmotionalDirective(
        val emotionTagLabel: String,
        val englishActingCue: String,
        val styledTtsPrompt: String
    )

    private fun buildDynamicEmotionalDirective(
        text: String,
        character: PetCharacter,
        language: AppLanguage,
        emotion: AvatarEmotion,
        overrideLanguageName: String? = null
    ): EmotionalDirective {
        val lower = text.lowercase()
        val hasExclamation = text.contains("!")
        val hasQuestion = text.contains("?")
        val hasEllipsis = text.contains("...") || text.contains("…")
        val isComforting = lower.contains("tenang") || lower.contains("peluk") ||
            lower.contains("semangat") || lower.contains("jangan sedih") || lower.contains("sayang")
        val isLaughing = lower.contains("haha") || lower.contains("hehe") ||
            lower.contains("wkwk") || lower.contains("hihi")
        val isShyOrTsundere = lower.contains("b-bukan") || lower.contains("hmph") ||
            lower.contains("salting") || (character.id == "zayn" && hasEllipsis)
        val isAngryOrFierce = lower.contains("heh!") || lower.contains("awas") ||
            lower.contains("berisik") || lower.contains("kesel")
        val isEmpatheticOrSad = lower.contains("maaf") || lower.contains("sedih") ||
            lower.contains("capek") || lower.contains("lelah")

        val (emotionTagLabel, englishActingCue) = when {
            isComforting || emotion == AvatarEmotion.LOVING ->
                "Nada Hangat & Penuh Kasih 💖" to "warmly, gently, and affectionately"
            isShyOrTsundere ->
                "Nada Salting & Malu-Malu 😳" to "playfully shy and slightly flustered"
            emotion == AvatarEmotion.HAPPY || isLaughing ->
                "Nada Ceria & Antusias ✨" to "cheerfully and energetically"
            isAngryOrFierce ->
                "Nada Ngambek Ekspresif 💢" to "dramatically and comically grumpy"
            emotion == AvatarEmotion.SURPRISED || (hasExclamation && hasQuestion) ->
                "Nada Kaget & Terpana 😲" to "in a surprised and lively tone"
            emotion == AvatarEmotion.SLEEPY ->
                "Nada Ngantuk & Rileks 💤" to "softly, slowly, and sleepily"
            isEmpatheticOrSad ->
                "Nada Empati & Simpati 🥺" to "softly and sympathetically"
            hasQuestion ->
                "Nada Penasaran & Akrab 🎵" to "curiously and warmly"
            hasExclamation ->
                "Nada Bersemangat & Hidup 🔥" to "with lively excitement"
            else ->
                "Nada Natural Ekspresif 🎙️" to "naturally and expressively"
        }

        val langDescriptor = overrideLanguageName?.takeIf { it.isNotBlank() } ?: language.displayName
        // Concise official TTS prompt pattern ("Say <style>: <exact text>")
        val styledTtsPrompt = "Say $englishActingCue in $langDescriptor: $text"

        return EmotionalDirective(
            emotionTagLabel = emotionTagLabel,
            englishActingCue = englishActingCue,
            styledTtsPrompt = styledTtsPrompt
        )
    }

    private fun recordFailure(reason: String) {
        if (reason.isNotBlank()) {
            lastFailureReason = reason
        }
    }

    private fun summarizeFailureReason(raw: String): String {
        if (raw.isBlank()) return ""
        val lower = raw.lowercase()
        return when {
            lower.contains("403") || lower.contains("app check") || lower.contains("appcheck") || lower.contains("attestation") ->
                "Verifikasi App Check (403)"
            lower.contains("429") || lower.contains("quota") || lower.contains("resource_exhausted") ->
                "Kuota API habis (429)"
            lower.contains("404") || lower.contains("not found") ->
                "Model belum tersedia (404)"
            lower.contains("timeout") ->
                "Waktu koneksi habis"
            lower.contains("audiotrack") ->
                "AudioTrack tidak aktif"
            lower.contains("unable to resolve host") || lower.contains("network") || lower.contains("connect") ->
                "Koneksi internet terputus"
            else -> raw.take(42)
        }
    }

    private fun sanitizeTextForSpeech(rawText: String): String {
        return rawText
            .replace(Regex("[*_~`#>]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseSampleRateFromMime(mimeType: String): Int {
        val match = Regex("rate=(\\d+)", RegexOption.IGNORE_CASE).find(mimeType)
        val parsed = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (parsed != null && parsed in 8000..48000) parsed else 24000
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
