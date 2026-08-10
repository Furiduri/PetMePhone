package com.gcatcode.petmephone.feature.overlay.di

import android.content.Context
import android.view.WindowManager
import com.gcatcode.petmephone.feature.overlay.sprite.BitmapDecoding
import com.gcatcode.petmephone.feature.overlay.sprite.MaxSpriteDimensionPx
import com.gcatcode.petmephone.feature.overlay.ui.PetAnimationConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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

    @Provides
    fun provideWindowManager(@ApplicationContext context: Context): WindowManager =
        context.getSystemService(WindowManager::class.java)

    @Provides
    @MaxSpriteDimensionPx
    fun provideMaxSpriteDimensionPx(): Int = MAX_SPRITE_DIMENSION_PX

    @Provides
    fun providePetAnimationConfig(): PetAnimationConfig =
        PetAnimationConfig(frameIntervalMillis = IDLE_FRAME_INTERVAL_MILLIS)

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
