package com.gcatcode.petmephone.feature.overlay.character

import android.content.Context
import android.net.Uri
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterImportRejection
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGridDeclaration
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

/**
 * A tier-1/tier-2 pass staged in `cacheDir/import/<uuid>.png`, not yet full-decoded. [candidate] is
 * a best-effort detected grid guess — `rows = 1`, `columns = widthPx / heightPx` — for the preview
 * screen to pre-fill; it is never itself validated. The grid is declared, not inferred: only the
 * user's confirmed (or corrected) declaration, passed to [CharacterImporter.decodeAndScan], is ever
 * checked against the pixels.
 */
data class StagedImport(
    val uuid: String,
    val cacheFile: File,
    val widthPx: Int,
    val heightPx: Int,
    val candidate: SpriteGridDeclaration,
)

/** Result of [CharacterImporter.stage]: tier 1 (header) and the oversize bound, no pixel buffer allocated. */
sealed interface StageResult {
    data class Staged(val staged: StagedImport) : StageResult
    data class Rejected(val reason: CharacterImportRejection) : StageResult
}

/**
 * Result of a passed import: the cached bytes are staged and validated against a declared grid, but
 * not yet moved into the library. [CharacterImporter.confirm] performs the move; abandoning this
 * result (never calling it) leaves the cache file for the OS to reclaim and nothing under
 * `filesDir/characters/`.
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
 * Picking-to-library import pipeline. Runs in order, stopping at the first failure
 * (design.md "Import pipeline" table, updated for decision 13's declared grid):
 *  1. copy the picked [Uri] to `cacheDir/import/<uuid>.png`, then check the PNG signature, the
 *     injected byte-size ceiling, and the oversize bound — no pixel buffer allocated, no grid
 *     declaration needed yet;
 *  2. [SpriteSheetDecoder.validateBounds] against the caller-supplied declaration — bounds only, no
 *     pixel buffer allocated;
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

    /** Tier 1 + the oversize bound: copy, header signature, byte ceiling, dimension check — no
     * grid declaration exists yet, so only the axis-independent oversize rule can be checked here. */
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

        val bounds = bitmapDecoding.decodeBounds(headerBytes)
        if (bounds.widthPx <= 0 || bounds.heightPx <= 0) {
            cacheFile.delete()
            return@withContext StageResult.Rejected(CharacterImportRejection.Undecodable)
        }
        if (bounds.widthPx > maxDimensionPx || bounds.heightPx > maxDimensionPx) {
            cacheFile.delete()
            return@withContext StageResult.Rejected(
                CharacterImportRejection.Oversized(
                    widthPx = bounds.widthPx,
                    heightPx = bounds.heightPx,
                    maxPx = maxDimensionPx,
                ),
            )
        }

        // A best-effort candidate for the preview screen to pre-fill — never itself validated.
        // The grid is declared, not inferred: only decodeAndScan's caller-supplied declaration is
        // ever checked against the pixels.
        val candidate = SpriteGridDeclaration(
            columns = if (bounds.heightPx > 0) bounds.widthPx / bounds.heightPx else 1,
            rows = 1,
        )

        StageResult.Staged(
            StagedImport(
                uuid = uuid,
                cacheFile = cacheFile,
                widthPx = bounds.widthPx,
                heightPx = bounds.heightPx,
                candidate = candidate,
            ),
        )
    }

    /** Tier 2 + tier 3: validates [declaration] against the pixels, then full decode + scan. The
     * only suspending, potentially slow call. */
    suspend fun decodeAndScan(
        staged: StagedImport,
        declaration: SpriteGridDeclaration,
    ): CharacterImportResult = withContext(Dispatchers.IO) {
        val headerBytes = staged.cacheFile.readBytes()

        val gridResult = decoder.validateBounds(headerBytes, declaration)
        if (gridResult is SpriteGridResult.Invalid) {
            staged.cacheFile.delete()
            return@withContext CharacterImportResult.Rejected(
                gridResult.failure.toRejection(widthPx = staged.widthPx, heightPx = staged.heightPx),
            )
        }

        val decodeResult = decoder.decode(headerBytes, declaration)
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

    /** Runs [stage] then [decodeAndScan] against the staged file's detected candidate — convenience
     * for callers that need no intermediate grid-entry UI (e.g. tests, or a caller happy to trust
     * the candidate without offering a correction step). UI code that must let the user confirm or
     * correct the declared grid should call [stage] and [decodeAndScan] separately instead. */
    suspend fun import(source: Uri): CharacterImportResult {
        return when (val staged = stage(source)) {
            is StageResult.Rejected -> CharacterImportResult.Rejected(staged.reason)
            is StageResult.Staged -> decodeAndScan(staged.staged, staged.staged.candidate)
        }
    }

    /**
     * Cap check, then the finalize-on-confirm move from the cache into
     * `filesDir/characters/<uuid>/idle.png`. Never called except on explicit user confirm. The
     * caller is responsible for building the resulting `Character` (with the captured name, if any)
     * and calling `CharacterRepository.add` — this class only owns the file move.
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
