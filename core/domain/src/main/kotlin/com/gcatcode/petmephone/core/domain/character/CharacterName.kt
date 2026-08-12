package com.gcatcode.petmephone.core.domain.character

/**
 * A user-supplied name for an imported character. Constructed only through [validOrNull], which is
 * the single gate a value coming off disk or a text field passes through: absence stays absent —
 * never a fabricated default and never a blank string standing in for "no name" — mirroring
 * `OverlayPositionFraction.validOrNull`'s absence rule.
 */
data class CharacterName(val value: String) {
    companion object {
        fun validOrNull(raw: String?): CharacterName? {
            val trimmed = raw?.trim() ?: return null
            return trimmed.takeIf { it.isNotEmpty() }?.let(::CharacterName)
        }
    }
}
