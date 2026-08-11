package com.gcatcode.petmephone.feature.overlay.character.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.Character
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.character.CharacterName
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `[IMPORT-12]` — no delete affordance for a built-in entry; cap-reached message shown at cap;
 * a real captured name renders, an absent one renders an honest placeholder (design decision 14). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeCharacterRepository(
        initial: List<Character> = emptyList(),
    ) : CharacterRepository {
        val flow = MutableStateFlow(initial)
        override val importedCharacters: Flow<List<Character>> get() = flow
        var removed: CharacterId.Imported? = null

        override suspend fun add(character: Character) {
            flow.value = flow.value + character
        }

        override suspend fun remove(id: CharacterId.Imported) {
            removed = id
            flow.value = flow.value.filterNot { it.id == id }
        }
    }

    private class FakeActiveCharacterRepository : ActiveCharacterRepository {
        val flow = MutableStateFlow<CharacterId>(CharacterId.BuiltIn("default"))
        override val active: Flow<CharacterId> get() = flow
        override suspend fun setActive(id: CharacterId) {
            flow.value = id
        }
    }

    @Test
    fun `delete action is absent for the built-in entry`() {
        val repository = FakeCharacterRepository(
            listOf(Character(id = CharacterId.Imported("uuid-1"), name = CharacterName("Whiskers"))),
        )

        composeRule.setContent {
            LibraryScreen(
                repository = repository,
                activeCharacterRepository = FakeActiveCharacterRepository(),
                config = CharacterLibraryConfig(
                    maxImportedCharacters = 3,
                    maxImportBytes = 1_000_000,
                    builtInFallbackName = "default",
                ),
                onImportClick = {},
            )
        }

        composeRule.onNodeWithText("Default").assertExists()
        // Exactly one "Delete" button exists — for the imported entry only, never for "Default".
        composeRule.onAllNodesWithText("Delete").assertCountEquals(1)
    }

    @Test
    fun `an imported character's captured name renders`() {
        val repository = FakeCharacterRepository(
            listOf(Character(id = CharacterId.Imported("uuid-1"), name = CharacterName("Whiskers"))),
        )

        composeRule.setContent {
            LibraryScreen(
                repository = repository,
                activeCharacterRepository = FakeActiveCharacterRepository(),
                config = CharacterLibraryConfig(
                    maxImportedCharacters = 3,
                    maxImportBytes = 1_000_000,
                    builtInFallbackName = "default",
                ),
                onImportClick = {},
            )
        }

        composeRule.onNodeWithText("Whiskers").assertExists()
    }

    @Test
    fun `an imported character with no supplied name renders an honest placeholder, never Whiskers-style fabrication`() {
        val repository = FakeCharacterRepository(
            listOf(Character(id = CharacterId.Imported("uuid-1"), name = null)),
        )

        composeRule.setContent {
            LibraryScreen(
                repository = repository,
                activeCharacterRepository = FakeActiveCharacterRepository(),
                config = CharacterLibraryConfig(
                    maxImportedCharacters = 3,
                    maxImportBytes = 1_000_000,
                    builtInFallbackName = "default",
                ),
                onImportClick = {},
            )
        }

        composeRule.onNodeWithText("Unnamed").assertExists()
    }

    @Test
    fun `cap-reached message renders when the import count equals the cap`() {
        val repository = FakeCharacterRepository(
            listOf(
                Character(id = CharacterId.Imported("uuid-1"), name = CharacterName("A")),
                Character(id = CharacterId.Imported("uuid-2"), name = CharacterName("B")),
            ),
        )

        composeRule.setContent {
            LibraryScreen(
                repository = repository,
                activeCharacterRepository = FakeActiveCharacterRepository(),
                config = CharacterLibraryConfig(
                    maxImportedCharacters = 2,
                    maxImportBytes = 1_000_000,
                    builtInFallbackName = "default",
                ),
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
                activeCharacterRepository = FakeActiveCharacterRepository(),
                config = CharacterLibraryConfig(
                    maxImportedCharacters = 2,
                    maxImportBytes = 1_000_000,
                    builtInFallbackName = "default",
                ),
                onImportClick = {},
            )
        }

        // By tag, not by label: the action moved into the header beside the title and its wording
        // shortened to fit there. What the test is protecting is that the way in exists under the
        // cap, which is unchanged.
        composeRule.onNodeWithTag(LIBRARY_IMPORT_TEST_TAG).assertExists()
    }

    @Test
    fun `the import action stays on screen no matter how long the list gets`() {
        // The regression this closes: the list was an unweighted LazyColumn inside a Column, so it
        // claimed the full height and everything after it — the import button included — was pushed
        // off the screen entirely.
        val repository = FakeCharacterRepository()

        composeRule.setContent {
            LibraryScreen(
                repository = repository,
                activeCharacterRepository = FakeActiveCharacterRepository(),
                config = CharacterLibraryConfig(
                    maxImportedCharacters = 99,
                    maxImportBytes = 1_000_000,
                    builtInFallbackName = "default",
                ),
                onImportClick = {},
            )
        }

        composeRule.onNodeWithTag(LIBRARY_IMPORT_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Character library").assertIsDisplayed()
    }
}
