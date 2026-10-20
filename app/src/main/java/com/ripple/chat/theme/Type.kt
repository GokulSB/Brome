package com.ripple.chat.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ripple.chat.data.TypefaceStyle

private fun createTypefaceFont(name: String, style: Int, fallback: FontFamily): FontFamily {
    return try {
        FontFamily(android.graphics.Typeface.create(name, style))
    } catch (e: Throwable) {
        fallback
    }
}

// 1. TIDE (Default - expressive casual title + modern sans body)
val TideTitleFontFamily: FontFamily by lazy {
    createTypefaceFont("casual", android.graphics.Typeface.BOLD, FontFamily.SansSerif)
}
val TideBodyFontFamily: FontFamily = FontFamily.SansSerif

val DefaultBricolageFontFamily: FontFamily get() = TideTitleFontFamily
val DefaultFigtreeFontFamily: FontFamily get() = TideBodyFontFamily

// 2. ROUNDED (Friendly, soft rounded sans-serif)
val RoundedTitleFontFamily: FontFamily by lazy {
    createTypefaceFont("sans-serif-rounded", android.graphics.Typeface.BOLD, FontFamily.SansSerif)
}
val RoundedBodyFontFamily: FontFamily by lazy {
    createTypefaceFont("sans-serif-rounded", android.graphics.Typeface.NORMAL, FontFamily.SansSerif)
}

// 3. CLASSIC (Clean, geometric neutral sans-serif)
val ClassicTitleFontFamily = FontFamily.SansSerif
val ClassicBodyFontFamily = FontFamily.SansSerif

// 4. SERIF (Classic editorial book serif)
val SerifTitleFontFamily = FontFamily.Serif
val SerifBodyFontFamily = FontFamily.Serif

// 5. MONO (Fixed-width terminal code font)
val MonoTitleFontFamily = FontFamily.Monospace
val MonoBodyFontFamily = FontFamily.Monospace

// 6. SYSTEM (Device default)
val SystemTitleFontFamily = FontFamily.Default
val SystemBodyFontFamily = FontFamily.Default

fun getAppFontFamilies(style: TypefaceStyle): Pair<FontFamily, FontFamily> {
    return when (style) {
        TypefaceStyle.TIDE -> Pair(TideTitleFontFamily, TideBodyFontFamily)
        TypefaceStyle.ROUNDED -> Pair(RoundedTitleFontFamily, RoundedBodyFontFamily)
        TypefaceStyle.CLASSIC -> Pair(ClassicTitleFontFamily, ClassicBodyFontFamily)
        TypefaceStyle.SERIF -> Pair(SerifTitleFontFamily, SerifBodyFontFamily)
        TypefaceStyle.MONO -> Pair(MonoTitleFontFamily, MonoBodyFontFamily)
        TypefaceStyle.SYSTEM -> Pair(SystemTitleFontFamily, SystemBodyFontFamily)
    }
}

val LocalAppFontFamilies = staticCompositionLocalOf {
    Pair(TideTitleFontFamily, TideBodyFontFamily)
}

val BricolageFontFamily: FontFamily
    @Composable
    get() = LocalAppFontFamilies.current.first

val FigtreeFontFamily: FontFamily
    @Composable
    get() = LocalAppFontFamilies.current.second

val PlusJakartaSansFontFamily: FontFamily
    @Composable
    get() = LocalAppFontFamilies.current.second

fun getAppTypography(titleFont: FontFamily, bodyFont: FontFamily): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontFamily = titleFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 44.sp,
            lineHeight = 50.sp,
            letterSpacing = (-1.5).sp
        ),
        displayMedium = TextStyle(
            fontFamily = titleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            letterSpacing = (-1).sp
        ),
        displaySmall = TextStyle(
            fontFamily = titleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = 0.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = titleFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            letterSpacing = (-1).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = titleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.5).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = titleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = titleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = titleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        ),
        titleSmall = TextStyle(
            fontFamily = titleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.25.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.25.sp
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.sp
        ),
        labelSmall = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.sp
        )
    )
}

val TideTypography = getAppTypography(DefaultBricolageFontFamily, DefaultFigtreeFontFamily)
