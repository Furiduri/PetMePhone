package com.gcatcode.petmephone.core.data.character

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.gcatcode.petmephone.core.domain.character.CharacterId
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

/** `[IMPORT-7]` `[IMPORT-12]` — cap counts only imported characters, delete updates the set and the folder. */
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
    fun `no imported characters persisted emits an empty set`() = runTest {
        val repository = CharacterRepositoryImpl(realDataStore(), fakeContext(temporaryFolder.newFolder("files")))

        val emitted = repository.importedCharacters.first()

        assertTrue(emitted.isEmpty())
    }

    @Test
    fun `adding an imported id below cap persists it and is reflected in the count`() = runTest {
        val repository = CharacterRepositoryImpl(realDataStore(), fakeContext(temporaryFolder.newFolder("files")))
        val id = CharacterId.Imported("uuid-below-cap")

        repository.add(id)
        val emitted = repository.importedCharacters.first()

        assertEquals(setOf(id), emitted)
    }

    /**
     * Same double-write caveat as `OverlayPositionRepositoryImplTest`'s legacy-key test: seeding a
     * real on-disk DataStore and then writing again in the same test fails on Windows. The
     * persisted-set transform [CharacterRepositoryImpl.remove] submits is proven directly instead.
     */
    @Test
    fun `deleting an id removes it from the persisted set`() = runTest {
        val key = stringSetPreferencesKey("characters")
        val seeded = mutablePreferencesOf(key to setOf("kept-uuid", "removed-uuid"))

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
        assertEquals(setOf("kept-uuid"), result[key])
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
