package com.gcatcode.petmephone.feature.overlay.di

import android.content.Context
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.pet.state.DraggingStateProvider
import com.gcatcode.petmephone.core.domain.pet.state.IdleStateProvider
import com.gcatcode.petmephone.core.domain.pet.state.PetStateConfig
import com.gcatcode.petmephone.core.domain.pet.state.PetStateProvider
import com.gcatcode.petmephone.core.domain.pet.state.PetStateResolver
import com.gcatcode.petmephone.feature.overlay.input.ChoreographerFrameScheduler
import com.gcatcode.petmephone.feature.overlay.input.FrameScheduler
import com.gcatcode.petmephone.feature.overlay.input.SnapAnimator
import com.gcatcode.petmephone.feature.overlay.input.SpringSnapAnimator
import com.gcatcode.petmephone.feature.overlay.position.OverlayPositionConfig
import com.gcatcode.petmephone.feature.overlay.quickmenu.QuickMenuConfig
import com.gcatcode.petmephone.feature.overlay.sprite.BitmapDecoding
import com.gcatcode.petmephone.feature.overlay.sprite.MaxSpriteDimensionPx
import com.gcatcode.petmephone.feature.overlay.ui.PetAnimationConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * `@Provides`-only, per the dependency-injection spec — real construction logic (`WindowManager`
 * from the application context), not an interface-to-implementation mapping. Injecting this
 * rather than fetching it inline in the service is what makes `PetOverlayService` testable under
 * Robolectric (issue #13's test-case note).
 *
 * Not `@Singleton`: `WindowManager` obtained this way targets the default display for the
 * process' lifetime, so a fresh lookup per injection is cheap and avoids holding it longer than
 * needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object OverlayModule {

    /** Sheet header safety bound, per `design.md`: injected value, never a literal in the decoder. */
    private const val MAX_SPRITE_DIMENSION_PX = 2048

    /** Manual clock interval, per `design.md`: injected value, never a literal in the clock. */
    private const val IDLE_FRAME_INTERVAL_MILLIS = 150L

    /**
     * Floor for a manifest-declared cycle duration, at roughly one frame per display refresh at
     * 60 Hz. Below this a declaration buys no visible smoothness and only spins the clock.
     */
    private const val MIN_FRAME_INTERVAL_MILLIS = 16L

    /** `WhileSubscribed` timeout for `PetOverlayStateHolder`'s reactive `sheets`/`petState`. */
    private const val STATE_SHARING_TIMEOUT_MILLIS = 5_000L

    /** Minimum dwell time between resolved-state emissions, per `design.md` decision 2. */
    private const val PET_STATE_MINIMUM_DWELL_MILLIS = 400L

    /** Timeout for the first position read before `addView`, per `[POS-6]`. */
    private const val POSITION_FIRST_READ_TIMEOUT_MILLIS = 500L

    /** Hard cap on imported characters, per `character-import`'s cap requirement. */
    private const val MAX_IMPORTED_CHARACTERS = 10

    /** Byte-size ceiling checked at tier 1, before any pixel buffer is allocated. */
    private const val MAX_IMPORT_BYTES = 10L * 1024 * 1024

    /** [CharacterId.BuiltIn.name] used whenever no active pointer is stored or its target is
     *  deleted — must match one of [BuiltInCharacters.all]'s entries. */
    private const val BUILT_IN_FALLBACK_NAME = "default"

    // Quick-menu card sizing (design decision 11). No product reference yet — design.md's open
    // questions flag these for a maintainer's eye on real hardware; injected here so rebalancing
    // stays a value change, never a code hunt. Replaces PR 5's placeholder constants that lived in
    // PetOverlayService.
    private const val QUICK_MENU_CARD_WIDTH_DP = 280
    // A CEILING, not a size. The window wraps its content, so the card is as tall as it needs to be;
    // this only bounds how far it may grow before the content scrolls. Two earlier attempts shipped
    // a fixed height and both were too small - the first put the launch button outside the window
    // entirely, so the card looked like it had none and the bottom metric row absorbed its taps.
    private const val QUICK_MENU_MAX_CARD_HEIGHT_DP = 420
    private const val QUICK_MENU_GAP_DP = 8

    @Provides
    fun provideWindowManager(@ApplicationContext context: Context): WindowManager =
        context.getSystemService(WindowManager::class.java)

    @Provides
    fun providePetStateConfig(): PetStateConfig =
        PetStateConfig(minimumDwellMillis = PET_STATE_MINIMUM_DWELL_MILLIS)

    @Provides
    fun providePetStateResolver(
        providers: Set<@JvmSuppressWildcards PetStateProvider>,
        config: PetStateConfig,
    ): PetStateResolver = PetStateResolver(providers, config)

    @Provides
    @IntoSet
    fun provideDraggingStateProvider(): PetStateProvider = DraggingStateProvider()

    @Provides
    @IntoSet
    fun provideIdleStateProvider(): PetStateProvider = IdleStateProvider()

    @Provides
    @MaxSpriteDimensionPx
    fun provideMaxSpriteDimensionPx(): Int = MAX_SPRITE_DIMENSION_PX

    @Provides
    fun providePetAnimationConfig(): PetAnimationConfig =
        PetAnimationConfig(
            frameIntervalMillis = IDLE_FRAME_INTERVAL_MILLIS,
            minFrameIntervalMillis = MIN_FRAME_INTERVAL_MILLIS,
            stateSharingTimeoutMillis = STATE_SHARING_TIMEOUT_MILLIS,
        )

    @Provides
    fun provideOverlayPositionConfig(): OverlayPositionConfig =
        OverlayPositionConfig(firstReadTimeoutMillis = POSITION_FIRST_READ_TIMEOUT_MILLIS)

    @Provides
    fun provideCharacterLibraryConfig(): CharacterLibraryConfig =
        CharacterLibraryConfig(
            maxImportedCharacters = MAX_IMPORTED_CHARACTERS,
            maxImportBytes = MAX_IMPORT_BYTES,
            builtInFallbackName = BUILT_IN_FALLBACK_NAME,
        )

    @Provides
    @Singleton
    @com.gcatcode.petmephone.feature.overlay.di.OverlayApplicationScope
    fun provideOverlayApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideQuickMenuConfig(): QuickMenuConfig =
        QuickMenuConfig(
            cardWidthDp = QUICK_MENU_CARD_WIDTH_DP,
            maxCardHeightDp = QUICK_MENU_MAX_CARD_HEIGHT_DP,
            gapDp = QUICK_MENU_GAP_DP,
        )
}

/** `@Binds`, not `@Provides`: pure interface-to-implementation mapping. */
@Module
@InstallIn(SingletonComponent::class)
interface OverlaySpriteBindingsModule {
    @Binds
    fun bindBitmapDecoding(default: BitmapDecoding.Default): BitmapDecoding
}

/** `@Binds`, not `@Provides`: pure interface-to-implementation mapping, per the DI spec. */
@Module
@InstallIn(SingletonComponent::class)
interface OverlayInputBindingsModule {
    @Binds
    fun bindFrameScheduler(default: ChoreographerFrameScheduler): FrameScheduler

    @Binds
    fun bindSnapAnimator(default: SpringSnapAnimator): SnapAnimator
}
