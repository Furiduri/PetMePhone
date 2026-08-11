package com.gcatcode.petmephone.feature.overlay.character

import com.gcatcode.petmephone.core.domain.character.CharacterImportRejection
import com.gcatcode.petmephone.feature.overlay.R

/** Maps every [CharacterImportRejection] case to its string-resource template and format args. */
fun CharacterImportRejection.messageResource(): Pair<Int, List<Any>> = when (this) {
    CharacterImportRejection.NotPng ->
        R.string.feature_overlay_import_rejection_not_png to emptyList()
    is CharacterImportRejection.TooLarge ->
        R.string.feature_overlay_import_rejection_too_large to listOf(actualBytes, maxBytes)
    is CharacterImportRejection.Oversized ->
        R.string.feature_overlay_import_rejection_oversized to listOf(widthPx, heightPx, maxPx)
    is CharacterImportRejection.NotDivisible ->
        R.string.feature_overlay_import_rejection_not_divisible to listOf(widthPx, heightPx)
    CharacterImportRejection.Undecodable ->
        R.string.feature_overlay_import_rejection_undecodable to emptyList()
    CharacterImportRejection.EmptySheet ->
        R.string.feature_overlay_import_rejection_empty_sheet to emptyList()
    is CharacterImportRejection.CapReached ->
        R.string.feature_overlay_import_rejection_cap_reached to listOf(cap)
}
