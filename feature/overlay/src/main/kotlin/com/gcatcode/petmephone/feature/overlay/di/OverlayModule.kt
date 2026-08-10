package com.gcatcode.petmephone.feature.overlay.di

import android.content.Context
import android.view.WindowManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

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

    @Provides
    fun provideWindowManager(@ApplicationContext context: Context): WindowManager =
        context.getSystemService(WindowManager::class.java)
}
