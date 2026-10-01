package com.mediavault.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.core.content.FileProvider
import com.mediavault.app.data.adapter.ZipArchiveAdapter
import com.mediavault.app.data.db.AppDatabase
import com.mediavault.app.data.model.MediaItem
import com.mediavault.app.data.model.MediaType
import com.mediavault.app.vault.VaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onPlayMedia: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val vaultManager = remember { VaultManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeIndex by remember { mutableStateOf(0) }
    val categories = listOf("All", "Videos", "Music", "Images", "Docs", "Favorites")

    val allMedia by db.mediaDao().getAllMedia().collectAsState(initial = emptyList())

    val filteredList = allMedia.filter { item ->
        val matchesQuery = searchQuery.isBlank() || item.title.contains(searchQuery, ignoreCase = true)
        val matchesCategory = when (selectedTypeIndex) {
            1 -> item.mediaType == MediaType.VIDEO
            2 -> item.mediaType == MediaType.AUDIO
            3 -> item.mediaType == MediaType.IMAGE
            4 -> item.mediaType == MediaType.DOCUMENT
            5 -> item.isFavorite
            else -> true
        }
        matchesQuery && matchesCategory
    }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val contentResolver = context.contentResolver
                contentResolver.openInputStream(it)?.use { input ->
                    val importDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "imported")
                    val imported = ZipArchiveAdapter.importOfficialArchive(context, input, importDir)
                    imported.forEach { item ->
                        db.mediaDao().insertMedia(item)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Media Library", style = MaterialTheme.typography.headlineMedium)
                    IconButton(onClick = { zipPickerLauncher.launch(arrayOf("application/zip")) }) {
                        Icon(Icons.Default.Archive, contentDescription = "Import Archive")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search files...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                ScrollableTabRow(selectedTabIndex = selectedTypeIndex, edgePadding = 0.dp) {
                    categories.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTypeIndex == idx,
                            onClick = { selectedTypeIndex = idx },
                            text = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No media files found")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (item.mediaType == MediaType.VIDEO || item.mediaType == MediaType.AUDIO) {
                                onPlayMedia(item.filePath, item.title)
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (item.mediaType) {
                                    MediaType.VIDEO -> Icons.Default.Videocam
                                    MediaType.AUDIO -> Icons.Default.MusicNote
                                    MediaType.IMAGE -> Icons.Default.Image
                                    MediaType.DOCUMENT -> Icons.Default.Description
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    "${item.mediaType.name} • ${item.sizeBytes / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    db.mediaDao().setFavorite(item.id, !item.isFavorite)
                                }
                            }) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    // Move to Vault
                                    withContext(Dispatchers.IO) {
                                        val originalFile = File(item.filePath)
                                        if (originalFile.exists()) {
                                            val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
                                            val encryptedFile = File(vaultDir, "${originalFile.name}.vault")
                                            vaultManager.encryptFile(originalFile, encryptedFile)
                                            originalFile.delete()
                                            db.mediaDao().updateMedia(
                                                item.copy(
                                                    filePath = encryptedFile.absolutePath,
                                                    isVaulted = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Lock, contentDescription = "Move to Vault")
                            }
                            IconButton(onClick = {
                                val file = File(item.filePath)
                                if (file.exists()) {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = item.mimeType
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                        }
                    }
                }
            }
        }
    }
}
