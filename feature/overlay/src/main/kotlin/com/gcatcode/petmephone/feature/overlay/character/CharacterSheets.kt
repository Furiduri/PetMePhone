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
     * [frameDurationMillis] is how long each frame of this character's animation is held, as
     * declared in its manifest. `null` means the character declared nothing — the renderer then
     * falls back to the injected default, never to a fabricated speed.
     *
     * Per frame, so the length of an animation follows from how many frames it has: a sheet with
     * more frames holds more movement and takes longer to play, rather than playing the same
     * movement faster. An earlier version declared the duration of a whole cycle instead, on the
     * assumption that extra frames were the same motion drawn more finely. They are not in this
     * project's art, and dividing a fixed cycle across them rushed the animation.
     */
    data class Ready(
        val byState: Map<PetState, SpriteSheetResult.Loaded>,
        val idle: SpriteSheetResult.Loaded,
        val frameDurationMillis: Long? = null,
    ) : CharacterSheets

    data class Broken(val failure: SpriteSheetFailure) : CharacterSheets
}
