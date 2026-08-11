package com.gcatcode.petmephone.feature.overlay.character

import com.gcatcode.petmephone.core.domain.character.Character
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterName

/**
 * The fixed compile-time list of bundled characters (design.md: "Built-in characters are a fixed
 * compile-time list, never counted against the cap"). Each entry's [CharacterId.BuiltIn.name]
 * names its folder under `assets/pet/`, where its `manifest.properties` declares its grid (design
 * decision 13).
 */
object BuiltInCharacters {
    val all: List<Character> = listOf(
        Character(id = CharacterId.BuiltIn("default"), name = CharacterName("Default")),
    )
}
