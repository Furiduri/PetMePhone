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

    /**
     * [cycleDurationMillis] is how long one full loop of this character's animation should take,
     * as declared in its manifest. `null` means the character declared nothing — the renderer then
     * falls back to the injected per-frame default, never to a fabricated speed.
     *
     * Declared per cycle rather than per frame because that is the unit the result is judged in: a
     * fixed per-frame interval makes a 12-frame sheet take twice as long as a 6-frame one, so the
     * same number reads as "right" on one character and "sluggish" on another. Adding frames to a
     * cycle-timed animation makes it smoother, not slower.
     */
    data class Ready(
        val byState: Map<PetState, SpriteSheetResult.Loaded>,
        val idle: SpriteSheetResult.Loaded,
        val cycleDurationMillis: Long? = null,
    ) : CharacterSheets

    data class Broken(val failure: SpriteSheetFailure) : CharacterSheets
}
