package com.ripple.chat.ui.chat

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ripple.chat.data.OtpBannerItem
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpScreen(
    otpItems: List<OtpBannerItem>,
    onBack: () -> Unit,
    onNavigateToChat: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val otpQuad = BromeCategoryColors.getColors(SmsCategory.OTP, dark)

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    var usedCodeIds by remember { mutableStateOf(setOf<String>()) }
    var isCopiedHero by remember { mutableStateOf(false) }

    LaunchedEffect(isCopiedHero) {
        if (isCopiedHero) {
            delay(1200)
            isCopiedHero = false
        }
    }

    val activeUnexpired = remember(otpItems, currentTime, usedCodeIds) {
        otpItems.filter { it.id !in usedCodeIds && currentTime <= it.expiresAt }
    }

    val heroItem = activeUnexpired.firstOrNull()
    val earlierItems = remember(otpItems, heroItem) {
        if (heroItem != null) otpItems.filter { it.id != heroItem.id } else otpItems
    }

    val bromeSettings = remember { com.ripple.chat.data.BromeSettings(context) }
    val appearance by bromeSettings.appearanceFlow.collectAsState(initial = com.ripple.chat.data.BromeAppearance())

    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(52.dp).pressScale()
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OTP",
                        fontFamily = BricolageFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val otpAutoDeleteSubtitle = when (appearance.otpAutoDelete) {
                        com.ripple.chat.data.OtpAutoDeleteTime.NEVER -> "Auto-delete disabled"
                        else -> "Auto-clears after ${appearance.otpAutoDelete.label}"
                    }
                    Text(
                        text = otpAutoDeleteSubtitle,
                        fontFamily = FigtreeFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // HERO CARD FOR NEWEST UNEXPIRED CODE
                if (heroItem != null) {
                    item {
                        val remainingMs = (heroItem.expiresAt - currentTime).coerceAtLeast(0L)
                        val progress = if (heroItem.durationMs > 0) {
                            (remainingMs.toFloat() / heroItem.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        val mins = remainingMs / 60000
                        val secs = (remainingMs % 60000) / 1000
                        val timeStr = String.format(Locale.US, "%d:%02d", mins, secs)

                        Surface(
                            shape = getTideCornerShape(0, baseRadius = 30.dp, sharpRadius = 10.dp),
                            color = otpQuad.container,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToChat(heroItem.threadId, heroItem.sender) }
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(Clover3Shape)
                                            .background(otpQuad.accent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.VpnKey,
                                            contentDescription = null,
                                            tint = otpQuad.onAccent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = heroItem.sender,
                                        fontFamily = BricolageFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = otpQuad.onContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Code in Bricolage 800 60sp with 2sp letter spacing
                                Text(
                                    text = heroItem.code,
                                    fontFamily = BricolageFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 54.sp,
                                    letterSpacing = 2.sp,
                                    color = otpQuad.onContainer,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 10dp Expiry Progress Bar
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Expires in $timeStr",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FigtreeFontFamily,
                                            color = otpQuad.onContainer.copy(alpha = 0.8f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(CircleShape)
                                            .background(otpQuad.accent.copy(alpha = 0.3f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(progress)
                                                .clip(CircleShape)
                                                .background(otpQuad.accent)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Buttons Row: Full width "Copy code" pill + "Mark used" pill
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            copyToClipboard(context, heroItem.code)
                                            isCopiedHero = true
                                        },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f).height(48.dp).pressScale()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                if (isCopiedHero) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isCopiedHero) "Copied!" else "Copy code",
                                                fontFamily = FigtreeFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            usedCodeIds = usedCodeIds + heroItem.id
                                        },
                                        shape = CircleShape,
                                        color = otpQuad.accent.copy(alpha = 0.25f),
                                        modifier = Modifier.height(48.dp).pressScale()
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Mark used",
                                                fontFamily = FigtreeFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = otpQuad.onContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // EARLIER TODAY SECTION
                if (earlierItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "EARLIER TODAY",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    item {
                        Surface(
                            shape = getTideCornerShape(1, baseRadius = 30.dp, sharpRadius = 10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                earlierItems.forEachIndexed { idx, item ->
                                    val isExpired = currentTime > item.expiresAt
                                    val isUsed = usedCodeIds.contains(item.id)
                                    val isInactive = isExpired || isUsed

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 64.dp)
                                            .clickable { onNavigateToChat(item.threadId, item.sender) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(Clover3Shape)
                                                .background(if (isInactive) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else otpQuad.accent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.VpnKey,
                                                contentDescription = null,
                                                tint = if (isInactive) MaterialTheme.colorScheme.onSurfaceVariant else otpQuad.onAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.sender,
                                                fontFamily = BricolageFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.code,
                                                fontFamily = BricolageFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                letterSpacing = 1.sp,
                                                color = if (isInactive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                                textDecoration = if (isInactive) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                        }

                                        if (isUsed) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ) {
                                                Text(
                                                    text = "Used",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else if (isExpired) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ) {
                                                Text(
                                                    text = "Expired",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (idx < earlierItems.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // FOOTER LINE
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Codes are read on your phone only",
                            fontSize = 12.sp,
                            fontFamily = FigtreeFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OTP Code", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        com.ripple.chat.ui.components.BromeToast.show(context, "Copied: $text")
    }
}
