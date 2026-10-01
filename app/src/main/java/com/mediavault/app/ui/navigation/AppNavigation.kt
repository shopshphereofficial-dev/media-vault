package com.mediavault.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.mediavault.app.ui.screens.*
import java.net.URLDecoder

@Composable
fun AppNavigation(
    initialUrl: String? = null,
    isAmoled: Boolean,
    onAmoledChanged: (Boolean) -> Unit,
    isWifiOnly: Boolean,
    onWifiOnlyChanged: (Boolean) -> Unit
) {
    val navController = rememberNavController()

    val bottomNavItems = listOf(
        Screen.Browser,
        Screen.Downloads,
        Screen.Library,
        Screen.Vault,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            val isPlayerScreen = currentDestination?.route?.startsWith("player/") == true

            if (!isPlayerScreen) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = when (screen) {
                                        Screen.Browser -> Icons.Default.Public
                                        Screen.Downloads -> Icons.Default.Download
                                        Screen.Library -> Icons.Default.VideoLibrary
                                        Screen.Vault -> Icons.Default.Lock
                                        Screen.Settings -> Icons.Default.Settings
                                        else -> Icons.Default.Home
                                    },
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = currentDestination?.route == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Browser.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(280)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(280)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(280)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(280)
                )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(280)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(280)
                )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(280)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(280)
                )
            }
        ) {
            composable(Screen.Browser.route) {
                BrowserScreen(initialUrl = initialUrl)
            }
            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    onPlayMedia = { path, title ->
                        navController.navigate(Screen.Player.createRoute(path, title))
                    }
                )
            }
            composable(Screen.Library.route) {
                LibraryScreen(
                    onPlayMedia = { path, title ->
                        navController.navigate(Screen.Player.createRoute(path, title))
                    }
                )
            }
            composable(Screen.Vault.route) {
                VaultScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    isAmoled = isAmoled,
                    onAmoledChanged = onAmoledChanged,
                    isWifiOnly = isWifiOnly,
                    onWifiOnlyChanged = onWifiOnlyChanged
                )
            }
            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument("filePath") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                val encodedTitle = backStackEntry.arguments?.getString("title") ?: "Media"
                val filePath = URLDecoder.decode(encodedPath, "UTF-8")
                val title = URLDecoder.decode(encodedTitle, "UTF-8")

                PlayerScreen(
                    filePath = filePath,
                    title = title,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
