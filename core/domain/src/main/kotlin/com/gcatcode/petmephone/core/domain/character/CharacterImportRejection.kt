package com.gcatcode.petmephone.core.domain.character

/**
 * Every way a character import can be rejected. Each case carries the concrete measured value and
 * the rule it broke — a generic "invalid image" case is deliberately absent, per
 * `character-import` spec's "Every rejection names the specific rule it broke" requirement.
 */
sealed interface CharacterImportRejection {
    /** The first 8 bytes are not the PNG signature. */
    data object NotPng : CharacterImportRejection

    /** File size exceeds the injected `maxImportBytes` ceiling, checked with no pixel allocation. */
    data class TooLarge(val actualBytes: Long, val maxBytes: Long) : CharacterImportRejection

    /** Header-declared width or height exceeds `maxPx`, checked with no pixel buffer allocated. */
    data class Oversized(val widthPx: Int, val heightPx: Int, val maxPx: Int) : CharacterImportRejection

    /** `width % height != 0` — a remainder, never truncated. */
    data class NotDivisible(val widthPx: Int, val heightPx: Int) : CharacterImportRejection

    /** Bytes passed the header/bounds tiers but failed to decode as an image. */
    data object Undecodable : CharacterImportRejection

    /** Every cell in the detected row is fully transparent: zero usable frames. */
    data object EmptySheet : CharacterImportRejection

    /** The library already holds [cap] imported characters. */
    data class CapReached(val cap: Int) : CharacterImportRejection
}
