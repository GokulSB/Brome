package com.ripple.chat.ui.settings

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.ripple.chat.R
import com.ripple.chat.data.*
import com.ripple.chat.theme.*
import com.ripple.chat.ui.AppStrings

val robustColors = listOf(
    null, // Default
    0xFF2196F3.toInt(), // Blue
    0xFF4CAF50.toInt(), // Green
    0xFF9C27B0.toInt(), // Purple
    0xFFE91E63.toInt(), // Pink
    0xFFFF9800.toInt(), // Orange
    0xFF607D8B.toInt(), // Blue Gray
    0xFFF44336.toInt(), // Red
    0xFFD84A3B.toInt()  // Dark Orange/Red
)

enum class SettingsDetailPage {
    APPEARANCE, LAYOUT, AUTO_DELETE_LANG, DYNAMIC_ISLAND
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()

    val appVersionName = remember(context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } ?: "2.0"
        } catch (_: Exception) {
            "2.0"
        }
    }

    val bromeSettings = remember { BromeSettings(context) }
    val appearance by bromeSettings.appearanceFlow.collectAsState(initial = BromeAppearance())
    val islandSettings by bromeSettings.islandSettingsFlow.collectAsState(initial = DynamicIslandSettings())

    val themeSettings = remember { ThemeSettings(context) }
    val themeConfig by themeSettings.themeConfigFlow.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    var activeDetailPage by remember { mutableStateOf<SettingsDetailPage?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    fun matches(vararg terms: String): Boolean {
        if (searchQuery.isBlank()) return true
        val q = searchQuery.trim().lowercase()
        return terms.any { it.lowercase().contains(q) }
    }

    // Confirmation dialog for auto-delete changes
    var pendingAutoDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmationDialogMessage by remember { mutableStateOf<String?>(null) }

    val dynamicColorEnabled = themeConfig?.useDynamicColor ?: true
    val isDark = when (themeConfig?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val seedColor = themeConfig?.customSeedColor ?: ThemeSettings.DEFAULT_COLOR

    val otpQuad = BromeCategoryColors.getColors(SmsCategory.OTP, isDark, themeConfig?.categoryColors?.get("OTP"))

    val cardBaseRadius = appearance.cornerRadius.dp
    val cardSharpRadius = (appearance.cornerRadius * 0.33f).coerceAtMost(10f).dp
    val keepOneSharp = appearance.keepOneSharpCorner
    fun appTileShape(index: Int) = getTideCornerShape(index, baseRadius = cardBaseRadius, sharpRadius = cardSharpRadius, keepOneSharp = keepOneSharp)

    CompositionLocalProvider(
        LocalThemeConfig provides themeConfig,
        LocalBromeAppearance provides appearance
    ) {
        BromeTheme(
            dynamicColorEnabled = dynamicColorEnabled,
            darkTheme = isDark,
            seedColor = seedColor,
            appearance = appearance
        ) {
            BromeBackground(
                design = appearance.backgroundDesign,
                seed = appearance.backgroundSeed,
                washLevel = appearance.gradientWashLevel
            ) {
                Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                if (activeDetailPage == null) {
                    // ─────────────────────────────────────────────────────
                    // HUB SCREEN
                    // ─────────────────────────────────────────────────────
                    Column(modifier = Modifier.fillMaxSize()) {

                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = onBack,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.size(48.dp).pressScale()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = stringResource(R.string.settings_title),
                                fontFamily = BricolageFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 34.sp,
                                letterSpacing = (-1).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Make Brome feel like yours.",
                            fontFamily = FigtreeFontFamily,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 28.dp, end = 20.dp, top = 4.dp, bottom = 14.dp)
                        )

                        // Content Column
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                        ) {
                            // Search Pill
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search settings",
                                                fontFamily = FigtreeFontFamily,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontFamily = FigtreeFontFamily,
                                                fontSize = 14.sp
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { searchQuery = "" }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // "Your Brome" Live Preview Card
                            YourBromePreviewCard(appearance = appearance, themeConfig = themeConfig)

                            Spacer(modifier = Modifier.height(20.dp))

                            // ── SECTION 1: APPEARANCE ──
                            if (matches("Appearance", "Background", "Wallpaper", "Design", "Theme", "Font", "Bubbles", "Color")) {
                                TideSettingsSectionLabel(stringResource(R.string.section_appearance))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    TideBentoTile(
                                        title = stringResource(R.string.tile_background),
                                        description = stringResource(R.string.tile_background_desc),
                                        icon = Icons.Rounded.Wallpaper,
                                        iconShape = Sunny12Shape,
                                        iconBg = MaterialTheme.colorScheme.tertiary,
                                        iconOn = MaterialTheme.colorScheme.onTertiary,
                                        cardShape = appTileShape(0),
                                        onClick = { activeDetailPage = SettingsDetailPage.APPEARANCE },
                                        modifier = Modifier.weight(1f),
                                        livePreview = {
                                            Text(appearance.backgroundDesign.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    )

                                    val modeLabel = when (themeConfig?.themeMode) {
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                        else -> "System"
                                    }
                                    TideBentoTile(
                                        title = "Theme & Font",
                                        description = "Colors, typeface & chat bubbles",
                                        icon = Icons.Rounded.Palette,
                                        iconShape = Flower5Shape,
                                        iconBg = MaterialTheme.colorScheme.primary,
                                        iconOn = MaterialTheme.colorScheme.onPrimary,
                                        cardShape = appTileShape(1),
                                        onClick = { activeDetailPage = SettingsDetailPage.APPEARANCE },
                                        modifier = Modifier.weight(1f),
                                        livePreview = {
                                            Text("$modeLabel · ${appearance.typefaceStyle.name.lowercase().replaceFirstChar { it.uppercase() }}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // ── SECTION 2: LAYOUT & SORTING ──
                            if (matches("Layout", "Navigation and buttons", "Search bar", "Categories", "Personal")) {
                                TideSettingsSectionLabel(stringResource(R.string.section_layout))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    TideBentoTile(
                                        title = stringResource(R.string.tile_nav_buttons),
                                        description = stringResource(R.string.tile_nav_buttons_desc),
                                        icon = Icons.Rounded.Navigation,
                                        iconShape = Cookie8Shape,
                                        iconBg = MaterialTheme.colorScheme.secondary,
                                        iconOn = MaterialTheme.colorScheme.onSecondary,
                                        cardShape = appTileShape(2),
                                        onClick = { activeDetailPage = SettingsDetailPage.LAYOUT },
                                        modifier = Modifier.weight(1f),
                                        livePreview = {
                                            Text("Nav: ${appearance.navPillHeightDp}dp · FAB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    )

                                    TideBentoTile(
                                        title = stringResource(R.string.tile_categories),
                                        description = stringResource(R.string.tile_categories_desc),
                                        icon = Icons.Rounded.Category,
                                        iconShape = Clover3Shape,
                                        iconBg = otpQuad.accent,
                                        iconOn = otpQuad.onAccent,
                                        cardShape = appTileShape(3),
                                        onClick = { activeDetailPage = SettingsDetailPage.LAYOUT },
                                        modifier = Modifier.weight(1f),
                                        livePreview = {
                                            val activeCount = themeConfig?.enabledCategories?.size ?: 9
                                            Text("$activeCount active categories", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // ── SECTION 3: AUTO-DELETE & LANGUAGE ──
                            if (matches("Auto-delete", "OTP", "Spam", "Recycle bin", "Language")) {
                                TideSettingsSectionLabel(stringResource(R.string.section_autodelete_lang))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    TideBentoTile(
                                        title = stringResource(R.string.tile_autodelete),
                                        description = "OTP (${appearance.otpAutoDelete.label}), Spam (${appearance.spamAutoDelete.label})",
                                        icon = Icons.Rounded.Timer,
                                        iconShape = Sunny12Shape,
                                        iconBg = MaterialTheme.colorScheme.primary,
                                        iconOn = MaterialTheme.colorScheme.onPrimary,
                                        cardShape = appTileShape(0),
                                        onClick = { activeDetailPage = SettingsDetailPage.AUTO_DELETE_LANG },
                                        modifier = Modifier.weight(1f),
                                        livePreview = {
                                            Text("OTP: ${appearance.otpAutoDelete.label}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    )

                                    val langDisplay = when (appearance.languageCode) {
                                        "system" -> stringResource(R.string.lang_system)
                                        "en" -> "English"
                                        "es" -> "Español"
                                        "fr" -> "Français"
                                        "de" -> "Deutsch"
                                        "ru" -> "Русский"
                                        "pl" -> "Polski"
                                        "hu" -> "Magyar"
                                        "ar" -> "العربية"
                                        "it" -> "Italiano"
                                        "pt" -> "Português"
                                        else -> appearance.languageCode
                                    }
                                    TideBentoTile(
                                        title = stringResource(R.string.tile_language),
                                        description = stringResource(R.string.tile_language_desc),
                                        icon = Icons.Rounded.Language,
                                        iconShape = Cookie8Shape,
                                        iconBg = MaterialTheme.colorScheme.secondary,
                                        iconOn = MaterialTheme.colorScheme.onSecondary,
                                        cardShape = appTileShape(1),
                                        onClick = { activeDetailPage = SettingsDetailPage.AUTO_DELETE_LANG },
                                        modifier = Modifier.weight(1f),
                                        livePreview = {
                                            Text(langDisplay, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // ── SECTION 4: NOTIFICATIONS & ABOUT ──
                            if (matches("Notifications", "Dynamic island", "Island", "Pill", "Notification", "About")) {
                                TideSettingsSectionLabel("Notifications & System")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val hasOverlay = android.provider.Settings.canDrawOverlays(context)
                                    val statusText = if (islandSettings.enabled && hasOverlay) "On" else "Off"
                                    TideBentoTile(
                                        title = "Dynamic island",
                                        description = "Interactive notifications pill at the top",
                                        icon = Icons.Rounded.NotificationsActive,
                                        iconShape = Clover3Shape,
                                        iconBg = MaterialTheme.colorScheme.primary,
                                        iconOn = MaterialTheme.colorScheme.onPrimary,
                                        cardShape = appTileShape(2),
                                        onClick = { activeDetailPage = SettingsDetailPage.DYNAMIC_ISLAND },
                                        modifier = Modifier.weight(1f),
                                        livePreview = {
                                            Text(statusText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    )

                                    TideBentoTile(
                                        title = "Brome version",
                                        description = "Modern, secure SMS for Android",
                                        icon = Icons.Rounded.AutoAwesome,
                                        iconShape = Squircle4Shape,
                                        iconBg = MaterialTheme.colorScheme.tertiary,
                                        iconOn = MaterialTheme.colorScheme.onTertiary,
                                        cardShape = appTileShape(3),
                                        onClick = { com.ripple.chat.ui.components.BromeToast.show(context, "Brome v$appVersionName • Up to date") },
                                        modifier = Modifier.weight(1f),
                                        livePreview = {
                                            Text("v$appVersionName · Up to date", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(40.dp))
                            }
                        }
                    }
                } else {
                    // ─────────────────────────────────────────────────────
                    // FOCUSED DETAIL PAGE
                    // ─────────────────────────────────────────────────────
                    when (activeDetailPage) {
                        SettingsDetailPage.APPEARANCE -> AppearanceDetailPage(
                            appearance = appearance,
                            themeConfig = themeConfig,
                            bromeSettings = bromeSettings,
                            themeSettings = themeSettings,
                            coroutineScope = coroutineScope,
                            onBack = { activeDetailPage = null }
                        )
                        SettingsDetailPage.LAYOUT -> LayoutDetailPage(
                            appearance = appearance,
                            themeConfig = themeConfig,
                            bromeSettings = bromeSettings,
                            themeSettings = themeSettings,
                            coroutineScope = coroutineScope,
                            onBack = { activeDetailPage = null }
                        )
                        SettingsDetailPage.AUTO_DELETE_LANG -> AutoDeleteLangDetailPage(
                            appearance = appearance,
                            bromeSettings = bromeSettings,
                            coroutineScope = coroutineScope,
                            onConfirmAutoDelete = { msg, action ->
                                confirmationDialogMessage = msg
                                pendingAutoDeleteAction = action
                            },
                            onBack = { activeDetailPage = null }
                        )
                        SettingsDetailPage.DYNAMIC_ISLAND -> DynamicIslandDetailPage(
                            bromeSettings = bromeSettings,
                            islandSettings = islandSettings,
                            coroutineScope = coroutineScope,
                            onBack = { activeDetailPage = null }
                        )
                        null -> {}
                    }
                }
            }

            // Confirmation Dialog for Auto-Delete changes
            if (confirmationDialogMessage != null && pendingAutoDeleteAction != null) {
                AlertDialog(
                    onDismissRequest = {
                        confirmationDialogMessage = null
                        pendingAutoDeleteAction = null
                    },
                    title = {
                        Text("Confirm Deletion", fontFamily = BricolageFontFamily, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(
                            confirmationDialogMessage ?: "",
                            fontFamily = FigtreeFontFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                pendingAutoDeleteAction?.invoke()
                                confirmationDialogMessage = null
                                pendingAutoDeleteAction = null
                            },
                            shape = CircleShape
                        ) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                confirmationDialogMessage = null
                                pendingAutoDeleteAction = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}
}

// ─────────────────────────────────────────────────────────────────────────────
// "YOUR BROME" LIVE MINI-PHONE PREVIEW CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun YourBromePreviewCard(
    appearance: BromeAppearance,
    themeConfig: ThemeConfig?,
    modifier: Modifier = Modifier
) {
    val sentColor = themeConfig?.senderBubbleColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val recColor = themeConfig?.receiverBubbleColor?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(
        shape = getTideCornerShape(0, baseRadius = appearance.cornerRadius.dp, sharpRadius = (appearance.cornerRadius * 0.33f).coerceAtMost(12f).dp, keepOneSharp = appearance.keepOneSharpCorner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 116x168dp Live Mini-Phone Frame
            Surface(
                shape = RoundedCornerShape((appearance.cornerRadius * 0.6f).coerceIn(8f, 24f).dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shadowElevation = 4.dp,
                modifier = Modifier.size(width = 116.dp, height = 168.dp)
            ) {
                BromeBackground(
                    design = appearance.backgroundDesign,
                    seed = appearance.backgroundSeed,
                    washLevel = appearance.gradientWashLevel
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Mini Search Bar
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth().height(16.dp)
                        ) {
                            Box(Modifier.padding(horizontal = 6.dp), Alignment.CenterStart) {
                                Text(
                                    text = appearance.searchHintText,
                                    fontSize = 7.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Mini Bubbles
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(18.dp)
                                    .clip(
                                        when (appearance.bubbleTheme) {
                                            BubbleThemeStyle.FLAT -> RoundedCornerShape(4.dp)
                                            BubbleThemeStyle.ROUND -> RoundedCornerShape(10.dp)
                                            BubbleThemeStyle.OUTLINE -> RoundedCornerShape(6.dp)
                                            else -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 2.dp)
                                        }
                                    )
                                    .background(
                                        if (appearance.bubbleTheme == BubbleThemeStyle.OUTLINE) Color.Transparent
                                        else recColor
                                    )
                                    .then(
                                        if (appearance.bubbleTheme == BubbleThemeStyle.OUTLINE) Modifier.border(1.dp, themeConfig?.receiverBubbleColor?.let { Color(it) } ?: MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)) else Modifier
                                    )
                            )

                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(18.dp)
                                    .align(Alignment.End)
                                    .clip(
                                        when (appearance.bubbleTheme) {
                                            BubbleThemeStyle.FLAT -> RoundedCornerShape(4.dp)
                                            BubbleThemeStyle.ROUND -> RoundedCornerShape(10.dp)
                                            BubbleThemeStyle.OUTLINE -> RoundedCornerShape(6.dp)
                                            else -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomEnd = 2.dp, bottomStart = 8.dp)
                                        }
                                    )
                                    .background(
                                        if (appearance.bubbleTheme == BubbleThemeStyle.OUTLINE) Color.Transparent
                                        else sentColor
                                    )
                                    .then(
                                        if (appearance.bubbleTheme == BubbleThemeStyle.OUTLINE) Modifier.border(1.dp, sentColor, RoundedCornerShape(6.dp)) else Modifier
                                    )
                            )
                        }

                        // Mini Nav Pill & Mini FAB
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .width(68.dp)
                                    .height((appearance.navPillHeightDp * 0.28f).dp)
                            ) {}

                            Surface(
                                shape = RoundedCornerShape((appearance.fabSizeDp * 0.1f).dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size((appearance.fabSizeDp * 0.28f).dp)
                            ) {}
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right Summary Lines
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your Brome",
                    fontFamily = BricolageFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(10.dp))

                val items = listOf(
                    MaterialTheme.colorScheme.primary to "Theme: ${if (themeConfig?.useDynamicColor == true) "Dynamic" else "Custom"}",
                    MaterialTheme.colorScheme.secondary to "Font: ${appearance.typefaceStyle.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    MaterialTheme.colorScheme.tertiary to "Corners: ${appearance.cornerRadius.toInt()}dp",
                    MaterialTheme.colorScheme.primaryContainer to "Bg: ${appearance.backgroundDesign.name.lowercase().replaceFirstChar { it.uppercase() }}"
                )

                items.forEach { (color, text) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = text,
                            fontFamily = FigtreeFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DETAIL PAGES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppearanceDetailPage(
    appearance: BromeAppearance,
    themeConfig: ThemeConfig?,
    bromeSettings: BromeSettings,
    themeSettings: ThemeSettings,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(title = "Appearance", subtitle = "Bubbles, theme, font, shapes & background", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            YourBromePreviewCard(appearance = appearance, themeConfig = themeConfig)
            Spacer(modifier = Modifier.height(20.dp))

            // Block 1: Background
            SettingBlock(
                title = "Background",
                description = "Pattern and texture behind Brome",
                icon = Icons.Rounded.Wallpaper,
                iconShape = Sunny12Shape,
                cardShape = rememberTideCornerShape(0)
            ) {
                BackgroundSettingsContent(bromeSettings, appearance, coroutineScope, isSystemInDarkTheme())
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Block 2: Font style
            SettingBlock(
                title = "Font style",
                description = "Typeface and text scaling",
                icon = Icons.Rounded.TextFields,
                iconShape = Clover3Shape,
                cardShape = rememberTideCornerShape(1)
            ) {
                FontStyleContent(bromeSettings, appearance, coroutineScope)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Block 3: Chat Bubbles
            SettingBlock(
                title = "Chat bubbles",
                description = "Colors for sent and received messages",
                icon = Icons.Rounded.ChatBubble,
                iconShape = Cookie8Shape,
                cardShape = rememberTideCornerShape(2)
            ) {
                ChatsSettingsContent(themeConfig, themeSettings, bromeSettings, appearance, coroutineScope)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Block 4: Theme
            SettingBlock(
                title = "Theme",
                description = "Dynamic color, custom accents, light or dark",
                icon = Icons.Rounded.ColorLens,
                iconShape = Flower5Shape,
                cardShape = rememberTideCornerShape(3)
            ) {
                if (themeConfig != null) {
                    TideThemeSettingsContent(themeSettings, themeConfig, coroutineScope)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Block 5: Shapes and corners
            SettingBlock(
                title = "Shapes and corners",
                description = "Corner roundness and sharp corner style",
                icon = Icons.Rounded.CropSquare,
                iconShape = Squircle4Shape,
                cardShape = rememberTideCornerShape(0)
            ) {
                ShapesCornersContent(bromeSettings, themeSettings, appearance, coroutineScope)
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun LayoutDetailPage(
    appearance: BromeAppearance,
    themeConfig: ThemeConfig?,
    bromeSettings: BromeSettings,
    themeSettings: ThemeSettings,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(title = "Layout & Sorting", subtitle = "Navigation, buttons, search hint & categories", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            YourBromePreviewCard(appearance = appearance, themeConfig = themeConfig)
            Spacer(modifier = Modifier.height(20.dp))

            // Block 1: Navigation & Buttons
            SettingBlock(
                title = "Navigation and buttons",
                description = "Bottom nav pill, compose FAB, and selection bar dimensions",
                icon = Icons.Rounded.Navigation,
                iconShape = Cookie8Shape,
                cardShape = rememberTideCornerShape(1)
            ) {
                NavigationButtonsContent(bromeSettings, appearance, coroutineScope)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Block 2: Search Bar
            SettingBlock(
                title = "Search bar",
                description = "Custom hint inside search pill",
                icon = Icons.Rounded.Search,
                iconShape = Flower5Shape,
                cardShape = rememberTideCornerShape(2)
            ) {
                SearchBarSettingsContent(bromeSettings, appearance, coroutineScope)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Block 3: Categories
            SettingBlock(
                title = "Categories",
                description = "Toggle and recolor how messages get sorted",
                icon = Icons.Rounded.Category,
                iconShape = Clover3Shape,
                cardShape = rememberTideCornerShape(3)
            ) {
                CategorizationSettingsContent(themeConfig, themeSettings, bromeSettings, appearance, coroutineScope)
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun AutoDeleteLangDetailPage(
    appearance: BromeAppearance,
    bromeSettings: BromeSettings,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onConfirmAutoDelete: (String, () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(title = stringResource(R.string.section_autodelete_lang), subtitle = stringResource(R.string.tile_autodelete), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // Block 1: OTP Messages
            SettingBlock(
                title = stringResource(R.string.cat_otp) + " " + stringResource(R.string.nav_messages).lowercase(),
                description = stringResource(R.string.otp_autodelete_title),
                icon = Icons.Rounded.Timer,
                iconShape = Clover3Shape,
                cardShape = rememberTideCornerShape(0)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OtpAutoDeleteTime.values().map { it.label }.chunked(2).forEach { rowOpts ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            rowOpts.forEach { opt ->
                                val isSelected = opt == appearance.otpAutoDelete.label
                                Surface(
                                    onClick = {
                                        val time = OtpAutoDeleteTime.values().first { it.label == opt }
                                        coroutineScope.launch {
                                            bromeSettings.setOtpAutoDelete(time)
                                            AutoDeleteManager.executeAutoDelete(context, bromeSettings, forcedOtpTime = time)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.weight(1f).height(40.dp).pressScale()
                                ) {
                                    Box(Modifier.padding(horizontal = 8.dp), Alignment.Center) {
                                        Text(AppStrings.autoDeleteLabel(opt), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FigtreeFontFamily, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (rowOpts.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Block 2: Spam Messages
            SettingBlock(
                title = stringResource(R.string.spam_autodelete_title),
                description = stringResource(R.string.spam_autodelete_desc),
                icon = Icons.Rounded.Report,
                iconShape = Sunny12Shape,
                cardShape = rememberTideCornerShape(1)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpamAutoDeleteTime.values().map { it.label }.chunked(2).forEach { rowOpts ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            rowOpts.forEach { opt ->
                                val isSelected = opt == appearance.spamAutoDelete.label
                                Surface(
                                    onClick = {
                                        val time = SpamAutoDeleteTime.values().first { it.label == opt }
                                        coroutineScope.launch {
                                            bromeSettings.setSpamAutoDelete(time)
                                            AutoDeleteManager.executeAutoDelete(context, bromeSettings, forcedSpamTime = time)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.weight(1f).height(40.dp).pressScale()
                                ) {
                                    Box(Modifier.padding(horizontal = 8.dp), Alignment.Center) {
                                        Text(AppStrings.autoDeleteLabel(opt), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FigtreeFontFamily, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (rowOpts.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Block 3: Recycle Bin
            SettingBlock(
                title = stringResource(R.string.recycle_bin_title),
                description = stringResource(R.string.recycle_bin_desc),
                icon = Icons.Rounded.DeleteSweep,
                iconShape = Cookie8Shape,
                cardShape = rememberTideCornerShape(2)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrashAutoDeleteTime.values().map { it.label }.chunked(2).forEach { rowOpts ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            rowOpts.forEach { opt ->
                                val isSelected = opt == appearance.trashAutoDelete.label
                                Surface(
                                    onClick = {
                                        val time = TrashAutoDeleteTime.values().first { it.label == opt }
                                        coroutineScope.launch {
                                            bromeSettings.setTrashAutoDelete(time)
                                            AutoDeleteManager.executeAutoDelete(context, bromeSettings, forcedTrashTime = time)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.weight(1f).height(40.dp).pressScale()
                                ) {
                                    Box(Modifier.padding(horizontal = 8.dp), Alignment.Center) {
                                        Text(AppStrings.autoDeleteLabel(opt), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FigtreeFontFamily, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (rowOpts.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.starred_never_deleted),
                fontSize = 12.sp,
                fontFamily = FigtreeFontFamily,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
            )

            // Block 4: Language
            SettingBlock(
                title = stringResource(R.string.tile_language),
                description = stringResource(R.string.tile_language_desc),
                icon = Icons.Rounded.Language,
                iconShape = Cookie8Shape,
                cardShape = rememberTideCornerShape(3)
            ) {
                val languages = listOf(
                    "system" to (stringResource(R.string.lang_system) to "System default"),
                    "en" to ("English" to "English"),
                    "es" to ("Español" to "Spanish"),
                    "fr" to ("Français" to "French"),
                    "de" to ("Deutsch" to "German"),
                    "ru" to ("Русский" to "Russian"),
                    "pl" to ("Polski" to "Polish"),
                    "hu" to ("Magyar" to "Hungarian"),
                    "ar" to ("العربية" to "Arabic"),
                    "it" to ("Italiano" to "Italian"),
                    "pt" to ("Português" to "Portuguese")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    languages.chunked(2).forEach { rowLangs ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            rowLangs.forEach { (code, names) ->
                                val (nativeName, englishName) = names
                                val isSelected = code == appearance.languageCode

                                Surface(
                                    onClick = {
                                        coroutineScope.launch {
                                            bromeSettings.setLanguageCode(code)
                                            val locales = if (code == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(code)
                                            AppCompatDelegate.setApplicationLocales(locales)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier.weight(1f).height(56.dp).pressScale()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                nativeName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (nativeName != englishName) {
                                                Text(
                                                    englishName,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                coroutineScope.launch {
                                                    bromeSettings.setLanguageCode(code)
                                                    val locales = if (code == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(code)
                                                    AppCompatDelegate.setApplicationLocales(locales)
                                                }
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            if (rowLangs.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT HELPERS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(48.dp).pressScale()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                fontFamily = BricolageFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                letterSpacing = (-1).sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, fontFamily = FigtreeFontFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 64.dp))
    }
}

@Composable
fun TideBentoTile(
    title: String,
    description: String,
    icon: ImageVector,
    iconShape: Shape,
    iconBg: Color,
    iconOn: Color,
    cardShape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    livePreview: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.pressScale()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(iconShape).background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = iconOn, modifier = Modifier.size(20.dp))
                    }

                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = title,
                    fontFamily = BricolageFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            livePreview()
        }
    }
}

@Composable
fun SettingBlock(
    title: String,
    description: String,
    icon: ImageVector,
    iconShape: Shape,
    cardShape: Shape,
    controls: @Composable () -> Unit
) {
    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().pressScale()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(iconShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontFamily = BricolageFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(description, fontFamily = FigtreeFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(16.dp))

            controls()
        }
    }
}

@Composable
fun TideSettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = FigtreeFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 26.dp, bottom = 10.dp)
    )
}

@Composable
fun ColorPickerRow(selectedColor: Int?, onColorSelected: (Int?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(robustColors) { colorInt ->
            val isSelected = selectedColor == colorInt
            val color = if (colorInt == null) MaterialTheme.colorScheme.primary else Color(colorInt)

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(colorInt) },
                contentAlignment = Alignment.Center
            ) {
                if (colorInt == null) {
                    Text("D", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChatsSettingsContent(
    themeConfig: com.ripple.chat.data.ThemeConfig?,
    themeSettings: ThemeSettings,
    bromeSettings: BromeSettings,
    appearance: BromeAppearance,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Sender Bubble Color",
            fontFamily = FigtreeFontFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        ColorPickerRow(
            selectedColor = themeConfig?.senderBubbleColor,
            onColorSelected = { color ->
                coroutineScope.launch { themeSettings.setSenderBubbleColor(color) }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Receiver Bubble Color",
            fontFamily = FigtreeFontFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        ColorPickerRow(
            selectedColor = themeConfig?.receiverBubbleColor,
            onColorSelected = { color ->
                coroutineScope.launch { themeSettings.setReceiverBubbleColor(color) }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Bubble Theme Grid (2x2 grid of 84dp preview tiles)
        Text(
            text = "Bubble theme",
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))

        val styles = listOf(
            BubbleThemeStyle.TIDE to "Tide",
            BubbleThemeStyle.ROUND to "Round",
            BubbleThemeStyle.FLAT to "Flat",
            BubbleThemeStyle.OUTLINE to "Outline"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            styles.chunked(2).forEach { rowStyles ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowStyles.forEach { (style, label) ->
                        val isSelected = appearance.bubbleTheme == style
                        Surface(
                            onClick = { coroutineScope.launch { bromeSettings.setBubbleTheme(style) } },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.weight(1f).height(84.dp).pressScale()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Mini received and sent bubble preview
                                val receiverBg = themeConfig?.receiverBubbleColor?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerHighest
                                val senderBg = themeConfig?.senderBubbleColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .width(48.dp)
                                            .height(16.dp)
                                            .clip(
                                                when (style) {
                                                    BubbleThemeStyle.FLAT -> RoundedCornerShape(5.dp)
                                                    BubbleThemeStyle.ROUND -> RoundedCornerShape(18.dp)
                                                    BubbleThemeStyle.OUTLINE -> RoundedCornerShape(10.dp)
                                                    else -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 4.dp)
                                                }
                                            )
                                            .background(
                                                if (style == BubbleThemeStyle.OUTLINE) Color.Transparent
                                                else receiverBg
                                            )
                                            .then(
                                                if (style == BubbleThemeStyle.OUTLINE) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)) else Modifier
                                            )
                                    )

                                    Box(
                                        modifier = Modifier
                                            .width(48.dp)
                                            .height(16.dp)
                                            .align(Alignment.End)
                                            .clip(
                                                when (style) {
                                                    BubbleThemeStyle.FLAT -> RoundedCornerShape(5.dp)
                                                    BubbleThemeStyle.ROUND -> RoundedCornerShape(18.dp)
                                                    BubbleThemeStyle.OUTLINE -> RoundedCornerShape(10.dp)
                                                    else -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 4.dp, bottomStart = 10.dp)
                                                }
                                            )
                                            .background(
                                                if (style == BubbleThemeStyle.OUTLINE) Color.Transparent
                                                else senderBg
                                            )
                                            .then(
                                                if (style == BubbleThemeStyle.OUTLINE) Modifier.border(1.5.dp, senderBg, RoundedCornerShape(10.dp)) else Modifier
                                            )
                                    )
                                }

                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FigtreeFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TideThemeSettingsContent(
    themeSettings: ThemeSettings,
    themeConfig: com.ripple.chat.data.ThemeConfig,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Dynamic Color", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)

            Switch(
                checked = themeConfig.useDynamicColor,
                onCheckedChange = { checked -> coroutineScope.launch { themeSettings.setUseDynamicColor(checked) } },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("CUSTOM ACCENTS", fontFamily = FigtreeFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(10.dp))

        val accentColors = listOf(
            0xFFCDF564.toInt(),
            0xFF2196F3.toInt(),
            0xFF9C27B0.toInt(),
            0xFFFF8F6B.toInt()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            accentColors.forEach { colorInt ->
                val isSelected = themeConfig.customSeedColor == colorInt
                val color = Color(colorInt)

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(Cookie8Shape)
                        .background(color)
                        .clickable { coroutineScope.launch { themeSettings.setCustomSeedColor(colorInt) } }
                        .pressScale(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("LIGHT OR DARK", fontFamily = FigtreeFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val modes = listOf(
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark",
                ThemeMode.SYSTEM to "System"
            )

            modes.forEach { (mode, label) ->
                val isSelected = themeConfig.themeMode == mode
                Surface(
                    onClick = { coroutineScope.launch { themeSettings.setThemeMode(mode) } },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.height(38.dp).pressScale()
                ) {
                    Box(Modifier.padding(horizontal = 16.dp), Alignment.Center) {
                        Text(label, fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun FontStyleContent(
    bromeSettings: BromeSettings,
    appearance: BromeAppearance,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Typeface", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))

        val typefaces = listOf(
            TypefaceStyle.TIDE to "Tide",
            TypefaceStyle.ROUNDED to "Rounded",
            TypefaceStyle.CLASSIC to "Classic",
            TypefaceStyle.SERIF to "Serif",
            TypefaceStyle.MONO to "Mono",
            TypefaceStyle.SYSTEM to "System"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            typefaces.chunked(2).forEach { rowTypefaces ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowTypefaces.forEach { (style, name) ->
                        val isSelected = appearance.typefaceStyle == style
                        Surface(
                            onClick = { coroutineScope.launch { bromeSettings.setTypefaceStyle(style) } },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.weight(1f).height(68.dp).pressScale()
                        ) {
                            val previewFonts = getAppFontFamilies(style)
                            Column(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Aa", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = previewFonts.first, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                Text(name, fontSize = 11.sp, fontFamily = previewFonts.second, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Text size", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextSizeScale.values().forEach { scale ->
                val isSelected = appearance.textSizeScale == scale
                Surface(
                    onClick = { coroutineScope.launch { bromeSettings.setTextSizeScale(scale) } },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.height(38.dp).pressScale()
                ) {
                    Box(Modifier.padding(horizontal = 16.dp), Alignment.Center) {
                        Text(scale.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FigtreeFontFamily, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live preview line
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Text(
                text = "The quick brown fox jumps over the lazy dog.",
                fontSize = (14 * appearance.textSizeScale.scale).sp,
                fontFamily = FigtreeFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun ShapesCornersContent(
    bromeSettings: BromeSettings,
    themeSettings: ThemeSettings,
    appearance: BromeAppearance,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Corner radius", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${appearance.cornerRadius.toInt()}dp", fontFamily = BricolageFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = appearance.cornerRadius,
            onValueChange = { radius ->
                coroutineScope.launch {
                    bromeSettings.setCornerRadius(radius)
                    themeSettings.setCornerRadius(radius)
                }
            },
            valueRange = 8f..40f,
            steps = 31,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = listOf("Sharp" to 8f, "Soft" to 20f, "Tide" to 30f, "Round" to 40f)
            presets.forEach { (label, r) ->
                val isSelected = appearance.cornerRadius.toInt() == r.toInt()
                Surface(
                    onClick = {
                        coroutineScope.launch {
                            bromeSettings.setCornerRadius(r)
                            themeSettings.setCornerRadius(r)
                        }
                    },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.height(38.dp).pressScale()
                ) {
                    Box(Modifier.padding(horizontal = 14.dp), Alignment.Center) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FigtreeFontFamily, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Keep one sharp corner", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Cards maintain one 10dp sharp corner for Tide look", fontSize = 13.sp, fontFamily = FigtreeFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Switch(
                checked = appearance.keepOneSharpCorner,
                onCheckedChange = { keep -> coroutineScope.launch { bromeSettings.setKeepOneSharpCorner(keep) } },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun BackgroundSettingsContent(
    bromeSettings: BromeSettings,
    appearance: BromeAppearance,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    dark: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Background Design", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))

        val designs = BackgroundDesign.values().toList()

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            designs.chunked(3).forEach { rowDesigns ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    rowDesigns.forEach { design ->
                        val isSelected = appearance.backgroundDesign == design
                        val isDisabled = design == BackgroundDesign.AMOLED && !dark

                        Surface(
                            onClick = {
                                if (!isDisabled) {
                                    coroutineScope.launch { bromeSettings.setBackgroundDesign(design) }
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.weight(1f).height(92.dp).pressScale()
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                BromeBackground(
                                    design = design,
                                    seed = appearance.backgroundSeed,
                                    washLevel = GradientWashLevel.OFF,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                                modifier = Modifier.width(36.dp).height(6.dp)
                                            ) {}
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                modifier = Modifier.width(24.dp).height(6.dp)
                                            ) {}
                                        }

                                        Text(
                                            text = if (isDisabled) "Dark only" else design.label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FigtreeFontFamily,
                                            color = if (isDisabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (rowDesigns.size < 3) {
                        repeat(3 - rowDesigns.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Random Design Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Random design",
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Seed ${appearance.backgroundSeed} · each shuffle draws a new arrangement",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                onClick = {
                    val newSeed = kotlin.random.Random.nextInt(1000, 9999)
                    coroutineScope.launch { bromeSettings.setBackgroundSeed(newSeed) }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(38.dp).pressScale()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Shuffle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FigtreeFontFamily,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Gradient Wash Chip Group
        Text(
            text = "Gradient wash",
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientWashLevel.values().forEach { level ->
                val isSelected = appearance.gradientWashLevel == level
                Surface(
                    onClick = {
                        coroutineScope.launch { bromeSettings.setGradientWashLevel(level) }
                    },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.height(38.dp).pressScale()
                ) {
                    Box(Modifier.padding(horizontal = 16.dp), Alignment.Center) {
                        Text(
                            text = level.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FigtreeFontFamily,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationButtonsContent(
    bromeSettings: BromeSettings,
    appearance: BromeAppearance,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    var navHeightState by remember(appearance.navPillHeightDp) { mutableStateOf(appearance.navPillHeightDp.toFloat()) }
    var fabSizeState by remember(appearance.fabSizeDp) { mutableStateOf(appearance.fabSizeDp.toFloat()) }
    var selectionBarScaleState by remember(appearance.selectionBarScale) { mutableStateOf(appearance.selectionBarScale) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Live Preview Box
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LIVE PREVIEW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FigtreeFontFamily,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini Pill Preview
                    val previewNavHeight = navHeightState.dp
                    val previewItemHeight = (previewNavHeight - 12.dp).coerceAtLeast(28.dp)
                    val previewIconSize = (previewItemHeight * 0.48f).coerceIn(14.dp, 22.dp)

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 4.dp,
                        modifier = Modifier.height(previewNavHeight)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(width = previewItemHeight + 8.dp, height = previewItemHeight)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Message,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(previewIconSize)
                                    )
                                }
                            }
                            listOf(Icons.Rounded.Star, Icons.Rounded.Contacts, Icons.Rounded.Archive).forEach { icon ->
                                Box(
                                    modifier = Modifier.size(previewItemHeight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size((previewIconSize * 0.9f).coerceAtLeast(13.dp))
                                    )
                                }
                            }
                        }
                    }

                    // Mini FAB Preview
                    val previewFabSize = fabSizeState.dp
                    val previewFabCorner = previewFabSize * 0.36f
                    val previewFabIcon = (previewFabSize * 0.40f).coerceIn(16.dp, 24.dp)

                    Surface(
                        shape = RoundedCornerShape(previewFabCorner),
                        color = MaterialTheme.colorScheme.tertiary,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(previewFabSize)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(previewFabIcon)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selection Bar Mini Preview (Centered, shrinks towards center, stable height)
                val previewScale = selectionBarScaleState
                val previewT = ((previewScale - 0.70f) / 0.30f).coerceIn(0f, 1f)
                val previewBarH = 38.dp
                val previewBtnH = (20f + 6f * previewT).dp
                val previewPillW = previewBtnH + (7f + 3f * previewT).dp
                val previewGap = (2f + 4f * previewT).dp
                val previewPad = (4f + 4f * previewT).dp
                val previewIconSz = (10f + 5f * previewT).dp

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 3.dp,
                        modifier = Modifier.height(previewBarH)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = previewPad, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(previewGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                Icons.Rounded.SelectAll to MaterialTheme.colorScheme.surfaceContainerHighest,
                                Icons.Rounded.MarkEmailRead to MaterialTheme.colorScheme.surfaceContainerHighest,
                                Icons.Rounded.StarOutline to MaterialTheme.colorScheme.surfaceContainerHighest,
                                Icons.Rounded.Archive to MaterialTheme.colorScheme.primary,
                                Icons.Rounded.Delete to MaterialTheme.colorScheme.errorContainer
                            ).forEachIndexed { i, (icon, bg) ->
                                val isPill = i == 3
                                val tint = when (i) {
                                    3 -> MaterialTheme.colorScheme.onPrimary
                                    4 -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = bg,
                                    modifier = Modifier.size(
                                        width = if (isPill) previewPillW else previewBtnH,
                                        height = previewBtnH
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = tint,
                                            modifier = Modifier.size(previewIconSz)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. Navigation Pill Height Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Navigation pill size",
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            val pillLabel = when {
                navHeightState <= 50f -> "Compact"
                navHeightState <= 58f -> "Default"
                else -> "Large"
            }
            Text(
                "${navHeightState.toInt()}dp ($pillLabel)",
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = navHeightState,
            onValueChange = {
                navHeightState = it
                coroutineScope.launch { bromeSettings.setNavPillHeightDp(it.toInt()) }
            },
            valueRange = 44f..68f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Compose Button (FAB) Size Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Compose button size",
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            val fabLabel = when {
                fabSizeState <= 48f -> "Small"
                fabSizeState <= 56f -> "Default"
                else -> "Large"
            }
            Text(
                "${fabSizeState.toInt()}dp ($fabLabel)",
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = fabSizeState,
            onValueChange = {
                fabSizeState = it
                coroutineScope.launch { bromeSettings.setFabSizeDp(it.toInt()) }
            },
            valueRange = 42f..66f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Selection Bar Size Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Selection bar size",
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            val scalePercent = (selectionBarScaleState * 100).toInt()
            val scaleLabel = when {
                selectionBarScaleState <= 0.78f -> "Compact"
                selectionBarScaleState <= 0.90f -> "Medium"
                else -> "Default"
            }
            Text(
                "$scalePercent% ($scaleLabel)",
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = selectionBarScaleState,
            onValueChange = {
                selectionBarScaleState = it
                coroutineScope.launch { bromeSettings.setSelectionBarScale(it) }
            },
            valueRange = 0.70f..1.0f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SearchBarSettingsContent(
    bromeSettings: BromeSettings,
    appearance: BromeAppearance,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    var textValue by remember(appearance.searchHintText) { mutableStateOf(appearance.searchHintText) }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = textValue,
            onValueChange = {
                if (it.length <= 30) {
                    textValue = it
                    coroutineScope.launch {
                        bromeSettings.setSearchHintText(it.ifBlank { "Search messages" })
                    }
                }
            },
            singleLine = true,
            label = { Text("Search pill hint") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Shown inside the search pill on the inbox and contacts.",
            fontSize = 12.sp,
            fontFamily = FigtreeFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            onClick = {
                textValue = "Search messages"
                coroutineScope.launch { bromeSettings.setSearchHintText("Search messages") }
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.height(32.dp).pressScale()
        ) {
            Box(Modifier.padding(horizontal = 12.dp), Alignment.Center) {
                Text("Reset to default", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FigtreeFontFamily, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun AutoDeleteOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconShape: Shape,
    currentChoice: String,
    options: List<String>,
    onSelectOption: (String) -> Unit,
    cardShape: Shape
) {
    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().pressScale()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(iconShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontFamily = BricolageFontFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(description, fontFamily = FigtreeFontFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(2).forEach { rowOpts ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        rowOpts.forEach { opt ->
                            val isSelected = opt == currentChoice
                            Surface(
                                onClick = { onSelectOption(opt) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.weight(1f).height(40.dp).pressScale()
                            ) {
                                Box(Modifier.padding(horizontal = 8.dp), Alignment.Center) {
                                    Text(opt, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FigtreeFontFamily, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (rowOpts.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageSettingsCard(
    currentLanguageCode: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    cardShape: Shape
) {
    val languages = listOf(
        "system" to (stringResource(R.string.lang_system) to "System default"),
        "en" to ("English" to "English"),
        "es" to ("Español" to "Spanish"),
        "fr" to ("Français" to "French"),
        "de" to ("Deutsch" to "German"),
        "ru" to ("Русский" to "Russian"),
        "pl" to ("Polski" to "Polish"),
        "hu" to ("Magyar" to "Hungarian"),
        "ar" to ("العربية" to "Arabic"),
        "it" to ("Italiano" to "Italian"),
        "pt" to ("Português" to "Portuguese")
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        languages.chunked(2).forEach { rowLangs ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowLangs.forEach { (code, names) ->
                    val (nativeName, englishName) = names
                    val isSelected = code == currentLanguageCode

                    Surface(
                        onClick = { onSelectLanguage(code) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.weight(1f).height(56.dp).pressScale()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(nativeName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (nativeName != englishName) {
                                    Text(englishName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            RadioButton(selected = isSelected, onClick = { onSelectLanguage(code) }, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                if (rowLangs.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorizationSettingsContent(
    themeConfig: com.ripple.chat.data.ThemeConfig?,
    themeSettings: ThemeSettings,
    bromeSettings: BromeSettings,
    appearance: BromeAppearance,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    if (themeConfig == null) return
    val context = LocalContext.current
    val keywordsVersion by CategoryKeywordManager.keywordsVersion.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Enable Categorization", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Sort incoming messages into smart category tiles", fontSize = 13.sp, fontFamily = FigtreeFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Switch(
                checked = themeConfig.enableCategorization,
                onCheckedChange = { checked -> coroutineScope.launch { themeSettings.setEnableCategorization(checked) } },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid Layout: Columns & Rows
        Text("GRID LAYOUT", fontFamily = FigtreeFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(10.dp))

        // Columns selector (2 - 4)
        Text("Columns (${appearance.categoryGridColumns})", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..4).forEach { cols ->
                val isSelected = appearance.categoryGridColumns == cols
                Surface(
                    onClick = { coroutineScope.launch { bromeSettings.setCategoryGridColumns(cols) } },
                    shape = RoundedCornerShape(appearance.cornerRadius.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.height(36.dp).pressScale()
                ) {
                    Box(Modifier.padding(horizontal = 16.dp), Alignment.Center) {
                        Text(
                            "$cols Columns",
                            fontFamily = FigtreeFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Rows selector (1 - 3)
        Text("Rows (${appearance.categoryGridRows})", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { rows ->
                val isSelected = appearance.categoryGridRows == rows
                Surface(
                    onClick = { coroutineScope.launch { bromeSettings.setCategoryGridRows(rows) } },
                    shape = RoundedCornerShape(appearance.cornerRadius.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.height(36.dp).pressScale()
                ) {
                    Box(Modifier.padding(horizontal = 16.dp), Alignment.Center) {
                        Text(
                            if (rows == 1) "1 Row" else "$rows Rows",
                            fontFamily = FigtreeFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("ACTIVE CATEGORIES & KEYWORDS", fontFamily = FigtreeFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(10.dp))

        val allCategories = themeConfig.categoryOrder
        val customCatsMap = remember(themeConfig.customCategories) {
            themeConfig.customCategories.associateBy { it.id }
        }
        var expandedCategories by remember { mutableStateOf(setOf<String>()) }
        var showAddCategoryDialog by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            allCategories.forEachIndexed { index, catKey ->
                val isFirst = index == 0
                val isLast = index == allCategories.size - 1
                val isEnabled = themeConfig.enabledCategories.contains(catKey)
                val customCat = customCatsMap[catKey]
                val isCustom = customCat != null
                val catColorInt = themeConfig.categoryColors[catKey] ?: customCat?.color
                val customIcon = customCat?.let { com.ripple.chat.theme.CategoryIcons.getIcon(it.iconName) }
                val isUnread = catKey == "UNREAD"
                val isExpanded = expandedCategories.contains(catKey)

                val catQuad = BromeCategoryColors.getColorsForCategory(
                    categoryKey = catKey,
                    isDark = isSystemInDarkTheme(),
                    customColorInt = catColorInt,
                    customIcon = customIcon
                )
                val catDisplayName = customCat?.name ?: when (catKey) {
                    "UNREAD" -> "Unread"
                    "PERSONAL" -> "Personal"
                    "FINANCE" -> "Transactions"
                    "PROMOTIONS" -> "Promotions"
                    "OTP" -> "OTP"
                    "TICKETS" -> "Tickets"
                    "REMINDERS" -> "Reminders"
                    "SUBSCRIPTIONS" -> "Subscriptions"
                    "SHOPPING" -> "Shopping"
                    "IMPORTANT" -> "Important"
                    "SPAM" -> "Spam"
                    else -> catKey.lowercase().replaceFirstChar { it.uppercase() }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .then(
                                    if (!isUnread) {
                                        Modifier.clickable {
                                            expandedCategories = if (isExpanded) {
                                                expandedCategories - catKey
                                            } else {
                                                expandedCategories + catKey
                                            }
                                        }
                                    } else Modifier
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                // ── ARRANGEMENT CONTROLS ──
                                IconButton(
                                    onClick = {
                                        if (!isFirst) {
                                            coroutineScope.launch { themeSettings.moveCategory(index, index - 1) }
                                        }
                                    },
                                    enabled = !isFirst,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = "Move Up",
                                        tint = if (!isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Rounded.Reorder,
                                    contentDescription = "Arrangement Handle",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )

                                IconButton(
                                    onClick = {
                                        if (!isLast) {
                                            coroutineScope.launch { themeSettings.moveCategory(index, index + 1) }
                                        }
                                    },
                                    enabled = !isLast,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Move Down",
                                        tint = if (!isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(catQuad.accent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = catDisplayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FigtreeFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (isCustom) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                themeSettings.removeCustomCategory(catKey)
                                                com.ripple.chat.ui.components.BromeToast.show(context, "Removed '$catDisplayName'")
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Delete Category",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Checkbox(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        val updated = if (checked) themeConfig.enabledCategories + catKey else themeConfig.enabledCategories - catKey
                                        coroutineScope.launch { themeSettings.setEnabledCategories(updated) }
                                    }
                                )

                                if (!isUnread) {
                                    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "catArrow")
                                    IconButton(
                                        onClick = {
                                            expandedCategories = if (isExpanded) {
                                                expandedCategories - catKey
                                            } else {
                                                expandedCategories + catKey
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.rotate(rotation)
                                        )
                                    }
                                }
                            }
                        }

                        if (!isUnread) {
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                ) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )

                                    if (catKey == "PERSONAL") {
                                        Text(
                                            text = "CONTACTS ONLY",
                                            fontFamily = FigtreeFontFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Messages from numbers saved in your device's contacts will automatically appear in this category.",
                                            fontSize = 13.sp,
                                            fontFamily = FigtreeFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        // Default Keywords
                                        val defaultKeywords = remember(catKey, customCat) {
                                            customCat?.defaultKeywords ?: CategoryKeywordManager.getDefaultKeywords(catKey)
                                        }

                                        Text(
                                            text = "DEFAULT KEYWORDS",
                                            fontFamily = FigtreeFontFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            defaultKeywords.forEach { kw ->
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                                ) {
                                                    Text(
                                                        text = kw,
                                                        fontSize = 11.sp,
                                                        fontFamily = FigtreeFontFamily,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Custom Keywords (if any)
                                        val customKeywords = remember(catKey, keywordsVersion) {
                                            CategoryKeywordManager.getUserCustomKeywords(context, catKey)
                                        }

                                        if (customKeywords.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "CUSTOM SAVED KEYWORDS",
                                                fontFamily = FigtreeFontFamily,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary,
                                                letterSpacing = 0.5.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))

                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                customKeywords.forEach { kw ->
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = kw,
                                                                fontSize = 11.sp,
                                                                fontFamily = FigtreeFontFamily,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            IconButton(
                                                                onClick = {
                                                                    CategoryKeywordManager.removeKeyword(context, catKey, kw)
                                                                    com.ripple.chat.ui.components.BromeToast.show(context, "Removed '$kw'")
                                                                },
                                                                modifier = Modifier.size(20.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Close,
                                                                    contentDescription = "Remove keyword",
                                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Add Custom Keyword Text Field + Save Button
                                        var inputKeyword by remember { mutableStateOf("") }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = inputKeyword,
                                                onValueChange = { inputKeyword = it },
                                                placeholder = { Text("Add custom keyword...", fontSize = 12.sp, fontFamily = FigtreeFontFamily) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            )

                                            Button(
                                                onClick = {
                                                    val trimmed = inputKeyword.trim().lowercase()
                                                    if (trimmed.isBlank()) return@Button

                                                    val conflict = CategoryKeywordManager.findConflictCategory(context, catKey, trimmed)
                                                    if (conflict != null) {
                                                        com.ripple.chat.ui.components.BromeToast.show(
                                                            context,
                                                            "Conflicts with ${conflict.lowercase().replaceFirstChar { it.uppercase() }}"
                                                        )
                                                        return@Button
                                                    }

                                                    if (CategoryKeywordManager.existsInCurrentCategory(context, catKey, trimmed)) {
                                                        com.ripple.chat.ui.components.BromeToast.show(
                                                            context,
                                                            "Keyword already exists"
                                                        )
                                                        return@Button
                                                    }

                                                    val added = CategoryKeywordManager.addKeyword(context, catKey, trimmed)
                                                    if (added) {
                                                        com.ripple.chat.ui.components.BromeToast.show(
                                                            context,
                                                            "Saved '$trimmed'"
                                                        )
                                                        inputKeyword = ""
                                                    }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.height(48.dp)
                                            ) {
                                                Text(
                                                    "Save",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FigtreeFontFamily
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
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── ADD CUSTOM CATEGORY BUTTON ──
        Surface(
            onClick = { showAddCategoryDialog = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().pressScale()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Custom Category",
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ── ADD CATEGORY DIALOG ──
        if (showAddCategoryDialog) {
            var newCatName by remember { mutableStateOf("") }
            val colorPalette = listOf(
                0xFF2196F3.toInt(), // Blue
                0xFF4CAF50.toInt(), // Green
                0xFF9C27B0.toInt(), // Purple
                0xFFFF9800.toInt(), // Orange
                0xFF009688.toInt(), // Teal
                0xFFE91E63.toInt(), // Pink
                0xFFFFC107.toInt(), // Amber
                0xFF00BCD4.toInt(), // Cyan
                0xFF3F51B5.toInt(), // Indigo
                0xFFFF5722.toInt(), // Deep Orange
                0xFF8BC34A.toInt(), // Lime
                0xFF795548.toInt()  // Brown
            )
            var selectedColor by remember { mutableStateOf(colorPalette[0]) }
            var selectedIconName by remember { mutableStateOf("Work") }
            var newKeywordsText by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddCategoryDialog = false },
                title = {
                    Text("Add New Category", fontFamily = BricolageFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Category Name
                        OutlinedTextField(
                            value = newCatName,
                            onValueChange = { newCatName = it },
                            label = { Text("Category Name", fontFamily = FigtreeFontFamily) },
                            placeholder = { Text("e.g. Work, College, Bills", fontFamily = FigtreeFontFamily) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Color Picker
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("DEFAULT COLOR", fontFamily = FigtreeFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                colorPalette.forEach { colorInt ->
                                    val isColorSelected = selectedColor == colorInt
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorInt))
                                            .clickable { selectedColor = colorInt },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isColorSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Icon Picker
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("HOMEPAGE CARD ICON", fontFamily = FigtreeFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                com.ripple.chat.theme.CategoryIcons.AVAILABLE_ICONS.forEach { (name, icon) ->
                                    val isIconSelected = selectedIconName == name
                                    Surface(
                                        onClick = { selectedIconName = name },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isIconSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = if (isIconSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = name,
                                                tint = if (isIconSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = name,
                                                fontSize = 11.sp,
                                                fontFamily = FigtreeFontFamily,
                                                fontWeight = if (isIconSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isIconSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Keywords Input
                        OutlinedTextField(
                            value = newKeywordsText,
                            onValueChange = { newKeywordsText = it },
                            label = { Text("Default Keywords (comma separated)", fontFamily = FigtreeFontFamily) },
                            placeholder = { Text("e.g. office, slack, meeting, standup", fontFamily = FigtreeFontFamily) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmedName = newCatName.trim()
                            if (trimmedName.isBlank()) {
                                com.ripple.chat.ui.components.BromeToast.show(context, "Please enter a category name")
                                return@Button
                            }
                            val id = "CUSTOM_" + trimmedName.uppercase().replace(Regex("[^A-Z0-9_]"), "_")
                            if (themeConfig.customCategories.any { it.id == id }) {
                                com.ripple.chat.ui.components.BromeToast.show(context, "Category already exists")
                                return@Button
                            }

                            val rawKeywords = newKeywordsText.split(",")
                                .map { it.trim().lowercase() }
                                .filter { it.isNotBlank() }
                                .distinct()

                            for (kw in rawKeywords) {
                                val conflict = CategoryKeywordManager.findConflictCategory(context, id, kw)
                                if (conflict != null) {
                                    com.ripple.chat.ui.components.BromeToast.show(
                                        context,
                                        "Keyword '$kw' conflicts with ${conflict.lowercase().replaceFirstChar { it.uppercase() }}"
                                    )
                                    return@Button
                                }
                            }

                            val newCategory = com.ripple.chat.data.CustomCategory(
                                id = id,
                                name = trimmedName,
                                color = selectedColor,
                                iconName = selectedIconName,
                                defaultKeywords = rawKeywords
                            )

                            coroutineScope.launch {
                                themeSettings.addCustomCategory(newCategory)
                                com.ripple.chat.ui.components.BromeToast.show(context, "Category '$trimmedName' created!")
                                showAddCategoryDialog = false
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Create Category", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false }) {
                        Text("Cancel", fontFamily = FigtreeFontFamily)
                    }
                }
            )
        }
    }
}
