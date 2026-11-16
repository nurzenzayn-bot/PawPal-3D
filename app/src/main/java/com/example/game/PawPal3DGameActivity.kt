package com.example.game

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

/** Separate activity keeps the existing Compose + Gemini app intact while hosting the GL renderer. */
class PawPal3DGameActivity : AndroidApplication() {
    private lateinit var game: PawPal3DGame
    private var resultSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The same Android SharedPreferences file is used by libGDX's AndroidPreferences.
        // This keeps cleanliness available even though the Compose state model has no hygiene field yet.
        val savedProgress = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val todayKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val savedDayKey = savedProgress.getString("daily_care_day", "") ?: ""
        val now = System.currentTimeMillis()
        val lastUpdatedAt = savedProgress.getLong("last_updated_at", now).coerceAtMost(now)
        // Needs continue to change while the app is closed, but cap catch-up at three days.
        // This prevents a long absence from producing negative stats or an unusable pet.
        val elapsedMillis = now - lastUpdatedAt
        val baseNeeds = PetProgressRules.Needs(
            hunger = intent.getFloatExtra(EXTRA_HUNGER, savedProgress.getFloat("hunger", 85f)),
            happiness = intent.getFloatExtra(EXTRA_HAPPINESS, savedProgress.getFloat("happiness", 90f)),
            energy = intent.getFloatExtra(EXTRA_ENERGY, savedProgress.getFloat("energy", 60f)),
            cleanliness = savedProgress.getFloat("cleanliness", 80f)
        )
        val offlineNeeds = PetProgressRules.applyOfflineDecay(baseNeeds, elapsedMillis)
        val session = PetGameSession(
            petName = intent.getStringExtra(EXTRA_PET_NAME)?.takeIf { it.isNotBlank() } ?: "Petto",
            species = intent.getStringExtra(EXTRA_SPECIES) ?: "CAT",
            furColorArgb = intent.getIntExtra(EXTRA_FUR_COLOR, 0xFFFF8A3D.toInt()),
            coins = intent.getIntExtra(EXTRA_COINS, savedProgress.getInt("coins", 1500)),
            hunger = offlineNeeds.hunger,
            happiness = offlineNeeds.happiness,
            energy = offlineNeeds.energy,
            cleanliness = offlineNeeds.cleanliness,
            xp = intent.getIntExtra(EXTRA_XP, savedProgress.getInt("xp", 0)),
            dailyCareCount = PetProgressRules.dailyCareCount(savedDayKey, todayKey, savedProgress.getInt("daily_care_count", 0)),
            dailyQuestClaimed = PetProgressRules.dailyQuestClaimed(savedDayKey, todayKey, savedProgress.getBoolean("daily_quest_claimed", false)),
            dailyCareDayKey = todayKey,
            lastUpdatedAtMillis = now
        )

        game = PawPal3DGame(session) { runOnUiThread { finishWithResult() } }
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            numSamples = 2
        }
        initialize(game, config)
    }

    override fun onBackPressed() {
        finishWithResult()
    }

    private fun finishWithResult() {
        if (resultSent || !::game.isInitialized) return
        resultSent = true
        val state = game.session
        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(EXTRA_COINS, state.coins.coerceAtLeast(0))
                putExtra(EXTRA_HUNGER, state.hunger.coerceIn(0f, 100f))
                putExtra(EXTRA_HAPPINESS, state.happiness.coerceIn(0f, 100f))
                putExtra(EXTRA_ENERGY, state.energy.coerceIn(0f, 100f))
                putExtra(EXTRA_CLEANLINESS, state.cleanliness.coerceIn(0f, 100f))
                putExtra(EXTRA_XP, state.xp)
            }
        )
        finish()
    }

    companion object {
        const val PREFERENCES_NAME = "pawpal_3d_progress"
        const val EXTRA_PET_NAME = "pawpal.pet_name"
        const val EXTRA_SPECIES = "pawpal.species"
        const val EXTRA_FUR_COLOR = "pawpal.fur_color"
        const val EXTRA_COINS = "pawpal.coins"
        const val EXTRA_HUNGER = "pawpal.hunger"
        const val EXTRA_HAPPINESS = "pawpal.happiness"
        const val EXTRA_ENERGY = "pawpal.energy"
        const val EXTRA_CLEANLINESS = "pawpal.cleanliness"
        const val EXTRA_XP = "pawpal.xp"
    }
}
