package com.gcatcode.petmephone.feature.overlay.character

import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteSheetFailure
import com.gcatcode.petmephone.core.domain.pet.state.PetState
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetResult

/**
 * The decoded state of one character's sheets, as projected by [CharacterSheetLoader]. Mirrors
 * [SpriteSheetResult]'s "no fabricated zero" discipline at the character level: every state that
 * has no bound animation is simply absent from [Ready.byState], never a placeholder frame.
 */
sealed interface CharacterSheets {
    data object Loading : CharacterSheets

    data class Ready(
        val byState: Map<PetState, SpriteSheetResult.Loaded>,
        val idle: SpriteSheetResult.Loaded,
    ) : CharacterSheets

    data class Broken(val failure: SpriteSheetFailure) : CharacterSheets
}
