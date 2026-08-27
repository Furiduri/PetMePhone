package com.gcatcode.petmephone.core.data.config

import com.gcatcode.petmephone.core.domain.config.ConfigOverrideStore
import com.gcatcode.petmephone.core.domain.config.DaySegmentBoundariesSource
import com.gcatcode.petmephone.core.domain.config.resolve
import com.gcatcode.petmephone.core.domain.habit.DaySegmentBoundaries
import com.gcatcode.petmephone.core.domain.habit.DaySegmentBoundariesResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Folds [DaySegmentBoundaries.ALL] through [resolve] against [store], the same shape as
 * [BalanceConfigSourceImpl]. `SharingStarted.Eagerly` keeps [boundaries] live for the process'
 * lifetime, so a change is observed without a restart.
 *
 * ## A non-ascending stored triple resolves to the shipped segmentation, whole
 *
 * Each descriptor validates its own range, but no descriptor can see the other two, so a store can
 * hold three individually-valid minute counts that are collectively out of order — an afternoon
 * that starts before the day does. That state is reachable through any writer that does not check
 * the combination, including the debug tuning panel.
 *
 * When it happens this falls back to [DaySegmentBoundaries.SHIPPED] **as a whole triple**, never to
 * a mix of stored and shipped values. A partial mix would be a segmentation the user never chose
 * and never shipped, which is harder to recognise as wrong than plainly getting the defaults back.
 *
 * This is a last-resort read-side guard, not the validation. The write path is where a
 * non-ascending combination should be refused with a reason the user can act on.
 */
@Singleton
class DaySegmentBoundariesSourceImpl @Inject constructor(
    store: ConfigOverrideStore,
) : DaySegmentBoundariesSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val boundaries: StateFlow<DaySegmentBoundaries> = combine(
        store.override(DaySegmentBoundaries.DAY_START_MINUTES),
        store.override(DaySegmentBoundaries.AFTERNOON_START_MINUTES),
        store.override(DaySegmentBoundaries.EVENING_START_MINUTES),
    ) { dayStart, afternoonStart, eveningStart ->
        val resolved = DaySegmentBoundaries.ofMinutes(
            dayStartMinutes = resolve(DaySegmentBoundaries.DAY_START_MINUTES, dayStart).value,
            afternoonStartMinutes = resolve(DaySegmentBoundaries.AFTERNOON_START_MINUTES, afternoonStart).value,
            eveningStartMinutes = resolve(DaySegmentBoundaries.EVENING_START_MINUTES, eveningStart).value,
        )
        when (resolved) {
            is DaySegmentBoundariesResult.Valid -> resolved.boundaries
            is DaySegmentBoundariesResult.Rejected -> DaySegmentBoundaries.SHIPPED
        }
    }.stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = DaySegmentBoundaries.SHIPPED)
}
