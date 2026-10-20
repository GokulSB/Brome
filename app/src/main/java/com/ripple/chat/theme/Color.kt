package com.ripple.chat.theme

import androidx.compose.ui.graphics.Color

// Expressive Light Theme Tokens
val ExpressiveLightBackground = Color(0xFFFFF8F6)
val ExpressiveLightSurface = Color(0xFFFFF8F6)
val ExpressiveLightSurfaceContainer = Color(0xFFF8EBE7)
val ExpressiveLightSurfaceContainerHigh = Color(0xFFF3DFDA)
val ExpressiveLightPrimaryContainer = Color(0xFFFFDBD0)
val ExpressiveLightOnPrimaryContainer = Color(0xFF3A0B01)
val ExpressiveLightPrimary = Color(0xFF8F4C38)
val ExpressiveLightOnPrimary = Color(0xFFFFFFFF)
val ExpressiveLightOnSurface = Color(0xFF231917)
val ExpressiveLightOnSurfaceVariant = Color(0xFF53433F)
val ExpressiveLightOutline = Color(0xFF85736E)

// Expressive Dark Theme Tokens
val ExpressiveDarkBackground = Color(0xFF1E1513)
val ExpressiveDarkSurface = Color(0xFF1E1513)
val ExpressiveDarkSurfaceContainer = Color(0xFF2E2321)
val ExpressiveDarkSurfaceContainerHigh = Color(0xFF3A2C29)
val ExpressiveDarkPrimaryContainer = Color(0xFF5E3226)
val ExpressiveDarkOnPrimaryContainer = Color(0xFFFFDBD0)
val ExpressiveDarkPrimary = Color(0xFFFFB4A0)
val ExpressiveDarkOnPrimary = Color(0xFF4A1A0D)
val ExpressiveDarkOnSurface = Color(0xFFF2DFDA)
val ExpressiveDarkOnSurfaceVariant = Color(0xFFD8C2BC)
val ExpressiveDarkOutline = Color(0xFFA08C86)

// Fixed Status Colors
val BadgeGreen = Color(0xFF25D366)
val BadgeRed = Color(0xFFEA3323)
val OnlineGreen = Color(0xFF4CAF50)

// Avatar Accent Colors (Light / Dark)
data class AvatarColorPair(val bgLight: Color, val bgDark: Color)

val AvatarAccentPairs = listOf(
    AvatarColorPair(Color(0xFFFFB4A0), Color(0xFF7A3A2A)), // Peach
    AvatarColorPair(Color(0xFFF9E2A2), Color(0xFF5C4B14)), // Yellow
    AvatarColorPair(Color(0xFFD8E8C8), Color(0xFF3D4D2E)), // Green
    AvatarColorPair(Color(0xFFFFDBD0), Color(0xFF4F2E26))  // Soft Peach
)

val AvatarLetterLight = Color(0xFF3A0B01)
val AvatarLetterDark = Color(0xFFFFE1D8)

// Decorative Shape Colors
val DecorPeachLight = Color(0xFFFFDBD0)
val DecorPeachDark = Color(0xFF5E3226)
val DecorYellowLight = Color(0xFFF9E2A2)
val DecorYellowDark = Color(0xFF5C4B14)
val DecorGreenLight = Color(0xFFD8E8C8)
val DecorGreenDark = Color(0xFF3D4D2E)

// Legacy / Settings Fallbacks
val SettingsChatsAccent = Color(0xFF8F4C38)
val SettingsChatsAccentBg = Color(0xFF2E2321)
val SettingsCategorizationAccent = Color(0xFFF9E2A2)
val SettingsCategorizationAccentBg = Color(0xFF3A2C29)
val SettingsThemeAccent = Color(0xFFFFB4A0)
val SettingsThemeAccentBg = Color(0xFF5E3226)
val SettingsMutedAccent = Color(0xFFA08C86)
val SettingsMutedAccentBg = Color(0xFF2E2321)
