package com.gcatcode.petmephone.core.data.character

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.gcatcode.petmephone.core.domain.character.Character
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterName
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `[IMPORT-7]` `[IMPORT-12]` — cap counts only imported characters, delete updates the set and the
 * folder, and a captured name persists and reads back through absence-preserving validation. */
class CharacterRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun realDataStore(): DataStore<Preferences> {
        // Same Windows-rename caveat as `OverlayPositionRepositoryImplTest`: the target file must
        // not already exist, so DataStore creates it on the first write.
        val file = temporaryFolder.root.resolve("characters_test.preferences_pb")
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun fakeContext(filesDir: File): Context {
        val context = mockk<Context>()
        every { context.filesDir } returns filesDir
        return context
    }

    @Test
    fun `no imported characters persisted emits an empty list`() = runTest {
        val repository = CharacterRepositoryImpl(realDataStore(), fakeContext(temporaryFolder.newFolder("files")))

        val emitted = repository.importedCharacters.first()

        assertTrue(emitted.isEmpty())
    }

    @Test
    fun `adding a character below cap persists it, name included`() = runTest {
        val repository = CharacterRepositoryImpl(realDataStore(), fakeContext(temporaryFolder.newFolder("files")))
        val character = Character(id = CharacterId.Imported("uuid-below-cap"), name = CharacterName("Whiskers"))

        repository.add(character)
        val emitted = repository.importedCharacters.first()

        assertEquals(listOf(character), emitted)
    }

    @Test
    fun `adding a character with no supplied name persists it with name null, never a fabricated string`() = runTest {
        val repository = CharacterRepositoryImpl(realDataStore(), fakeContext(temporaryFolder.newFolder("files")))
        val character = Character(id = CharacterId.Imported("uuid-unnamed"), name = null)

        repository.add(character)
        val emitted = repository.importedCharacters.first()

        assertEquals(listOf(character), emitted)
        assertEquals(null, emitted.single().name)
    }

    /**
     * Same double-write caveat as `OverlayPositionRepositoryImplTest`'s legacy-key test: seeding a
     * real on-disk DataStore and then writing again in the same test fails on Windows. The
     * persisted-set transform [CharacterRepositoryImpl.remove] submits is proven directly instead.
     */
    @Test
    fun `deleting an id removes it from the persisted set and clears its name key`() = runTest {
        val idsKey = stringSetPreferencesKey("characters")
        val nameKey = stringPreferencesKey("character_name_removed-uuid")
        val seeded = mutablePreferencesOf(
            idsKey to setOf("kept-uuid", "removed-uuid"),
            nameKey to "Whiskers",
        )

        var appliedResult: Preferences? = null
        val fakeDataStore = mockk<DataStore<Preferences>>()
        coEvery { fakeDataStore.updateData(any()) } coAnswers {
            val transform = firstArg<suspend (Preferences) -> Preferences>()
            transform(seeded).also { appliedResult = it }
        }
        val filesDir = temporaryFolder.newFolder("files")
        val repository = CharacterRepositoryImpl(fakeDataStore, fakeContext(filesDir))

        repository.remove(CharacterId.Imported("removed-uuid"))

        val result = requireNotNull(appliedResult)
        assertEquals(setOf("kept-uuid"), result[idsKey])
        assertEquals(null, result[nameKey])
    }

    @Test
    fun `deleting an id removes the whole character folder, not just idle png`() = runTest {
        val filesDir = temporaryFolder.newFolder("files")
        val characterDir = File(File(filesDir, "characters"), "removed-uuid").apply { mkdirs() }
        File(characterDir, "idle.png").writeText("idle")
        File(characterDir, "happy.png").writeText("happy")

        val fakeDataStore = mockk<DataStore<Preferences>>()
        coEvery { fakeDataStore.updateData(any()) } coAnswers {
            val transform = firstArg<suspend (Preferences) -> Preferences>()
            transform(mutablePreferencesOf())
        }
        val repository = CharacterRepositoryImpl(fakeDataStore, fakeContext(filesDir))

        repository.remove(CharacterId.Imported("removed-uuid"))

        assertFalse(characterDir.exists())
    }
}
