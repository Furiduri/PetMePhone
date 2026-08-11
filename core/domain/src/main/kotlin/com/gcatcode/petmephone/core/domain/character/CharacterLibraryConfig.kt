package com.gcatcode.petmephone.core.domain.character

/**
 * Injected character-library configuration — never a literal inside the importer or repository.
 */
data class CharacterLibraryConfig(
    val maxImportedCharacters: Int,
    val maxImportBytes: Long,
)
