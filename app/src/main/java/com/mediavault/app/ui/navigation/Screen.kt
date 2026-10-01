package com.mediavault.app.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Browser : Screen("browser", "Browser")
    object Downloads : Screen("downloads", "Downloads")
    object Library : Screen("library", "Library")
    object Vault : Screen("vault", "Vault")
    object Settings : Screen("settings", "Settings")
    object Player : Screen("player/{filePath}/{title}", "Player") {
        fun createRoute(filePath: String, title: String) =
            "player/${java.net.URLEncoder.encode(filePath, "UTF-8")}/${java.net.URLEncoder.encode(title, "UTF-8")}"
    }
}
