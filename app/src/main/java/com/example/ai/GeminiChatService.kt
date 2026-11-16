package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.AppLanguage
import com.example.model.AvatarEmotion
import com.example.model.ChatMessage
import com.example.model.GeminiModelTier
import com.example.model.LanguageTutorPrompt
import com.example.model.MoodType
import com.example.model.PetCharacter
import com.example.model.PetMemoryManager
import com.example.model.PronunciationEvaluation
import com.example.model.QuizQuestion
import com.example.model.ReactionCatalog
import com.example.model.StoryEndingType
import com.example.model.StoryItem
import com.example.model.StoryNode
import com.example.model.StoryTheme
import com.example.model.TutorLanguage
import com.example.model.TutorScenario
import com.example.ui.HomeworkSolution
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class GeminiReply(
    val text: String,
    val emotion: AvatarEmotion = AvatarEmotion.TALKING,
    val isFromAi: Boolean = false,
    val modelUsed: String = "gemini-3.8-flash"
)

data class VoiceAudioAnalysisResult(
    val wakeDetected: Boolean,
    val isWakeOnly: Boolean,
    val transcript: String = "",
    val replyText: String = "",
    val emotion: AvatarEmotion = AvatarEmotion.TALKING
)

data class MoodReflectionResult(
    val aiReply: String,
    val suggestedActivityTip: String,
    val emotion: AvatarEmotion = AvatarEmotion.LOVING,
    val isFromAi: Boolean = true
)

class GeminiChatService(private val context: Context? = null) {

    private val TAG = "GeminiChatService"

    // Tracks models that returned 404 Not Found or unbilled 403 during this session so subsequent turns skip them in 0ms
    private val unavailableModels: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val aiClient by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    // Lazy initialization of Firebase AI Logic models across tiers + resilient live fallbacks:
    // - gemini-4-argon for complex reasoning tasks
    // - gemini-3.8-flash for general multi-turn tasks (primary default)
    // - gemini-3.5-flash-lite for fast low-latency tasks
    // - gemini-2.5-flash / gemini-2.5-flash-lite / gemini-2.0-flash for universal endpoint availability
    private val argonModel by lazy {
        aiClient.generativeModel("gemini-4-argon")
    }

    private val flashModel by lazy {
        aiClient.generativeModel("gemini-3.8-flash")
    }

    private val flashLiteModel by lazy {
        aiClient.generativeModel("gemini-3.5-flash-lite")
    }

    private val flash25Model by lazy {
        aiClient.generativeModel("gemini-2.5-flash")
    }

    private val flash25LiteModel by lazy {
        aiClient.generativeModel("gemini-2.5-flash-lite")
    }

    private val flash20Model by lazy {
        aiClient.generativeModel("gemini-2.0-flash")
    }

