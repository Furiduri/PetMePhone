package com.gcatcode.petmephone.core.data.di

import com.gcatcode.petmephone.core.data.repository.PetProfileRepositoryImpl
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
}
