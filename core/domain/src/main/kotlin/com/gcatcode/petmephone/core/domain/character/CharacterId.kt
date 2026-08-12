package com.gcatcode.petmephone.core.domain.character

/**
 * Identifies a pet character. [BuiltIn] names a compile-time bundled asset (never counted against
 * the import cap, never deletable). [Imported] names a user-imported character by the UUID that is
 * also its `filesDir/characters/<uuid>/` folder name.
 */
sealed interface CharacterId {
    data class BuiltIn(val name: String) : CharacterId
    data class Imported(val uuid: String) : CharacterId
}
