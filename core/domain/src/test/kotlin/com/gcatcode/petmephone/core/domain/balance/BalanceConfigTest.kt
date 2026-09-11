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

    /**
     * Each registered descriptor paired with the constructor default it must agree with. A shipped
     * default lives in two places — the constructor parameter and the descriptor's
     * [ConfigField.shippedDefault] — and the resolver reads the descriptor while callers of
     * `BalanceConfig()` read the parameter. Promoting a tuned value into only one of them leaves
     * the build with two answers for the same field.
     *
     * Paired by hand on purpose: keys are frozen and never derived from property names (design
     * decision 8), so no reflection can bridge the two. The coverage test below turns a forgotten
     * entry into a failure rather than a silent gap.
     */
    private val descriptorToConstructorDefault: Map<ConfigField<*>, Any> = BalanceConfig().let { shipped ->
        mapOf(
            BalanceConfig.DAILY_TASK_GOAL to shipped.dailyTaskGoal,
            BalanceConfig.HUNGRY_THRESHOLD_RATIO to shipped.hungryThresholdRatio,
            BalanceConfig.RECURRING_HUNGER_RATIO to shipped.recurringHungerRatio,
            BalanceConfig.RECURRING_HUNGER_CAP to shipped.recurringHungerCap,
            BalanceConfig.STANDARD_TASK_POINTS to shipped.standardTaskPoints,
        )
    }

    @Test
    fun `every registered descriptor is paired with a constructor default`() {
        assertEquals(
            "a field in BalanceConfig.ALL has no constructor default paired with it below",
            BalanceConfig.ALL.toSet(),
            descriptorToConstructorDefault.keys,
        )
    }

    @Test
    fun `each descriptor shippedDefault equals the constructor default for the same field`() {
        for ((field, constructorDefault) in descriptorToConstructorDefault) {
            assertEquals(
                "${field.key}: descriptor shippedDefault and constructor default disagree",
                field.shippedDefault,
                constructorDefault,
            )
        }
    }

    private fun assertMinLessThanOrEqualDefaultLessThanOrEqualMax(field: ConfigField<*>) {
        when (field) {
            is ConfigField.IntField -> assertTrue(field.key, field.min <= field.shippedDefault && field.shippedDefault <= field.max)
            is ConfigField.LongField -> assertTrue(field.key, field.min <= field.shippedDefault && field.shippedDefault <= field.max)
            is ConfigField.DoubleField -> assertTrue(field.key, field.min <= field.shippedDefault && field.shippedDefault <= field.max)
        }
    }
}
