package com.ripple.chat.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ripple.chat.data.BromeAppearance
import com.ripple.chat.data.ThemeMode
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Variant B Dark Tokens (Default)
val VariantBDarkBackground = Color(0xFF1A1210)
val VariantBDarkSurface = Color(0xFF261B18)
val VariantBDarkContainer = Color(0xFF33241F)
val VariantBDarkContainerHigh = Color(0xFF3F2E29)
val VariantBDarkText = Color(0xFFF2DFDA)
val VariantBDarkTextVariant = Color(0xFFD8C2BC)
val VariantBDarkMuted = Color(0xFFA08C86)
val VariantBDarkDivider = Color(0xFF3F2E29)
val VariantBDarkDecor = Color(0xFF5E3226)

// Variant B Light Fallback Tokens
val VariantBLightBackground = Color(0xFFFFF8F6)
val VariantBLightSurface = Color(0xFFF8EBE7)
val VariantBLightContainer = Color(0xFFF3DFDA)
val VariantBLightContainerHigh = Color(0xFFEBD6D0)
val VariantBLightText = Color(0xFF231917)
val VariantBLightTextVariant = Color(0xFF53433F)
val VariantBLightMuted = Color(0xFF85736E)

// Accent Pairs (container / on-container)
data class VariantBAccentPair(val container: Color, val onContainer: Color)

val VariantBPeach = VariantBAccentPair(Color(0xFFFFB4A0), Color(0xFF4A1A0D))
val VariantBYellow = VariantBAccentPair(Color(0xFFF9E2A2), Color(0xFF3B2F00))
val VariantBGreen = VariantBAccentPair(Color(0xFFD8E8C8), Color(0xFF1F2E14))
val VariantBRose = VariantBAccentPair(Color(0xFFF2B8C8), Color(0xFF4A1730))
val VariantBError = VariantBAccentPair(Color(0xFF93000A), Color(0xFFFFDAD6))

val VariantBAccentPairs = listOf(VariantBPeach, VariantBYellow, VariantBGreen, VariantBRose)

// Expressive Custom Avatar Shapes
class Cookie10Shape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = minOf(cx, cy)
        val numLobes = 10
        val baseR = maxRadius * 0.90f
        val ampR = maxRadius * 0.10f

        val steps = 180
        for (i in 0..steps) {
            val angle = (2.0 * Math.PI * i / steps).toFloat()
            val r = baseR + ampR * cos(numLobes * angle)
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

class Flower6Shape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = minOf(cx, cy)
        val numLobes = 6
        val baseR = maxRadius * 0.86f
        val ampR = maxRadius * 0.14f

        val steps = 180
        for (i in 0..steps) {
            val angle = (2.0 * Math.PI * i / steps).toFloat()
            val r = baseR + ampR * cos(numLobes * angle)
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

fun getVariantBAvatarShapeAndAccent(name: String): Pair<Shape, VariantBAccentPair> {
    val hash = abs(name.hashCode())
    val shape: Shape = when (hash % 3) {
        0 -> Cookie10Shape()
        1 -> Flower6Shape()
        else -> RoundedCornerShape(18.dp)
    }
    val accent = VariantBAccentPairs[hash % VariantBAccentPairs.size]
    return Pair(shape, accent)
}

// Asymmetric Corner Shapes
val AsymmetricPillShape = RoundedCornerShape(topStart = 12.dp, topEnd = 16.dp, bottomEnd = 12.dp, bottomStart = 16.dp)

val VariantBDarkColorScheme = darkColorScheme(
    background = VariantBDarkBackground,
    surface = VariantBDarkSurface,
    surfaceVariant = VariantBDarkContainer,
    surfaceContainer = VariantBDarkContainer,
    surfaceContainerHigh = VariantBDarkContainerHigh,
    primary = VariantBPeach.container,
    onPrimary = VariantBPeach.onContainer,
    primaryContainer = VariantBPeach.container,
    onPrimaryContainer = VariantBPeach.onContainer,
    secondary = VariantBYellow.container,
    onSecondary = VariantBYellow.onContainer,
    tertiary = VariantBGreen.container,
    onTertiary = VariantBGreen.onContainer,
    onBackground = VariantBDarkText,
    onSurface = VariantBDarkText,
    onSurfaceVariant = VariantBDarkTextVariant,
    outline = VariantBDarkMuted,
    outlineVariant = VariantBDarkDivider,
    error = VariantBError.container,
    onError = VariantBError.onContainer
)

val VariantBLightColorScheme = lightColorScheme(
    background = VariantBLightBackground,
    surface = VariantBLightSurface,
    surfaceVariant = VariantBLightContainer,
    surfaceContainer = VariantBLightContainer,
    surfaceContainerHigh = VariantBLightContainerHigh,
    primary = VariantBPeach.container,
    onPrimary = VariantBPeach.onContainer,
    primaryContainer = VariantBPeach.container,
    onPrimaryContainer = VariantBPeach.onContainer,
    secondary = VariantBYellow.container,
    onSecondary = VariantBYellow.onContainer,
    tertiary = VariantBGreen.container,
    onTertiary = VariantBGreen.onContainer,
    onBackground = VariantBLightText,
    onSurface = VariantBLightText,
    onSurfaceVariant = VariantBLightTextVariant,
    outline = VariantBLightMuted,
    outlineVariant = VariantBLightContainerHigh,
    error = VariantBError.container,
    onError = VariantBError.onContainer
)

val VariantBTypography: Typography
    @Composable
    get() {
        val font = LocalAppFontFamilies.current.second
        return remember(font) {
            Typography(
                headlineLarge = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 44.sp,
                    lineHeight = 50.sp,
                    letterSpacing = (-1.5).sp
                ),
                headlineMedium = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.5).sp
                ),
                titleLarge = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    letterSpacing = 0.sp
                ),
                titleMedium = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.sp
                ),
                bodyLarge = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.25.sp
                ),
                bodyMedium = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.25.sp
                ),
                bodySmall = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.25.sp
                ),
                labelMedium = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.5.sp
                ),
                labelSmall = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }

@Composable
fun BromeVariantBTheme(
    darkTheme: Boolean = when (LocalThemeConfig.current?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    },
    appearance: BromeAppearance = LocalBromeAppearance.current,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VariantBDarkColorScheme else VariantBLightColorScheme
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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VariantBTypography,
        shapes = shapes,
        content = content
    )
}
