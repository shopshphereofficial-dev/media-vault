package com.mediavault.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
        val initialNotif = buildNotification("MediaVault Downloader Active", 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notificationId, initialNotif)
        }
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
                            if (!isActive) break
                            output.write(buffer, 0, bytesRead)
                            downloadedSoFar += bytesRead
                            bytesSinceUpdate += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 1000) {
                                val speed = bytesSinceUpdate * 1000 / (now - lastUpdate)
                                val remaining = if (totalLength > downloadedSoFar) totalLength - downloadedSoFar else 0L
                                val eta = if (speed > 0) remaining / speed else 0L

                                db.downloadDao().updateProgress(taskId, downloadedSoFar, totalLength, speed, eta)
                                updateNotification(task.title, downloadedSoFar, totalLength)

                                bytesSinceUpdate = 0L
                                lastUpdate = now
                            }
                        }
                    }
                }

                if (isActive) {
                    db.downloadDao().updateStatus(taskId, DownloadStatus.COMPLETED)

                    val isVideo = safeName.endsWith(".mp4", true) || safeName.endsWith(".mkv", true)
                    val isAudio = safeName.endsWith(".mp3", true) || safeName.endsWith(".m4a", true)
                    val isImage = safeName.endsWith(".jpg", true) || safeName.endsWith(".png", true)
                    val mediaType = when {
                        isVideo -> MediaType.VIDEO
                        isAudio -> MediaType.AUDIO
                        isImage -> MediaType.IMAGE
                        else -> MediaType.DOCUMENT
                    }

                    val mediaItem = MediaItem(
                        title = task.title,
                        filePath = outputFile.absolutePath,
                        fileSizeBytes = outputFile.length(),
                        mimeType = if (isVideo) "video/mp4" else if (isAudio) "audio/mpeg" else "application/octet-stream",
                        mediaType = mediaType,
                        sourceUrl = task.url
                    )
                    db.mediaDao().insertMedia(mediaItem)
                    notifyFinished(task.title, true)
                }

            } catch (e: Exception) {
                db.downloadDao().updateStatus(taskId, DownloadStatus.FAILED)
                notifyFinished(task.title, false)
            } finally {
                activeJobs.remove(taskId)
                if (activeJobs.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
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
            val db = AppDatabase.getInstance(applicationContext)
            db.downloadDao().updateStatus(taskId, DownloadStatus.PAUSED)
        }
    }

    private fun cancelDownload(taskId: Long) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            db.downloadDao().updateStatus(taskId, DownloadStatus.FAILED)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, downloaded: Long, total: Long): Notification {
        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("MediaVault Downloader")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, total <= 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, downloaded: Long, total: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, buildNotification(title, downloaded, total))
    }

    private fun notifyFinished(title: String, success: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle(if (success) "Download Complete" else "Download Failed")
            .setContentText(title)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notif)
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
