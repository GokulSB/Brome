package com.ripple.chat.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ripple.chat.data.SmsCategory
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Expressive Polygon Shape Generator
class TidePolygonShape(
    val lobes: Int,
    val amplitude: Float = 0.10f,
    val baseRatio: Float = 0.90f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = minOf(cx, cy)
        val baseR = maxR * baseRatio
        val ampR = maxR * amplitude

        val steps = 180
        for (i in 0..steps) {
            val angle = (2.0 * Math.PI * i / steps).toFloat()
            val r = baseR + ampR * cos(lobes * angle)
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

val Cookie8Shape = TidePolygonShape(lobes = 8, amplitude = 0.09f, baseRatio = 0.91f)
val Flower5Shape = TidePolygonShape(lobes = 5, amplitude = 0.13f, baseRatio = 0.87f)
val Clover3Shape = TidePolygonShape(lobes = 3, amplitude = 0.18f, baseRatio = 0.82f)
val Sunny12Shape = TidePolygonShape(lobes = 12, amplitude = 0.07f, baseRatio = 0.93f)
val Squircle4Shape = TidePolygonShape(lobes = 4, amplitude = 0.12f, baseRatio = 0.88f)

// Corner Language Helper: 30dp corners with ONE corner at 10dp
enum class SharpCornerPosition { BOTTOM_START, BOTTOM_END, TOP_END, TOP_START }

fun getTideCornerShape(
    index: Int,
    baseRadius: Dp = 30.dp,
    sharpRadius: Dp = 10.dp,
    keepOneSharp: Boolean = true
): Shape {
    if (!keepOneSharp) return RoundedCornerShape(baseRadius)
    val effectiveSharp = if (baseRadius < sharpRadius) baseRadius else sharpRadius
    val pos = SharpCornerPosition.values()[abs(index) % 4]
    return when (pos) {
        SharpCornerPosition.BOTTOM_START -> RoundedCornerShape(topStart = baseRadius, topEnd = baseRadius, bottomEnd = baseRadius, bottomStart = effectiveSharp)
        SharpCornerPosition.BOTTOM_END -> RoundedCornerShape(topStart = baseRadius, topEnd = baseRadius, bottomEnd = effectiveSharp, bottomStart = baseRadius)
        SharpCornerPosition.TOP_END -> RoundedCornerShape(topStart = baseRadius, topEnd = effectiveSharp, bottomEnd = baseRadius, bottomStart = baseRadius)
        SharpCornerPosition.TOP_START -> RoundedCornerShape(topStart = effectiveSharp, topEnd = baseRadius, bottomEnd = baseRadius, bottomStart = baseRadius)
    }
}

@Composable
fun rememberTideCornerShape(
    index: Int = 0,
    baseRadius: Dp = LocalBromeAppearance.current.cornerRadius.dp,
    sharpRadius: Dp = (LocalBromeAppearance.current.cornerRadius * 0.33f).coerceAtMost(10f).dp,
    keepOneSharp: Boolean = LocalBromeAppearance.current.keepOneSharpCorner
): Shape {
    return remember(index, baseRadius, sharpRadius, keepOneSharp) {
        getTideCornerShape(index, baseRadius, sharpRadius, keepOneSharp)
    }
}

// Fixed Category Colors Data Quad
data class CategoryColorQuad(
    val container: Color,
    val accent: Color,
    val onContainer: Color,
    val onAccent: Color,
    val shape: Shape,
    val icon: ImageVector
)

object CategoryIcons {
    val AVAILABLE_ICONS = listOf(
        "Work" to Icons.Rounded.Work,
        "School" to Icons.Rounded.School,
        "Family" to Icons.Rounded.Favorite,
        "Fitness" to Icons.Rounded.FitnessCenter,
        "Travel" to Icons.Rounded.Flight,
        "Food" to Icons.Rounded.Restaurant,
        "Health" to Icons.Rounded.LocalHospital,
        "Home" to Icons.Rounded.Home,
        "Star" to Icons.Rounded.Star,
        "Tag" to Icons.Rounded.LocalOffer,
        "Folder" to Icons.Rounded.Folder,
        "Receipt" to Icons.Rounded.Receipt,
        "Car" to Icons.Rounded.DirectionsCar,
        "Gaming" to Icons.Rounded.SportsEsports,
        "Music" to Icons.Rounded.MusicNote,
        "Book" to Icons.Rounded.MenuBook
    )

    fun getIcon(name: String): ImageVector {
        return AVAILABLE_ICONS.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
            ?: Icons.Rounded.Category
    }
}

object BromeCategoryColors {
    fun getColors(category: SmsCategory, isDark: Boolean, customColorInt: Int? = null): CategoryColorQuad {
        return getColorsForCategory(category.name, isDark, customColorInt)
    }

    fun getColorsForCategory(
        categoryKey: String,
        isDark: Boolean,
        customColorInt: Int? = null,
        customIcon: ImageVector? = null,
        customShape: Shape? = null
    ): CategoryColorQuad {
        val baseQuad = when (categoryKey.uppercase()) {
            "UNREAD" -> if (isDark)
                CategoryColorQuad(Color(0xFF283593).copy(alpha = 0.45f), Color(0xFF9FA8DA), Color(0xFFE8EAF6), Color(0xFF1A237E), Squircle4Shape, Icons.Rounded.MarkEmailUnread)
            else
                CategoryColorQuad(Color(0xFFE8EAF6), Color(0xFF3F51B5), Color(0xFF1A237E), Color.White, Squircle4Shape, Icons.Rounded.MarkEmailUnread)

            "PERSONAL" -> if (isDark)
                CategoryColorQuad(Color(0xFF1B3D2F), Color(0xFF81C784), Color(0xFFD4EED8), Color(0xFF1B3D2F), Squircle4Shape, Icons.Rounded.Person)
            else
                CategoryColorQuad(Color(0xFFE8F5E9), Color(0xFF2E7D32), Color(0xFF1B5E20), Color.White, Squircle4Shape, Icons.Rounded.Person)

            "IMPORTANT" -> if (isDark)
                CategoryColorQuad(Color(0xFF5D2510), Color(0xFFFF8A65), Color(0xFFFFDBCF), Color(0xFF5D2510), Sunny12Shape, Icons.Rounded.Star)
            else
                CategoryColorQuad(Color(0xFFFFDBCF), Color(0xFFD84315), Color(0xFF3E1200), Color.White, Sunny12Shape, Icons.Rounded.Star)

            "OTP" -> if (isDark)
                CategoryColorQuad(Color(0xFF3A3369), Color(0xFFB8A9FF), Color(0xFFE3DDFF), Color(0xFF3A3369), Clover3Shape, Icons.Rounded.VpnKey)
            else
                CategoryColorQuad(Color(0xFFE3D6FF), Color(0xFF6B3FD4), Color(0xFF2A0F66), Color(0xFFFFFFFF), Clover3Shape, Icons.Rounded.VpnKey)

            "FINANCE" -> if (isDark)
                CategoryColorQuad(Color(0xFF123F3A), Color(0xFF6FE0D0), Color(0xFFC8F5EF), Color(0xFF123F3A), Cookie8Shape, Icons.Rounded.AccountBalance)
            else
                CategoryColorQuad(Color(0xFFCDEFD6), Color(0xFF1F7A3E), Color(0xFF0B3A1B), Color(0xFFFFFFFF), Cookie8Shape, Icons.Rounded.AccountBalance)

            "PROMOTIONS", "PROMOTIONAL" -> if (isDark)
                CategoryColorQuad(Color(0xFF5A2A1C), Color(0xFFFF8F6B), Color(0xFFFFD9CC), Color(0xFF5A2A1C), Flower5Shape, Icons.Rounded.LocalOffer)
            else
                CategoryColorQuad(Color(0xFFFFDFC9), Color(0xFFB4510F), Color(0xFF4A1E00), Color(0xFFFFFFFF), Flower5Shape, Icons.Rounded.LocalOffer)

            "SPAM" -> if (isDark)
                CategoryColorQuad(Color(0xFF4F1F20), Color(0xFFFF9A93), Color(0xFFFFDAD6), Color(0xFF4F1F20), Sunny12Shape, Icons.Rounded.Shield)
            else
                CategoryColorQuad(Color(0xFFFFD9D6), Color(0xFFB3261E), Color(0xFF410002), Color(0xFFFFFFFF), Sunny12Shape, Icons.Rounded.Shield)

            "SHOPPING" -> if (isDark)
                CategoryColorQuad(Color(0xFF1E3A54), Color(0xFF72B8FF), Color(0xFFD9ECFF), Color(0xFF1E3A54), Squircle4Shape, Icons.Rounded.ShoppingCart)
            else
                CategoryColorQuad(Color(0xFFD9ECFF), Color(0xFF1B63A8), Color(0xFF0A2B4D), Color(0xFFFFFFFF), Squircle4Shape, Icons.Rounded.ShoppingCart)

            "TICKETS" -> if (isDark)
                CategoryColorQuad(Color(0xFF4A2840), Color(0xFFE89AD1), Color(0xFFFCD8F2), Color(0xFF4A2840), Cookie8Shape, Icons.Rounded.ConfirmationNumber)
            else
                CategoryColorQuad(Color(0xFFFCD8F2), Color(0xFF8E3077), Color(0xFF3F0E34), Color(0xFFFFFFFF), Cookie8Shape, Icons.Rounded.ConfirmationNumber)

            "REMINDERS" -> if (isDark)
                CategoryColorQuad(Color(0xFF4A3E1E), Color(0xFFFCD36A), Color(0xFFFEF3D1), Color(0xFF4A3E1E), Flower5Shape, Icons.Rounded.Notifications)
            else
                CategoryColorQuad(Color(0xFFFEF3D1), Color(0xFF8A6D15), Color(0xFF3D2E02), Color(0xFFFFFFFF), Flower5Shape, Icons.Rounded.Notifications)

            "SUBSCRIPTIONS" -> if (isDark)
                CategoryColorQuad(Color(0xFF2B2D54), Color(0xFFA0A6FF), Color(0xFFE0E3FF), Color(0xFF2B2D54), Clover3Shape, Icons.Rounded.CreditCard)
            else
                CategoryColorQuad(Color(0xFFE0E3FF), Color(0xFF434AA8), Color(0xFF121650), Color(0xFFFFFFFF), Clover3Shape, Icons.Rounded.CreditCard)

            else -> {
                val effectiveColor = if (customColorInt != null) Color(customColorInt) else Color(0xFF4CAF50)
                val icon = customIcon ?: Icons.Rounded.Category
                val shape = customShape ?: Squircle4Shape
                val container = if (isDark) effectiveColor.copy(alpha = 0.25f) else effectiveColor.copy(alpha = 0.15f)
                val onContainer = if (isDark) Color(0xFFE6EDE7) else Color(0xFF101612)
                val onAccent = if (isDark) Color(0xFF101612) else Color.White
                CategoryColorQuad(container, effectiveColor, onContainer, onAccent, shape, icon)
            }
        }

        if (customColorInt != null && !categoryKey.startsWith("CUSTOM_")) {
            val customColor = Color(customColorInt)
            val container = if (isDark) customColor.copy(alpha = 0.25f) else customColor.copy(alpha = 0.15f)
            val onContainer = if (isDark) Color(0xFFE6EDE7) else Color(0xFF101612)
            val onAccent = if (isDark) Color(0xFF101612) else Color.White
            return baseQuad.copy(
                container = container,
                accent = customColor,
                onContainer = onContainer,
                onAccent = onAccent,
                icon = customIcon ?: baseQuad.icon
            )
        }

        return baseQuad
    }
}
