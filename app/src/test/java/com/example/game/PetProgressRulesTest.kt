package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetProgressRulesTest {
    @Test fun decaysNeedsByElapsedWholeHours() {
        val result = PetProgressRules.applyOfflineDecay(
            PetProgressRules.Needs(80f, 70f, 60f, 50f), 3L * 60L * 60L * 1000L
        )
        assertEquals(74f, result.hunger, 0.001f)
        assertEquals(67f, result.happiness, 0.001f)
        assertEquals(57f, result.energy, 0.001f)
        assertEquals(47f, result.cleanliness, 0.001f)
    }

    @Test fun ignoresNegativeElapsedTime() {
        val needs = PetProgressRules.Needs(80f, 70f, 60f, 50f)
        assertEquals(needs, PetProgressRules.applyOfflineDecay(needs, -100L))
    }

    @Test fun capsOfflineDecayAt72Hours() {
        val result = PetProgressRules.applyOfflineDecay(
            PetProgressRules.Needs(100f, 100f, 100f, 100f), 1000L * 60L * 60L * 1000L
        )
        assertEquals(0f, result.hunger, 0.001f)
        assertEquals(28f, result.happiness, 0.001f)
        assertEquals(28f, result.energy, 0.001f)
        assertEquals(28f, result.cleanliness, 0.001f)
    }

    @Test fun clampsAllNeedsToValidRange() {
        val result = PetProgressRules.applyOfflineDecay(
            PetProgressRules.Needs(150f, -2f, 0f, 10f), 10L * 60L * 60L * 1000L
        )
        assertEquals(100f, result.hunger, 0.001f)
        assertEquals(0f, result.happiness, 0.001f)
        assertEquals(0f, result.energy, 0.001f)
        assertEquals(0f, result.cleanliness, 0.001f)
    }

    @Test fun dailyProgressResetsWhenDateChanges() {
        assertEquals(2, PetProgressRules.dailyCareCount("20261116", "20261116", 2))
        assertEquals(0, PetProgressRules.dailyCareCount("20261115", "20261116", 2))
        assertTrue(PetProgressRules.dailyQuestClaimed("20261116", "20261116", true))
        assertFalse(PetProgressRules.dailyQuestClaimed("20261115", "20261116", true))
    }
}
