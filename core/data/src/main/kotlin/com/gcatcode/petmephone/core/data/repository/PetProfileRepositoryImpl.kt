package com.gcatcode.petmephone.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.gcatcode.petmephone.core.domain.repository.PetProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PetProfileRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PetProfileRepository {

    override val isOnboarded: Flow<Boolean>
        get() = dataStore.data.map { preferences -> preferences[ONBOARDED_KEY] ?: false }

    override suspend fun setOnboarded(onboarded: Boolean) {
        dataStore.edit { preferences -> preferences[ONBOARDED_KEY] = onboarded }
    }

    private companion object {
        val ONBOARDED_KEY = booleanPreferencesKey("onboarded")
    }
}
