package com.mediavault.app.ui.quickshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediavault.app.MainActivity
import com.mediavault.app.data.db.AppDatabase
import com.mediavault.app.data.model.DownloadStatus
import com.mediavault.app.data.model.DownloadTask
import com.mediavault.app.data.model.MediaType
import com.mediavault.app.download.DownloadForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

class QuickDownloadDialogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val extractedUrl = extractUrl(rawText)

        setContent {
            QuickDownloadBottomSheet(
                url = extractedUrl,
                onDismiss = { finish() },
                onOpenFullApp = {
                    val openIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(openIntent)
                    finish()
                },
                onStartDownload = { title, downloadUrl, format, mediaType ->
                    triggerDownload(title, downloadUrl, format, mediaType)
                }
            )
        }
    }

    private fun extractUrl(text: String): String {
        val regex = Regex("""https?://[^\s]+""")
        return regex.find(text)?.value ?: text.trim()
    }

    private fun triggerDownload(title: String, url: String, format: String, mediaType: MediaType) {
        val appContext = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(appContext)
            val extension = when (mediaType) {
                MediaType.AUDIO -> if (format.contains("M4A", true)) "m4a" else "mp3"
                MediaType.IMAGE -> "jpg"
                else -> "mp4"
            }
            val fileName = "MediaVault_${System.currentTimeMillis()}.$extension"

            val task = DownloadTask(
                title = "$title ($format)",
                url = url,
                fileName = fileName,
                status = DownloadStatus.QUEUED,
                totalBytes = 0L,
                downloadedBytes = 0L,
                speedBytesPerSec = 0L,
                etaSeconds = 0L
            )
            val newId = db.downloadDao().insertDownload(task)

            val serviceIntent = Intent(appContext, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_START
                putExtra("TASK_ID", newId)
            }
            appContext.startService(serviceIntent)

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "⬇️ Download started in background: $format",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}

@Composable
fun QuickDownloadBottomSheet(
    url: String,
    onDismiss: () -> Unit,
    onOpenFullApp: () -> Unit,
    onStartDownload: (title: String, url: String, format: String, mediaType: MediaType) -> Unit
) {
    val platformInfo = remember(url) { detectPlatform(url) }
    var selectedOption by remember { mutableStateOf<String?>(null) }

    // Dimmed background that closes dialog on tap outside
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {}) // Block tap propagation
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131826)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Drag handle bar
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Header with Platform Badge and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(platformInfo.badgeStartColor, platformInfo.badgeEndColor)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = platformInfo.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = platformInfo.name,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (url.length > 36) url.take(36) + "..." else url.ifEmpty { "Shared Media" },
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = Color.White.copy(alpha = 0.08f))

                Spacer(modifier = Modifier.height(14.dp))

                // Video Download Section
                SectionHeader(title = "Video Formats (MP4)", icon = Icons.Default.Videocam, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.height(8.dp))

                val videoFormats = listOf(
                    FormatOption("1080p Full HD", "Best Quality • 60 FPS", "~35 MB", MediaType.VIDEO),
                    FormatOption("720p HD", "Standard HD • Recommended", "~18 MB", MediaType.VIDEO),
                    FormatOption("480p SD", "Data Saver", "~9 MB", MediaType.VIDEO),
                    FormatOption("360p Fast", "Fast Download", "~5 MB", MediaType.VIDEO)
                )

                videoFormats.forEach { format ->
                    FormatItemRow(
                        title = format.title,
                        subtitle = format.subtitle,
                        size = format.size,
                        isSelected = selectedOption == format.title,
                        onClick = {
                            selectedOption = format.title
                            onStartDownload(platformInfo.name, url, format.title, format.type)
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Audio Download Section
                SectionHeader(title = "Audio Formats (Music/Voice)", icon = Icons.Default.MusicNote, tint = Color(0xFFA78BFA))
                Spacer(modifier = Modifier.height(8.dp))

                val audioFormats = listOf(
                    FormatOption("MP3 High Quality", "320 kbps • Studio Audio", "~4.2 MB", MediaType.AUDIO),
                    FormatOption("M4A Fast Audio", "128 kbps • Lightweight", "~2.1 MB", MediaType.AUDIO)
                )

                audioFormats.forEach { format ->
                    FormatItemRow(
                        title = format.title,
                        subtitle = format.subtitle,
                        size = format.size,
                        isSelected = selectedOption == format.title,
                        onClick = {
                            selectedOption = format.title
                            onStartDownload(platformInfo.name, url, format.title, format.type)
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cover & Extra options
                SectionHeader(title = "Thumbnail / Photo", icon = Icons.Default.Image, tint = Color(0xFF34D399))
                Spacer(modifier = Modifier.height(8.dp))

                FormatItemRow(
                    title = "HD Thumbnail / Cover",
                    subtitle = "Original Image Resolution",
                    size = "~450 KB",
                    isSelected = selectedOption == "HD Thumbnail",
                    onClick = {
                        selectedOption = "HD Thumbnail"
                        onStartDownload(platformInfo.name, url, "HD Thumbnail", MediaType.IMAGE)
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom row: open full app
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onOpenFullApp) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open in MediaVault App",
                            color = Color(0xFF60A5FA),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color(0xFFE2E8F0),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FormatItemRow(
    title: String,
    subtitle: String,
    size: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgModifier = if (isSelected) {
        Modifier.background(Color(0xFF2563EB).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
    } else {
        Modifier.background(Color(0xFF1E2638).copy(alpha = 0.7f), RoundedCornerShape(12.dp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(bgModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Text(
                    text = size,
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                Icons.Default.Download,
                contentDescription = "Download",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private data class FormatOption(
    val title: String,
    val subtitle: String,
    val size: String,
    val type: MediaType
)

private data class PlatformInfo(
    val name: String,
    val icon: ImageVector,
    val badgeStartColor: Color,
    val badgeEndColor: Color
)

private fun detectPlatform(url: String): PlatformInfo {
    val lower = url.lowercase()
    return when {
        lower.contains("instagram.com") -> PlatformInfo(
            name = "Instagram Media",
            icon = Icons.Default.CameraAlt,
            badgeStartColor = Color(0xFFE1306C),
            badgeEndColor = Color(0xFFF77737)
        )
        lower.contains("youtube.com") || lower.contains("youtu.be") -> PlatformInfo(
            name = "YouTube Video",
            icon = Icons.Default.PlayArrow,
            badgeStartColor = Color(0xFFFF0000),
            badgeEndColor = Color(0xFFCC0000)
        )
        lower.contains("facebook.com") || lower.contains("fb.watch") -> PlatformInfo(
            name = "Facebook Media",
            icon = Icons.Default.ThumbUp,
            badgeStartColor = Color(0xFF1877F2),
            badgeEndColor = Color(0xFF0C5EC9)
        )
        lower.contains("twitter.com") || lower.contains("x.com") -> PlatformInfo(
            name = "X / Twitter Media",
            icon = Icons.Default.Send,
            badgeStartColor = Color(0xFF000000),
            badgeEndColor = Color(0xFF333333)
        )
        else -> PlatformInfo(
            name = "Web Download",
            icon = Icons.Default.CloudDownload,
            badgeStartColor = Color(0xFF2563EB),
            badgeEndColor = Color(0xFF8B5CF6)
        )
    }
}
