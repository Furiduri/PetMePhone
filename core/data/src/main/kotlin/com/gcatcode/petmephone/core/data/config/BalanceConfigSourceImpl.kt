package com.gcatcode.petmephone.core.data.config

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.config.BalanceConfigSource
import com.gcatcode.petmephone.core.domain.config.ConfigOverrideStore
import com.gcatcode.petmephone.core.domain.config.resolve
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
 * Folds every [BalanceConfig.ALL] descriptor through [resolve] against [store], never constructing
 * a [BalanceConfig] from anything but resolved values (design decision 7) — a corrupt or empty
 * store therefore yields the complete shipped-default object, field for field, with no partial
 * result possible. `SharingStarted.Eagerly` keeps [config] live for the process' lifetime so a
 * write is observed with no re-injection (`config-override-store` spec, "observable without a
 * restart").
 */
@Singleton
class BalanceConfigSourceImpl @Inject constructor(
    store: ConfigOverrideStore,
) : BalanceConfigSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val config: StateFlow<BalanceConfig> = combine(
        store.override(BalanceConfig.DAILY_TASK_GOAL),
        store.override(BalanceConfig.HUNGRY_THRESHOLD_RATIO),
        store.override(BalanceConfig.RECURRING_HUNGER_RATIO),
        store.override(BalanceConfig.RECURRING_HUNGER_CAP),
        store.override(BalanceConfig.STANDARD_TASK_POINTS),
    ) { dailyTaskGoal, hungryThresholdRatio, recurringHungerRatio, recurringHungerCap, standardTaskPoints ->
        BalanceConfig(
            dailyTaskGoal = resolve(BalanceConfig.DAILY_TASK_GOAL, dailyTaskGoal).value,
            hungryThresholdRatio = resolve(BalanceConfig.HUNGRY_THRESHOLD_RATIO, hungryThresholdRatio).value,
            recurringHungerRatio = resolve(BalanceConfig.RECURRING_HUNGER_RATIO, recurringHungerRatio).value,
            recurringHungerCap = resolve(BalanceConfig.RECURRING_HUNGER_CAP, recurringHungerCap).value,
            standardTaskPoints = resolve(BalanceConfig.STANDARD_TASK_POINTS, standardTaskPoints).value,
        )
    }.stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = BalanceConfig())
}
