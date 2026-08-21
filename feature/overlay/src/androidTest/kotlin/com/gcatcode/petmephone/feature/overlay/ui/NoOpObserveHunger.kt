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
 * Instrumented-test-only fixtures, mirroring the JVM-test ones in `src/test/.../ui/NoOpObserveHunger.kt`.
 * These suites build [PetOverlayStateHolder] directly to exercise pet rendering and never collect
 * `hunger` or a config change reactively, so fixed sources are safe — nothing subscribes.
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

internal fun noOpBalanceConfigSource(): BalanceConfigSource = object : BalanceConfigSource {
    override val config: StateFlow<BalanceConfig> = MutableStateFlow(BalanceConfig())
}

/** A `ConfigOverrideStore` that reports [config]'s three fields as present overrides, keyed by frozen key. */
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
