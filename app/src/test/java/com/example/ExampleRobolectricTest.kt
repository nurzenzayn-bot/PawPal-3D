package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiChatService
import com.example.game.PetGameSession
import com.example.model.AppLanguage
import com.example.model.BackgroundTheme
import com.example.model.CharacterCatalog
import com.example.model.PetSpecies
import com.example.model.TimeOfDay
import com.example.model.TouchZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PawPal AI", appName)
    }

    @Test
    fun `verify cartoon room themes have valid drawable resources`() {
        assertEquals(R.drawable.img_bg_cartoon_livingroom, BackgroundTheme.LIVING_ROOM.imageRes)
        assertEquals(R.drawable.img_bg_cartoon_bedroom, BackgroundTheme.BEDROOM.imageRes)
        assertEquals(R.drawable.img_bg_cartoon_kitchen, BackgroundTheme.KITCHEN.imageRes)

        assertEquals(R.drawable.img_bg_cartoon_livingroom, BackgroundTheme.AUTO_REALTIME.resolveDrawableRes(TimeOfDay.DAY))
        assertEquals(R.drawable.img_bg_cartoon_bedroom, BackgroundTheme.AUTO_REALTIME.resolveDrawableRes(TimeOfDay.NIGHT))
        assertEquals(R.drawable.img_bg_cartoon_kitchen, BackgroundTheme.AUTO_REALTIME.resolveDrawableRes(TimeOfDay.SUNSET))
    }

    @Test
    fun `model tier router selects argon for complex why and analysis questions`() {
        val aiService = GeminiChatService()
        val tier = aiService.resolveModelTier("kenapa setiap orang punya masalah", com.example.model.GeminiModelTier.AUTO)
        assertEquals(com.example.model.GeminiModelTier.ARGON_COMPLEX, tier)
    }

    @Test
    fun `connection error reply is transparent and does not repeat question as template`() {
        val aiService = GeminiChatService()
        val zayn = CharacterCatalog.getById("zayn")
        val reply = aiService.createAiConnectionErrorReply(zayn, "Network unreachable")
        assertNotNull(reply)
        assertTrue(reply.text.contains("Gemini AI"))
        assertEquals(false, reply.isFromAi)
    }

    @Test
    fun `touch zones generate valid responses for all characters`() {
        for (char in CharacterCatalog.characters) {
            assertNotNull(char.headTouchResponse)
            assertNotNull(char.bellyTouchResponse)
            assertNotNull(char.handsTouchResponse)
            assertNotNull(char.feetTouchResponse)
            assertTrue(char.headTouchResponse.isNotBlank())
            assertTrue(char.bellyTouchResponse.isNotBlank())
            assertTrue(char.handsTouchResponse.isNotBlank())
            assertTrue(char.feetTouchResponse.isNotBlank())
        }
    }

    @Test
    fun `audio synthesizer generates non-empty audio without crash`() {
        for (species in PetSpecies.values()) {
            for (zone in TouchZone.values()) {
                com.example.audio.ExpressiveAudioSynthesizer.playTouchReactionSound(species, zone)
            }
        }
    }

    @Test
    fun `talking tom care system handles food and feeding`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.AvatarViewModel(context)

        val initialHunger = vm.uiState.value.hungerLevel
        vm.feedPet(com.example.model.FoodItem.FISH)
        assertTrue(vm.uiState.value.hungerLevel >= initialHunger)
        assertTrue(vm.uiState.value.careFeedbackMessage.contains("Kenyang"))
    }

    @Test
    fun `bouncing ball game increases fun and coins`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.AvatarViewModel(context)

        val initialCoins = vm.uiState.value.coins
        val initialScore = vm.uiState.value.miniGameScore
        vm.playBallBounce()
        assertEquals(initialScore + 1, vm.uiState.value.miniGameScore)
        assertEquals(initialCoins + 10, vm.uiState.value.coins)
    }

    @Test
    fun `mimic mode toggles properly`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.AvatarViewModel(context)

        val initialMode = vm.uiState.value.isMimicModeActive
        vm.toggleMimicMode()
        assertEquals(!initialMode, vm.uiState.value.isMimicModeActive)
    }

    @Test
    fun `homework connection error solution is transparent and marks isFromAi false`() {
        val aiService = GeminiChatService()
        val jamal = CharacterCatalog.getById("jamal")

        val errorSolution = aiService.createHomeworkConnectionErrorSolution("Tugas Umum", "kenapa setiap orang punya masalah", jamal)
        assertEquals(false, errorSolution.isFromAi)
        assertTrue(errorSolution.finalAnswer.contains("Coba Lagi"))
    }

    @Test
    fun `speech to wake parser preserves isWakeOnly and filters non-wake ambient talk in strict standby`() {
        val aiService = GeminiChatService()
        val zayn = CharacterCatalog.getById("zayn")

        val wakeOnlyJson = """{"wakeDetected":true,"isWakeOnly":true,"transcript":"Hey Petto","replyText":"Ya ada apa?","emotion":"SURPRISED"}"""
        val wakeOnlyResult = aiService.parseVoiceAudioJson(wakeOnlyJson, zayn, strictStandbyWake = true)
        assertNotNull(wakeOnlyResult)
        assertEquals(true, wakeOnlyResult?.wakeDetected)
        assertEquals(true, wakeOnlyResult?.isWakeOnly)
        assertEquals("", wakeOnlyResult?.replyText)

        val ambientJson = """{"wakeDetected":false,"isWakeOnly":false,"transcript":"Suara berita di televisi sore ini","replyText":"","emotion":"TALKING"}"""
        val strictResult = aiService.parseVoiceAudioJson(ambientJson, zayn, strictStandbyWake = true)
        assertNotNull(strictResult)
        assertEquals(false, strictResult?.wakeDetected)

        val directResult = aiService.parseVoiceAudioJson(ambientJson, zayn, strictStandbyWake = false)
        assertNotNull(directResult)
        assertEquals(true, directResult?.wakeDetected)

        val plainTextResponse = "Hmph! Tentu saja aku mendengarmu dengan jelas, mau ngobrolin apa kita hari ini?"
        val plainParsed = aiService.parseVoiceAudioJson(plainTextResponse, zayn, strictStandbyWake = false)
        assertNotNull(plainParsed)
        assertEquals(true, plainParsed?.wakeDetected)
        assertEquals(false, plainParsed?.isWakeOnly)
        assertEquals(plainTextResponse, plainParsed?.replyText)
    }

    @Test
    fun `avatar view model wake settings update state and callHeyPetto enters wake responding`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.AvatarViewModel(context)

        vm.setStandbyRequireWakeWord(false)
        assertEquals(false, vm.uiState.value.standbyRequireWakeWord)

        vm.setMicSensitivityHigh(true)
        assertEquals(true, vm.uiState.value.micSensitivityHigh)

        vm.callHeyPetto()
        assertEquals(com.example.ui.VoiceSessionState.WAKE_RESPONDING, vm.uiState.value.voiceSessionState)
        assertEquals(true, vm.uiState.value.isWakeCommandListening)
        assertEquals("", vm.uiState.value.lastAvatarSpeech)
    }

    @Test
    fun `libGDX pet session clamps invalid stats before the scene starts`() {
        val session = PetGameSession(
            petName = "Mochi",
            species = "CAT",
            furColorArgb = 0xFFFF8A3D.toInt(),
            coins = -8,
            hunger = 145f,
            happiness = -12f,
            energy = 101f,
            cleanliness = 44f
        )

        session.normalize()

        assertEquals(0, session.coins)
        assertEquals(100f, session.hunger, 0.0001f)
        assertEquals(0f, session.happiness, 0.0001f)
        assertEquals(100f, session.energy, 0.0001f)
        assertEquals(44f, session.cleanliness, 0.0001f)
    }

    @Test
    fun `libGDX game result updates the original pet care state and wallet`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.AvatarViewModel(context)

        vm.applyLibGdxGameResult(
            coins = 321,
            hungerPercent = 61f,
            happinessPercent = 74f,
            energyPercent = 42f
        )

        assertEquals(321, vm.uiState.value.coins)
        assertEquals(0.61f, vm.uiState.value.hungerLevel, 0.0001f)
        assertEquals(0.74f, vm.uiState.value.funLevel, 0.0001f)
        assertEquals(0.42f, vm.uiState.value.energyLevel, 0.0001f)
        assertEquals(321, context.getSharedPreferences("pawpal_ai_prefs", Context.MODE_PRIVATE).getInt("economy_coins_v1", -1))
    }

    @Test
    fun `pet memory manager retains newest 25 memories and canonicalizes nickname category`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.model.PetMemoryManager(context)
        manager.clearAllMemories()

        for (i in 1..30) {
            Thread.sleep(2)
            manager.addOrUpdateFact("Hobi & Minat", "Fakta ke-$i")
        }

        val memories = manager.getMemories()
        assertEquals(25, memories.size)
        assertTrue(memories.any { it.content == "Fakta ke-30" })
        assertTrue(memories.none { it.content == "Fakta ke-1" })

        manager.addOrUpdateFact("Nama Panggilan", "Nama panggilan pengguna adalah Budi")
        manager.addOrUpdateFact("Panggilan", "Nama panggilan pengguna adalah Andi")
        val nicknameFacts = manager.getMemories().filter {
            it.category == "Panggilan" || it.category == "Nama Panggilan"
        }
        assertEquals(1, nicknameFacts.size)
        assertEquals("Panggilan", nicknameFacts.first().category)
        assertTrue(nicknameFacts.first().content.contains("Andi"))

        val upserted = manager.upsertFactFromCloud(
            id = "cloud_doc_99",
            category = "Nama Panggilan",
            content = "Nama panggilan pengguna adalah Citra",
            timestampMs = System.currentTimeMillis() + 5000L
        )
        val cloudNickname = upserted.first { it.id == "cloud_doc_99" }
        assertEquals("Panggilan", cloudNickname.category)
        assertTrue(cloudNickname.content.contains("Citra"))
    }

    @Test
    fun `feedPet rejects purchase when coins insufficient and persists coin changes`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = context.getSharedPreferences("pawpal_ai_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("economy_coins_v1", 10).commit()

        val vm = com.example.ui.AvatarViewModel(context)
        assertEquals(10, vm.uiState.value.coins)
        val hungerBefore = vm.uiState.value.hungerLevel

        vm.feedPet(com.example.model.FoodItem.BURGER)
        assertEquals(10, vm.uiState.value.coins)
        assertEquals(hungerBefore, vm.uiState.value.hungerLevel, 0.0001f)
        assertTrue(vm.uiState.value.careFeedbackMessage.contains("Koin kurang"))

        vm.playBallBounce()
        assertEquals(20, vm.uiState.value.coins)

        val reloadedVm = com.example.ui.AvatarViewModel(context)
        assertEquals(20, reloadedVm.uiState.value.coins)
    }

    @Test
    fun `language selection and healthy habits daily rollover persist across viewmodel instances`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = context.getSharedPreferences("pawpal_ai_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("healthy_habits_date_v1", "2020-01-01")
            .putInt("water_glasses_today", 6)
            .putInt("stretch_count_today", 4)
            .commit()

        val vm = com.example.ui.AvatarViewModel(context)
        assertEquals(0, vm.uiState.value.waterGlassesToday)
        assertEquals(0, vm.uiState.value.stretchCountToday)

        vm.setLanguage(AppLanguage.JAPANESE)
        assertEquals(AppLanguage.JAPANESE, vm.uiState.value.selectedLanguage)

        val reloadedVm = com.example.ui.AvatarViewModel(context)
        assertEquals(AppLanguage.JAPANESE, reloadedVm.uiState.value.selectedLanguage)

        vm.clearHistory()
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test
    fun `mood journal and story repositories merge cloud entries without duplicates`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val moodRepo = com.example.data.MoodJournalRepository(context)
        val localEntry = com.example.model.MoodJournalEntry(
            id = "m_local",
            timestampEpochMs = 1000L,
            dateFormatted = "01 Jan 2026",
            mood = com.example.model.MoodType.HAPPY,
            userNote = "Senang",
            characterId = "zayn",
            characterName = "Zayn",
            characterEmoji = "🐱",
            aiReply = "Mantap!",
            suggestedActivityTip = "Minum air"
        )
        moodRepo.addEntry(localEntry)
        val mergedMoods = moodRepo.mergeEntriesFromCloud(
            listOf(
                localEntry,
                localEntry.copy(id = "m_cloud", timestampEpochMs = 2000L, userNote = "Dari cloud")
            )
        )
        assertEquals(2, mergedMoods.size)
        assertEquals("m_cloud", mergedMoods.first().id)

        val storyRepo = com.example.data.StoryAdventureRepository(context)
        val node = com.example.model.StoryNode(
            chapterNumber = 1,
            maxChapters = 5,
            title = "Bab 1",
            narrative = "Petualangan dimulai",
            petReactionComment = "Ayo!",
            sceneEmotion = com.example.model.AvatarEmotion.HAPPY,
            choiceA = "Kiri",
            choiceB = "Kanan"
        )
        val s1 = com.example.model.SavedStorySession("s1", com.example.model.StoryTheme.CRYSTAL_FOREST, "zayn", node, emptyList(), emptyList(), 1000L)
        storyRepo.saveOrUpdateSession(s1)
        val mergedStories = storyRepo.mergeCloudSessions(
            listOf(
                s1.copy(updatedAtEpochMs = 5000L, currentNode = node.copy(narrative = "Updated cloud")),
                s1.copy(sessionId = "s2", updatedAtEpochMs = 3000L)
            )
        )
        assertEquals(2, mergedStories.size)
        assertEquals("s1", mergedStories.first().sessionId)
        assertEquals("Updated cloud", mergedStories.first().currentNode.narrative)
    }

    @Test
    fun `daily checkin scheduler creates distinct morning and evening alarms and boot receiver reschedules`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.notifications.DailyCheckInScheduler.setRemindersEnabled(context, true)
        assertTrue(com.example.notifications.DailyCheckInScheduler.isEnabled(context))

        val shadowAlarmManager = org.robolectric.Shadows.shadowOf(
            context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        )
        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertTrue(scheduledAlarms.size >= 2)

        com.example.notifications.DailyCheckInScheduler.setRemindersEnabled(context, false)
        assertEquals(false, com.example.notifications.DailyCheckInScheduler.isEnabled(context))

        com.example.notifications.DailyCheckInScheduler.setRemindersEnabled(context, true)
        val countBeforeBoot = shadowAlarmManager.scheduledAlarms.size
        val bootReceiver = com.example.notifications.DailyCheckInBootReceiver()
        bootReceiver.onReceive(context, android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED))
        assertTrue(shadowAlarmManager.scheduledAlarms.size >= countBeforeBoot)
    }

    @Test
    fun `speech tts engine handles gemini audio speak and stop cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val states = mutableListOf<Boolean>()
        val engine = com.example.audio.SpeechTtsEngine(
            context = context,
            onSpeakingStateChanged = { states.add(it) }
        )
        engine.setLanguage(AppLanguage.INDONESIAN)
        engine.setVoiceProfile(1.2f, 1.1f)
        engine.speak("Halo, aku siap berbicara!", character = CharacterCatalog.getById("zayn"))
        engine.stop()
        assertTrue(states.isNotEmpty() && !states.last())
        engine.release()
    }

    @Test
    fun `continuous mic wake detector pauses cleanly without triggering barge-in while ai is not speaking`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val speechEvents = mutableListOf<Boolean>()
        val rmsEvents = mutableListOf<Float>()
        var bargeInCount = 0

        val detector = com.example.audio.ContinuousMicWakeDetector(
            context = context,
            onRmsChanged = { rmsEvents.add(it) },
            onSpeechActivityChanged = { speechEvents.add(it) },
            onVoiceSegmentCaptured = { _, _, _ -> },
            onLiveSessionSilenceTimeout = {},
            onBargeInTriggered = { bargeInCount++ }
        )

        detector.setAiSpeaking(false)
        detector.setPaused(true)
        assertEquals(0, bargeInCount)
        assertTrue(speechEvents.isNotEmpty() && !speechEvents.last())
        assertTrue(rmsEvents.isNotEmpty() && rmsEvents.last() == 0f)

        detector.setAllowBargeIn(false)
        detector.setAiSpeaking(true)
        detector.updateAiPlaybackAmplitude(0.8f)
        detector.setPaused(false)
        detector.suppressAudioForMs(600L)
        detector.updateAiPlaybackAmplitude(0f)
        detector.setAiSpeaking(false)
        detector.stop()
        assertEquals(0, bargeInCount)

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.AvatarViewModel(app)
        assertEquals(true, vm.uiState.value.isHandsFreeWakeEnabled)
        vm.setAllowBargeInWhileAiSpeaking(false)
        assertEquals(false, vm.uiState.value.allowBargeInWhileAiSpeaking)

        val reloadedVm = com.example.ui.AvatarViewModel(app)
        assertEquals(false, reloadedVm.uiState.value.allowBargeInWhileAiSpeaking)
        assertEquals(true, reloadedVm.uiState.value.isHandsFreeWakeEnabled)

        reloadedVm.callHeyPetto()
        assertEquals(false, reloadedVm.uiState.value.isUserSpeakingNow)

        reloadedVm.speakVoiceOnly("Halo teman!")
        assertEquals(false, reloadedVm.uiState.value.isUserSpeakingNow)

        reloadedVm.sendMessage("Halo Zayn")
        assertEquals(false, reloadedVm.uiState.value.isUserSpeakingNow)
    }
}



