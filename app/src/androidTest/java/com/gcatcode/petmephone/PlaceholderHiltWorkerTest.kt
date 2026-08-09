package com.gcatcode.petmephone

import androidx.hilt.work.HiltWorkerFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.gcatcode.petmephone.worker.PlaceholderHiltWorker
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Drives [PlaceholderHiltWorker] end-to-end through `HiltWorkerFactory`, proving the DI graph
 * from `PetMePhoneApplication` down to a worker's constructor injection (task 3.12; spec
 * scenario "Placeholder worker executes and succeeds").
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlaceholderHiltWorkerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Before
    fun setUp() {
        hiltRule.inject()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun placeholderHiltWorker_executesSuccessfully_withInjectedDependency() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<PlaceholderHiltWorker>().build()

        workManager.enqueue(request).result.get()

        val workInfo = workManager.getWorkInfoById(request.id).get()!!
        assertEquals(WorkInfo.State.SUCCEEDED, workInfo.state)
        assertTrue(
            workInfo.outputData.getBoolean(PlaceholderHiltWorker.KEY_DEPENDENCY_INJECTED, false),
        )
    }
}
