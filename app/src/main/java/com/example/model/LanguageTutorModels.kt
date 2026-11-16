package com.example.model

enum class TutorLanguage(
    val displayName: String,
    val nativeLabel: String,
    val flagEmoji: String,
    val bcp47: String
) {
    ENGLISH("Bahasa Inggris", "English", "🇬🇧", "en-US"),
    JAPANESE("Bahasa Jepang", "日本語", "🇯🇵", "ja-JP"),
    ARABIC("Bahasa Arab", "العربية", "🇸🇦", "ar-SA"),
    SPANISH("Bahasa Spanyol", "Español", "🇪🇸", "es-ES"),
    THAI("Bahasa Thailand", "ภาษาไทย", "🇹🇭", "th-TH")
}

enum class TutorScenario(
    val title: String,
    val emoji: String,
    val promptHint: String
) {
    GREETING("Perkenalan & Sapaan", "👋", "sapaan ramah, memperkenalkan diri, dan menanyakan kabar"),
    CAFE("Kafe & Pesan Makanan", "☕", "memesan minuman atau makanan favorit di kafe dengan sopan"),
    TRAVEL("Jalan-Jalan & Arah", "✈️", "menanyakan arah jalan, tiket transportasi, atau tempat wisata"),
    STUDY_WORK("Sekolah & Kampus", "📚", "berdiskusi tentang pelajaran, tugas, atau kegiatan harian"),
    FREE_TALK("Obrolan Bebas", "💬", "percakapan santai sehari-hari yang natural dan ekspresif")
}

data class LanguageTutorPrompt(
    val language: TutorLanguage,
    val scenario: TutorScenario,
    val targetPhrase: String,
    val transliteration: String,
    val indonesianMeaning: String,
    val coachTip: String
)

data class PronunciationEvaluation(
    val score: Int,
    val heardTranscript: String,
    val feedback: String,
    val nextPhrase: String,
    val nextTransliteration: String,
    val nextMeaning: String,
    val isFromAi: Boolean = true
)
