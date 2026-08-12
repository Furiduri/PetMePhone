package com.gcatcode.petmephone.core.domain.balance

/**
 * The live Hunger percentage (`hunger-metric` spec). Pure and computable for any date's counts,
 * including a past date — no I/O, no frozen snapshot, no `balanceVersion` stamping (day-close is
 * #34, slice 4).
 *
 * `manualTasksCreatedToday` counts `Task` rows by `createdDate`, never `dueDate`. Completion status
 * and carried-over occurrences have no effect on either term.
 */
fun calculateHunger(
    manualTasksCreatedToday: Int,
    recurringOccurrencesScheduledToday: Int,
    config: BalanceConfig,
): Int {
    val recurringPoints =
        (recurringOccurrencesScheduledToday / config.recurringHungerRatio)
            .coerceAtMost(config.recurringHungerCap)
    val totalPoints = manualTasksCreatedToday + recurringPoints
    return MetricRounding.percentOf(totalPoints, config.dailyTaskGoal)
}

/**
 * Tier 1 — is `hungry` an *applicable* animation? True whenever Hunger is below a fully met goal
 * (100%), so true at 80%. This is the applicability check tap-to-browse (#70) needs; it is NOT
 * wired into any `PetSnapshot` field or pet-state provider in this change.
 */
fun isHungry(
    manualTasksCreatedToday: Int,
    recurringOccurrencesScheduledToday: Int,
    config: BalanceConfig,
): Boolean = calculateHunger(manualTasksCreatedToday, recurringOccurrencesScheduledToday, config) < 100

/**
 * Tier 2 — does hunger claim the screen unprompted? True only strictly below
 * `hungryThresholdRatio * dailyTaskGoal`. The boundary is exclusive: a value exactly at the ratio
 * is hungry (tier 1) but does not claim priority. Not wired into any `PetSnapshot` field or
 * pet-state provider in this change.
 */
fun isHungerPriority(
    manualTasksCreatedToday: Int,
    recurringOccurrencesScheduledToday: Int,
    config: BalanceConfig,
): Boolean {
    val hunger = calculateHunger(manualTasksCreatedToday, recurringOccurrencesScheduledToday, config)
    val cutoff = config.hungryThresholdRatio * 100
    return hunger < cutoff
}
