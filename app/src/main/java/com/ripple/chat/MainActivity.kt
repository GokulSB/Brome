package com.ripple.chat

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ripple.chat.data.AutoDeleteWorker
import com.ripple.chat.data.BromeAppearance
import com.ripple.chat.data.BromeSettings
import com.ripple.chat.data.SmsRepository
import com.ripple.chat.data.ThemeSettings
import com.ripple.chat.data.TrashManager
import com.ripple.chat.theme.BromeBackground
import com.ripple.chat.theme.BromeTheme
import com.ripple.chat.theme.LocalBromeAppearance
import com.ripple.chat.theme.LocalThemeConfig
import kotlinx.coroutines.launch
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.os.LocaleListCompat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPrefs = getSharedPreferences("RippleChatPrefs", Context.MODE_PRIVATE)
        
        // --- CRASH CATCHER ---
        val previousCrash = sharedPrefs.getString("CRASH_TRACE", null)
        if (previousCrash != null) {
            sharedPrefs.edit().remove("CRASH_TRACE").apply()
        }
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler { thread, exception ->
            val sw = java.io.StringWriter()
            exception.printStackTrace(java.io.PrintWriter(sw))
            sharedPrefs.edit().putString("CRASH_TRACE", sw.toString()).commit()
            defaultHandler?.uncaughtException(thread, exception)
        })
        // ---------------------

        val hasCompletedOnboarding = sharedPrefs.getBoolean("hasCompletedOnboarding", false)

        val trashManager = TrashManager(this)
        val smsRepository = SmsRepository(this)
        lifecycleScope.launch {
            trashManager.cleanupOldTrash(smsRepository)
        }

        // Schedule periodic auto-delete worker
        try {
            val autoDeleteWorkRequest = PeriodicWorkRequestBuilder<AutoDeleteWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "BromeAutoDeleteWork",
                ExistingPeriodicWorkPolicy.KEEP,
                autoDeleteWorkRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val themeSettings = ThemeSettings(this)
        val bromeSettings = BromeSettings(this)
        com.ripple.chat.island.IslandNotifier.init(applicationContext)

        lifecycleScope.launch {
            bromeSettings.appearanceFlow.collect { app ->
                val currentLocales = AppCompatDelegate.getApplicationLocales()
                val targetTag = if (app.languageCode == "system") "" else app.languageCode
                val currentTag = currentLocales.toLanguageTags()
                if (targetTag != currentTag) {
                    val locales = if (app.languageCode == "system") {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(app.languageCode)
                    }
                    AppCompatDelegate.setApplicationLocales(locales)
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            val themeConfig by themeSettings.themeConfigFlow.collectAsState(initial = null)
            val appearance by bromeSettings.appearanceFlow.collectAsState(initial = BromeAppearance())

            val context = LocalContext.current
            val currentLang = appearance.languageCode
            val localizedConfiguration = remember(currentLang) {
                val cfg = Configuration(context.resources.configuration)
                val targetLocale = if (currentLang == "system") {
                    Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
                } else {
                    Locale.forLanguageTag(currentLang)
                }
                cfg.setLocale(targetLocale)
                cfg.setLayoutDirection(targetLocale)
                cfg
            }
            val localizedContext = remember(currentLang, localizedConfiguration) {
                if (currentLang == "system") {
                    context
                } else {
                    context.createConfigurationContext(localizedConfiguration)
                }
            }
            val isRtl = localizedConfiguration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL

            val dynamicColorEnabled = themeConfig?.useDynamicColor ?: true
            val darkTheme = when (themeConfig?.themeMode) {
                com.ripple.chat.data.ThemeMode.LIGHT -> false
                com.ripple.chat.data.ThemeMode.DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                LocalContext provides localizedContext,
                LocalLayoutDirection provides (if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr),
                LocalThemeConfig provides themeConfig,
                LocalBromeAppearance provides appearance
            ) {
                BromeTheme(
                    dynamicColorEnabled = dynamicColorEnabled,
                    darkTheme = darkTheme,
                    seedColor = themeConfig?.customSeedColor ?: ThemeSettings.DEFAULT_COLOR,
                    appearance = appearance
                ) {
                    BromeBackground(
                        design = appearance.backgroundDesign,
                        seed = appearance.backgroundSeed,
                        washLevel = appearance.gradientWashLevel
                    ) {
                        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                            if (previousCrash != null) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = {},
                                    title = { androidx.compose.material3.Text("Crash Detected") },
                                    text = {
                                        androidx.compose.foundation.lazy.LazyColumn {
                                            item { androidx.compose.material3.Text(previousCrash) }
                                        }
                                    },
                                    confirmButton = {
                                        androidx.compose.material3.Button(onClick = { finish() }) {
                                            androidx.compose.material3.Text("OK")
                                        }
                                    }
                                )
                            } else {
                                val openThreadId = intent.getLongExtra("open_thread_id", -1L)
                                val openRoute = intent.getStringExtra("open_route")
                                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                                    MainNavigation(
                                        startWithOnboarding = !hasCompletedOnboarding,
                                        initialThreadId = if (openThreadId != -1L) openThreadId else null,
                                        initialRoute = openRoute
                                    )
                                    com.ripple.chat.island.IslandInAppBannerHost(
                                        onNavigateToThread = { threadId ->
                                            intent.putExtra("open_thread_id", threadId)
                                            recreate()
                                        }
                                    )
                                    com.ripple.chat.ui.components.BromeToastHost(
                                        modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
