package com.ripple.chat

import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ripple.chat.ui.onboarding.OnboardingScreen
import com.ripple.chat.ui.main.MainScreen
import com.ripple.chat.ui.chat.ChatDetailScreen

@Composable
fun MainNavigation(startWithOnboarding: Boolean = true, initialThreadId: Long? = null, initialRoute: String? = null) {
    val navController = rememberNavController()
    val startDestination = if (startWithOnboarding) "onboarding" else "main"
    
    androidx.compose.runtime.LaunchedEffect(initialThreadId, initialRoute) {
        if (initialThreadId != null) {
            navController.navigate("chat/$initialThreadId")
        } else if (initialRoute != null) {
            navController.navigate(initialRoute)
        }
    }
    
    NavHost(
        navController = navController, 
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { 300 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -300 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -300 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { 300 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onNavigateToMain = {
                    navController.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        
        composable("main") {
            MainScreen(
                onNavigateToChat = { chatId, address, category, contactName, photoUri -> 
                    val cleanAddr = com.ripple.chat.utils.MessageParser.cleanAddress(address)
                    val params = listOfNotNull(
                        cleanAddr?.let { "address=${java.net.URLEncoder.encode(it, "UTF-8")}" },
                        category?.let { "category=${java.net.URLEncoder.encode(it, "UTF-8")}" },
                        contactName?.let { "contactName=${java.net.URLEncoder.encode(it, "UTF-8")}" },
                        photoUri?.let { "photoUri=${java.net.URLEncoder.encode(it, "UTF-8")}" }
                    ).joinToString("&")
                    val suffix = if (params.isNotEmpty()) "?$params" else ""
                    val encodedChatId = java.net.URLEncoder.encode(chatId, "UTF-8")
                    navController.navigate("chat/$encodedChatId$suffix")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToProfile = { address ->
                    val encoded = address?.let { java.net.URLEncoder.encode(it, "UTF-8") }
                    val suffix = if (encoded != null) "?address=$encoded" else ""
                    navController.navigate("profile$suffix")
                },
                onNavigateToThemes = {
                    navController.navigate("themes")
                },
                onNavigateToTrash = {
                    navController.navigate("trash")
                },
                onNavigateToFinanceLedger = {
                    navController.navigate("finance_ledger")
                },
                onNavigateToNewMessage = {
                    navController.navigate("new_message")
                }
            )
        }
        

        composable("new_message") {
            com.ripple.chat.ui.chat.NewMessageScreen(
                onBack = { navController.popBackStack() },
                onNavigateToChat = { chatId, address, category, contactName, photoUri -> 
                    val cleanAddr = com.ripple.chat.utils.MessageParser.cleanAddress(address)
                    val params = listOfNotNull(
                        cleanAddr?.let { "address=${java.net.URLEncoder.encode(it, "UTF-8")}" },
                        category?.let { "category=${java.net.URLEncoder.encode(it, "UTF-8")}" },
                        contactName?.let { "contactName=${java.net.URLEncoder.encode(it, "UTF-8")}" },
                        photoUri?.let { "photoUri=${java.net.URLEncoder.encode(it, "UTF-8")}" }
                    ).joinToString("&")
                    val suffix = if (params.isNotEmpty()) "?$params" else ""
                    val encodedChatId = java.net.URLEncoder.encode(chatId, "UTF-8")
                    navController.navigate("chat/$encodedChatId$suffix") {
                        popUpTo("main")
                    }
                }
            )
        }
        
        composable("settings") {
            com.ripple.chat.ui.settings.SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("themes") {
            com.ripple.chat.ui.settings.ThemeScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("trash") {
            com.ripple.chat.ui.trash.TrashScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("send_probe") {
            com.ripple.chat.ui.debug.SendProbeScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            "profile?address={address}",
            arguments = listOf(
                androidx.navigation.navArgument("address") { type = androidx.navigation.NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString("address")
            com.ripple.chat.ui.profile.ProfileScreen(
                address = address,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            "chat/{chatId}?address={address}&category={category}&contactName={contactName}&photoUri={photoUri}",
            arguments = listOf(
                androidx.navigation.navArgument("chatId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("address") { type = androidx.navigation.NavType.StringType; nullable = true },
                androidx.navigation.navArgument("category") { type = androidx.navigation.NavType.StringType; nullable = true },
                androidx.navigation.navArgument("contactName") { type = androidx.navigation.NavType.StringType; nullable = true },
                androidx.navigation.navArgument("photoUri") { type = androidx.navigation.NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val rawAddress = backStackEntry.arguments?.getString("address")
            val address = com.ripple.chat.utils.MessageParser.cleanAddress(rawAddress) ?: rawAddress
            val category = backStackEntry.arguments?.getString("category")
            val contactName = backStackEntry.arguments?.getString("contactName")
            val photoUri = backStackEntry.arguments?.getString("photoUri")
            ChatDetailScreen(
                chatId = chatId,
                address = address,
                category = category,
                contactName = contactName,
                photoUri = photoUri,
                onBack = { navController.navigateUp() }
            )
        }
        
        composable("finance_ledger") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val viewModel: com.ripple.chat.ui.finance.FinanceLedgerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return com.ripple.chat.ui.finance.FinanceLedgerViewModel(context.applicationContext) as T
                    }
                }
            )
            com.ripple.chat.ui.finance.FinanceLedgerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
