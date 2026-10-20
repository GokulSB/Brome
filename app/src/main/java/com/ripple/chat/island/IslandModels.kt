package com.ripple.chat.island

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.theme.*

enum class IslandCategory(val key: String, val label: String) {
    MESSAGES("MESSAGES", "Messages"),
    OTP("OTP", "OTP"),
    TRANSACTIONS("FINANCE", "Transactions"),
    PROMOTIONS("PROMOTIONAL", "Promotions"),
    SPAM("SPAM", "Spam");

    companion object {
        fun fromSmsCategory(category: SmsCategory): IslandCategory {
            return when (category) {
                SmsCategory.OTP -> OTP
                SmsCategory.FINANCE -> TRANSACTIONS
                SmsCategory.PROMOTIONAL -> PROMOTIONS
                SmsCategory.SPAM -> SPAM
                else -> MESSAGES
            }
        }
    }
}

enum class IslandState {
    IDLE,
    COMPACT,
    EXPANDED,
    REPLYING
}

enum class IslandSizeScale(val scale: Float, val label: String) {
    COMPACT(0.9f, "Compact"),
    DEFAULT(1.0f, "Default"),
    LARGE(1.1f, "Large");

    companion object {
        fun fromString(value: String): IslandSizeScale {
            return when (value.uppercase()) {
                "COMPACT" -> COMPACT
                "LARGE" -> LARGE
                else -> DEFAULT
            }
        }
    }
}

data class IslandCategoryTheme(
    val category: IslandCategory,
    val containerColor: Color,
    val accentColor: Color,
    val shape: Shape,
    val tagText: String,
    val icon: ImageVector
)

object IslandVisualDefaults {
    val PillBackground = Color(0xFF000000)
    val PillTextColor = Color(0xFFFFFFFF)

    // Exact category colors and shapes specified by the prompt:
    // OTP: #B8A9FF on #3A3369, shape clover, lock icon
    // Transactions: #6FE0D0 on #123F3A, shape cookie, bank icon
    // Promotions: #FF8F6B on #5A2A1C, shape flower, offer icon
    // Spam: #FF9A93 on #4F1F20, shape sunny, shield icon
    // Messages: #CDF564 on #1B2600, shape flower, person icon
    val OtpTheme = IslandCategoryTheme(
        category = IslandCategory.OTP,
        containerColor = Color(0xFF3A3369),
        accentColor = Color(0xFFB8A9FF),
        shape = Clover3Shape,
        tagText = "OTP",
        icon = Icons.Rounded.Lock
    )

    val TransactionsTheme = IslandCategoryTheme(
        category = IslandCategory.TRANSACTIONS,
        containerColor = Color(0xFF123F3A),
        accentColor = Color(0xFF6FE0D0),
        shape = Cookie8Shape,
        tagText = "Transaction",
        icon = Icons.Rounded.AccountBalance
    )

    val PromotionsTheme = IslandCategoryTheme(
        category = IslandCategory.PROMOTIONS,
        containerColor = Color(0xFF5A2A1C),
        accentColor = Color(0xFFFF8F6B),
        shape = Flower5Shape,
        tagText = "Promotion",
        icon = Icons.Rounded.LocalOffer
    )

    val SpamTheme = IslandCategoryTheme(
        category = IslandCategory.SPAM,
        containerColor = Color(0xFF4F1F20),
        accentColor = Color(0xFFFF9A93),
        shape = Sunny12Shape,
        tagText = "Spam",
        icon = Icons.Rounded.Shield
    )

    val MessagesTheme = IslandCategoryTheme(
        category = IslandCategory.MESSAGES,
        containerColor = Color(0xFF1B2600),
        accentColor = Color(0xFFCDF564),
        shape = Flower5Shape,
        tagText = "Message",
        icon = Icons.Rounded.Person
    )

    fun themeFor(category: IslandCategory): IslandCategoryTheme {
        return when (category) {
            IslandCategory.OTP -> OtpTheme
            IslandCategory.TRANSACTIONS -> TransactionsTheme
            IslandCategory.PROMOTIONS -> PromotionsTheme
            IslandCategory.SPAM -> SpamTheme
            IslandCategory.MESSAGES -> MessagesTheme
        }
    }
}

data class IslandMessage(
    val id: Long = System.currentTimeMillis(),
    val address: String,
    val senderName: String,
    val body: String,
    val date: Long = System.currentTimeMillis(),
    val category: SmsCategory = SmsCategory.PERSONAL,
    val otpCode: String? = null,
    val otpExpirySeconds: Int = 300,
    val transactionAmount: String? = null,
    val merchant: String? = null,
    val accountInfo: String? = null,
    val notificationId: Int = 1001,
    val threadId: Long = -1L,
    val context: Context? = null
) {
    val islandCategory: IslandCategory
        get() = IslandCategory.fromSmsCategory(category)
}

typealias Message = IslandMessage

fun formatPeekText(
    message: IslandMessage,
    peekText: com.ripple.chat.data.IslandPeekText,
    maxChars: Int,
    hideContent: Boolean
): String {
    if (hideContent) {
        return when (message.islandCategory) {
            IslandCategory.OTP -> "OTP"
            IslandCategory.TRANSACTIONS -> "Transaction"
            else -> message.senderName
        }
    }

    if (peekText == com.ripple.chat.data.IslandPeekText.NOTHING) {
        return ""
    }

    val rawText = when (peekText) {
        com.ripple.chat.data.IslandPeekText.SMART -> {
            when (message.islandCategory) {
                IslandCategory.OTP -> message.otpCode ?: "OTP"
                IslandCategory.TRANSACTIONS -> message.transactionAmount ?: message.senderName
                else -> message.senderName
            }
        }
        com.ripple.chat.data.IslandPeekText.SENDER -> message.senderName
        com.ripple.chat.data.IslandPeekText.PREVIEW -> {
            when (message.islandCategory) {
                IslandCategory.OTP -> {
                    val code = message.otpCode
                    if (code != null) "$code is your OTP" else message.body
                }
                IslandCategory.TRANSACTIONS -> {
                    val amt = message.transactionAmount
                    val merch = message.merchant ?: message.senderName
                    if (amt != null) "$amt $merch" else message.body
                }
                else -> message.body
            }
        }
        com.ripple.chat.data.IslandPeekText.CATEGORY -> message.islandCategory.label
        com.ripple.chat.data.IslandPeekText.NOTHING -> ""
    }

    return if (rawText.length > maxChars) {
        rawText.take(maxChars) + "…"
    } else {
        rawText
    }
}
