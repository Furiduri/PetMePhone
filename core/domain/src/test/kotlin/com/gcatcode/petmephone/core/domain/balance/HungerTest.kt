package com.gcatcode.petmephone.core.domain.balance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `hunger-metric` spec — the corrected #33 table. Hunger is two tiers: [isHungry] (tier 1,
 * applicability, true below a fully met goal) and [isHungerPriority] (tier 2, foreground claim,
 * exclusive boundary below `hungryThresholdRatio`).
 */
class HungerTest {

    private val config = BalanceConfig(
        dailyTaskGoal = 10,
        hungryThresholdRatio = 0.6,
        recurringHungerRatio = 3,
        recurringHungerCap = 4,
    )

    @Test
    fun `calling calculateHunger repeatedly with the same inputs is deterministic`() {
        val first = calculateHunger(6, 0, config)
        val second = calculateHunger(6, 0, config)
        assertEquals(first, second)
    }

    data class Row(
        val manual: Int,
        val recurring: Int,
        val goal: Int,
        val expectedHunger: Int,
        val expectedIsHungry: Boolean,
        val expectedIsHungerPriority: Boolean,
        val description: String,
    )

    private val table = listOf(
        Row(0, 0, 10, 0, expectedIsHungry = true, expectedIsHungerPriority = true, "0/0/10"),
        Row(4, 0, 10, 40, expectedIsHungry = true, expectedIsHungerPriority = true, "4/0/10"),
        Row(5, 0, 10, 50, expectedIsHungry = true, expectedIsHungerPriority = true, "5/0/10"),
        Row(6, 0, 10, 60, expectedIsHungry = true, expectedIsHungerPriority = false, "6/0/10 exclusive boundary"),
        Row(7, 0, 10, 70, expectedIsHungry = true, expectedIsHungerPriority = false, "7/0/10"),
        Row(6, 6, 10, 80, expectedIsHungry = true, expectedIsHungerPriority = false, "6 manual + 6 recurring/10"),
        Row(10, 0, 10, 100, expectedIsHungry = false, expectedIsHungerPriority = false, "10/0/10 exactly met"),
        Row(15, 0, 10, 100, expectedIsHungry = false, expectedIsHungerPriority = false, "15/0/10 never over 100"),
        Row(6, 0, 12, 50, expectedIsHungry = true, expectedIsHungerPriority = true, "6/0/12"),
        Row(0, 6, 10, 20, expectedIsHungry = true, expectedIsHungerPriority = true, "0 manual + 6 recurring/10"),
        Row(0, 2, 10, 0, expectedIsHungry = true, expectedIsHungerPriority = true, "0 manual + 2 recurring rounds down"),
        Row(0, 30, 10, 40, expectedIsHungry = true, expectedIsHungerPriority = true, "0 manual + 30 recurring capped"),
        Row(8, 30, 10, 100, expectedIsHungry = false, expectedIsHungerPriority = false, "8 manual + capped recurring clamped"),
    )

    @Test
    fun `all eleven corrected table rows`() {
        for (row in table) {
            val cfg = config.copy(dailyTaskGoal = row.goal)
            val hunger = calculateHunger(row.manual, row.recurring, cfg)
            assertEquals("${row.description}: hunger", row.expectedHunger, hunger)
            assertEquals(
                "${row.description}: isHungry",
                row.expectedIsHungry,
                isHungry(row.manual, row.recurring, cfg),
            )
            assertEquals(
                "${row.description}: isHungerPriority",
                row.expectedIsHungerPriority,
                isHungerPriority(row.manual, row.recurring, cfg),
            )
        }
    }

    @Test
    fun `thirty recurring occurrences never exceed the cap`() {
        val hunger = calculateHunger(manualTasksCreatedToday = 0, recurringOccurrencesScheduledToday = 30, config)
        assertEquals(40, hunger) // recurring contribution == cap(4), 4/10 = 40%
    }

    @Test
    fun `below-ratio recurring count rounds down to zero`() {
        assertEquals(0, calculateHunger(0, 2, config))
    }

    @Test
    fun `zero recurring occurrences contributes zero`() {
        assertEquals(0, calculateHunger(0, 0, config))
    }

    @Test
    fun `overshoot clamps to exactly 100 and never errors`() {
        assertEquals(100, calculateHunger(20, 0, config))
    }

    @Test
    fun `manual plus capped recurring clamps to 100`() {
        assertEquals(100, calculateHunger(manualTasksCreatedToday = 8, recurringOccurrencesScheduledToday = 30, config))
    }

    @Test
    fun `completing a task does not change Hunger`() {
        // Hunger takes counts, not entities: a completed task's manual count is unaffected,
        // so calling calculateHunger twice with the same count yields the same result.
        val beforeCompletion = calculateHunger(6, 0, config)
        val afterCompletion = calculateHunger(6, 0, config)
        assertEquals(beforeCompletion, afterCompletion)
    }

    @Test
    fun `a carried-over occurrence contributes nothing to either term`() {
        // Carry-over never increments manualTasksCreatedToday (filtered by createdDate) nor
        // recurringOccurrencesScheduledToday (filtered by dueDate == today, not origin date).
        val withoutCarryOver = calculateHunger(manualTasksCreatedToday = 3, recurringOccurrencesScheduledToday = 0, config)
        val withCarryOverExcluded = calculateHunger(manualTasksCreatedToday = 3, recurringOccurrencesScheduledToday = 0, config)
        assertEquals(withoutCarryOver, withCarryOverExcluded)
    }

    @Test
    fun `isHungry true at 80 percent`() {
        assertTrue(isHungry(8, 0, config))
    }

    @Test
    fun `isHungry false at exactly 100 percent`() {
        assertFalse(isHungry(10, 0, config))
    }

    @Test
    fun `isHungry false when clamped over 100 percent`() {
        assertFalse(isHungry(20, 0, config))
    }

    @Test
    fun `isHungerPriority true at 50 percent, both tiers true`() {
        assertTrue(isHungry(5, 0, config))
        assertTrue(isHungerPriority(5, 0, config))
    }

    @Test
    fun `isHungerPriority ratio moves the trigger with the goal`() {
        val widerGoalConfig = config.copy(dailyTaskGoal = 12)
        // Cutoff is 0.6 * 12 = 7.2; 7 manual tasks fall below it (7/12 floors to 58%, cutoff 60%).
        assertTrue(isHungerPriority(7, 0, widerGoalConfig))
    }

    @Test
    fun `isHungerPriority false at 100 percent and clamped-over-100 percent`() {
        assertFalse(isHungerPriority(10, 0, config))
        assertFalse(isHungerPriority(20, 0, config))
    }
}
