package com.gcatcode.petmephone.core.domain.balance

import org.junit.Assert.assertEquals
import org.junit.Test

/** `balance-configuration` spec: defaults match the PRD, and the field set stays disciplined. */
class BalanceConfigTest {

    @Test
    fun `defaults equal the documented PRD values`() {
        val config = BalanceConfig()

        assertEquals(10, config.dailyTaskGoal)
        assertEquals(0.6, config.hungryThresholdRatio, 0.0)
        assertEquals(3, config.recurringHungerRatio)
        assertEquals(4, config.recurringHungerCap)
        assertEquals(1, config.standardTaskPoints)
        assertEquals(1, config.version)
    }

    @Test
    fun `hungryThresholdRatio defaults to 0-6`() {
        assertEquals(0.6, BalanceConfig().hungryThresholdRatio, 0.0)
    }

    @Test
    fun `exactly one field represents the daily task goal`() {
        // Plain Java reflection over declared fields: no kotlin-reflect dependency needed just for
        // one structural assertion.
        val goalFields = BalanceConfig::class.java.declaredFields
            .filter { it.name.contains("dailyTaskGoal", ignoreCase = true) }

        assertEquals(1, goalFields.size)
    }
}
