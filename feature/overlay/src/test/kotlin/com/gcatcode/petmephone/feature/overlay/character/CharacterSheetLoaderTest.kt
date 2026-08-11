package com.gcatcode.petmephone.feature.overlay.character

import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteSheetFailure
import com.gcatcode.petmephone.core.domain.pet.state.PetState
import com.gcatcode.petmephone.feature.overlay.sprite.BitmapDecoding
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteFixtures
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Drives [CharacterSheetLoader] through fake [CharacterAssetSource]s — one shaped like a built-in
 * folder, one shaped like an imported folder — and through the real `CharacterId`-based [load]
 * overload against a real `filesDir` folder, to prove no id-type branching survives past source
 * selection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CharacterSheetLoaderTest {

    private val context = RuntimeEnvironment.getApplication()
    private val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 2048)
    private val loader = CharacterSheetLoader(context, decoder)

    private val manifestBytes = "columns=6\nrows=1\n".toByteArray()
    private val idleBytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 6)

    private fun fakeSource(files: Map<String, ByteArray>): CharacterAssetSource =
        CharacterAssetSource { fileName -> files[fileName]?.let { ByteArrayInputStream(it) } as InputStream? }

    @Test
    fun `a missing optional animation file is an ordinary absence, not Broken`() {
        val source = fakeSource(
            mapOf(
                "manifest.properties" to manifestBytes,
                "idle.png" to idleBytes,
                // "dragging.png" deliberately absent.
            ),
        )

        val result = loader.load(source)

        assertTrue("expected Ready, got $result", result is CharacterSheets.Ready)
        val ready = result as CharacterSheets.Ready
        assertFalse(ready.byState.containsKey(PetState.DRAGGING))
        assertTrue(ready.byState.containsKey(PetState.IDLE))
        assertEquals(ready.idle, ready.byState[PetState.IDLE])
    }

    @Test
    fun `a missing idle png is Broken, covering a file deleted outside the app`() {
        val source = fakeSource(
            mapOf(
                "manifest.properties" to manifestBytes,
                // "idle.png" deliberately absent.
            ),
        )

        val result = loader.load(source)

        assertTrue("expected Broken, got $result", result is CharacterSheets.Broken)
        assertEquals(SpriteSheetFailure.Undecodable, (result as CharacterSheets.Broken).failure)
    }

    @Test
    fun `an idle png that fails to decode is Broken with the real failure reason`() {
        // Bytes whose header dimensions do not divide evenly under the manifest's declared 6x1
        // grid — a real decode failure, not merely an absent file, so the loader must report the
        // specific `SpriteSheetFailure` the decoder returned rather than a fabricated one.
        val source = fakeSource(
            mapOf(
                "manifest.properties" to manifestBytes,
                "idle.png" to SpriteFixtures.nonDivisibleHeaderBytes(),
            ),
        )

        val result = loader.load(source)

        assertTrue("expected Broken, got $result", result is CharacterSheets.Broken)
        assertEquals(SpriteSheetFailure.NotDivisible, (result as CharacterSheets.Broken).failure)
    }

    @Test
    fun `a missing manifest is Broken, since no declaration exists to decode against`() {
        val source = fakeSource(mapOf("idle.png" to idleBytes))

        val result = loader.load(source)

        assertTrue("expected Broken, got $result", result is CharacterSheets.Broken)
    }

    @Test
    fun `built-in-shaped and imported-shaped sources run the identical decode path`() {
        val files = mapOf(
            "manifest.properties" to manifestBytes,
            "idle.png" to idleBytes,
        )

        val builtInShaped = loader.load(fakeSource(files))
        val importedShaped = loader.load(fakeSource(files))

        assertTrue(builtInShaped is CharacterSheets.Ready)
        assertTrue(importedShaped is CharacterSheets.Ready)
        val builtInReady = builtInShaped as CharacterSheets.Ready
        val importedReady = importedShaped as CharacterSheets.Ready
        assertEquals(builtInReady.byState.keys, importedReady.byState.keys)
        assertEquals(builtInReady.idle.layout, importedReady.idle.layout)
    }

    @Test
    fun `load(CharacterId) selects the source but decodes through the same shared path`() {
        val uuid = "loader-test-uuid"
        val characterDir = File(File(File(context.filesDir, "characters"), uuid), "")
        characterDir.mkdirs()
        File(characterDir, "manifest.properties").writeBytes(manifestBytes)
        File(characterDir, "idle.png").writeBytes(idleBytes)

        val fromId = loader.load(CharacterId.Imported(uuid))
        val fromSource = loader.load(fakeSource(mapOf("manifest.properties" to manifestBytes, "idle.png" to idleBytes)))

        assertTrue(fromId is CharacterSheets.Ready)
        assertTrue(fromSource is CharacterSheets.Ready)
        assertEquals((fromSource as CharacterSheets.Ready).byState.keys, (fromId as CharacterSheets.Ready).byState.keys)
    }

    private fun readyFrom(manifest: String): CharacterSheets.Ready {
        val result = loader.load(
            fakeSource(mapOf("manifest.properties" to manifest.toByteArray(), "idle.png" to idleBytes)),
        )
        assertTrue("expected Ready, got $result", result is CharacterSheets.Ready)
        return result as CharacterSheets.Ready
    }

    @Test
    fun `a declared cycle duration is carried through to the renderer`() {
        assertEquals(900L, readyFrom("columns=6\nrows=1\ndurationMillis=900\n").cycleDurationMillis)
    }

    @Test
    fun `a character that declares no duration reports none, rather than a default standing in for one`() {
        // The fallback belongs to the renderer's injected config. Inventing a number here would
        // make "the character asked for this speed" indistinguishable from "nobody said".
        assertEquals(null, readyFrom("columns=6\nrows=1\n").cycleDurationMillis)
    }

    @Test
    fun `a zero, negative or unparseable duration is treated as undeclared, never as a frozen animation`() {
        assertEquals(null, readyFrom("columns=6\nrows=1\ndurationMillis=0\n").cycleDurationMillis)
        assertEquals(null, readyFrom("columns=6\nrows=1\ndurationMillis=-500\n").cycleDurationMillis)
        assertEquals(null, readyFrom("columns=6\nrows=1\ndurationMillis=fast\n").cycleDurationMillis)
    }

    @Test
    fun `a malformed duration never costs the character its grid`() {
        // The grid is required and the duration is not, so a typo in the optional key must not
        // demote a perfectly decodable character to Broken.
        val ready = readyFrom("columns=6\nrows=1\ndurationMillis=fast\n")

        assertEquals(6, ready.idle.layout.frameCount)
    }
}
