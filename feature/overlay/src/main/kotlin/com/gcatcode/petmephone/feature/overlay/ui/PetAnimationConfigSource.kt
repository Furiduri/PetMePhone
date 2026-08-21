package com.gcatcode.petmephone.feature.overlay.ui

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
 * A plain `@Singleton @Inject` class in `:feature:overlay` depending only on the `:core:domain`
 * [ConfigOverrideStore] interface (design decision 5) — no main-source `:core:data` dependency
 * exists here. Hilt resolves the injected [ConfigOverrideStore] to the `:core:data` implementation
 * in the app graph; this class never references it by name.
 */
@Singleton
class PetAnimationConfigSource @Inject constructor(
    store: ConfigOverrideStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val config: StateFlow<PetAnimationConfig> = combine(
        store.override(PetAnimationConfig.FRAME_INTERVAL_MILLIS),
        store.override(PetAnimationConfig.MIN_FRAME_INTERVAL_MILLIS),
        store.override(PetAnimationConfig.STATE_SHARING_TIMEOUT_MILLIS),
    ) { frameIntervalMillis, minFrameIntervalMillis, stateSharingTimeoutMillis ->
        PetAnimationConfig(
            frameIntervalMillis = resolve(PetAnimationConfig.FRAME_INTERVAL_MILLIS, frameIntervalMillis).value,
            minFrameIntervalMillis = resolve(PetAnimationConfig.MIN_FRAME_INTERVAL_MILLIS, minFrameIntervalMillis).value,
            stateSharingTimeoutMillis =
                resolve(PetAnimationConfig.STATE_SHARING_TIMEOUT_MILLIS, stateSharingTimeoutMillis).value,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = PetAnimationConfig(
            frameIntervalMillis = PetAnimationConfig.FRAME_INTERVAL_MILLIS.shippedDefault,
            minFrameIntervalMillis = PetAnimationConfig.MIN_FRAME_INTERVAL_MILLIS.shippedDefault,
            stateSharingTimeoutMillis = PetAnimationConfig.STATE_SHARING_TIMEOUT_MILLIS.shippedDefault,
        ),
    )
}
