package com.gcatcode.petmephone.core.domain.character

/**
 * Injected character-library configuration — never a literal inside the importer or repository.
 *
 * [builtInFallbackName] names the [CharacterId.BuiltIn] used whenever no active pointer is
 * stored, or a deletion removes the currently active pointer's target (design.md decision 8).
 */
data class CharacterLibraryConfig(
    val maxImportedCharacters: Int,
    val maxImportBytes: Long,
    val builtInFallbackName: String,
)
