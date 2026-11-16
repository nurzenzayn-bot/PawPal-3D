package com.example.game

/** Deterministic rules for pet needs while PawPal is not running. */
object PetProgressRules {
    data class Needs(
        val hunger: Float,
        val happiness: Float,
        val energy: Float,
        val cleanliness: Float
    )

    /**
     * Applies gentle offline decay, capped at 72 hours so a long absence never
     * creates invalid values or an impossible recovery loop.
     */
    fun applyOfflineDecay(needs: Needs, elapsedMillis: Long): Needs {
        val hours = (elapsedMillis.coerceAtLeast(0L) / ONE_HOUR_MILLIS).coerceIn(0L, MAX_OFFLINE_HOURS).toFloat()
        return Needs(
            hunger = (needs.hunger - hours * 2f).coerceIn(0f, 100f),
            happiness = (needs.happiness - hours).coerceIn(0f, 100f),
            energy = (needs.energy - hours).coerceIn(0f, 100f),
            cleanliness = (needs.cleanliness - hours).coerceIn(0f, 100f)
        )
    }

    fun dailyCareCount(savedDay: String, today: String, savedCount: Int): Int =
        if (savedDay == today) savedCount.coerceIn(0, 3) else 0

    fun dailyQuestClaimed(savedDay: String, today: String, savedClaimed: Boolean): Boolean =
        savedDay == today && savedClaimed

    private const val ONE_HOUR_MILLIS = 60L * 60L * 1000L
    private const val MAX_OFFLINE_HOURS = 72L
}
