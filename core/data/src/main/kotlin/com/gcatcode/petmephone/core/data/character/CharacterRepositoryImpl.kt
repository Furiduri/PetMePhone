package com.gcatcode.petmephone.core.data.character

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.gcatcode.petmephone.core.domain.character.Character
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterName
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * DataStore-backed store for the *imported* character set (design decision 8:
 * `stringSetPreferencesKey("characters")`). Built-in characters are a fixed compile-time list and
 * are never persisted here, so the cap check reads only this set's size.
 *
 * Each imported id's captured name is stored in its own `stringPreferencesKey`, read back through
 * [CharacterName.validOrNull] — an absent or blank stored value reads as `null`, never a fabricated
 * default.
 *
 * [remove] deletes the whole `filesDir/characters/<uuid>/` folder, never just `idle.png`, so a
 * half-deleted character — a folder with some animation files gone and others left behind — cannot
 * exist as an observable state.
 */
class CharacterRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) : CharacterRepository {

    override val importedCharacters: Flow<List<Character>>
        get() = dataStore.data.map { preferences ->
            (preferences[CHARACTERS_KEY] ?: emptySet())
                .map { uuid ->
                    val id = CharacterId.Imported(uuid)
                    val name = CharacterName.validOrNull(preferences[nameKey(uuid)])
                    Character(id = id, name = name)
                }
        }

    override suspend fun add(character: Character) {
        val id = character.id as? CharacterId.Imported
            ?: error("CharacterRepository only persists imported characters, got ${character.id}")
        dataStore.edit { preferences ->
            val current = preferences[CHARACTERS_KEY] ?: emptySet()
            preferences[CHARACTERS_KEY] = current + id.uuid
            val name = character.name
            if (name != null) {
                preferences[nameKey(id.uuid)] = name.value
            } else {
                preferences.remove(nameKey(id.uuid))
            }
        }
    }

    override suspend fun remove(id: CharacterId.Imported) {
        dataStore.edit { preferences ->
            val current = preferences[CHARACTERS_KEY] ?: emptySet()
            preferences[CHARACTERS_KEY] = current - id.uuid
            preferences.remove(nameKey(id.uuid))
        }
        withContext(Dispatchers.IO) {
            characterDir(id.uuid).deleteRecursively()
        }
    }

    private fun characterDir(uuid: String): File = File(File(context.filesDir, "characters"), uuid)

    private companion object {
        val CHARACTERS_KEY = stringSetPreferencesKey("characters")
        fun nameKey(uuid: String) = stringPreferencesKey("character_name_$uuid")
    }
}
