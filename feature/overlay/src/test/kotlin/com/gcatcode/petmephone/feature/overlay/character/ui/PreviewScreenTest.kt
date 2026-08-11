package com.gcatcode.petmephone.feature.overlay.character.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.feature.overlay.character.CharacterImporter
import com.gcatcode.petmephone.feature.overlay.character.ValidatedImport
import com.gcatcode.petmephone.feature.overlay.sprite.BitmapDecoding
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteFixtures
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetResult
import java.io.File
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

    private fun loadedImport(): ValidatedImport {
        val bytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 4)
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 64)
        val loaded = decoder.decode(bytes) as SpriteSheetResult.Loaded
        return ValidatedImport(uuid = "test-uuid", cacheFile = File.createTempFile("preview", ".png"), decoded = loaded)
    }

    @Test
    fun `preview shows the row-to-state mapping label`() {
        val import = loadedImport()

        composeRule.setContent {
            PreviewScreen(
                import = import,
                importer = fakeImporter(),
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
                currentImportedCount = 0,
                onImported = {},
                onCancel = {},
            )
        }

        composeRule.onNodeWithText("Confirm import").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
    }

    private fun fakeImporter() =
        CharacterImporter(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 64),
            bitmapDecoding = BitmapDecoding.Default(),
            maxDimensionPx = 64,
            config = CharacterLibraryConfig(maxImportedCharacters = 3, maxImportBytes = 1_000_000),
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
            config = CharacterLibraryConfig(maxImportedCharacters = 3, maxImportBytes = 1_000_000),
        )
        val controller = CharacterImportController(importer)
        val bytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 4)
        val file = File.createTempFile("picked", ".png")
        file.writeBytes(bytes)

        composeRule.setContent {
            ImportScreen(controller = controller, onPickImage = {}, onValidated = {})
        }

        composeRule.runOnIdle { controller.onImagePicked(Uri.fromFile(file)) }

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
