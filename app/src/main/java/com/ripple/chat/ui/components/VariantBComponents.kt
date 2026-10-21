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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.MessageDeliveryStatus
import com.ripple.chat.data.OtpBannerItem
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.theme.*
import com.ripple.chat.ui.chat.HighlightedText
import com.ripple.chat.ui.chat.extractAmount
import com.ripple.chat.ui.chat.formatAmountCompact
import com.ripple.chat.ui.chat.formatTime
import kotlinx.coroutines.delay
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// 1. VARIANT B DECOR BACKGROUND
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VariantBDecorBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val dotColor = if (dark) Color(0xFF3F2E29).copy(alpha = 0.5f) else Color(0xFFEBD6D0).copy(alpha = 0.6f)
    val decorFlowerColor = if (dark) Color(0xFF5E3226).copy(alpha = 0.55f) else Color(0xFFFFDBD0).copy(alpha = 0.65f)
    val faintCookieColor = if (dark) Color(0xFFF9E2A2).copy(alpha = 0.18f) else Color(0xFFF9E2A2).copy(alpha = 0.35f)
    val ringColor = if (dark) Color(0xFF261B18) else Color(0xFFF8EBE7)

    Box(modifier = modifier.fillMaxSize()) {
        // Dot Grid
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotRadius = 1.dp.toPx()
            val spacing = 20.dp.toPx()
            val cols = (size.width / spacing).toInt() + 1
            val rows = (size.height / spacing).toInt() + 1

            for (i in 0..cols) {
                for (j in 0..rows) {
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = androidx.compose.ui.geometry.Offset(i * spacing, j * spacing)
                    )
                }
            }
        }

        // Top-Left Large Decor Flower
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-40).dp, y = (-40).dp)
                .size(240.dp)
                .clip(Flower6Shape())
                .background(decorFlowerColor)
        )

        // Right Faint Yellow Cookie
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 40.dp, y = 80.dp)
                .size(180.dp)
                .clip(Cookie10Shape())
                .background(faintCookieColor)
        )

        // Bottom-Right Thick Ring
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .size(200.dp)
        ) {
            drawCircle(
                color = ringColor,
                radius = size.width / 2,
                style = Stroke(width = 26.dp.toPx())
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. VARIANT B SHAPED AVATAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VariantBShapedAvatar(
    name: String,
    photoUri: String? = null,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val (shape, accent) = remember(name) { getVariantBAvatarShapeAndAccent(name) }
    val letter = name.trim().firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(accent.container),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = photoUri,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Text(
                text = letter,
                color = accent.onContainer,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. VARIANT B CATEGORY TILE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VariantBCategoryTile(
    label: String,
    count: Int,
    icon: ImageVector,
    accentPair: VariantBAccentPair,
    cornerType: Int, // 0: TL, 1: TR, 2: BL, 3: BR
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = when (cornerType) {
        0 -> RoundedCornerShape(topStart = 36.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
        1 -> RoundedCornerShape(topStart = 12.dp, topEnd = 36.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
        2 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 36.dp)
        else -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 36.dp, bottomStart = 12.dp)
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = accentPair.container,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, accentPair.onContainer) else null,
        modifier = modifier
            .size(88.dp)
    ) {
        Box(modifier = Modifier.padding(10.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentPair.onContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.TopEnd)
            )

            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = count.toString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentPair.onContainer,
                    lineHeight = 28.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentPair.onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. VARIANT B FLOATING TOOLBAR & FAB
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VariantBFloatingBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Floating Toolbar (64dp tall, containerHigh, fully rounded)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier.height(64.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val items = listOf(
                    Icons.Rounded.Message to "Messages",
                    Icons.Rounded.Contacts to "Contacts"
                )

                items.forEachIndexed { index, (icon, label) ->
                    val isSelected = selectedIndex == index
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isSelected) VariantBPeach.container else Color.Transparent)
                            .clickable { onTabSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) VariantBPeach.onContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Separate 64dp Compose FAB on Right (Yellow, corners 26/32/26/32)
        Surface(
            onClick = onFabClick,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 32.dp, bottomEnd = 26.dp, bottomStart = 32.dp),
            color = VariantBYellow.container,
            shadowElevation = 8.dp,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "New Conversation",
                    tint = VariantBYellow.onContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. VARIANT B CHAT BUBBLE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VariantBChatBubble(
    messageText: String,
    timeText: String,
    isSentByMe: Boolean,
    deliveryStatusText: String? = null,
    deliveryStatus: MessageDeliveryStatus? = null,
    failureReason: String? = null,
    onRetry: (() -> Unit)? = null,
    isPrevSameSender: Boolean = false,
    isNextSameSender: Boolean = false,
    onUrlClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appearance = com.ripple.chat.theme.LocalBromeAppearance.current
    val bubbleStyle = appearance.bubbleTheme

    val shape = remember(isSentByMe, isPrevSameSender, bubbleStyle) {
        when (bubbleStyle) {
            com.ripple.chat.data.BubbleThemeStyle.ROUND -> RoundedCornerShape(18.dp)
            com.ripple.chat.data.BubbleThemeStyle.FLAT -> RoundedCornerShape(5.dp)
            com.ripple.chat.data.BubbleThemeStyle.OUTLINE -> RoundedCornerShape(14.dp)
            else -> {
                if (isSentByMe) {
                    val topEnd = if (isPrevSameSender) 8.dp else 28.dp
                    RoundedCornerShape(topStart = 28.dp, topEnd = topEnd, bottomEnd = 8.dp, bottomStart = 28.dp)
                } else {
                    val topStart = if (isPrevSameSender) 8.dp else 28.dp
                    RoundedCornerShape(topStart = topStart, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 8.dp)
                }
            }
        }
    }

    val themeConfig = com.ripple.chat.theme.LocalThemeConfig.current
    val customSent = themeConfig?.senderBubbleColor?.let { Color(it) }
    val customRec = themeConfig?.receiverBubbleColor?.let { Color(it) }

    val isOutline = bubbleStyle == com.ripple.chat.data.BubbleThemeStyle.OUTLINE
    val bgColor = when {
        isOutline -> Color.Transparent
        isSentByMe -> customSent ?: MaterialTheme.colorScheme.primary
        else -> customRec ?: MaterialTheme.colorScheme.surfaceContainer
    }
    val textColor = when {
        isOutline -> if (isSentByMe) (customSent ?: MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.onSurface
        isSentByMe -> {
            if (customSent != null) {
                val lum = 0.299f * customSent.red + 0.587f * customSent.green + 0.114f * customSent.blue
                if (lum > 0.5f) Color.Black else Color.White
            } else MaterialTheme.colorScheme.onPrimary
        }
        else -> {
            if (customRec != null) {
                val lum = 0.299f * customRec.red + 0.587f * customRec.green + 0.114f * customRec.blue
                if (lum > 0.5f) Color.Black else Color.White
            } else MaterialTheme.colorScheme.onSurface
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(250)) + slideInVertically(initialOffsetY = { 40 }, animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f))
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
        ) {
            val linkColor = when {
                isOutline -> if (isSentByMe) (customSent ?: MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.primary
                isSentByMe -> textColor
                else -> MaterialTheme.colorScheme.primary
            }

            Box(
                modifier = Modifier
                    .widthIn(min = 60.dp, max = 290.dp)
                    .clip(shape)
                    .then(if (isOutline) Modifier.border(1.5.dp, if (isSentByMe) (customSent ?: MaterialTheme.colorScheme.primary) else (customRec ?: MaterialTheme.colorScheme.outline), shape) else Modifier)
                    .background(bgColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (onUrlClick != null) {
                    MessageLinkText(
                        text = messageText,
                        textColor = textColor,
                        linkColor = linkColor,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        onUrlClick = onUrlClick
                    )
                } else {
                    Text(
                        text = messageText,
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            val showStatusRow = !isNextSameSender || deliveryStatus == MessageDeliveryStatus.FAILED
            if (showStatusRow) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = timeText,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 12.sp
                    )

                    if (isSentByMe) {
                        Text(
                            text = "·",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 12.sp
                        )

                        when (deliveryStatus) {
                            MessageDeliveryStatus.PENDING -> {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = "Sending",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Sending",
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 12.sp
                                )
                            }
                            MessageDeliveryStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Sent",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Sent",
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 12.sp
                                )
                            }
                            MessageDeliveryStatus.DELIVERED -> {
                                Box(
                                    modifier = Modifier.size(18.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Delivered",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp).offset(x = 4.dp)
                                    )
                                }
                                Text(
                                    text = "Delivered",
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 12.sp
                                )
                            }
                            MessageDeliveryStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = "Failed",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                val reasonText = failureReason ?: "Failed"
                                Text(
                                    text = reasonText,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (onRetry != null) {
                                    Text(
                                        text = "·",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Retry",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { onRetry() }
                                            .padding(horizontal = 2.dp)
                                    )
                                }
                            }
                            null -> {
                                if (deliveryStatusText != null) {
                                    Text(
                                        text = deliveryStatusText,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. VARIANT B OTP BANNER CARD
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariantBOtpBannerSection(
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

            VariantBOtpBannerCard(
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
                    VariantBOtpBannerCard(
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
fun VariantBOtpBannerCard(
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

    // Color definitions per Variant B spec
    val bgColor = when {
        isExpired -> MaterialTheme.colorScheme.surfaceContainer
        isExpiringSoon -> Color(0xFF93000A)
        else -> VariantBPeach.container
    }

    val textColor = when {
        isExpired -> MaterialTheme.colorScheme.onSurfaceVariant
        isExpiringSoon -> Color(0xFFFFDAD6)
        else -> VariantBPeach.onContainer
    }

    val ringColor = when {
        isExpired -> MaterialTheme.colorScheme.outline
        isExpiringSoon -> Color(0xFFFFDAD6)
        else -> VariantBPeach.onContainer
    }

    val codeSpelledOut = remember(item.code) {
        item.code.toList().joinToString(" ")
    }

    val minutes = remainingMs / 60000
    val seconds = (remainingMs % 60000) / 1000
    val accessibilityDesc = "Verification code $codeSpelledOut, expires in $minutes minutes $seconds seconds"

    val cardShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomEnd = 36.dp, bottomStart = 12.dp)

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
            Box(modifier = Modifier.padding(18.dp)) {
                // Faint decor circle top right
                Canvas(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-12).dp)
                        .size(60.dp)
                ) {
                    drawCircle(color = textColor.copy(alpha = 0.08f))
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top Row: 40dp Yellow Cookie Tile + Sender + Timer + Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 40dp Yellow Cookie Tile with Key icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(Cookie10Shape())
                                .background(VariantBYellow.container),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VpnKey,
                                contentDescription = null,
                                tint = VariantBYellow.onContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Sender + Verification code
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.sender,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Verification code",
                                fontSize = 12.sp,
                                color = textColor.copy(alpha = 0.75f)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Timer Ring (52dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (extraCount > 0) {
                                Surface(
                                    onClick = onToggleExpand,
                                    shape = CircleShape,
                                    color = VariantBYellow.container,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "+$extraCount more",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VariantBYellow.onContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier.size(52.dp),
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

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onDismiss() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Code (34sp, weight 800, letter-spacing 5sp, tabular)
                    Text(
                        text = item.code,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 5.sp,
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // 40dp Pill Copy Button (#4A1A0D background, peach text)
                    Button(
                        onClick = {
                            if (!isExpired) {
                                copyOtpToClipboard(context, item.code)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isCopied = true
                            }
                        },
                        enabled = !isExpired,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VariantBPeach.onContainer,
                            contentColor = VariantBPeach.container,
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
