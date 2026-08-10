package com.gcatcode.petmephone.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gcatcode.petmephone.core.domain.repository.PetProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Companion of [PlaceholderHiltWorker] with `@HiltWorker` deliberately removed (task 3.13).
 * Compiles cleanly — `@AssistedInject` alone still generates a factory — but `HiltWorkerFactory`
 * never learns about it, since Hilt only registers `@HiltWorker`-annotated classes in its
 * generated `WorkerFactory` delegate map. Enqueuing it therefore falls through to WorkManager's
 * default reflective factory, which cannot find a `(Context, WorkerParameters)` constructor and
 * fails at execution time.
 */
class PlaceholderWorkerWithoutHiltAnnotation @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    val repository: PetProfileRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.success()
}