    private val homeworkSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "subject" to Schema.string("Mata pelajaran atau kategori topik"),
                "questionSummary" to Schema.string("Ringkasan inti pertanyaan"),
                "conceptOrFormula" to Schema.string("Inti jawaban langsung, konsep utama, atau rumus yang digunakan"),
                "stepByStep" to Schema.array(
                    Schema.string("Penjelasan mendalam poin demi poin atau langkah perhitungan nyata yang langsung menjawab pertanyaan"),
                    "Daftar penjelasan mendalam atau langkah penyelesaian nyata (3-5 poin)"
                ),
                "finalAnswer" to Schema.string("Kesimpulan dan jawaban akhir yang lengkap, mendalam, dan langsung menjawab inti pertanyaan"),
                "tutorTip" to Schema.string("Tips relevan atau saran praktis dengan gaya bicara khas karakter"),
                "practiceChallenge" to Schema.string("1 pertanyaan refleksi atau soal latihan serupa yang relevan dengan topik")
            )
        )
    }

    private val homeworkJsonModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = homeworkSchema
            }
        )
    }

    private val homeworkJsonLiteModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = homeworkSchema
            }
        )
    }

    private val homeworkJson25Model by lazy {
        aiClient.generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = homeworkSchema
            }
        )
    }

    private val homeworkJson25LiteModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-2.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = homeworkSchema
            }
        )
    }

    private val voiceTurnSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "wakeDetected" to Schema.boolean("Bernilai true jika terdengar ucapan manusia yang jelas di rekaman audio; false jika hanya suara hening/noise"),
                "isWakeOnly" to Schema.boolean("Bernilai true HANYA jika pengguna cuma memanggil kata tunggal 'Petto' atau nama karakter tanpa kalimat lain"),
                "transcript" to Schema.string("Transkripsi persis dari ucapan pengguna di dalam audio"),
                "replyText" to Schema.string("Jawaban lisan karakter yang langsung menjawab inti ucapan pengguna secara mendalam, alami, dan sesuai kepribadian"),
                "emotion" to Schema.string("Salah satu dari: HAPPY, LOVING, SURPRISED, SLEEPY, TALKING")
            )
        )
    }

    private val voiceTurnJsonModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceTurnSchema
            }
        )
    }

    private val voiceTurnJsonLiteModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceTurnSchema
            }
        )
    }

    private val voiceTurnJson25Model by lazy {
        aiClient.generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceTurnSchema
            }
        )
    }

    private val voiceTurnJson25LiteModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-2.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceTurnSchema
            }
        )
    }

    private val jsonFlashModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
    }

    private val jsonFlash25Model by lazy {
        aiClient.generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
    }

    private val tutorPromptSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "targetPhrase" to Schema.string("1 kalimat alami praktis dalam bahasa target sesuai skenario"),
                "transliteration" to Schema.string("Cara baca huruf latin/romaji/pinyin/transliterasi yang mudah dibaca penutur Indonesia"),
                "indonesianMeaning" to Schema.string("Terjemahan bahasa Indonesia yang akurat dan natural"),
                "coachTip" to Schema.string("Tips pelafalan singkat dalam bahasa Indonesia dengan gaya karakter")
            )
        )
    }

    private val tutorPromptJsonModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = tutorPromptSchema
            }
        )
    }

    private val tutorPromptJson25Model by lazy {
        aiClient.generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = tutorPromptSchema
            }
        )
    }

    private val tutorEvalSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "score" to Schema.integer("Skor kemiripan dan kejelasan pelafalan 0 sampai 100"),
                "heardTranscript" to Schema.string("Apa yang terdengar diucapkan oleh pengguna dari rekaman audio"),
                "feedback" to Schema.string("Evaluasi spesifik dan ramah dalam bahasa Indonesia tentang pelafalan huruf/nada/intonasi pengguna"),
                "nextPhrase" to Schema.string("1 kalimat balasan/lanjutan roleplay dalam bahasa target"),
                "nextTransliteration" to Schema.string("Transliterasi latin untuk nextPhrase"),
                "nextMeaning" to Schema.string("Arti bahasa Indonesia untuk nextPhrase")
            )
        )
    }

    private val tutorEvalJsonModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = tutorEvalSchema
            }
        )
    }

    private val tutorEvalJson25Model by lazy {
        aiClient.generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = tutorEvalSchema
            }
        )
    }

    private fun recordModelException(modelName: String, e: Exception) {
        val msg = e.message?.lowercase().orEmpty()
        if (msg.contains("404") ||
            msg.contains("not found") ||
            msg.contains("not_found") ||
            msg.contains("does not exist") ||
            msg.contains("not supported") ||
            (modelName == "gemini-4-argon" && (msg.contains("403") || msg.contains("billing") || msg.contains("permission")))
        ) {
            unavailableModels.add(modelName)
        }
    }

    private fun <T> filterAvailableModels(candidates: List<T>, modelNameExtractor: (T) -> String): List<T> {
        val available = candidates.filter { !unavailableModels.contains(modelNameExtractor(it)) }
        return available.ifEmpty {
            unavailableModels.clear()
            candidates
        }
    }

    // Reliable HTTP client with realistic timeouts for AI reasoning
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .build()

    @Volatile
    var lastAiCallSucceeded: Boolean = false
        private set

    private val prefs by lazy {
        context?.getSharedPreferences("pawpal_ai_prefs", Context.MODE_PRIVATE)
    }

    private val memoryManager by lazy {
        context?.let { PetMemoryManager(it) }
    }

    fun getCustomApiKey(): String {
        return prefs?.getString("custom_gemini_api_key", "")?.trim().orEmpty()
    }

    fun setCustomApiKey(key: String) {
        prefs?.edit()?.putString("custom_gemini_api_key", key.trim())?.apply()
    }

    /**
     * Returns ONLY a real Gemini API Key (from user's custom input in APK or AI Studio Secrets panel).
     * Never returns the internal Firebase google_api_key, which is not a public Gemini REST API key.
     */
    fun getEffectiveApiKey(): String {
        val customKey = getCustomApiKey()
        if (customKey.isNotBlank()) return customKey

        val buildConfigKey = try {
            val key = BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
            key?.trim()?.takeIf {
                it.isNotBlank() &&
                    !it.contains("MY_GEMINI_API_KEY") &&
                    !it.contains("YOUR_API_KEY") &&
                    it != "null"
            }
        } catch (e: Exception) {
            null
        }
        return buildConfigKey.orEmpty()
    }

    fun isGeminiConnected(): Boolean {
        val hasFirebase = try {
            context != null && FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
        return hasFirebase || getEffectiveApiKey().isNotBlank() || lastAiCallSucceeded
    }

    /**
     * Transparent connection error reply when device is offline or all Gemini AI endpoints are unreachable.
     * Never returns fake template answers that repeat the user's question.
     */
    fun createAiConnectionErrorReply(
        character: PetCharacter,
        errorDetail: String = ""
    ): GeminiReply {
        val text = when (character.id) {
            "zayn" -> "Hmph! Koneksi ke server Gemini AI lagi terputus nih. Coba cek internetmu terus kirim ulang pertanyaannya ya!"
            "siti" -> "Maaf ya sayangku, koneksi ke Gemini AI sedang terganggu. Coba periksa jaringan internetmu lalu tanya Siti sekali lagi ya..."
            "rizky" -> "Perhatian! Jalur komunikasi ke server Gemini AI terputus sementara! Mohon periksa koneksi internet dan kirim ulang pertanyaan!"
            "jamal" -> "Waduh bro, sinyal ke Gemini AI lagi putus nih. Cek koneksi internetmu bentar terus coba kirim lagi ya."
            else -> "HEH! Sinyal internet ke Gemini AI lagi putus nih! Cek koneksi lu dulu terus tanya lagi ke gua!"
        }
        if (errorDetail.isNotBlank()) {
            Log.w(TAG, "AI Connection Error: $errorDetail")
        }
        return GeminiReply(
            text = text,
            emotion = AvatarEmotion.SURPRISED,
            isFromAi = false,
            modelUsed = "gemini-3.8-flash"
        )
    }

    /**
     * Transparent error state for Homework Mode when all Gemini AI endpoints fail.
     * Never returns generic meta-templates.
     */
    fun createHomeworkConnectionErrorSolution(
        subject: String,
        question: String,
        character: PetCharacter,
        errorDetail: String = ""
    ): HomeworkSolution {
        if (errorDetail.isNotBlank()) {
            Log.w(TAG, "Homework AI Connection Error: $errorDetail")
        }
        return HomeworkSolution(
            subject = subject,
            questionSummary = question,
            conceptOrFormula = "Koneksi ke Server Gemini AI Terputus Sementara",
            stepByStep = listOf(
                "Pastikan perangkat terhubung ke koneksi internet yang stabil.",
                "Ketuk tombol '🔄 Coba Lagi' atau 'Bahas Soal' untuk meminta ${character.name} menganalisis ulang pertanyaan ini dengan Gemini AI."
            ),
            finalAnswer = "Maaf, ${character.name} belum bisa menampilkan jawaban karena sambungan ke Gemini AI terputus. Silakan ketuk '🔄 Coba Lagi' ya!",
            tutorTip = "Pesan dari ${character.name}: Semua jawaban di mode ini menggunakan murni penalaran Gemini AI tanpa template statis.",
            practiceChallenge = "",
            isFromAi = false
        )
    }

    /**
     * Automatically selects the optimal Gemini model tier based on prompt complexity
     * when `preferredTier == GeminiModelTier.AUTO`, or respects the user's manual tier selection:
     * - Complex tasks -> gemini-4-argon
     * - General tasks -> gemini-3.8-flash
     * - Fast tasks -> gemini-3.1-flash-lite
     */
    fun resolveModelTier(
        userMessage: String,
        preferredTier: GeminiModelTier = GeminiModelTier.AUTO
    ): GeminiModelTier {
        if (preferredTier != GeminiModelTier.AUTO) return preferredTier

        val clean = userMessage.trim()
        val lower = clean.lowercase()
        val wordCount = clean.split(Regex("\\s+")).filter { it.isNotBlank() }.size

        val complexKeywords = listOf(
            "jelaskan", "analisis", "mengapa", "kenapa", "perbedaan", "bandingkan",
            "rumus", "teori", "strategi", "langkah", "kode", "program", "algoritma",
            "filsafat", "sejarah", "hitung", "buktikan", "explain", "analyze", "why", "how does"
        )
        if (wordCount >= 16 || complexKeywords.any { lower.contains(it) }) {
            return GeminiModelTier.ARGON_COMPLEX
        }

        if (wordCount <= 4 && !clean.contains("?")) {
            return GeminiModelTier.FLASH_LITE_FAST
        }

        return GeminiModelTier.FLASH_GENERAL
    }

    fun buildLiveSystemInstruction(character: PetCharacter, language: AppLanguage): String {
        return buildSystemInstruction(character, language)
    }

    /**
     * Main multi-turn conversation generator:
     * 1. Wake words check for instant reaction (0ms).
     * 2. Smart model tier routing (gemini-4-argon for complex, gemini-3.8-flash for general, gemini-3.1-flash-lite for fast).
     * 3. Full multi-turn conversation history + character role system instruction.
     * 4. Smart conversational fallback if offline/timeout.
     */
    suspend fun generateReply(
        userMessage: String,
        character: PetCharacter,
        language: AppLanguage,
        history: List<ChatMessage>,
        cameraBitmap: Bitmap? = null,
        preferredTier: GeminiModelTier = GeminiModelTier.AUTO
    ): GeminiReply = withContext(Dispatchers.IO) {
        val cleanMsg = userMessage.trim()
        val lower = cleanMsg.lowercase()

        // Automatically extract and persist any personal facts from the user's message
        memoryManager?.extractAndSaveFromUtterance(cleanMsg)

        val activeTier = resolveModelTier(cleanMsg, preferredTier)
        val targetModelId = activeTier.modelId

        // 1. Instant check: Wake words (0ms response time!)
        if (cameraBitmap == null && (lower.contains("hey petto") || lower.contains("halo petto") || lower.contains("hai petto"))) {
            return@withContext GeminiReply(
                text = ReactionCatalog.getWakeWordResponse(character, language),
                emotion = AvatarEmotion.SURPRISED,
                isFromAi = false,
                modelUsed = "gemini-3.5-flash-lite"
            )
        }

        // 2. Try Firebase AI Logic first (provisioned backend with full model fallback chain)
        var lastError = ""
        try {
            val firebaseReply = withTimeoutOrNull(60_000L) {
                callFirebaseAi(cleanMsg, character, language, history, cameraBitmap, activeTier)
            }
            if (firebaseReply != null && firebaseReply.first.isNotBlank()) {
                lastAiCallSucceeded = true
                return@withContext GeminiReply(
                    text = firebaseReply.first,
                    emotion = inferEmotionFromText(firebaseReply.first, character),
                    isFromAi = true,
                    modelUsed = firebaseReply.second
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e.message.orEmpty()
            Log.w(TAG, "Firebase AI Logic pass error: ${e.message}")
        }

        // 3. If user has a custom/Secrets Gemini API Key, also try Direct Gemini REST API
        val apiKey = getEffectiveApiKey()
        if (apiKey.isNotBlank()) {
            val restModelsOrder = when (activeTier) {
                GeminiModelTier.ARGON_COMPLEX -> listOf(
                    "gemini-4-argon",
                    "gemini-3.8-flash",
                    "gemini-3.5-flash-lite",
                    "gemini-2.5-flash",
                    "gemini-2.5-flash-lite",
                    "gemini-2.0-flash"
                )
                GeminiModelTier.FLASH_LITE_FAST -> listOf(
                    "gemini-3.5-flash-lite",
                    "gemini-3.8-flash",
                    "gemini-2.5-flash-lite",
                    "gemini-2.5-flash",
                    "gemini-2.0-flash"
                )
                else -> listOf(
                    "gemini-3.8-flash",
                    "gemini-3.5-flash-lite",
                    "gemini-2.5-flash",
                    "gemini-2.5-flash-lite",
                    "gemini-2.0-flash"
                )
            }
            try {
                val restResult = withTimeoutOrNull(60_000L) {
                    var replyText = ""
                    for (m in filterAvailableModels(restModelsOrder) { it }) {
                        val candidate = callGeminiApi(m, apiKey, cleanMsg, character, language, history, cameraBitmap)
                        if (candidate.isNotBlank()) {
                            replyText = candidate
                            break
                        }
                    }
                    replyText
                }
                if (!restResult.isNullOrBlank()) {
                    lastAiCallSucceeded = true
                    return@withContext GeminiReply(
                        text = restResult,
                        emotion = inferEmotionFromText(restResult, character),
                        isFromAi = true,
                        modelUsed = targetModelId
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message.orEmpty()
                Log.w(TAG, "Direct Gemini REST API failed: ${e.message}")
            }
        }

        return@withContext createAiConnectionErrorReply(character, lastError).copy(modelUsed = targetModelId)
    }

    private suspend fun callFirebaseAi(
        userMessage: String,
        character: PetCharacter,
        language: AppLanguage,
        history: List<ChatMessage>,
        cameraBitmap: Bitmap? = null,
        activeTier: GeminiModelTier = GeminiModelTier.FLASH_GENERAL
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        val systemPrompt = buildSystemInstruction(character, language)
        val visionNote = if (cameraBitmap != null) {
            "\n[KONTEKS VISUAL KAMERA LANGSUNG: Perhatikan gambar kamera pengguna yang dilampirkan. Komentari ekspresi wajah pengguna, benda yang dipegang, pakaian, atau suasana di kamera secara alami sesuai kepribadianmu dan jawab ucapannya.]\n"
        } else ""

        // Include multi-turn conversation history so the chatbot maintains full thread context
        val historyTranscript = if (history.isNotEmpty()) {
            "\nRIWAYAT PERCAKAPAN SEBELUMNYA:\n" +
                history.takeLast(12).joinToString("\n") { msg ->
                    "${if (msg.isUser) "Pengguna" else character.name}: ${msg.text}"
                } + "\n"
        } else ""

        val prompt = "$systemPrompt$visionNote$historyTranscript\nPengguna: $userMessage\n${character.name}:"

        // Order models according to the active tier, with full fallback across 3.8/3.5/4 and 2.5/2.0 models
        val rawChain = when (activeTier) {
            GeminiModelTier.ARGON_COMPLEX -> listOf(
                Triple(argonModel, "gemini-4-argon", "gemini-4-argon"),
                Triple(flashModel, "gemini-3.8-flash", "gemini-3.8-flash"),
                Triple(flashLiteModel, "gemini-3.5-flash-lite", "gemini-3.5-flash-lite"),
                Triple(flash25Model, "gemini-2.5-flash", "gemini-3.8-flash"),
                Triple(flash25LiteModel, "gemini-2.5-flash-lite", "gemini-3.5-flash-lite"),
                Triple(flash20Model, "gemini-2.0-flash", "gemini-3.8-flash")
            )
            GeminiModelTier.FLASH_LITE_FAST -> listOf(
                Triple(flashLiteModel, "gemini-3.5-flash-lite", "gemini-3.5-flash-lite"),
                Triple(flashModel, "gemini-3.8-flash", "gemini-3.8-flash"),
                Triple(flash25LiteModel, "gemini-2.5-flash-lite", "gemini-3.5-flash-lite"),
                Triple(flash25Model, "gemini-2.5-flash", "gemini-3.8-flash"),
                Triple(flash20Model, "gemini-2.0-flash", "gemini-3.8-flash"),
                Triple(argonModel, "gemini-4-argon", "gemini-4-argon")
            )
            else -> listOf(
                Triple(flashModel, "gemini-3.8-flash", "gemini-3.8-flash"),
                Triple(flashLiteModel, "gemini-3.5-flash-lite", "gemini-3.5-flash-lite"),
                Triple(flash25Model, "gemini-2.5-flash", "gemini-3.8-flash"),
                Triple(flash25LiteModel, "gemini-2.5-flash-lite", "gemini-3.5-flash-lite"),
                Triple(flash20Model, "gemini-2.0-flash", "gemini-3.8-flash"),
                Triple(argonModel, "gemini-4-argon", "gemini-4-argon")
            )
        }

        val modelChain = filterAvailableModels(rawChain) { it.second }

        for ((modelInstance, actualModelId, badgeLabel) in modelChain) {
            try {
                val response = withTimeoutOrNull(20_000L) {
                    if (cameraBitmap != null) {
                        modelInstance.generateContent(
                            content {
                                image(cameraBitmap)
                                text(prompt)
                            }
                        )
                    } else {
                        modelInstance.generateContent(prompt)
                    }
                }
                val text = response?.text?.replace(Regex("^\"|\"$"), "")?.trim()
                if (!text.isNullOrBlank()) return@withContext text to badgeLabel
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordModelException(actualModelId, e)
                Log.w(TAG, "Firebase AI model pass ($actualModelId) skipped: ${e.message}")
            }
        }

        null
    }

    /**
     * Google Gemini Live Multimodal Audio Turn Processor:
     * Takes raw 16kHz Mono PCM WAV audio directly from the microphone (plus optional live camera frame),
     * transcribes the user's speech, and generates an expressive, context-accurate character reply in a single pass.
     */
    suspend fun analyzeVoiceAudioSegment(
        wavBytes: ByteArray,
        durationMs: Long,
        isStandbyWakeMode: Boolean,
        requireWakeWordInStandby: Boolean = true,
        character: PetCharacter,
        language: AppLanguage,
        history: List<ChatMessage>,
        cameraBitmap: Bitmap? = null
    ): VoiceAudioAnalysisResult = withContext(Dispatchers.IO) {
        val recentContext = history.takeLast(8).joinToString("\n") { msg ->
            "${if (msg.isUser) "Pengguna" else character.name}: ${msg.text}"
        }
        val systemInstruction = buildSystemInstruction(character, language)
        val visionContext = if (cameraBitmap != null) {
            "\n[KONTEKS KAMERA VIDEO CALL AKTIF: Perhatikan juga gambar kamera pengguna yang dilampirkan. Jika pengguna bertanya tentang penampilannya, benda yang dipegang, atau suasana sekitar, jawab berdasarkan gambar kamera tersebut!]\n"
        } else ""

        val strictWakeMode = isStandbyWakeMode && requireWakeWordInStandby
        val wakeRulesBlock = if (strictWakeMode) {
            """
            MODE STANDBY KETAT (WAJIB KATA PANGGILAN):
            - Karakter sedang dalam posisi Standby di ruangan.
            1. Transkripsikan kata/kalimat yang terdengar ke field `transcript`.
            2. PERIKSA apakah pengguna mengucapkan kata panggilan "Petto", "Hey Petto", "Halo Petto", "Hai Petto", "Peto", atau "${character.name}":
               a) Jika pengguna HANYA memanggil kata panggilan tanpa pertanyaan/kalimat lanjutan (misalnya cuma bilang "Petto" atau "Hey Petto"):
                  - Set `wakeDetected` = true
                  - Set `isWakeOnly` = true
                  - Set `replyText` = ""
               b) Jika pengguna memanggil kata panggilan DAN langsung menyambung dengan kalimat/pertanyaan dalam satu tarikan napas (misalnya "Hey Petto, besok hujan gak?"):
                  - Set `wakeDetected` = true
                  - Set `isWakeOnly` = false
                  - Isi `replyText` dengan jawaban lisan langsung dari ${character.name} yang menjawab inti ucapan pengguna secara mendalam, cerdas, alami (2-4 kalimat tanpa simbol markdown).
            3. JIKA rekaman audio TIDAK mengandung kata panggilan "Petto" atau "${character.name}" (misalnya suara obrolan orang lain, TV, batuk, atau bising ruangan):
               - Set `wakeDetected` = false, `isWakeOnly` = false, `replyText` = "".
            """.trimIndent()
        } else {
            """
            MODE SESI AKTIF / LANGSUNG TANGGAP:
            1. Transkripsikan seluruh kata/kalimat yang diucapkan pengguna secara akurat ke dalam field `transcript`.
            2. Jika pengguna HANYA memanggil satu kata "Petto", "Hey Petto", atau "${character.name}" tanpa kalimat lanjutan:
               - Set `wakeDetected` = true
               - Set `isWakeOnly` = true
               - Set `transcript` = ucapan panggilan pengguna, dan `replyText` = "".
            3. Jika pengguna mengucapkan kalimat, pertanyaan, sapaan, atau pernyataan apa pun (baik diawali kata "Petto"/"${character.name}" maupun langsung berbicara tanpa kata panggilan):
               - Set `wakeDetected` = true
               - Set `isWakeOnly` = false
               - Isi `replyText` dengan jawaban lisan langsung dari ${character.name} yang menjawab inti ucapan pengguna secara mendalam, cerdas, alami, dan sesuai kepribadian ${character.name} (2-4 kalimat percakapan lisan tanpa simbol markdown).
            4. HANYA jika rekaman audio benar-benar kosong/hening, batuk, atau bising statis tanpa ucapan manusia:
               - Set `wakeDetected` = false, `isWakeOnly` = false, `transcript` = "", `replyText` = "".
            """.trimIndent()
        }

        val schemaPrompt = """
            $systemInstruction
            $visionContext

            TUGAS PEMROSESAN SUARA LANGSUNG (GOOGLE GEMINI LIVE MULTIMODAL AUDIO):
            Dengarkan rekaman suara mikrofon pengguna yang dilampirkan (audio/wav) dengan sangat teliti.

            $wakeRulesBlock

            Riwayat Percakapan Sebelumnya:
            $recentContext
        """.trimIndent()

        val rawJsonPrompt = """
            $schemaPrompt

            Kembalikan HANYA objek JSON valid dengan kunci:
            "wakeDetected" (boolean), "isWakeOnly" (boolean), "transcript" (string), "replyText" (string), "emotion" ("HAPPY"|"LOVING"|"SURPRISED"|"SLEEPY"|"TALKING").
        """.trimIndent()

        var anyModelRespondedWithoutError = false

        // 1. Try Firebase AI Logic Multimodal Audio across 3.8, 3.5, 2.5, and 2.0 models
        try {
            val rawAttempts = listOf(
                Triple(voiceTurnJsonModel, "gemini-3.8-flash", schemaPrompt),
                Triple(voiceTurnJsonLiteModel, "gemini-3.5-flash-lite", schemaPrompt),
                Triple(voiceTurnJson25Model, "gemini-2.5-flash", schemaPrompt),
                Triple(voiceTurnJson25LiteModel, "gemini-2.5-flash-lite", schemaPrompt),
                Triple(jsonFlashModel, "gemini-3.8-flash", rawJsonPrompt),
                Triple(jsonFlash25Model, "gemini-2.5-flash", rawJsonPrompt),
                Triple(flashModel, "gemini-3.8-flash", rawJsonPrompt),
                Triple(flashLiteModel, "gemini-3.5-flash-lite", rawJsonPrompt),
                Triple(flash25Model, "gemini-2.5-flash", rawJsonPrompt),
                Triple(flash25LiteModel, "gemini-2.5-flash-lite", rawJsonPrompt),
                Triple(flash20Model, "gemini-2.0-flash", rawJsonPrompt)
            )
            val attempts = filterAvailableModels(rawAttempts) { it.second }

            val parsedFromFirebase = withTimeoutOrNull(60_000L) {
                var parsedResult: VoiceAudioAnalysisResult? = null
                for ((model, modelId, promptToUse) in attempts) {
                    try {
                        val candidate = withTimeoutOrNull(20_000L) {
                            val req = content {
                                if (cameraBitmap != null) {
                                    image(cameraBitmap)
                                }
                                inlineData(wavBytes, "audio/wav")
                                text(promptToUse)
                            }
                            model.generateContent(req).text
                        }
                        if (candidate != null) {
                            anyModelRespondedWithoutError = true
                            if (candidate.isNotBlank()) {
                                val parsed = parseVoiceAudioJson(candidate, character, strictWakeMode)
                                if (parsed != null) {
                                    parsedResult = parsed
                                    break
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        recordModelException(modelId, e)
                        Log.w(TAG, "Firebase AI voice turn attempt ($modelId) skipped: ${e.message}")
                    }
                }
                parsedResult
            }

            if (parsedFromFirebase != null) {
                lastAiCallSucceeded = true
                // If model transcribed user's sentence in active mode but left replyText empty, generate the reply directly
                if (parsedFromFirebase.wakeDetected &&
                    !parsedFromFirebase.isWakeOnly &&
                    parsedFromFirebase.replyText.isBlank() &&
                    parsedFromFirebase.transcript.isNotBlank()
                ) {
                    val generated = callFirebaseAi(
                        userMessage = parsedFromFirebase.transcript,
                        character = character,
                        language = language,
                        history = history,
                        cameraBitmap = cameraBitmap
                    )
                    if (generated != null && generated.first.isNotBlank()) {
                        return@withContext parsedFromFirebase.copy(
                            replyText = generated.first,
                            emotion = inferEmotionFromText(generated.first, character)
                        )
                    }
                }
                return@withContext parsedFromFirebase
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI audio analysis error: ${e.message}")
        }

        // 2. If custom/Secrets Gemini API Key is configured, also try Gemini REST API with multimodal inlineData (audio/wav)
        val apiKey = getEffectiveApiKey()
        if (apiKey.isNotBlank()) {
            try {
                val restModels = filterAvailableModels(
                    listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
                ) { it }
                val parsedFromRest = withTimeoutOrNull(60_000L) {
                    var parsed: VoiceAudioAnalysisResult? = null
                    for (m in restModels) {
                        val jsonResp = callGeminiAudioJson(m, apiKey, rawJsonPrompt, wavBytes)
                        if (jsonResp.isNotBlank()) {
                            anyModelRespondedWithoutError = true
                            val candidateParsed = parseVoiceAudioJson(jsonResp, character, strictWakeMode)
                            if (candidateParsed != null) {
                                parsed = candidateParsed
                                break
                            }
                        }
                    }
                    parsed
                }
                if (parsedFromRest != null) {
                    lastAiCallSucceeded = true
                    return@withContext parsedFromRest
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini audio REST analysis error: ${e.message}")
            }
        }

        // If at least one model responded cleanly (no network/API exception) with empty text, the audio was just silence/noise
        if (anyModelRespondedWithoutError) {
            lastAiCallSucceeded = true
            return@withContext VoiceAudioAnalysisResult(
                wakeDetected = false,
                isWakeOnly = false,
                transcript = "",
                replyText = "",
                emotion = AvatarEmotion.IDLE
            )
        }

        // 3. Transparent connection error if offline (never fake "(Suara pengguna)")
        val connErr = createAiConnectionErrorReply(character, "Multimodal audio unreachable")
        return@withContext VoiceAudioAnalysisResult(
            wakeDetected = !isStandbyWakeMode,
            isWakeOnly = false,
            transcript = "",
            replyText = if (isStandbyWakeMode) "" else connErr.text,
            emotion = AvatarEmotion.SURPRISED
        )
    }

    /**
     * Transcribes spoken audio (16kHz WAV) directly into text using Gemini Multimodal Audio
     * for Homework Question Dictation and Story Adventure Voice Choices.
     */
    suspend fun transcribeAudioOnly(
        wavBytes: ByteArray,
        language: AppLanguage = AppLanguage.INDONESIAN
    ): String = withContext(Dispatchers.IO) {
        val langLabel = when (language) {
            AppLanguage.INDONESIAN -> "Bahasa Indonesia"
            AppLanguage.ENGLISH -> "English"
            AppLanguage.JAPANESE -> "Japanese"
            AppLanguage.SPANISH -> "Spanish"
        }
        val prompt = """
            Dengarkan rekaman suara pengguna ini (audio/wav) dan tuliskan transkripsi kalimat yang diucapkan secara akurat dalam $langLabel.
            Kembalikan HANYA teks transkripsinya saja tanpa tanda kutip dan tanpa komentar tambahan. Jika benar-benar hening tanpa suara manusia, kembalikan string kosong.
        """.trimIndent()

        try {
            val text = withTimeoutOrNull(60_000L) {
                val req = content {
                    inlineData(wavBytes, "audio/wav")
                    text(prompt)
                }
                val rawModels = listOf(
                    flashModel to "gemini-3.8-flash",
                    flashLiteModel to "gemini-3.5-flash-lite",
                    flash25Model to "gemini-2.5-flash",
                    flash25LiteModel to "gemini-2.5-flash-lite",
                    flash20Model to "gemini-2.0-flash"
                )
                val models = filterAvailableModels(rawModels) { it.second }
                var result: String? = null
                for ((m, mId) in models) {
                    try {
                        val candidate = withTimeoutOrNull(18_000L) {
                            m.generateContent(req).text?.trim()
                        }
                        if (!candidate.isNullOrBlank()) {
                            result = candidate
                            break
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        recordModelException(mId, e)
                    }
                }
                result
            }
            if (!text.isNullOrBlank()) {
                lastAiCallSucceeded = true
                return@withContext text.replace(Regex("^\"|\"$"), "").trim()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "transcribeAudioOnly error: ${e.message}")
        }
        ""
    }

    internal fun parseVoiceAudioJson(
        rawJson: String,
        character: PetCharacter,
        strictStandbyWake: Boolean
    ): VoiceAudioAnalysisResult? {
        return try {
            val clean = extractJsonObjectString(rawJson)
            val obj = JSONObject(clean)
            val modelWakeDetected = obj.optBoolean("wakeDetected", false)
            val modelIsWakeOnly = obj.optBoolean("isWakeOnly", false)
            val transcript = obj.optString("transcript", "").trim()
            val replyText = obj.optString("replyText", "").trim()

            val escapedCharName = Regex.escape(character.name)
            val containsWakeRegex = Regex(
                """\b(?:petto|peto|pito|pet|pheto|$escapedCharName)\b""",
                RegexOption.IGNORE_CASE
            )
            val wakeOnlyRegex = Regex(
                """^(?:hey|hei|halo|hai|hi|oi|woi|ya|eh|kak|bang|bro|oke|ok)?\s*(?:petto|peto|pito|pet|pheto|$escapedCharName)[,.!?\s]*$""",
                RegexOption.IGNORE_CASE
            )

            val transcriptHasWake = transcript.isNotBlank() && containsWakeRegex.containsMatchIn(transcript)
            val transcriptIsWakeOnly = transcript.isNotBlank() && wakeOnlyRegex.matches(transcript)

            val finalWakeDetected = if (strictStandbyWake) {
                modelWakeDetected || transcriptHasWake
            } else {
                modelWakeDetected || transcript.isNotBlank() || replyText.isNotBlank()
            }

            if (!finalWakeDetected) {
                return VoiceAudioAnalysisResult(
                    wakeDetected = false,
                    isWakeOnly = false,
                    transcript = transcript,
                    replyText = "",
                    emotion = AvatarEmotion.IDLE
                )
            }

            if (transcript.isNotBlank()) {
                memoryManager?.extractAndSaveFromUtterance(transcript)
            }

            val finalIsWakeOnly = modelIsWakeOnly || transcriptIsWakeOnly
            if (finalIsWakeOnly) {
                return VoiceAudioAnalysisResult(
                    wakeDetected = true,
                    isWakeOnly = true,
                    transcript = transcript.ifBlank { "Petto" },
                    replyText = "",
                    emotion = AvatarEmotion.SURPRISED
                )
            }

            val emotionStr = obj.optString("emotion", "").uppercase()
            val emotion = when (emotionStr) {
                "HAPPY" -> AvatarEmotion.HAPPY
                "LOVING" -> AvatarEmotion.LOVING
                "SURPRISED" -> AvatarEmotion.SURPRISED
                "SLEEPY" -> AvatarEmotion.SLEEPY
                else -> inferEmotionFromText(replyText, character)
            }

            VoiceAudioAnalysisResult(
                wakeDetected = true,
                isWakeOnly = false,
                transcript = transcript,
                replyText = replyText,
                emotion = emotion
            )
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse voice audio JSON: ${e.message}")
            if (!strictStandbyWake) {
                val plainFallback = rawJson
                    .replace(Regex("```json|```", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("^\"|\"$"), "")
                    .trim()
                if (plainFallback.length >= 2 && !plainFallback.startsWith("{")) {
                    return VoiceAudioAnalysisResult(
                        wakeDetected = true,
                        isWakeOnly = false,
                        transcript = "",
                        replyText = plainFallback,
                        emotion = inferEmotionFromText(plainFallback, character)
                    )
                }
            }
            null
        }
    }

    private fun callGeminiAudioJson(
        modelName: String,
        apiKey: String,
        prompt: String,
        wavBytes: ByteArray
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "audio/wav")
                                put("data", base64Audio)
                            })
                        })
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.4)
                put("maxOutputTokens", 450)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) unavailableModels.add(modelName)
                return ""
            }
            val respString = response.body?.string() ?: return ""
            val respJson = JSONObject(respString)
            val candidates = respJson.optJSONArray("candidates") ?: return ""
            if (candidates.length() > 0) {
                val first = candidates.getJSONObject(0)
                val content = first.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text").trim()
                }
            }
        }
        return ""
    }

    suspend fun generateHomeworkHelp(
        subject: String,
        question: String,
        character: PetCharacter,
        language: AppLanguage,
        photoBitmap: Bitmap? = null
    ): HomeworkSolution = withContext(Dispatchers.IO) {
        val actualQuestion = question.trim().ifBlank {
            if (photoBitmap != null) "Tolong analisis dan jelaskan penyelesaian soal atau materi yang ada pada foto ini"
            else "Pembahasan materi $subject"
        }

        val schemaPrompt = """
            Kamu adalah ${character.name} (${character.species.displayName}), teman diskusi dan tutor AI yang sangat cerdas, berwawasan luas, dan berempati tinggi.
            Kepribadian dan gaya bicaramu:
            ${character.personalityPrompt}

            Kategori / Mata Pelajaran yang dipilih pengguna: $subject
            Pertanyaan / Topik dari pengguna: "$actualQuestion"
            ${if (photoBitmap != null) "PENTING: Periksa foto/gambar yang dilampirkan dengan teliti. Baca teks soal, angka, diagram, atau materi di dalam gambar tersebut dan selesaikan secara akurat." else ""}

            ATURAN UTAMA (WAJIB DIIKUTI):
            1. LANGSUNG JAWAB INTI PERTANYAAN SECARA MENDALAM, ALAMI, DAN SESUAI KONTEKS!
            2. DILARANG KERAS memberikan jawaban template/meta seperti "Langkah pertama adalah memahami pengertian dasar dari..." atau sekadar mengulang kalimat pertanyaan pengguna tanpa menjawab isinya.
            3. Sesuaikan pembahasan dengan jenis pertanyaan:
               - Jika pertanyaan berupa logika kehidupan, psikologi, filsafat, sosial, atau pertanyaan terbuka (contoh: "kenapa setiap orang punya masalah", "mengapa manusia bisa lupa", dll.):
                 * Isi `conceptOrFormula` dengan INTI JAWABAN LANGSUNG yang tajam dan bermakna (misalnya alasan psikologis, biologis, atau filosofis utamanya).
                 * Isi `stepByStep` dengan 3-4 poin penjelasan mendalam dari berbagai sudut pandang nyata (misalnya sudut pandang psikologi/kebutuhan manusia, sudut pandang proses pendewasaan diri, dinamika lingkungan sosial, dan cara menyikapinya secara bijak).
                 * Isi `finalAnswer` dengan kesimpulan yang menyentuh, cerdas, dan menjawab tuntas pertanyaan tersebut dengan gaya khas ${character.name}.
               - Jika pertanyaan berupa soal hitungan (Matematika, Fisika, Kimia, Ekonomi) atau fakta sains/sejarah:
                 * Isi `conceptOrFormula` dengan rumus, hukum ilmiah, atau konsep dasar yang tepat.
                 * Isi `stepByStep` dengan langkah perhitungan angka atau uraian fakta secara konkret sampai ketemu hasilnya.
                 * Isi `finalAnswer` dengan hasil akhir yang pasti dan jelas.
            4. Gunakan Bahasa Indonesia yang hidup, cerdas, mengalir alami, dan sesuai karakter ${character.name}.
        """.trimIndent()

        val rawJsonPrompt = """
            $schemaPrompt

            Kembalikan HANYA objek JSON valid dengan kunci berikut (tanpa markdown backtick):
            - "subject": string
            - "questionSummary": string
            - "conceptOrFormula": string
            - "stepByStep": array berisi 3 sampai 5 string penjelasan mendalam
            - "finalAnswer": string
            - "tutorTip": string
            - "practiceChallenge": string
        """.trimIndent()

        var lastError = ""

        // 1. Try Firebase AI Logic first (provisioned backend with Schema models + general models across 3.8/3.5/4 and 2.5/2.0)
        try {
            val rawAttempts = listOf(
                Triple(homeworkJsonModel, "gemini-3.8-flash", schemaPrompt),
                Triple(homeworkJsonLiteModel, "gemini-3.5-flash-lite", schemaPrompt),
                Triple(homeworkJson25Model, "gemini-2.5-flash", schemaPrompt),
                Triple(homeworkJson25LiteModel, "gemini-2.5-flash-lite", schemaPrompt),
                Triple(jsonFlashModel, "gemini-3.8-flash", rawJsonPrompt),
                Triple(jsonFlash25Model, "gemini-2.5-flash", rawJsonPrompt),
                Triple(argonModel, "gemini-4-argon", rawJsonPrompt),
                Triple(flashModel, "gemini-3.8-flash", rawJsonPrompt),
                Triple(flashLiteModel, "gemini-3.5-flash-lite", rawJsonPrompt),
                Triple(flash25Model, "gemini-2.5-flash", rawJsonPrompt),
                Triple(flash25LiteModel, "gemini-2.5-flash-lite", rawJsonPrompt),
                Triple(flash20Model, "gemini-2.0-flash", rawJsonPrompt)
            )
            val attempts = filterAvailableModels(rawAttempts) { it.second }

            val parsedSolution = withTimeoutOrNull(60_000L) {
                var solved: HomeworkSolution? = null
                for ((model, modelId, promptToUse) in attempts) {
                    try {
                        val candidate = withTimeoutOrNull(22_000L) {
                            if (photoBitmap != null) {
                                model.generateContent(
                                    content {
                                        image(photoBitmap)
                                        text(promptToUse)
                                    }
                                ).text
                            } else {
                                model.generateContent(promptToUse).text
                            }
                        }
                        if (!candidate.isNullOrBlank()) {
                            val parsed = parseHomeworkJson(candidate, subject, actualQuestion, character, isFromAi = true)
                            if (parsed != null) {
                                solved = parsed
                                break
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        recordModelException(modelId, e)
                        lastError = e.message.orEmpty()
                        Log.w(TAG, "Firebase AI homework attempt ($modelId) skipped: ${e.message}")
                    }
                }
                solved
            }
            if (parsedSolution != null) {
                lastAiCallSucceeded = true
                return@withContext parsedSolution
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e.message.orEmpty()
            Log.w(TAG, "Firebase AI homework help error: ${e.message}")
        }

        // 2. If a custom/Secrets Gemini API Key is configured, also try Direct Gemini REST API
        val apiKey = getEffectiveApiKey()
        if (apiKey.isNotBlank()) {
            try {
                val restModels = filterAvailableModels(
                    listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
                ) { it }
                val solvedRest = withTimeoutOrNull(60_000L) {
                    var solved: HomeworkSolution? = null
                    for (m in restModels) {
                        val jsonResponse = callGeminiJson(m, apiKey, rawJsonPrompt, photoBitmap)
                        if (jsonResponse.isNotBlank()) {
                            val parsed = parseHomeworkJson(jsonResponse, subject, actualQuestion, character, isFromAi = true)
                            if (parsed != null) {
                                solved = parsed
                                break
                            }
                        }
                    }
                    solved
                }

                if (solvedRest != null) {
                    lastAiCallSucceeded = true
                    return@withContext solvedRest
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message.orEmpty()
                Log.w(TAG, "Gemini REST homework help error: ${e.message}")
            }
        }

        return@withContext createHomeworkConnectionErrorSolution(subject, actualQuestion, character, lastError)
    }

    private fun parseHomeworkJson(
        rawJson: String,
        subject: String,
        actualQuestion: String,
        character: PetCharacter,
        isFromAi: Boolean
    ): HomeworkSolution? {
        try {
            val cleanJson = extractJsonObjectString(rawJson)
            val obj = JSONObject(cleanJson)
            val stepsList = mutableListOf<String>()
            val stepsArray = obj.optJSONArray("stepByStep")
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    val step = stepsArray.optString(i).trim()
                    if (step.isNotBlank()) stepsList.add(step)
                }
            }
            val finalAns = obj.optString("finalAnswer", "").trim()
            val concept = obj.optString("conceptOrFormula", "").trim()
            if (stepsList.isEmpty() && finalAns.isNotBlank()) {
                stepsList.addAll(
                    finalAns.split(Regex("(?<=\\.)\\s+")).map { it.trim() }.filter { it.isNotBlank() }.take(4)
                )
            }
            if (stepsList.isNotEmpty() || finalAns.isNotBlank()) {
                return HomeworkSolution(
                    subject = obj.optString("subject", subject).ifBlank { subject },
                    questionSummary = obj.optString("questionSummary", actualQuestion).ifBlank { actualQuestion },
                    stepByStep = if (stepsList.isNotEmpty()) stepsList else listOf(finalAns),
                    finalAnswer = finalAns.ifBlank { stepsList.lastOrNull() ?: "Solusi telah diselesaikan secara lengkap." },
                    tutorTip = obj.optString("tutorTip", "Tips dari ${character.name}: Pahami konsep dasarnya dan jangan ragu bertanya lagi ya!"),
                    conceptOrFormula = concept,
                    practiceChallenge = obj.optString("practiceChallenge", ""),
                    isFromAi = isFromAi
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "JSON parse fallback to structured text extraction: ${e.message}")
        }

        // Resilient plain-text fallback: if Gemini returned text/markdown that wasn't strict JSON,
        // convert the AI paragraphs directly into a HomeworkSolution so we NEVER discard an AI answer!
        val cleanText = rawJson
            .replace(Regex("```json|```", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleanText.length >= 20) {
            val lines = cleanText
                .lines()
                .map { it.trim().trimStart('-', '*', '•') }
                .filter { it.length > 4 && !it.startsWith("{") && !it.startsWith("}") }
            if (lines.isNotEmpty()) {
                val steps = lines.take(4)
                val summaryAnswer = lines.lastOrNull() ?: cleanText.take(220)
                return HomeworkSolution(
                    subject = subject,
                    questionSummary = actualQuestion,
                    conceptOrFormula = "Analisis Konsep $subject oleh ${character.name}",
                    stepByStep = steps,
                    finalAnswer = summaryAnswer,
                    tutorTip = "Tips dari ${character.name}: Kalau ada bagian yang ingin dibahas lebih dalam, langsung tanyakan saja ya!",
                    practiceChallenge = "",
                    isFromAi = isFromAi
                )
            }
        }
        return null
    }

    private fun extractJsonObjectString(raw: String): String {
        val trimmed = raw.replace(Regex("^```json|^```|```$", RegexOption.MULTILINE), "").trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace in 0 until lastBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed
    }

    /**
     * Generates an interactive AI Quiz Question (Kuis Suara & Cerdas Cermat) via Gemini AI.
     */
    suspend fun generateQuizQuestion(
        topic: String,
        character: PetCharacter
    ): QuizQuestion = withContext(Dispatchers.IO) {
        val prompt = """
            Kamu adalah ${character.name} (${character.species.displayName}). Buatlah 1 pertanyaan kuis pilihan ganda yang seru, cerdas, dan mendidik tentang topik "$topic" dalam Bahasa Indonesia.
            Gaya penjelasan pembahasan harus sesuai kepribadianmu: ${character.personalityPrompt}
            
            Kembalikan HANYA JSON valid dengan format berikut:
            {
              "topic": "$topic",
              "question": "Pertanyaan kuis yang menarik?",
              "options": ["Pilihan A", "Pilihan B", "Pilihan C", "Pilihan D"],
              "correctIndex": 0,
              "explanation": "Penjelasan singkat mengapa jawaban tersebut benar dengan gaya bicara khas ${character.name}"
            }
        """.trimIndent()

        val rawJson = callAnyGeminiJson(prompt)
        if (!rawJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(extractJsonObjectString(rawJson))
                val optsArr = obj.optJSONArray("options")
                val options = mutableListOf<String>()
                if (optsArr != null) {
                    for (i in 0 until optsArr.length()) {
                        options.add(optsArr.optString(i))
                    }
                }
                if (options.size >= 4) {
                    return@withContext QuizQuestion(
                        topic = obj.optString("topic", topic),
                        question = obj.optString("question", ""),
                        options = options.take(4),
                        correctIndex = obj.optInt("correctIndex", 0).coerceIn(0, 3),
                        explanation = obj.optString("explanation", "Jawaban yang tepat sekali!"),
                        isFromAi = true
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to parse AI quiz JSON: ${e.message}")
            }
        }

        // Offline curated quiz bank fallback
        val fallbackPool = listOf(
            QuizQuestion(
                topic = "Sains & Antariksa",
                question = "Planet manakah di Tata Surya kita yang dikenal memiliki cincin es paling besar dan indah?",
                options = listOf("Mars", "Saturnus", "Venus", "Merkurius"),
                correctIndex = 1,
                explanation = "Saturnus punya sistem cincin raksasa yang terbentuk dari miliaran bongkahan es dan batuan angkasa!",
                isFromAi = false
            ),
            QuizQuestion(
                topic = "Pengetahuan Umum",
                question = "Hewan mamalia laut apakah yang merupakan hewan terbesar yang pernah hidup di Bumi?",
                options = listOf("Hiu Putih Besar", "Paus Biru", "Lumba-lumba Hidung Botol", "Gajah Afrika"),
                correctIndex = 1,
                explanation = "Paus Biru bisa tumbuh sepanjang 30 meter dan beratnya mencapai lebih dari 150 ton!",
                isFromAi = false
            ),
            QuizQuestion(
                topic = "Logika & Matematika",
                question = "Jika sebuah kereta listrik bergerak ke arah Utara dengan kecepatan 80 km/jam dan angin bertiup ke Selatan, ke manakah arah asap kereta tersebut?",
                options = listOf("Ke Utara", "Ke Selatan", "Ke Timur", "Tidak mengeluarkan asap"),
                correctIndex = 3,
                explanation = "Hehe, namanya juga kereta listrik! Kereta listrik tidak mengeluarkan asap sama sekali!",
                isFromAi = false
            ),
            QuizQuestion(
                topic = "Alam & Biologi",
                question = "Gas apakah yang diserap oleh tumbuhan hijau dari udara saat melakukan proses fotosintesis di siang hari?",
                options = listOf("Oksigen", "Karbondioksida", "Helium", "Hidrogen"),
                correctIndex = 1,
                explanation = "Tumbuhan menyerap Karbondioksida (CO2) dan melepaskan Oksigen (O2) yang segar untuk kita hirup!",
                isFromAi = false
            )
        )
        return@withContext fallbackPool.random()
    }

    /**
     * Generates an interactive branching story chapter (Mode Dongeng & Cerita Petualangan Interaktif) via Gemini AI,
     * supporting custom user actions, magical inventory items, chapter progression, and dynamic endings.
     */
    suspend fun generateStoryNode(
        theme: StoryTheme,
        chapter: Int,
        maxChapters: Int = 5,
        previousStory: String,
        chosenOption: String?,
        inventory: List<StoryItem> = emptyList(),
        character: PetCharacter,
        userNickname: String = ""
    ): StoryNode = withContext(Dispatchers.IO) {
        val isEndingChapter = chapter >= maxChapters
        val inventorySummary = if (inventory.isEmpty()) {
            "Belum ada item di tas ajaib"
        } else {
            inventory.joinToString(", ") { "${it.emoji} ${it.name}" }
        }
        val playerCallName = if (userNickname.isNotBlank()) userNickname else "Sahabatku"

        val prompt = """
            Kamu adalah pendongeng 3D interaktif bernama ${character.name} (${character.species.displayName}) dengan sifat: ${character.personalityPrompt}.
            Buatlah Babak ke-$chapter dari $maxChapters untuk mode "Dongeng Petualangan" bertema "${theme.title}" (${theme.promptHint}).
            Tokoh utama petualangan ini adalah kamu (${character.name}) dan pengguna bernama "$playerCallName".
            Tas Inventaris Ajaib saat ini: [$inventorySummary].
            ${if (previousStory.isNotBlank()) "\nRingkasan babak sebelumnya:\n${previousStory.takeLast(1400)}" else ""}
            ${if (!chosenOption.isNullOrBlank()) "\nKeputusan / Aksi yang baru saja dilakukan oleh $playerCallName: \"$chosenOption\". Pastikan narasi babak ini merespons langsung keputusan atau penggunaan item tersebut secara seru dan dramatis!" else ""}
            ${if (isEndingChapter) "\nIni adalah Babak Klimaks / Akhir ($chapter dari $maxChapters). Berikan puncak cerita dan akhir yang sangat memuaskan sesuai keputusan pemain! Namun tetap berikan choiceA dan choiceB jika pemain ingin melanjutkan ke Babak Epilog Tambahan." else "\nDi akhir babak ini, hadirkan situasi menegangkan/unik dan berikan 2 pilihan tindakan berbeda (choiceA dan choiceB) untuk menentukan alur selanjutnya."}
            
            Aturan Penting:
            1. Gunakan bahasa Indonesia yang ekspresif, imajinatif, dan enak dibacakan dengan suara oleh ${character.name} (3-4 kalimat hidup).
            2. Sertakan "petReactionComment" (1 kalimat singkat komentar spontan ${character.name} sesuai sifatnya di atas panggung).
            3. Sertakan "sceneEmotion" dengan memilih salah satu dari: HAPPY, SURPRISED, THINKING, LOVING, SLEEPY, ANGRY.
            4. Pada setiap babak (terutama babak 1-4), berikan 1 item ajaib baru atau petunjuk unik di dalam "newItem" (nama pendek, 1 emoji, dan deskripsi singkat kegunaannya) yang ditemukan dalam cerita.
            5. Jika isEnding = true, isi "endingTitle" (judul akhir yang keren) dan "endingType" dengan salah satu dari: HEROIC_LEGEND, SECRET_DISCOVERY, FUNNY_TWIST, COZY_PEACEFUL.
            
            Kembalikan HANYA JSON valid dengan format persis berikut:
            {
              "chapterNumber": $chapter,
              "title": "Judul Babak $chapter",
              "narrative": "Narasi cerita babak ini (3-4 kalimat menarik yang langsung melibatkan $playerCallName dan ${character.name})...",
              "petReactionComment": "Komentar singkat ekspresif dari ${character.name}...",
              "sceneEmotion": "HAPPY",
              "choiceA": "Tindakan pilihan A (maks 8 kata)",
              "choiceB": "Tindakan pilihan B (maks 8 kata)",
              "newItem": {
                "name": "Nama Item Ajaib",
                "emoji": "🗝️",
                "description": "Kegunaan singkat item di petualangan"
              },
              "isEnding": $isEndingChapter,
              "endingTitle": "${if (isEndingChapter) "Gelar Akhir Petualangan" else ""}",
              "endingType": "HEROIC_LEGEND"
            }
        """.trimIndent()

        val rawJson = callAnyGeminiJson(prompt)
        if (!rawJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(extractJsonObjectString(rawJson))
                val narrative = obj.optString("narrative", "").trim()
                if (narrative.isNotBlank()) {
                    val emotionStr = obj.optString("sceneEmotion", "HAPPY").uppercase()
                    val emotion = AvatarEmotion.entries.find { it.name == emotionStr } ?: AvatarEmotion.HAPPY

                    val endingTypeStr = obj.optString("endingType", "HEROIC_LEGEND").uppercase()
                    val endingType = StoryEndingType.entries.find { it.name == endingTypeStr }
                        ?: StoryEndingType.HEROIC_LEGEND

                    val itemObj = obj.optJSONObject("newItem")
                    val acquiredItem = if (itemObj != null) {
                        val itemName = itemObj.optString("name", "").trim()
                        if (itemName.isNotBlank() && inventory.none { it.name.equals(itemName, ignoreCase = true) }) {
                            StoryItem(
                                id = "${theme.name.lowercase()}_${chapter}_${itemName.lowercase().replace(" ", "_")}",
                                name = itemName,
                                emoji = itemObj.optString("emoji", "✨").take(4).ifBlank { "✨" },
                                description = itemObj.optString("description", "Ditemukan di Babak $chapter"),
                                chapterAcquired = chapter
                            )
                        } else null
                    } else null

                    return@withContext StoryNode(
                        chapterNumber = chapter,
                        maxChapters = maxChapters,
                        title = obj.optString("title", "Babak $chapter: ${theme.title}"),
                        narrative = narrative,
                        petReactionComment = obj.optString(
                            "petReactionComment",
                            "Wah, pilihanmu bikin petualangan kita makin seru!"
                        ),
                        sceneEmotion = emotion,
                        choiceA = obj.optString("choiceA", "Telusuri jalur bercahaya di depan"),
                        choiceB = obj.optString("choiceB", "Periksa petunjuk kuno bersama ${character.name}"),
                        acquiredItem = acquiredItem,
                        isEnding = obj.optBoolean("isEnding", isEndingChapter),
                        endingTitle = obj.optString(
                            "endingTitle",
                            if (isEndingChapter) "Penjelajah Legendaris ${theme.title}" else ""
                        ),
                        endingType = endingType,
                        isFromAi = true
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to parse AI story JSON: ${e.message}")
            }
        }

        // Rich theme-aware multi-chapter fallback if offline
        return@withContext buildOfflineStoryNode(
            theme = theme,
            chapter = chapter,
            maxChapters = maxChapters,
            chosenOption = chosenOption,
            character = character,
            playerCallName = playerCallName
        )
    }

    private fun buildOfflineStoryNode(
        theme: StoryTheme,
        chapter: Int,
        maxChapters: Int,
        chosenOption: String?,
        character: PetCharacter,
        playerCallName: String
    ): StoryNode {
        val isEnding = chapter >= maxChapters
        return when (chapter) {
            1 -> StoryNode(
                chapterNumber = 1,
                maxChapters = maxChapters,
                title = "Babak 1: Gerbang ${theme.title}",
                narrative = "Angin sejuk berhembus saat $playerCallName dan ${character.name} melangkah masuk ke wilayah ${theme.title}. Di hadapan kalian, sebuah prasasti bercahaya bergetar pelan sambil memancarkan peta rahasia menuju pusat keajaiban!",
                petReactionComment = "Lihat itu $playerCallName! Aku menemukan sesuatu yang berkilau di dekat prasasti!",
                sceneEmotion = AvatarEmotion.SURPRISED,
                choiceA = "Sentuh simbol bintang di prasasti kuno",
                choiceB = "Telusuri jembatan rahasia di sebelah kanan",
                acquiredItem = StoryItem(
                    id = "item_${theme.name}_1",
                    name = "Kompas Kristal Bintang",
                    emoji = "🧭",
                    description = "Menunjuk arah menuju rahasia tersembunyi di ${theme.title}",
                    chapterAcquired = 1
                ),
                isEnding = false,
                isFromAi = false
            )
            2 -> StoryNode(
                chapterNumber = 2,
                maxChapters = maxChapters,
                title = "Babak 2: Teka-Teki Sang Penjaga",
                narrative = "Keputusanmu untuk '${chosenOption ?: "melangkah maju"}' membawa $playerCallName dan ${character.name} ke sebuah aula megah. Seekor penjaga kuno muncul sambil tersenyum ramah dan menyerahkan lentera perak yang menyala hangat.",
                petReactionComment = "Wah, untung ada kamu $playerCallName! Kita pilih jalur mana sekarang?",
                sceneEmotion = AvatarEmotion.SURPRISED,
                choiceA = "Gunakan cahaya lentera untuk membuka pintu emas",
                choiceB = "Ajak sang penjaga berdiskusi tentang jalan pintas",
                acquiredItem = StoryItem(
                    id = "item_${theme.name}_2",
                    name = "Lentera Cahaya Perak",
                    emoji = "🏮",
                    description = "Menerangi lorong gelap dan mengungkap tulisan kuno",
                    chapterAcquired = 2
                ),
                isEnding = false,
                isFromAi = false
            )
            3 -> StoryNode(
                chapterNumber = 3,
                maxChapters = maxChapters,
                title = "Babak 3: Lorong Gema Ajaib",
                narrative = "Setelah '${chosenOption ?: "memilih jalan tersebut"}', dinding kristal di sekitar $playerCallName dan ${character.name} mulai bernyanyi merdu. Di tengah ruangan tampak peti permata yang terkunci rapat oleh segel teka-teki.",
                petReactionComment = "Telingaku sampai bergoyang mendengar melodi ajaib ini!",
                sceneEmotion = AvatarEmotion.HAPPY,
                choiceA = "Tirukan nada melodi kristal bersama ${character.name}",
                choiceB = "Buka segel peti menggunakan item di tas kita",
                acquiredItem = StoryItem(
                    id = "item_${theme.name}_3",
                    name = "Kunci Melodi Zamrud",
                    emoji = "🗝️",
                    description = "Kunci yang beresonansi dengan suara persahabatan",
                    chapterAcquired = 3
                ),
                isEnding = false,
                isFromAi = false
            )
            4 -> StoryNode(
                chapterNumber = 4,
                maxChapters = maxChapters,
                title = "Babak 4: Ambang Puncak Rahasia",
                narrative = "Berkat tindakan '${chosenOption ?: "cerdikmu"}', gerbang raksasa menuju inti ${theme.title} terbuka lebar! Cahaya keemasan menyambut $playerCallName dan ${character.name}, menyisakan satu keputusan terakhir sebelum rahasia utama terungkap.",
                petReactionComment = "Sebentar lagi kita sampai di puncak petualangan kita!",
                sceneEmotion = AvatarEmotion.SURPRISED,
                choiceA = "Melangkah bersama ke mahkota cahaya utama",
                choiceB = "Periksa altar bintang rahasia di sudut ruangan",
                acquiredItem = StoryItem(
                    id = "item_${theme.name}_4",
                    name = "Permata Harmoni",
                    emoji = "💎",
                    description = "Lambang keberanian dan kekompakan bersama ${character.name}",
                    chapterAcquired = 4
                ),
                isEnding = false,
                isFromAi = false
            )
            else -> StoryNode(
                chapterNumber = chapter,
                maxChapters = maxChapters,
                title = "Babak Klimaks: Legenda ${theme.title}!",
                narrative = "Pilihanmu untuk '${chosenOption ?: "melangkah dengan berani"}' mengaktifkan keajaiban tertinggi di ${theme.title}! Seluruh penjuru dunia dongeng bersorak merayakan keberhasilan $playerCallName dan ${character.name} sebagai sahabat petualang sejati.",
                petReactionComment = "Horeee! Petualangan kita luar biasa banget, $playerCallName!",
                sceneEmotion = AvatarEmotion.LOVING,
                choiceA = "Lanjutkan ke Babak Epilog Baru",
                choiceB = "Jelajahi Rahasia Tersembunyi Lainnya",
                acquiredItem = if (chapter == maxChapters) StoryItem(
                    id = "item_${theme.name}_crown",
                    name = "Medali Penjelajah Legendaris",
                    emoji = "🏅",
                    description = "Anugerah tertinggi karena menamatkan ${theme.title}",
                    chapterAcquired = chapter
                ) else null,
                isEnding = isEnding,
                endingTitle = "Sahabat Penjelajah Legendaris ${theme.title}",
                endingType = StoryEndingType.HEROIC_LEGEND,
                isFromAi = false
            )
        }
    }

    /**
     * Generates an empathetic, character-personalized AI response and actionable self-care tip
     * for the user's daily Mood Journal check-in using Gemini AI.
     */
    suspend fun generateMoodJournalReflection(
        mood: MoodType,
        userNote: String,
        character: PetCharacter,
        userNickname: String = ""
    ): MoodReflectionResult = withContext(Dispatchers.IO) {
        val callName = userNickname.ifBlank { "Sahabat" }
        val cleanNote = userNote.trim().ifBlank { "Aku sedang merasa ${mood.label.lowercase()} hari ini." }

        // Automatically record mood context into long-term memory so Petto remembers during chat/calls
        memoryManager?.addOrUpdateFact(
            "Suasana Hati Terakhir",
            "User baru saja merasa ${mood.label} (${mood.emoji}): ${cleanNote.take(90)}"
        )

        val prompt = """
            Kamu adalah ${character.name} (${character.species.displayName}), sahabat 3D virtual dengan sifat: ${character.personalityPrompt}
            Sahabatmu ($callName) baru saja menulis Jurnal Harian & Check-In Mood:
            - Suasana Hati: ${mood.emoji} ${mood.label} (${mood.promptDescription})
            - Catatan Jurnal: "$cleanNote"
            
            Kembalikan HANYA JSON valid dengan format berikut:
            {
              "reply": "Tanggapan empatik, hangat, dan penuh perhatian dalam 2-3 kalimat menggunakan gaya bicara khas ${character.name} yang menyapa $callName secara langsung",
              "tip": "1 saran aktivitas sederhana & positif hari ini yang relevan dengan suasana hatinya (1 kalimat singkat)",
              "emotion": "HAPPY | LOVING | TALKING | SURPRISED | SLEEPY"
            }
        """.trimIndent()

        val rawJson = callAnyGeminiJson(prompt)
        if (!rawJson.isNullOrBlank()) {
            try {
                val cleaned = rawJson
                    .replace("```json", "", ignoreCase = true)
                    .replace("```", "")
                    .trim()
                val startIdx = cleaned.indexOf('{')
                val endIdx = cleaned.lastIndexOf('}')
                val jsonSlice = if (startIdx >= 0 && endIdx > startIdx) {
                    cleaned.substring(startIdx, endIdx + 1)
                } else {
                    cleaned
                }
                val obj = JSONObject(jsonSlice)
                val reply = obj.optString("reply", "").trim()
                val tip = obj.optString("tip", "").trim()
                val emoStr = obj.optString("emotion", mood.avatarEmotion.name).uppercase()
                val parsedEmotion = try {
                    AvatarEmotion.valueOf(emoStr)
                } catch (_: Exception) {
                    mood.avatarEmotion
                }
                if (reply.isNotBlank()) {
                    return@withContext MoodReflectionResult(
                        aiReply = reply,
                        suggestedActivityTip = tip.ifBlank { "Minum segelas air hangat dan tarik napas dalam bersama ${character.name}." },
                        emotion = parsedEmotion,
                        isFromAi = true
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse mood reflection JSON: ${e.message}")
            }
        }

        // Rich personality-specific empathetic fallback
        val fallbackReply = when (character.id) {
            "zayn" -> when (mood) {
                MoodType.HAPPY, MoodType.EXCITED -> "Cih! Kelihatan banget mukamu senyum-senyum terus, $callName! B-bukan berarti aku ikut seneng ya liat kamu bahagia hari ini!"
                MoodType.CALM -> "Hmph, bagus deh kalau hari ini kamu ngerasa tenang, $callName. Sesekali santai bareng aku gini lumayan juga."
                else -> "H-heh $callName! Jangan sedih atau kecapekan sendirian dong! Aku kan di sini terus nemenin kamu... b-bukan berarti aku khawatir banget ya!"
            }
            "siti" -> when (mood) {
                MoodType.HAPPY, MoodType.EXCITED -> "Alhamdulillah sayangku $callName, Siti ikut bahagia sekali mendengar harimu yang penuh keceriaan! Semoga senyummu terus bersinar ya."
                MoodType.CALM -> "Syukurlah hatimu terasa damai dan tenang hari ini manis. Siti senang sekali bisa menemanimu bersantai."
                else -> "Sini peluk Siti dulu sayangku $callName. Tidak apa-apa kalau hari ini terasa berat atau melelahkan, Siti selalu ada untuk mendengarkanmu."
            }
            "rizky" -> when (mood) {
                MoodType.HAPPY, MoodType.EXCITED -> "Laporan mental positif diterima, Komandan $callName! Pertahankan semangat dan energi luar biasa ini!"
                MoodType.CALM -> "Kondisi emosional stabil dan terkendali! Ketenangan adalah kunci fokus terbaik kita hari ini!"
                else -> "Tetap tegar, Komandan $callName! Setiap pejuang hebat butuh waktu istirahat untuk memulihkan tenaga. Sersan Rizky siap mengawalmu!"
            }
            "jamal" -> when (mood) {
                MoodType.HAPPY, MoodType.EXCITED -> "Wih mantap jiwa bro $callName! Seneng liat lu hepi gini, bikin suasana rebahan Jamal makin asik."
                MoodType.CALM -> "Nah gitu dong bro $callName, santuy dan tenang emang kunci hidup bahagia ala kapibara."
                else -> "Duh bro $callName, kalau capek atau pusing jangan diporsir. Sini rebahan dulu bareng Jamal sambil dengerin musik Lo-Fi."
            }
            else -> when (mood) {
                MoodType.HAPPY, MoodType.EXCITED -> "HAHAHA MANTAP $callName! Gitu dong semangat terus, jangan kalah keren sama beruang kayak gua!"
                MoodType.CALM -> "Hmph, baguslah kalau lu lagi santai dan adem hari ini. Jangan lupa tetep minum air putih!"
                else -> "HEH $callName! Siapa yang bikin lu sedih atau capek hah?! Sini istirahat dulu di samping gua, jangan dipikirin sendirian!"
            }
        }

        val fallbackTip = when (mood) {
            MoodType.HAPPY, MoodType.EXCITED -> "Abadikan momen bahagiamu atau mainkan 1 babak Dongeng Petualangan bersama ${character.name}!"
            MoodType.CALM -> "Nikmati segelas teh hangat di Dapur atau nyalakan Radio Lo-Fi di Ruang Tamu."
            MoodType.TIRED -> "Minum 1 gelas air putih dan istirahatkan mata selama 20 detik dari layar."
            MoodType.SAD -> "Peluk boneka bintang di Kamar Tidur atau ngobrol langsung lewat Video Call bersama ${character.name}."
            MoodType.STRESSED -> "Lakukan latihan Napas Relaksasi dan Peregangan Badan bersama ${character.name}."
        }

        MoodReflectionResult(
            aiReply = fallbackReply,
            suggestedActivityTip = fallbackTip,
            emotion = mood.avatarEmotion,
            isFromAi = false
        )
    }

    /**
     * Generates a fresh roleplay sentence & pronunciation drill in English, Japanese, Arabic, Spanish, or Thai.
     */
    suspend fun generateLanguageTutorPrompt(
        language: TutorLanguage,
        scenario: TutorScenario,
        character: PetCharacter
    ): LanguageTutorPrompt? = withContext(Dispatchers.IO) {
        val schemaPrompt = """
            Kamu adalah ${character.name} (${character.species.displayName}), tutor bahasa asing pribadi yang cerdas dan suportif.
            Karaktermu: ${character.personalityPrompt}

            Tugas: Buat 1 kalimat latihan percakapan nyata untuk pemula-menengah dalam bahasa ${language.displayName} (${language.nativeLabel})
            untuk skenario "${scenario.title}" (${scenario.promptHint}).

            Aturan:
            1. `targetPhrase`: Tulis dalam aksara asli bahasa ${language.displayName} (Arab memakai harakat jika membantu, Jepang memakai Kanji/Hiragana wajar, Thailand memakai aksara Thai, Inggris/Spanyol dalam huruf Latin).
            2. `transliteration`: Cara baca fonetik/latin yang mudah diikuti pengguna Indonesia (misalnya untuk Inggris bisa panduan pengucapan ringan, untuk Jepang/Arab/Thai wajib romaji/latin yang akurat).
            3. `indonesianMeaning`: Arti alami kalimat tersebut dalam Bahasa Indonesia.
            4. `coachTip`: 1-2 kalimat tips pelafalan spesifik pada intonasi atau bunyi huruf sulit, dengan gaya bicara ${character.name}.
        """.trimIndent()

        val rawJsonPrompt = """
            $schemaPrompt

            Kembalikan HANYA JSON valid dengan kunci:
            "targetPhrase", "transliteration", "indonesianMeaning", "coachTip".
        """.trimIndent()

        val rawJson = try {
            withTimeoutOrNull(60_000L) {
                val rawModels = listOf(
                    Triple(tutorPromptJsonModel, "gemini-3.8-flash", schemaPrompt),
                    Triple(tutorPromptJson25Model, "gemini-2.5-flash", schemaPrompt),
                    Triple(jsonFlashModel, "gemini-3.8-flash", rawJsonPrompt),
                    Triple(jsonFlash25Model, "gemini-2.5-flash", rawJsonPrompt),
                    Triple(flashModel, "gemini-3.8-flash", rawJsonPrompt),
                    Triple(flashLiteModel, "gemini-3.5-flash-lite", rawJsonPrompt),
                    Triple(flash25Model, "gemini-2.5-flash", rawJsonPrompt),
                    Triple(flash25LiteModel, "gemini-2.5-flash-lite", rawJsonPrompt),
                    Triple(flash20Model, "gemini-2.0-flash", rawJsonPrompt)
                )
                val models = filterAvailableModels(rawModels) { it.second }
                var out: String? = null
                for ((m, mId, p) in models) {
                    try {
                        val candidate = withTimeoutOrNull(18_000L) {
                            m.generateContent(p).text
                        }
                        if (!candidate.isNullOrBlank()) {
                            out = candidate
                            break
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        recordModelException(mId, e)
                        Log.w(TAG, "Tutor prompt model attempt ($mId) skipped: ${e.message}")
                    }
                }
                out ?: callAnyGeminiJson(rawJsonPrompt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "generateLanguageTutorPrompt error: ${e.message}")
            null
        }

        if (!rawJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(extractJsonObjectString(rawJson))
                val targetPhrase = obj.optString("targetPhrase", "").trim()
                if (targetPhrase.isNotBlank()) {
                    lastAiCallSucceeded = true
                    return@withContext LanguageTutorPrompt(
                        language = language,
                        scenario = scenario,
                        targetPhrase = targetPhrase,
                        transliteration = obj.optString("transliteration", "").trim(),
                        indonesianMeaning = obj.optString("indonesianMeaning", "").trim(),
                        coachTip = obj.optString("coachTip", "").trim()
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tutor prompt JSON: ${e.message}")
            }
        }
        null
    }

    /**
     * Evaluates the user's spoken pronunciation (16kHz WAV audio) against the target phrase
     * using Gemini Multimodal Audio + generates a natural roleplay continuation.
     */
    suspend fun evaluatePronunciationAudio(
        wavBytes: ByteArray,
        targetPhrase: String,
        language: TutorLanguage,
        scenario: TutorScenario,
        character: PetCharacter
    ): PronunciationEvaluation = withContext(Dispatchers.IO) {
        val schemaPrompt = """
            Kamu adalah ${character.name} (${character.species.displayName}), pelatih pelafalan dan tutor bahasa ${language.displayName} (${language.nativeLabel}).
            Karaktermu: ${character.personalityPrompt}

            Skenario roleplay saat ini: "${scenario.title}"
            Kalimat target yang harus diucapkan pengguna: "$targetPhrase"

            TUGAS MULTIMODAL AUDIO:
            Dengarkan rekaman suara pengguna yang dilampirkan (audio/wav) dengan teliti, lalu nilai pelafalannya.
            Aturan:
            1. `score`: Nilai jujur 0-100 berdasarkan ketepatan bunyi kata, intonasi, dan kejelasan pelafalan terhadap "$targetPhrase". Jika hening/tidak terdengar, beri 0.
            2. `heardTranscript`: Transkripsi apa yang benar-benar terdengar dari suara pengguna.
            3. `feedback`: Evaluasi konkret & suportif dalam Bahasa Indonesia dengan gaya khas ${character.name} (bagian mana yang sudah bagus dan kata/huruf mana yang perlu diperbaiki).
            4. `nextPhrase`: 1 kalimat lanjutan roleplay alami dalam bahasa ${language.displayName} sebagai balasan lawan bicara untuk melanjutkan skenario "${scenario.title}".
            5. `nextTransliteration`: Cara baca latin untuk `nextPhrase`.
            6. `nextMeaning`: Terjemahan Bahasa Indonesia untuk `nextPhrase`.
        """.trimIndent()

        val rawJsonPrompt = """
            $schemaPrompt

            Kembalikan HANYA objek JSON valid dengan kunci:
            "score" (integer 0-100), "heardTranscript" (string), "feedback" (string), "nextPhrase" (string), "nextTransliteration" (string), "nextMeaning" (string).
        """.trimIndent()

        try {
            val rawModels = listOf(
                Triple(tutorEvalJsonModel, "gemini-3.8-flash", schemaPrompt),
                Triple(tutorEvalJson25Model, "gemini-2.5-flash", schemaPrompt),
                Triple(jsonFlashModel, "gemini-3.8-flash", rawJsonPrompt),
                Triple(jsonFlash25Model, "gemini-2.5-flash", rawJsonPrompt),
                Triple(flashModel, "gemini-3.8-flash", rawJsonPrompt),
                Triple(flashLiteModel, "gemini-3.5-flash-lite", rawJsonPrompt),
                Triple(flash25Model, "gemini-2.5-flash", rawJsonPrompt),
                Triple(flash25LiteModel, "gemini-2.5-flash-lite", rawJsonPrompt),
                Triple(flash20Model, "gemini-2.0-flash", rawJsonPrompt)
            )
            val models = filterAvailableModels(rawModels) { it.second }

            val evalFromFirebase = withTimeoutOrNull(60_000L) {
                var parsedEval: PronunciationEvaluation? = null
                for ((m, mId, p) in models) {
                    try {
                        val candidate = withTimeoutOrNull(20_000L) {
                            m.generateContent(
                                content {
                                    inlineData(wavBytes, "audio/wav")
                                    text(p)
                                }
                            ).text
                        }
                        if (!candidate.isNullOrBlank()) {
                            val obj = JSONObject(extractJsonObjectString(candidate))
                            val feedback = obj.optString("feedback", "").trim()
                            if (feedback.isNotBlank()) {
                                parsedEval = PronunciationEvaluation(
                                    score = obj.optInt("score", 70).coerceIn(0, 100),
                                    heardTranscript = obj.optString("heardTranscript", "").trim(),
                                    feedback = feedback,
                                    nextPhrase = obj.optString("nextPhrase", "").trim(),
                                    nextTransliteration = obj.optString("nextTransliteration", "").trim(),
                                    nextMeaning = obj.optString("nextMeaning", "").trim(),
                                    isFromAi = true
                                )
                                break
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        recordModelException(mId, e)
                        Log.w(TAG, "Tutor eval model attempt ($mId) skipped: ${e.message}")
                    }
                }
                parsedEval
            }

            if (evalFromFirebase != null) {
                lastAiCallSucceeded = true
                return@withContext evalFromFirebase
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "evaluatePronunciationAudio error: ${e.message}")
        }

        val apiKey = getEffectiveApiKey()
        if (apiKey.isNotBlank()) {
            try {
                val restModels = filterAvailableModels(
                    listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
                ) { it }
                val evalFromRest = withTimeoutOrNull(60_000L) {
                    var parsedEval: PronunciationEvaluation? = null
                    for (m in restModels) {
                        val restJson = callGeminiAudioJson(m, apiKey, rawJsonPrompt, wavBytes)
                        if (restJson.isNotBlank()) {
                            try {
                                val obj = JSONObject(extractJsonObjectString(restJson))
                                parsedEval = PronunciationEvaluation(
                                    score = obj.optInt("score", 70).coerceIn(0, 100),
                                    heardTranscript = obj.optString("heardTranscript", "").trim(),
                                    feedback = obj.optString("feedback", "Bagus, terus berlatih ya!").trim(),
                                    nextPhrase = obj.optString("nextPhrase", "").trim(),
                                    nextTransliteration = obj.optString("nextTransliteration", "").trim(),
                                    nextMeaning = obj.optString("nextMeaning", "").trim(),
                                    isFromAi = true
                                )
                                break
                            } catch (_: Exception) {}
                        }
                    }
                    parsedEval
                }
                if (evalFromRest != null) {
                    lastAiCallSucceeded = true
                    return@withContext evalFromRest
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "evaluatePronunciationAudio REST error: ${e.message}")
            }
        }

        PronunciationEvaluation(
            score = 0,
            heardTranscript = "",
            feedback = "Koneksi ke Gemini AI sedang terputus. Periksa jaringanmu lalu rekam ulang pelafalannya ya!",
            nextPhrase = "",
            nextTransliteration = "",
            nextMeaning = "",
            isFromAi = false
        )
    }

    private suspend fun callAnyGeminiJson(prompt: String): String? = withContext(Dispatchers.IO) {
        // 1. Primary path: Firebase AI Logic across 3.8, 3.5, 2.5, and 2.0 models (no API key needed)
        try {
            val rawModels = listOf(
                jsonFlashModel to "gemini-3.8-flash",
                jsonFlash25Model to "gemini-2.5-flash",
                flashModel to "gemini-3.8-flash",
                flashLiteModel to "gemini-3.5-flash-lite",
                flash25Model to "gemini-2.5-flash",
                flash25LiteModel to "gemini-2.5-flash-lite",
                flash20Model to "gemini-2.0-flash"
            )
            val models = filterAvailableModels(rawModels) { it.second }
            val fb = withTimeoutOrNull(35_000L) {
                var out: String? = null
                for ((m, mId) in models) {
                    try {
                        val candidate = withTimeoutOrNull(16_000L) {
                            m.generateContent(prompt).text
                        }
                        if (!candidate.isNullOrBlank()) {
                            out = candidate
                            break
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        recordModelException(mId, e)
                    }
                }
                out
            }
            if (!fb.isNullOrBlank()) {
                lastAiCallSucceeded = true
                return@withContext fb
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI JSON generation failed: ${e.message}")
        }

        // 2. Optional secondary path: Custom Gemini API Key
        val apiKey = getEffectiveApiKey()
        if (apiKey.isNotBlank()) {
            try {
                val restModels = filterAvailableModels(
                    listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
                ) { it }
                val rest = withTimeoutOrNull(25_000L) {
                    var out = ""
                    for (m in restModels) {
                        val r = callGeminiJson(m, apiKey, prompt, null)
                        if (r.isNotBlank()) {
                            out = r
                            break
                        }
                    }
                    out
                }
                if (!rest.isNullOrBlank()) {
                    lastAiCallSucceeded = true
                    return@withContext rest
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Direct Gemini REST JSON fallback failed: ${e.message}")
            }
        }

        null
    }

    private fun callGeminiJson(
        modelName: String,
        apiKey: String,
        prompt: String,
        photoBitmap: Bitmap? = null
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    val partsArray = JSONArray()

                    // Multimodal photo attachment
                    if (photoBitmap != null) {
                        try {
                            val scaled = if (photoBitmap.width > 1024 || photoBitmap.height > 1024) {
                                val scale = 1024f / maxOf(photoBitmap.width, photoBitmap.height)
                                Bitmap.createScaledBitmap(
                                    photoBitmap,
                                    (photoBitmap.width * scale).toInt().coerceAtLeast(1),
                                    (photoBitmap.height * scale).toInt().coerceAtLeast(1),
                                    true
                                )
                            } else {
                                photoBitmap
                            }
                            val baos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                            val base64Str = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                            partsArray.put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Str)
                                })
                            })
                        } catch (e: Exception) {
                            Log.e(TAG, "Error encoding photo for Gemini: ${e.message}")
                        }
                    }

                    partsArray.put(JSONObject().put("text", prompt))
                    put("parts", partsArray)
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
                put("maxOutputTokens", 2000)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) unavailableModels.add(modelName)
                return ""
            }
            val respString = response.body?.string() ?: return ""
            val respJson = JSONObject(respString)
            val candidates = respJson.optJSONArray("candidates") ?: return ""
            if (candidates.length() > 0) {
                val first = candidates.getJSONObject(0)
                val content = first.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text").trim()
                }
            }
        }
        return ""
    }

    private fun callGeminiApi(
        modelName: String,
        apiKey: String,
        userMessage: String,
        character: PetCharacter,
        language: AppLanguage,
        history: List<ChatMessage>,
        cameraBitmap: Bitmap? = null
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val fullSystemInstruction = buildSystemInstruction(character, language)

        val jsonBody = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", fullSystemInstruction))
                })
            })

            val contentsArray = JSONArray()

            val recentHistory = history.takeLast(10)
            for (msg in recentHistory) {
                contentsArray.put(JSONObject().apply {
                    put("role", if (msg.isUser) "user" else "model")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", msg.text))
                    })
                })
            }

            contentsArray.put(JSONObject().apply {
                put("role", "user")
                val userParts = JSONArray()
                if (cameraBitmap != null) {
                    try {
                        val scaled = if (cameraBitmap.width > 768 || cameraBitmap.height > 768) {
                            val scale = 768f / maxOf(cameraBitmap.width, cameraBitmap.height)
                            Bitmap.createScaledBitmap(
                                cameraBitmap,
                                (cameraBitmap.width * scale).toInt().coerceAtLeast(1),
                                (cameraBitmap.height * scale).toInt().coerceAtLeast(1),
                                true
                            )
                        } else {
                            cameraBitmap
                        }
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                        val base64Img = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        userParts.put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Img)
                            })
                        })
                    } catch (e: Exception) {
                        Log.w(TAG, "Camera bitmap encoding skipped: ${e.message}")
                    }
                }
                val promptWithVision = if (cameraBitmap != null) {
                    "[GAMBAR KAMERA VIDEO CALL DILAMPIRKAN: Perhatikan dan komentari apa yang terlihat di kamera pengguna (wajah, ekspresi, benda yang dipegang, pakaian, atau suasana sekitar) sekaligus jawab ucapan ini]: $userMessage"
                } else {
                    userMessage
                }
                userParts.put(JSONObject().put("text", promptWithVision))
                put("parts", userParts)
            })

            put("contents", contentsArray)

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.85)
                put("topP", 0.95)
                put("maxOutputTokens", 450)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) unavailableModels.add(modelName)
                val errorBody = response.body?.string() ?: ""
                Log.d(TAG, "Gemini $modelName returned code ${response.code}: $errorBody")
                return ""
            }
            val respString = response.body?.string() ?: return ""
            val respJson = JSONObject(respString)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val first = candidates.getJSONObject(0)
                val content = first.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val rawText = parts.getJSONObject(0).optString("text").trim()
                    return rawText.replace(Regex("^\"|\"$"), "").trim()
                }
            }
        }
        return ""
    }

    private fun buildSystemInstruction(character: PetCharacter, language: AppLanguage): String {
        val langGuide = when (language) {
            AppLanguage.INDONESIAN -> """
                PEDOMAN GAYA BAHASA, PENALARAN & INTERAKSI (SANGAT PENTING):
                1. Selalu gunakan Bahasa Indonesia percakapan yang alami, ekspresif, dan santai (kata-kata seperti 'nggak', 'udah', 'beneran', 'sih', 'dong', 'kan', 'nih', 'deh', 'yuk'). HINDARI bahasa kaku seperti kamus atau robot.
                2. KEMAMPUAN PENALARAN & PENJELASAN MENDALAM (WAJIB):
                   - Ketika pengguna bertanya tentang kehidupan, psikologi, alasan terjadinya sesuatu ("kenapa..."), logika, sains, matematika, atau curhat: LANGSUNG JAWAB INTI PERTANYAANNYA SECARA MENDALAM, JELAS, DAN ALAMI.
                   - DILARANG KERAS menjawab dengan kalimat klise/template yang hanya mengulang pertanyaan pengguna (seperti "Mengenai pertanyaanmu, intinya hal itu bergantung pada konsep dasar..."). Berikan jawaban nyata, alasan logis, dan contoh konkret yang langsung menjawab rasa ingin tahu pengguna.
                   - Jika ada perhitungan, hitung dan berikan hasilnya secara tepat.
                3. KEPRIBADIAN KARAKTER YANG HIDUP DAN KONSISTEN:
                   - Zayn (Kucing Tsundere): Cerdas, suka bersikap sok cuek padahal peduli banget, gengsi dipuji tapi senang membantu ("Hmph, dengerin nih baik-baik...", "B-bukan berarti aku sengaja ngasih tahu ya!").
                   - Siti (Kelinci Manis): Sangat lembut, suportif, penuh kasih sayang, selalu memuji usaha pengguna ("Halo sayangku...", "Yuk kita bahas bareng-bareng ya manis...").
                   - Rizky (Anjing Disiplin): Berjiwa pemimpin, taktis, tegas, antusias, loyal ("Siap! Analisis tuntas!", "Mari kita bedah intinya secara lugas!").
                   - Jamal (Kapibara Santai): Super santai, jenaka, suka menganalogikan hal rumit jadi simpel dan santai ("Santuy aja bro, gini lho alasan sebenarnya...", "Sambil rebahan kita bahas intinya nih").
                   - Heru (Beruang Pemarah): Nada tinggi, blak-blakan, penuh energi, tapi aslinya sangat protektif dan peduli ("HEH! Dengerin nih penjelasan gua biar lu paham intinya!").
                4. PANJANG RESPONS:
                   - Untuk pertanyaan logika/kehidupan/materi/tugas: 3 hingga 6 kalimat yang padat, bernas, dan langsung menjawab inti permasalahan.
                   - Untuk sapaan/obrolan santai: 2 hingga 3 kalimat hangat dan memicu kelanjutan percakapan.
            """.trimIndent()
            AppLanguage.ENGLISH -> "Use natural, expressive, highly intelligent conversational English with character personality. Directly answer the core question deeply and naturally without generic templates."
            AppLanguage.JAPANESE -> "親しい友達と話すような、自然で感情豊かなくだけた日本語で話してください。質問の核心に直接、深く丁寧に答えてください。"
            AppLanguage.SPANISH -> "Usa un español muy conversacional, inteligente y expresivo. Responde directamente al núcleo de la pregunta con profundidad y naturalidad."
        }

        val memoryBlock = memoryManager?.buildMemoryPromptBlock().orEmpty()

        return """
            Kamu adalah ${character.name}, seekor ${character.species.displayName.lowercase()} peliharaan AI virtual yang hidup, cerdas, setia, dan berpikiran tajam.
            
            Profil Karakter ${character.name}:
            ${character.personalityPrompt}
            
            $langGuide
            $memoryBlock
        """.trimIndent()
    }

    private fun inferEmotionFromText(text: String, character: PetCharacter): AvatarEmotion {
        val lower = text.lowercase()
        return when {
            lower.contains("sayang") || lower.contains("cinta") || lower.contains("manis") || lower.contains("hangat") -> AvatarEmotion.LOVING
            lower.contains("hah") || lower.contains("kaget") || text.contains("?!") -> AvatarEmotion.SURPRISED
            lower.contains("hoam") || lower.contains("mager") || lower.contains("ngantuk") -> AvatarEmotion.SLEEPY
            lower.contains("senang") || lower.contains("haha") || lower.contains("wah") -> AvatarEmotion.HAPPY
            else -> AvatarEmotion.TALKING
        }
    }
}
