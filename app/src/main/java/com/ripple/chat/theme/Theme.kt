package com.ripple.chat.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ripple.chat.data.BromeAppearance
import com.ripple.chat.data.ThemeConfig
import com.ripple.chat.data.ThemeMode
import com.ripple.chat.data.ThemeSettings

val LocalThemeConfig = compositionLocalOf<ThemeConfig?> { null }
val LocalBromeAppearance = compositionLocalOf { BromeAppearance() }

private fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
        hsv
    )
    return hsv
}

private fun hsvColor(h: Float, s: Float, v: Float): Color {
    val hNorm = (h % 360f + 360f) % 360f
    val sClamped = s.coerceIn(0f, 1f)
    val vClamped = v.coerceIn(0f, 1f)
    val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(hNorm, sClamped, vClamped))
    return Color(colorInt)
}

fun createSeedColorScheme(seed: Color, darkTheme: Boolean): ColorScheme {
    val hsv = colorToHsv(seed)
    val h = hsv[0]
    val s = hsv[1].coerceAtLeast(0.4f)
    val secH = (h + 25f) % 360f
    val tertH = (h + 60f) % 360f

    return if (darkTheme) {
        val primary = hsvColor(h, (s * 0.75f).coerceIn(0.3f, 0.7f), 0.90f)
        val onPrimary = hsvColor(h, s, 0.15f)
        val primaryContainer = hsvColor(h, (s * 0.9f).coerceIn(0.4f, 0.85f), 0.30f)
        val onPrimaryContainer = hsvColor(h, 0.25f, 0.95f)

        val secondary = hsvColor(secH, (s * 0.6f).coerceIn(0.25f, 0.6f), 0.85f)
        val onSecondary = hsvColor(secH, s, 0.15f)
        val secondaryContainer = hsvColor(secH, (s * 0.8f).coerceIn(0.35f, 0.75f), 0.28f)
        val onSecondaryContainer = hsvColor(secH, 0.2f, 0.95f)

        val tertiary = hsvColor(tertH, (s * 0.7f).coerceIn(0.3f, 0.7f), 0.90f)
        val onTertiary = hsvColor(tertH, s, 0.15f)
        val tertiaryContainer = hsvColor(tertH, (s * 0.85f).coerceIn(0.35f, 0.8f), 0.30f)
        val onTertiaryContainer = hsvColor(tertH, 0.25f, 0.95f)

        val background = hsvColor(h, 0.12f, 0.08f)
        val surface = hsvColor(h, 0.10f, 0.12f)
        val surfaceContainer = hsvColor(h, 0.12f, 0.15f)
        val surfaceContainerHigh = hsvColor(h, 0.14f, 0.19f)
        val surfaceContainerHighest = hsvColor(h, 0.15f, 0.23f)
        val surfaceVariant = surfaceContainer
        val onBackground = Color(0xFFE6EDE7)
        val onSurface = Color(0xFFE6EDE7)
        val onSurfaceVariant = Color(0xFFC2CCC4)
        val outline = Color(0xFF8C968F)
        val outlineVariant = Color(0xFF3B433E)

        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline,
            outlineVariant = outlineVariant,
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005)
        )
    } else {
        val primary = hsvColor(h, s.coerceIn(0.5f, 0.95f), 0.45f)
        val onPrimary = Color.White
        val primaryContainer = hsvColor(h, (s * 0.35f).coerceIn(0.15f, 0.45f), 0.92f)
        val onPrimaryContainer = hsvColor(h, s, 0.20f)

        val secondary = hsvColor(secH, (s * 0.7f).coerceIn(0.4f, 0.85f), 0.42f)
        val onSecondary = Color.White
        val secondaryContainer = hsvColor(secH, (s * 0.3f).coerceIn(0.12f, 0.4f), 0.92f)
        val onSecondaryContainer = hsvColor(secH, s, 0.20f)

        val tertiary = hsvColor(tertH, (s * 0.75f).coerceIn(0.45f, 0.9f), 0.45f)
        val onTertiary = Color.White
        val tertiaryContainer = hsvColor(tertH, (s * 0.35f).coerceIn(0.15f, 0.45f), 0.93f)
        val onTertiaryContainer = hsvColor(tertH, s, 0.20f)

        val background = hsvColor(h, 0.04f, 0.98f)
        val surface = Color.White
        val surfaceContainer = hsvColor(h, 0.07f, 0.95f)
        val surfaceContainerHigh = hsvColor(h, 0.09f, 0.91f)
        val surfaceContainerHighest = hsvColor(h, 0.11f, 0.87f)
        val surfaceVariant = surfaceContainer
        val onBackground = Color(0xFF161C18)
        val onSurface = Color(0xFF161C18)
        val onSurfaceVariant = Color(0xFF48514A)
        val outline = Color(0xFF727B74)
        val outlineVariant = Color(0xFFC5CEC7)

        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline,
            outlineVariant = outlineVariant,
            error = Color(0xFFBA1A1A),
            onError = Color.White
        )
    }
}

// Meadow Fallbacks
val MeadowDarkColorScheme = createSeedColorScheme(Color(0xFFCDF564), true)
val MeadowLightColorScheme = createSeedColorScheme(Color(0xFF3A6B10), false)

@Composable
fun BromeTheme(
    dynamicColorEnabled: Boolean = LocalThemeConfig.current?.useDynamicColor ?: true,
    darkTheme: Boolean = when (LocalThemeConfig.current?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    },
    seedColor: Int = LocalThemeConfig.current?.customSeedColor ?: ThemeSettings.DEFAULT_COLOR,
    appearance: BromeAppearance = LocalBromeAppearance.current,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> createSeedColorScheme(Color(seedColor), darkTheme)
    }

    val (titleFont, bodyFont) = getAppFontFamilies(appearance.typefaceStyle)
    val typography = remember(titleFont, bodyFont) { getAppTypography(titleFont, bodyFont) }

    val cornerRadius = appearance.cornerRadius
    val shapes = remember(cornerRadius) {
        Shapes(
            extraSmall = RoundedCornerShape((cornerRadius * 0.2f).coerceAtLeast(4f).dp),
            small = RoundedCornerShape((cornerRadius * 0.4f).coerceAtLeast(8f).dp),
            medium = RoundedCornerShape(cornerRadius.dp),
            large = RoundedCornerShape((cornerRadius * 1.2f).coerceAtLeast(16f).dp),
            extraLarge = RoundedCornerShape((cornerRadius * 1.5f).coerceAtLeast(24f).dp)
        )
    }

    val currentDensity = LocalDensity.current
    val scaledDensity = remember(currentDensity, appearance.textSizeScale) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * appearance.textSizeScale.scale
        )
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalAppFontFamilies provides Pair(titleFont, bodyFont)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
