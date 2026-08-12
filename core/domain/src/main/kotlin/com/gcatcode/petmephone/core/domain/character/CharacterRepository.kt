package com.gcatcode.petmephone.core.domain.character

import kotlinx.coroutines.flow.Flow

/**
 * Exposes the persisted set of *imported* characters — built-in characters are a fixed
 * compile-time list and are never stored here. Carries the full [Character] (id plus name), not
 * just the id, so a captured import name survives everywhere the library is displayed.
 */
interface CharacterRepository {
    val importedCharacters: Flow<List<Character>>
    suspend fun add(character: Character)
    suspend fun remove(id: CharacterId.Imported)
}
