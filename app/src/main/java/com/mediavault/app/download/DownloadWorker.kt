package com.mediavault.app.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong("TASK_ID", -1L)
        if (taskId != -1L) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_START
                putExtra("TASK_ID", taskId)
            }
            ContextCompat.startForegroundService(context, intent)
        }
        return Result.success()
    }

    companion object {
        fun enqueueDownload(context: Context, taskId: Long, wifiOnly: Boolean = false) {
            val constraintsBuilder = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)

            val inputData = Data.Builder()
                .putLong("TASK_ID", taskId)
                .build()

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraintsBuilder.build())
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
