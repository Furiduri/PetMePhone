package com.gcatcode.petmephone.core.data.character

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.gcatcode.petmephone.core.domain.character.Character
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.character.CharacterName
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `[RENDER-3]` `[IMPORT-11]` — no stored pointer, and a deletion that removes the active
 * target, both resolve to the injected built-in fallback; deleting a non-active character leaves
 * the pointer untouched.
 *
 * Runs under Robolectric (as [OverlayPermissionCheckerImplTest] already does) rather than plain
 * JUnit: an un-stubbed `Build.VERSION.SDK_INT` reads as `0` on a plain JVM unit test, which routes
 * DataStore's internal file rename through the legacy `File.renameTo` path instead of
 * `Files.move(..., REPLACE_EXISTING)`. `renameTo` cannot overwrite an existing target on Windows,
 * so every `edit()` past the first one in a test fails with "Unable to rename ... this likely
 * means there are multiple instances of DataStore" — a false diagnosis; there was only ever one
 * instance. Declaring the real SDK level exercises the code path DataStore actually ships with. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActiveCharacterRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun realDataStore(): DataStore<Preferences> {
        val file = temporaryFolder.root.resolve("active_character_test.preferences_pb")
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun fakeContext(filesDir: File): Context {
        val context = mockk<Context>()
        every { context.filesDir } returns filesDir
        return context
    }

    private val defaultConfig = CharacterLibraryConfig(
        maxImportedCharacters = 10,
        maxImportBytes = 10_000_000,
        builtInFallbackName = "default",
    )

    @Test
    fun `no active pointer stored resolves to the built-in fallback`() = runTest {
        val repository = ActiveCharacterRepositoryImpl(realDataStore(), defaultConfig)

        val active = repository.active.first()

        assertEquals(CharacterId.BuiltIn("default"), active)
    }

    @Test
    fun `setActive persists an imported id and reads it back`() = runTest {
        val repository = ActiveCharacterRepositoryImpl(realDataStore(), defaultConfig)
        val id = CharacterId.Imported("uuid-1")

        repository.setActive(id)
        val active = repository.active.first()

        assertEquals(id, active)
    }

    @Test
    fun `deleting the active character falls back to the built-in`() = runTest {
        val dataStore = realDataStore()
        val activeRepository = ActiveCharacterRepositoryImpl(dataStore, defaultConfig)
        val characterRepository = CharacterRepositoryImpl(
            dataStore,
            fakeContext(temporaryFolder.newFolder("files")),
            activeRepository,
            defaultConfig,
        )
        val id = CharacterId.Imported("active-uuid")
        characterRepository.add(Character(id = id, name = CharacterName("Whiskers")))
        activeRepository.setActive(id)

        characterRepository.remove(id)

        assertEquals(CharacterId.BuiltIn("default"), activeRepository.active.first())
    }

    @Test
    fun `deleting a non-active character leaves the active pointer untouched`() = runTest {
        val dataStore = realDataStore()
        val activeRepository = ActiveCharacterRepositoryImpl(dataStore, defaultConfig)
        val characterRepository = CharacterRepositoryImpl(
            dataStore,
            fakeContext(temporaryFolder.newFolder("files")),
            activeRepository,
            defaultConfig,
        )
        val activeId = CharacterId.Imported("active-uuid")
        val otherId = CharacterId.Imported("other-uuid")
        characterRepository.add(Character(id = activeId, name = CharacterName("Whiskers")))
        characterRepository.add(Character(id = otherId, name = CharacterName("Mittens")))
        activeRepository.setActive(activeId)

        characterRepository.remove(otherId)

        assertEquals(activeId, activeRepository.active.first())
    }
}
