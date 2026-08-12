package com.gcatcode.petmephone.core.domain.balance

/**
 * The one flooring convention shared by every percentage-shaped domain metric (Hunger now,
 * Happiness in slice 4). Flooring never claims a target reached that is not: 9.6/10 must not
 * display a satisfied pet (design.md decision 7).
 */
object MetricRounding {

    /**
     * Percentage of [value] out of [goal], clamped to `[0, 100]` and floored.
     *
     * @param goal MUST be greater than zero.
     */
    fun percentOf(value: Int, goal: Int): Int {
        require(goal > 0) { "goal must be greater than 0, was $goal" }
        val clamped = value.coerceIn(0, goal)
        return clamped * 100 / goal
    }
}
