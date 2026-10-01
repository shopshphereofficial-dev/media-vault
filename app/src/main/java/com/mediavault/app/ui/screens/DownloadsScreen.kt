package com.mediavault.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mediavault.app.data.db.AppDatabase
import com.mediavault.app.data.model.DownloadStatus
import com.mediavault.app.data.model.DownloadTask
import com.mediavault.app.download.DownloadForegroundService
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(onPlayMedia: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val downloads by db.downloadDao().getAllDownloads().collectAsState(initial = emptyList())

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Active", "Completed")

    val activeDownloads = downloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED }
    val completedDownloads = downloads.filter { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Downloads", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val displayedList = if (selectedTabIndex == 0) activeDownloads else completedDownloads

        if (displayedList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloads in this section", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(displayedList, key = { it.id }) { task ->
                    DownloadItemCard(
                        task = task,
                        onPause = {
                            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                                action = DownloadForegroundService.ACTION_PAUSE
                                putExtra("TASK_ID", task.id)
                            }
                            context.startService(intent)
                        },
                        onResume = {
                            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                                action = DownloadForegroundService.ACTION_START
                                putExtra("TASK_ID", task.id)
                            }
                            context.startService(intent)
                        },
                        onCancel = {
                            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                                action = DownloadForegroundService.ACTION_CANCEL
                                putExtra("TASK_ID", task.id)
                            }
                            context.startService(intent)
                        },
                        onDelete = {
                            scope.launch {
                                db.downloadDao().deleteDownload(task)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(task.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Spacer(modifier = Modifier.height(8.dp))

            val progress = if (task.totalBytes > 0) (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val speedKb = task.speedBps / 1024
                Text(
                    text = "${task.status.name} • ${speedKb} KB/s • ETA: ${task.etaSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    if (task.status == DownloadStatus.DOWNLOADING) {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                        }
                    } else if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.QUEUED) {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                        }
                    }

                    if (task.status == DownloadStatus.COMPLETED) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    } else {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                }
            }
        }
    }
}
