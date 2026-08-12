package com.gcatcode.petmephone.core.domain.pet.state

/**
 * The pet's resolved visible state. Names map directly to the animation filenames the sprite
 * loader looks for (`pet-sprite-sheet`'s one-file-per-animation contract): e.g. `IDLE` maps to
 * `idle.png`.
 *
 * Only [DRAGGING] and [IDLE] have a registered provider in this slice. [TYPING], [HAPPY],
 * [SLEEPING] and [HUNGRY] exist so the enum shape and downstream file-name mapping are stable
 * ahead of the providers that will produce them in slices 3-5.
 */
enum class PetState { IDLE, DRAGGING, TYPING, HAPPY, SLEEPING, HUNGRY }
