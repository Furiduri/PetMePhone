package com.gcatcode.petmephone.core.domain.character

import kotlinx.coroutines.flow.Flow

/**
 * Tracks which character is currently active for rendering. Never emits an unresolved state:
 * the stored pointer is either a real [CharacterId] or, when nothing was ever stored, the
 * implementation's built-in fallback — there is no third, "unknown" value.
 */
interface ActiveCharacterRepository {
    val active: Flow<CharacterId>
    suspend fun setActive(id: CharacterId)
}
