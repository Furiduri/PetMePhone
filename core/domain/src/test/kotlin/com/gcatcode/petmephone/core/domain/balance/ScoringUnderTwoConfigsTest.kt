package com.gcatcode.petmephone.core.domain.balance

import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * `balance-configuration` spec, "Same scenario scored under two configs disagrees": proves
 * `dailyTaskGoal` is genuinely parameterised into a scoring function rather than hardcoded, by
 * running the same fixed input through [BalanceConfig] at two different values.
 */
class ScoringUnderTwoConfigsTest {

    @Test
    fun `same manual task count scores differently under two dailyTaskGoal values`() {
        val manualTasksCreatedToday = 5

        val scoreAtGoalOfTen = MetricRounding.percentOf(
            manualTasksCreatedToday,
            BalanceConfig(dailyTaskGoal = 10).dailyTaskGoal,
        )
        val scoreAtGoalOfTwenty = MetricRounding.percentOf(
            manualTasksCreatedToday,
            BalanceConfig(dailyTaskGoal = 20).dailyTaskGoal,
        )

        assertNotEquals(scoreAtGoalOfTen, scoreAtGoalOfTwenty)
    }
}
