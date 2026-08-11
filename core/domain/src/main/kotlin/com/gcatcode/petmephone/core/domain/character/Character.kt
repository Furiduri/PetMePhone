package com.gcatcode.petmephone.core.domain.character

/**
 * A character available in the library: its identity plus display metadata. [name] is `null` when
 * no name has ever been supplied — never a fabricated placeholder string standing in for one.
 */
data class Character(
    val id: CharacterId,
    val name: CharacterName?,
)
