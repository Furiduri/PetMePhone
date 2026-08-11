package com.gcatcode.petmephone.core.domain.character

/** A character available in the library: its identity plus display metadata. */
data class Character(
    val id: CharacterId,
    val displayName: String,
)
