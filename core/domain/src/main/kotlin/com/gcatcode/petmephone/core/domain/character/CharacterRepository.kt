package com.gcatcode.petmephone.core.domain.character

import kotlinx.coroutines.flow.Flow

/**
 * Interface only in this PR (#39a). The DataStore-backed implementation and its `@Binds` land in
 * PR 5 (#39b) alongside the library UI. Exposes the persisted set of *imported* character ids —
 * built-in characters are a fixed compile-time list and are never stored here.
 */
interface CharacterRepository {
    val importedCharacters: Flow<Set<CharacterId.Imported>>
    suspend fun add(id: CharacterId.Imported)
    suspend fun remove(id: CharacterId.Imported)
}
