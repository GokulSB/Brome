package com.ripple.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ripple.chat.R

object AppStrings {
    @Composable
    fun categoryName(catKey: String): String = when (catKey.uppercase()) {
        "UNREAD" -> stringResource(R.string.cat_unread)
        "PERSONAL" -> stringResource(R.string.cat_personal)
        "OTP" -> stringResource(R.string.cat_otp)
        "FINANCE" -> stringResource(R.string.cat_finance)
        "PROMOTIONS", "PROMOTIONAL" -> stringResource(R.string.cat_promotions)
        "SPAM" -> stringResource(R.string.cat_spam)
        "SHOPPING" -> stringResource(R.string.cat_shopping)
        "TICKETS" -> stringResource(R.string.cat_tickets)
        "REMINDERS" -> stringResource(R.string.cat_reminders)
        "SUBSCRIPTIONS" -> stringResource(R.string.cat_subscriptions)
        "IMPORTANT" -> stringResource(R.string.cat_important)
        "ALL" -> stringResource(R.string.cat_all)
        "MESSAGES" -> stringResource(R.string.cat_messages)
        "OTHERS" -> stringResource(R.string.cat_others)
        else -> catKey.lowercase().replaceFirstChar { it.uppercase() }
    }

    @Composable
    fun autoDeleteLabel(label: String): String = when (label.lowercase()) {
        "never" -> stringResource(R.string.time_never)
        "1 hour" -> stringResource(R.string.time_1_hour)
        "24 hours" -> stringResource(R.string.time_24_hours)
        "7 days" -> stringResource(R.string.time_7_days)
        "30 days" -> stringResource(R.string.time_30_days)
        "60 days" -> stringResource(R.string.time_60_days)
        else -> label
    }
}
