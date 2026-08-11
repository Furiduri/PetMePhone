package com.gcatcode.petmephone.feature.overlay.character

import android.content.Context
import android.net.Uri
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterImportRejection
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGridResult
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteSheetFailure
import com.gcatcode.petmephone.feature.overlay.sprite.BitmapDecoding
import com.gcatcode.petmephone.feature.overlay.sprite.MaxSpriteDimensionPx
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A tier-1/tier-2 pass staged in `cacheDir/import/<uuid>.png`, not yet full-decoded. */
data class StagedImport(val uuid: String, val cacheFile: File)

/** Result of [CharacterImporter.stage]: tiers 1 (header) and 2 (bounds), no pixel buffer allocated. */
sealed interface StageResult {
    data class Staged(val staged: StagedImport) : StageResult
    data class Rejected(val reason: CharacterImportRejection) : StageResult
}

/**
 * Result of a passed import: the cached bytes are staged and validated, but not yet moved into
 * the library. [CharacterImporter.confirm] performs the move; abandoning this result (never
 * calling it) leaves the cache file for the OS to reclaim and nothing under `filesDir/characters/`.
 */
data class ValidatedImport(
    val uuid: String,
    val cacheFile: File,
    val decoded: SpriteSheetResult.Loaded,
)

sealed interface CharacterImportResult {
    data class Validated(val import: ValidatedImport) : CharacterImportResult
    data class Rejected(val reason: CharacterImportRejection) : CharacterImportResult
}

/**
 * Picking-to-library import pipeline. Runs three tiers, in order, stopping at the first failure
 * (design.md "Import pipeline" table):
 *  1. copy the picked [Uri] to `cacheDir/import/<uuid>.png`, then check the PNG signature and the
 *     injected byte-size ceiling — no pixel buffer allocated;
 *  2. [SpriteSheetDecoder.validateBounds] — bounds only, no pixel buffer allocated;
 *  3. full decode + trailing-transparent scan via [SpriteSheetDecoder.decode] — the only tier
 *     costly enough to need its own suspending call, so the UI can surface a loading state that
 *     covers exactly it, per `character-import`'s "slow validation shows a loading state"
 *     requirement.
 *
 * The source [Uri] is copied exactly once, inside [stage], and never read again afterward, per
 * `character-import`'s "The picked file is copied to app-private storage" requirement. The move
 * into `filesDir/characters/<uuid>/idle.png` — a folder, never a flat `<uuid>.png` — happens only
 * on explicit [confirm].
 */
class CharacterImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val decoder: SpriteSheetDecoder,
    private val bitmapDecoding: BitmapDecoding,
    @MaxSpriteDimensionPx private val maxDimensionPx: Int,
    private val config: CharacterLibraryConfig,
) {

    private val pngSignature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /** Tiers 1 and 2: copy, header signature, byte ceiling, then bounds — no pixel buffer allocated. */
    suspend fun stage(source: Uri): StageResult = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val cacheFile = stagingFile(uuid)

        val copied = copyToCache(source, cacheFile)
        if (!copied) {
            cacheFile.delete()
            return@withContext StageResult.Rejected(CharacterImportRejection.NotPng)
        }

        // Tier 1: header-only — PNG signature and byte-size ceiling, no pixel buffer allocated.
        val sizeBytes = cacheFile.length()
        if (sizeBytes > config.maxImportBytes) {
            cacheFile.delete()
            return@withContext StageResult.Rejected(
                CharacterImportRejection.TooLarge(actualBytes = sizeBytes, maxBytes = config.maxImportBytes),
            )
        }

        val headerBytes = cacheFile.readBytes()
        if (!hasPngSignature(headerBytes)) {
            cacheFile.delete()
            return@withContext StageResult.Rejected(CharacterImportRejection.NotPng)
        }

        // Tier 2: bounds only — no pixel buffer allocated.
        val gridResult = decoder.validateBounds(headerBytes)
        if (gridResult is SpriteGridResult.Invalid) {
            cacheFile.delete()
            val bounds = bitmapDecoding.decodeBounds(headerBytes)
            return@withContext StageResult.Rejected(
                gridResult.failure.toRejection(widthPx = bounds.widthPx, heightPx = bounds.heightPx),
            )
        }

        StageResult.Staged(StagedImport(uuid = uuid, cacheFile = cacheFile))
    }

    /** Tier 3: full decode + trailing-transparent scan. The only suspending, potentially slow tier. */
    suspend fun decodeAndScan(staged: StagedImport): CharacterImportResult = withContext(Dispatchers.IO) {
        val headerBytes = staged.cacheFile.readBytes()
        val decodeResult = decoder.decode(headerBytes)
        when (decodeResult) {
            is SpriteSheetResult.Failed -> {
                staged.cacheFile.delete()
                CharacterImportResult.Rejected(decodeResult.failure.toRejection())
            }
            is SpriteSheetResult.Loaded -> CharacterImportResult.Validated(
                ValidatedImport(uuid = staged.uuid, cacheFile = staged.cacheFile, decoded = decodeResult),
            )
        }
    }

    /** Runs [stage] then [decodeAndScan] in sequence — convenience for callers that need no
     * intermediate loading state (e.g. tests, or a caller happy to show one loading state for the
     * whole pipeline). UI code that must show a loading state scoped to tier 3 only should call
     * [stage] and [decodeAndScan] separately instead. */
    suspend fun import(source: Uri): CharacterImportResult {
        return when (val staged = stage(source)) {
            is StageResult.Rejected -> CharacterImportResult.Rejected(staged.reason)
            is StageResult.Staged -> decodeAndScan(staged.staged)
        }
    }

    /**
     * Cap check, then the finalize-on-confirm move from the cache into
     * `filesDir/characters/<uuid>/idle.png`. Never called except on explicit user confirm.
     */
    suspend fun confirm(
        import: ValidatedImport,
        currentImportedCount: Int,
    ): Result<CharacterId.Imported> = withContext(Dispatchers.IO) {
        if (currentImportedCount >= config.maxImportedCharacters) {
            import.cacheFile.delete()
            return@withContext Result.failure(
                CharacterImportRejectionException(CharacterImportRejection.CapReached(config.maxImportedCharacters)),
            )
        }

        val characterDir = File(File(context.filesDir, "characters"), import.uuid)
        characterDir.mkdirs()
        val destination = File(characterDir, "idle.png")

        val moved = try {
            import.cacheFile.copyTo(destination, overwrite = true)
            import.cacheFile.delete()
            true
        } catch (_: Exception) {
            false
        }

        if (!moved) {
            destination.delete()
            characterDir.delete()
            return@withContext Result.failure(IllegalStateException("Failed to move import ${import.uuid} into place"))
        }

        Result.success(CharacterId.Imported(import.uuid))
    }

    /** Abandons a validated-but-unconfirmed import, leaving nothing behind. */
    fun abandon(import: ValidatedImport) {
        import.cacheFile.delete()
    }

    private fun stagingDir(): File = File(context.cacheDir, "import").apply { mkdirs() }

    private fun stagingFile(uuid: String): File = File(stagingDir(), "$uuid.png")

    private fun copyToCache(source: Uri, destination: File): Boolean {
        val input = context.contentResolver.openInputStream(source) ?: return false
        return input.use { stream ->
            destination.outputStream().use { output -> stream.copyTo(output) }
            true
        }
    }

    private fun hasPngSignature(bytes: ByteArray): Boolean {
        if (bytes.size < pngSignature.size) return false
        for (i in pngSignature.indices) {
            if (bytes[i] != pngSignature[i]) return false
        }
        return true
    }

    private fun SpriteSheetFailure.toRejection(
        widthPx: Int = 0,
        heightPx: Int = 0,
    ): CharacterImportRejection = when (this) {
        SpriteSheetFailure.Oversized -> CharacterImportRejection.Oversized(
            widthPx = widthPx,
            heightPx = heightPx,
            maxPx = maxDimensionPx,
        )
        SpriteSheetFailure.NotDivisible -> CharacterImportRejection.NotDivisible(widthPx = widthPx, heightPx = heightPx)
        SpriteSheetFailure.Undecodable -> CharacterImportRejection.Undecodable
        SpriteSheetFailure.EmptySheet -> CharacterImportRejection.EmptySheet
    }
}

class CharacterImportRejectionException(val reason: CharacterImportRejection) : Exception()
