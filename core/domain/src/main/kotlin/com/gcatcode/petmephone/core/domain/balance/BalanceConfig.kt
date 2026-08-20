package com.gcatcode.petmephone.core.domain.balance

import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.ConfigGroup

/**
 * Every tuned number in the app, injected rather than referenced as a literal (dependency-injection
 * spec, `balance-configuration` spec). No domain function may read a balance value from a global,
 * companion, or top-level constant — it takes [BalanceConfig] or one of its fields as a parameter.
 *
 * Fields are limited to those with a consumer in this slice; the PRD's full table also lists
 * Energy and Happiness fields, deferred to the slices that give them a function and a test
 * (design.md decision 5, written back to #29).
 */
data class BalanceConfig(
    /**
     * The number of manually created tasks needed to fully satisfy a day, shared by Hunger's goal
     * and Happiness's floor. Raising it makes both metrics harder to satisfy for the same number
     * of created tasks; lowering it makes them easier.
     */
    val dailyTaskGoal: Int = 10,
    /**
     * Tier-2 only: the ratio of [dailyTaskGoal] below which Hunger claims the screen unprompted
     * (`isHungerPriority` in `hunger-metric`). It does NOT gate tier 1 (`isHungry`), which is
     * derived only from whether [dailyTaskGoal] is fully met. The boundary at exactly this ratio
     * is exclusive: it does not claim priority. Raising it makes hunger claim the screen sooner
     * (at a higher completion percentage); lowering it delays that claim.
     */
    val hungryThresholdRatio: Double = 0.6,
    /**
     * How many recurring occurrences scheduled today are needed to contribute one point toward
     * Hunger's recurring term. Raising it makes recurring occurrences worth less per occurrence;
     * lowering it makes each one worth more.
     */
    val recurringHungerRatio: Int = 3,
    /**
     * The maximum points the recurring term of Hunger may ever contribute, regardless of how many
     * recurring occurrences are scheduled. Raising it lets recurring occurrences close a larger
     * share of the goal on their own; lowering it keeps manually created tasks more load-bearing.
     */
    val recurringHungerCap: Int = 4,
    /**
     * Points a standard (non-recurring) task contributes toward its `TaskOccurrence`. Raising it
     * makes a single task count for more; lowering it makes more tasks needed to make progress.
     */
    val standardTaskPoints: Int = 1,
    /**
     * Increments whenever a default above changes, so a persisted or displayed value can be traced
     * back to the balance revision that produced it.
     */
    val version: Int = 1,
) {
    companion object {
        /** This config's override group (design decision 3). [version] above tracks its revisions. */
        val GROUP = ConfigGroup(id = "balance", currentVersion = 1)

        /** Range for [dailyTaskGoal]; a goal of 0 would make Hunger divide by nothing. */
        val DAILY_TASK_GOAL = ConfigField.IntField("config_override.balance.daily_task_goal", GROUP, 10, 1, 100)

        /** Range for [hungryThresholdRatio]; see that field's KDoc for the semantics. */
        val HUNGRY_THRESHOLD_RATIO = ConfigField.DoubleField("config_override.balance.hungry_threshold_ratio", GROUP, 0.6, 0.0, 1.0)

        /** Range for [recurringHungerRatio]; a ratio of 0 would make each occurrence worth infinity. */
        val RECURRING_HUNGER_RATIO = ConfigField.IntField("config_override.balance.recurring_hunger_ratio", GROUP, 3, 1, 100)

        /** Range for [recurringHungerCap]; see that field's KDoc for the semantics. */
        val RECURRING_HUNGER_CAP = ConfigField.IntField("config_override.balance.recurring_hunger_cap", GROUP, 4, 0, 100)

        /** Range for [standardTaskPoints]; a task worth 0 points could never contribute progress. */
        val STANDARD_TASK_POINTS = ConfigField.IntField("config_override.balance.standard_task_points", GROUP, 1, 1, 100)

        /** Registry consumed by #92's tuning panel and by the resolution/registry-invariant tests. */
        val ALL: List<ConfigField<*>> =
            listOf(DAILY_TASK_GOAL, HUNGRY_THRESHOLD_RATIO, RECURRING_HUNGER_RATIO, RECURRING_HUNGER_CAP, STANDARD_TASK_POINTS)
    }
}
