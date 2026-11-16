package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.SpeechRecognitionHelper
import com.example.model.AppLanguage
import com.example.model.IdlePetBehavior
import com.example.ui.AvatarViewModel
import com.example.game.PawPal3DGameActivity
import com.example.ui.VoiceSessionState
import com.example.model.CareOverlayType
import com.example.ui.components.Avatar3DCanvas
import com.example.ui.components.BackgroundThemeDialog
import com.example.ui.components.CharacterPickerDialog
import com.example.ui.components.ChatHistoryDialog
import com.example.ui.components.DynamicBackgroundCanvas
import com.example.ui.components.FaceToFaceCallView
import com.example.ui.components.LiquidGlassDefaults
import com.example.ui.components.LiquidGlassIconButton
import com.example.ui.components.LiquidGlassPillButton
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.SettingsDialog
import com.example.ui.components.VoiceInputDialog
import com.example.ui.components.WardrobeSheet
import com.example.ui.components.TalkingTomTopHud
import com.example.ui.components.HomeworkStudyView
import com.example.ui.components.InteractiveRoomPropsOverlay
import com.example.ui.components.MemoryProfileDialog
import com.example.ui.components.FunActivitiesDialog
import com.example.ui.components.InteractiveStoryStageView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarRoomScreen(
    viewModel: AvatarViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showWardrobe by remember { mutableStateOf(false) }
    var showCharacterPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showChatHistory by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showMemoryProfile by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var showChatInput by remember { mutableStateOf(false) }

    val wardrobeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Fallback system speech launcher (Google Voice Typing)
    val systemSpeechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                viewModel.onFallbackSpeechResult(matches[0])
            }
        }
    }

    LaunchedEffect(uiState.triggerSystemSpeechFallback) {
        if (uiState.triggerSystemSpeechFallback) {
            try {
                systemSpeechLauncher.launch(
                    SpeechRecognitionHelper.createSystemSpeechIntent(uiState.selectedLanguage)
                )
            } catch (e: Exception) {
                Log.e("AvatarRoomScreen", "Failed to launch system speech", e)
            }
            viewModel.onSystemSpeechFallbackConsumed()
        }
    }

    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val libGdxGameLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            viewModel.applyLibGdxGameResult(
                coins = data.getIntExtra(PawPal3DGameActivity.EXTRA_COINS, uiState.coins),
                hungerPercent = data.getFloatExtra(PawPal3DGameActivity.EXTRA_HUNGER, uiState.hungerLevel * 100f),
                happinessPercent = data.getFloatExtra(PawPal3DGameActivity.EXTRA_HAPPINESS, uiState.funLevel * 100f),
                energyPercent = data.getFloatExtra(PawPal3DGameActivity.EXTRA_ENERGY, uiState.energyLevel * 100f)
            )
        }
    }

    // Permission launcher for microphone recording
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val action = pendingMicAction
        pendingMicAction = null
        if (isGranted) {
            if (action != null) {
                action.invoke()
            } else {
                viewModel.startHandsFreeWakeListening()
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPermission) {
            viewModel.startHandsFreeWakeListening()
        } else {
            try {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } catch (e: Exception) {
                Log.w("AvatarRoomScreen", "Mic permission request skipped: ${e.message}")
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onAppPaused()
                Lifecycle.Event.ON_RESUME -> {
                    val hasAudioPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    viewModel.onAppResumed(hasAudioPermission)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launchFaceToFaceCall: () -> Unit = {
        try {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.startFaceToFaceCall()
            } else {
                pendingMicAction = { viewModel.startFaceToFaceCall() }
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } catch (e: Exception) {
            Log.e("AvatarRoomScreen", "Error launching face-to-face call", e)
            viewModel.startFaceToFaceCall()
        }
    }

    val launchVoiceInput: () -> Unit = {
        try {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.openVoiceInput()
            } else {
                pendingMicAction = { viewModel.openVoiceInput() }
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } catch (e: Exception) {
            Log.e("AvatarRoomScreen", "Error launching voice input", e)
            viewModel.openVoiceInput()
        }
    }

    val launchPronunciationRecording: () -> Unit = {
        try {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.togglePronunciationRecording()
            } else {
                pendingMicAction = { viewModel.togglePronunciationRecording() }
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } catch (e: Exception) {
            Log.e("AvatarRoomScreen", "Error launching pronunciation recording", e)
            viewModel.togglePronunciationRecording()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Dynamic Layered 3D Living Room Background Canvas
            DynamicBackgroundCanvas(
                theme = uiState.backgroundTheme,
                currentTimeOfDay = uiState.currentTimeOfDay,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Main Talking Tom Interactive UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Clean Top Room Bar (Character Switcher, Room Theme, History, Wardrobe & Settings)
                LiquidGlassTopRoomBar(
                    characterName = uiState.activeCharacter.name,
                    characterSpeciesEmoji = uiState.activeCharacter.species.emoji,
                    currentThemeIcon = uiState.backgroundTheme.icon,
                    coins = uiState.coins,
                    diamonds = uiState.diamonds,
                    onOpenCharacters = { showCharacterPicker = true },
                    onOpenThemes = { showThemeDialog = true },
                    onOpenChatHistory = { showChatHistory = true },
                    onOpenWardrobe = {
                        showWardrobe = true
                        coroutineScope.launch { wardrobeSheetState.show() }
                    },
                    onOpenSettings = { showSettings = true }
                )

                // Compact Continuous Microphone & Gemini Live Status Pill (tap to open Gemini Live sheet)
                GeminiLiveVoicePill(
                    characterName = uiState.activeCharacter.name,
                    characterEmoji = uiState.activeCharacter.species.emoji,
                    voiceSessionState = uiState.voiceSessionState,
                    isHandsFreeWakeEnabled = uiState.isHandsFreeWakeEnabled,
                    standbyRequireWakeWord = uiState.standbyRequireWakeWord,
                    isUserSpeakingNow = uiState.isUserSpeakingNow,
                    rmsLevel = uiState.speechRms,
                    voiceAmplitude = uiState.voiceAmplitude,
                    emotionalTone = uiState.activeEmotionalTone,
                    isTalking = uiState.isTalking,
                    isLoadingAi = uiState.isLoadingAi,
                    idleBehavior = uiState.activeIdleBehavior,
                    onClick = { launchVoiceInput() }
                )

                // Always show Conversation Speech Bubble so user clearly sees what was said & replied
                if (uiState.lastAvatarSpeech.isNotBlank() || uiState.isLoadingAi) {
                    LiquidGlassSpeechBubble(
                        speakerName = uiState.activeCharacter.name,
                        speakerEmoji = uiState.activeCharacter.species.emoji,
                        userSpeech = uiState.liveUserSpeech,
                        text = uiState.lastAvatarSpeech,
                        isTalking = uiState.isTalking,
                        isLoading = uiState.isLoadingAi,
                        emotionalTone = uiState.activeEmotionalTone,
                        onReplay = { viewModel.replayCurrentSpeech() }
                    )
                }

                // 3D Touchable Character Scene + Interactive 3D Room Props
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Alive 3D Character standing on the carpet
                    Avatar3DCanvas(
                        species = uiState.activeCharacter.species,
                        customization = uiState.customization,
                        emotion = uiState.currentEmotion,
                        isTalking = uiState.isTalking,
                        voiceAmplitude = uiState.voiceAmplitude,
                        onZoneTouched = { zone -> viewModel.onCharacterTouched(zone) },
                        backgroundTheme = uiState.backgroundTheme,
                        idleBehavior = uiState.activeIdleBehavior,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("avatar_interactive_canvas")
                    )

                    // Interactive 3D Room Props (Radio Lo-Fi, Bola Mainan, Lampu Tidur, Boneka, Camilan Dapur)
                    InteractiveRoomPropsOverlay(
                        theme = uiState.backgroundTheme,
                        isRadioPlaying = uiState.isLofiRadioPlaying,
                        isNightLampDimmed = uiState.isNightLampDimmed,
                        ballBounceCount = uiState.miniGameScore,
                        onPropTriggered = { action, food ->
                            viewModel.onRoomPropTriggered(action, food)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Button(
                        onClick = {
                            val launchIntent = Intent(context, PawPal3DGameActivity::class.java).apply {
                                putExtra(PawPal3DGameActivity.EXTRA_PET_NAME, uiState.activeCharacter.name)
                                putExtra(PawPal3DGameActivity.EXTRA_SPECIES, uiState.activeCharacter.species.name)
                                putExtra(PawPal3DGameActivity.EXTRA_FUR_COLOR, uiState.customization.furColor.toArgb())
                                putExtra(PawPal3DGameActivity.EXTRA_COINS, uiState.coins)
                                putExtra(PawPal3DGameActivity.EXTRA_HUNGER, uiState.hungerLevel * 100f)
                                putExtra(PawPal3DGameActivity.EXTRA_HAPPINESS, uiState.funLevel * 100f)
                                putExtra(PawPal3DGameActivity.EXTRA_ENERGY, uiState.energyLevel * 100f)
                                // Cleanliness is persisted by the libGDX game; do not reset it to 80 on each launch.
                            }
                            libGdxGameLauncher.launch(launchIntent)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                            .testTag("avatar_libgdx_3d_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF263B55),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("GAME 3D", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Quick Keyboard Text Chat Input Bar (toggled by 💬 in bottom dock)
                AnimatedVisibility(
                    visible = showChatInput,
                    enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    LiquidGlassBottomChatInputBar(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        isLoading = uiState.isLoadingAi,
                        onSend = {
                            val clean = inputText.trim()
                            if (clean.isNotBlank()) {
                                inputText = ""
                                viewModel.sendMessage(clean)
                            }
                        }
                    )
                }

                // Clean Streamlined Bottom Action Dock: Bicara Live, Video Call Live, Mode Tugas Sekolah, Aktivitas Seru & Chat
                CleanBottomActionDock(
                    onVoiceClick = { launchVoiceInput() },
                    isVoiceListening = uiState.isUserSpeakingNow || uiState.showVoiceDialog,
                    onVideoCallClick = { launchFaceToFaceCall() },
                    onHomeworkClick = { viewModel.openHomeworkMode() },
                    onFunActivitiesClick = { viewModel.openFunActivities() },
                    onChatToggle = { showChatInput = !showChatInput },
                    isChatActive = showChatInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // Live Voice Input Modal Dialog with Crash Protection
        if (uiState.showVoiceDialog) {
            VoiceInputDialog(
                isListening = uiState.isListeningMic,
                liveTranscript = uiState.speechTranscript,
                rmsLevel = uiState.speechRms,
                characterName = uiState.activeCharacter.name,
                isUserSpeakingNow = uiState.isUserSpeakingNow,
                isLoadingAi = uiState.isLoadingAi,
                isServiceUnavailable = uiState.speechServiceUnavailable,
                onLaunchSystemSpeech = {
                    try {
                        systemSpeechLauncher.launch(
                            SpeechRecognitionHelper.createSystemSpeechIntent(uiState.selectedLanguage)
                        )
                    } catch (e: Exception) {
                        Log.e("AvatarRoomScreen", "Failed to launch system speech", e)
                    }
                },
                onSendTranscript = { text ->
                    viewModel.sendVoiceTranscript(text)
                },
                onCancel = {
                    viewModel.closeVoiceInput()
                }
            )
        }

        // Modals & Sheets
        if (showWardrobe) {
            WardrobeSheet(
                sheetState = wardrobeSheetState,
                customization = uiState.customization,
                currentBackgroundTheme = uiState.backgroundTheme,
                coins = uiState.coins,
                diamonds = uiState.diamonds,
                unlockedCostumes = uiState.unlockedCostumes,
                unlockedAccessories = uiState.unlockedAccessories,
                unlockedThemes = uiState.unlockedThemes,
                shopFeedbackMessage = uiState.shopFeedbackMessage,
                onCustomizationChanged = { viewModel.updateCustomization(it) },
                onSelectBackgroundTheme = { theme -> viewModel.setBackgroundTheme(theme) },
                onDismiss = {
                    viewModel.clearShopFeedback()
                    coroutineScope.launch {
                        if (wardrobeSheetState.isVisible) {
                            wardrobeSheetState.hide()
                        }
                        showWardrobe = false
                    }
                }
            )
        }

        if (showThemeDialog) {
            BackgroundThemeDialog(
                currentTheme = uiState.backgroundTheme,
                currentTimeOfDay = uiState.currentTimeOfDay,
                coins = uiState.coins,
                diamonds = uiState.diamonds,
                unlockedThemes = uiState.unlockedThemes,
                shopFeedbackMessage = uiState.shopFeedbackMessage,
                onSelectTheme = { theme ->
                    if (viewModel.setBackgroundTheme(theme)) {
                        showThemeDialog = false
                    }
                },
                onDismiss = {
                    viewModel.clearShopFeedback()
                    showThemeDialog = false
                }
            )
        }

        if (showCharacterPicker) {
            CharacterPickerDialog(
                currentCharacter = uiState.activeCharacter,
                onSelectCharacter = { char ->
                    viewModel.selectCharacter(char)
                    showCharacterPicker = false
                },
                onPreviewVoice = { char ->
                    viewModel.previewCharacterVoice(char)
                },
                onDismiss = { showCharacterPicker = false }
            )
        }

        if (showSettings) {
            SettingsDialog(
                selectedLanguage = uiState.selectedLanguage,
                onLanguageSelected = { viewModel.setLanguage(it) },
                userNickname = uiState.userNickname,
                onSaveNickname = { viewModel.saveUserNickname(it) },
                memoryCount = uiState.memories.size,
                onOpenMemories = {
                    showSettings = false
                    showMemoryProfile = true
                },
                signedInEmail = uiState.signedInEmail,
                signedInDisplayName = uiState.signedInDisplayName,
                isCloudSyncing = uiState.isCloudSyncing,
                cloudSyncStatus = uiState.cloudSyncStatus,
                onGoogleSignedIn = { email, name -> viewModel.onGoogleSignedIn(email, name) },
                onSyncNow = { viewModel.syncNowToCloud() },
                onSignedOut = { viewModel.onSignedOut() },
                dailyRemindersEnabled = uiState.dailyRemindersEnabled,
                onToggleDailyReminders = { enabled -> viewModel.setDailyRemindersEnabled(enabled) },
                onTestReminderNotification = { viewModel.sendTestCheckInNotification() },
                conversationRemindersEnabled = uiState.conversationRemindersEnabled,
                onToggleConversationReminders = { enabled -> viewModel.setConversationRemindersEnabled(enabled) },
                conversationReminders = uiState.conversationReminders,
                onRemoveConversationReminder = { id -> viewModel.removeConversationReminder(id) },
                isHandsFreeWakeEnabled = uiState.isHandsFreeWakeEnabled,
                onToggleHandsFreeWake = { enabled -> viewModel.setHandsFreeWakeEnabled(enabled) },
                standbyRequireWakeWord = uiState.standbyRequireWakeWord,
                onStandbyRequireWakeWordChanged = { req -> viewModel.setStandbyRequireWakeWord(req) },
                micSensitivityHigh = uiState.micSensitivityHigh,
                onMicSensitivityHighChanged = { high -> viewModel.setMicSensitivityHigh(high) },
                allowBargeInWhileAiSpeaking = uiState.allowBargeInWhileAiSpeaking,
                onAllowBargeInChanged = { allowed -> viewModel.setAllowBargeInWhileAiSpeaking(allowed) },
                onTestVoice = { viewModel.testVoice() },
                onClearHistory = {
                    viewModel.clearHistory()
                    showSettings = false
                },
                onDismiss = { showSettings = false }
            )
        }

        if (showChatHistory) {
            ChatHistoryDialog(
                character = uiState.activeCharacter,
                messages = uiState.messages,
                selectedModelTier = uiState.selectedModelTier,
                isLoading = uiState.isLoadingAi,
                onSelectModelTier = { viewModel.setModelTier(it) },
                onSendMessage = { viewModel.sendMessage(it) },
                onOpenVoiceInput = { launchVoiceInput() },
                onClearHistory = { viewModel.clearHistory() },
                onSpeakMessage = { viewModel.speakText(it) },
                onDismiss = { showChatHistory = false }
            )
        }

        if (showMemoryProfile) {
            MemoryProfileDialog(
                characterName = uiState.activeCharacter.name,
                characterEmoji = uiState.activeCharacter.species.emoji,
                userNickname = uiState.userNickname,
                memories = uiState.memories,
                onSaveNickname = { viewModel.saveUserNickname(it) },
                onAddMemory = { category, content -> viewModel.addMemoryFact(category, content) },
                onDeleteMemory = { factId -> viewModel.deleteMemoryFact(factId) },
                onClearAllMemories = { viewModel.clearAllMemories() },
                onDismiss = { showMemoryProfile = false }
            )
        }

        if (uiState.showFunActivitiesDialog) {
            FunActivitiesDialog(
                character = uiState.activeCharacter,
                currentTimeOfDay = uiState.currentTimeOfDay,
                activeTab = uiState.activeFunTab,
                onSelectTab = { viewModel.selectFunTab(it) },
                selectedMood = uiState.selectedMood,
                moodJournalNote = uiState.moodJournalNote,
                moodJournalEntries = uiState.moodJournalEntries,
                dailyQuests = viewModel.getDailyCompanionQuests(),
                isGeneratingMoodReply = uiState.isGeneratingMoodReply,
                onSelectMood = { viewModel.selectMoodType(it) },
                onMoodNoteChange = { viewModel.updateMoodJournalNote(it) },
                onSubmitMoodJournal = { viewModel.submitMoodJournal() },
                onDeleteMoodEntry = { viewModel.deleteMoodJournalEntry(it) },
                rpsRound = uiState.rpsRound,
                rpsWins = uiState.rpsWins,
                rpsDraws = uiState.rpsDraws,
                rpsLosses = uiState.rpsLosses,
                onPlayRps = { viewModel.playRockPaperScissors(it) },
                quizQuestion = uiState.currentQuizQuestion,
                selectedQuizOption = uiState.selectedQuizOption,
                quizScore = uiState.quizScore,
                isGeneratingQuiz = uiState.isGeneratingQuiz,
                onGenerateQuiz = { topic -> viewModel.generateNewQuiz(topic) },
                onAnswerQuiz = { viewModel.answerQuizOption(it) },
                selectedStoryTheme = uiState.selectedStoryTheme,
                currentStoryNode = uiState.currentStoryNode,
                isGeneratingStory = uiState.isGeneratingStory,
                savedStoriesCount = uiState.savedStorySessions.size,
                onStartStory = { viewModel.startInteractiveStory(it) },
                onChooseStoryBranch = { chosenText ->
                    viewModel.chooseStoryBranch(chosenText)
                },
                onOpenFullStoryStage = { viewModel.openStoryStageMode() },
                waterGlassesToday = uiState.waterGlassesToday,
                stretchCountToday = uiState.stretchCountToday,
                onTriggerHealthyHabit = { viewModel.triggerHealthyHabit(it) },
                onTriggerTimeGreeting = { viewModel.triggerTimeOfDayGreeting() },
                tutorLanguage = uiState.tutorLanguage,
                tutorScenario = uiState.tutorScenario,
                tutorPrompt = uiState.tutorPrompt,
                pronunciationEvaluation = uiState.pronunciationEvaluation,
                isGeneratingTutor = uiState.isGeneratingTutor,
                isRecordingPronunciation = uiState.isRecordingPronunciation,
                isEvaluatingPronunciation = uiState.isEvaluatingPronunciation,
                onSelectTutorLanguage = { viewModel.selectTutorLanguage(it) },
                onSelectTutorScenario = { viewModel.selectTutorScenario(it) },
                onGenerateTutorChallenge = { viewModel.generateTutorChallenge() },
                onSpeakTutorPhrase = { phrase -> viewModel.speakTutorPhrase(phrase) },
                onTogglePronunciationRecording = { launchPronunciationRecording() },
                onSpeakText = { viewModel.speakText(it) },
                onDismiss = { viewModel.closeFunActivities() }
            )
        }

        // Fullscreen Face-to-Face Live Conversational Call Overlay
        AnimatedVisibility(
            visible = uiState.isFaceToFaceActive,
            enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { it })
        ) {
            BackHandler(enabled = uiState.isFaceToFaceActive) {
                viewModel.endFaceToFaceCall()
            }

            FaceToFaceCallView(
                uiState = uiState,
                onEndCall = { viewModel.endFaceToFaceCall() },
                onToggleMute = { viewModel.toggleFaceToFaceMute() },
                onInterruptOrSpeak = { viewModel.interruptOrSpeak() },
                onQuickPrompt = { prompt -> viewModel.quickPromptFaceToFace(prompt) },
                onSwitchCharacter = { char -> viewModel.switchCharacterInFaceToFace(char) },
                onPreviewVoice = { char -> viewModel.previewCharacterVoice(char) },
                onPetAvatar = { viewModel.petAvatar() },
                onCameraFrameUpdated = { bitmap -> viewModel.updateLiveCameraFrame(bitmap) },
                onAskCameraVision = { viewModel.askCameraVisionInCall() }
            )
        }

        // Fullscreen Mode Ngerjain Tugas Sekolah Overlay
        AnimatedVisibility(
            visible = uiState.showHomeworkMode,
            enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { it })
        ) {
            BackHandler(enabled = uiState.showHomeworkMode) {
                viewModel.closeHomeworkMode()
            }

            HomeworkStudyView(
                character = uiState.activeCharacter,
                selectedSubject = uiState.homeworkSubject,
                questionText = uiState.homeworkQuestion,
                solution = uiState.homeworkSolution,
                isSolving = uiState.isSolvingHomework,
                photoBitmap = uiState.homeworkPhotoBitmap,
                isGeminiConnected = viewModel.isGeminiConnected(),
                currentApiKey = viewModel.getCustomApiKey(),
                onSaveApiKey = { viewModel.setCustomApiKey(it) },
                onSelectSubject = { viewModel.setHomeworkSubject(it) },
                onQuestionChange = { viewModel.setHomeworkQuestion(it) },
                onSetPhoto = { viewModel.setHomeworkPhoto(it) },
                onSolve = { viewModel.solveHomework(it) },
                onSpeakExplanation = { viewModel.speakText(it) },
                onClose = { viewModel.closeHomeworkMode() },
                onOpenVoiceInput = { launchVoiceInput() }
            )
        }

        // Fullscreen 3D Interactive Story Theater ("Panggung Dongeng Petualangan") Overlay
        AnimatedVisibility(
            visible = uiState.showStoryStageMode,
            enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { it })
        ) {
            BackHandler(enabled = uiState.showStoryStageMode) {
                viewModel.closeStoryStageMode()
            }

            InteractiveStoryStageView(
                character = uiState.activeCharacter,
                customization = uiState.customization,
                isTalking = uiState.isTalking,
                activeIdleBehavior = uiState.activeIdleBehavior,
                selectedTheme = uiState.selectedStoryTheme,
                currentNode = uiState.currentStoryNode,
                inventory = uiState.storyInventory,
                storyHistoryLog = uiState.storyHistoryLog,
                savedSessions = uiState.savedStorySessions,
                isGenerating = uiState.isGeneratingStory,
                onSelectThemeAndStart = { theme -> viewModel.startInteractiveStory(theme) },
                onChooseAction = { actionText -> viewModel.chooseStoryBranch(actionText) },
                onStartNewStory = { viewModel.startInteractiveStory(uiState.selectedStoryTheme) },
                onResumeSavedSession = { session -> viewModel.resumeSavedStorySession(session) },
                onDeleteSavedSession = { sessionId -> viewModel.deleteSavedStorySession(sessionId) },
                onSpeakNarrative = { text -> viewModel.speakText(text) },
                onOpenVoiceAction = { launchVoiceInput() },
                onCharacterTouched = { zone -> viewModel.onCharacterTouched(zone) },
                onClose = { viewModel.closeStoryStageMode() }
            )
        }
    }
}

/**
 * Clean Modern Liquid Glass Top Bar (Character, Room Theme, Wallet Balance, History, Wardrobe, Settings)
 */
@Composable
private fun LiquidGlassTopRoomBar(
    characterName: String,
    characterSpeciesEmoji: String,
    currentThemeIcon: String,
    coins: Int,
    diamonds: Int,
    onOpenCharacters: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenChatHistory: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LiquidGlassSurface(
        shape = RoundedCornerShape(26.dp),
        backgroundColor = Color(0x45151D33),
        elevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("top_room_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left: Character quick switcher & Room Theme button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Character quick pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(onClick = onOpenCharacters)
                            .background(Color(0x30FFFFFF))
                            .border(BorderStroke(1.dp, Color(0x40FFFFFF)), RoundedCornerShape(18.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("switch_character_button")
                    ) {
                        Text(text = characterSpeciesEmoji, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = characterName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Ganti >",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = Color(0xFF93C5FD)
                                )
                            )
                        }
                    }

                    // Theme Switcher Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenThemes)
                            .background(Color(0x28FFFFFF))
                            .border(BorderStroke(1.dp, Color(0x38FFFFFF)), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("open_theme_button")
                    ) {
                        Text(text = currentThemeIcon, fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Ruangan",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Right: Chat History, Wardrobe & Settings action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onOpenChatHistory,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .testTag("open_chat_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Riwayat Chat",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenWardrobe,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .testTag("open_wardrobe_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Style,
                            contentDescription = "Kostum & Aksesoris",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pengaturan",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Compact Economy Balance Strip (tap to open Wardrobe Shop)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenWardrobe)
                    .background(Color(0x220F172A))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("economy_balance_strip")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "⭐ $coins Koin",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFFDE68A),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "💎 $diamonds Berlian",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF7DD3FC),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Text(
                    text = "Toko Kostum >",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFC7D2FE),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

/**
 * Modern Floating Liquid Glass Speech Bubble showing both user's latest prompt/speech and character's response
 */
@Composable
private fun LiquidGlassSpeechBubble(
    speakerName: String,
    speakerEmoji: String,
    userSpeech: String = "",
    text: String,
    isTalking: Boolean,
    isLoading: Boolean,
    emotionalTone: String = "",
    onReplay: () -> Unit
) {
    if (text.isBlank() && !isLoading) return

    LiquidGlassSurface(
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x52131B2E),
        elevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("speech_bubble_view")
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Optional User Speech / Question Header so user clearly sees what was heard
            if (userSpeech.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x3538BDF8),
                    border = BorderStroke(1.dp, Color(0x5538BDF8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🗣️ Kamu:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF7DD3FC),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "\"$userSpeech\"",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White,
                                fontSize = 12.sp
                            ),
                            maxLines = 2
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = speakerEmoji, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = speakerName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF93C5FD)
                        )
                    )
                    val hasAudioError = emotionalTone.contains("⚠️") || emotionalTone.contains("gagal", ignoreCase = true)
                    if (isTalking || hasAudioError) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                hasAudioError -> emotionalTone
                                emotionalTone.isNotBlank() -> "● $emotionalTone"
                                else -> "● Streaming Suara AI..."
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (hasAudioError) Color(0xFFFBBF24) else Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onReplay,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Putar ulang suara",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF93C5FD)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sedang berpikir...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp
                        )
                    )
                }
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
}

/**
 * Modern Liquid Glass Bottom Chat Input Bar (Keyboard text chat only; voice is 100% hands-free via "Petto" wake word)
 */
@Composable
private fun LiquidGlassBottomChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit
) {
    LiquidGlassSurface(
        shape = RoundedCornerShape(26.dp),
        backgroundColor = Color(0x40131B2E),
        elevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("bottom_chat_input_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Liquid Glass text field
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        "Ketik pesan ke sahabat 3D...",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF818CF8),
                    unfocusedBorderColor = Color(0x35FFFFFF),
                    focusedContainerColor = Color(0x280F172A),
                    unfocusedContainerColor = Color(0x200F172A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_text_input_field")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send button
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLoading) Color(0x606366F1)
                        else if (inputText.isNotBlank()) Color(0xFF6366F1)
                        else Color(0x20FFFFFF)
                    )
                    .testTag("send_message_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Kirim",
                        tint = if (inputText.isNotBlank()) Color.White else Color(0x60FFFFFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Continuous Background Wake-Word ("Petto") & Gemini Live Session Pill (No conversation text).
 */
@Composable
private fun GeminiLiveVoicePill(
    characterName: String,
    characterEmoji: String,
    voiceSessionState: VoiceSessionState,
    isHandsFreeWakeEnabled: Boolean = true,
    standbyRequireWakeWord: Boolean = true,
    isUserSpeakingNow: Boolean,
    rmsLevel: Float,
    voiceAmplitude: Float = 0f,
    emotionalTone: String = "",
    isTalking: Boolean,
    isLoadingAi: Boolean,
    idleBehavior: IdlePetBehavior = IdlePetBehavior.NONE,
    onClick: () -> Unit = {}
) {
    val isWakeResponding = voiceSessionState == VoiceSessionState.WAKE_RESPONDING
    val isLiveListening = voiceSessionState == VoiceSessionState.GEMINI_LIVE_LISTENING
    val isThinking = voiceSessionState == VoiceSessionState.GEMINI_LIVE_THINKING || isLoadingAi
    val isSpeaking = voiceSessionState == VoiceSessionState.GEMINI_LIVE_SPEAKING || isTalking
    val hasAudioError = !isSpeaking && !isThinking &&
        (emotionalTone.contains("⚠️") || emotionalTone.contains("gagal", ignoreCase = true))

    val statusColor = when {
        hasAudioError -> Color(0xFFF59E0B)
        !isHandsFreeWakeEnabled && !isSpeaking && !isThinking -> Color(0xFF64748B)
        isWakeResponding -> Color(0xFFF59E0B)
        isLiveListening -> Color(0xFF10B981)
        isThinking -> Color(0xFFA855F7)
        isSpeaking -> Color(0xFF38BDF8)
        isUserSpeakingNow -> Color(0xFF34D399)
        idleBehavior != IdlePetBehavior.NONE -> Color(0xFFF59E0B)
        else -> Color(0xFF38BDF8)
    }

    val statusLabel = when {
        isSpeaking -> if (emotionalTone.isNotBlank()) {
            "$characterName • $emotionalTone"
        } else {
            "Gemini Audio Stream • $characterName berbicara..."
        }
        isThinking -> "✨ Gemini Live • Memproses suaramu..."
        hasAudioError -> emotionalTone
        isWakeResponding -> "🔔 Chime! Langsung bicara sekarang..."
        isUserSpeakingNow -> "🎙️ Mendengarkanmu... (Otomatis kirim saat berhenti)"
        isLiveListening -> "🎙️ Gemini Live Aktif • Langsung bicara sekarang!"
        !isHandsFreeWakeEnabled -> "🔇 Mic Standby Nonaktif • Ketuk Bicara"
        idleBehavior != IdlePetBehavior.NONE -> if (standbyRequireWakeWord) {
            "🎙️ Sebut 'Petto' ke $characterName (${idleBehavior.displayLabel.lowercase()})"
        } else {
            "🎙️ Langsung bicara ke $characterName (${idleBehavior.displayLabel.lowercase()})"
        }
        standbyRequireWakeWord -> "🎙️ Standby • Sebut 'Petto' untuk memanggil"
        else -> "🎙️ Standby • Langsung bicara kapan saja"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "liveWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wavePhase"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("gemini_live_voice_pill")
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xCC0F172A),
            border = BorderStroke(
                width = if (isLiveListening || isSpeaking || isUserSpeakingNow) 1.5.dp else 1.dp,
                color = statusColor.copy(alpha = if (isLiveListening || isSpeaking || isUserSpeakingNow) 0.85f else 0.55f)
            ),
            shadowElevation = 6.dp,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(text = characterEmoji, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                )

                if (isLiveListening || isSpeaking || isUserSpeakingNow) {
                    Spacer(modifier = Modifier.width(10.dp))
                    val activeEnergy = if (isSpeaking && voiceAmplitude > 0.02f) {
                        voiceAmplitude.coerceIn(0.25f, 1f)
                    } else {
                        (rmsLevel / 10f).coerceIn(0.25f, 1f)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val multipliers = listOf(0.6f, 1.0f, 0.75f, 0.9f)
                        multipliers.forEach { mult ->
                            val barHeight = (6f + 11f * activeEnergy * wavePhase * mult).dp
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(statusColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Streamlined Clean Bottom Action Dock:
 * - Live Video Call with Front Camera PiP
 * - Mode Ngerjain Tugas Sekolah (Homework & Study Companion)
 * - Aktivitas Seru (Mini Game, Dongeng Interaktif, Rutinitas Sehat)
 * - Keyboard Text Chat Toggle
 * (No manual voice button needed — continuous background "Petto" wake detector handles hands-free Gemini Live)
 */
@Composable
private fun CleanBottomActionDock(
    onVoiceClick: () -> Unit,
    isVoiceListening: Boolean = false,
    onVideoCallClick: () -> Unit,
    onHomeworkClick: () -> Unit,
    onFunActivitiesClick: () -> Unit,
    onChatToggle: () -> Unit,
    isChatActive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xD90F172A),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        shadowElevation = 12.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            // 1. Gemini Live Voice Button (Tap to Speak / Flush)
            Button(
                onClick = onVoiceClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVoiceListening) Color(0xFF059669) else Color(0xFF0284C7),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 10.dp),
                modifier = Modifier.testTag("action_voice_live_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Bicara Gemini Live",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Bicara",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 2. Live Video Call (WhatsApp style)
            Button(
                onClick = onVideoCallClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 10.dp),
                modifier = Modifier.testTag("action_video_call_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Call",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 3. Mode Ngerjain Tugas Sekolah
            Button(
                onClick = onHomeworkClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 10.dp),
                modifier = Modifier.testTag("action_homework_button")
            ) {
                Text(text = "📚", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Tugas",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 4. Aktivitas Seru (Mini Game, Dongeng, Rutinitas Sehat)
            Button(
                onClick = onFunActivitiesClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD97706),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 10.dp),
                modifier = Modifier.testTag("action_fun_activities_button")
            ) {
                Text(text = "✨", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Seru",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 5. Chat Keyboard Toggle
            IconButton(
                onClick = onChatToggle,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isChatActive) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.12f))
                    .testTag("action_chat_toggle_button")
            ) {
                Text(text = "💬", fontSize = 15.sp)
            }
        }
    }
}
