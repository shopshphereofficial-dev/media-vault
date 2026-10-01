package com.mediavault.app.data.db

import androidx.room.*
import com.mediavault.app.data.model.DownloadStatus
import com.mediavault.app.data.model.DownloadTask
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdTimestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM downloads WHERE status = :status")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadTask>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: Long): DownloadTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(task: DownloadTask): Long

    @Update
    suspend fun updateDownload(task: DownloadTask)

    @Delete
    suspend fun deleteDownload(task: DownloadTask)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, speedBps = :speed, etaSeconds = :eta WHERE id = :id")
    suspend fun updateProgress(id: Long, downloaded: Long, total: Long, speed: Long, eta: Long)
}
