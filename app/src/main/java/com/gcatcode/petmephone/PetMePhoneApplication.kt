package com.gcatcode.petmephone

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Single Hilt root and WorkManager configuration provider, per the dependency-injection spec.
 * `HiltWorkerFactory` is injected and handed to WorkManager's [Configuration] so that every
 * `@HiltWorker` is constructed through the Hilt graph instead of the default no-arg factory.
 */
@HiltAndroidApp
class PetMePhoneApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
