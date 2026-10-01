package com.mediavault.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType {
    VIDEO, AUDIO, IMAGE, DOCUMENT
}

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val mediaType: MediaType,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val sourceUrl: String = "",
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isVaulted: Boolean = false,
    val isFavorite: Boolean = false,
    val playlistName: String? = null
)
