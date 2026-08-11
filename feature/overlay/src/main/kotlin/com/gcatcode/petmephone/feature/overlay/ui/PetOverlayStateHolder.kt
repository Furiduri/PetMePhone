package com.gcatcode.petmephone.feature.overlay.ui

import android.content.Context
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteSheetFailure
import com.gcatcode.petmephone.feature.overlay.character.BuiltInCharacterManifestReader
import com.gcatcode.petmephone.feature.overlay.character.CharacterManifestResult
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetResult
import com.gcatcode.petmephone.feature.overlay.system.ScreenStateMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

// One folder per character, fixed animation filenames inside: `pet/<character>/idle.png`. Only
// the `default` character and the `idle` animation exist today — state resolution across other
// animations (#37) is slice 2, so nothing here loads on demand or falls back yet.
private const val DEFAULT_CHARACTER_NAME = "default"
private const val IDLE_SHEET_ASSET_PATH = "pet/default/idle.png"

/**
 * `@Inject`ed, never `hiltViewModel()` — the overlay tree has no `ViewModelStoreOwner`
 * (`ComposeOverlayHost`'s kdoc). Decodes the bundled IDLE sheet exactly once and exposes it
 * alongside the animation config and the screen-on signal.
 *
 * The `default` character's grid is read from its manifest (design decision 13), not inferred.
 * This class is superseded by `CharacterSheetLoader` in the next PR, which will propagate a
 * [com.gcatcode.petmephone.core.domain.character.CharacterManifestFailure] as its own typed
 * result; until then, a manifest failure here collapses to [SpriteSheetFailure.Undecodable] — a
 * documented, deliberately narrow deviation, since this class's decode-lifecycle rework is out of
 * scope for this PR.
 */
@Singleton
class PetOverlayStateHolder @Inject constructor(
    @ApplicationContext context: Context,
    decoder: SpriteSheetDecoder,
    manifestReader: BuiltInCharacterManifestReader,
    val config: PetAnimationConfig,
    screenStateMonitor: ScreenStateMonitor,
    positionRepository: OverlayPositionRepository,
) {
    val sheetResult: SpriteSheetResult = when (val manifest = manifestReader.read(DEFAULT_CHARACTER_NAME)) {
        is CharacterManifestResult.Failed -> SpriteSheetResult.Failed(SpriteSheetFailure.Undecodable)
        is CharacterManifestResult.Found -> context.assets.open(IDLE_SHEET_ASSET_PATH).use { input ->
            decoder.decode(input.readBytes(), manifest.declaration)
        }
    }

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
