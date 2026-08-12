package com.gcatcode.petmephone.core.data.character

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed store for the active-character pointer (design decision 8:
 * `stringPreferencesKey("active_character")`). Never emits an unresolved value: a missing,
 * malformed, or stale (deleted-target) stored pointer all resolve to the injected
 * [CharacterLibraryConfig.builtInFallbackName] built-in, never a fabricated "no character" state.
 *
 * The stored value is a small tagged encoding (`builtin:<name>` / `imported:<uuid>`), not a raw
 * uuid, so [CharacterId.BuiltIn] and [CharacterId.Imported] round-trip without ambiguity.
 */
class ActiveCharacterRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val config: CharacterLibraryConfig,
) : ActiveCharacterRepository {

    private val builtInFallback: CharacterId.BuiltIn
        get() = CharacterId.BuiltIn(config.builtInFallbackName)

    override val active: Flow<CharacterId>
        get() = dataStore.data.map { preferences ->
            decode(preferences[ACTIVE_CHARACTER_KEY]) ?: builtInFallback
        }

    override suspend fun setActive(id: CharacterId) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_CHARACTER_KEY] = encode(id)
        }
    }

    private fun encode(id: CharacterId): String = when (id) {
        is CharacterId.BuiltIn -> "$BUILT_IN_PREFIX${id.name}"
        is CharacterId.Imported -> "$IMPORTED_PREFIX${id.uuid}"
    }

    private fun decode(stored: String?): CharacterId? = when {
        stored == null -> null
        stored.startsWith(BUILT_IN_PREFIX) -> CharacterId.BuiltIn(stored.removePrefix(BUILT_IN_PREFIX))
        stored.startsWith(IMPORTED_PREFIX) -> CharacterId.Imported(stored.removePrefix(IMPORTED_PREFIX))
        else -> null
    }

    private companion object {
        val ACTIVE_CHARACTER_KEY = stringPreferencesKey("active_character")
        const val BUILT_IN_PREFIX = "builtin:"
        const val IMPORTED_PREFIX = "imported:"
    }
}
