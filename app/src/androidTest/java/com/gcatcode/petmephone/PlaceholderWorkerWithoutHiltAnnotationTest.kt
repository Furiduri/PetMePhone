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
import com.gcatcode.petmephone.worker.PlaceholderWorkerWithoutHiltAnnotation
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Companion of [PlaceholderHiltWorkerTest] proving the negative case (task 3.13; spec scenario
 * "Removing @HiltWorker fails at execution, not compile time"): the module compiles, but without
 * `@HiltWorker`, `HiltWorkerFactory` never learns about the class, so WorkManager's default
 * factory takes over and fails at execution time — never `SUCCEEDED`.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlaceholderWorkerWithoutHiltAnnotationTest {

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
    fun workerWithoutHiltAnnotation_doesNotSucceed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<PlaceholderWorkerWithoutHiltAnnotation>().build()

        workManager.enqueue(request).result.get()

        val workInfo = workManager.getWorkInfoById(request.id).get()!!
        assertNotEquals(WorkInfo.State.SUCCEEDED, workInfo.state)
    }
}
