package com.gcatcode.petmephone.feature.overlay.character.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.Character
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGridDeclaration
import com.gcatcode.petmephone.feature.overlay.character.CharacterImporter
import com.gcatcode.petmephone.feature.overlay.character.ValidatedImport
import com.gcatcode.petmephone.feature.overlay.sprite.BitmapDecoding
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteFixtures
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetResult
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric 4.16.1 ships no SDK 37 shadows; `@Config(sdk = [36])` is the repo convention. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreviewScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeCharacterRepository : CharacterRepository {
        val flow = MutableStateFlow<List<Character>>(emptyList())
        var added: Character? = null
        override val importedCharacters: Flow<List<Character>> get() = flow
        override suspend fun add(character: Character) {
            added = character
            flow.value = flow.value + character
        }
        override suspend fun remove(id: CharacterId.Imported) {
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

    private fun loadedImport(): ValidatedImport {
        val bytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 4)
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 64)
        val loaded = decoder.decode(bytes, SpriteGridDeclaration(columns = 4, rows = 1)) as SpriteSheetResult.Loaded
        return ValidatedImport(uuid = "test-uuid", cacheFile = File.createTempFile("preview", ".png"), decoded = loaded)
    }

    @Test
    fun `preview shows the row-to-state mapping label`() {
        val import = loadedImport()

        composeRule.setContent {
            PreviewScreen(
                import = import,
                importer = fakeImporter(),
                repository = FakeCharacterRepository(),
                activeCharacterRepository = FakeActiveCharacterRepository(),
                currentImportedCount = 0,
                onImported = {},
                onCancel = {},
            )
        }

        composeRule.onNodeWithText("Maps to: IDLE").assertExists()
    }

    @Test
    fun `preview shows the confirm and cancel actions before commit`() {
        val import = loadedImport()

        composeRule.setContent {
            PreviewScreen(
                import = import,
                importer = fakeImporter(),
                repository = FakeCharacterRepository(),
                activeCharacterRepository = FakeActiveCharacterRepository(),
                currentImportedCount = 0,
                onImported = {},
                onCancel = {},
            )
        }

        composeRule.onNodeWithText("Confirm import").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun `an empty name confirms and persists a null name, never a fabricated string`() {
        val import = loadedImport()
        val repository = FakeCharacterRepository()

        composeRule.setContent {
            PreviewScreen(
                import = import,
                importer = fakeImporter(),
                repository = repository,
                activeCharacterRepository = FakeActiveCharacterRepository(),
                currentImportedCount = 0,
                onImported = {},
                onCancel = {},
            )
        }

        composeRule.onNodeWithText("Confirm import").performClick()
        waitForCondition { repository.added != null }

        assertEquals(null, repository.added?.name)
    }

    @Test
    fun `a supplied name is captured and persisted exactly`() {
        val import = loadedImport()
        val repository = FakeCharacterRepository()

        composeRule.setContent {
            PreviewScreen(
                import = import,
                importer = fakeImporter(),
                repository = repository,
                activeCharacterRepository = FakeActiveCharacterRepository(),
                currentImportedCount = 0,
                onImported = {},
                onCancel = {},
            )
        }

        composeRule.onNodeWithText("Name (optional)").performTextInput("Whiskers")
        composeRule.onNodeWithText("Confirm import").performClick()
        waitForCondition { repository.added != null }

        assertEquals("Whiskers", repository.added?.name?.value)
    }

    private fun waitForCondition(timeoutMillis: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!predicate()) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            composeRule.waitForIdle()
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for condition")
            }
            Thread.sleep(10)
        }
    }

    private fun fakeImporter() =
        CharacterImporter(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 64),
            bitmapDecoding = BitmapDecoding.Default(),
            maxDimensionPx = 64,
            config = CharacterLibraryConfig(
                maxImportedCharacters = 3,
                maxImportBytes = 1_000_000,
                builtInFallbackName = "default",
            ),
        )

    /** Wraps the real [BitmapDecoding.Default] but blocks briefly inside [decodeFull] — the only
     * costly tier — so tests can observe the loading window without faking the decode itself. */
    private class SlowBitmapDecoding(private val delayMillis: Long) : BitmapDecoding {
        private val delegate = BitmapDecoding.Default()
        override fun decodeBounds(bytes: ByteArray) = delegate.decodeBounds(bytes)
        override fun decodeFull(bytes: ByteArray): Bitmap? {
            Thread.sleep(delayMillis)
            return delegate.decodeFull(bytes)
        }
    }

    @Test
    fun `ImportScreen shows the loading indicator for the duration of a slow tier-3 decode`() {
        val slowBitmapDecoding = SlowBitmapDecoding(delayMillis = 300)
        val importer = CharacterImporter(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            decoder = SpriteSheetDecoder(slowBitmapDecoding, maxDimensionPx = 64),
            bitmapDecoding = slowBitmapDecoding,
            maxDimensionPx = 64,
            config = CharacterLibraryConfig(
                maxImportedCharacters = 3,
                maxImportBytes = 1_000_000,
                builtInFallbackName = "default",
            ),
        )
        val controller = CharacterImportController(importer)
        val bytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 4)
        val file = File.createTempFile("picked", ".png")
        file.writeBytes(bytes)

        composeRule.setContent {
            ImportScreen(controller = controller, onPickImage = {}, onValidated = {})
        }

        composeRule.runOnIdle { controller.onImagePicked(Uri.fromFile(file)) }

        waitForState(composeRule, controller) { it is CharacterImportController.State.GridEntry }
        composeRule.onNodeWithText("Preview").performClick()

        // Tier 1+2 pass quickly, then tier 3 is genuinely slow: the loading indicator must be
        // visible mid-decode, not just flash-and-gone. `Dispatchers.Main` posts its continuation
        // back onto Robolectric's (paused) main looper, so pump it by hand rather than relying on
        // `composeRule.waitUntil` alone to drive a raw coroutine dispatch outside Compose's clock.
        waitForState(composeRule, controller) { it is CharacterImportController.State.DecodingAndScanning }
        composeRule.onNodeWithText("Reading the animation…").assertExists()

        waitForState(composeRule, controller) { it is CharacterImportController.State.Ready }
    }

    private fun waitForState(
        rule: androidx.compose.ui.test.junit4.ComposeContentTestRule,
        controller: CharacterImportController,
        timeoutMillis: Long = 5_000,
        predicate: (CharacterImportController.State) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!predicate(controller.state.value)) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            rule.waitForIdle()
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for controller state; last state was ${controller.state.value}")
            }
            Thread.sleep(10)
        }
    }
}
