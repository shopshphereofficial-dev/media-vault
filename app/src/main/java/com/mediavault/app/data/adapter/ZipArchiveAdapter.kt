package com.mediavault.app.data.adapter

import android.content.Context
import com.mediavault.app.data.model.MediaItem
import com.mediavault.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object ZipArchiveAdapter {

    suspend fun importOfficialArchive(
        context: Context,
        zipStream: InputStream,
        targetDir: File
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<MediaItem>()
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val canonicalDestDirPath = targetDir.canonicalPath

        ZipInputStream(zipStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outputFile = File(targetDir, entry.name)
                    // Security: Zip Slip vulnerability prevention
                    val canonicalOutputPath = outputFile.canonicalPath
                    if (!canonicalOutputPath.startsWith(canonicalDestDirPath + File.separator)) {
                        throw SecurityException("Zip traversal path detected: ${entry.name}")
                    }

                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { fos ->
                        zis.copyTo(fos)
                    }

                    val nameLower = outputFile.name.lowercase()
                    val mediaType = when {
                        nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".mov") -> MediaType.VIDEO
                        nameLower.endsWith(".mp3") || nameLower.endsWith(".m4a") || nameLower.endsWith(".wav") -> MediaType.AUDIO
                        nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp") -> MediaType.IMAGE
                        else -> MediaType.DOCUMENT
                    }

                    val mimeType = when (mediaType) {
                        MediaType.VIDEO -> "video/mp4"
                        MediaType.AUDIO -> "audio/mpeg"
                        MediaType.IMAGE -> "image/jpeg"
                        MediaType.DOCUMENT -> "application/octet-stream"
                    }

                    imported.add(
                        MediaItem(
                            title = outputFile.name,
                            filePath = outputFile.absolutePath,
                            mediaType = mediaType,
                            mimeType = mimeType,
                            sizeBytes = outputFile.length(),
                            sourceUrl = "Account Data Export"
                        )
                    )
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        imported
    }
}
