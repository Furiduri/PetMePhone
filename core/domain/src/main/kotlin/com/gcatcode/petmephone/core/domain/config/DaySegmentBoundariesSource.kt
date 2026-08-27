package com.gcatcode.petmephone.core.domain.config

import com.gcatcode.petmephone.core.domain.habit.DaySegmentBoundaries
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes the user's resolved [DaySegmentBoundaries] as observable state, in `:core:domain` for the
 * same reason [BalanceConfigSource] is — so feature modules can depend on it without a main-source
 * `:core:data` dependency.
 *
 * Implemented in `:core:data` by folding [DaySegmentBoundaries.ALL] through [resolve], exactly as
 * the balance config is folded. No code path can construct a partial or non-ascending set of
 * boundaries from this seam.
 */
interface DaySegmentBoundariesSource {
    val boundaries: StateFlow<DaySegmentBoundaries>
}
