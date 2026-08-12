package com.gcatcode.petmephone.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.gcatcode.petmephone.core.data.local.AppDatabase
import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * `@Provides`-only bindings, per the dependency-injection spec: real construction logic (the Room
 * database instance, the `DataStore<Preferences>` instance), not interface-to-implementation
 * mappings — those live in [BindingsModule] as `@Binds`.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "petmephone.db")
            // Pre-release only. Tracked for removal before first public release in issue #74
            // (task 2.16, task-persistence spec) — the app is pre-release, so a schema revert
            // destroys task rows rather than corrupting them (design.md "Migration / rollout").
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideSystemClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("petmephone_prefs")
        }

    /**
     * Real construction logic per the dependency-injection spec: `BalanceConfig`'s own defaults
     * ARE the current balance revision. A future DataStore-backed override changes only this
     * function's body, no call site (`balance-configuration` spec).
     */
    @Provides
    fun provideBalanceConfig(): BalanceConfig = BalanceConfig()
}
