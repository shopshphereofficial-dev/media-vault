package com.mediavault.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mediavault.app.data.adapter.DirectMediaAdapter
import com.mediavault.app.data.adapter.MediaResource
import com.mediavault.app.data.db.AppDatabase
import com.mediavault.app.data.model.DownloadStatus
import com.mediavault.app.data.model.DownloadTask
import com.mediavault.app.download.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(initialUrl: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf(initialUrl ?: "https://duckduckgo.com") }
    var currentUrl by remember { mutableStateOf(initialUrl ?: "https://duckduckgo.com") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val detectedMedia = remember { mutableStateListOf<MediaResource>() }
    var showDownloadDialog by remember { mutableStateOf(false) }
    val directAdapter = remember { DirectMediaAdapter(OkHttpClient()) }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank() && initialUrl != currentUrl) {
            urlInput = initialUrl
            currentUrl = initialUrl
            webViewInstance?.loadUrl(initialUrl)
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (canGoBack) webViewInstance?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(
                        onClick = { if (canGoForward) webViewInstance?.goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                    }
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.weight(1f).height(50.dp),
                        singleLine = true,
                        placeholder = { Text("Search or enter URL") },
                        trailingIcon = {
                            IconButton(onClick = {
                                val target = if (urlInput.startsWith("http://") || urlInput.startsWith("https://")) {
                                    urlInput
                                } else {
                                    "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(urlInput, "UTF-8")}"
                                }
                                currentUrl = target
                                webViewInstance?.loadUrl(target)
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Go")
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                }
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        floatingActionButton = {
            if (detectedMedia.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("Detected (${detectedMedia.size})") },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Download") },
                    onClick = { showDownloadDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                url?.let { urlInput = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false

                                url?.let { pageUrl ->
                                    scope.launch {
                                        if (directAdapter.canHandle(pageUrl)) {
                                            val resources = directAdapter.resolveMedia(pageUrl)
                                            resources.forEach { res ->
                                                if (detectedMedia.none { it.directUrl == res.directUrl }) {
                                                    detectedMedia.add(res)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                request?.url?.toString()?.let { reqUrl ->
                                    if (directAdapter.canHandle(reqUrl)) {
                                        scope.launch {
                                            val resources = directAdapter.resolveMedia(reqUrl)
                                            resources.forEach { res ->
                                                if (detectedMedia.none { it.directUrl == res.directUrl }) {
                                                    detectedMedia.add(res)
                                                }
                                            }
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        loadUrl(currentUrl)
                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (showDownloadDialog) {
                AlertDialog(
                    onDismissRequest = { showDownloadDialog = false },
                    title = { Text("Available Media Downloads") },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(detectedMedia) { media ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(media.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                            Text(
                                                "${media.quality} • ${media.mimeType}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    val db = AppDatabase.getInstance(context)
                                                    val newId = db.downloadDao().insertDownload(
                                                        DownloadTask(
                                                            url = media.directUrl,
                                                            fileName = media.title,
                                                            mediaType = media.mediaType,
                                                            quality = media.quality,
                                                            status = DownloadStatus.QUEUED
                                                        )
                                                    )
                                                    DownloadWorker.enqueueDownload(context, newId)
                                                    showDownloadDialog = false
                                                }
                                            }
                                        ) {
                                            Text("Download")
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDownloadDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}
