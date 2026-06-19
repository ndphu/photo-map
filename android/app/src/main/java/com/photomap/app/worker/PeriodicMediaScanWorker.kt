package com.photomap.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.photomap.app.PhotoMapApplication
import kotlinx.coroutines.CancellationException

class PeriodicMediaScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val container = (appContext.applicationContext as PhotoMapApplication).container

    override suspend fun doWork(): Result {
        if (container.tokenStore.accessToken() == null) return Result.success()

        return try {
            container.mediaScanner.scan()
            container.syncRepository.enqueueSync()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "periodic-media-scan"
    }
}
