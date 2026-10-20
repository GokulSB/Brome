package com.ripple.chat.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

fun formatTime(timeMillis: Long): String {
    val date = Date(timeMillis)
    val now = Calendar.getInstance()
    val timeCalendar = Calendar.getInstance().apply { time = date }

    val isToday = now.get(Calendar.YEAR) == timeCalendar.get(Calendar.YEAR) &&
                  now.get(Calendar.DAY_OF_YEAR) == timeCalendar.get(Calendar.DAY_OF_YEAR)

    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    if (isToday) {
        return timeFormatter.format(date)
    }

    now.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = now.get(Calendar.YEAR) == timeCalendar.get(Calendar.YEAR) &&
                      now.get(Calendar.DAY_OF_YEAR) == timeCalendar.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) {
        return "Yesterday"
    }

    val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
    return formatter.format(date)
}

fun extractAmount(text: String): Double {
    val cleanText = text
        .replace(Regex("(?i)(?:available|avl|total|outst|remaining)\\s*(?:balance|bal|limit|amt|amount|outstanding)\\s*(?:is|:|-|=)?\\s*(?:rs\\.?|inr|\\u20B9)?\\s*[\\d,]+\\.?\\d*"), "")
        .replace(Regex("(?i)(?:order|txn|transaction|ref|reference)\\s*(?:id|no|number)?\\s*[:\\-]?\\s*[#*]?\\s*\\d+"), "")
        .replace(Regex("[#*]\\s*\\d+"), "")
        .replace(Regex("\\b\\d{10,}\\b"), "")

    val regexCurrency = Regex("(?i)(?:rs\\.?|inr|\\u20B9)\\s*([\\d,]+\\.?\\d*)")
    val match = regexCurrency.find(cleanText)
    if (match != null) {
        val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
        if (amount > 0) return amount
    }

    val regexAmountOf = Regex("(?i)amount(?: of)?\\s*([\\d,]+\\.?\\d*)")
    val matchAmountOf = regexAmountOf.find(cleanText)
    if (matchAmountOf != null) {
        val amount = matchAmountOf.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
        if (amount > 0) return amount
    }

    val decimalRegex = Regex("([\\d,]+\\.\\d{1,2})")
    val decimalMatch = decimalRegex.find(cleanText)
    if (decimalMatch != null) {
        val amount = decimalMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
        if (amount > 0) return amount
    }

    val genericRegex = Regex("\\b([\\d,]+)\\b")
    val allMatches = genericRegex.findAll(cleanText).mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.toList()
    return allMatches.firstOrNull { it > 0 } ?: 0.0
}

fun formatAmountCompact(amount: Double): String {
    return when {
        amount >= 100000 -> String.format(Locale.US, "%.1fL", amount / 100000)
        amount >= 1000 -> String.format(Locale.US, "%.1fk", amount / 1000)
        else -> String.format(Locale.US, "%.0f", amount)
    }
}

@Composable
fun HighlightedText(
    text: String,
    query: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    if (query.isBlank()) {
        Text(text = text, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = maxLines, overflow = overflow)
        return
    }

    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer

    val annotatedString = buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        var startIndex = 0
        while (startIndex < text.length) {
            val index = lowerText.indexOf(lowerQuery, startIndex)
            if (index == -1) {
                append(text.substring(startIndex))
                break
            }

            append(text.substring(startIndex, index))

            withStyle(SpanStyle(background = highlightColor, color = highlightTextColor)) {
                append(text.substring(index, index + query.length))
            }

            startIndex = index + query.length
        }
    }

    Text(text = annotatedString, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = maxLines, overflow = overflow)
}

fun checkIsDefaultSms(context: android.content.Context): Boolean {
    val roleHeld = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.content.Context.ROLE_SERVICE) as? android.app.role.RoleManager
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true
        } else false
    } catch (_: Exception) {
        false
    }
    val pkgMatches = try {
        val defPkg = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        defPkg?.equals(context.packageName, ignoreCase = true) == true
    } catch (_: Exception) {
        false
    }
    val secureSettingMatches = try {
        val defApp = android.provider.Settings.Secure.getString(context.contentResolver, "sms_default_application")
        defApp?.equals(context.packageName, ignoreCase = true) == true
    } catch (_: Exception) {
        false
    }
    return roleHeld || pkgMatches || secureSettingMatches
}

