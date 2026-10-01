package com.mediavault.app.download

import java.io.File
import java.net.URI

object FileSecurityUtil {

    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    fun sanitizeFileName(originalName: String, fallbackExtension: String = "mp4"): String {
        var clean = originalName.replace(ILLEGAL_CHARS, "_").trim()
        if (clean.isEmpty()) {
            clean = "media_${System.currentTimeMillis()}"
        }
        if (!clean.contains(".")) {
            clean = "$clean.$fallbackExtension"
        }
        return clean
    }

    fun isSafeUrl(urlString: String): Boolean {
        return try {
            val uri = URI(urlString)
            val scheme = uri.scheme?.lowercase()
            scheme == "http" || scheme == "https"
        } catch (e: Exception) {
            false
        }
    }

    fun ensureSafePath(baseDir: File, targetFile: File): Boolean {
        return try {
            val canonicalBase = baseDir.canonicalPath
            val canonicalTarget = targetFile.canonicalPath
            canonicalTarget.startsWith(canonicalBase + File.separator)
        } catch (e: Exception) {
            false
        }
    }
}
