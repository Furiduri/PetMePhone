package com.gcatcode.petmephone.core.data.di

import com.gcatcode.petmephone.core.data.character.ActiveCharacterRepositoryImpl
import com.gcatcode.petmephone.core.data.character.CharacterRepositoryImpl
import com.gcatcode.petmephone.core.data.overlay.DragStateRepositoryImpl
import com.gcatcode.petmephone.core.data.overlay.OverlayPositionRepositoryImpl
import com.gcatcode.petmephone.core.data.permission.OverlayOnboardingRepositoryImpl
import com.gcatcode.petmephone.core.data.permission.OverlayPermissionCheckerImpl
import com.gcatcode.petmephone.core.data.repository.PetProfileRepositoryImpl
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import com.gcatcode.petmephone.core.domain.overlay.DragStateRepository
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import com.gcatcode.petmephone.core.domain.repository.PetProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Interface-to-implementation bindings only, per the dependency-injection spec — `@Binds`, never
 * `@Provides`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindPetProfileRepository(impl: PetProfileRepositoryImpl): PetProfileRepository

    @Binds
    abstract fun bindOverlayPermissionChecker(impl: OverlayPermissionCheckerImpl): OverlayPermissionChecker

    @Binds
    abstract fun bindOverlayOnboardingRepository(impl: OverlayOnboardingRepositoryImpl): OverlayOnboardingRepository

    @Binds
    abstract fun bindOverlayPositionRepository(impl: OverlayPositionRepositoryImpl): OverlayPositionRepository

    @Binds
    abstract fun bindDragStateRepository(impl: DragStateRepositoryImpl): DragStateRepository

    @Binds
    abstract fun bindCharacterRepository(impl: CharacterRepositoryImpl): CharacterRepository

    @Binds
    abstract fun bindActiveCharacterRepository(impl: ActiveCharacterRepositoryImpl): ActiveCharacterRepository
}
