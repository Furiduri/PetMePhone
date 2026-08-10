package com.gcatcode.petmephone.feature.overlay.ui

import android.content.Context
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetResult
import com.gcatcode.petmephone.feature.overlay.system.ScreenStateMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

// One folder per character, fixed animation filenames inside: `pet/<character>/idle.png`. Only
// the `default` character and the `idle` animation exist today — state resolution across other
// animations (#37) is slice 2, so nothing here loads on demand or falls back yet.
private const val IDLE_SHEET_ASSET_PATH = "pet/default/idle.png"

/**
 * `@Inject`ed, never `hiltViewModel()` — the overlay tree has no `ViewModelStoreOwner`
 * (`ComposeOverlayHost`'s kdoc). Decodes the bundled IDLE sheet exactly once and exposes it
 * alongside the animation config and the screen-on signal.
 */
@Singleton
class PetOverlayStateHolder @Inject constructor(
    @ApplicationContext context: Context,
    decoder: SpriteSheetDecoder,
    val config: PetAnimationConfig,
    screenStateMonitor: ScreenStateMonitor,
) {
    val sheetResult: SpriteSheetResult =
        context.assets.open(IDLE_SHEET_ASSET_PATH).use { input -> decoder.decode(input.readBytes()) }

    val screenOn: StateFlow<Boolean> = screenStateMonitor.screenOn
}
