package com.gcatcode.petmephone.core.domain.metric

/**
 * Per-metric display state for Hunger, Happiness, and Energy on the quick-menu card.
 *
 * `percent` exists ONLY on [Available] — a zero is unconstructible without deliberately writing
 * `Available(0)`, which is a real reading. There is no numeric default anywhere else in this type,
 * so absence never renders as zero (decided project-wide, see `overlay-metric-display`).
 *
 * [Loading] is transient: it is only valid before a metric's flow has emitted its first value, and
 * a `stateIn`-backed flow only moves forward out of it, never back. [Unavailable] is permanent for
 * a metric with no producer in this change (Happiness, Energy) — it is a distinct terminal state,
 * not a fallback spelling of [Loading].
 */
sealed interface MetricReading {

    /** No value has been produced yet; this is transient, never a permanent stand-in. */
    data object Loading : MetricReading

    /** The only case carrying a number. `percent` is a real, produced reading. */
    data class Available(val percent: Int) : MetricReading

    /** No producer exists for this metric in this change. Renders as an explicit label, never `0`. */
    data object Unavailable : MetricReading
}
