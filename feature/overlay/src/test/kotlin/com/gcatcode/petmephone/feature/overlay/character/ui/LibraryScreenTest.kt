package com.gcatcode.petmephone.feature.overlay.character.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `[IMPORT-12]` — no delete affordance for a built-in entry; cap-reached message shown at cap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeCharacterRepository(
        initial: Set<CharacterId.Imported> = emptySet(),
    ) : CharacterRepository {
        val flow = MutableStateFlow(initial)
        override val importedCharacters: Flow<Set<CharacterId.Imported>> get() = flow
        var removed: CharacterId.Imported? = null

        override suspend fun add(id: CharacterId.Imported) {
            flow.value = flow.value + id
        }

        override suspend fun remove(id: CharacterId.Imported) {
            removed = id
            flow.value = flow.value - id
        }
    }

    @Test
    fun `delete action is absent for the built-in entry`() {
        val repository = FakeCharacterRepository(setOf(CharacterId.Imported("uuid-1")))

        composeRule.setContent {
            LibraryScreen(
                repository = repository,
                config = CharacterLibraryConfig(maxImportedCharacters = 3, maxImportBytes = 1_000_000),
                onImportClick = {},
            )
        }

        composeRule.onNodeWithText("Default").assertExists()
        // Exactly one "Delete" button exists — for the imported entry only, never for "Default".
        composeRule.onAllNodesWithText("Delete").assertCountEquals(1)
    }

    @Test
    fun `cap-reached message renders when the import count equals the cap`() {
        val repository = FakeCharacterRepository(
            setOf(CharacterId.Imported("uuid-1"), CharacterId.Imported("uuid-2")),
        )

        composeRule.setContent {
            LibraryScreen(
                repository = repository,
                config = CharacterLibraryConfig(maxImportedCharacters = 2, maxImportBytes = 1_000_000),
                onImportClick = {},
            )
        }

        composeRule.onNodeWithText("You already have 2 characters, the maximum. Delete one to import another.").assertExists()
    }

    @Test
    fun `import action renders instead of the cap message when under the cap`() {
        val repository = FakeCharacterRepository()

        composeRule.setContent {
            LibraryScreen(
                repository = repository,
                config = CharacterLibraryConfig(maxImportedCharacters = 2, maxImportBytes = 1_000_000),
                onImportClick = {},
            )
        }

        composeRule.onNodeWithText("Import a character").assertExists()
    }
}
