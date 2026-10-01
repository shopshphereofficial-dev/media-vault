package com.mediavault.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mediavault.app.R
import com.mediavault.app.data.db.AppDatabase
import com.mediavault.app.data.model.DownloadStatus
import com.mediavault.app.data.model.MediaItem
import com.mediavault.app.data.model.MediaType
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val okHttpClient = OkHttpClient()
    private val notificationChannelId = "media_downloads"
    private val notificationId = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, buildNotification("Ready", 0, 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskId = intent?.getLongExtra("TASK_ID", -1L) ?: -1L

        when (action) {
            ACTION_START -> if (taskId != -1L) startDownload(taskId)
            ACTION_PAUSE -> if (taskId != -1L) pauseDownload(taskId)
            ACTION_CANCEL -> if (taskId != -1L) cancelDownload(taskId)
        }
        return START_NOT_STICKY
    }

    private fun startDownload(taskId: Long) {
        if (activeJobs.containsKey(taskId)) return

        val job = serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val task = db.downloadDao().getDownloadById(taskId) ?: return@launch

            db.downloadDao().updateStatus(taskId, DownloadStatus.DOWNLOADING)

            val downloadDir = getExternalFilesDir(null) ?: filesDir
            val safeName = FileSecurityUtil.sanitizeFileName(task.fileName)
            val outputFile = File(downloadDir, safeName)

            var downloadedSoFar = if (outputFile.exists()) outputFile.length() else 0L

            try {
                val requestBuilder = Request.Builder().url(task.url)
                if (downloadedSoFar > 0L) {
                    requestBuilder.header("Range", "bytes=$downloadedSoFar-")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                val body = response.body
                val totalLength = (body?.contentLength() ?: 0L) + downloadedSoFar

                val fos = FileOutputStream(outputFile, downloadedSoFar > 0L)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastUpdate = System.currentTimeMillis()
                var bytesSinceUpdate = 0L

                body?.byteStream()?.use { input ->
                    fos.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) {
                                break
                            }
                            output.write(buffer, 0, bytesRead)
                            downloadedSoFar += bytesRead
                            bytesSinceUpdate += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 1000) {
                                val speed = bytesSinceUpdate * 1000 / (now - lastUpdate)
                                val remaining = if (totalLength > downloadedSoFar) totalLength - downloadedSoFar else 0L
                                val eta = if (speed > 0) remaining / speed else 0L

                                db.downloadDao().updateProgress(taskId, downloadedSoFar, totalLength, speed, eta)
                                updateNotification(task.fileName, downloadedSoFar, totalLength)

                                lastUpdate = now
                                bytesSinceUpdate = 0L
                            }
                        }
                    }
                }

                if (isActive) {
                    db.downloadDao().updateStatus(taskId, DownloadStatus.COMPLETED)
                    val mimeType = when (task.mediaType) {
                        MediaType.VIDEO -> "video/mp4"
                        MediaType.AUDIO -> "audio/mpeg"
                        MediaType.IMAGE -> "image/jpeg"
                        MediaType.DOCUMENT -> "application/octet-stream"
                    }
                    db.mediaDao().insertMedia(
                        MediaItem(
                            title = safeName,
                            filePath = outputFile.absolutePath,
                            mediaType = task.mediaType,
                            mimeType = mimeType,
                            sizeBytes = outputFile.length(),
                            sourceUrl = task.url
                        )
                    )
                }
            } catch (e: Exception) {
                db.downloadDao().updateStatus(taskId, DownloadStatus.FAILED)
            } finally {
                activeJobs.remove(taskId)
                if (activeJobs.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
        activeJobs[taskId] = job
    }

    private fun pauseDownload(taskId: Long) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        serviceScope.launch {
            AppDatabase.getInstance(applicationContext).downloadDao().updateStatus(taskId, DownloadStatus.PAUSED)
        }
    }

    private fun cancelDownload(taskId: Long) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val task = db.downloadDao().getDownloadById(taskId)
            if (task != null) {
                db.downloadDao().deleteDownload(task)
                val downloadDir = getExternalFilesDir(null) ?: filesDir
                File(downloadDir, task.fileName).delete()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, current: Long, total: Long): android.app.Notification {
        val progress = if (total > 0) ((current * 100) / total).toInt() else 0
        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("MediaVault Downloader")
            .setContentText("$title ($progress%)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, total <= 0)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, current: Long, total: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, buildNotification(title, current, total))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "com.mediavault.START_DOWNLOAD"
        const val ACTION_PAUSE = "com.mediavault.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "com.mediavault.CANCEL_DOWNLOAD"
    }
}
