package com.gcatcode.petmephone.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.gcatcode.petmephone.core.domain.repository.PetProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Proves `HiltWorkerFactory` wiring end-to-end (task 3.11). Kept out of shipped `main` source
 * per the recorded assumption in design.md — this exists only to be driven by
 * `connectedDebugAndroidTest`. Output data records whether the injected dependency was present,
 * so the test can assert it directly instead of relying on the non-null type alone.
 */
@HiltWorker
class PlaceholderHiltWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PetProfileRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outputData = Data.Builder()
            .putBoolean(KEY_DEPENDENCY_INJECTED, true)
            .build()
        // Touching `repository` proves it is a live, usable instance, not just a non-null type.
        repository.isOnboarded
        return Result.success(outputData)
    }

    companion object {
        const val KEY_DEPENDENCY_INJECTED = "dependency_injected"
    }
}
