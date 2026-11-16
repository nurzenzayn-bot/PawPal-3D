package com.example.data

import android.content.Context
import com.example.model.MoodJournalEntry
import com.example.model.MoodType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoodJournalRepository(context: Context) {

    private val prefs = context.getSharedPreferences("pawpal_mood_journal_prefs", Context.MODE_PRIVATE)
    private val KEY_ENTRIES = "mood_journal_entries_v1"

    fun getEntries(): List<MoodJournalEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<MoodJournalEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val moodName = obj.optString("mood", MoodType.HAPPY.name)
                val mood = try {
                    MoodType.valueOf(moodName)
                } catch (_: Exception) {
                    MoodType.HAPPY
                }
                list.add(
                    MoodJournalEntry(
                        id = obj.optString("id", "mood_$i"),
                        timestampEpochMs = obj.optLong("timestampEpochMs", System.currentTimeMillis()),
                        dateFormatted = obj.optString("dateFormatted", formatTimestamp(System.currentTimeMillis())),
                        mood = mood,
                        userNote = obj.optString("userNote", ""),
                        characterId = obj.optString("characterId", "zayn"),
                        characterName = obj.optString("characterName", "Zayn"),
                        characterEmoji = obj.optString("characterEmoji", "🐱"),
                        aiReply = obj.optString("aiReply", ""),
                        suggestedActivityTip = obj.optString("suggestedActivityTip", ""),
                        isFromAi = obj.optBoolean("isFromAi", true)
                    )
                )
            }
            list.sortedByDescending { it.timestampEpochMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addEntry(entry: MoodJournalEntry): List<MoodJournalEntry> {
        val current = getEntries().toMutableList()
        current.add(0, entry)
        val trimmed = current.take(40)
        saveEntries(trimmed)
        return trimmed
    }

    fun deleteEntry(entryId: String): List<MoodJournalEntry> {
        val updated = getEntries().filterNot { it.id == entryId }
        saveEntries(updated)
        return updated
    }

    fun mergeEntriesFromCloud(cloudEntries: List<MoodJournalEntry>): List<MoodJournalEntry> {
        if (cloudEntries.isEmpty()) return getEntries()
        val existingById = getEntries().associateBy { it.id }.toMutableMap()
        for (ce in cloudEntries) {
            if (!existingById.containsKey(ce.id)) {
                existingById[ce.id] = ce
            }
        }
        val merged = existingById.values.sortedByDescending { it.timestampEpochMs }.take(40)
        saveEntries(merged)
        return merged
    }

    private fun saveEntries(entries: List<MoodJournalEntry>) {
        val arr = JSONArray()
        for (entry in entries) {
            arr.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("timestampEpochMs", entry.timestampEpochMs)
                    put("dateFormatted", entry.dateFormatted)
                    put("mood", entry.mood.name)
                    put("userNote", entry.userNote)
                    put("characterId", entry.characterId)
                    put("characterName", entry.characterName)
                    put("characterEmoji", entry.characterEmoji)
                    put("aiReply", entry.aiReply)
                    put("suggestedActivityTip", entry.suggestedActivityTip)
                    put("isFromAi", entry.isFromAi)
                }
            )
        }
        prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    companion object {
        fun formatTimestamp(epochMs: Long): String {
            return try {
                val sdf = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.forLanguageTag("id-ID"))
                sdf.format(Date(epochMs))
            } catch (_: Exception) {
                "Hari ini"
            }
        }
    }
}
