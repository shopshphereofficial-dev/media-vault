package com.mediavault.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.runtime.*
import com.mediavault.app.ui.navigation.AppNavigation
import com.mediavault.app.ui.theme.MediaVaultTheme

class MainActivity : AppCompatActivity() {

    private var sharedUrlState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            var isAmoled by remember { mutableStateOf(false) }
            var isWifiOnly by remember { mutableStateOf(false) }
            val sharedUrl by sharedUrlState

            MediaVaultTheme(isAmoled = isAmoled) {
                AppNavigation(
                    initialUrl = sharedUrl,
                    isAmoled = isAmoled,
                    onAmoledChanged = { isAmoled = it },
                    isWifiOnly = isWifiOnly,
                    onWifiOnlyChanged = { isWifiOnly = it }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                val regex = Regex("""https?://[^\s]+""")
                val found = regex.find(text)?.value ?: text
                sharedUrlState.value = found
            }
        }
    }
}
