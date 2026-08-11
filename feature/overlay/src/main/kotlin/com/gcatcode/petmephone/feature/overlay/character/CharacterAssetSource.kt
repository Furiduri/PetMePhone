package com.gcatcode.petmephone.feature.overlay.character

import java.io.InputStream

/**
 * Opens a named file inside one character's folder — `assets/pet/<name>/` for a built-in
 * character, `filesDir/characters/<uuid>/` for an imported one. A `null` return models an absent
 * file (an optional animation that was never bound, or a manifest that was never written); it is
 * never an exception, so [CharacterSheetLoader] can treat every lookup the same way regardless of
 * which folder backs it.
 */
fun interface CharacterAssetSource {
    fun open(animationFileName: String): InputStream?
}
