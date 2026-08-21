package com.gcatcode.petmephone.core.domain.balance

import com.gcatcode.petmephone.core.domain.config.ConfigField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `every ALL descriptor is bounded and uniquely keyed, and version is not itself overridable`() {
        val keys = BalanceConfig.ALL.map { it.key }

        for (field in BalanceConfig.ALL) {
            assertMinLessThanOrEqualDefaultLessThanOrEqualMax(field)
            assertTrue("unexpected key shape: ${field.key}", Regex("""config_override\.balance\.[a-z_]+""").matches(field.key))
        }
        assertEquals("duplicate key found in BalanceConfig.ALL", keys.size, keys.toSet().size)
        assertTrue("version must not be a registered override field", keys.none { it.contains("version") })
    }

    private fun assertMinLessThanOrEqualDefaultLessThanOrEqualMax(field: ConfigField<*>) {
        when (field) {
            is ConfigField.IntField -> assertTrue(field.key, field.min <= field.shippedDefault && field.shippedDefault <= field.max)
            is ConfigField.LongField -> assertTrue(field.key, field.min <= field.shippedDefault && field.shippedDefault <= field.max)
            is ConfigField.DoubleField -> assertTrue(field.key, field.min <= field.shippedDefault && field.shippedDefault <= field.max)
        }
    }
}
