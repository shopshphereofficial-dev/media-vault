package com.mediavault.app.data.adapter

import com.mediavault.app.data.model.MediaType

data class MediaResource(
    val title: String,
    val directUrl: String,
    val mimeType: String,
    val mediaType: MediaType,
    val quality: String,
    val sizeBytes: Long = 0L
)

interface MediaSourceAdapter {
    fun canHandle(url: String): Boolean
    suspend fun resolveMedia(url: String): List<MediaResource>
}
