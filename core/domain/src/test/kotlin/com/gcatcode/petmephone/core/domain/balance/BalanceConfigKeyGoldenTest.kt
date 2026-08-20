package com.gcatcode.petmephone.core.domain.balance

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A golden test over the frozen storage keys of [BalanceConfig]'s override group (design decision 8).
 *
 * These strings are the contract between a stored override and the field it belongs to. A user who
 * tuned `dailyTaskGoal` has a row in DataStore under this exact key and nothing else identifies it.
 *
 * The uniqueness assertion in `BalanceConfigTest` does not cover this. Renaming a Kotlin property and
 * its key together leaves every key unique and still discards every stored override in the field,
 * silently, with the app reporting no error at all. Only pinning the literals catches that.
 *
 * If this test fails, the change under review is a **storage migration**, not a rename. Either restore
 * the literal, or add the old string to that descriptor's `previousKeys` so existing overrides survive.
 */
class BalanceConfigKeyGoldenTest {
    @Test
    fun `the frozen storage keys are exactly these literals`() {
        assertEquals(
            listOf(
                "config_override.balance.daily_task_goal",
                "config_override.balance.hungry_threshold_ratio",
                "config_override.balance.recurring_hunger_ratio",
                "config_override.balance.recurring_hunger_cap",
                "config_override.balance.standard_task_points",
            ),
            BalanceConfig.ALL.map { it.key },
        )
    }

    @Test
    fun `the override group id is frozen too`() {
        assertEquals("balance", BalanceConfig.GROUP.id)
    }
}
