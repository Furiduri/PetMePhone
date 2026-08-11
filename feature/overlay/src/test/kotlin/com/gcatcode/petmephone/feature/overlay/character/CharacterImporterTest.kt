package com.gcatcode.petmephone.feature.overlay.character

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.gcatcode.petmephone.core.domain.character.CharacterImportRejection
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGridDeclaration
import com.gcatcode.petmephone.feature.overlay.sprite.BitmapDecoding
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteFixtures
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import java.io.File
import java.util.Properties
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric 4.16.1 ships no SDK 37 shadows; `@Config(sdk = [36])` is the repo convention. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CharacterImporterTest {

    private val maxDimensionPx = 64
    private val maxImportBytes = 1_000_000L
    private val maxImportedCharacters = 3

    private class CountingBitmapDecoding(private val delegate: BitmapDecoding) : BitmapDecoding {
        var fullDecodeCalls = 0
            private set

        override fun decodeBounds(bytes: ByteArray) = delegate.decodeBounds(bytes)

        override fun decodeFull(bytes: ByteArray): Bitmap? {
            fullDecodeCalls++
            return delegate.decodeFull(bytes)
        }
    }

    private lateinit var counting: CountingBitmapDecoding
    private lateinit var importer: CharacterImporter

    @Before
    fun setUp() {
        counting = CountingBitmapDecoding(BitmapDecoding.Default())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val decoder = SpriteSheetDecoder(counting, maxDimensionPx = maxDimensionPx)
        val config = CharacterLibraryConfig(
            maxImportedCharacters = maxImportedCharacters,
            maxImportBytes = maxImportBytes,
            builtInFallbackName = "default",
        )
        importer = CharacterImporter(context, decoder, counting, maxDimensionPx, config)
    }

    private fun uriFor(bytes: ByteArray, name: String = "picked.png"): Uri {
        val file = File.createTempFile(name, null)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    @Test
    fun `oversized image rejected at staging with zero full-decode calls`() = runTest {
        // cellSizePx 20 * 6 columns = 120 width, well over the 64px bound.
        val bytes = SpriteFixtures.validSheetBytes(cellSizePx = 20, columns = 6)

        val result = importer.import(uriFor(bytes))

        val rejected = result as CharacterImportResult.Rejected
        assertTrue(rejected.reason is CharacterImportRejection.Oversized)
        assertEquals(0, counting.fullDecodeCalls)
    }

    @Test
    fun `corrupt bytes rejected at header tier with a distinct message, never a crash`() = runTest {
        val result = importer.import(uriFor(byteArrayOf(1, 2, 3)))

        val rejected = result as CharacterImportResult.Rejected
        assertEquals(CharacterImportRejection.NotPng, rejected.reason)
        assertEquals(0, counting.fullDecodeCalls)
    }

    @Test
    fun `valid sheet with an all-transparent row rejected at tier 3 with EmptySheet`() = runTest {
        val bytes = SpriteFixtures.emptySheetBytes(cellSizePx = 8, columns = 6)

        val result = importer.import(uriFor(bytes))

        val rejected = result as CharacterImportResult.Rejected
        assertEquals(CharacterImportRejection.EmptySheet, rejected.reason)
        assertEquals(1, counting.fullDecodeCalls)
    }

    @Test
    fun `over-byte-ceiling file rejected at tier 1 with zero full-decode calls`() = runTest {
        val validBytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 6)
        val tinyMaxConfig = CharacterLibraryConfig(
            maxImportedCharacters = maxImportedCharacters,
            maxImportBytes = 4,
            builtInFallbackName = "default",
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val decoder = SpriteSheetDecoder(counting, maxDimensionPx = maxDimensionPx)
        val tinyImporter = CharacterImporter(context, decoder, counting, maxDimensionPx, tinyMaxConfig)

        val result = tinyImporter.import(uriFor(validBytes))

        val rejected = result as CharacterImportResult.Rejected
        assertTrue(rejected.reason is CharacterImportRejection.TooLarge)
        assertEquals(0, counting.fullDecodeCalls)
    }

    @Test
    fun `stage proposes a candidate grid the caller never validates automatically`() = runTest {
        val bytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 6)

        val staged = (importer.stage(uriFor(bytes)) as StageResult.Staged).staged

        assertEquals(SpriteGridDeclaration(columns = 6, rows = 1), staged.candidate)
    }

    @Test
    fun `a wrong declared grid over a square sheet is rejected, never accepted as one frame`() = runTest {
        // The regression #69 exists to close: a genuinely 6x6 square sheet declared as 1x1 must be
        // rejected as NotDivisible (a non-square resulting cell), never silently drawn as one frame.
        val bytes = SpriteFixtures.multiRowSheetBytes(cellSizePx = 8, columns = 6, rows = 6)
        val staged = (importer.stage(uriFor(bytes)) as StageResult.Staged).staged

        val wrongDeclaration = importer.decodeAndScan(staged, SpriteGridDeclaration(columns = 1, rows = 6))

        val rejected = wrongDeclaration as CharacterImportResult.Rejected
        assertTrue(rejected.reason is CharacterImportRejection.NotDivisible)
    }

    @Test
    fun `a correctly declared 6x6 grid loads all 36 frames`() = runTest {
        val bytes = SpriteFixtures.multiRowSheetBytes(cellSizePx = 8, columns = 6, rows = 6, opaqueFrames = 36)
        val staged = (importer.stage(uriFor(bytes)) as StageResult.Staged).staged

        val validated = importer.decodeAndScan(staged, SpriteGridDeclaration(columns = 6, rows = 6))

        val ready = validated as CharacterImportResult.Validated
        assertEquals(36, ready.import.decoded.layout.frameCount)
    }

    @Test
    fun `import attempted at cap is rejected with CapReached guidance`() = runTest {
        val bytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 6)
        val validated = (importer.import(uriFor(bytes)) as CharacterImportResult.Validated).import

        val confirmResult = importer.confirm(validated, currentImportedCount = maxImportedCharacters)

        assertTrue(confirmResult.isFailure)
        val exception = confirmResult.exceptionOrNull() as CharacterImportRejectionException
        assertEquals(CharacterImportRejection.CapReached(maxImportedCharacters), exception.reason)
    }

    @Test
    fun `a validated but not confirmed import leaves no file under filesDir characters`() = runTest {
        val bytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 6)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        importer.import(uriFor(bytes))

        val charactersDir = File(context.filesDir, "characters")
        assertFalse(charactersDir.exists() && charactersDir.listFiles()?.isNotEmpty() == true)
    }

    @Test
    fun `a confirmed import moves the file to the folder path and the cache copy is gone`() = runTest {
        val bytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 6)
        val validated = (importer.import(uriFor(bytes)) as CharacterImportResult.Validated).import
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val result = importer.confirm(validated, currentImportedCount = 0)

        assertTrue(result.isSuccess)
        val destination = File(File(File(context.filesDir, "characters"), validated.uuid), "idle.png")
        assertTrue(destination.exists())
        assertFalse(validated.cacheFile.exists())
    }

    @Test
    fun `a confirmed import persists the declared grid so a later load can decode the sheet`() = runTest {
        // The grid the user confirmed lives only in memory until confirm() writes it down. Without
        // this file every reload of an imported character decodes against nothing and reads Broken.
        val bytes = SpriteFixtures.multiRowSheetBytes(cellSizePx = 8, columns = 6, rows = 6, opaqueFrames = 36)
        val staged = (importer.stage(uriFor(bytes)) as StageResult.Staged).staged
        val declaration = SpriteGridDeclaration(columns = 6, rows = 6)
        val validated = (importer.decodeAndScan(staged, declaration) as CharacterImportResult.Validated).import
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertTrue(importer.confirm(validated, currentImportedCount = 0).isSuccess)

        val manifest = File(File(File(context.filesDir, "characters"), validated.uuid), "manifest.properties")
        assertTrue("confirm must persist the declared grid", manifest.exists())
        val properties = Properties().apply { manifest.inputStream().use { load(it) } }
        assertEquals("6", properties.getProperty("columns"))
        assertEquals("6", properties.getProperty("rows"))
    }

    @Test
    fun `a confirm that fails part way leaves no half-built character folder behind`() = runTest {
        val bytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 6)
        val validated = (importer.import(uriFor(bytes)) as CharacterImportResult.Validated).import
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Occupying the destination path with a non-empty directory makes the copy throw, so the
        // rollback runs against a folder that already holds content — the case a plain
        // File.delete() silently refuses, stranding the folder.
        val characterDir = File(File(context.filesDir, "characters"), validated.uuid)
        File(characterDir, "idle.png").mkdirs()
        File(File(characterDir, "idle.png"), "occupied").writeText("x")

        val result = importer.confirm(validated, currentImportedCount = 0)

        assertTrue(result.isFailure)
        assertFalse("a failed confirm must leave nothing behind", characterDir.exists())
    }
}
