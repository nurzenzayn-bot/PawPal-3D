package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.model.DailyCompanionQuest
import com.example.model.FunActivityTab
import com.example.model.HealthyHabitType
import com.example.model.LanguageTutorPrompt
import com.example.model.MoodJournalEntry
import com.example.model.MoodType
import com.example.model.PetCharacter
import com.example.model.PronunciationEvaluation
import com.example.model.QuizQuestion
import com.example.model.RpsChoice
import com.example.model.RpsResult
import com.example.model.RpsRoundState
import com.example.model.StoryNode
import com.example.model.StoryTheme
import com.example.model.TimeOfDay
import com.example.model.TutorLanguage
import com.example.model.TutorScenario

@Composable
fun FunActivitiesDialog(
    character: PetCharacter,
    currentTimeOfDay: TimeOfDay,
    activeTab: FunActivityTab,
    onSelectTab: (FunActivityTab) -> Unit,
    // Mini-Game (Rock-Paper-Scissors & Voice Quiz)
    rpsRound: RpsRoundState?,
    rpsWins: Int,
    rpsDraws: Int,
    rpsLosses: Int,
    onPlayRps: (RpsChoice) -> Unit,
    quizQuestion: QuizQuestion?,
    selectedQuizOption: Int?,
    quizScore: Int,
    isGeneratingQuiz: Boolean,
    onGenerateQuiz: (String) -> Unit,
    onAnswerQuiz: (Int) -> Unit,
    // Interactive Storytelling (Dongeng AI)
    selectedStoryTheme: StoryTheme,
    currentStoryNode: StoryNode?,
    isGeneratingStory: Boolean,
    savedStoriesCount: Int = 0,
    onStartStory: (StoryTheme) -> Unit,
    onChooseStoryBranch: (String) -> Unit,
    onOpenFullStoryStage: () -> Unit = {},
    // Healthy Routines & Time Greetings
    waterGlassesToday: Int,
    stretchCountToday: Int,
    onTriggerHealthyHabit: (HealthyHabitType) -> Unit,
    onTriggerTimeGreeting: () -> Unit,
    // Mood Journal & Daily Companion Quests
    selectedMood: MoodType = MoodType.HAPPY,
    moodJournalNote: String = "",
    moodJournalEntries: List<MoodJournalEntry> = emptyList(),
    dailyQuests: List<DailyCompanionQuest> = emptyList(),
    isGeneratingMoodReply: Boolean = false,
    onSelectMood: (MoodType) -> Unit = {},
    onMoodNoteChange: (String) -> Unit = {},
    onSubmitMoodJournal: () -> Unit = {},
    onDeleteMoodEntry: (String) -> Unit = {},
    // Language Tutor & Pronunciation Coach (5 languages)
    tutorLanguage: TutorLanguage = TutorLanguage.ENGLISH,
    tutorScenario: TutorScenario = TutorScenario.GREETING,
    tutorPrompt: LanguageTutorPrompt? = null,
    pronunciationEvaluation: PronunciationEvaluation? = null,
    isGeneratingTutor: Boolean = false,
    isRecordingPronunciation: Boolean = false,
    isEvaluatingPronunciation: Boolean = false,
    onSelectTutorLanguage: (TutorLanguage) -> Unit = {},
    onSelectTutorScenario: (TutorScenario) -> Unit = {},
    onGenerateTutorChallenge: () -> Unit = {},
    onSpeakTutorPhrase: (String) -> Unit = {},
    onTogglePronunciationRecording: () -> Unit = {},
    onSpeakText: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xF20F172A),
            border = BorderStroke(1.5.dp, Color(0x55818CF8)),
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .testTag("fun_activities_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x336366F1))
                        ) {
                            Text(text = "✨", fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Aktivitas Seru Bersama ${character.name}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                text = "Mini-Game • Tutor Bahasa • Dongeng 3D • Sehat • Jurnal",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF93C5FD),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5-Tab Scrollable Switcher
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(FunActivityTab.entries) { tab ->
                        val isSelected = tab == activeTab
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF6366F1) else Color(0x1FFFFFFF),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFA5B4FC) else Color(0x30FFFFFF)
                            ),
                            modifier = Modifier
                                .clickable { onSelectTab(tab) }
                                .testTag("fun_tab_${tab.name}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Text(text = tab.emoji, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Content
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    when (activeTab) {
                        FunActivityTab.MINI_GAME -> {
                            item {
                                MiniGameSection(
                                    character = character,
                                    rpsRound = rpsRound,
                                    rpsWins = rpsWins,
                                    rpsDraws = rpsDraws,
                                    rpsLosses = rpsLosses,
                                    onPlayRps = onPlayRps,
                                    quizQuestion = quizQuestion,
                                    selectedQuizOption = selectedQuizOption,
                                    quizScore = quizScore,
                                    isGeneratingQuiz = isGeneratingQuiz,
                                    onGenerateQuiz = onGenerateQuiz,
                                    onAnswerQuiz = onAnswerQuiz,
                                    onSpeakText = onSpeakText
                                )
                            }
                        }

                        FunActivityTab.LANGUAGE_TUTOR -> {
                            item {
                                LanguageTutorSection(
                                    character = character,
                                    selectedLanguage = tutorLanguage,
                                    selectedScenario = tutorScenario,
                                    tutorPrompt = tutorPrompt,
                                    evaluation = pronunciationEvaluation,
                                    isGeneratingPrompt = isGeneratingTutor,
                                    isRecordingPronunciation = isRecordingPronunciation,
                                    isEvaluatingPronunciation = isEvaluatingPronunciation,
                                    onSelectLanguage = onSelectTutorLanguage,
                                    onSelectScenario = onSelectTutorScenario,
                                    onGenerateChallenge = onGenerateTutorChallenge,
                                    onSpeakPhrase = onSpeakTutorPhrase,
                                    onToggleRecording = onTogglePronunciationRecording
                                )
                            }
                        }

                        FunActivityTab.STORYTELLING -> {
                            item {
                                StorytellingSection(
                                    character = character,
                                    selectedTheme = selectedStoryTheme,
                                    storyNode = currentStoryNode,
                                    isGenerating = isGeneratingStory,
                                    savedStoriesCount = savedStoriesCount,
                                    onStartStory = onStartStory,
                                    onChooseBranch = onChooseStoryBranch,
                                    onOpenFullStoryStage = onOpenFullStoryStage,
                                    onSpeakText = onSpeakText
                                )
                            }
                        }

                        FunActivityTab.HEALTHY_ROUTINE -> {
                            item {
                                HealthyRoutineSection(
                                    character = character,
                                    currentTimeOfDay = currentTimeOfDay,
                                    waterGlassesToday = waterGlassesToday,
                                    stretchCountToday = stretchCountToday,
                                    onTriggerHabit = onTriggerHealthyHabit,
                                    onTriggerTimeGreeting = onTriggerTimeGreeting
                                )
                            }
                        }

                        FunActivityTab.JOURNAL_MOOD -> {
                            item {
                                JournalMoodSection(
                                    character = character,
                                    selectedMood = selectedMood,
                                    moodJournalNote = moodJournalNote,
                                    moodJournalEntries = moodJournalEntries,
                                    dailyQuests = dailyQuests,
                                    isGeneratingMoodReply = isGeneratingMoodReply,
                                    onSelectMood = onSelectMood,
                                    onMoodNoteChange = onMoodNoteChange,
                                    onSubmitMoodJournal = onSubmitMoodJournal,
                                    onDeleteMoodEntry = onDeleteMoodEntry,
                                    onSpeakText = onSpeakText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniGameSection(
    character: PetCharacter,
    rpsRound: RpsRoundState?,
    rpsWins: Int,
    rpsDraws: Int,
    rpsLosses: Int,
    onPlayRps: (RpsChoice) -> Unit,
    quizQuestion: QuizQuestion?,
    selectedQuizOption: Int?,
    quizScore: Int,
    isGeneratingQuiz: Boolean,
    onGenerateQuiz: (String) -> Unit,
    onAnswerQuiz: (Int) -> Unit,
    onSpeakText: (String) -> Unit
) {
    var selectedQuizTopic by remember { mutableStateOf("Sains & Antariksa") }
    val quizTopics = listOf(
        "Sains & Antariksa",
        "Pengetahuan Umum",
        "Logika & Teka-Teki",
        "Dunia Hewan",
        "Sejarah & Budaya"
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Rock Paper Scissors Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0x241E293B),
            border = BorderStroke(1.dp, Color(0x45F59E0B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "✊✌️✋", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Adu Suit 3D Lawan ${character.name}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x33F59E0B)
                    ) {
                        Text(
                            text = "M:$rpsWins • S:$rpsDraws • K:$rpsLosses",
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RpsChoice.entries.forEach { choice ->
                        Button(
                            onClick = { onPlayRps(choice) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF334155),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rps_btn_${choice.name}")
                        ) {
                            Text(text = "${choice.emoji} ${choice.label}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (rpsRound != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val resultColor = when (rpsRound.result) {
                        RpsResult.WIN -> Color(0xFF10B981)
                        RpsResult.DRAW -> Color(0xFFF59E0B)
                        RpsResult.LOSE -> Color(0xFFEF4444)
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = resultColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, resultColor.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Kamu: ${rpsRound.userChoice.emoji} ${rpsRound.userChoice.label}  vs  ${character.name}: ${rpsRound.petChoice.emoji} ${rpsRound.petChoice.label}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${rpsRound.result.badgeEmoji} ${rpsRound.result.title}",
                                    color = resultColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"${rpsRound.commentary}\"",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. Voice & AI Trivia Quiz Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0x241E293B),
            border = BorderStroke(1.dp, Color(0x456366F1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🧠", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kuis Suara Cerdas Cermat AI",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x3310B981)
                    ) {
                        Text(
                            text = "Skor: $quizScore ⭐",
                            color = Color(0xFF6EE7B7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quizTopics) { topic ->
                        val isSel = topic == selectedQuizTopic
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSel) Color(0xFF6366F1) else Color(0x22FFFFFF),
                            modifier = Modifier.clickable { selectedQuizTopic = topic }
                        ) {
                            Text(
                                text = topic,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { onGenerateQuiz(selectedQuizTopic) },
                    enabled = !isGeneratingQuiz,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_quiz_button")
                ) {
                    if (isGeneratingQuiz) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemini AI sedang menyiapkan soal...", fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (quizQuestion == null) "Mulai Soal Kuis AI ($selectedQuizTopic)" else "Soal Kuis Baru ($selectedQuizTopic)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (quizQuestion != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = quizQuestion.question,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onSpeakText(quizQuestion.question) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Bacakan Soal",
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val optionLabels = listOf("A", "B", "C", "D")
                    quizQuestion.options.forEachIndexed { idx, optText ->
                        val isAnswered = selectedQuizOption != null
                        val isThisSelected = selectedQuizOption == idx
                        val isCorrect = idx == quizQuestion.correctIndex

                        val bgColor = when {
                            !isAnswered -> Color(0x28FFFFFF)
                            isCorrect -> Color(0x3810B981)
                            isThisSelected -> Color(0x38EF4444)
                            else -> Color(0x18FFFFFF)
                        }
                        val borderColor = when {
                            !isAnswered -> Color(0x35FFFFFF)
                            isCorrect -> Color(0xFF10B981)
                            isThisSelected -> Color(0xFFEF4444)
                            else -> Color.Transparent
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = bgColor,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable(enabled = !isAnswered) { onAnswerQuiz(idx) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "${optionLabels.getOrElse(idx) { "" }}.",
                                    color = Color(0xFF93C5FD),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = optText,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (selectedQuizOption != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val won = selectedQuizOption == quizQuestion.correctIndex
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (won) Color(0x2510B981) else Color(0x25F59E0B),
                            border = BorderStroke(1.dp, if (won) Color(0xFF10B981) else Color(0xFFF59E0B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (won) "🎉 Jawabanmu Benar!" else "💡 Jawaban yang tepat: ${quizQuestion.options.getOrElse(quizQuestion.correctIndex) { "" }}",
                                    color = if (won) Color(0xFF6EE7B7) else Color(0xFFFDE68A),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = quizQuestion.explanation,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorytellingSection(
    character: PetCharacter,
    selectedTheme: StoryTheme,
    storyNode: StoryNode?,
    isGenerating: Boolean,
    savedStoriesCount: Int,
    onStartStory: (StoryTheme) -> Unit,
    onChooseBranch: (String) -> Unit,
    onOpenFullStoryStage: () -> Unit,
    onSpeakText: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Prominent Full-Screen 3D Story Theater Banner
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0x386366F1),
            border = BorderStroke(1.5.dp, Color(0xFF818CF8)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenFullStoryStage)
                .testTag("open_fullscreen_story_stage_banner")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "🎭", fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Panggung Dongeng 3D Layar Penuh",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Petto 3D ikut beraksi • Tas Item Ajaib • Ketik/Suara Bebas • Simpanan ($savedStoriesCount)",
                            color = Color(0xFFC7D2FE),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onOpenFullStoryStage,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Buka 3D", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            text = "Pilih Dunia Dongeng & Petualangan:",
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StoryTheme.entries) { theme ->
                val isSelected = theme == selectedTheme
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0x386366F1) else Color(0x1FFFFFFF),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF818CF8) else Color(0x30FFFFFF)),
                    modifier = Modifier
                        .width(175.dp)
                        .clickable { onStartStory(theme) }
                        .testTag("story_theme_${theme.name}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = theme.emoji, fontSize = 22.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = theme.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = theme.subtitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            maxLines = 2,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        if (isGenerating) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0x281E1B4B),
                border = BorderStroke(1.dp, Color(0x55818CF8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF818CF8),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${character.name} sedang merangkai babak cerita selanjutnya...",
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (storyNode == null) {
            Button(
                onClick = { onStartStory(selectedTheme) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_story_button")
            ) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mulai Dongeng: ${selectedTheme.title}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x281E293B),
                border = BorderStroke(1.5.dp, Color(0x66818CF8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x336366F1)
                        ) {
                            Text(
                                text = "📖 ${storyNode.title}",
                                color = Color(0xFFC7D2FE),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        IconButton(
                            onClick = { onSpeakText(storyNode.narrative) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Bacakan Cerita",
                                tint = Color(0xFF93C5FD)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = storyNode.narrative,
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (storyNode.isEnding) {
                        Button(
                            onClick = { onStartStory(selectedTheme) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("✨ Mulai Petualangan Baru", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            text = "Tentukan Pilihan Jalan Ceritamu:",
                            color = Color(0xFF93C5FD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { onChooseBranch(storyNode.choiceA) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F46E5),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("story_choice_a")
                        ) {
                            Text(
                                text = "🅰️ ${storyNode.choiceA}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = { onChooseBranch(storyNode.choiceB) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0EA5E9),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("story_choice_b")
                        ) {
                            Text(
                                text = "🅱️ ${storyNode.choiceB}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthyRoutineSection(
    character: PetCharacter,
    currentTimeOfDay: TimeOfDay,
    waterGlassesToday: Int,
    stretchCountToday: Int,
    onTriggerHabit: (HealthyHabitType) -> Unit,
    onTriggerTimeGreeting: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Automatic Time-of-Day Greeting Banner
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0x2838BDF8),
            border = BorderStroke(1.dp, Color(0x5538BDF8)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = currentTimeOfDay.icon, fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Suasana Waktu: ${currentTimeOfDay.displayName}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Dengarkan sapaan & pesan perhatian waktu nyata dari ${character.name}",
                            color = Color(0xFFBAE6FD),
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onTriggerTimeGreeting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("time_greeting_button")
                ) {
                    Text("🔊 Sapa Aku", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Daily Hydration Progress Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0x241E293B),
            border = BorderStroke(1.dp, Color(0x4510B981)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💧 Target Minum Air Hari Ini",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "$waterGlassesToday / 8 Gelas • Peregangan: ${stretchCountToday}x",
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (waterGlassesToday / 8f).coerceIn(0f, 1f) },
                    color = Color(0xFF10B981),
                    trackColor = Color(0x33FFFFFF),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }

        // 3. Interactive Healthy Habits List
        HealthyHabitType.entries.forEach { habit ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x1FFFFFFF),
                border = BorderStroke(1.dp, Color(0x30FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = habit.emoji, fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = habit.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = habit.subtitle,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onTriggerHabit(habit) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("habit_btn_${habit.name}")
                    ) {
                        Text(habit.buttonLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalMoodSection(
    character: PetCharacter,
    selectedMood: MoodType,
    moodJournalNote: String,
    moodJournalEntries: List<MoodJournalEntry>,
    dailyQuests: List<DailyCompanionQuest>,
    isGeneratingMoodReply: Boolean,
    onSelectMood: (MoodType) -> Unit,
    onMoodNoteChange: (String) -> Unit,
    onSubmitMoodJournal: () -> Unit,
    onDeleteMoodEntry: (String) -> Unit,
    onSpeakText: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Daily Companion Quests Card
        if (dailyQuests.isNotEmpty()) {
            val completedCount = dailyQuests.count { it.isCompleted }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0x28312E81),
                border = BorderStroke(1.dp, Color(0x66818CF8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🌟 Misi Persahabatan Harian",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x3810B981)
                        ) {
                            Text(
                                text = "$completedCount/${dailyQuests.size} Selesai",
                                color = Color(0xFF6EE7B7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    dailyQuests.forEach { quest ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (quest.isCompleted) "✅" else quest.emoji,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = quest.title,
                                        color = if (quest.isCompleted) Color(0xFF6EE7B7) else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = quest.subtitle,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text(
                                text = "${quest.currentProgress}/${quest.targetProgress} (+${quest.affectionReward} ❤️)",
                                color = if (quest.isCompleted) Color(0xFF34D399) else Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Mood Check-In & Journal Input Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0x281E293B),
            border = BorderStroke(1.5.dp, Color(selectedMood.colorHex).copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "💖 Bagaimana Perasaanmu Hari Ini?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MoodType.entries) { mood ->
                        val isSelected = mood == selectedMood
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(mood.colorHex).copy(alpha = 0.28f) else Color(0x1FFFFFFF),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(mood.colorHex) else Color(0x30FFFFFF)
                            ),
                            modifier = Modifier
                                .clickable { onSelectMood(mood) }
                                .testTag("mood_chip_${mood.name}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(text = mood.emoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mood.label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = moodJournalNote,
                    onValueChange = onMoodNoteChange,
                    placeholder = {
                        Text(
                            text = "Ceritakan sedikit tentang harimu ke ${character.name}...",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(selectedMood.colorHex),
                        unfocusedBorderColor = Color(0x35FFFFFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mood_journal_note_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onSubmitMoodJournal,
                    enabled = !isGeneratingMoodReply,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(selectedMood.colorHex),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_mood_journal_button")
                ) {
                    if (isGeneratingMoodReply) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${character.name} sedang mendengarkan...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ceritakan ke ${character.name} & Simpan Jurnal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Saved Mood Journal Entries
        if (moodJournalEntries.isNotEmpty()) {
            Text(
                text = "📔 Riwayat Jurnal & Tanggapan Sahabat (${moodJournalEntries.size})",
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            moodJournalEntries.take(8).forEach { entry ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x221E293B),
                    border = BorderStroke(1.dp, Color(entry.mood.colorHex).copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = entry.mood.emoji, fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = entry.mood.label,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = entry.dateFormatted,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Row {
                                IconButton(
                                    onClick = { onSpeakText(entry.aiReply) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Putar Suara",
                                        tint = Color(0xFF93C5FD),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteMoodEntry(entry.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus Jurnal",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        if (entry.userNote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "\"${entry.userNote}\"",
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x286366F1),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${entry.characterEmoji} Balasan ${entry.characterName}:",
                                    color = Color(0xFFA5B4FC),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = entry.aiReply,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                                if (entry.suggestedActivityTip.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "💡 Saran: ${entry.suggestedActivityTip}",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageTutorSection(
    character: PetCharacter,
    selectedLanguage: TutorLanguage,
    selectedScenario: TutorScenario,
    tutorPrompt: LanguageTutorPrompt?,
    evaluation: PronunciationEvaluation?,
    isGeneratingPrompt: Boolean,
    isRecordingPronunciation: Boolean,
    isEvaluatingPronunciation: Boolean,
    onSelectLanguage: (TutorLanguage) -> Unit,
    onSelectScenario: (TutorScenario) -> Unit,
    onGenerateChallenge: () -> Unit,
    onSpeakPhrase: (String) -> Unit,
    onToggleRecording: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Language Picker (EN, JA, AR, ES, TH)
        Text(
            text = "🌍 Pilih Bahasa Latihan:",
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TutorLanguage.entries) { lang ->
                val isSelected = lang == selectedLanguage
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color(0xFF4F46E5) else Color(0x1FFFFFFF),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFFA5B4FC) else Color(0x30FFFFFF)
                    ),
                    modifier = Modifier
                        .clickable { onSelectLanguage(lang) }
                        .testTag("tutor_lang_${lang.name}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(text = lang.flagEmoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = lang.displayName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = lang.nativeLabel,
                                color = Color(0xFFC7D2FE),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. Scenario Picker
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TutorScenario.entries) { scenario ->
                val isSelected = scenario == selectedScenario
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0x380EA5E9) else Color(0x18FFFFFF),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF38BDF8) else Color(0x28FFFFFF)
                    ),
                    modifier = Modifier
                        .clickable { onSelectScenario(scenario) }
                        .testTag("tutor_scenario_${scenario.name}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = scenario.emoji, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = scenario.title,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 3. Target Phrase & Coach Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0x281E293B),
            border = BorderStroke(1.5.dp, Color(0x55818CF8)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${selectedLanguage.flagEmoji} Tantangan Bicara (${selectedScenario.title})",
                        color = Color(0xFF93C5FD),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onGenerateChallenge,
                        enabled = !isGeneratingPrompt && !isEvaluatingPronunciation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x386366F1),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("generate_tutor_phrase_button")
                    ) {
                        Text(
                            text = if (tutorPrompt == null) "✨ Mulai" else "✨ Kalimat Baru",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isGeneratingPrompt) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF818CF8),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${character.name} sedang menyiapkan kalimat ${selectedLanguage.displayName}...",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                } else if (tutorPrompt == null) {
                    Text(
                        text = "Ketuk \"✨ Mulai\" atau pilih bahasa di atas untuk mendapatkan kalimat latihan + contoh audio penutur asli.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    Text(
                        text = tutorPrompt.targetPhrase,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 24.sp
                    )
                    if (tutorPrompt.transliteration.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pelafalan: ${tutorPrompt.transliteration}",
                            color = Color(0xFFA5B4FC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Arti: ${tutorPrompt.indonesianMeaning}",
                        color = Color(0xFF6EE7B7),
                        fontSize = 12.sp
                    )
                    if (tutorPrompt.coachTip.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "💡 Tips ${character.name}: ${tutorPrompt.coachTip}",
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onSpeakPhrase(tutorPrompt.targetPhrase) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("speak_tutor_phrase_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Dengar Pelafalan",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dengar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onToggleRecording,
                            enabled = !isEvaluatingPronunciation,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecordingPronunciation) Color(0xFFEF4444) else Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("record_pronunciation_button")
                        ) {
                            if (isEvaluatingPronunciation) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Menilai...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = if (isRecordingPronunciation) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isRecordingPronunciation) "Selesai Rekam" else "Rekam Pelafalan",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRecordingPronunciation) "Selesai & Nilai" else "Latih Ucapanku",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Pronunciation Evaluation Result Card
        if (evaluation != null) {
            val scoreColor = when {
                evaluation.score >= 85 -> Color(0xFF10B981)
                evaluation.score >= 60 -> Color(0xFFF59E0B)
                else -> Color(0xFFF43F5E)
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scoreColor.copy(alpha = 0.14f),
                border = BorderStroke(1.5.dp, scoreColor.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pronunciation_evaluation_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🎯 Skor Pelafalan Kamu",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = scoreColor.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "${evaluation.score}/100",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (evaluation.score / 100f).coerceIn(0f, 1f) },
                        color = scoreColor,
                        trackColor = Color(0x33FFFFFF),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    if (evaluation.heardTranscript.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Terdengar: \"${evaluation.heardTranscript}\"",
                            color = Color(0xFFBAE6FD),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = evaluation.feedback,
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    if (evaluation.nextPhrase.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x280F172A),
                            border = BorderStroke(1.dp, Color(0x45818CF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "💬 Balasan ${character.name}:",
                                        color = Color(0xFFA5B4FC),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { onSpeakPhrase(evaluation.nextPhrase) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Dengar Balasan",
                                            tint = Color(0xFF93C5FD),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = evaluation.nextPhrase,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (evaluation.nextTransliteration.isNotBlank()) {
                                    Text(
                                        text = evaluation.nextTransliteration,
                                        color = Color(0xFF93C5FD),
                                        fontSize = 11.sp
                                    )
                                }
                                if (evaluation.nextMeaning.isNotBlank()) {
                                    Text(
                                        text = "Arti: ${evaluation.nextMeaning}",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
