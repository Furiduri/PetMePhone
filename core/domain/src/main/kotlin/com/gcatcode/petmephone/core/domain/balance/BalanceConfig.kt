package com.gcatcode.petmephone.core.domain.balance

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
)
