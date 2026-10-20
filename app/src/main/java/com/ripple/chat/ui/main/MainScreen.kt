package com.ripple.chat.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ripple.chat.theme.BromeTheme
import com.ripple.chat.ui.chat.ArchiveScreen
import com.ripple.chat.ui.chat.ChatsScreen
import com.ripple.chat.ui.chat.ChatsViewModel
import com.ripple.chat.ui.chat.StarredScreen
import com.ripple.chat.ui.components.TideFloatingNavAndFab
import com.ripple.chat.ui.contacts.ContactsScreen

@Composable
fun MainScreen(
    onNavigateToChat: (String, String?, String?, String?, String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: (String?) -> Unit,
    onNavigateToThemes: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToFinanceLedger: () -> Unit,
    onNavigateToNewMessage: () -> Unit,
    chatsViewModel: ChatsViewModel = viewModel()
) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "chats"

    val threads by chatsViewModel.threads.collectAsState()
    val starredThreadIds by chatsViewModel.starredThreadIds.collectAsState()
    val archivedThreadIds by chatsViewModel.archivedThreadIds.collectAsState()
    val isSelectionMode by chatsViewModel.isSelectionMode.collectAsState()

    val selectedIndex = when (currentRoute) {
        "starred" -> 1
        "calls" -> 2
        "archive" -> 3
        else -> 0
    }

    val themeConfig = com.ripple.chat.theme.LocalThemeConfig.current
    val appearance = com.ripple.chat.theme.LocalBromeAppearance.current
    val darkTheme = when (themeConfig?.themeMode) {
        com.ripple.chat.data.ThemeMode.LIGHT -> false
        com.ripple.chat.data.ThemeMode.DARK -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val dynamicColorEnabled = themeConfig?.useDynamicColor ?: true
    val seedColor = themeConfig?.customSeedColor ?: com.ripple.chat.data.ThemeSettings.DEFAULT_COLOR

    val starredThreads = remember(threads, starredThreadIds) {
        threads.filter { it.threadId in starredThreadIds }
    }

    val archivedThreads = remember(threads, archivedThreadIds) {
        threads.filter { it.threadId in archivedThreadIds }
    }

    val inboxThreads = remember(threads, archivedThreadIds) {
        threads.filter { it.threadId !in archivedThreadIds }
    }

    BromeTheme(
        dynamicColorEnabled = dynamicColorEnabled,
        darkTheme = darkTheme,
        seedColor = seedColor,
        appearance = appearance
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = bottomNavController,
                startDestination = "chats",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("chats") {
                    ChatsScreen(
                        inboxThreads = inboxThreads,
                        onNavigateToChat = onNavigateToChat,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToThemes = onNavigateToThemes,
                        onNavigateToTrash = onNavigateToTrash,
                        onNavigateToFinanceLedger = onNavigateToFinanceLedger,
                        onNavigateToNewMessage = onNavigateToNewMessage,
                        chatsViewModel = chatsViewModel
                    )
                }
                composable("starred") {
                    StarredScreen(
                        starredThreads = starredThreads,
                        onUnstarThread = { threadId ->
                            chatsViewModel.unstarThreads(setOf(threadId))
                        },
                        onNavigateToChat = onNavigateToChat,
                        onBack = { bottomNavController.navigate("chats") },
                        chatsViewModel = chatsViewModel
                    )
                }
                composable("calls") {
                    ContactsScreen(
                        onNavigateToChat = { chatId, address, name, photo ->
                            onNavigateToChat(chatId, address, "PERSONAL", name, photo)
                        }
                    )
                }
                composable("archive") {
                    ArchiveScreen(
                        archivedThreads = archivedThreads,
                        onUnarchiveThread = { threadId ->
                            chatsViewModel.unarchiveThreads(setOf(threadId))
                        },
                        onNavigateToChat = onNavigateToChat,
                        onBack = { bottomNavController.navigate("chats") },
                        chatsViewModel = chatsViewModel
                    )
                }
            }

            // Tide Nav Pill and Compose FAB (Hidden cleanly when isSelectionMode == true!)
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                TideFloatingNavAndFab(
                    selectedIndex = selectedIndex,
                    onTabSelected = { index ->
                        val route = when (index) {
                            1 -> "starred"
                            2 -> "calls"
                            3 -> "archive"
                            else -> "chats"
                        }
                        if (currentRoute != route) {
                            bottomNavController.navigate(route) {
                                popUpTo("chats") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onComposeClick = onNavigateToNewMessage
                )
            }
        }
    }
}
