package com.mediavault.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
}

@Entity(tableName = "downloads")
data class DownloadTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val fileName: String,
    val mediaType: MediaType,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val speedBps: Long = 0L,
    val etaSeconds: Long = 0L,
    val quality: String = "Standard",
    val errorReason: String? = null,
    val createdTimestamp: Long = System.currentTimeMillis()
)
