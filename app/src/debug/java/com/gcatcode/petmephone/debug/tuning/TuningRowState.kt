package com.gcatcode.petmephone.debug.tuning

import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.StoredOverride
import com.gcatcode.petmephone.core.domain.config.resolve

/**
 * The literal both halves of the CI artifact check grep for (token T2, design decision 1). Pinned
 * exactly by `TuningPanelMarkerTest`, and reused verbatim as the debug launcher activity's
 * `android:label` string value — a comment saying "keep in sync" is not the guarantee, this const
 * being the only place the literal is typed is.
 */
const val TUNING_PANEL_MARKER = "PETMEPHONE_DEBUG_TUNING_PANEL"

/**
 * A row's staleness, exactly one of three distinct states (`debug-tuning-panel` spec, "Staleness
 * renders as exactly one of three distinct states per row"). [NotVersioned] is never reported as
 * [Fresh] or [Stale].
 */
sealed interface Staleness {
    /** No stored override exists, or one exists and matches the group's current version. */
    data object Fresh : Staleness

    /** A stored override exists, recorded under a version older than the group's current one. */
    data class Stale(val writtenUnderVersion: Int?) : Staleness

    /** The field's group has no current version to compare against — the check never ran. */
    data object NotVersioned : Staleness
}

/**
 * Whether a write to this field is visible on the running overlay immediately, or only after the
 * overlay service restarts. Decided by the field's group id, never hardcoded per row (design's
 * "follow-up slice" note) — every field of both registries reads [LIVE] today.
 */
enum class ValueApplication { LIVE, NEXT_SERVICE_START }

/** Everything one panel row renders, produced by [tuningRowOf]. */
data class TuningRow(
    val key: String,
    val groupId: String,
    val shippedDefault: String,
    val currentValue: String,
    val overridden: Boolean,
    val rangeLabel: String,
    val staleness: Staleness,
    val application: ValueApplication,
)

/**
 * Pure. No Android import, no Compose import, no coroutines import. Calls `resolve` — the same
 * function `BalanceConfigSourceImpl` and `PetAnimationConfigSource` call — and never re-implements
 * it (design decision 2, 3).
 */
fun <T : Comparable<T>> tuningRowOf(field: ConfigField<T>, stored: StoredOverride<T>): TuningRow {
    val resolved = resolve(field, stored)

    // An entry present is "overridden" regardless of whether resolution fell back to the shipped
    // default because it was out of range: the spec's "an override coincidentally equal to the
    // default is still marked overridden" scenario, extended the same way to a rejected-range read.
    val overridden = stored is StoredOverride.Present

    val staleness = when {
        field.group.currentVersion == null -> Staleness.NotVersioned
        resolved.staleFrom != null -> Staleness.Stale(resolved.staleFrom)
        else -> Staleness.Fresh
    }

    return TuningRow(
        key = field.key,
        groupId = field.group.id,
        shippedDefault = field.shippedDefault.toString(),
        currentValue = resolved.value.toString(),
        overridden = overridden,
        rangeLabel = "${field.min}..${field.max}",
        staleness = staleness,
        application = applicationFor(field.group.id),
    )
}

/**
 * Both registered groups read live today. A group that only takes effect on the next overlay-
 * service start would be added here, not by hardcoding the label on individual rows.
 */
private fun applicationFor(groupId: String): ValueApplication = when (groupId) {
    "balance", "pet_animation" -> ValueApplication.LIVE
    else -> ValueApplication.NEXT_SERVICE_START
}
