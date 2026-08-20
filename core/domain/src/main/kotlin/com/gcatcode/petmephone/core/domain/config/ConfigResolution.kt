package com.gcatcode.petmephone.core.domain.config

/** Where a resolved value came from. */
enum class ResolutionSource {
    /** No entry existed for the field; [Resolved.value] is [ConfigField.shippedDefault]. */
    SHIPPED_DEFAULT,

    /** A valid, in-range entry existed; [Resolved.value] is the stored value. */
    OVERRIDE,

    /**
     * An entry existed but fell outside the field's declared range; [Resolved.value] falls back to
     * [ConfigField.shippedDefault] rather than the out-of-range stored value.
     */
    SHIPPED_DEFAULT_RANGE_NARROWED,
}

/**
 * The outcome of resolving one [ConfigField]. [staleFrom] is non-null only for an [OVERRIDE] whose
 * recorded [StoredOverride.Present.writtenUnderVersion] differs from the field's group's current
 * version (design decision 3); it names that older version, never merely a boolean.
 */
data class Resolved<T>(val value: T, val source: ResolutionSource, val staleFrom: Int?)

/**
 * Total over the two-state contract (`config-override-store` spec). Never returns zero for any
 * input, never throws, no Android and no coroutines dependency (design decision 7's pure half).
 */
fun <T : Comparable<T>> resolve(field: ConfigField<T>, stored: StoredOverride<T>): Resolved<T> =
    when (stored) {
        is StoredOverride.Absent ->
            Resolved(value = field.shippedDefault, source = ResolutionSource.SHIPPED_DEFAULT, staleFrom = null)

        is StoredOverride.Present ->
            if (stored.value in field.min..field.max) {
                Resolved(
                    value = stored.value,
                    source = ResolutionSource.OVERRIDE,
                    staleFrom = staleFromOf(field.group, stored.writtenUnderVersion),
                )
            } else {
                Resolved(
                    value = field.shippedDefault,
                    source = ResolutionSource.SHIPPED_DEFAULT_RANGE_NARROWED,
                    staleFrom = null,
                )
            }
    }

private fun staleFromOf(group: ConfigGroup, writtenUnderVersion: Int?): Int? {
    val currentVersion = group.currentVersion ?: return null
    return if (writtenUnderVersion != currentVersion) writtenUnderVersion else null
}
