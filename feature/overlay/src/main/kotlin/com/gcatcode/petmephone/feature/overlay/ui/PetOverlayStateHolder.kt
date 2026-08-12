package com.gcatcode.petmephone.feature.overlay.ui

import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.overlay.DragStateRepository
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.core.domain.pet.state.PetSnapshot
import com.gcatcode.petmephone.core.domain.pet.state.PetState
import com.gcatcode.petmephone.core.domain.pet.state.PetStateResolver
import com.gcatcode.petmephone.feature.overlay.character.CharacterSheetLoader
import com.gcatcode.petmephone.feature.overlay.character.CharacterSheets
import com.gcatcode.petmephone.feature.overlay.di.OverlayApplicationScope
import com.gcatcode.petmephone.feature.overlay.system.ScreenStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `@Inject`ed, never `hiltViewModel()` — the overlay tree has no `ViewModelStoreOwner`
 * (`ComposeOverlayHost`'s kdoc).
 *
 * A pure projection of the active character and the resolved pet state, never a decode-at-
 * construction snapshot (superseded from the previous, single-character-only shape). [sheets]
 * re-derives from [ActiveCharacterRepository.active] on every switch; `mapLatest` cancels a
 * still-running decode the instant a faster switch supersedes it, so a stale character never wins
 * a race against a newer one (`[RENDER-3]`).
 */
@Singleton
class PetOverlayStateHolder @Inject constructor(
    activeCharacterRepository: ActiveCharacterRepository,
    sheetLoader: CharacterSheetLoader,
    dragStateRepository: DragStateRepository,
    stateResolver: PetStateResolver,
    screenStateMonitor: ScreenStateMonitor,
    positionRepository: OverlayPositionRepository,
    val config: PetAnimationConfig,
    @OverlayApplicationScope scope: CoroutineScope,
) {
    /**
     * `Loading` until the first character finishes decoding; `Ready`/`Broken` thereafter. Decoding
     * runs on [Dispatchers.IO], matching every other file-touching call in this module.
     */
    val sheets: StateFlow<CharacterSheets> = activeCharacterRepository.active
        .mapLatest { id -> withContext(Dispatchers.IO) { sheetLoader.load(id) } }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(config.stateSharingTimeoutMillis),
            initialValue = CharacterSheets.Loading,
        )

    /** Resolved via `pet-state-resolution`'s dwell-and-priority pipeline, seeded [PetState.IDLE]
     * — the same fallback [PetStateResolver.resolve] itself uses when nothing else matches. */
    val petState: StateFlow<PetState> = stateResolver
        .states(dragStateRepository.isDragging.map { isDragging -> PetSnapshot(isDragging) })
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(config.stateSharingTimeoutMillis),
            initialValue = PetState.IDLE,
        )

    val screenOn: StateFlow<Boolean> = screenStateMonitor.screenOn

    /**
     * Signals the pet acknowledges with a one-second glow behind itself.
     *
     * A recovered position is a [PetFeedback.WARNING], not an [PetFeedback.ERROR]: the pet
     * adjusted its own stored coordinate and still placed itself sensibly. Nothing the user asked
     * for failed, and colouring it red would blame them for the app's housekeeping.
     */
    val feedback: Flow<PetFeedback> =
        positionRepository.normalizations.map { PetFeedback.WARNING }
}
