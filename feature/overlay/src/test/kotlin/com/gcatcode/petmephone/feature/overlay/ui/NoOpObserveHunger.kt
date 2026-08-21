package com.gcatcode.petmephone.feature.overlay.ui

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.config.BalanceConfigSource
import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.ConfigOverrideStore
import com.gcatcode.petmephone.core.domain.config.ConfigWriteResult
import com.gcatcode.petmephone.core.domain.config.StoredOverride
import com.gcatcode.petmephone.core.domain.task.TaskId
import com.gcatcode.petmephone.core.domain.task.TaskOccurrence
import com.gcatcode.petmephone.core.domain.task.TaskRepository
import com.gcatcode.petmephone.core.domain.task.TaskTitle
import com.gcatcode.petmephone.core.domain.time.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Test-only fixtures for suites in this file's package that build [PetOverlayStateHolder] directly
 * but never collect its `hunger`/`config` `StateFlow`s reactively — Hunger's own reactive plumbing
 * is exercised by `ObserveHungerTest` in `:core:domain`, and the persisted-override resolution
 * itself by `PreferencesConfigOverrideStoreTest`/`PetAnimationConfigSourceTest`, not here.
 */
internal fun noOpAppClock(): AppClock = object : AppClock {
    override fun now(): Instant = Instant.EPOCH
    override fun zone(): ZoneId = ZoneId.of("UTC")
}

internal fun noOpTaskRepository(): TaskRepository = object : TaskRepository {
    override suspend fun createOneOff(
        title: TaskTitle,
        createdAt: Instant,
        createdDate: LocalDate,
        points: Int,
    ): TaskId = throw UnsupportedOperationException("not used by noOpTaskRepository")

    override suspend fun countManuallyCreatedOn(date: LocalDate): Int = 0
    override suspend fun countRecurringScheduledOn(date: LocalDate): Int = 0
    override fun occurrencesDueOn(date: LocalDate): Flow<List<TaskOccurrence>> = emptyFlow()
    override fun observeManuallyCreatedOn(date: LocalDate): Flow<Int> = emptyFlow()
    override fun observeRecurringScheduledOn(date: LocalDate): Flow<Int> = emptyFlow()
}

/** A `BalanceConfigSource` fixed at the shipped defaults; never emits a second value. */
internal fun noOpBalanceConfigSource(): BalanceConfigSource = object : BalanceConfigSource {
    override val config: StateFlow<BalanceConfig> = MutableStateFlow(BalanceConfig())
}

/**
 * A `ConfigOverrideStore` that reports [config]'s three fields as present overrides, matched by
 * frozen key rather than by field identity — so tests can drive [PetAnimationConfigSource] to a
 * specific, fast-timing config without touching a real store.
 */
internal fun fixedPetAnimationConfigStore(config: PetAnimationConfig): ConfigOverrideStore = object : ConfigOverrideStore {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Comparable<T>> override(field: ConfigField<T>): Flow<StoredOverride<T>> {
        val value: Comparable<*>? = when (field.key) {
            PetAnimationConfig.FRAME_INTERVAL_MILLIS.key -> config.frameIntervalMillis
            PetAnimationConfig.MIN_FRAME_INTERVAL_MILLIS.key -> config.minFrameIntervalMillis
            PetAnimationConfig.STATE_SHARING_TIMEOUT_MILLIS.key -> config.stateSharingTimeoutMillis
            else -> null
        }
        return flowOf(if (value == null) StoredOverride.Absent else StoredOverride.Present(value as T, null))
    }

    override suspend fun <T : Comparable<T>> set(field: ConfigField<T>, value: T): ConfigWriteResult =
        ConfigWriteResult.Accepted

    override suspend fun <T : Comparable<T>> reset(field: ConfigField<T>) = Unit
}

/**
 * An in-memory, mutable [ConfigOverrideStore] fake keyed by field identity — [set]/[reset] on it
 * are observed live through any `PetAnimationConfigSource` built over it, exercising the same
 * `flatMapLatest`-style reactivity `PetOverlayStateHolderTest` drives against
 * [MutableFakeBalanceConfigSource].
 */
internal class MutableFakeConfigOverrideStore : ConfigOverrideStore {
    private val flows = mutableMapOf<ConfigField<*>, MutableStateFlow<StoredOverride<*>>>()

    override fun <T : Comparable<T>> override(field: ConfigField<T>): Flow<StoredOverride<T>> = stateFlowFor(field)

    override suspend fun <T : Comparable<T>> set(field: ConfigField<T>, value: T): ConfigWriteResult {
        stateFlowFor(field).value = StoredOverride.Present(value, null)
        return ConfigWriteResult.Accepted
    }

    override suspend fun <T : Comparable<T>> reset(field: ConfigField<T>) {
        stateFlowFor(field).value = StoredOverride.Absent
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Comparable<T>> stateFlowFor(field: ConfigField<T>): MutableStateFlow<StoredOverride<T>> =
        flows.getOrPut(field) { MutableStateFlow(StoredOverride.Absent) } as MutableStateFlow<StoredOverride<T>>
}

/** A [BalanceConfigSource] whose value can be mutated directly, for reactivity tests. */
internal class MutableFakeBalanceConfigSource : BalanceConfigSource {
    val mutableConfig = MutableStateFlow(BalanceConfig())
    override val config: StateFlow<BalanceConfig> get() = mutableConfig
}
