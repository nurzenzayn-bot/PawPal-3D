package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nickname: String = "",
    val playerLevel: Long = 12L,
    val levelProgress: Double = 0.65,
    val coins: Long = 1500L,
    val diamonds: Long = 240L,
    val unlockedCostumes: List<String> = emptyList(),
    val unlockedAccessories: List<String> = emptyList(),
    val unlockedThemes: List<String> = emptyList(),
    val waterGlassesToday: Long = 0L,
    val stretchCountToday: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudMemoryFact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudMoodEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val moodType: String = "",
    val note: String = "",
    val aiReply: String = "",
    val characterId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudStorySession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeId: String = "",
    val characterId: String = "",
    val currentNarrative: String = "",
    val inventoryLabels: List<String> = emptyList(),
    val historyLog: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google sebelum mengakses Cloud Sync.")
    }

    private fun sanitizeDocId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return if (cleaned.isBlank()) "item_default" else cleaned.take(120)
    }

    suspend fun getUserProfile(): CloudUserProfile? {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("sync").document("profile")
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) null
            else snap.toObject(CloudUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    fun observeUserProfile(): Flow<CloudUserProfile?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("sync").document("profile")
        docRef.snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(CloudUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun upsertUserProfile(profile: CloudUserProfile) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("sync").document("profile")
        try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updatePayload = mapOf(
                    "nickname" to profile.nickname.take(60),
                    "playerLevel" to profile.playerLevel.coerceIn(1L, 999L),
                    "levelProgress" to profile.levelProgress.coerceIn(0.0, 1.0),
                    "coins" to profile.coins.coerceIn(0L, 10_000_000L),
                    "diamonds" to profile.diamonds.coerceIn(0L, 10_000_000L),
                    "unlockedCostumes" to profile.unlockedCostumes.distinct().take(50),
                    "unlockedAccessories" to profile.unlockedAccessories.distinct().take(50),
                    "unlockedThemes" to profile.unlockedThemes.distinct().take(50),
                    "waterGlassesToday" to profile.waterGlassesToday.coerceIn(0L, 100L),
                    "stretchCountToday" to profile.stretchCountToday.coerceIn(0L, 100L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "nickname" to profile.nickname.take(60),
                    "playerLevel" to profile.playerLevel.coerceIn(1L, 999L),
                    "levelProgress" to profile.levelProgress.coerceIn(0.0, 1.0),
                    "coins" to profile.coins.coerceIn(0L, 10_000_000L),
                    "diamonds" to profile.diamonds.coerceIn(0L, 10_000_000L),
                    "unlockedCostumes" to profile.unlockedCostumes.distinct().take(50),
                    "unlockedAccessories" to profile.unlockedAccessories.distinct().take(50),
                    "unlockedThemes" to profile.unlockedThemes.distinct().take(50),
                    "waterGlassesToday" to profile.waterGlassesToday.coerceIn(0L, 100L),
                    "stretchCountToday" to profile.stretchCountToday.coerceIn(0L, 100L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun upsertMemoryFact(memoryId: String, category: String, content: String) {
        val uid = requireUserId()
        val safeId = sanitizeDocId(memoryId)
        val docRef = db.collection("users").document(uid).collection("memories").document(safeId)
        try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "category" to category.take(60).ifBlank { "Umum" },
                        "content" to content.take(500).ifBlank { "-" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "category" to category.take(60).ifBlank { "Umum" },
                        "content" to content.take(500).ifBlank { "-" },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getMemories(): List<CloudMemoryFact> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("memories")
        return try {
            colRef.get().await().toObjects(CloudMemoryFact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    suspend fun deleteMemoryFact(memoryId: String) {
        val uid = requireUserId()
        val safeId = sanitizeDocId(memoryId)
        val docRef = db.collection("users").document(uid).collection("memories").document(safeId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun upsertMoodEntry(
        entryId: String,
        moodType: String,
        note: String,
        aiReply: String,
        characterId: String
    ) {
        val uid = requireUserId()
        val safeId = sanitizeDocId(entryId)
        val docRef = db.collection("users").document(uid).collection("mood_entries").document(safeId)
        try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "moodType" to moodType.take(40).ifBlank { "HAPPY" },
                        "note" to note.take(1500),
                        "aiReply" to aiReply.take(2000).ifBlank { "Terima kasih sudah berbagi." },
                        "characterId" to characterId.take(40).ifBlank { "zayn" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "moodType" to moodType.take(40).ifBlank { "HAPPY" },
                        "note" to note.take(1500),
                        "aiReply" to aiReply.take(2000).ifBlank { "Terima kasih sudah berbagi." },
                        "characterId" to characterId.take(40).ifBlank { "zayn" },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getMoodEntries(): List<CloudMoodEntry> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("mood_entries")
        return try {
            colRef.get().await().toObjects(CloudMoodEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    suspend fun deleteMoodEntry(entryId: String) {
        val uid = requireUserId()
        val safeId = sanitizeDocId(entryId)
        val docRef = db.collection("users").document(uid).collection("mood_entries").document(safeId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun upsertStorySession(
        sessionId: String,
        themeId: String,
        characterId: String,
        currentNarrative: String,
        inventoryLabels: List<String>,
        historyLog: List<String>
    ) {
        val uid = requireUserId()
        val safeId = sanitizeDocId(sessionId)
        val docRef = db.collection("users").document(uid).collection("story_sessions").document(safeId)
        try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "themeId" to themeId.take(60).ifBlank { "CRYSTAL_FOREST" },
                        "characterId" to characterId.take(40).ifBlank { "zayn" },
                        "currentNarrative" to currentNarrative.take(5000).ifBlank { "Petualangan dimulai." },
                        "inventoryLabels" to inventoryLabels.take(30),
                        "historyLog" to historyLog.take(80),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "themeId" to themeId.take(60).ifBlank { "CRYSTAL_FOREST" },
                        "characterId" to characterId.take(40).ifBlank { "zayn" },
                        "currentNarrative" to currentNarrative.take(5000).ifBlank { "Petualangan dimulai." },
                        "inventoryLabels" to inventoryLabels.take(30),
                        "historyLog" to historyLog.take(80),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getStorySessions(): List<CloudStorySession> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("story_sessions")
        return try {
            colRef.get().await().toObjects(CloudStorySession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    suspend fun deleteStorySession(sessionId: String) {
        val uid = requireUserId()
        val safeId = sanitizeDocId(sessionId)
        val docRef = db.collection("users").document(uid).collection("story_sessions").document(safeId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
