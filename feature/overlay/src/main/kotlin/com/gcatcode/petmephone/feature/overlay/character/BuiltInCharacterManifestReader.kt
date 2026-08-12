package com.gcatcode.petmephone.feature.overlay.character

import android.content.Context
import com.gcatcode.petmephone.core.domain.character.CharacterManifestFailure
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGridDeclaration
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Properties
import javax.inject.Inject

/** Result of [BuiltInCharacterManifestReader.read]. */
sealed interface CharacterManifestResult {
    data class Found(val declaration: SpriteGridDeclaration) : CharacterManifestResult
    data class Failed(val failure: CharacterManifestFailure) : CharacterManifestResult
}

/**
 * Reads a bundled character's declared grid from `assets/pet/<name>/manifest.properties`
 * (`columns`/`rows` keys). A missing file or missing/non-positive keys is a typed
 * [CharacterManifestFailure], never a guessed grid (`design.md` decision 13).
 */
class BuiltInCharacterManifestReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun read(characterName: String): CharacterManifestResult {
        val path = "pet/$characterName/manifest.properties"
        val stream = try {
            context.assets.open(path)
        } catch (_: IOException) {
            return CharacterManifestResult.Failed(CharacterManifestFailure.Missing)
        }

        return stream.use { input ->
            val properties = Properties()
            try {
                properties.load(input)
            } catch (e: IOException) {
                return CharacterManifestResult.Failed(
                    CharacterManifestFailure.Malformed(e.message ?: "manifest could not be read"),
                )
            }

            val columns = properties.getProperty("columns")?.toIntOrNull()
            val rows = properties.getProperty("rows")?.toIntOrNull()
            if (columns == null || rows == null || columns <= 0 || rows <= 0) {
                CharacterManifestResult.Failed(
                    CharacterManifestFailure.Malformed("columns/rows missing or non-positive"),
                )
            } else {
                CharacterManifestResult.Found(SpriteGridDeclaration(columns = columns, rows = rows))
            }
        }
    }
}
