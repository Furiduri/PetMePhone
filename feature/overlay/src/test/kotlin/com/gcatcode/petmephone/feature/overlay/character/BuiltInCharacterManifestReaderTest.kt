package com.gcatcode.petmephone.feature.overlay.character

import androidx.test.core.app.ApplicationProvider
import com.gcatcode.petmephone.core.domain.character.CharacterManifestFailure
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGridDeclaration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric 4.16.1 ships no SDK 37 shadows; `@Config(sdk = [36])` is the repo convention. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BuiltInCharacterManifestReaderTest {

    private val reader = BuiltInCharacterManifestReader(ApplicationProvider.getApplicationContext())

    @Test
    fun `the shipped default manifest declares its real grid`() {
        val result = reader.read("default")

        assertEquals(
            CharacterManifestResult.Found(SpriteGridDeclaration(columns = 6, rows = 1)),
            result,
        )
    }

    @Test
    fun `a character with no manifest folder is a typed Missing failure`() {
        val result = reader.read("no-such-character")

        assertEquals(CharacterManifestResult.Failed(CharacterManifestFailure.Missing), result)
    }
}
