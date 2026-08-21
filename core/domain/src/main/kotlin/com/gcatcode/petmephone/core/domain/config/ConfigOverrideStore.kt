package com.gcatcode.petmephone.core.domain.config

import kotlinx.coroutines.flow.Flow

/**
 * One generic store keyed by a typed [ConfigField] descriptor (design decision 1). Every method
 * takes exactly one field — no method accepts a collection, a `vararg`, or a whole config object,
 * so writing one field is structurally incapable of touching another (`config-override-store`
 * spec, "No whole-config write exists").
 */
interface ConfigOverrideStore {
    /** Observes [field]'s raw stored state; never resolves it — that is [resolve]'s job. */
    fun <T : Comparable<T>> override(field: ConfigField<T>): Flow<StoredOverride<T>>

    /** Validates [value] against [field]'s range before persisting; rejects without clamping. */
    suspend fun <T : Comparable<T>> set(field: ConfigField<T>, value: T): ConfigWriteResult

    /** Deletes [field]'s entry. Never rewrites the current default as a value. */
    suspend fun <T : Comparable<T>> reset(field: ConfigField<T>)
}

/** Typed data, no display copy (design decision 4) — a UI layer owns wording, not this result. */
sealed interface ConfigWriteResult {
    data object Accepted : ConfigWriteResult

    /** [offending] is typed to the field's own `T`, never a string. */
    data class OutOfRange<T>(val key: String, val min: T, val max: T, val offending: T) : ConfigWriteResult
}
