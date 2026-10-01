package com.mediavault.app.data.adapter

import com.mediavault.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

class DirectMediaAdapter(private val okHttpClient: OkHttpClient) : MediaSourceAdapter {

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
               lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".flac") ||
               lower.endsWith(".wav") || lower.endsWith(".jpg") || lower.endsWith(".png") ||
               lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".pdf")
    }

    override suspend fun resolveMedia(url: String): List<MediaResource> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaResource>()
        try {
            val fileName = runCatching {
                val path = URI(url).path
                path.substringAfterLast("/")
            }.getOrDefault("media_${System.currentTimeMillis()}")

            val headRequest = Request.Builder().url(url).head().build()
            val response = okHttpClient.newCall(headRequest).execute()
            val contentType = response.header("Content-Type") ?: "application/octet-stream"
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L

            val mediaType = when {
                contentType.startsWith("video/") -> MediaType.VIDEO
                contentType.startsWith("audio/") -> MediaType.AUDIO
                contentType.startsWith("image/") -> MediaType.IMAGE
                else -> MediaType.DOCUMENT
            }

            list.add(
                MediaResource(
                    title = fileName,
                    directUrl = url,
                    mimeType = contentType,
                    mediaType = mediaType,
                    quality = "Original",
                    sizeBytes = contentLength
                )
            )
        } catch (e: Exception) {
            // Fallback resolution based purely on file extension
            val lower = url.lowercase()
            val mediaType = when {
                lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") -> MediaType.VIDEO
                lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".flac") || lower.endsWith(".wav") -> MediaType.AUDIO
                lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif") -> MediaType.IMAGE
                else -> MediaType.DOCUMENT
            }
            list.add(
                MediaResource(
                    title = url.substringAfterLast("/").ifEmpty { "download" },
                    directUrl = url,
                    mimeType = "application/octet-stream",
                    mediaType = mediaType,
                    quality = "Direct",
                    sizeBytes = 0L
                )
            )
        }
        list
    }
}
