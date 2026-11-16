package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAndGetUserProfile_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repository = CloudSyncRepository(firestore, auth)

        withTimeout(5000L) {
            repository.upsertUserProfile(
                CloudUserProfile(
                    userId = uid,
                    nickname = "Zayn",
                    playerLevel = 14L,
                    levelProgress = 0.5,
                    coins = 2200L,
                    diamonds = 310L,
                    unlockedCostumes = listOf("NONE", "HOODIE"),
                    unlockedAccessories = listOf("NONE", "SUNGLASSES"),
                    unlockedThemes = listOf("LIVING_ROOM", "COZY_BEDROOM")
                )
            )
            val fetched = repository.getUserProfile()
            assertNotNull(fetched)
            assertEquals("Zayn", fetched?.nickname)
            assertEquals(2200L, fetched?.coins)
        }
    }

    @Test
    fun observeUserProfile_authenticatedOwner_emitsRealtimeUpdate() = runBlocking {
        val uid = signInTestUser("alice_stream_${UUID.randomUUID()}@test.com")
        val repository = CloudSyncRepository(firestore, auth)

        withTimeout(5000L) {
            repository.upsertUserProfile(
                CloudUserProfile(userId = uid, nickname = "Rizky", coins = 1750L)
            )
            val observed = repository.observeUserProfile().first { it?.coins == 1750L }
            assertEquals("Rizky", observed?.nickname)
        }
    }

    @Test
    fun upsertMemoriesMoodAndStory_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser("alice_data_${UUID.randomUUID()}@test.com")
        val repository = CloudSyncRepository(firestore, auth)
        val id = "id_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        withTimeout(5000L) {
            repository.upsertMemoryFact(id, "Hobi", "Suka belajar bahasa Jepang")
            val memories = repository.getMemories()
            assertTrue(memories.any { it.id == id && it.content == "Suka belajar bahasa Jepang" })

            repository.upsertMoodEntry(id, "HAPPY", "Latihan pelafalan seru!", "Keren banget!", "siti")
            val moods = repository.getMoodEntries()
            assertTrue(moods.any { it.id == id && it.moodType == "HAPPY" })

            repository.upsertStorySession(
                sessionId = id,
                themeId = "CRYSTAL_FOREST",
                characterId = "siti",
                currentNarrative = "Menemukan peta cahaya.",
                inventoryLabels = listOf("Peta Cahaya"),
                historyLog = listOf("Mulai petualangan")
            )
            val stories = repository.getStorySessions()
            assertTrue(stories.any { it.id == id && it.themeId == "CRYSTAL_FOREST" })
        }
    }

    @Test
    fun unauthenticatedCaller_rejectedWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = CloudSyncRepository(firestore, auth)

        var caughtException: Exception? = null
        try {
            withTimeout(4000L) {
                firestore.collection("users").document("some_uid").collection("memories").get()
            }
        } catch (e: Exception) {
            caughtException = e
        }

        // Also test repository fail-fast guard when unauthenticated
        var authGuardFailed = false
        try {
            withTimeout(4000L) {
                repository.observeUserProfile().first()
            }
        } catch (e: IllegalStateException) {
            authGuardFailed = true
        } catch (e: Exception) {
            val fs = e.unwrapFirestoreException()
            if (fs?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                authGuardFailed = true
            }
        }
        assertTrue(authGuardFailed || caughtException != null)
    }

    @Test
    fun crossUserRead_rejectedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso_${UUID.randomUUID()}@test.com")
        val aliceRepo = CloudSyncRepository(firestore, auth)
        withTimeout(5000L) {
            aliceRepo.upsertUserProfile(CloudUserProfile(userId = aliceUid, nickname = "Alice"))
        }

        signInTestUser("bob_iso_${UUID.randomUUID()}@test.com")
        var denied = false
        try {
            withTimeout(5000L) {
                // Direct attempt to read Alice's doc while signed in as Bob
                val task = firestore.collection("users").document(aliceUid)
                    .collection("sync").document("profile").get()
                com.google.android.gms.tasks.Tasks.await(task)
            }
        } catch (e: Exception) {
            val fs = e.unwrapFirestoreException()
            if (fs?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue(denied)
    }
}
