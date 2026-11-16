package com.example.audio

import android.content.Context
import android.util.Log
import com.example.model.AppLanguage
import com.example.model.AvatarEmotion
import com.example.model.CharacterCatalog
import com.example.model.PetCharacter
import com.example.model.PetSpecies
import com.example.model.TouchZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Pure Gemini AI Expressive Voice Engine (NO Android TextToSpeech fallback).
 *
 * Pipes Gemini AI's native 24kHz PCM audio output directly from Firebase AI Logic (`Firebase.ai`)
 * to device speakers via `AudioTrack` (`MODE_STREAM`) with real-time RMS lip-sync and
 * dynamic emotional acting directions.
 */
class SpeechTtsEngine(
    private val context: Context,
    private val onSpeakingStateChanged: (Boolean) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit = {},
    private val onEmotionalToneChanged: (String) -> Unit = {}
) {

    private val TAG = "SpeechTtsEngine"
    private var currentLanguage = AppLanguage.INDONESIAN
    private var currentPitch: Float = 1.25f
    private var currentRate: Float = 1.15f

    private val ttsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val speechTurnGen = java.util.concurrent.atomic.AtomicLong(0L)

    // Stream-based Gemini Native Audio Client (Direct PCM to AudioTrack via Firebase AI Logic)
    private val neuralClient = NeuralTtsClient(
        context = context,
        onSpeakingStateChanged = onSpeakingStateChanged,
        onAmplitudeChanged = onAmplitudeChanged,
        onEmotionalToneChanged = onEmotionalToneChanged
    )

    var engineMode: VoiceEngineMode = VoiceEngineMode.GEMINI_FLASH_TTS
    var googleApiKey: String = ""
    var elevenLabsApiKey: String = ""

    fun setVoiceProfile(pitch: Float, rate: Float) {
        currentPitch = pitch
        currentRate = rate
    }

    fun setLanguage(language: AppLanguage) {
        currentLanguage = language
    }

    /**
     * Streams speech exclusively via Gemini AI Native Audio (`Firebase.ai`) with real-time
     * 40ms PCM RMS lip-sync. Never uses local robotic Android `TextToSpeech`.
     */
    fun speak(
        text: String,
        pitch: Float = currentPitch,
        rate: Float = currentRate,
        character: PetCharacter? = null,
        emotion: AvatarEmotion = AvatarEmotion.TALKING,
        overrideLanguageName: String? = null
    ) {
        val myTurn = speechTurnGen.incrementAndGet()
        ttsScope.coroutineContext.cancelChildren()
        neuralClient.stop(notifyIdle = false)
        onAmplitudeChanged(0f)

        val cleanText = sanitizeForSpeech(text)
        if (cleanText.isBlank()) {
            onSpeakingStateChanged(false)
            return
        }

        val targetCharacter = character ?: CharacterCatalog.characters.first()

        ttsScope.launch {
            try {
                val geminiSuccess = neuralClient.speakWithGeminiTts(
                    text = cleanText,
                    apiKey = googleApiKey,
                    character = targetCharacter,
                    language = currentLanguage,
                    emotion = emotion,
                    overrideLanguageName = overrideLanguageName
                )

                if (!geminiSuccess && myTurn == speechTurnGen.get()) {
                    Log.w(TAG, "Gemini AI audio stream did not produce audio for turn $myTurn")
                    onAmplitudeChanged(0f)
                    onSpeakingStateChanged(false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini AI audio playback error: ${e.message}", e)
                if (myTurn == speechTurnGen.get()) {
                    onAmplitudeChanged(0f)
                    onSpeakingStateChanged(false)
                }
            }
        }
    }

    private fun sanitizeForSpeech(rawText: String): String {
        return rawText
            .replace(Regex("[*_~`#>]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun stop() {
        speechTurnGen.incrementAndGet()
        ttsScope.coroutineContext.cancelChildren()
        neuralClient.stop(notifyIdle = false)
        onAmplitudeChanged(0f)
        onSpeakingStateChanged(false)
    }

    /**
     * Zero-latency tactile cartoon sound effects on 3D body touch events.
     */
    fun playTouchSound(species: PetSpecies, zone: TouchZone) {
        ExpressiveAudioSynthesizer.playTouchReactionSound(species, zone)
    }

    fun playHappyChime() {
        ExpressiveAudioSynthesizer.playHeadPetChime()
    }

    fun playPurrEffect() {
        ExpressiveAudioSynthesizer.playTouchReactionSound(PetSpecies.CAT, TouchZone.HEAD)
    }

    fun release() {
        try {
            speechTurnGen.incrementAndGet()
            ttsScope.coroutineContext.cancelChildren()
            neuralClient.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing SpeechTtsEngine", e)
        }
    }
}
