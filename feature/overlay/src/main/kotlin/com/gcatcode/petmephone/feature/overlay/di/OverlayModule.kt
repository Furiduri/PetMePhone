package com.gcatcode.petmephone.feature.overlay.di

import android.content.Context
import android.view.WindowManager
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

    /** Minimum dwell time between resolved-state emissions, per `design.md` decision 2. */
    private const val PET_STATE_MINIMUM_DWELL_MILLIS = 400L

    /** Timeout for the first position read before `addView`, per `[POS-6]`. */
    private const val POSITION_FIRST_READ_TIMEOUT_MILLIS = 500L

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
        PetAnimationConfig(frameIntervalMillis = IDLE_FRAME_INTERVAL_MILLIS)

    @Provides
    fun provideOverlayPositionConfig(): OverlayPositionConfig =
        OverlayPositionConfig(firstReadTimeoutMillis = POSITION_FIRST_READ_TIMEOUT_MILLIS)

    @Provides
    @Singleton
    @com.gcatcode.petmephone.feature.overlay.di.OverlayApplicationScope
    fun provideOverlayApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
