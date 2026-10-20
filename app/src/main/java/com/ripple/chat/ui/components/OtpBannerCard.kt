package com.ripple.chat.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ripple.chat.data.OtpBannerItem
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpBannerSection(
    items: List<OtpBannerItem>,
    dismissedIds: Set<String>,
    onDismiss: (String) -> Unit,
    onNavigateToChat: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTime = rememberTicker()

    val activeItems = remember(items, dismissedIds, currentTime) {
        items.filter { item ->
            !dismissedIds.contains(item.id) && (currentTime <= item.expiresAt + 30_000L)
        }
    }

    if (activeItems.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = activeItems.isNotEmpty(),
        enter = expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val mainItem = activeItems.first()

            OtpBannerCard(
                item = mainItem,
                currentTime = currentTime,
                extraCount = if (activeItems.size > 1) activeItems.size - 1 else 0,
                isExpanded = isExpanded,
                onToggleExpand = { isExpanded = !isExpanded },
                onDismiss = { onDismiss(mainItem.id) },
                onNavigateToChat = { onNavigateToChat(mainItem.threadId, mainItem.sender) }
            )

            if (isExpanded && activeItems.size > 1) {
                activeItems.drop(1).forEach { extraItem ->
                    OtpBannerCard(
                        item = extraItem,
                        currentTime = currentTime,
                        extraCount = 0,
                        isExpanded = false,
                        onToggleExpand = {},
                        onDismiss = { onDismiss(extraItem.id) },
                        onNavigateToChat = { onNavigateToChat(extraItem.threadId, extraItem.sender) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpBannerCard(
    item: OtpBannerItem,
    currentTime: Long,
    extraCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val remainingMs = remember(currentTime, item.expiresAt) {
        (item.expiresAt - currentTime).coerceAtLeast(0L)
    }

    val isExpired = remainingMs == 0L
    val isExpiringSoon = remainingMs in 1L..30_000L

    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    val accessibilityManager = remember {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }

    LaunchedEffect(remainingMs) {
        if (accessibilityManager?.isEnabled == true) {
            val totalSec = remainingMs / 1000
            if (totalSec == 60L || totalSec == 30L || totalSec == 0L) {
                val announceText = if (totalSec == 0L) "Verification code expired" else "Verification code expires in $totalSec seconds"
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                event.text.add(announceText)
                accessibilityManager.sendAccessibilityEvent(event)
            }
        }
    }

    val dark = isSystemInDarkTheme()
    val bgColor = when {
        isExpired -> MaterialTheme.colorScheme.surfaceContainer
        isExpiringSoon -> if (dark) Color(0xFF93000A) else Color(0xFFFFDAD6)
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val textColor = when {
        isExpired -> MaterialTheme.colorScheme.onSurfaceVariant
        isExpiringSoon -> if (dark) Color(0xFFFFDAD6) else Color(0xFF410002)
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val ringColor = when {
        isExpired -> MaterialTheme.colorScheme.outline
        isExpiringSoon -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val tileBgColor = when {
        isExpired -> MaterialTheme.colorScheme.outline
        isExpiringSoon -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val codeSpelledOut = remember(item.code) {
        item.code.toList().joinToString(" ")
    }

    val minutes = remainingMs / 60000
    val seconds = (remainingMs % 60000) / 1000
    val accessibilityDesc = "Verification code $codeSpelledOut, expires in $minutes minutes $seconds seconds"

    val cardShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 12.dp)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.StartToEnd || dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { Box(Modifier.fillMaxSize()) },
        modifier = modifier
    ) {
        Surface(
            shape = cardShape,
            color = bgColor,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToChat() }
                .semantics { this.contentDescription = accessibilityDesc }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 20.dp, bottomEnd = 14.dp, bottomStart = 20.dp))
                            .background(tileBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.sender,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "· Verification code",
                                fontSize = 12.sp,
                                color = textColor.copy(alpha = 0.75f)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = item.code,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp,
                            fontFamily = FontFamily.Monospace,
                            color = textColor,
                            textDecoration = if (isExpired) TextDecoration.LineThrough else TextDecoration.None
                        )

                        if (item.usedDefaultExpiry && !isExpired) {
                            Text(
                                text = "Default 10 min",
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (extraCount > 0) {
                                Surface(
                                    onClick = onToggleExpand,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "+$extraCount more",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onDismiss() }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val progress = if (item.durationMs > 0) {
                                (remainingMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = ringColor.copy(alpha = 0.2f),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = ringColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * progress,
                                    useCenter = false,
                                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            val timeFormatted = formatTimeRemaining(remainingMs)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isExpired) "0:00" else timeFormatted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                if (isExpiringSoon && !isExpired) {
                                    Text(
                                        text = "Expiring",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ringColor
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (!isExpired) {
                            copyOtpToClipboard(context, item.code)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isCopied = true
                        }
                    },
                    enabled = !isExpired,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isExpiringSoon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = CircleShape
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isExpired -> "Expired"
                                isCopied -> "Copied"
                                else -> "Copy"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberTicker(): Long {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }
    return currentTime
}

private fun formatTimeRemaining(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (min >= 60) {
        val hrs = min / 60
        val mins = min % 60
        "${hrs}h ${mins}m"
    } else {
        String.format(Locale.US, "%d:%02d", min, sec)
    }
}

private fun copyOtpToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OTP Code", code)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }

    clipboard.setPrimaryClip(clip)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        BromeToast.show(context, "OTP $code copied")
    }
}
