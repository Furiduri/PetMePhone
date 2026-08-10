package com.gcatcode.petmephone.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for the (as yet minimal) pet profile onboarding state. Lives in
 * `:core:domain` per the dependency-injection spec: only `javax.inject` types may accompany it,
 * never `dagger.*`. The implementation and its Hilt bindings live in `:core:data`.
 */
interface PetProfileRepository {
    val isOnboarded: Flow<Boolean>

    suspend fun setOnboarded(onboarded: Boolean)
}
