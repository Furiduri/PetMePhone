package com.gcatcode.petmephone.debug.tuning

import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.ConfigWriteResult

/** What typing a value into a row's field produced, before it ever reaches [ConfigOverrideStore]. */
sealed interface ParsedInput<out T> {
    data class Valid<T>(val value: T) : ParsedInput<T>
    data object Unparseable : ParsedInput<Nothing>
}

/**
 * Pure. Parses [text] against [field]'s declared type only — range enforcement stays the store's
 * job, this only decides whether the text is even a number of the right shape. A blank string, a
 * non-numeric string, and a numeric string of the wrong shape for the field's type (`"1e9"` into an
 * [ConfigField.IntField]) are all [ParsedInput.Unparseable].
 */
@Suppress("UNCHECKED_CAST")
fun <T : Comparable<T>> parseTypedValue(field: ConfigField<T>, text: String): ParsedInput<T> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ParsedInput.Unparseable

    val parsed: Any? = when (field) {
        is ConfigField.IntField -> trimmed.toIntOrNull()
        is ConfigField.LongField -> trimmed.toLongOrNull()
        is ConfigField.DoubleField -> trimmed.toDoubleOrNull()
    }
    return if (parsed == null) ParsedInput.Unparseable else ParsedInput.Valid(parsed as T)
}

/**
 * Names the field, its declared range, and the offending value as typed data taken straight from
 * [rejection] — no display copy borrowed from `:core:domain`, which stays wording-free by design
 * decision 4 of #91.
 */
fun rejectionMessage(rejection: ConfigWriteResult.OutOfRange<*>): String =
    "${rejection.key} must be between ${rejection.min} and ${rejection.max}; got ${rejection.offending}"

/** The wording for a value the parser could not even turn into [field]'s declared type. */
fun unparseableMessage(field: ConfigField<*>): String =
    "${field.key}: not a valid number (expected ${field.min}..${field.max})"
