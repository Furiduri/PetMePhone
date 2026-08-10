package com.gcatcode.petmephone.core.domain.pet.sprite

/**
 * Fixed row order of a pet sprite sheet, top to bottom. `ordinal` doubles as the row index used
 * for cell arithmetic (`SpriteLayout.cellTopPx`), so this order is a public contract: reordering
 * these entries changes which pixels every existing sheet asset is read from.
 *
 * Only [IDLE] is drawn by slice 1 (#36); the other five rows are modelled here because the sheet
 * format is defined for the full six-row grid even though this change renders one row.
 */
enum class PetSpriteRow {
    IDLE,
    DRAGGING,
    HUNGRY,
    HAPPY,
    SLEEPING,
    TYPING,
}
