package com.gcatcode.petmephone.core.domain.config

/**
 * A field's frozen storage key, its shipped default, its valid range, and its override group
 * (design decision 1). Every reader, writer and validator agrees on this one object; no second,
 * independently-maintained range copy exists (`config-override-store` spec). Closed hierarchy
 * (decision 1a): a `when` over the three subclasses is exhaustive, no reflection, no codec.
 */
sealed class ConfigField<T : Comparable<T>>(
    /** Frozen storage key. NEVER derived from the Kotlin property name (design decision 8). */
    val key: String,
    /** The override group this field belongs to; its version drives staleness (decision 3). */
    val group: ConfigGroup,
    /** The value resolved when no entry exists for [key]. Never zero-substituted for absence. */
    val shippedDefault: T,
    /** Inclusive lower bound. A write below this is rejected, never clamped. */
    val min: T,
    /** Inclusive upper bound. A write above this is rejected, never clamped. */
    val max: T,
    /** Ordered fallback keys for a deliberate key rename (decision 8). Empty for now. */
    val previousKeys: List<String> = emptyList(),
) {
    class IntField(
        key: String,
        group: ConfigGroup,
        shippedDefault: Int,
        min: Int,
        max: Int,
        previousKeys: List<String> = emptyList(),
    ) : ConfigField<Int>(key, group, shippedDefault, min, max, previousKeys)

    class LongField(
        key: String,
        group: ConfigGroup,
        shippedDefault: Long,
        min: Long,
        max: Long,
        previousKeys: List<String> = emptyList(),
    ) : ConfigField<Long>(key, group, shippedDefault, min, max, previousKeys)

    class DoubleField(
        key: String,
        group: ConfigGroup,
        shippedDefault: Double,
        min: Double,
        max: Double,
        previousKeys: List<String> = emptyList(),
    ) : ConfigField<Double>(key, group, shippedDefault, min, max, previousKeys)
}

/**
 * The staleness unit (design decision 3). `currentVersion = null` means this group carries no
 * staleness notion at all — an override in such a group is never flagged stale.
 */
data class ConfigGroup(val id: String, val currentVersion: Int?)

/**
 * The two-state resolution contract (`config-override-store` spec, "Resolution follows a two-state
 * contract per field"). No third state and no partial state exist.
 */
sealed interface StoredOverride<out T> {
    /** No entry exists for the field. Resolves to the field's shipped default, never to zero. */
    data object Absent : StoredOverride<Nothing>

    /** An entry exists, recorded under the group version it was written with. */
    data class Present<T>(val value: T, val writtenUnderVersion: Int?) : StoredOverride<T>
}
