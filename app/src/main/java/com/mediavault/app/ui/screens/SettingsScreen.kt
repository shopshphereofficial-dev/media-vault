package com.mediavault.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun SettingsScreen(
    isAmoled: Boolean,
    onAmoledChanged: (Boolean) -> Unit,
    isWifiOnly: Boolean,
    onWifiOnlyChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var cacheSizeMb by remember {
        mutableStateOf(
            ((context.cacheDir.walkTopDown().sumOf { it.length() }) / (1024 * 1024))
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Text("Display & Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("AMOLED Pure Black Theme", style = MaterialTheme.typography.bodyLarge)
                Text("Deep blacks for OLED displays", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = isAmoled, onCheckedChange = onAmoledChanged)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Network & Downloads", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Download on Wi-Fi only", style = MaterialTheme.typography.bodyLarge)
                Text("Avoid consuming mobile cellular data", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = isWifiOnly, onCheckedChange = onWifiOnlyChanged)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Storage Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Cache Size", style = MaterialTheme.typography.bodyLarge)
                Text("${cacheSizeMb} MB currently used", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = {
                context.cacheDir.deleteRecursively()
                context.cacheDir.mkdirs()
                cacheSizeMb = 0L
            }) {
                Text("Clear Cache")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Privacy & Security Commitment", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "MediaVault is 100% ad-free, tracker-free, and collects zero analytics or credentials. Your private files are encrypted locally on your device with hardware-backed AES-GCM.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
