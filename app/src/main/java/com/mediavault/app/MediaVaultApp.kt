package com.mediavault.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MediaVaultApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val downloadChannel = NotificationChannel(
                "media_downloads",
                "Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and status of active downloads"
            }

            val playbackChannel = NotificationChannel(
                "media_playback",
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows media controls for playing audio"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(downloadChannel)
            manager.createNotificationChannel(playbackChannel)
        }
    }
}
