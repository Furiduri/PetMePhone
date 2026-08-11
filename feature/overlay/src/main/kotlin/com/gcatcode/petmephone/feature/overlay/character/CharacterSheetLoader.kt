package com.gcatcode.petmephone.feature.overlay.character

import android.content.Context
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGridDeclaration
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteSheetFailure
import com.gcatcode.petmephone.core.domain.pet.state.PetState
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Properties
import javax.inject.Inject

private const val MANIFEST_FILE_NAME = "manifest.properties"

/**
 * Loads one character's [CharacterSheets] from whichever folder backs its [CharacterId] — the
 * **only** place that ever branches on the id type is [assetSourceFor], to pick a
 * [CharacterAssetSource]. Every decoding decision after that point runs the identical code below
 * for a `BuiltIn` and an `Imported` character, satisfying the "one model, one render path"
 * requirement: the same declared grid, the same unmodified [SpriteSheetDecoder], and the same
 * absence handling either way.
 *
 * A missing optional animation file is an ordinary absence — simply not added to
 * [CharacterSheets.Ready.byState]. [CharacterSheets.Broken] is returned only when the grid cannot
 * be read at all, or when `idle.png` is absent or fails to decode — the "someone deleted a file
 * outside the app" case a bundled character can never hit (its assets are read-only) but an
 * imported one can.
 */
class CharacterSheetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val decoder: SpriteSheetDecoder,
) {

    fun load(id: CharacterId): CharacterSheets = load(assetSourceFor(id))

    /**
     * The shared decode path both [CharacterId] variants run through unchanged — public so a test
     * can drive it directly against a fake [CharacterAssetSource] and prove the id-based [load]
     * overload adds nothing but source selection.
     */
    fun load(source: CharacterAssetSource): CharacterSheets {
        val declaration = readDeclaration(source)
            ?: return CharacterSheets.Broken(SpriteSheetFailure.Undecodable)

        val idleBytes = source.open(fileNameFor(PetState.IDLE))?.use { it.readBytes() }
            ?: return CharacterSheets.Broken(SpriteSheetFailure.Undecodable)
        val idleResult = decoder.decode(idleBytes, declaration)
        val idle = when (idleResult) {
            is SpriteSheetResult.Failed -> return CharacterSheets.Broken(idleResult.failure)
            is SpriteSheetResult.Loaded -> idleResult
        }

        val byState = buildMap {
            put(PetState.IDLE, idle)
            for (state in PetState.entries) {
                if (state == PetState.IDLE) continue
                val bytes = source.open(fileNameFor(state))?.use { it.readBytes() } ?: continue
                val result = decoder.decode(bytes, declaration)
                if (result is SpriteSheetResult.Loaded) put(state, result)
            }
        }

        return CharacterSheets.Ready(byState = byState, idle = idle)
    }

    /** The one call site that branches on [CharacterId]'s type — everything past this point reads
     * exclusively through the returned [CharacterAssetSource]. */
    private fun assetSourceFor(id: CharacterId): CharacterAssetSource = when (id) {
        is CharacterId.BuiltIn -> CharacterAssetSource { fileName ->
            try {
                context.assets.open("pet/${id.name}/$fileName")
            } catch (_: IOException) {
                null
            }
        }
        is CharacterId.Imported -> CharacterAssetSource { fileName ->
            val file = File(File(File(context.filesDir, "characters"), id.uuid), fileName)
            if (file.exists()) file.inputStream() else null
        }
    }

    private fun readDeclaration(source: CharacterAssetSource): SpriteGridDeclaration? {
        val stream = source.open(MANIFEST_FILE_NAME) ?: return null
        return stream.use { input ->
            val properties = Properties()
            try {
                properties.load(input)
            } catch (_: IOException) {
                return null
            }
            val columns = properties.getProperty("columns")?.toIntOrNull()
            val rows = properties.getProperty("rows")?.toIntOrNull()
            if (columns == null || rows == null || columns <= 0 || rows <= 0) {
                null
            } else {
                SpriteGridDeclaration(columns = columns, rows = rows)
            }
        }
    }

    private fun fileNameFor(state: PetState): String = "${state.name.lowercase()}.png"
}
