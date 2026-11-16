package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiChatService
import com.example.ai.GeminiLiveSessionClient
import com.example.audio.ContinuousMicWakeDetector
import com.example.audio.SpeechRecognitionHelper
import com.example.audio.SpeechTtsEngine
import com.example.model.AppLanguage
import com.example.model.AvatarCustomization
import com.example.model.AvatarEmotion
import com.example.model.CharacterCatalog
import com.example.model.ChatMessage
import com.example.model.GeminiModelTier
import com.example.model.PetCharacter
import com.example.model.PetSpecies
import com.example.model.ReactionCatalog
import android.util.Log
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException

import com.example.model.BackgroundTheme
import com.example.model.TimeOfDay
import com.example.model.TouchZone
import com.example.model.EarStyle
import com.example.audio.VoiceEngineMode

import com.example.data.CloudSyncRepository
import com.example.data.CloudUserProfile
import com.example.data.MoodJournalRepository
import com.example.data.StoryAdventureRepository
import com.example.model.AccessoryStyle
import com.example.model.CareOverlayType
import com.example.model.CostumeStyle
import com.example.model.DailyCompanionQuest
import com.example.model.FoodItem
import com.example.model.FunActivityTab
import com.example.model.HealthyHabitType
import com.example.model.IdlePetBehavior
import com.example.model.LanguageTutorPrompt
import com.example.model.MemoryFact
import com.example.model.MoodJournalEntry
import com.example.model.MoodType
import com.example.model.PetMemoryManager
import com.example.model.PronunciationEvaluation
import com.example.model.QuizQuestion
import com.example.model.RpsChoice
import com.example.model.RpsResult
import com.example.model.RpsRoundState
import com.example.model.SavedStorySession
import com.example.model.StoryItem
import com.example.model.StoryNode
import com.example.model.StoryTheme
import com.example.model.TutorLanguage
import com.example.model.TutorScenario
import com.example.notifications.DailyCheckInScheduler
import com.example.notifications.ConversationReminderScheduler
import com.example.ui.components.RoomPropAction
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.isActive

enum class FaceToFaceCallState {
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    MUTED
}

enum class VoiceSessionState {
    STANDBY_WAKE,
    WAKE_RESPONDING,
    GEMINI_LIVE_LISTENING,
    GEMINI_LIVE_THINKING,
    GEMINI_LIVE_SPEAKING
}

data class AvatarUiState(
    val activeCharacter: PetCharacter = CharacterCatalog.characters.first(),
    val customization: AvatarCustomization = AvatarCustomization(),
    val messages: List<ChatMessage> = emptyList(),
    val lastAvatarSpeech: String = "",
    val isTalking: Boolean = false,
    val isListeningMic: Boolean = false,
    val showVoiceDialog: Boolean = false,
    val speechTranscript: String = "",
    val speechRms: Float = 0f,
    val speechServiceUnavailable: Boolean = false,
    val triggerSystemSpeechFallback: Boolean = false,
    val currentEmotion: AvatarEmotion = AvatarEmotion.IDLE,
    val affectionScore: Int = 20,
    val selectedLanguage: AppLanguage = AppLanguage.INDONESIAN,
    val voicePitch: Float = 1.25f,
    val speechRate: Float = 1.15f,
    val voiceEngineMode: VoiceEngineMode = VoiceEngineMode.GEMINI_FLASH_TTS,
    val isLoadingAi: Boolean = false,
    val backgroundTheme: BackgroundTheme = BackgroundTheme.LIVING_ROOM,
    val currentTimeOfDay: TimeOfDay = TimeOfDay.fromCurrentHour(),
    // Face-to-Face live hands-free conversation state
    val isFaceToFaceActive: Boolean = false,
    val faceToFaceCallState: FaceToFaceCallState = FaceToFaceCallState.CONNECTING,
    val isFaceToFaceMuted: Boolean = false,
    val liveUserSpeech: String = "",
    val liveAvatarSpeech: String = "",
    val callDurationSeconds: Int = 0,
    // Talking Tom Living Care System & Leveling
    val playerLevel: Int = 12,
    val levelProgress: Float = 0.65f, // Circular progress for LVL 12 badge
    val coins: Int = 1500, // Matches 1,500 coins capsule in screenshot
    val diamonds: Int = 240, // Matches 240 diamonds capsule in screenshot
    val hungerLevel: Float = 0.85f, // "Makan" 85%
    val toiletLevel: Float = 0.70f, // "Toilet" 70%
    val energyLevel: Float = 0.60f, // "Tidur" 60%
    val funLevel: Float = 0.90f, // "Bermain/Hiburan" 90%
    val activeCareOverlay: CareOverlayType = CareOverlayType.NONE,
    val isMimicModeActive: Boolean = false,
    val miniGameScore: Int = 0,
    val careFeedbackMessage: String = "",
    // Mode Ngerjain Tugas Sekolah
    val showHomeworkMode: Boolean = false,
    val homeworkSubject: String = "Matematika",
    val homeworkQuestion: String = "",
    val homeworkSolution: HomeworkSolution? = null,
    val isSolvingHomework: Boolean = false,
    val homeworkPhotoBitmap: android.graphics.Bitmap? = null,
    // Speak to Wake ("Petto") Continuous Hands-Free Listener via Microphone API & Gemini Live
    val isHandsFreeWakeEnabled: Boolean = true,
    val standbyRequireWakeWord: Boolean = true,
    val micSensitivityHigh: Boolean = false,
    val allowBargeInWhileAiSpeaking: Boolean = true,
    val isWakeCommandListening: Boolean = false,
    val voiceSessionState: VoiceSessionState = VoiceSessionState.STANDBY_WAKE,
    val isUserSpeakingNow: Boolean = false,
    // Autonomous 3D Idle Behavior System when not in conversation
    val activeIdleBehavior: IdlePetBehavior = IdlePetBehavior.NONE,
    // 1. Long-Term Character Memory & User Profile
    val userNickname: String = "",
    val memories: List<MemoryFact> = emptyList(),
    // 2. Live Camera Vision during Video Call
    val liveCameraBitmap: android.graphics.Bitmap? = null,
    // 6. Interactive Room & 3D Props state
    val isLofiRadioPlaying: Boolean = false,
    val isNightLampDimmed: Boolean = false,
    // Fun Activities Hub (Mini-Game, Interactive Storytelling, Healthy Routines)
    val showFunActivitiesDialog: Boolean = false,
    val activeFunTab: FunActivityTab = FunActivityTab.MINI_GAME,
    val rpsRound: RpsRoundState? = null,
    val rpsWins: Int = 0,
    val rpsDraws: Int = 0,
    val rpsLosses: Int = 0,
    val currentQuizQuestion: QuizQuestion? = null,
    val selectedQuizOption: Int? = null,
    val quizScore: Int = 0,
    val isGeneratingQuiz: Boolean = false,
    val selectedStoryTheme: StoryTheme = StoryTheme.CRYSTAL_FOREST,
    val currentStoryNode: StoryNode? = null,
    val storyContextHistory: String = "",
    val isGeneratingStory: Boolean = false,
    val showStoryStageMode: Boolean = false,
    val activeStorySessionId: String = "",
    val storyInventory: List<StoryItem> = emptyList(),
    val storyHistoryLog: List<String> = emptyList(),
    val savedStorySessions: List<SavedStorySession> = emptyList(),
    val waterGlassesToday: Int = 0,
    val stretchCountToday: Int = 0,
    // Gemini Multi-Turn Model Tier & Gemini 3.8 Live Real-Time Session state
    val selectedModelTier: GeminiModelTier = GeminiModelTier.AUTO,
    val isGeminiLiveConnected: Boolean = false,
    val activeLiveModelName: String = "gemini-3.1-flash-live-preview",
    // Mood Journal & Daily Companion Quests state
    val selectedMood: MoodType = MoodType.HAPPY,
    val moodJournalNote: String = "",
    val moodJournalEntries: List<MoodJournalEntry> = emptyList(),
    val isGeneratingMoodReply: Boolean = false,
    // Real-time stream-based PCM speaker amplitude (0f..1f) & dynamic emotional tone tag
    val voiceAmplitude: Float = 0f,
    val activeEmotionalTone: String = "",
    // Shop & Economy Unlocks
    val unlockedCostumes: Set<String> = setOf("none"),
    val unlockedAccessories: Set<String> = setOf("none", "bell_collar"),
    val unlockedThemes: Set<String> = setOf("living_room", "bedroom", "kitchen"),
    val shopFeedbackMessage: String = "",
    // Language Tutor & Pronunciation Coach (5 languages)
    val tutorLanguage: TutorLanguage = TutorLanguage.ENGLISH,
    val tutorScenario: TutorScenario = TutorScenario.GREETING,
    val tutorPrompt: LanguageTutorPrompt? = null,
    val pronunciationEvaluation: PronunciationEvaluation? = null,
    val isGeneratingTutor: Boolean = false,
    val isRecordingPronunciation: Boolean = false,
    val isEvaluatingPronunciation: Boolean = false,
    // Optional Google Sign-In & Cloud Sync + Scheduled Daily Reminders
    val signedInEmail: String? = null,
    val signedInDisplayName: String? = null,
    val isCloudSyncing: Boolean = false,
    val cloudSyncStatus: String = "",
    val dailyRemindersEnabled: Boolean = false,
    val conversationRemindersEnabled: Boolean = false,
    val conversationReminders: List<ConversationReminderScheduler.Reminder> = emptyList()
)

data class HomeworkSolution(
    val subject: String,
    val questionSummary: String,
    val stepByStep: List<String>,
    val finalAnswer: String,
    val tutorTip: String,
    val conceptOrFormula: String = "",
    val practiceChallenge: String = "",
    val isFromAi: Boolean = true
)

class AvatarViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AvatarUiState())
    val uiState: StateFlow<AvatarUiState> = _uiState.asStateFlow()

    private val aiService = GeminiChatService(application)
    private val memoryManager = PetMemoryManager(application)
    private val storyRepository = StoryAdventureRepository(application)
    private val moodJournalRepository = MoodJournalRepository(application)
    private var emotionResetJob: Job? = null
    private var callTimerJob: Job? = null
    private var autoListenJob: Job? = null
    private var wakeListenJob: Job? = null
    private var audioAnalysisJob: Job? = null
    private var idleBehaviorJob: Job? = null
    private var liveTurnTimeoutJob: Job? = null
    private var quizGenerationJob: Job? = null
    private var storyGenerationJob: Job? = null
    private var lastIdleBehavior: IdlePetBehavior = IdlePetBehavior.NONE
    @Volatile
    private var rapidSpeechErrorCount: Int = 0
    @Volatile
    private var isSpeakingWakeAck: Boolean = false
    @Volatile
    private var isSpeakingVoiceReply: Boolean = false
    @Volatile
    private var isHandlingBargeIn: Boolean = false
    @Volatile
    private var isLivePlaybackActive: Boolean = false
    @Volatile
    private var isTtsPlaybackActive: Boolean = false
    @Volatile
    private var isSynthesizingSpeech: Boolean = false
    private val voiceTurnSeq = java.util.concurrent.atomic.AtomicLong(0L)
    private val speechPlaybackTurnSeq = java.util.concurrent.atomic.AtomicLong(0L)
    private val quizReqSeq = java.util.concurrent.atomic.AtomicLong(0L)
    private val storyReqSeq = java.util.concurrent.atomic.AtomicLong(0L)

    private val cloudSyncRepository by lazy { CloudSyncRepository(application) }

    private fun currentSignedInUserOrNull() = try {
        Firebase.auth.currentUser
    } catch (_: Exception) {
        null
    }

    private fun todayDateKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    private fun ensureHealthyHabitsDateCurrent() {
        val today = todayDateKey()
        val storedDate = prefs.getString("healthy_habits_date_v1", "") ?: ""
        if (storedDate != today) {
            prefs.edit()
                .putString("healthy_habits_date_v1", today)
                .putInt("water_glasses_today", 0)
                .putInt("stretch_count_today", 0)
                .apply()
            _uiState.update { cur ->
                cur.copy(
                    waterGlassesToday = 0,
                    stretchCountToday = 0
                )
            }
        }
    }

    private fun loadSavedMessages(): List<ChatMessage> {
        val raw = prefs.getString("saved_chat_history_v1", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val text = obj.optString("text", "")
                if (text.isBlank()) continue
                val emotionName = obj.optString("emotion", AvatarEmotion.TALKING.name)
                val emotion = AvatarEmotion.entries.find { it.name == emotionName } ?: AvatarEmotion.TALKING
                list.add(
                    ChatMessage(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        senderName = obj.optString("senderName", "Sahabat"),
                        text = text,
                        isUser = obj.optBoolean("isUser", false),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        emotion = emotion,
                        modelUsed = obj.optString("modelUsed", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistMessages(messages: List<ChatMessage>) {
        try {
            val arr = org.json.JSONArray()
            messages.takeLast(60).forEach { msg ->
                arr.put(
                    org.json.JSONObject().apply {
                        put("id", msg.id)
                        put("senderName", msg.senderName)
                        put("text", msg.text)
                        put("isUser", msg.isUser)
                        put("timestamp", msg.timestamp)
                        put("emotion", msg.emotion.name)
                        put("modelUsed", msg.modelUsed)
                    }
                )
            }
            prefs.edit().putString("saved_chat_history_v1", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private val micWakeDetector = ContinuousMicWakeDetector(
        context = application,
        onRmsChanged = { rms ->
            _uiState.update { it.copy(speechRms = rms) }
        },
        onSpeechActivityChanged = { isSpeaking ->
            _uiState.update { current ->
                // Ignore speech-start indicator while AI is waking, thinking, synthesizing, or speaking
                val aiBusyOrAboutToSpeak = isSynthesizingSpeech ||
                    isTtsPlaybackActive ||
                    isLivePlaybackActive ||
                    current.isTalking ||
                    current.isLoadingAi ||
                    current.voiceSessionState == VoiceSessionState.GEMINI_LIVE_THINKING ||
                    current.voiceSessionState == VoiceSessionState.GEMINI_LIVE_SPEAKING ||
                    current.voiceSessionState == VoiceSessionState.WAKE_RESPONDING
                if (isSpeaking && !current.isFaceToFaceActive && !current.showVoiceDialog && !current.isRecordingPronunciation &&
                    aiBusyOrAboutToSpeak
                ) {
                    current
                } else {
                    current.copy(
                        isUserSpeakingNow = isSpeaking,
                        activeIdleBehavior = if (isSpeaking) IdlePetBehavior.NONE else current.activeIdleBehavior,
                        speechTranscript = if (isSpeaking && current.showVoiceDialog && current.speechTranscript.isBlank()) {
                            "🎙️ Mendengarkan ucapanmu..."
                        } else {
                            current.speechTranscript
                        },
                        liveUserSpeech = if (isSpeaking && current.isFaceToFaceActive) {
                            "🎙️ Mendengarkan ucapanmu..."
                        } else {
                            current.liveUserSpeech
                        }
                    )
                }
            }
        },
        onVoiceSegmentCaptured = { wavBytes, durationMs, _ ->
            handleCapturedMicAudioSegment(wavBytes, durationMs)
        },
        onLiveSessionSilenceTimeout = {
            // Automatically return to passive standby after silence
            _uiState.update { current ->
                if (!current.showVoiceDialog && !current.isFaceToFaceActive && !current.isRecordingPronunciation) {
                    current.copy(
                        voiceSessionState = VoiceSessionState.STANDBY_WAKE,
                        isWakeCommandListening = false,
                        isUserSpeakingNow = false,
                        currentEmotion = if (current.isTalking) current.currentEmotion else AvatarEmotion.IDLE
                    )
                } else {
                    current
                }
            }
        },
        onBargeInTriggered = {
            if (_uiState.value.allowBargeInWhileAiSpeaking &&
                !isSynthesizingSpeech &&
                !isHandlingBargeIn &&
                (_uiState.value.isTalking || isLivePlaybackActive || isTtsPlaybackActive)
            ) {
                isHandlingBargeIn = true
                stopSpeechPlaybackAndListen()
            }
        }
    )

    private val geminiLiveClient = GeminiLiveSessionClient(
        context = application,
        onConnectionStateChanged = { connected, modelName ->
            _uiState.update {
                it.copy(
                    isGeminiLiveConnected = connected,
                    activeLiveModelName = modelName.ifBlank { "gemini-3.1-flash-live-preview" }
                )
            }
        },
        onSpeakingStateChanged = { isSpeaking ->
            if (isSpeaking) {
                liveTurnTimeoutJob?.cancel()
            }
            val turnAtCallback = speechPlaybackTurnSeq.get()
            isLivePlaybackActive = isSpeaking
            val effectiveTalking = isLivePlaybackActive || isTtsPlaybackActive
            _uiState.update { cur ->
                cur.copy(
                    isTalking = effectiveTalking,
                    isUserSpeakingNow = if (effectiveTalking) false else cur.isUserSpeakingNow,
                    activeIdleBehavior = if (effectiveTalking) IdlePetBehavior.NONE else cur.activeIdleBehavior,
                    currentEmotion = if (effectiveTalking) AvatarEmotion.TALKING else AvatarEmotion.IDLE,
                    faceToFaceCallState = if (cur.isFaceToFaceActive && effectiveTalking) {
                        FaceToFaceCallState.SPEAKING
                    } else {
                        cur.faceToFaceCallState
                    }
                )
            }
            micWakeDetector.setAiSpeaking(effectiveTalking)
            val sNow = _uiState.value
            val canUnpauseMic = (sNow.isHandsFreeWakeEnabled || sNow.isFaceToFaceActive) &&
                !(sNow.isFaceToFaceActive && sNow.isFaceToFaceMuted) &&
                !sNow.isRecordingPronunciation &&
                !sNow.showVoiceDialog &&
                (sNow.allowBargeInWhileAiSpeaking || !effectiveTalking)
            if (effectiveTalking && canUnpauseMic && !isSynthesizingSpeech) {
                micWakeDetector.setPaused(false)
            } else if (effectiveTalking) {
                micWakeDetector.setPaused(true)
            }
            if (!effectiveTalking) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    if (turnAtCallback != speechPlaybackTurnSeq.get() ||
                        isSynthesizingSpeech || isTtsPlaybackActive || isLivePlaybackActive
                    ) {
                        return@launch
                    }
                    val cur = _uiState.value
                    if (cur.isFaceToFaceActive && !cur.isFaceToFaceMuted) {
                        onAvatarFinishedSpeakingInCall()
                    } else if (cur.isHandsFreeWakeEnabled && !cur.showVoiceDialog && !cur.isFaceToFaceActive) {
                        enterGeminiLiveListeningAfterSpeech(delayMs = 280L)
                    }
                }
            }
        },
        onLiveTranscriptUpdate = { partialText ->
            if (partialText.isNotBlank()) {
                liveTurnTimeoutJob?.cancel()
                _uiState.update {
                    it.copy(
                        lastAvatarSpeech = partialText,
                        liveAvatarSpeech = partialText
                    )
                }
            }
        },
        onTurnCompleted = { fullText ->
            liveTurnTimeoutJob?.cancel()
            if (fullText.isNotBlank() && _uiState.value.isFaceToFaceActive) {
                _uiState.update { state ->
                    val liveMsg = ChatMessage(
                        senderName = state.activeCharacter.name,
                        text = fullText,
                        isUser = false,
                        emotion = AvatarEmotion.TALKING,
                        modelUsed = state.activeLiveModelName.ifBlank { "gemini-3.1-flash-live-preview" }
                    )
                    val updated = state.messages + liveMsg
                    persistMessages(updated)
                    state.copy(
                        messages = updated,
                        lastAvatarSpeech = fullText,
                        liveAvatarSpeech = fullText,
                        isLoadingAi = false
                    )
                }
            }
        },
        onAmplitudeChanged = { amplitude ->
            micWakeDetector.updateAiPlaybackAmplitude(amplitude)
            _uiState.update { it.copy(voiceAmplitude = amplitude) }
        }
    )

    private val ttsEngine = SpeechTtsEngine(
        context = application,
        onSpeakingStateChanged = { isSpeaking ->
            val turnAtCallback = speechPlaybackTurnSeq.get()
            isTtsPlaybackActive = isSpeaking
            if (isSpeaking) {
                isSynthesizingSpeech = false
            }
            val effectiveTalking = isLivePlaybackActive || isTtsPlaybackActive
            _uiState.update { cur ->
                cur.copy(
                    isTalking = effectiveTalking,
                    isUserSpeakingNow = if (effectiveTalking) false else cur.isUserSpeakingNow,
                    voiceAmplitude = if (effectiveTalking) cur.voiceAmplitude else 0f,
                    activeIdleBehavior = if (effectiveTalking) IdlePetBehavior.NONE else cur.activeIdleBehavior,
                    currentEmotion = if (effectiveTalking) {
                        if (cur.currentEmotion == AvatarEmotion.IDLE) AvatarEmotion.TALKING else cur.currentEmotion
                    } else {
                        AvatarEmotion.IDLE
                    }
                )
            }
            // Notify Echo-Envelope Smart Barge-In AFTER UI state reflects talking
            micWakeDetector.setAiSpeaking(effectiveTalking)
            val sNow = _uiState.value
            val canUnpauseMic = (sNow.isHandsFreeWakeEnabled || sNow.isFaceToFaceActive) &&
                !(sNow.isFaceToFaceActive && sNow.isFaceToFaceMuted) &&
                !sNow.isRecordingPronunciation &&
                !sNow.showVoiceDialog &&
                (sNow.allowBargeInWhileAiSpeaking || !effectiveTalking)
            if (effectiveTalking && canUnpauseMic) {
                micWakeDetector.setPaused(false)
            } else if (effectiveTalking) {
                micWakeDetector.setPaused(true)
            }
            if (!effectiveTalking) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    if (turnAtCallback != speechPlaybackTurnSeq.get()) {
                        return@launch
                    }
                    isSynthesizingSpeech = false
                    if (isHandlingBargeIn) {
                        isHandlingBargeIn = false
                        return@launch
                    }
                    if (isTtsPlaybackActive || isLivePlaybackActive) {
                        return@launch
                    }
                    val cur = _uiState.value
                    if (cur.isFaceToFaceActive && !cur.isFaceToFaceMuted) {
                        onAvatarFinishedSpeakingInCall()
                    } else if (isSpeakingWakeAck) {
                        isSpeakingWakeAck = false
                        enterGeminiLiveListeningAfterSpeech(delayMs = 240L)
                    } else if (isSpeakingVoiceReply) {
                        isSpeakingVoiceReply = false
                        // Stay in active Gemini Live session for follow-up until silence timeout
                        enterGeminiLiveListeningAfterSpeech(delayMs = 280L)
                    } else if (cur.isHandsFreeWakeEnabled &&
                        !cur.showVoiceDialog &&
                        !cur.isFaceToFaceActive &&
                        !cur.isUserSpeakingNow &&
                        cur.voiceSessionState != VoiceSessionState.WAKE_RESPONDING
                    ) {
                        resumeContinuousWakeStandby(delayMs = 300L)
                    }
                }
            }
        },
        onAmplitudeChanged = { amplitude ->
            micWakeDetector.updateAiPlaybackAmplitude(amplitude)
            _uiState.update { it.copy(voiceAmplitude = amplitude) }
        },
        onEmotionalToneChanged = { toneLabel ->
            _uiState.update { it.copy(activeEmotionalTone = toneLabel) }
        }
    )

    private val speechRecognitionHelper: SpeechRecognitionHelper = SpeechRecognitionHelper(
        context = application,
        onResult = { recognizedText ->
            rapidSpeechErrorCount = 0
            val clean = recognizedText.trim()
            if (clean.isNotBlank()) {
                if (_uiState.value.isFaceToFaceActive) {
                    handleFaceToFaceUserUtterance(clean)
                    return@SpeechRecognitionHelper
                }

                if (_uiState.value.showVoiceDialog) {
                    _uiState.update {
                        it.copy(
                            speechTranscript = clean,
                            isListeningMic = false
                        )
                    }
                    return@SpeechRecognitionHelper
                }

                // Check if user said wake phrase ("Hey Petto", "Halo Petto", character name, etc.)
                val (isWake, commandAfterWake) = parseWakePhrase(clean, _uiState.value.activeCharacter.name)
                if (isWake) {
                    if (commandAfterWake.isNotBlank()) {
                        // User said "Hey Petto <pertanyaan>" in one breath -> answer directly with voice!
                        handleDirectVoiceUtterance(commandAfterWake)
                    } else {
                        // User called "Hey Petto" -> character immediately answers ("nyaut") and listens!
                        callHeyPetto()
                    }
                    return@SpeechRecognitionHelper
                }

                // If character was actively listening after wake or user spoke directly in room
                if (_uiState.value.isWakeCommandListening || _uiState.value.isHandsFreeWakeEnabled) {
                    handleDirectVoiceUtterance(clean)
                    return@SpeechRecognitionHelper
                }
            }
            scheduleHandsFreeWakeRestart(700L)
        },
        onPartialResult = { partial ->
            if (partial.isNotBlank()) {
                rapidSpeechErrorCount = 0
                _uiState.update { cur ->
                    when {
                        cur.isFaceToFaceActive -> cur.copy(liveUserSpeech = partial)
                        cur.showVoiceDialog -> cur.copy(speechTranscript = partial)
                        else -> cur
                    }
                }
            }
        },
        onRmsChanged = { rms ->
            _uiState.update { it.copy(speechRms = rms) }
        },
        onListeningStateChanged = { isListening ->
            _uiState.update { cur ->
                val updatedCallState = if (cur.isFaceToFaceActive && !cur.isFaceToFaceMuted && isListening &&
                    cur.faceToFaceCallState != FaceToFaceCallState.THINKING &&
                    cur.faceToFaceCallState != FaceToFaceCallState.SPEAKING
                ) {
                    FaceToFaceCallState.LISTENING
                } else {
                    cur.faceToFaceCallState
                }
                cur.copy(
                    isListeningMic = isListening,
                    faceToFaceCallState = updatedCallState
                )
            }
        },
        onServiceUnavailable = {
            // Do NOT disable ContinuousMicWakeDetector (AudioRecord + Gemini) just because Android legacy SpeechRecognizer is unavailable
            _uiState.update {
                it.copy(
                    speechServiceUnavailable = true,
                    triggerSystemSpeechFallback = it.showVoiceDialog,
                    isListeningMic = false
                )
            }
        },
        onErrorCallback = { errorCode, durationMs ->
            val isSilenceTimeout = errorCode == android.speech.SpeechRecognizer.ERROR_NO_MATCH ||
                errorCode == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT

            if (isSilenceTimeout && durationMs >= 500L) {
                rapidSpeechErrorCount = 0
                if (_uiState.value.isWakeCommandListening) {
                    _uiState.update { it.copy(isWakeCommandListening = false) }
                }
                onSpeechErrorInCall(errorCode)
                scheduleHandsFreeWakeRestart(900L)
                return@SpeechRecognitionHelper
            }

            if (durationMs < 500L) {
                rapidSpeechErrorCount++
            }
            val isFatalError = errorCode == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                errorCode == android.speech.SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                errorCode == android.speech.SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE

            if (isFatalError || rapidSpeechErrorCount >= 4) {
                _uiState.update {
                    it.copy(
                        speechServiceUnavailable = true,
                        isListeningMic = false
                    )
                }
            } else {
                onSpeechErrorInCall(errorCode)
                scheduleHandsFreeWakeRestart(1800L)
            }
        }
    )

    private fun parseWakePhrase(rawText: String, charName: String): Pair<Boolean, String> {
        val trimmed = rawText.trim()
        val cName = charName.lowercase()
        val wakeRegex = Regex(
            pattern = """^(?:hey|hei|halo|hai|hi|oi|woi|ya)?\s*(?:petto|peto|pito|$cName)\b[,!.?\s]*(.*)$""",
            option = RegexOption.IGNORE_CASE
        )
        val match = wakeRegex.find(trimmed)
        if (match != null) {
            val remainder = match.groupValues.getOrNull(1)?.trim().orEmpty()
            return true to remainder
        }
        val lower = trimmed.lowercase()
        val prefixes = listOf("hey petto", "hei petto", "halo petto", "hai petto", "hey $cName", "halo $cName", "hai $cName")
        for (prefix in prefixes) {
            val idx = lower.indexOf(prefix)
            if (idx >= 0) {
                val remainder = trimmed.substring(idx + prefix.length).trimStart(',', '.', '!', '?', ' ')
                return true to remainder
            }
        }
        return false to trimmed
    }

    private fun enterGeminiLiveListeningAfterSpeech(delayMs: Long = 250L) {
        wakeListenJob?.cancel()
        if (_uiState.value.isFaceToFaceActive || _uiState.value.showVoiceDialog) return
        wakeListenJob = viewModelScope.launch {
            delay(delayMs)
            val s = _uiState.value
            if (!s.isTalking && !isTtsPlaybackActive && !isLivePlaybackActive &&
                !isSynthesizingSpeech && !s.isLoadingAi && !s.isFaceToFaceActive && !s.showVoiceDialog
            ) {
                _uiState.update {
                    it.copy(
                        voiceSessionState = VoiceSessionState.GEMINI_LIVE_LISTENING,
                        isWakeCommandListening = true
                    )
                }
                micWakeDetector.setLiveSessionMode(true)
                micWakeDetector.setPaused(false)
                micWakeDetector.start()
            }
        }
    }

    private fun resumeContinuousWakeStandby(delayMs: Long = 300L) {
        wakeListenJob?.cancel()
        val nowState = _uiState.value
        if (!nowState.isHandsFreeWakeEnabled ||
            nowState.isFaceToFaceActive ||
            nowState.isTalking ||
            isTtsPlaybackActive ||
            isLivePlaybackActive ||
            isSynthesizingSpeech ||
            nowState.isLoadingAi ||
            isSpeakingWakeAck ||
            isSpeakingVoiceReply ||
            nowState.showVoiceDialog
        ) {
            return
        }
        wakeListenJob = viewModelScope.launch {
            delay(delayMs)
            val s = _uiState.value
            if (!s.isTalking && !isTtsPlaybackActive && !isLivePlaybackActive &&
                !isSynthesizingSpeech && !s.isLoadingAi && !s.isFaceToFaceActive && !s.showVoiceDialog
            ) {
                _uiState.update {
                    it.copy(
                        voiceSessionState = VoiceSessionState.STANDBY_WAKE,
                        isWakeCommandListening = false
                    )
                }
                micWakeDetector.setLiveSessionMode(false)
                micWakeDetector.setPaused(false)
                micWakeDetector.start()
            }
        }
    }

    /**
     * Immediately interrupts active AI character speech when Smart Barge-In triggers
     * so the user can interrupt mid-sentence just like Google Gemini Live.
     */
    private fun stopSpeechPlaybackAndListen() {
        speechPlaybackTurnSeq.incrementAndGet()
        isHandlingBargeIn = true
        isSynthesizingSpeech = false
        micWakeDetector.setAiSpeaking(false)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            voiceTurnSeq.incrementAndGet()
            wakeListenJob?.cancel()
            audioAnalysisJob?.cancel()
            isSpeakingWakeAck = false
            isSpeakingVoiceReply = false
            ttsEngine.stop()
            geminiLiveClient.interruptPlayback()
            isHandlingBargeIn = false

            val s = _uiState.value
            micWakeDetector.setLiveSessionMode(true)
            micWakeDetector.setAiSpeaking(false)
            if (!(s.isFaceToFaceActive && s.isFaceToFaceMuted)) {
                micWakeDetector.setPaused(false)
            }

            _uiState.update { cur ->
                cur.copy(
                    isTalking = false,
                    isLoadingAi = false,
                    voiceAmplitude = 0f,
                    isUserSpeakingNow = true,
                    voiceSessionState = VoiceSessionState.GEMINI_LIVE_LISTENING,
                    isWakeCommandListening = !cur.isFaceToFaceActive,
                    faceToFaceCallState = if (cur.isFaceToFaceActive && !cur.isFaceToFaceMuted) {
                        FaceToFaceCallState.LISTENING
                    } else {
                        cur.faceToFaceCallState
                    }
                )
            }
        }
    }

    private fun handleCapturedMicAudioSegment(wavBytes: ByteArray, durationMs: Long) {
        val snapshot = _uiState.value

        // 0. Language Tutor Pronunciation Coach Evaluation
        if (snapshot.isRecordingPronunciation) {
            micWakeDetector.setAiSpeaking(false)
            micWakeDetector.setPaused(true)
            audioAnalysisJob?.cancel()
            audioAnalysisJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                _uiState.update {
                    it.copy(
                        isRecordingPronunciation = false,
                        isEvaluatingPronunciation = true,
                        isUserSpeakingNow = false
                    )
                }
                try {
                    val targetPhrase = snapshot.tutorPrompt?.targetPhrase ?: snapshot.tutorLanguage.displayName
                    val eval = aiService.evaluatePronunciationAudio(
                        wavBytes = wavBytes,
                        targetPhrase = targetPhrase,
                        language = snapshot.tutorLanguage,
                        scenario = snapshot.tutorScenario,
                        character = snapshot.activeCharacter
                    )
                    _uiState.update {
                        it.copy(
                            isEvaluatingPronunciation = false,
                            pronunciationEvaluation = eval
                        )
                    }
                    if (eval.isFromAi && eval.score > 0) {
                        val earnedCoins = (eval.score / 5).coerceAtLeast(10)
                        val earnedDiamonds = if (eval.score >= 85) 2 else 0
                        grantReward(coinsDelta = earnedCoins, diamondsDelta = earnedDiamonds, xpProgress = 0.08f)
                    }
                    speakVoiceOnly(eval.feedback, if (eval.score >= 75) AvatarEmotion.HAPPY else AvatarEmotion.TALKING)
                } catch (e: Exception) {
                    Log.w("AvatarViewModel", "Pronunciation eval error: ${e.message}")
                    _uiState.update { it.copy(isEvaluatingPronunciation = false) }
                }
            }
            return
        }

        if (_uiState.value.isTalking || isTtsPlaybackActive || isLivePlaybackActive || isSynthesizingSpeech || isSpeakingWakeAck || isSpeakingVoiceReply) {
            return
        }

        // 1. Voice Input Sheet (Google Gemini Live Voice Dialog)
        if (snapshot.showVoiceDialog) {
            val mySeq = voiceTurnSeq.incrementAndGet()
            micWakeDetector.setAiSpeaking(false)
            micWakeDetector.setPaused(true)
            audioAnalysisJob?.cancel()
            audioAnalysisJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                _uiState.update {
                    it.copy(
                        isListeningMic = false,
                        isUserSpeakingNow = false,
                        isLoadingAi = true,
                        speechTranscript = "✨ Gemini AI sedang memahami suaramu..."
                    )
                }
                try {
                    val state = _uiState.value
                    val wasStoryStage = state.showStoryStageMode && state.currentStoryNode != null
                    val wasHomework = state.showHomeworkMode

                    if (wasHomework || wasStoryStage) {
                        val transcript = aiService.transcribeAudioOnly(wavBytes, state.selectedLanguage)
                        if (mySeq != voiceTurnSeq.get() || !_uiState.value.showVoiceDialog) return@launch
                        if (transcript.isNotBlank()) {
                            _uiState.update {
                                it.copy(
                                    speechTranscript = transcript,
                                    isLoadingAi = false,
                                    showVoiceDialog = false
                                )
                            }
                            if (wasStoryStage) {
                                chooseStoryBranch(transcript)
                            } else {
                                setHomeworkQuestion(transcript)
                                solveHomework(transcript)
                            }
                        } else {
                            // Resume listening if audio was empty
                            _uiState.update {
                                it.copy(
                                    isListeningMic = true,
                                    isLoadingAi = false,
                                    speechTranscript = ""
                                )
                            }
                            micWakeDetector.setPaused(false)
                            micWakeDetector.start()
                        }
                    } else {
                        val result = aiService.analyzeVoiceAudioSegment(
                            wavBytes = wavBytes,
                            durationMs = durationMs,
                            isStandbyWakeMode = false,
                            requireWakeWordInStandby = false,
                            character = state.activeCharacter,
                            language = state.selectedLanguage,
                            history = state.messages
                        )
                        if (mySeq != voiceTurnSeq.get() || !_uiState.value.showVoiceDialog) return@launch
                        if (result.wakeDetected && (result.replyText.isNotBlank() || result.isWakeOnly)) {
                            _uiState.update {
                                it.copy(
                                    speechTranscript = result.transcript,
                                    showVoiceDialog = false,
                                    isListeningMic = false,
                                    isLoadingAi = false
                                )
                            }
                            if (result.isWakeOnly || result.replyText.isBlank()) {
                                callHeyPetto()
                            } else {
                                deliverGeminiLiveVoiceReply(
                                    userTranscript = result.transcript.ifBlank { "Halo ${state.activeCharacter.name}" },
                                    replyText = result.replyText,
                                    emotion = result.emotion
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isListeningMic = true,
                                    isLoadingAi = false,
                                    speechTranscript = ""
                                )
                            }
                            micWakeDetector.setPaused(false)
                            micWakeDetector.start()
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("AvatarViewModel", "Voice dialog audio analysis error: ${e.message}")
                    if (mySeq == voiceTurnSeq.get() && _uiState.value.showVoiceDialog) {
                        _uiState.update {
                            it.copy(
                                isListeningMic = true,
                                isLoadingAi = false,
                                speechTranscript = ""
                            )
                        }
                        micWakeDetector.setPaused(false)
                        micWakeDetector.start()
                    }
                }
            }
            return
        }

        // 2. Face-to-Face Live Video Call
        if (snapshot.isFaceToFaceActive) {
            if (snapshot.isFaceToFaceMuted) return
            val mySeq = voiceTurnSeq.incrementAndGet()
            micWakeDetector.setAiSpeaking(false)
            micWakeDetector.setPaused(true)
            audioAnalysisJob?.cancel()
            audioAnalysisJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                _uiState.update {
                    it.copy(
                        faceToFaceCallState = FaceToFaceCallState.THINKING,
                        isListeningMic = false,
                        isUserSpeakingNow = false,
                        liveUserSpeech = "✨ Gemini AI sedang memproses suaramu..."
                    )
                }
                try {
                    val state = _uiState.value
                    val result = aiService.analyzeVoiceAudioSegment(
                        wavBytes = wavBytes,
                        durationMs = durationMs,
                        isStandbyWakeMode = false,
                        requireWakeWordInStandby = false,
                        character = state.activeCharacter,
                        language = state.selectedLanguage,
                        history = state.messages,
                        cameraBitmap = state.liveCameraBitmap
                    )
                    if (mySeq != voiceTurnSeq.get() || !_uiState.value.isFaceToFaceActive) return@launch
                    if (result.wakeDetected && result.replyText.isNotBlank()) {
                        val userTranscript = result.transcript.ifBlank { "Halo ${state.activeCharacter.name}" }
                        captureConversationReminder(userTranscript)
                        val userMsg = ChatMessage(senderName = "Kamu", text = userTranscript, isUser = true)
                        val avatarMsg = ChatMessage(
                            senderName = state.activeCharacter.name,
                            text = result.replyText,
                            isUser = false,
                            emotion = result.emotion,
                            modelUsed = "gemini-3.8-live"
                        )
                        speechPlaybackTurnSeq.incrementAndGet()
                        isHandlingBargeIn = false
                        isSynthesizingSpeech = true
                        micWakeDetector.setAiSpeaking(false)
                        micWakeDetector.setPaused(true)
                        _uiState.update { cur ->
                            val updatedMessages = cur.messages + userMsg + avatarMsg
                            persistMessages(updatedMessages)
                            cur.copy(
                                messages = updatedMessages,
                                liveUserSpeech = userTranscript,
                                liveAvatarSpeech = result.replyText,
                                lastAvatarSpeech = result.replyText,
                                currentEmotion = result.emotion,
                                faceToFaceCallState = FaceToFaceCallState.SPEAKING,
                                isUserSpeakingNow = false,
                                affectionScore = (cur.affectionScore + 3).coerceAtMost(100),
                                userNickname = memoryManager.getUserNickname(),
                                memories = memoryManager.getMemories()
                            )
                        }
                        ttsEngine.speak(
                            text = result.replyText,
                            pitch = _uiState.value.voicePitch,
                            rate = _uiState.value.speechRate,
                            character = _uiState.value.activeCharacter,
                            emotion = result.emotion
                        )
                    } else {
                        // Resume listening in video call
                        if (_uiState.value.isFaceToFaceActive && !_uiState.value.isFaceToFaceMuted) {
                            _uiState.update {
                                it.copy(
                                    faceToFaceCallState = FaceToFaceCallState.LISTENING,
                                    isListeningMic = true,
                                    liveUserSpeech = ""
                                )
                            }
                            micWakeDetector.setPaused(false)
                            micWakeDetector.start()
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("AvatarViewModel", "Face-to-face audio analysis error: ${e.message}")
                    if (mySeq == voiceTurnSeq.get() && _uiState.value.isFaceToFaceActive && !_uiState.value.isFaceToFaceMuted) {
                        _uiState.update {
                            it.copy(
                                faceToFaceCallState = FaceToFaceCallState.LISTENING,
                                isListeningMic = true
                            )
                        }
                        micWakeDetector.setPaused(false)
                        micWakeDetector.start()
                    }
                }
            }
            return
        }

        // 3. Main 3D Room Hands-Free Gemini Live Conversation
        if (snapshot.isLoadingAi || isSynthesizingSpeech) {
            return
        }
        if (!snapshot.isHandsFreeWakeEnabled && snapshot.voiceSessionState == VoiceSessionState.STANDBY_WAKE) {
            return
        }
        val currentSession = snapshot.voiceSessionState
        val isStandby = currentSession == VoiceSessionState.STANDBY_WAKE
        val mySeq = voiceTurnSeq.incrementAndGet()
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(true)

        audioAnalysisJob?.cancel()
        audioAnalysisJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            _uiState.update {
                it.copy(
                    voiceSessionState = VoiceSessionState.GEMINI_LIVE_THINKING,
                    isLoadingAi = true,
                    isUserSpeakingNow = false,
                    currentEmotion = AvatarEmotion.SURPRISED
                )
            }

            try {
                val state = _uiState.value
                val result = aiService.analyzeVoiceAudioSegment(
                    wavBytes = wavBytes,
                    durationMs = durationMs,
                    isStandbyWakeMode = isStandby,
                    requireWakeWordInStandby = state.standbyRequireWakeWord,
                    character = state.activeCharacter,
                    language = state.selectedLanguage,
                    history = state.messages
                )
                if (mySeq != voiceTurnSeq.get()) return@launch

                if (result.wakeDetected) {
                    if (result.isWakeOnly || result.replyText.isBlank()) {
                        // User called "Petto" -> instant chime and enter Gemini Live active listening
                        callHeyPetto()
                    } else {
                        // User spoke a question/sentence -> answer directly in Gemini Live session
                        deliverGeminiLiveVoiceReply(
                            userTranscript = result.transcript.ifBlank { "Halo ${state.activeCharacter.name}" },
                            replyText = result.replyText,
                            emotion = result.emotion
                        )
                    }
                } else {
                    // Ambient noise or non-wake speech during strict standby -> stay silently in standby/live
                    _uiState.update {
                        it.copy(
                            voiceSessionState = if (isStandby) VoiceSessionState.STANDBY_WAKE else VoiceSessionState.GEMINI_LIVE_LISTENING,
                            isLoadingAi = false,
                            isUserSpeakingNow = false,
                            currentEmotion = AvatarEmotion.IDLE
                        )
                    }
                    micWakeDetector.setLiveSessionMode(!isStandby)
                    micWakeDetector.setPaused(false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AvatarViewModel", "Audio segment analysis error: ${e.message}")
                if (mySeq == voiceTurnSeq.get()) {
                    _uiState.update {
                        it.copy(
                            voiceSessionState = if (isStandby) VoiceSessionState.STANDBY_WAKE else VoiceSessionState.GEMINI_LIVE_LISTENING,
                            isLoadingAi = false,
                            isUserSpeakingNow = false
                        )
                    }
                    micWakeDetector.setLiveSessionMode(!isStandby)
                    micWakeDetector.setPaused(false)
                }
            }
        }
    }

    private fun deliverGeminiLiveVoiceReply(
        userTranscript: String,
        replyText: String,
        emotion: AvatarEmotion
    ) {
        captureConversationReminder(userTranscript)
        isSpeakingVoiceReply = true
        micWakeDetector.setPaused(true)
        _uiState.update { state ->
            val userMsg = ChatMessage(senderName = "Kamu", text = userTranscript, isUser = true)
            val avatarMsg = ChatMessage(
                senderName = state.activeCharacter.name,
                text = replyText,
                isUser = false,
                emotion = emotion,
                modelUsed = state.activeLiveModelName.ifBlank { "gemini-3.8-live" }
            )
            val updatedMessages = state.messages + userMsg + avatarMsg
            persistMessages(updatedMessages)
            state.copy(
                messages = updatedMessages,
                liveUserSpeech = userTranscript,
                lastAvatarSpeech = replyText,
                isLoadingAi = false,
                isUserSpeakingNow = false,
                isWakeCommandListening = true,
                voiceSessionState = VoiceSessionState.GEMINI_LIVE_SPEAKING,
                affectionScore = (state.affectionScore + 3).coerceAtMost(100),
                userNickname = memoryManager.getUserNickname(),
                memories = memoryManager.getMemories()
            )
        }
        speakVoiceOnly(replyText, emotion)
    }

    private fun startActiveWakeCommandListening(delayMs: Long = 250L) {
        enterGeminiLiveListeningAfterSpeech(delayMs)
    }

    private fun scheduleHandsFreeWakeRestart(delayMs: Long) {
        resumeContinuousWakeStandby(delayMs)
    }

    fun toggleHandsFreeWake() {
        setHandsFreeWakeEnabled(!_uiState.value.isHandsFreeWakeEnabled)
    }

    fun setHandsFreeWakeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("hands_free_wake_enabled_v1", enabled).apply()
        rapidSpeechErrorCount = 0
        wakeListenJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        _uiState.update {
            it.copy(
                isHandsFreeWakeEnabled = enabled,
                isWakeCommandListening = false,
                isUserSpeakingNow = false,
                isLoadingAi = false,
                voiceSessionState = VoiceSessionState.STANDBY_WAKE
            )
        }
        if (enabled) {
            startHandsFreeWakeListening()
        } else {
            micWakeDetector.stop()
            speechRecognitionHelper.stopListening()
        }
    }

    fun setStandbyRequireWakeWord(required: Boolean) {
        prefs.edit().putBoolean("standby_require_wake_word_v1", required).apply()
        _uiState.update { it.copy(standbyRequireWakeWord = required) }
    }

    fun setMicSensitivityHigh(high: Boolean) {
        prefs.edit().putBoolean("mic_sensitivity_high_v1", high).apply()
        micWakeDetector.setHighSensitivity(high)
        _uiState.update { it.copy(micSensitivityHigh = high) }
    }

    fun setAllowBargeInWhileAiSpeaking(allowed: Boolean) {
        prefs.edit().putBoolean("allow_barge_in_while_ai_speaking_v1", allowed).apply()
        micWakeDetector.setAllowBargeIn(allowed)
        _uiState.update { it.copy(allowBargeInWhileAiSpeaking = allowed) }
    }

    fun onAppPaused() {
        wakeListenJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        micWakeDetector.stop()
        speechRecognitionHelper.stopListening()
    }

    fun onAppResumed(hasAudioPermission: Boolean) {
        if (!hasAudioPermission) return
        val s = _uiState.value
        if (s.isHandsFreeWakeEnabled && !s.isFaceToFaceActive && !s.showVoiceDialog && !s.isRecordingPronunciation) {
            startHandsFreeWakeListening()
        }
    }

    fun triggerDirectVoiceListen() {
        callHeyPetto()
    }

    fun startHandsFreeWakeListening() {
        val s = _uiState.value
        if (!s.isHandsFreeWakeEnabled ||
            s.isFaceToFaceActive ||
            s.showVoiceDialog
        ) {
            return
        }
        try {
            val isCurrentlyTalking = s.isTalking || isTtsPlaybackActive || isLivePlaybackActive
            val isAiPreparingOrBusy = s.isLoadingAi ||
                isSynthesizingSpeech ||
                isSpeakingWakeAck ||
                isSpeakingVoiceReply ||
                s.voiceSessionState == VoiceSessionState.WAKE_RESPONDING ||
                s.voiceSessionState == VoiceSessionState.GEMINI_LIVE_THINKING
            val shouldPauseMic = isAiPreparingOrBusy ||
                (isCurrentlyTalking && !s.allowBargeInWhileAiSpeaking)

            micWakeDetector.setHighSensitivity(s.micSensitivityHigh)
            micWakeDetector.setAllowBargeIn(s.allowBargeInWhileAiSpeaking)
            micWakeDetector.setLiveSessionMode(
                s.voiceSessionState == VoiceSessionState.GEMINI_LIVE_LISTENING
            )
            micWakeDetector.setAiSpeaking(isCurrentlyTalking)
            micWakeDetector.setPaused(shouldPauseMic)
            micWakeDetector.start()
        } catch (e: Exception) {
            Log.w("AvatarViewModel", "Continuous mic wake start failed: ${e.message}")
        }
    }

    private fun onSpeechErrorInCall(errorCode: Int) {
        if (_uiState.value.isFaceToFaceActive && !_uiState.value.isFaceToFaceMuted && rapidSpeechErrorCount < 4) {
            if (errorCode == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                errorCode == android.speech.SpeechRecognizer.ERROR_NO_MATCH) {
                autoListenJob?.cancel()
                autoListenJob = viewModelScope.launch {
                    delay(1200)
                    if (_uiState.value.isFaceToFaceActive &&
                        !_uiState.value.isFaceToFaceMuted &&
                        !_uiState.value.isTalking &&
                        _uiState.value.faceToFaceCallState != FaceToFaceCallState.THINKING) {
                        _uiState.update { it.copy(faceToFaceCallState = FaceToFaceCallState.LISTENING) }
                        speechRecognitionHelper.startListening(_uiState.value.selectedLanguage)
                    }
                }
            }
        }
    }

    private val prefs by lazy {
        application.getSharedPreferences("pawpal_ai_prefs", android.content.Context.MODE_PRIVATE)
    }

    init {
        val initialChar = CharacterCatalog.getById(prefs.getString("selected_character_id", "zayn") ?: "zayn")
        val initialMode = getVoiceEngineMode()
        val savedLanguageName = prefs.getString("selected_language_v1", AppLanguage.INDONESIAN.name)
            ?: AppLanguage.INDONESIAN.name
        val initialLanguage = AppLanguage.entries.find { it.name == savedLanguageName } ?: AppLanguage.INDONESIAN

        val today = todayDateKey()
        val storedHabitDate = prefs.getString("healthy_habits_date_v1", "") ?: ""
        val initialWater: Int
        val initialStretch: Int
        if (storedHabitDate != today) {
            prefs.edit()
                .putString("healthy_habits_date_v1", today)
                .putInt("water_glasses_today", 0)
                .putInt("stretch_count_today", 0)
                .apply()
            initialWater = 0
            initialStretch = 0
        } else {
            initialWater = prefs.getInt("water_glasses_today", 0)
            initialStretch = prefs.getInt("stretch_count_today", 0)
        }

        val savedCostumes = prefs.getStringSet("unlocked_costumes_v1", setOf("none")) ?: setOf("none")
        val savedAccessories = prefs.getStringSet("unlocked_accessories_v1", setOf("none", "bell_collar"))
            ?: setOf("none", "bell_collar")
        val savedThemes = prefs.getStringSet("unlocked_themes_v1", setOf("living_room", "bedroom", "kitchen"))
            ?: setOf("living_room", "bedroom", "kitchen")
        val savedCoins = prefs.getInt("economy_coins_v1", 1500)
        val savedDiamonds = prefs.getInt("economy_diamonds_v1", 240)
        val savedLevel = prefs.getInt("player_level_v1", 12)
        val savedProgress = prefs.getFloat("level_progress_v1", 0.65f)
        val savedHandsFree = prefs.getBoolean("hands_free_wake_enabled_v1", true)
        val savedRequireWake = prefs.getBoolean("standby_require_wake_word_v1", true)
        val savedMicHigh = prefs.getBoolean("mic_sensitivity_high_v1", false)
        val savedAllowBargeIn = prefs.getBoolean("allow_barge_in_while_ai_speaking_v1", true)
        val savedMessages = loadSavedMessages()
        val remindersOn = DailyCheckInScheduler.isEnabled(application)
        val conversationRemindersOn = ConversationReminderScheduler.isEnabled(application)
        val savedConversationReminders = ConversationReminderScheduler.all(application)
        val currentUser = currentSignedInUserOrNull()

        micWakeDetector.setHighSensitivity(savedMicHigh)
        micWakeDetector.setAllowBargeIn(savedAllowBargeIn)

        _uiState.update { cur ->
            cur.copy(
                activeCharacter = initialChar,
                selectedLanguage = initialLanguage,
                messages = savedMessages,
                voicePitch = initialChar.defaultVoicePitch,
                speechRate = initialChar.defaultSpeechRate,
                voiceEngineMode = initialMode,
                customization = cur.customization.copy(
                    furColor = initialChar.defaultFurColor,
                    earStyle = if (initialChar.species == PetSpecies.DOG) EarStyle.DOG_FLOPPY else EarStyle.CAT_POINTY
                ),
                userNickname = memoryManager.getUserNickname(),
                memories = memoryManager.getMemories(),
                savedStorySessions = storyRepository.getSavedSessions(),
                moodJournalEntries = moodJournalRepository.getEntries(),
                waterGlassesToday = initialWater,
                stretchCountToday = initialStretch,
                coins = savedCoins,
                diamonds = savedDiamonds,
                playerLevel = savedLevel,
                levelProgress = savedProgress,
                unlockedCostumes = savedCostumes + "none",
                unlockedAccessories = savedAccessories + setOf("none", "bell_collar"),
                unlockedThemes = savedThemes + setOf("living_room", "bedroom", "kitchen"),
                isHandsFreeWakeEnabled = savedHandsFree,
                standbyRequireWakeWord = savedRequireWake,
                micSensitivityHigh = savedMicHigh,
                allowBargeInWhileAiSpeaking = savedAllowBargeIn,
                dailyRemindersEnabled = remindersOn,
                conversationRemindersEnabled = conversationRemindersOn,
                conversationReminders = savedConversationReminders,
                signedInEmail = currentUser?.email,
                signedInDisplayName = currentUser?.displayName
            )
        }

        ttsEngine.engineMode = initialMode
        ttsEngine.elevenLabsApiKey = getElevenLabsApiKey()
        ttsEngine.googleApiKey = getGoogleCloudTtsApiKey()
        ttsEngine.setVoiceProfile(initialChar.defaultVoicePitch, initialChar.defaultSpeechRate)
        ttsEngine.setLanguage(initialLanguage)

        viewModelScope.launch {
            delay(500)
            deliverGreeting(initialChar, initialLanguage)
            if (currentUser != null) {
                syncFromAndToCloud()
            }
        }

        startIdleBehaviorLoop()
    }

    /**
     * Periodically triggers lifelike 3D idle animations (stretching, looking around, sneezing,
     * yawning, ear scratching, happy bounce) when the pet is not in an active conversation.
     */
    private fun startIdleBehaviorLoop() {
        idleBehaviorJob?.cancel()
        idleBehaviorJob = viewModelScope.launch {
            while (isActive) {
                val waitTimeMs = (6500..11000).random().toLong()
                delay(waitTimeMs)

                val s = _uiState.value
                val isCompletelyIdle = !s.isTalking &&
                    !s.isLoadingAi &&
                    !s.isFaceToFaceActive &&
                    !s.showHomeworkMode &&
                    !s.showVoiceDialog &&
                    s.voiceSessionState == VoiceSessionState.STANDBY_WAKE &&
                    !s.isUserSpeakingNow &&
                    !isSpeakingWakeAck &&
                    !isSpeakingVoiceReply

                if (isCompletelyIdle) {
                    val candidates = IdlePetBehavior.activeBehaviors.filter { it != lastIdleBehavior }
                    val picked = candidates.randomOrNull() ?: IdlePetBehavior.LOOKING_AROUND
                    lastIdleBehavior = picked

                    val idleEmotion = when (picked) {
                        IdlePetBehavior.STRETCHING -> AvatarEmotion.HAPPY
                        IdlePetBehavior.LOOKING_AROUND -> AvatarEmotion.IDLE
                        IdlePetBehavior.SNEEZING -> AvatarEmotion.SURPRISED
                        IdlePetBehavior.YAWNING -> AvatarEmotion.SLEEPY
                        IdlePetBehavior.EAR_SCRATCH -> AvatarEmotion.HAPPY
                        IdlePetBehavior.HAPPY_BOUNCE -> AvatarEmotion.HAPPY
                        IdlePetBehavior.NONE -> AvatarEmotion.IDLE
                    }

                    _uiState.update {
                        it.copy(
                            activeIdleBehavior = picked,
                            currentEmotion = idleEmotion
                        )
                    }

                    // Duration of the 3D idle behavior choreography
                    delay(2600L)

                    _uiState.update { cur ->
                        if (cur.activeIdleBehavior == picked && !cur.isTalking) {
                            cur.copy(
                                activeIdleBehavior = IdlePetBehavior.NONE,
                                currentEmotion = AvatarEmotion.IDLE
                            )
                        } else {
                            cur
                        }
                    }
                }
            }
        }
    }

    private fun deliverGreeting(character: PetCharacter, language: AppLanguage) {
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        val timeOfDay = TimeOfDay.fromCurrentHour()
        val nickname = memoryManager.getUserNickname()
        val nameSuffix = if (nickname.isNotBlank()) " $nickname" else ""
        val timeWord = when (timeOfDay) {
            TimeOfDay.DAWN -> "pagi"
            TimeOfDay.DAY -> "siang"
            TimeOfDay.SUNSET -> "sore"
            TimeOfDay.NIGHT -> "malam"
        }
        val greetingText = when (character.id) {
            "zayn" -> "Cih! Selamat $timeWord$nameSuffix! Aku Zayn. B-bukan berarti aku seneng ditemenin kamu $timeWord ini ya! Jangan lupa minum air putih!"
            "siti" -> "Selamat $timeWord sayangku manis$nameSuffix, nama Siti. Siti sangat bersyukur bisa mendampingimu $timeWord ini. Ceritakan harimu ya sayang."
            "rizky" -> "Siap! Selamat $timeWord$nameSuffix! Rizky siap bertugas! Pertahankan disiplin, kesehatan, dan fokus pada target kita!"
            "jamal" -> "Hoaaammm... Selamat $timeWord bro$nameSuffix. Jamal lagi santuy banget nih, enak nih kalau kita main atau dengerin cerita bareng."
            else -> "HEH! Selamat $timeWord$nameSuffix! Gua Heru! Jangan lupa jaga kesehatan lu hari ini kalau gak mau kena semprot!"
        }

        speakVoiceOnly(greetingText, AvatarEmotion.HAPPY)
    }

    /**
     * "Hey Google" style Wake Word response ("Hey Petto" / "Halo Petto"):
     * Plays a crisp zero-latency chime, flashes a brief excited cue, and transitions
     * directly into active Gemini Live listening for the user's spoken command.
     */
    fun callHeyPetto() {
        wakeListenJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        speechPlaybackTurnSeq.incrementAndGet()
        isSpeakingWakeAck = false
        isSpeakingVoiceReply = false
        isSynthesizingSpeech = false
        isHandlingBargeIn = true
        ttsEngine.stop()
        isHandlingBargeIn = false
        micWakeDetector.setPaused(true)
        micWakeDetector.setAiSpeaking(false)
        speechRecognitionHelper.stopListening()

        _uiState.update {
            it.copy(
                currentEmotion = AvatarEmotion.SURPRISED,
                activeIdleBehavior = IdlePetBehavior.NONE,
                isWakeCommandListening = true,
                isLoadingAi = false,
                isTalking = false,
                isUserSpeakingNow = false,
                voiceSessionState = VoiceSessionState.WAKE_RESPONDING,
                liveUserSpeech = "Petto!",
                lastAvatarSpeech = "",
                showVoiceDialog = false
            )
        }
        ttsEngine.playHappyChime()
        micWakeDetector.suppressAudioForMs(650L)

        // Allow the chime echo to decay cleanly before opening active Gemini Live listening
        enterGeminiLiveListeningAfterSpeech(delayMs = 680L)
    }

    /**
     * Handles user's spoken utterance directly like a voice assistant ("Hey Google"):
     * Processes speech with AI and replies purely via voice without creating text bubbles.
     */
    private fun handleDirectVoiceUtterance(spokenText: String) {
        val clean = spokenText.trim()
        if (clean.isBlank()) return

        wakeListenJob?.cancel()
        audioAnalysisJob?.cancel()
        val mySeq = voiceTurnSeq.incrementAndGet()
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(true)
        speechRecognitionHelper.stopListening()
        val state = _uiState.value
        val userMsg = ChatMessage(senderName = "Kamu", text = clean, isUser = true)
        captureConversationReminder(clean)
        val updatedHistory = state.messages + userMsg

        _uiState.update { cur ->
            val h = cur.messages + userMsg
            cur.copy(
                messages = h,
                liveUserSpeech = clean,
                isLoadingAi = true,
                isWakeCommandListening = false,
                isListeningMic = false,
                isUserSpeakingNow = false,
                currentEmotion = AvatarEmotion.SURPRISED
            )
        }

        viewModelScope.launch {
            try {
                val reply = withTimeoutOrNull(60_000L) {
                    aiService.generateReply(
                        userMessage = clean,
                        character = state.activeCharacter,
                        language = state.selectedLanguage,
                        history = updatedHistory,
                        preferredTier = state.selectedModelTier
                    )
                } ?: aiService.createAiConnectionErrorReply(state.activeCharacter, "Direct voice timeout")
                if (mySeq != voiceTurnSeq.get()) return@launch

                val avatarReplyMsg = ChatMessage(
                    senderName = state.activeCharacter.name,
                    text = reply.text,
                    isUser = false,
                    emotion = reply.emotion,
                    modelUsed = "gemini-3.8-live"
                )

                _uiState.update { cur ->
                    val finalMessages = cur.messages + avatarReplyMsg
                    persistMessages(finalMessages)
                    cur.copy(
                        messages = finalMessages,
                        lastAvatarSpeech = reply.text,
                        isLoadingAi = false,
                        isUserSpeakingNow = false,
                        affectionScore = (cur.affectionScore + 3).coerceAtMost(100),
                        userNickname = memoryManager.getUserNickname(),
                        memories = memoryManager.getMemories()
                    )
                }

                isSpeakingVoiceReply = true
                speakVoiceOnly(reply.text, reply.emotion)
            } catch (e: Exception) {
                Log.e("AvatarViewModel", "Error in handleDirectVoiceUtterance: ${e.message}", e)
                if (mySeq != voiceTurnSeq.get()) return@launch
                val errorReply = aiService.createAiConnectionErrorReply(state.activeCharacter, e.message.orEmpty())
                _uiState.update {
                    it.copy(
                        lastAvatarSpeech = errorReply.text,
                        isLoadingAi = false,
                        isUserSpeakingNow = false
                    )
                }
                isSpeakingVoiceReply = true
                speakVoiceOnly(errorReply.text, errorReply.emotion)
            }
        }
    }

    /**
     * Speaks voice audio, shows the conversation bubble, and animates character lip-sync/emotion.
     */
    fun speakVoiceOnly(text: String, emotion: AvatarEmotion = AvatarEmotion.TALKING) {
        if (text.isBlank()) return
        wakeListenJob?.cancel()
        speechPlaybackTurnSeq.incrementAndGet()
        isHandlingBargeIn = false
        // Hold mic paused while fetching Gemini native audio; Smart Barge-In arms only when playback truly starts
        isSynthesizingSpeech = true
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(true)
        speechRecognitionHelper.stopListening()

        _uiState.update {
            it.copy(
                lastAvatarSpeech = text,
                currentEmotion = emotion,
                isUserSpeakingNow = false
            )
        }

        val current = _uiState.value
        ttsEngine.speak(
            text = text,
            pitch = current.voicePitch,
            rate = current.speechRate,
            character = current.activeCharacter,
            emotion = emotion
        )

        emotionResetJob?.cancel()
        emotionResetJob = viewModelScope.launch {
            delay(3500)
            _uiState.update { cur ->
                if (cur.currentEmotion != AvatarEmotion.IDLE && !cur.isTalking) {
                    cur.copy(currentEmotion = AvatarEmotion.IDLE)
                } else {
                    cur
                }
            }
        }
    }

    fun updateSpeechAndAutoSpeak(text: String, emotion: AvatarEmotion = AvatarEmotion.TALKING) {
        if (text.isBlank()) return
        wakeListenJob?.cancel()
        speechPlaybackTurnSeq.incrementAndGet()
        isHandlingBargeIn = false
        // Hold mic paused while fetching Gemini native audio; Smart Barge-In arms only when playback truly starts
        isSynthesizingSpeech = true
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(true)
        speechRecognitionHelper.stopListening()

        _uiState.update {
            it.copy(
                lastAvatarSpeech = text,
                currentEmotion = emotion,
                isUserSpeakingNow = false
            )
        }

        val current = _uiState.value
        ttsEngine.speak(
            text = text,
            pitch = current.voicePitch,
            rate = current.speechRate,
            character = current.activeCharacter,
            emotion = emotion
        )

        emotionResetJob?.cancel()
        emotionResetJob = viewModelScope.launch {
            delay(3500)
            _uiState.update { cur ->
                if (cur.currentEmotion != AvatarEmotion.IDLE && !cur.isTalking) {
                    cur.copy(currentEmotion = AvatarEmotion.IDLE)
                } else {
                    cur
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        captureConversationReminder(text)

        wakeListenJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        val state = _uiState.value

        // Check wake word in text
        val lower = text.lowercase()
        if (lower.contains("hey petto") || lower.contains("halo petto") || lower.contains("hai petto")) {
            val userMsg = ChatMessage(senderName = "Kamu", text = text, isUser = true)
            val wakeReply = ReactionCatalog.getWakeWordResponse(state.activeCharacter, state.selectedLanguage)
            val avatarReplyMsg = ChatMessage(senderName = state.activeCharacter.name, text = wakeReply, isUser = false, emotion = AvatarEmotion.SURPRISED)
            _uiState.update { cur ->
                cur.copy(
                    messages = cur.messages + userMsg + avatarReplyMsg,
                    affectionScore = (cur.affectionScore + 3).coerceAtMost(100),
                    isUserSpeakingNow = false
                )
            }
            updateSpeechAndAutoSpeak(wakeReply, AvatarEmotion.SURPRISED)
            return
        }

        val userMsg = ChatMessage(
            senderName = "Kamu",
            text = text,
            isUser = true
        )

        val updatedMessages = state.messages + userMsg
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(true)
        _uiState.update { cur ->
            val updated = cur.messages + userMsg
            persistMessages(updated)
            cur.copy(
                messages = updated,
                liveUserSpeech = text,
                isLoadingAi = true,
                isUserSpeakingNow = false,
                currentEmotion = AvatarEmotion.TALKING
            )
        }

        viewModelScope.launch {
            try {
                // Generates AI reply using selected or auto-routed Gemini model tier (gemini-4-argon, gemini-3.8-flash, gemini-3.1-flash-lite)
                val reply = withTimeoutOrNull(60_000L) {
                    aiService.generateReply(
                        userMessage = text,
                        character = state.activeCharacter,
                        language = state.selectedLanguage,
                        history = updatedMessages,
                        preferredTier = state.selectedModelTier
                    )
                } ?: aiService.createAiConnectionErrorReply(state.activeCharacter, "Chat timeout")

                val avatarReplyMsg = ChatMessage(
                    senderName = state.activeCharacter.name,
                    text = reply.text,
                    isUser = false,
                    emotion = reply.emotion,
                    modelUsed = reply.modelUsed
                )

                _uiState.update { cur ->
                    val finalMessages = cur.messages + avatarReplyMsg
                    persistMessages(finalMessages)
                    cur.copy(
                        messages = finalMessages,
                        isLoadingAi = false,
                        isUserSpeakingNow = false,
                        affectionScore = (cur.affectionScore + 3).coerceAtMost(100),
                        userNickname = memoryManager.getUserNickname(),
                        memories = memoryManager.getMemories()
                    )
                }
                if (reply.isFromAi) {
                    grantReward(coinsDelta = 15, xpProgress = 0.04f)
                }

                updateSpeechAndAutoSpeak(reply.text, reply.emotion)
            } catch (e: Exception) {
                Log.e("AvatarViewModel", "Error in sendMessage: ${e.message}", e)
                val errorReply = aiService.createAiConnectionErrorReply(state.activeCharacter, e.message.orEmpty())
                val avatarReplyMsg = ChatMessage(
                    senderName = state.activeCharacter.name,
                    text = errorReply.text,
                    isUser = false,
                    emotion = errorReply.emotion,
                    modelUsed = errorReply.modelUsed
                )
                _uiState.update { cur ->
                    val finalMessages = cur.messages + avatarReplyMsg
                    persistMessages(finalMessages)
                    cur.copy(
                        messages = finalMessages,
                        isLoadingAi = false,
                        isUserSpeakingNow = false
                    )
                }
                updateSpeechAndAutoSpeak(errorReply.text, errorReply.emotion)
            } finally {
                _uiState.update { it.copy(isLoadingAi = false) }
            }
        }
    }

    fun setModelTier(tier: GeminiModelTier) {
        _uiState.update { it.copy(selectedModelTier = tier) }
    }

    /** Applies gameplay progress returned by the libGDX 3D room without replacing the Compose app state. */
    fun applyLibGdxGameResult(
        coins: Int,
        hungerPercent: Float,
        happinessPercent: Float,
        energyPercent: Float
    ) {
        _uiState.update { current ->
            current.copy(
                coins = coins.coerceAtLeast(0),
                hungerLevel = (hungerPercent / 100f).coerceIn(0f, 1f),
                funLevel = (happinessPercent / 100f).coerceIn(0f, 1f),
                energyLevel = (energyPercent / 100f).coerceIn(0f, 1f),
                careFeedbackMessage = "Progres PawPal 3D berhasil disimpan!"
            )
        }
        persistEconomyState()
    }

    fun onCharacterTouched(zone: TouchZone) {
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        val state = _uiState.value

        // Trigger expressive procedural cartoon touch reaction sound effect (giggle, boing, pop, purr, grunt)
        ttsEngine.playTouchSound(state.activeCharacter.species, zone)

        val reactionText = ReactionCatalog.getTouchZoneReaction(state.activeCharacter, zone, state.selectedLanguage)
        val emotion = when (zone) {
            TouchZone.HEAD -> AvatarEmotion.HAPPY
            TouchZone.BELLY -> AvatarEmotion.LOVING
            TouchZone.HANDS -> AvatarEmotion.TALKING
            TouchZone.FEET -> AvatarEmotion.SURPRISED
        }
        _uiState.update { cur ->
            cur.copy(
                affectionScore = (cur.affectionScore + 3).coerceAtMost(100),
                activeIdleBehavior = IdlePetBehavior.NONE,
                isUserSpeakingNow = false
            )
        }
        speakVoiceOnly(reactionText, emotion)
    }

    fun petAvatar() {
        onCharacterTouched(TouchZone.HEAD)
    }

    fun setBackgroundTheme(theme: BackgroundTheme): Boolean {
        val s = _uiState.value
        val isUnlocked = (theme.coinPrice == 0 && theme.diamondPrice == 0) || s.unlockedThemes.contains(theme.id)
        if (!isUnlocked) {
            if (theme.coinPrice > 0 && s.coins < theme.coinPrice) {
                setShopFeedback("Koin belum cukup! Butuh ${theme.coinPrice} Koin untuk membuka ${theme.displayName}.")
                return false
            }
            if (theme.diamondPrice > 0 && s.diamonds < theme.diamondPrice) {
                setShopFeedback("Berlian belum cukup! Butuh ${theme.diamondPrice} 💎 untuk membuka ${theme.displayName}.")
                return false
            }
            _uiState.update { cur ->
                cur.copy(
                    coins = (cur.coins - theme.coinPrice).coerceAtLeast(0),
                    diamonds = (cur.diamonds - theme.diamondPrice).coerceAtLeast(0),
                    unlockedThemes = cur.unlockedThemes + theme.id,
                    backgroundTheme = theme,
                    shopFeedbackMessage = "🎉 ${theme.displayName} berhasil dibuka!"
                )
            }
            ttsEngine.playHappyChime()
            micWakeDetector.suppressAudioForMs(600L)
            persistEconomyState()
        } else {
            val oldTheme = s.backgroundTheme
            _uiState.update { cur -> cur.copy(backgroundTheme = theme) }
            if (oldTheme == theme) return true
        }

        val char = _uiState.value.activeCharacter
        val comment = when (theme) {
            BackgroundTheme.LIVING_ROOM -> when (char.id) {
                "zayn" -> "Sofa ruang tamu ini empuk juga sih. B-bukan berarti aku mau mager di sini ya!"
                "siti" -> "Ruang tamu yang ceria dan penuh mainan sayang, Siti senang bermain di sini bersamamu."
                "rizky" -> "Pangkalan ruang tamu siap patroli! Kondisi aman terkendali!"
                "jamal" -> "Nah, sofa ruang tamu emang spot rebahan paling santuy bro."
                else -> "SOFA KARTUNNYA KEREN JUGA! Awas lu jangan berantakin mainan di lantai!"
            }
            BackgroundTheme.BEDROOM -> when (char.id) {
                "zayn" -> "Kamar tidur? Cih, ngantuk ya tidur aja sana! B-bukan berarti aku mau nemenin kamu ya!"
                "siti" -> "Selamat datang di kamar tidur yang hangat manisku, semoga istirahatmu ditemani mimpi indah."
                "rizky" -> "Waktu istirahat taktis di kamar tidur! Pulihkan tenaga untuk misi berikutnya!"
                "jamal" -> "HOAAAMMM! Ini ruangan favorit Jamal sedunia! Jangan bangunin gua ya, mau tidur."
                else -> "KASUR NYAMAN BANGET! Lu mau tidur sekarang hah?! Awas lu ngorok gua lempar bantal!"
            }
            BackgroundTheme.KITCHEN -> when (char.id) {
                "zayn" -> "Dapur? Hah?! Mau bikinin makanan buat aku?! B-bukan berarti aku laper ya!"
                "siti" -> "Dapur yang cantik sayang! Siti mau buatkan camilan lezat dan susu hangat untukmu."
                "rizky" -> "Stasiun logistik nutrisi terkonfirmasi! Asupan energi siap dikonsumsi!"
                "jamal" -> "KULKAS! Buka kulkasnya bro, ambilin ikan sama susu dingin dong buat Jamal."
                else -> "DAPUR! MANA MAKANAN GUA?! Beruang laper nih, cepetan buka kulkasnya lu!"
            }
            else -> "${char.name} menyukai suasana ruangan ${theme.displayName}!"
        }
        speakVoiceOnly(comment, AvatarEmotion.HAPPY)
        return true
    }

    fun cycleBackgroundTheme() {
        val themes = BackgroundTheme.values()
        val currentIndex = themes.indexOf(_uiState.value.backgroundTheme)
        val nextTheme = themes[(currentIndex + 1) % themes.size]
        setBackgroundTheme(nextTheme)
    }

    fun openVoiceInput() {
        wakeListenJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        speechPlaybackTurnSeq.incrementAndGet()
        isSynthesizingSpeech = false
        speechRecognitionHelper.stopListening()
        rapidSpeechErrorCount = 0
        _uiState.update {
            it.copy(
                showVoiceDialog = true,
                isListeningMic = true,
                isLoadingAi = false,
                isUserSpeakingNow = false,
                speechTranscript = "",
                speechServiceUnavailable = false,
                triggerSystemSpeechFallback = false
            )
        }
        ttsEngine.stop()
        micWakeDetector.setLiveSessionMode(true)
        micWakeDetector.setPaused(false)
        micWakeDetector.start()
    }

    /**
     * Immediately finishes and submits the current spoken audio segment when the user taps
     * the "Selesai & Kirim Sekarang" button in VoiceInputDialog or FaceToFaceCallView.
     */
    fun finishVoiceInputNow() {
        micWakeDetector.finishUtteranceNow()
    }

    fun onSystemSpeechFallbackConsumed() {
        _uiState.update { it.copy(triggerSystemSpeechFallback = false) }
    }

    fun closeVoiceInput() {
        wakeListenJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        speechRecognitionHelper.stopListening()
        _uiState.update {
            it.copy(
                showVoiceDialog = false,
                speechTranscript = "",
                isListeningMic = false,
                isLoadingAi = false,
                isUserSpeakingNow = false,
                triggerSystemSpeechFallback = false
            )
        }
        resumeContinuousWakeStandby(350L)
    }

    fun onFallbackSpeechResult(text: String) {
        if (text.isNotBlank()) {
            sendVoiceTranscript(text)
        }
    }

    fun sendVoiceTranscript(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || clean.startsWith("🎙️") || clean.startsWith("✨")) {
            finishVoiceInputNow()
            return
        }
        val wasStoryStage = _uiState.value.showStoryStageMode
        val wasHomework = _uiState.value.showHomeworkMode
        closeVoiceInput()
        if (wasStoryStage && _uiState.value.currentStoryNode != null) {
            chooseStoryBranch(clean)
        } else if (wasHomework) {
            setHomeworkQuestion(clean)
            solveHomework(clean)
        } else {
            sendMessage(clean)
        }
    }

    fun replayCurrentSpeech() {
        val speech = _uiState.value.lastAvatarSpeech
        if (speech.isNotBlank()) {
            wakeListenJob?.cancel()
            audioAnalysisJob?.cancel()
            voiceTurnSeq.incrementAndGet()
            speechPlaybackTurnSeq.incrementAndGet()
            isHandlingBargeIn = false
            isSynthesizingSpeech = true
            micWakeDetector.setAiSpeaking(false)
            micWakeDetector.setPaused(true)
            _uiState.update { it.copy(isUserSpeakingNow = false) }
            val cur = _uiState.value
            ttsEngine.speak(
                text = speech,
                pitch = cur.voicePitch,
                rate = cur.speechRate,
                character = cur.activeCharacter,
                emotion = cur.currentEmotion
            )
        }
    }

    fun speakText(text: String) {
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        updateSpeechAndAutoSpeak(text, AvatarEmotion.TALKING)
    }

    fun updateCustomization(newCustomization: AvatarCustomization) {
        val s = _uiState.value
        val old = s.customization
        val char = s.activeCharacter
        val lang = s.selectedLanguage

        var updatedCoins = s.coins
        var updatedDiamonds = s.diamonds
        var updatedCostumes = s.unlockedCostumes
        var updatedAccessories = s.unlockedAccessories
        var boughtItemName = ""

        if (newCustomization.costume != old.costume) {
            val c = newCustomization.costume
            val isUnlocked = (c.coinPrice == 0 && c.diamondPrice == 0) || s.unlockedCostumes.contains(c.id)
            if (!isUnlocked) {
                if (c.coinPrice > 0 && updatedCoins < c.coinPrice) {
                    setShopFeedback("Koin tidak cukup! Butuh ${c.coinPrice} ⭐ untuk ${c.displayName}.")
                    return
                }
                if (c.diamondPrice > 0 && updatedDiamonds < c.diamondPrice) {
                    setShopFeedback("Berlian tidak cukup! Butuh ${c.diamondPrice} 💎 untuk ${c.displayName}.")
                    return
                }
                updatedCoins -= c.coinPrice
                updatedDiamonds -= c.diamondPrice
                updatedCostumes = updatedCostumes + c.id
                boughtItemName = c.displayName
            }
        }

        if (newCustomization.accessory != old.accessory) {
            val a = newCustomization.accessory
            val isUnlocked = (a.coinPrice == 0 && a.diamondPrice == 0) || s.unlockedAccessories.contains(a.id)
            if (!isUnlocked) {
                if (a.coinPrice > 0 && updatedCoins < a.coinPrice) {
                    setShopFeedback("Koin tidak cukup! Butuh ${a.coinPrice} ⭐ untuk ${a.displayName}.")
                    return
                }
                if (a.diamondPrice > 0 && updatedDiamonds < a.diamondPrice) {
                    setShopFeedback("Berlian tidak cukup! Butuh ${a.diamondPrice} 💎 untuk ${a.displayName}.")
                    return
                }
                updatedCoins -= a.coinPrice
                updatedDiamonds -= a.diamondPrice
                updatedAccessories = updatedAccessories + a.id
                boughtItemName = a.displayName
            }
        }

        _uiState.update { cur ->
            cur.copy(
                customization = newCustomization,
                coins = updatedCoins,
                diamonds = updatedDiamonds,
                unlockedCostumes = updatedCostumes,
                unlockedAccessories = updatedAccessories,
                shopFeedbackMessage = if (boughtItemName.isNotBlank()) "🎉 $boughtItemName berhasil dibeli & dipakai!" else ""
            )
        }

        if (boughtItemName.isNotBlank()) {
            ttsEngine.playHappyChime()
            persistEconomyState()
        }

        val reactionText = when {
            newCustomization.costume != old.costume ->
                ReactionCatalog.getCostumeReaction(newCustomization.costume, char, lang)
            newCustomization.accessory != old.accessory ->
                ReactionCatalog.getAccessoryReaction(newCustomization.accessory, char, lang)
            newCustomization.furColorId != old.furColorId ->
                ReactionCatalog.getFurColorReaction(newCustomization.furColorId, char, lang)
            newCustomization.roomTheme != old.roomTheme ->
                when (char.id) {
                    "zayn" -> "Hmph! Ruangan ${newCustomization.roomTheme.displayName}? Baguslah, lumayan nyaman."
                    "siti" -> "Ruangan ${newCustomization.roomTheme.displayName} ini sangat indah dan damai sayang."
                    "rizky" -> "Lokasi pangkalan ${newCustomization.roomTheme.displayName} telah siap beroperasi!"
                    "jamal" -> "Ruangan baru? Yang penting kasurnya empuk buat rebahan."
                    else -> "Ruangan ${newCustomization.roomTheme.displayName}? Jangan sampai berantakan, awas lu!"
                }
            else -> ""
        }

        if (reactionText.isNotBlank()) {
            speakVoiceOnly(reactionText, AvatarEmotion.HAPPY)
        }
    }

    fun previewCharacterVoice(character: PetCharacter) {
        val sampleLine = when (character.id) {
            "zayn" -> "Hah?! Lu mau denger suara gua?! B-bukan berarti gua mau ngomong sama lu ya!"
            "siti" -> "Halo sayangku, Siti si kelinci manis selalu siap mendengarkanmu dengan penuh kehangatan."
            "rizky" -> "Siap! Laporan vokal Rizky terdengar tegas, jelas, dan siap bertugas!"
            "jamal" -> "Hoaaammm... Kapibara lagi mager ngomong nih bro, tapi buat lu gua sapa deh."
            else -> "HEH! Lu ngapain nyuruh-nyuruh beruang ngomong?! Berisik amat lu jangan bikin emosi!"
        }
        wakeListenJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        speechPlaybackTurnSeq.incrementAndGet()
        isHandlingBargeIn = false
        isSynthesizingSpeech = true
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(true)
        _uiState.update { it.copy(isUserSpeakingNow = false) }
        ttsEngine.speak(
            text = sampleLine,
            pitch = character.defaultVoicePitch,
            rate = character.defaultSpeechRate,
            character = character,
            emotion = AvatarEmotion.HAPPY
        )
    }

    fun selectCharacter(character: PetCharacter) {
        prefs.edit().putString("selected_character_id", character.id).apply()
        wakeListenJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        speechPlaybackTurnSeq.incrementAndGet()
        isSynthesizingSpeech = true
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(true)
        ttsEngine.stop()
        ttsEngine.setVoiceProfile(character.defaultVoicePitch, character.defaultSpeechRate)

        _uiState.update { cur ->
            cur.copy(
                activeCharacter = character,
                voicePitch = character.defaultVoicePitch,
                speechRate = character.defaultSpeechRate,
                isUserSpeakingNow = false,
                customization = cur.customization.copy(
                    furColor = character.defaultFurColor,
                    earStyle = if (character.species == PetSpecies.DOG) EarStyle.DOG_FLOPPY else EarStyle.CAT_POINTY
                )
            )
        }

        deliverGreeting(character, _uiState.value.selectedLanguage)
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("selected_language_v1", language.name).apply()
        _uiState.update { it.copy(selectedLanguage = language) }
        ttsEngine.setLanguage(language)
        deliverGreeting(_uiState.value.activeCharacter, language)
    }

    fun setVoicePitch(pitch: Float) {
        _uiState.update { it.copy(voicePitch = pitch) }
        ttsEngine.setVoiceProfile(pitch, _uiState.value.speechRate)
    }

    fun setSpeechRate(rate: Float) {
        _uiState.update { it.copy(speechRate = rate) }
        ttsEngine.setVoiceProfile(_uiState.value.voicePitch, rate)
    }

    fun testVoice() {
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        val testPhrase = when (_uiState.value.selectedLanguage) {
            AppLanguage.INDONESIAN -> "Halo! Suaraku terdengar bagus dan jernih, kan?"
            AppLanguage.ENGLISH -> "Hello! My voice sounds crisp and friendly, right?"
            AppLanguage.JAPANESE -> "こんにちは！私の声は聞こえますか？"
            AppLanguage.SPANISH -> "¡Hola! Mi voz se escucha muy bien, ¿verdad?"
        }
        updateSpeechAndAutoSpeak(testPhrase, AvatarEmotion.HAPPY)
    }

    fun clearHistory() {
        prefs.edit().remove("saved_chat_history_v1").apply()
        _uiState.update { it.copy(messages = emptyList()) }
    }

    fun getCustomApiKey(): String = aiService.getCustomApiKey()
    fun setCustomApiKey(key: String) {
        aiService.setCustomApiKey(key)
        ttsEngine.googleApiKey = getGoogleCloudTtsApiKey()
    }
    fun isGeminiConnected(): Boolean = aiService.isGeminiConnected()

    fun getVoiceEngineMode(): VoiceEngineMode {
        val name = prefs.getString("voice_engine_mode", VoiceEngineMode.GEMINI_FLASH_TTS.name)
        return try {
            VoiceEngineMode.valueOf(name ?: VoiceEngineMode.GEMINI_FLASH_TTS.name)
        } catch (e: Exception) {
            VoiceEngineMode.GEMINI_FLASH_TTS
        }
    }

    fun setVoiceEngineMode(mode: VoiceEngineMode) {
        prefs.edit().putString("voice_engine_mode", mode.name).apply()
        _uiState.update { it.copy(voiceEngineMode = mode) }
        ttsEngine.engineMode = mode
    }

    fun getElevenLabsApiKey(): String = prefs.getString("elevenlabs_api_key", "") ?: ""
    fun setElevenLabsApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("elevenlabs_api_key", trimmed).apply()
        ttsEngine.elevenLabsApiKey = trimmed
    }

    fun getGoogleCloudTtsApiKey(): String {
        val custom = prefs.getString("google_cloud_tts_api_key", "") ?: ""
        if (custom.isNotBlank()) return custom
        return aiService.getEffectiveApiKey()
    }

    fun setGoogleCloudTtsApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("google_cloud_tts_api_key", trimmed).apply()
        ttsEngine.googleApiKey = if (trimmed.isNotBlank()) trimmed else aiService.getEffectiveApiKey()
    }

    fun startFaceToFaceCall() {
        wakeListenJob?.cancel()
        autoListenJob?.cancel()
        callTimerJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        speechPlaybackTurnSeq.incrementAndGet()
        isSpeakingWakeAck = false
        isSpeakingVoiceReply = false
        isSynthesizingSpeech = true
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(true)
        speechRecognitionHelper.stopListening()
        rapidSpeechErrorCount = 0

        val char = _uiState.value.activeCharacter
        val openingLine = when (char.id) {
            "zayn" -> "Hah?! Ngapain kamu video call ke aku?! B-bukan berarti aku seneng ya! Mau ngomong apa, cepetan?!"
            "siti" -> "Halo sayangku manis, Siti senang sekali bisa tatap muka langsung sama kamu. Ada cerita apa hari ini sayang?"
            "rizky" -> "Panggilan tatap muka terhubung! Sersan Rizky siap bertugas dan mendengarkan laporanmu. Silakan bicara!"
            "jamal" -> "Hoaaam... Video call segala bro, Jamal lagi pw rebahan nih. Yaudah mau ngobrolin apa santai aja..."
            else -> "HEH! Nelpon-nelpon gua lu! Awas aja kalau gak penting gua cakar! Ngomong sekarang lu mau apa?!"
        }

        _uiState.update {
            it.copy(
                isFaceToFaceActive = true,
                faceToFaceCallState = FaceToFaceCallState.SPEAKING,
                isFaceToFaceMuted = false,
                isUserSpeakingNow = false,
                liveUserSpeech = "",
                liveAvatarSpeech = openingLine,
                callDurationSeconds = 0,
                showVoiceDialog = false,
                isWakeCommandListening = false,
                voiceSessionState = VoiceSessionState.STANDBY_WAKE,
                lastAvatarSpeech = "",
                currentEmotion = AvatarEmotion.HAPPY
            )
        }
        ttsEngine.stop()

        // Start call duration timer
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_uiState.value.isFaceToFaceActive) {
                    _uiState.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1) }
                } else {
                    break
                }
            }
        }

        // Connect real-time Gemini Live session via Firebase AI Logic (no manual API key required)
        val lang = _uiState.value.selectedLanguage
        val liveSysPrompt = aiService.buildLiveSystemInstruction(
            character = char,
            language = lang
        )
        geminiLiveClient.connect(
            apiKey = aiService.getEffectiveApiKey(),
            character = char,
            language = lang,
            systemInstruction = liveSysPrompt
        )

        // Deliver spoken opening line
        ttsEngine.playHappyChime()
        isSynthesizingSpeech = true
        val cur = _uiState.value
        ttsEngine.speak(
            text = openingLine,
            pitch = cur.voicePitch,
            rate = cur.speechRate,
            character = char,
            emotion = AvatarEmotion.HAPPY
        )
    }

    fun endFaceToFaceCall() {
        callTimerJob?.cancel()
        autoListenJob?.cancel()
        wakeListenJob?.cancel()
        liveTurnTimeoutJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        speechPlaybackTurnSeq.incrementAndGet()
        isLivePlaybackActive = false
        isTtsPlaybackActive = false
        isSynthesizingSpeech = false

        _uiState.update {
            it.copy(
                isFaceToFaceActive = false,
                faceToFaceCallState = FaceToFaceCallState.CONNECTING,
                liveUserSpeech = "",
                liveAvatarSpeech = "",
                liveCameraBitmap = null,
                isListeningMic = false,
                isUserSpeakingNow = false,
                isTalking = false,
                isGeminiLiveConnected = false,
                voiceSessionState = VoiceSessionState.STANDBY_WAKE
            )
        }
        geminiLiveClient.disconnect()
        ttsEngine.stop()
        speechRecognitionHelper.stopListening()
        resumeContinuousWakeStandby(500L)
    }

    fun updateLiveCameraFrame(bitmap: android.graphics.Bitmap?) {
        _uiState.update { it.copy(liveCameraBitmap = bitmap) }
    }

    fun askCameraVisionInCall() {
        val prompt = "Lihat aku dari kamera sekarang! Apa yang sedang kamu lihat di kameraku dan gimana penampilanku?"
        handleFaceToFaceUserUtterance(prompt)
    }

    fun toggleFaceToFaceMute() {
        val newMuted = !_uiState.value.isFaceToFaceMuted
        rapidSpeechErrorCount = 0
        _uiState.update {
            it.copy(
                isFaceToFaceMuted = newMuted,
                isListeningMic = !newMuted,
                isUserSpeakingNow = if (newMuted) false else it.isUserSpeakingNow,
                faceToFaceCallState = if (newMuted) FaceToFaceCallState.MUTED else FaceToFaceCallState.LISTENING
            )
        }
        if (newMuted) {
            micWakeDetector.setPaused(true)
            speechRecognitionHelper.stopListening()
            autoListenJob?.cancel()
        } else {
            if (!_uiState.value.isTalking && !isSynthesizingSpeech) {
                micWakeDetector.setLiveSessionMode(true)
                micWakeDetector.setPaused(false)
                micWakeDetector.start()
            }
        }
    }

    fun interruptOrSpeak() {
        autoListenJob?.cancel()
        liveTurnTimeoutJob?.cancel()
        rapidSpeechErrorCount = 0
        isSynthesizingSpeech = false
        if (_uiState.value.isTalking) {
            geminiLiveClient.interruptPlayback()
            ttsEngine.stop()
        }
        if (_uiState.value.isUserSpeakingNow) {
            // If user was already speaking in call and tapped the button, finish and send immediately!
            micWakeDetector.finishUtteranceNow()
            return
        }
        _uiState.update {
            it.copy(
                isFaceToFaceMuted = false,
                isListeningMic = true,
                faceToFaceCallState = FaceToFaceCallState.LISTENING
            )
        }
        micWakeDetector.setLiveSessionMode(true)
        micWakeDetector.setPaused(false)
        micWakeDetector.start()
    }

    fun handleFaceToFaceUserUtterance(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        autoListenJob?.cancel()
        liveTurnTimeoutJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(true)
        val state = _uiState.value

        val userMsg = ChatMessage(senderName = "Kamu", text = cleanText, isUser = true)
        val updatedMessages = state.messages + userMsg

        _uiState.update { cur ->
            val updated = cur.messages + userMsg
            persistMessages(updated)
            cur.copy(
                liveUserSpeech = cleanText,
                messages = updated,
                faceToFaceCallState = FaceToFaceCallState.THINKING,
                currentEmotion = AvatarEmotion.TALKING,
                isListeningMic = false,
                isUserSpeakingNow = false
            )
        }

        // If Gemini 3.8 Live bidirectional WebSocket is active, stream turn directly with timeout fallback
        if (geminiLiveClient.isSetupComplete.get() && geminiLiveClient.sendUserTurn(cleanText, state.liveCameraBitmap)) {
            speechPlaybackTurnSeq.incrementAndGet()
            isHandlingBargeIn = false
            liveTurnTimeoutJob = viewModelScope.launch {
                delay(15_000L)
                if (_uiState.value.isFaceToFaceActive &&
                    _uiState.value.faceToFaceCallState == FaceToFaceCallState.THINKING &&
                    !_uiState.value.isTalking
                ) {
                    Log.w("AvatarViewModel", "Gemini Live turn timed out in THINKING; falling back to generateReply")
                    runFaceToFaceTextFallback(cleanText, _uiState.value, _uiState.value.messages)
                }
            }
            return
        }

        viewModelScope.launch {
            runFaceToFaceTextFallback(cleanText, state, updatedMessages)
        }
    }

    private suspend fun runFaceToFaceTextFallback(
        cleanText: String,
        state: AvatarUiState,
        updatedMessages: List<ChatMessage>
    ) {
        try {
            val reply = withTimeoutOrNull(45_000L) {
                aiService.generateReply(
                    userMessage = cleanText,
                    character = state.activeCharacter,
                    language = state.selectedLanguage,
                    history = updatedMessages,
                    cameraBitmap = state.liveCameraBitmap,
                    preferredTier = state.selectedModelTier
                )
            } ?: aiService.createAiConnectionErrorReply(state.activeCharacter, "Face-to-face timeout")

            if (!_uiState.value.isFaceToFaceActive) return

            val avatarReplyMsg = ChatMessage(
                senderName = state.activeCharacter.name,
                text = reply.text,
                isUser = false,
                emotion = reply.emotion,
                modelUsed = "gemini-3.8-live"
            )

            speechPlaybackTurnSeq.incrementAndGet()
            isHandlingBargeIn = false
            isSynthesizingSpeech = true
            micWakeDetector.setAiSpeaking(false)
            micWakeDetector.setPaused(true)
            _uiState.update { cur ->
                val finalMessages = cur.messages + avatarReplyMsg
                persistMessages(finalMessages)
                cur.copy(
                    messages = finalMessages,
                    liveAvatarSpeech = reply.text,
                    lastAvatarSpeech = reply.text,
                    currentEmotion = reply.emotion,
                    faceToFaceCallState = FaceToFaceCallState.SPEAKING,
                    isUserSpeakingNow = false,
                    affectionScore = (cur.affectionScore + 3).coerceAtMost(100),
                    userNickname = memoryManager.getUserNickname(),
                    memories = memoryManager.getMemories()
                )
            }

            val cur = _uiState.value
            ttsEngine.speak(
                text = reply.text,
                pitch = cur.voicePitch,
                rate = cur.speechRate,
                character = cur.activeCharacter,
                emotion = reply.emotion
            )
        } catch (e: Exception) {
            Log.e("AvatarViewModel", "Error in runFaceToFaceTextFallback: ${e.message}", e)
            if (!_uiState.value.isFaceToFaceActive) return
            val errorReply = aiService.createAiConnectionErrorReply(state.activeCharacter, e.message.orEmpty())
            speechPlaybackTurnSeq.incrementAndGet()
            isHandlingBargeIn = false
            isSynthesizingSpeech = true
            micWakeDetector.setAiSpeaking(false)
            micWakeDetector.setPaused(true)
            _uiState.update {
                it.copy(
                    liveAvatarSpeech = errorReply.text,
                    lastAvatarSpeech = errorReply.text,
                    currentEmotion = errorReply.emotion,
                    faceToFaceCallState = FaceToFaceCallState.SPEAKING,
                    isUserSpeakingNow = false
                )
            }
            val cur = _uiState.value
            ttsEngine.speak(
                text = errorReply.text,
                pitch = cur.voicePitch,
                rate = cur.speechRate,
                character = cur.activeCharacter,
                emotion = errorReply.emotion
            )
        }
    }

    fun quickPromptFaceToFace(promptText: String) {
        handleFaceToFaceUserUtterance(promptText)
    }

    fun switchCharacterInFaceToFace(character: PetCharacter) {
        prefs.edit().putString("selected_character_id", character.id).apply()
        liveTurnTimeoutJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        speechPlaybackTurnSeq.incrementAndGet()
        isHandlingBargeIn = false
        geminiLiveClient.disconnect()
        isSynthesizingSpeech = true
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(true)
        speechRecognitionHelper.stopListening()
        autoListenJob?.cancel()

        ttsEngine.setVoiceProfile(character.defaultVoicePitch, character.defaultSpeechRate)

        val greeting = when (character.id) {
            "zayn" -> "Hmph! Sekarang kamu ngobrol sama Zayn! Jangan mikir aneh-aneh ya!"
            "siti" -> "Halo sayang, Siti hadir di sini untukmu. Siti siap mendengarkan."
            "rizky" -> "Pergantian personel! Sersan Rizky mengambil alih komunikasi!"
            "jamal" -> "Yoo, sekarang giliran Jamal. Santai aja ya ngobrolnya..."
            else -> "SEKARANG GUA HERU YANG HADEPIN LU! Mau ngomong apa lu?!"
        }

        _uiState.update { cur ->
            cur.copy(
                activeCharacter = character,
                voicePitch = character.defaultVoicePitch,
                speechRate = character.defaultSpeechRate,
                liveAvatarSpeech = greeting,
                lastAvatarSpeech = greeting,
                isGeminiLiveConnected = false,
                isUserSpeakingNow = false,
                faceToFaceCallState = FaceToFaceCallState.SPEAKING,
                customization = cur.customization.copy(
                    furColor = character.defaultFurColor,
                    earStyle = if (character.species == PetSpecies.DOG) com.example.model.EarStyle.DOG_FLOPPY else com.example.model.EarStyle.CAT_POINTY
                )
            )
        }
        ttsEngine.stop()

        val lang = _uiState.value.selectedLanguage
        val liveSysPrompt = aiService.buildLiveSystemInstruction(
            character = character,
            language = lang
        )
        geminiLiveClient.connect(
            apiKey = aiService.getEffectiveApiKey(),
            character = character,
            language = lang,
            systemInstruction = liveSysPrompt
        )

        isSynthesizingSpeech = true
        ttsEngine.speak(
            text = greeting,
            pitch = character.defaultVoicePitch,
            rate = character.defaultSpeechRate,
            character = character,
            emotion = AvatarEmotion.HAPPY
        )
    }

    private fun onAvatarFinishedSpeakingInCall() {
        autoListenJob?.cancel()
        autoListenJob = viewModelScope.launch {
            delay(320)
            val s = _uiState.value
            if (s.isFaceToFaceActive &&
                !s.isFaceToFaceMuted &&
                !s.isTalking &&
                !isLivePlaybackActive &&
                !isTtsPlaybackActive &&
                !isSynthesizingSpeech &&
                s.faceToFaceCallState != FaceToFaceCallState.THINKING) {
                _uiState.update {
                    it.copy(
                        faceToFaceCallState = FaceToFaceCallState.LISTENING,
                        isListeningMic = true
                    )
                }
                micWakeDetector.setLiveSessionMode(true)
                micWakeDetector.setPaused(false)
                micWakeDetector.start()
            }
        }
    }

    // ========================================================
    // TALKING TOM LIVING CARE SYSTEMS (MAKAN, TOILET, TIDUR, HIBURAN)
    // ========================================================

    fun setCareOverlay(overlay: CareOverlayType) {
        _uiState.update { it.copy(activeCareOverlay = overlay) }
    }

    fun closeCareOverlay() {
        _uiState.update { it.copy(activeCareOverlay = CareOverlayType.NONE) }
    }

    fun feedPet(food: FoodItem) {
        val state = _uiState.value
        val charName = state.activeCharacter.name
        if (food.coinCost > 0 && state.coins < food.coinCost) {
            val msg = "Koin kurang! Butuh ${food.coinCost} ⭐ untuk membeli ${food.displayName}."
            val spoken = "Koinnya kurang nih! Main mini-game atau ngobrol dulu yuk bareng $charName!"
            _uiState.update { cur ->
                cur.copy(
                    careFeedbackMessage = msg,
                    lastAvatarSpeech = spoken,
                    currentEmotion = AvatarEmotion.SURPRISED
                )
            }
            speakText(spoken)
            return
        }

        val foodLine = when (food) {
            FoodItem.STRAWBERRY -> "Mmm nyam! Strawberry manis ini segar banget!"
            FoodItem.CHEESE -> "Nyam nyam keju! Gurih banget, favorit $charName!"
            FoodItem.FISH -> "Wah ikan segar! Ini makanan paling lezat di dunia!"
            FoodItem.MILK -> "Glek glek glek... Segarnya susu hangat!"
            FoodItem.BURGER -> "Waaah burger mini! Kenyang dan mantap banget!"
            FoodItem.CAKE -> "Manisnya kue ini bikin $charName berenergi lagi!"
        }

        _uiState.update { cur ->
            val newHunger = (cur.hungerLevel + food.hungerRestore).coerceAtMost(1.0f)
            val newProgress = cur.levelProgress + 0.12f
            val newLevel = if (newProgress >= 1f) cur.playerLevel + 1 else cur.playerLevel
            val finalProgress = if (newProgress >= 1f) newProgress - 1f else newProgress
            val newCoins = (cur.coins - food.coinCost).coerceAtLeast(0)
            cur.copy(
                hungerLevel = newHunger,
                levelProgress = finalProgress,
                playerLevel = newLevel,
                coins = newCoins,
                currentEmotion = AvatarEmotion.TALKING,
                lastAvatarSpeech = foodLine,
                careFeedbackMessage = "+${(food.hungerRestore * 100).toInt()}% Kenyang! 😋"
            )
        }
        persistEconomyState()
        speakText(foodLine)
    }

    fun useToilet() {
        viewModelScope.launch {
            val charName = _uiState.value.activeCharacter.name
            _uiState.update { cur ->
                cur.copy(
                    activeCareOverlay = CareOverlayType.TOILET_ROOM,
                    currentEmotion = AvatarEmotion.SURPRISED,
                    lastAvatarSpeech = "Sebentar ya, $charName mau ke toilet dulu...",
                    careFeedbackMessage = "Menuju toilet... 🚽"
                )
            }
            speakText("Sebentar ya, $charName mau ke toilet dulu...")
            delay(2200)

            _uiState.update { cur ->
                val newProgress = cur.levelProgress + 0.15f
                val newLevel = if (newProgress >= 1f) cur.playerLevel + 1 else cur.playerLevel
                val finalProgress = if (newProgress >= 1f) newProgress - 1f else newProgress
                cur.copy(
                    toiletLevel = 1.0f,
                    levelProgress = finalProgress,
                    playerLevel = newLevel,
                    coins = cur.coins + 15,
                    currentEmotion = AvatarEmotion.HAPPY,
                    lastAvatarSpeech = "Aaaah lega dan bersih banget! Sekarang $charName siap beraktivitas lagi!",
                    careFeedbackMessage = "Toilet Bersih 100%! +15 Koin ✨"
                )
            }
            persistEconomyState()
            speakText("Aaaah lega dan bersih banget! Sekarang $charName siap beraktivitas lagi!")
            delay(2000)
            closeCareOverlay()
        }
    }

    fun toggleSleep() {
        val state = _uiState.value
        val charName = state.activeCharacter.name
        if (state.activeCareOverlay == CareOverlayType.SLEEP_MODE) {
            // Wake up
            _uiState.update { cur ->
                val newProgress = cur.levelProgress + 0.20f
                val newLevel = if (newProgress >= 1f) cur.playerLevel + 1 else cur.playerLevel
                val finalProgress = if (newProgress >= 1f) newProgress - 1f else newProgress
                cur.copy(
                    activeCareOverlay = CareOverlayType.NONE,
                    energyLevel = 1.0f,
                    levelProgress = finalProgress,
                    playerLevel = newLevel,
                    currentEmotion = AvatarEmotion.HAPPY,
                    lastAvatarSpeech = "Hoaaaam! Bangun tidur segar banget! Ayo kita main lagi!",
                    careFeedbackMessage = "Energi Pulih 100%! ⚡"
                )
            }
            persistEconomyState()
            speakText("Hoaaaam! Bangun tidur segar banget! Ayo kita main lagi!")
        } else {
            // Go to sleep
            _uiState.update { cur ->
                cur.copy(
                    activeCareOverlay = CareOverlayType.SLEEP_MODE,
                    currentEmotion = AvatarEmotion.IDLE,
                    lastAvatarSpeech = "Hoaaam... $charName ngantuk banget. Selamat tidur ya...",
                    careFeedbackMessage = "Mode Tidur Nyenyak 🌙 Zzz..."
                )
            }
            speakText("Hoaaam... $charName ngantuk banget. Selamat tidur...")
        }
    }

    fun playBallBounce() {
        _uiState.update { cur ->
            val newFun = (cur.funLevel + 0.10f).coerceAtMost(1.0f)
            val newScore = cur.miniGameScore + 1
            val newCoins = cur.coins + 10
            val newProgress = cur.levelProgress + 0.05f
            val newLevel = if (newProgress >= 1f) cur.playerLevel + 1 else cur.playerLevel
            val finalProgress = if (newProgress >= 1f) newProgress - 1f else newProgress
            cur.copy(
                funLevel = newFun,
                miniGameScore = newScore,
                coins = newCoins,
                levelProgress = finalProgress,
                playerLevel = newLevel,
                currentEmotion = AvatarEmotion.HAPPY,
                careFeedbackMessage = "Mantap! Pantulan ke-$newScore (+10 Koin) ⚽"
            )
        }
        persistEconomyState()
    }

    fun toggleMimicMode() {
        val newMode = !_uiState.value.isMimicModeActive
        val charName = _uiState.value.activeCharacter.name
        _uiState.update { cur ->
            cur.copy(
                isMimicModeActive = newMode,
                lastAvatarSpeech = if (newMode) "Mode Tiru Suara Aktif! Bicaralah, $charName akan menirumu!" else "Mode Tiru Suara Mati."
            )
        }
        if (newMode) {
            speakText("Mode Tiru Suara Aktif! Bicaralah, $charName akan menirumu!")
        }
    }

    fun openHomeworkMode() {
        val char = _uiState.value.activeCharacter
        val greeting = when (char.id) {
            "zayn" -> "Hmph... Ada tugas sekolah apa yang bikin kamu pusing? Sini aku bantu jelasin... bukan berarti aku peduli ya!"
            "siti" -> "Halo sayangku! Siti siap menemani belajar dan ngerjain tugas sekolah. Mau bahas soal apa hari ini manis?"
            "rizky" -> "Siap komando belajar! Tunjukkan soal tugas sekolah yang harus kita selesaikan sekarang, laksanakan!"
            "jamal" -> "Hoaaammm... Ngerjain PR bareng Jamal santuy aja bro. Santai tapi pasti beres... Soal apa nih?"
            else -> "HEH! Buka buku PR lu! Mau nanya tugas apa ke beruang pintar kayak gua?! Cepetan sini!"
        }
        _uiState.update { cur ->
            cur.copy(
                showHomeworkMode = true,
                currentEmotion = AvatarEmotion.HAPPY,
                lastAvatarSpeech = greeting
            )
        }
        speakText(greeting)
    }

    fun closeHomeworkMode() {
        _uiState.update { it.copy(showHomeworkMode = false) }
    }

    fun setHomeworkSubject(subject: String) {
        _uiState.update { it.copy(homeworkSubject = subject) }
    }

    fun setHomeworkQuestion(question: String) {
        _uiState.update { it.copy(homeworkQuestion = question) }
    }

    fun setHomeworkPhoto(bitmap: android.graphics.Bitmap?) {
        _uiState.update { it.copy(homeworkPhotoBitmap = bitmap) }
    }

    fun solveHomework(customQuestion: String? = null) {
        if (_uiState.value.isSolvingHomework) return
        val rawQ = customQuestion ?: _uiState.value.homeworkQuestion
        val photo = _uiState.value.homeworkPhotoBitmap
        val q = if (rawQ.isBlank() && photo != null) {
            "Tolong analisis dan jelaskan langkah penyelesaian soal tugas yang ada pada foto ini secara lengkap dan benar."
        } else {
            rawQ
        }
        val subject = _uiState.value.homeworkSubject
        val char = _uiState.value.activeCharacter
        val lang = _uiState.value.selectedLanguage

        if (q.isBlank() && photo == null) return

        _uiState.update { it.copy(isSolvingHomework = true) }

        viewModelScope.launch {
            try {
                // Resolves via Live Gemini AI (Firebase AI Logic or Direct Gemini API Key) with 60s timeout
                val solution = withTimeoutOrNull(60_000L) {
                    aiService.generateHomeworkHelp(subject, q, char, lang, photo)
                } ?: aiService.createHomeworkConnectionErrorSolution(subject, q, char, "Homework timeout")

                _uiState.update {
                    it.copy(
                        isSolvingHomework = false,
                        homeworkSolution = solution,
                        currentEmotion = AvatarEmotion.TALKING
                    )
                }
                if (solution.isFromAi) {
                    grantReward(coinsDelta = 30, diamondsDelta = 2, xpProgress = 0.08f)
                }
                val spokenSummary = if (solution.isFromAi) {
                    "${char.name} sudah selesai membahas! ${solution.finalAnswer}"
                } else {
                    solution.finalAnswer
                }
                speakText(spokenSummary)
            } catch (e: Exception) {
                Log.e("AvatarViewModel", "Error solving homework: ${e.message}", e)
                val errorSolution = aiService.createHomeworkConnectionErrorSolution(subject, q, char, e.message.orEmpty())
                _uiState.update {
                    it.copy(
                        isSolvingHomework = false,
                        homeworkSolution = errorSolution,
                        currentEmotion = AvatarEmotion.SURPRISED
                    )
                }
                speakText(errorSolution.finalAnswer)
            } finally {
                _uiState.update { it.copy(isSolvingHomework = false) }
            }
        }
    }

    // ========================================================
    // 1. LONG-TERM CHARACTER MEMORY & USER PROFILE MANAGEMENT
    // ========================================================

    fun saveUserNickname(nickname: String) {
        memoryManager.setUserNickname(nickname)
        val clean = nickname.trim()
        if (clean.isNotBlank()) {
            memoryManager.addOrUpdateFact("Panggilan", "Nama panggilan pengguna adalah $clean")
        }
        val updatedMemories = memoryManager.getMemories()
        _uiState.update {
            it.copy(
                userNickname = memoryManager.getUserNickname(),
                memories = updatedMemories
            )
        }
        if (clean.isNotBlank()) {
            if (currentSignedInUserOrNull() != null) {
                syncProfileToCloudIfSignedIn()
                val nicknameFact = updatedMemories.firstOrNull { it.category == "Panggilan" }
                if (nicknameFact != null) {
                    viewModelScope.launch {
                        try {
                            cloudSyncRepository.upsertMemoryFact(
                                nicknameFact.id,
                                nicknameFact.category,
                                nicknameFact.content
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
            val char = _uiState.value.activeCharacter
            val ack = when (char.id) {
                "zayn" -> "Hmph! Oke $clean, aku bakal inget namamu! B-bukan berarti namamu keren ya!"
                "siti" -> "Baik sayangku $clean, Siti akan selalu mengingat nama indahmu."
                "rizky" -> "Siap! Identitas panggilan '$clean' telah tercatat dalam memori komando!"
                "jamal" -> "Sip bro $clean, gampang diinget tuh sambil rebahan."
                else -> "Oke $clean! Awas aja kalau lu lupa nama gua Heru!"
            }
            speakVoiceOnly(ack, AvatarEmotion.HAPPY)
        }
    }

    fun addMemoryFact(category: String, content: String) {
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) return
        memoryManager.addOrUpdateFact(category, cleanContent)
        val updated = memoryManager.getMemories()
        _uiState.update {
            it.copy(
                userNickname = memoryManager.getUserNickname(),
                memories = updated
            )
        }
        if (currentSignedInUserOrNull() != null) {
            val matched = updated.firstOrNull { it.content.equals(cleanContent, ignoreCase = true) }
            if (matched != null) {
                viewModelScope.launch {
                    try {
                        cloudSyncRepository.upsertMemoryFact(matched.id, matched.category, matched.content)
                    } catch (_: Exception) {}
                }
            }
        }
        ttsEngine.playHappyChime()
        micWakeDetector.suppressAudioForMs(600L)
    }

    fun deleteMemoryFact(factId: String) {
        memoryManager.removeFact(factId)
        _uiState.update {
            it.copy(
                userNickname = memoryManager.getUserNickname(),
                memories = memoryManager.getMemories()
            )
        }
        if (currentSignedInUserOrNull() != null) {
            viewModelScope.launch {
                try {
                    cloudSyncRepository.deleteMemoryFact(factId)
                } catch (_: Exception) {}
            }
        }
    }

    fun clearAllMemories() {
        val oldIds = _uiState.value.memories.map { it.id }
        memoryManager.clearAllMemories()
        _uiState.update {
            it.copy(
                userNickname = "",
                memories = emptyList()
            )
        }
        if (currentSignedInUserOrNull() != null) {
            viewModelScope.launch {
                oldIds.forEach { id ->
                    try {
                        cloudSyncRepository.deleteMemoryFact(id)
                    } catch (_: Exception) {}
                }
                syncProfileToCloudIfSignedIn()
            }
        }
    }

    // ========================================================
    // 6. INTERACTIVE 3D ROOM & PROPS HANDLER
    // ========================================================

    fun onRoomPropTriggered(action: RoomPropAction, food: FoodItem? = null) {
        val state = _uiState.value
        val char = state.activeCharacter

        when (action) {
            RoomPropAction.TOGGLE_LOFI_RADIO -> {
                val newPlaying = !state.isLofiRadioPlaying
                ttsEngine.playHappyChime()
                val line = if (newPlaying) {
                    when (char.id) {
                        "zayn" -> "Hmph, musik Lo-Fi di ruang tamu ini lumayan enak juga buat santai..."
                        "siti" -> "Wah, alunan musik radio Lo-Fi ini bikin suasana ruang tamu makin hangat sayang!"
                        "rizky" -> "Frekuensi radio Lo-Fi diaktifkan! Ritme santai untuk pemulihan fokus!"
                        "jamal" -> "Nah gitu dong, nyalain musik Lo-Fi biar rebahan makin syahdu bro~"
                        else -> "Wih musik apaan nih?! Lumayan asik juga ketukannya buat goyang dikit!"
                    }
                } else {
                    "Radio Lo-Fi dimatikan dulu ya, sekarang ruangan tenang kembali."
                }
                _uiState.update { cur ->
                    cur.copy(
                        isLofiRadioPlaying = newPlaying,
                        activeIdleBehavior = if (newPlaying) IdlePetBehavior.HAPPY_BOUNCE else IdlePetBehavior.NONE,
                        currentEmotion = AvatarEmotion.HAPPY,
                        liveUserSpeech = if (newPlaying) "📻 Nyalakan Radio Lo-Fi" else "📻 Matikan Radio Lo-Fi"
                    )
                }
                speakVoiceOnly(line, AvatarEmotion.HAPPY)
            }

            RoomPropAction.BOUNCE_TOY_BALL -> {
                ttsEngine.playTouchSound(char.species, TouchZone.HANDS)
                val line = when (char.id) {
                    "zayn" -> "Hup! Tangkap bolanya! Jangan remehkan refleks kucingku ya!"
                    "siti" -> "Yeeay! Seru sekali main lempar bola sama kamu sayang!"
                    "rizky" -> "Target bola berhasil ditangkap dengan presisi tinggi! Latihan refleks mantap!"
                    "jamal" -> "Hup... duh bro, main bolanya jangan jauh-jauh ya, Jamal mager lari."
                    else -> "HAHAHA! Sini lempar bolanya yang kenceng biar gua tangkep!"
                }
                _uiState.update { cur ->
                    cur.copy(
                        activeIdleBehavior = IdlePetBehavior.HAPPY_BOUNCE,
                        currentEmotion = AvatarEmotion.HAPPY,
                        miniGameScore = cur.miniGameScore + 1,
                        affectionScore = (cur.affectionScore + 4).coerceAtMost(100),
                        liveUserSpeech = "⚽ Main Bola Mainan"
                    )
                }
                speakVoiceOnly(line, AvatarEmotion.HAPPY)
            }

            RoomPropAction.TOGGLE_NIGHT_LAMP -> {
                val newDimmed = !state.isNightLampDimmed
                ttsEngine.playHappyChime()
                val line = if (newDimmed) {
                    when (char.id) {
                        "zayn" -> "Lampunya diredupkan ya... Hoaaam, jadi agak ngantuk nih."
                        "siti" -> "Cahaya lampu tidur bintangnya teduh sekali sayang, nyaman buat istirahat."
                        "rizky" -> "Mode pencahayaan malam aktif! Bersiap untuk istirahat berkualitas."
                        "jamal" -> "Hoaaammm... Nah gelap-gelap adem gini baru surga dunia buat tidur..."
                        else -> "Gelap juga ya! Awas lu jangan nakut-nakutin gua pas lampu redup!"
                    }
                } else {
                    "Lampu kamar dinyalakan terang lagi! Ayo semangat beraktivitas!"
                }
                _uiState.update { cur ->
                    cur.copy(
                        isNightLampDimmed = newDimmed,
                        activeIdleBehavior = if (newDimmed) IdlePetBehavior.YAWNING else IdlePetBehavior.STRETCHING,
                        currentEmotion = if (newDimmed) AvatarEmotion.SLEEPY else AvatarEmotion.HAPPY,
                        liveUserSpeech = if (newDimmed) "🌙 Redupkan Lampu Tidur" else "☀️ Nyalakan Lampu Kamar"
                    )
                }
                speakVoiceOnly(line, if (newDimmed) AvatarEmotion.SLEEPY else AvatarEmotion.HAPPY)
            }

            RoomPropAction.HUG_STAR_PLUSH -> {
                ttsEngine.playTouchSound(char.species, TouchZone.BELLY)
                val line = when (char.id) {
                    "zayn" -> "B-boneka bintang ini empuk banget... Jangan liatin aku meluk boneka ya!"
                    "siti" -> "Awww boneka bintangnya lembut sekali sayang, rasanya kayak dipeluk kamu!"
                    "rizky" -> "Bantal taktis bentuk bintang ini sangat ergonomis untuk istirahat!"
                    "jamal" -> "Bantal bintang empuk... pas banget buat ganjel kepala Jamal..."
                    else -> "Hmph! Boneka bintang ini punya gua sekarang, empuk juga!"
                }
                _uiState.update { cur ->
                    cur.copy(
                        activeIdleBehavior = IdlePetBehavior.STRETCHING,
                        currentEmotion = AvatarEmotion.LOVING,
                        affectionScore = (cur.affectionScore + 5).coerceAtMost(100),
                        liveUserSpeech = "🧸 Peluk Boneka Bintang"
                    )
                }
                speakVoiceOnly(line, AvatarEmotion.LOVING)
            }

            RoomPropAction.SERVE_KITCHEN_SNACK -> {
                val chosenFood = food ?: FoodItem.STRAWBERRY
                ttsEngine.playHappyChime()
                val line = when (char.id) {
                    "zayn" -> "Nyam nyam... ${chosenFood.displayName} ini enak juga! Makasih ya... dikit!"
                    "siti" -> "Mmm lezatnya ${chosenFood.displayName} ini sayang! Terima kasih sudah menyuapi Siti!"
                    "rizky" -> "Asupan nutrisi ${chosenFood.displayName} diterima! Energi tempur meningkat 100%!"
                    "jamal" -> "Nyam... Mantap bro, makan ${chosenFood.displayName} tanpa perlu bangun dari rebahan."
                    else -> "NYAM NYAM! Enak banget ${chosenFood.displayName}-nya! Tambah lagi dong!"
                }
                _uiState.update { cur ->
                    cur.copy(
                        activeIdleBehavior = IdlePetBehavior.HAPPY_BOUNCE,
                        currentEmotion = AvatarEmotion.HAPPY,
                        hungerLevel = (cur.hungerLevel + chosenFood.hungerRestore).coerceAtMost(1f),
                        affectionScore = (cur.affectionScore + 5).coerceAtMost(100),
                        liveUserSpeech = "${chosenFood.emoji} Sajikan ${chosenFood.displayName}"
                    )
                }
                speakVoiceOnly(line, AvatarEmotion.HAPPY)
            }

            RoomPropAction.BREW_WARM_TEA -> {
                ttsEngine.playHappyChime()
                val line = when (char.id) {
                    "zayn" -> "Slurp... Teh hangatnya wangi juga. Bikin tenggorokan enak!"
                    "siti" -> "Slurp~ Teh hangat buatanmu menenangkan hati sekali sayangku."
                    "rizky" -> "Seduhan teh hangat siap! Suhu optimal untuk menyegarkan pikiran!"
                    "jamal" -> "Slurp... Aaaah seger banget teh angetnya bro..."
                    else -> "Slurp! Awas panas! Tapi enak juga teh bikinan lu!"
                }
                _uiState.update { cur ->
                    cur.copy(
                        activeIdleBehavior = IdlePetBehavior.STRETCHING,
                        currentEmotion = AvatarEmotion.LOVING,
                        liveUserSpeech = "🍵 Seduh Teh Hangat"
                    )
                }
                speakVoiceOnly(line, AvatarEmotion.LOVING)
            }

            RoomPropAction.BLOW_BUBBLES -> {
                ttsEngine.playHappyChime()
                val line = "Waaah lihat gelembung sabunnya melayang warna-warni! Seru banget!"
                _uiState.update { cur ->
                    cur.copy(
                        activeIdleBehavior = IdlePetBehavior.HAPPY_BOUNCE,
                        currentEmotion = AvatarEmotion.HAPPY,
                        liveUserSpeech = "🫧 Tiup Gelembung"
                    )
                }
                speakVoiceOnly(line, AvatarEmotion.HAPPY)
            }
        }
    }

    // ========================================================
    // FUN ACTIVITIES HUB (MINI-GAME, STORYTELLING, HEALTHY ROUTINES)
    // ========================================================

    fun openFunActivities(tab: FunActivityTab = _uiState.value.activeFunTab) {
        _uiState.update { cur ->
            cur.copy(
                showFunActivitiesDialog = true,
                activeFunTab = tab,
                currentTimeOfDay = TimeOfDay.fromCurrentHour()
            )
        }
    }

    fun closeFunActivities() {
        _uiState.update { it.copy(showFunActivitiesDialog = false) }
    }

    fun selectFunTab(tab: FunActivityTab) {
        _uiState.update { it.copy(activeFunTab = tab) }
    }

    fun playRockPaperScissors(userChoice: RpsChoice) {
        val state = _uiState.value
        val char = state.activeCharacter
        val petChoice = RpsChoice.entries.random()

        val result = when {
            userChoice == petChoice -> RpsResult.DRAW
            (userChoice == RpsChoice.ROCK && petChoice == RpsChoice.SCISSORS) ||
                (userChoice == RpsChoice.SCISSORS && petChoice == RpsChoice.PAPER) ||
                (userChoice == RpsChoice.PAPER && petChoice == RpsChoice.ROCK) -> RpsResult.WIN
            else -> RpsResult.LOSE
        }

        val commentary = when (result) {
            RpsResult.WIN -> when (char.id) {
                "zayn" -> "H-hah?! Kok kamu bisa menang lawan aku?! Tadi aku cuma sengaja ngalah dikit ya!"
                "siti" -> "Yeeay! Kamu menang sayangku! Hebat sekali tebakanmu, Siti ikut senang!"
                "rizky" -> "Strategi lawan yang luar biasa! Kemenangan mutlak untukmu, komandan!"
                "jamal" -> "Wah lu menang bro... Jamal telat mikir tadi gara-gara ngantuk, hehe."
                else -> "APA?! GUA KALAH SUIT?! Pasti lu curang nih, ayo tanding ulang sekarang!"
            }
            RpsResult.DRAW -> when (char.id) {
                "zayn" -> "Cih, seri! Pikiran kita kebetulan aja sama, jangan geer!"
                "siti" -> "Wah kita seri sayang! Hati kita sehati banget ya, hihi."
                "rizky" -> "Kekuatan seimbang! Pertahanan dan serangan sama kuat, ayo ulangi!"
                "jamal" -> "Seri bro~ Emang kita kompak banget kalau soal santai gini."
                else -> "Hmph, seri! Ayo suit sekali lagi sampai ada yang menang!"
            }
            RpsResult.LOSE -> when (char.id) {
                "zayn" -> "Hehehe, aku menang! Sudah kuduga kamu bakal ngeluarin ${userChoice.label}!"
                "siti" -> "Hihi, kali ini Siti yang menang sayang. Jangan menyerah ya, ayo coba lagi!"
                "rizky" -> "Poin taktis untuk Sersan Rizky! Jangan lengah, ayo coba ronde berikutnya!"
                "jamal" -> "Wih Jamal menang sambil merem nih bro, hoki banget hari ini."
                else -> "HAHAHA! GUA MENANG TELAK! Makanya jangan remehin beruang pintar kayak gua!"
            }
        }

        ttsEngine.playHappyChime()
        _uiState.update { cur ->
            cur.copy(
                rpsRound = RpsRoundState(
                    userChoice = userChoice,
                    petChoice = petChoice,
                    result = result,
                    commentary = commentary
                ),
                rpsWins = cur.rpsWins + if (result == RpsResult.WIN) 1 else 0,
                rpsDraws = cur.rpsDraws + if (result == RpsResult.DRAW) 1 else 0,
                rpsLosses = cur.rpsLosses + if (result == RpsResult.LOSE) 1 else 0,
                activeIdleBehavior = IdlePetBehavior.HAPPY_BOUNCE,
                currentEmotion = if (result == RpsResult.LOSE) AvatarEmotion.SURPRISED else AvatarEmotion.HAPPY,
                liveUserSpeech = "Suit: ${userChoice.emoji} ${userChoice.label}"
            )
        }
        when (result) {
            RpsResult.WIN -> grantReward(coinsDelta = 25, diamondsDelta = 1, xpProgress = 0.06f)
            RpsResult.DRAW -> grantReward(coinsDelta = 10, diamondsDelta = 0, xpProgress = 0.03f)
            RpsResult.LOSE -> grantReward(coinsDelta = 5, diamondsDelta = 0, xpProgress = 0.02f)
        }
        speakVoiceOnly(commentary, AvatarEmotion.HAPPY)
    }

    fun generateNewQuiz(topic: String) {
        val reqSeq = quizReqSeq.incrementAndGet()
        quizGenerationJob?.cancel()
        val char = _uiState.value.activeCharacter
        _uiState.update { cur ->
            cur.copy(
                isGeneratingQuiz = true,
                selectedQuizOption = null
            )
        }

        quizGenerationJob = viewModelScope.launch {
            try {
                val quiz = withTimeoutOrNull(45_000L) {
                    aiService.generateQuizQuestion(topic, char)
                }
                if (reqSeq != quizReqSeq.get()) return@launch
                if (quiz != null) {
                    _uiState.update {
                        it.copy(
                            currentQuizQuestion = quiz,
                            selectedQuizOption = null,
                            isGeneratingQuiz = false,
                            currentEmotion = AvatarEmotion.TALKING
                        )
                    }
                    speakVoiceOnly("Pertanyaan kuis $topic: ${quiz.question}", AvatarEmotion.TALKING)
                } else {
                    _uiState.update { it.copy(isGeneratingQuiz = false) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AvatarViewModel", "Error generating quiz: ${e.message}", e)
                if (reqSeq == quizReqSeq.get()) {
                    _uiState.update { it.copy(isGeneratingQuiz = false) }
                }
            }
        }
    }

    fun answerQuizOption(selectedIndex: Int) {
        val state = _uiState.value
        val quiz = state.currentQuizQuestion ?: return
        if (state.selectedQuizOption != null) return

        val isCorrect = selectedIndex == quiz.correctIndex
        val spokenReaction = if (isCorrect) {
            "Betul sekali! +10 poin! ${quiz.explanation}"
        } else {
            val rightAns = quiz.options.getOrElse(quiz.correctIndex) { "" }
            "Hampir tepat! Jawaban yang benar adalah $rightAns. ${quiz.explanation}"
        }

        ttsEngine.playHappyChime()
        _uiState.update { cur ->
            cur.copy(
                selectedQuizOption = selectedIndex,
                quizScore = if (isCorrect) cur.quizScore + 10 else cur.quizScore,
                activeIdleBehavior = if (isCorrect) IdlePetBehavior.HAPPY_BOUNCE else IdlePetBehavior.LOOKING_AROUND,
                currentEmotion = if (isCorrect) AvatarEmotion.HAPPY else AvatarEmotion.SURPRISED
            )
        }
        if (isCorrect) {
            grantReward(coinsDelta = 30, diamondsDelta = 2, xpProgress = 0.08f)
        } else {
            grantReward(coinsDelta = 5, diamondsDelta = 0, xpProgress = 0.02f)
        }
        speakVoiceOnly(spokenReaction, if (isCorrect) AvatarEmotion.HAPPY else AvatarEmotion.TALKING)
    }

    fun openStoryStageMode() {
        val state = _uiState.value
        val saved = storyRepository.getSavedSessions()
        // If no current story node is loaded yet, auto-load the most recent saved session or start a new one
        if (state.currentStoryNode == null) {
            val latestSession = saved.firstOrNull()
            if (latestSession != null) {
                _uiState.update { cur ->
                    cur.copy(
                        showFunActivitiesDialog = false,
                        showStoryStageMode = true,
                        activeStorySessionId = latestSession.sessionId,
                        selectedStoryTheme = latestSession.theme,
                        currentStoryNode = latestSession.currentNode,
                        storyInventory = latestSession.inventory,
                        storyHistoryLog = latestSession.storyHistoryLog,
                        storyContextHistory = latestSession.storyHistoryLog.joinToString("\n"),
                        savedStorySessions = saved,
                        currentEmotion = latestSession.currentNode.sceneEmotion
                    )
                }
                speakVoiceOnly(latestSession.currentNode.narrative, latestSession.currentNode.sceneEmotion)
                return
            } else {
                _uiState.update { cur ->
                    cur.copy(
                        showFunActivitiesDialog = false,
                        showStoryStageMode = true,
                        savedStorySessions = saved
                    )
                }
                startInteractiveStory(state.selectedStoryTheme)
                return
            }
        }
        _uiState.update { cur ->
            cur.copy(
                showFunActivitiesDialog = false,
                showStoryStageMode = true,
                savedStorySessions = saved
            )
        }
    }

    fun closeStoryStageMode() {
        _uiState.update {
            it.copy(
                showStoryStageMode = false,
                currentEmotion = AvatarEmotion.IDLE
            )
        }
    }

    fun startInteractiveStory(theme: StoryTheme) {
        if (_uiState.value.isGeneratingStory) return
        val reqSeq = storyReqSeq.incrementAndGet()
        storyGenerationJob?.cancel()
        val state = _uiState.value
        val char = state.activeCharacter
        val newSessionId = "story_${theme.name.lowercase()}_${System.currentTimeMillis()}"
        _uiState.update { cur ->
            cur.copy(
                showFunActivitiesDialog = false,
                showStoryStageMode = true,
                activeStorySessionId = newSessionId,
                selectedStoryTheme = theme,
                currentStoryNode = null,
                storyInventory = emptyList(),
                storyHistoryLog = emptyList(),
                isGeneratingStory = true,
                storyContextHistory = ""
            )
        }

        storyGenerationJob = viewModelScope.launch {
            try {
                val firstNode = withTimeoutOrNull(45_000L) {
                    aiService.generateStoryNode(
                        theme = theme,
                        chapter = 1,
                        maxChapters = 5,
                        previousStory = "",
                        chosenOption = null,
                        inventory = emptyList(),
                        character = char,
                        userNickname = state.userNickname
                    )
                }
                if (reqSeq != storyReqSeq.get()) return@launch
                if (firstNode == null) {
                    _uiState.update { it.copy(isGeneratingStory = false) }
                    return@launch
                }
                val updatedInventory = if (firstNode.acquiredItem != null) {
                    listOf(firstNode.acquiredItem)
                } else {
                    emptyList()
                }
                val initialHistoryLog = listOf("Babak 1 (${firstNode.title}): ${firstNode.narrative}")
                val session = SavedStorySession(
                    sessionId = newSessionId,
                    theme = theme,
                    characterId = char.id,
                    currentNode = firstNode,
                    storyHistoryLog = initialHistoryLog,
                    inventory = updatedInventory,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                val updatedSaved = storyRepository.saveOrUpdateSession(session)

                _uiState.update {
                    it.copy(
                        currentStoryNode = firstNode,
                        storyContextHistory = firstNode.narrative,
                        storyHistoryLog = initialHistoryLog,
                        storyInventory = updatedInventory,
                        savedStorySessions = updatedSaved,
                        isGeneratingStory = false,
                        activeIdleBehavior = IdlePetBehavior.HAPPY_BOUNCE,
                        currentEmotion = firstNode.sceneEmotion
                    )
                }
                syncStorySessionToCloudIfSignedIn(session)
                speakVoiceOnly(firstNode.narrative, firstNode.sceneEmotion)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AvatarViewModel", "Error starting story: ${e.message}", e)
                if (reqSeq == storyReqSeq.get()) {
                    _uiState.update { it.copy(isGeneratingStory = false) }
                }
            }
        }
    }

    fun chooseStoryBranch(chosenOptionText: String) {
        val state = _uiState.value
        if (state.isGeneratingStory) return
        val currentNode = state.currentStoryNode ?: return
        val reqSeq = storyReqSeq.incrementAndGet()
        storyGenerationJob?.cancel()
        val nextChapter = currentNode.chapterNumber + 1
        val char = state.activeCharacter
        val sessionId = state.activeStorySessionId.ifBlank {
            "story_${state.selectedStoryTheme.name.lowercase()}_${System.currentTimeMillis()}"
        }

        _uiState.update { cur ->
            cur.copy(
                isGeneratingStory = true,
                activeStorySessionId = sessionId,
                currentEmotion = AvatarEmotion.SURPRISED
            )
        }

        storyGenerationJob = viewModelScope.launch {
            try {
                val nextNode = withTimeoutOrNull(45_000L) {
                    aiService.generateStoryNode(
                        theme = state.selectedStoryTheme,
                        chapter = nextChapter,
                        maxChapters = currentNode.maxChapters,
                        previousStory = state.storyContextHistory,
                        chosenOption = chosenOptionText,
                        inventory = state.storyInventory,
                        character = char,
                        userNickname = state.userNickname
                    )
                }
                if (reqSeq != storyReqSeq.get()) return@launch
                if (nextNode == null) {
                    _uiState.update { it.copy(isGeneratingStory = false) }
                    return@launch
                }
                val updatedHistory = "${state.storyContextHistory}\n[Keputusan: $chosenOptionText]\n${nextNode.narrative}"
                val updatedLog = state.storyHistoryLog +
                    "[Keputusan: $chosenOptionText]" +
                    "Babak $nextChapter (${nextNode.title}): ${nextNode.narrative}"

                val updatedInventory = if (nextNode.acquiredItem != null &&
                    state.storyInventory.none { it.name.equals(nextNode.acquiredItem.name, ignoreCase = true) }
                ) {
                    state.storyInventory + nextNode.acquiredItem
                } else {
                    state.storyInventory
                }

                val session = SavedStorySession(
                    sessionId = sessionId,
                    theme = state.selectedStoryTheme,
                    characterId = char.id,
                    currentNode = nextNode,
                    storyHistoryLog = updatedLog,
                    inventory = updatedInventory,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                val updatedSaved = storyRepository.saveOrUpdateSession(session)

                ttsEngine.playHappyChime()
                _uiState.update {
                    it.copy(
                        currentStoryNode = nextNode,
                        storyContextHistory = updatedHistory,
                        storyHistoryLog = updatedLog,
                        storyInventory = updatedInventory,
                        savedStorySessions = updatedSaved,
                        isGeneratingStory = false,
                        activeIdleBehavior = IdlePetBehavior.HAPPY_BOUNCE,
                        currentEmotion = nextNode.sceneEmotion
                    )
                }
                syncStorySessionToCloudIfSignedIn(session)
                grantReward(
                    coinsDelta = if (nextNode.isEnding) 45 else 20,
                    diamondsDelta = if (nextNode.isEnding) 3 else 1,
                    xpProgress = 0.07f
                )
                speakVoiceOnly(nextNode.narrative, nextNode.sceneEmotion)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AvatarViewModel", "Error advancing story branch: ${e.message}", e)
                if (reqSeq == storyReqSeq.get()) {
                    _uiState.update { it.copy(isGeneratingStory = false) }
                }
            }
        }
    }

    fun resumeSavedStorySession(session: SavedStorySession) {
        _uiState.update {
            it.copy(
                showFunActivitiesDialog = false,
                showStoryStageMode = true,
                activeStorySessionId = session.sessionId,
                selectedStoryTheme = session.theme,
                currentStoryNode = session.currentNode,
                storyInventory = session.inventory,
                storyHistoryLog = session.storyHistoryLog,
                storyContextHistory = session.storyHistoryLog.joinToString("\n"),
                currentEmotion = session.currentNode.sceneEmotion
            )
        }
        speakVoiceOnly(session.currentNode.narrative, session.currentNode.sceneEmotion)
    }

    fun deleteSavedStorySession(sessionId: String) {
        val updated = storyRepository.deleteSession(sessionId)
        _uiState.update { it.copy(savedStorySessions = updated) }
        if (currentSignedInUserOrNull() != null) {
            viewModelScope.launch {
                try {
                    cloudSyncRepository.deleteStorySession(sessionId)
                } catch (_: Exception) {}
            }
        }
    }

    private fun syncStorySessionToCloudIfSignedIn(session: SavedStorySession) {
        if (currentSignedInUserOrNull() == null) return
        viewModelScope.launch {
            try {
                cloudSyncRepository.upsertStorySession(
                    sessionId = session.sessionId,
                    themeId = session.theme.name,
                    characterId = session.characterId,
                    currentNarrative = session.currentNode.narrative,
                    inventoryLabels = session.inventory.map { "${it.emoji} ${it.name}" },
                    historyLog = session.storyHistoryLog
                )
            } catch (e: Exception) {
                Log.w("AvatarViewModel", "Story cloud sync skipped: ${e.message}")
            }
        }
    }

    fun triggerHealthyHabit(habit: HealthyHabitType) {
        ensureHealthyHabitsDateCurrent()
        val state = _uiState.value
        val char = state.activeCharacter
        ttsEngine.playHappyChime()

        when (habit) {
            HealthyHabitType.DRINK_WATER -> {
                val newWater = (state.waterGlassesToday + 1).coerceAtMost(12)
                prefs.edit()
                    .putString("healthy_habits_date_v1", todayDateKey())
                    .putInt("water_glasses_today", newWater)
                    .apply()
                val msg = when (char.id) {
                    "zayn" -> "Glek glek! Bagus, kamu udah minum $newWater gelas air putih hari ini. Jangan sampai dehidrasi ya!"
                    "siti" -> "Alhamdulillah, segar sekali sayang! Sudah $newWater gelas air putih hari ini, tubuhmu pasti makin sehat."
                    "rizky" -> "Hidrasi terkonfirmasi! Gelas ke-$newWater berhasil diminum! Jaga stamina tetap prima!"
                    "jamal" -> "Seger banget bro air putihnya... udah $newWater gelas nih hari ini, mantap."
                    else -> "BAGUS! Minum air putih yang rajin! Udah $newWater gelas nih, pertahankan!"
                }
                _uiState.update { cur ->
                    cur.copy(
                        waterGlassesToday = newWater,
                        activeIdleBehavior = IdlePetBehavior.HAPPY_BOUNCE,
                        currentEmotion = AvatarEmotion.HAPPY,
                        liveUserSpeech = "💧 Minum Air Putih ($newWater/8)"
                    )
                }
                speakVoiceOnly(msg, AvatarEmotion.HAPPY)
            }

            HealthyHabitType.BODY_STRETCH -> {
                val newStretch = state.stretchCountToday + 1
                prefs.edit()
                    .putString("healthy_habits_date_v1", todayDateKey())
                    .putInt("stretch_count_today", newStretch)
                    .apply()
                val msg = "Ayo rentangkan tangan ke atas dan putar bahumu pelan-pelan bersama ${char.name}... Nah, badan jadi enteng dan segar lagi kan!"
                _uiState.update { cur ->
                    cur.copy(
                        stretchCountToday = newStretch,
                        activeIdleBehavior = IdlePetBehavior.STRETCHING,
                        currentEmotion = AvatarEmotion.HAPPY,
                        liveUserSpeech = "🧘 Peregangan Badan Bersama"
                    )
                }
                speakVoiceOnly(msg, AvatarEmotion.HAPPY)
            }

            HealthyHabitType.EYE_REST -> {
                val msg = "Sekarang alihkan pandanganmu dari layar HP ke objek jauh selama 20 detik sambil berkedip santai... Matamu pasti terasa lebih adem!"
                _uiState.update { cur ->
                    cur.copy(
                        activeIdleBehavior = IdlePetBehavior.LOOKING_AROUND,
                        currentEmotion = AvatarEmotion.LOVING,
                        liveUserSpeech = "👀 Istirahat Mata 20-20-20"
                    )
                }
                speakVoiceOnly(msg, AvatarEmotion.LOVING)
            }

            HealthyHabitType.DEEP_BREATH -> {
                val msg = "Mari tarik napas dalam-dalam lewat hidung... tahan sebentar... lalu hembuskan perlahan lewat mulut. Pikiran jadi jauh lebih tenang!"
                _uiState.update { cur ->
                    cur.copy(
                        activeIdleBehavior = IdlePetBehavior.YAWNING,
                        currentEmotion = AvatarEmotion.LOVING,
                        liveUserSpeech = "🌿 Napas Relaksasi"
                    )
                }
                speakVoiceOnly(msg, AvatarEmotion.LOVING)
            }
        }
        grantReward(coinsDelta = 15, diamondsDelta = 0, xpProgress = 0.04f)
    }

    fun triggerTimeOfDayGreeting() {
        val current = TimeOfDay.fromCurrentHour()
        _uiState.update { it.copy(currentTimeOfDay = current) }
        deliverGreeting(_uiState.value.activeCharacter, _uiState.value.selectedLanguage)
    }

    // ========================================================
    // MOOD JOURNAL & DAILY COMPANION QUESTS
    // ========================================================

    fun selectMoodType(mood: MoodType) {
        _uiState.update {
            it.copy(
                selectedMood = mood,
                currentEmotion = mood.avatarEmotion
            )
        }
    }

    fun updateMoodJournalNote(note: String) {
        _uiState.update { it.copy(moodJournalNote = note) }
    }

    fun submitMoodJournal() {
        val state = _uiState.value
        if (state.isGeneratingMoodReply) return

        val char = state.activeCharacter
        val mood = state.selectedMood
        val note = state.moodJournalNote.trim()

        _uiState.update {
            it.copy(
                isGeneratingMoodReply = true,
                currentEmotion = mood.avatarEmotion
            )
        }

        viewModelScope.launch {
            try {
                val reflection = aiService.generateMoodJournalReflection(
                    mood = mood,
                    userNote = note,
                    character = char,
                    userNickname = state.userNickname
                )
                val now = System.currentTimeMillis()
                val newEntry = MoodJournalEntry(
                    id = "mood_$now",
                    timestampEpochMs = now,
                    dateFormatted = MoodJournalRepository.formatTimestamp(now),
                    mood = mood,
                    userNote = note,
                    characterId = char.id,
                    characterName = char.name,
                    characterEmoji = char.species.emoji,
                    aiReply = reflection.aiReply,
                    suggestedActivityTip = reflection.suggestedActivityTip,
                    isFromAi = reflection.isFromAi
                )
                val updatedEntries = moodJournalRepository.addEntry(newEntry)

                ttsEngine.playHappyChime()
                _uiState.update { cur ->
                    cur.copy(
                        moodJournalEntries = updatedEntries,
                        moodJournalNote = "",
                        isGeneratingMoodReply = false,
                        affectionScore = (cur.affectionScore + 5).coerceAtMost(100),
                        currentEmotion = reflection.emotion,
                        lastAvatarSpeech = reflection.aiReply,
                        memories = memoryManager.getMemories()
                    )
                }
                grantReward(coinsDelta = 25, diamondsDelta = 1, xpProgress = 0.07f)
                if (currentSignedInUserOrNull() != null) {
                    try {
                        cloudSyncRepository.upsertMoodEntry(
                            newEntry.id,
                            newEntry.mood.name,
                            newEntry.userNote,
                            newEntry.aiReply,
                            newEntry.characterId
                        )
                    } catch (_: Exception) {}
                }
                speakVoiceOnly(reflection.aiReply, reflection.emotion)
            } catch (e: Exception) {
                Log.e("AvatarViewModel", "Error submitting mood journal: ${e.message}", e)
                _uiState.update { it.copy(isGeneratingMoodReply = false) }
            }
        }
    }

    fun deleteMoodJournalEntry(entryId: String) {
        val updated = moodJournalRepository.deleteEntry(entryId)
        _uiState.update { it.copy(moodJournalEntries = updated) }
        if (currentSignedInUserOrNull() != null) {
            viewModelScope.launch {
                try {
                    cloudSyncRepository.deleteMoodEntry(entryId)
                } catch (_: Exception) {}
            }
        }
    }

    fun getDailyCompanionQuests(): List<DailyCompanionQuest> {
        val s = _uiState.value
        val chatTurns = s.messages.count { it.isUser }
        val gamePlayed = s.rpsWins + s.rpsDraws + s.rpsLosses + (if (s.quizScore > 0) 1 else 0) + s.savedStorySessions.size
        return listOf(
            DailyCompanionQuest(
                id = "quest_mood",
                title = "Check-In Jurnal Mood",
                subtitle = "Catat perasaanmu hari ini bersama ${s.activeCharacter.name}",
                emoji = "💖",
                currentProgress = s.moodJournalEntries.size.coerceAtMost(1),
                targetProgress = 1,
                affectionReward = 5
            ),
            DailyCompanionQuest(
                id = "quest_water",
                title = "Hidrasi Sehat (4 Gelas)",
                subtitle = "Minum air putih di tab Sehat",
                emoji = "💧",
                currentProgress = s.waterGlassesToday.coerceAtMost(4),
                targetProgress = 4,
                affectionReward = 10
            ),
            DailyCompanionQuest(
                id = "quest_play",
                title = "Main Game / Dongeng 3D",
                subtitle = "Main Suit, Kuis, atau mulai Dongeng Petualangan",
                emoji = "🎮",
                currentProgress = gamePlayed.coerceAtMost(2),
                targetProgress = 2,
                affectionReward = 10
            ),
            DailyCompanionQuest(
                id = "quest_chat",
                title = "Ngobrol Bersama Sahabat",
                subtitle = "Kirim 3 pesan chat atau suara ke ${s.activeCharacter.name}",
                emoji = "💬",
                currentProgress = chatTurns.coerceAtMost(3),
                targetProgress = 3,
                affectionReward = 15
            )
        )
    }

    // ========================================================
    // ECONOMY & SHOP HELPERS
    // ========================================================

    private fun setShopFeedback(message: String) {
        _uiState.update { it.copy(shopFeedbackMessage = message) }
        viewModelScope.launch {
            delay(2800L)
            _uiState.update { cur ->
                if (cur.shopFeedbackMessage == message) cur.copy(shopFeedbackMessage = "") else cur
            }
        }
    }

    fun clearShopFeedback() {
        _uiState.update { it.copy(shopFeedbackMessage = "") }
    }

    private fun grantReward(coinsDelta: Int, diamondsDelta: Int = 0, xpProgress: Float = 0.05f) {
        _uiState.update { cur ->
            val rawProg = cur.levelProgress + xpProgress
            val newLevel = if (rawProg >= 1f) cur.playerLevel + 1 else cur.playerLevel
            val finalProg = if (rawProg >= 1f) rawProg - 1f else rawProg
            cur.copy(
                coins = (cur.coins + coinsDelta).coerceAtLeast(0),
                diamonds = (cur.diamonds + diamondsDelta).coerceAtLeast(0),
                playerLevel = newLevel,
                levelProgress = finalProg
            )
        }
        persistEconomyState()
    }

    private fun persistEconomyState() {
        val s = _uiState.value
        prefs.edit()
            .putInt("economy_coins_v1", s.coins)
            .putInt("economy_diamonds_v1", s.diamonds)
            .putInt("player_level_v1", s.playerLevel)
            .putFloat("level_progress_v1", s.levelProgress)
            .putStringSet("unlocked_costumes_v1", s.unlockedCostumes)
            .putStringSet("unlocked_accessories_v1", s.unlockedAccessories)
            .putStringSet("unlocked_themes_v1", s.unlockedThemes)
            .apply()
        syncProfileToCloudIfSignedIn()
    }

    // ========================================================
    // LANGUAGE TUTOR & PRONUNCIATION COACH (EN, JA, AR, ES, TH)
    // ========================================================

    fun selectTutorLanguage(language: TutorLanguage) {
        _uiState.update {
            it.copy(
                tutorLanguage = language,
                tutorPrompt = null,
                pronunciationEvaluation = null
            )
        }
        generateTutorChallenge()
    }

    fun selectTutorScenario(scenario: TutorScenario) {
        _uiState.update {
            it.copy(
                tutorScenario = scenario,
                tutorPrompt = null,
                pronunciationEvaluation = null
            )
        }
        generateTutorChallenge()
    }

    fun generateTutorChallenge() {
        val s = _uiState.value
        if (s.isGeneratingTutor) return
        _uiState.update {
            it.copy(
                isGeneratingTutor = true,
                pronunciationEvaluation = null
            )
        }
        viewModelScope.launch {
            try {
                val prompt = aiService.generateLanguageTutorPrompt(
                    language = _uiState.value.tutorLanguage,
                    scenario = _uiState.value.tutorScenario,
                    character = _uiState.value.activeCharacter
                )
                _uiState.update {
                    it.copy(
                        isGeneratingTutor = false,
                        tutorPrompt = prompt
                    )
                }
                if (prompt != null) {
                    speakTutorPhrase(prompt.targetPhrase, _uiState.value.tutorLanguage)
                }
            } catch (e: Exception) {
                Log.w("AvatarViewModel", "generateTutorChallenge failed: ${e.message}")
                _uiState.update { it.copy(isGeneratingTutor = false) }
            }
        }
    }

    fun speakTutorPhrase(phrase: String, language: TutorLanguage = _uiState.value.tutorLanguage) {
        if (phrase.isBlank()) return
        wakeListenJob?.cancel()
        audioAnalysisJob?.cancel()
        voiceTurnSeq.incrementAndGet()
        speechPlaybackTurnSeq.incrementAndGet()
        isHandlingBargeIn = false
        isSynthesizingSpeech = true
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(true)
        _uiState.update {
            it.copy(
                lastAvatarSpeech = phrase,
                currentEmotion = AvatarEmotion.TALKING,
                isUserSpeakingNow = false
            )
        }
        val cur = _uiState.value
        ttsEngine.speak(
            text = phrase,
            pitch = cur.voicePitch,
            rate = cur.speechRate,
            character = cur.activeCharacter,
            emotion = AvatarEmotion.TALKING,
            overrideLanguageName = language.displayName
        )
    }

    fun togglePronunciationRecording() {
        val s = _uiState.value
        if (s.isEvaluatingPronunciation) return
        if (s.isRecordingPronunciation) {
            micWakeDetector.finishUtteranceNow()
            return
        }
        speechPlaybackTurnSeq.incrementAndGet()
        isHandlingBargeIn = false
        isSynthesizingSpeech = false
        ttsEngine.stop()
        _uiState.update {
            it.copy(
                isRecordingPronunciation = true,
                pronunciationEvaluation = null
            )
        }
        micWakeDetector.setLiveSessionMode(true)
        micWakeDetector.setAiSpeaking(false)
        micWakeDetector.setPaused(false)
        micWakeDetector.start()
    }

    // ========================================================
    // OPTIONAL GOOGLE CLOUD SYNC & SCHEDULED MOOD NOTIFICATIONS
    // ========================================================

    fun onGoogleSignedIn(email: String?, displayName: String?) {
        _uiState.update {
            it.copy(
                signedInEmail = email,
                signedInDisplayName = displayName,
                cloudSyncStatus = "Menyinkronkan data dengan Cloud Firestore..."
            )
        }
        syncNowToCloud()
    }

    fun onSignedOut() {
        _uiState.update {
            it.copy(
                signedInEmail = null,
                signedInDisplayName = null,
                isCloudSyncing = false,
                cloudSyncStatus = "Keluar dari Google Cloud Sync (Data tetap aman di perangkat)"
            )
        }
    }

    fun syncNowToCloud() {
        if (currentSignedInUserOrNull() == null) return
        viewModelScope.launch {
            syncFromAndToCloud()
        }
    }

    private fun syncProfileToCloudIfSignedIn() {
        if (currentSignedInUserOrNull() == null) return
        viewModelScope.launch {
            try {
                val s = _uiState.value
                cloudSyncRepository.upsertUserProfile(
                    CloudUserProfile(
                        nickname = s.userNickname,
                        playerLevel = s.playerLevel.toLong(),
                        levelProgress = s.levelProgress.toDouble(),
                        coins = s.coins.toLong(),
                        diamonds = s.diamonds.toLong(),
                        unlockedCostumes = s.unlockedCostumes.toList(),
                        unlockedAccessories = s.unlockedAccessories.toList(),
                        unlockedThemes = s.unlockedThemes.toList(),
                        waterGlassesToday = s.waterGlassesToday.toLong(),
                        stretchCountToday = s.stretchCountToday.toLong()
                    )
                )
            } catch (e: Exception) {
                Log.w("AvatarViewModel", "Cloud profile upsert skipped: ${e.message}")
            }
        }
    }

    private suspend fun syncFromAndToCloud() {
        val user = currentSignedInUserOrNull() ?: return
        ensureHealthyHabitsDateCurrent()
        _uiState.update {
            it.copy(
                isCloudSyncing = true,
                signedInEmail = user.email,
                signedInDisplayName = user.displayName,
                cloudSyncStatus = "Sinkronisasi Cloud aktif..."
            )
        }
        try {
            val remoteProfile = cloudSyncRepository.getUserProfile()
            val local = _uiState.value
            val mergedCoins = if (remoteProfile != null) maxOf(local.coins, remoteProfile.coins.toInt()) else local.coins
            val mergedDiamonds = if (remoteProfile != null) maxOf(local.diamonds, remoteProfile.diamonds.toInt()) else local.diamonds
            val mergedLevel = if (remoteProfile != null) maxOf(local.playerLevel, remoteProfile.playerLevel.toInt()) else local.playerLevel
            val mergedLevelProgress = when {
                remoteProfile == null -> local.levelProgress
                remoteProfile.playerLevel.toInt() > local.playerLevel -> remoteProfile.levelProgress.toFloat()
                remoteProfile.playerLevel.toInt() == local.playerLevel -> maxOf(local.levelProgress, remoteProfile.levelProgress.toFloat())
                else -> local.levelProgress
            }.coerceIn(0f, 1f)
            val mergedCostumes = local.unlockedCostumes + (remoteProfile?.unlockedCostumes ?: emptyList())
            val mergedAccessories = local.unlockedAccessories + (remoteProfile?.unlockedAccessories ?: emptyList())
            val mergedThemes = local.unlockedThemes + (remoteProfile?.unlockedThemes ?: emptyList())
            val mergedNickname = local.userNickname.ifBlank { remoteProfile?.nickname.orEmpty() }
            val mergedWater = if (remoteProfile != null) maxOf(local.waterGlassesToday, remoteProfile.waterGlassesToday.toInt()) else local.waterGlassesToday
            val mergedStretch = if (remoteProfile != null) maxOf(local.stretchCountToday, remoteProfile.stretchCountToday.toInt()) else local.stretchCountToday

            if (mergedNickname.isNotBlank() && mergedNickname != local.userNickname) {
                memoryManager.setUserNickname(mergedNickname)
            }
            if (mergedWater != local.waterGlassesToday || mergedStretch != local.stretchCountToday) {
                prefs.edit()
                    .putString("healthy_habits_date_v1", todayDateKey())
                    .putInt("water_glasses_today", mergedWater)
                    .putInt("stretch_count_today", mergedStretch)
                    .apply()
            }

            _uiState.update {
                it.copy(
                    coins = mergedCoins,
                    diamonds = mergedDiamonds,
                    playerLevel = mergedLevel,
                    levelProgress = mergedLevelProgress,
                    unlockedCostumes = mergedCostumes,
                    unlockedAccessories = mergedAccessories,
                    unlockedThemes = mergedThemes,
                    userNickname = mergedNickname,
                    waterGlassesToday = mergedWater,
                    stretchCountToday = mergedStretch
                )
            }

            // Sync memories bidirectionally preserving remote Firestore document IDs
            val remoteMemories = cloudSyncRepository.getMemories()
            val remoteById = remoteMemories.associateBy { it.id }
            for (rm in remoteMemories) {
                memoryManager.upsertFactFromCloud(
                    id = rm.id,
                    category = rm.category,
                    content = rm.content,
                    timestampMs = rm.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }
            val updatedLocalMemories = memoryManager.getMemories()
            for (lm in updatedLocalMemories) {
                val remoteMatch = remoteById[lm.id]
                if (remoteMatch == null || !remoteMatch.content.equals(lm.content, ignoreCase = false) || remoteMatch.category != lm.category) {
                    cloudSyncRepository.upsertMemoryFact(lm.id, lm.category, lm.content)
                }
            }

            // Sync mood entries bidirectionally
            val remoteMoods = cloudSyncRepository.getMoodEntries().map { cm ->
                val moodType = MoodType.entries.find { it.name == cm.moodType } ?: MoodType.HAPPY
                val char = CharacterCatalog.getById(cm.characterId)
                val ts = cm.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                MoodJournalEntry(
                    id = cm.id,
                    timestampEpochMs = ts,
                    dateFormatted = MoodJournalRepository.formatTimestamp(ts),
                    mood = moodType,
                    userNote = cm.note,
                    characterId = char.id,
                    characterName = char.name,
                    characterEmoji = char.species.emoji,
                    aiReply = cm.aiReply,
                    suggestedActivityTip = "",
                    isFromAi = true
                )
            }
            val mergedMoods = moodJournalRepository.mergeEntriesFromCloud(remoteMoods)
            val remoteMoodIds = remoteMoods.map { it.id }.toSet()
            for (me in mergedMoods.take(20)) {
                if (me.id !in remoteMoodIds) {
                    cloudSyncRepository.upsertMoodEntry(me.id, me.mood.name, me.userNote, me.aiReply, me.characterId)
                }
            }

            // Sync story sessions bidirectionally
            val remoteStories = cloudSyncRepository.getStorySessions().mapNotNull { cs ->
                if (cs.id.isBlank() || cs.currentNarrative.isBlank()) null
                else {
                    val theme = StoryTheme.entries.find { it.name == cs.themeId } ?: StoryTheme.CRYSTAL_FOREST
                    val items = cs.inventoryLabels.mapIndexed { idx, label ->
                        StoryItem(
                            id = "cloud_item_$idx",
                            name = label.trim(),
                            emoji = "✨",
                            description = "Item petualangan dari Cloud",
                            chapterAcquired = 1
                        )
                    }
                    SavedStorySession(
                        sessionId = cs.id,
                        theme = theme,
                        characterId = cs.characterId.ifBlank { "zayn" },
                        currentNode = StoryNode(
                            chapterNumber = (cs.historyLog.count { !it.startsWith("[Keputusan:") }).coerceAtLeast(1),
                            maxChapters = 5,
                            title = theme.title,
                            narrative = cs.currentNarrative,
                            petReactionComment = "",
                            sceneEmotion = AvatarEmotion.HAPPY,
                            choiceA = "Lanjutkan petualangan",
                            choiceB = "Jelajahi area sekitar",
                            isFromAi = true
                        ),
                        storyHistoryLog = cs.historyLog,
                        inventory = items,
                        updatedAtEpochMs = cs.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }
            }
            val mergedStories = storyRepository.mergeCloudSessions(remoteStories)
            val remoteStoryIds = remoteStories.map { it.sessionId }.toSet()
            for (ss in mergedStories.take(12)) {
                if (ss.sessionId !in remoteStoryIds) {
                    cloudSyncRepository.upsertStorySession(
                        sessionId = ss.sessionId,
                        themeId = ss.theme.name,
                        characterId = ss.characterId,
                        currentNarrative = ss.currentNode.narrative,
                        inventoryLabels = ss.inventory.map { "${it.emoji} ${it.name}" },
                        historyLog = ss.storyHistoryLog
                    )
                }
            }

            // Push merged profile back up
            cloudSyncRepository.upsertUserProfile(
                CloudUserProfile(
                    nickname = mergedNickname,
                    playerLevel = mergedLevel.toLong(),
                    levelProgress = mergedLevelProgress.toDouble(),
                    coins = mergedCoins.toLong(),
                    diamonds = mergedDiamonds.toLong(),
                    unlockedCostumes = mergedCostumes.toList(),
                    unlockedAccessories = mergedAccessories.toList(),
                    unlockedThemes = mergedThemes.toList(),
                    waterGlassesToday = mergedWater.toLong(),
                    stretchCountToday = mergedStretch.toLong()
                )
            )

            _uiState.update {
                it.copy(
                    memories = updatedLocalMemories,
                    moodJournalEntries = mergedMoods,
                    savedStorySessions = mergedStories,
                    isCloudSyncing = false,
                    cloudSyncStatus = "Tersinkron ke Cloud Firestore (${user.email ?: "Akun Google"})"
                )
            }
            persistEconomyState()
        } catch (e: Exception) {
            Log.w("AvatarViewModel", "Cloud sync error: ${e.message}")
            _uiState.update {
                it.copy(
                    isCloudSyncing = false,
                    cloudSyncStatus = "Gagal sinkronisasi: ${e.localizedMessage ?: "Coba lagi nanti"}"
                )
            }
        }
    }

    fun setDailyRemindersEnabled(enabled: Boolean) {
        DailyCheckInScheduler.setRemindersEnabled(getApplication(), enabled)
        _uiState.update { it.copy(dailyRemindersEnabled = enabled) }
    }

    fun setConversationRemindersEnabled(enabled: Boolean) {
        ConversationReminderScheduler.setEnabled(getApplication(), enabled)
        _uiState.update {
            it.copy(
                conversationRemindersEnabled = enabled,
                conversationReminders = ConversationReminderScheduler.all(getApplication())
            )
        }
    }

    fun removeConversationReminder(id: String) {
        ConversationReminderScheduler.remove(getApplication(), id)
        _uiState.update { state ->
            state.copy(conversationReminders = state.conversationReminders.filterNot { it.id == id })
        }
    }

    private fun captureConversationReminder(message: String) {
        val reminder = runCatching { ConversationReminderScheduler.captureFromMessage(getApplication(), message) }.getOrNull()
        if (reminder != null) {
            Log.i("PawPalReminder", "Saved reminder: ${reminder.title} at ${reminder.at}")
            _uiState.update { state ->
                state.copy(
                    conversationReminders = (state.conversationReminders.filterNot { it.id == reminder.id } + reminder).sortedBy { it.at }
                )
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(getApplication(), "🐾 PawPal akan mengingatkan: ${reminder.title}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun sendTestCheckInNotification() {
        DailyCheckInScheduler.showMoodAwareNotificationNow(
            getApplication(),
            DailyCheckInScheduler.SLOT_MORNING
        )
    }

    override fun onCleared() {
        super.onCleared()
        geminiLiveClient.disconnect()
        micWakeDetector.stop()
        ttsEngine.release()
        speechRecognitionHelper.destroy()
    }
}
