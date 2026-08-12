package com.gcatcode.petmephone.core.domain.character

/**
 * Why a bundled character's grid manifest failed to produce a usable
 * `SpriteGridDeclaration`. A bundled character with no manifest is one of these, never a guessed
 * grid (`design.md` decision 13).
 */
sealed interface CharacterManifestFailure {
    /** No manifest file exists at all for this character's asset folder. */
    data object Missing : CharacterManifestFailure

    /** A manifest exists but its declared grid is absent, non-numeric, or non-positive. */
    data class Malformed(val reason: String) : CharacterManifestFailure
}
