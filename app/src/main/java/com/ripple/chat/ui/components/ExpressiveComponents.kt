package com.ripple.chat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.theme.*
import com.ripple.chat.ui.chat.HighlightedText
import com.ripple.chat.ui.chat.formatTime
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// 1. DECORATIVE BACKGROUND
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DecorBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val peachColor = if (dark) DecorPeachDark.copy(alpha = 0.5f) else DecorPeachLight.copy(alpha = 0.7f)
    val yellowColor = if (dark) DecorYellowDark else DecorYellowLight
    val ringColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
    val greenColor = if (dark) DecorGreenDark.copy(alpha = 0.6f) else DecorGreenLight.copy(alpha = 0.8f)

    Box(modifier = modifier.fillMaxSize()) {
        // Dot Grid canvas
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
                        center = Offset(i * spacing, j * spacing)
                    )
                }
            }
        }

        // Top-Right Large Peach Blob
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-40).dp)
                .size(240.dp)
                .clip(RoundedCornerShape(60, 40, 70, 50))
                .background(peachColor)
        )

        // Small Yellow Dot beside Peach Blob
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-130).dp, y = 80.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(yellowColor)
        )

        // Bottom-Left Thick Tonal Ring
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 60.dp)
                .size(210.dp)
        ) {
            drawCircle(
                color = ringColor,
                radius = size.width / 2,
                style = Stroke(width = 30.dp.toPx())
            )
        }

        // Soft Tilted Green Rounded Square on Right Edge
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 30.dp, y = 120.dp)
                .size(90.dp)
                .rotate(22f)
                .clip(RoundedCornerShape(24.dp))
                .background(greenColor)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. SHAPED AVATAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ShapedAvatar(
    name: String,
    photoUri: String? = null,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val hash = abs(name.hashCode())

    // 5 varied expressive shapes
    val shape = remember(hash) {
        when (hash % 5) {
            0 -> RoundedCornerShape(topStartPercent = 60, topEndPercent = 60, bottomEndPercent = 40, bottomStartPercent = 40) // blob 60/60/40/40
            1 -> RoundedCornerShape(16.dp) // rounded square 16dp
            2 -> RoundedCornerShape(topStartPercent = 60, topEndPercent = 30, bottomEndPercent = 70, bottomStartPercent = 40) // organic blob
            3 -> CircleShape // circle
            else -> RoundedCornerShape(topStart = 14.dp, topEnd = 24.dp, bottomEnd = 14.dp, bottomStart = 24.dp) // asymmetric 14/24/14/24
        }
    }

    // Pick color pair from hash
    val colorPair = AvatarAccentPairs[hash % AvatarAccentPairs.size]
    val bgColor = if (dark) colorPair.bgDark else colorPair.bgLight
    val textColor = if (dark) AvatarLetterDark else AvatarLetterLight

    val letter = name.trim().firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bgColor),
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
                color = textColor,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. TONAL ICON BUTTON
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TonalIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadiusPercent by animateFloatAsState(
        targetValue = if (isPressed) 50f else 32f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "buttonShape"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(cornerRadiusPercent.toInt()))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. CATEGORY CHIP
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CategoryChip(
    label: String,
    isSelected: Boolean,
    unreadCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val targetRadius = if (isSelected) 12.dp else 16.dp
    val animatedRadius by animateDpAsState(
        targetValue = targetRadius,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "chipRadius"
    )

    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(animatedRadius))
            .background(bgColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )

            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .padding(horizontal = 5.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. THREAD ROW (Featured and Flat variants)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ThreadRow(
    thread: ChatThread,
    isUnread: Boolean,
    unreadCount: Int = 1,
    searchQuery: String = "",
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayName = thread.contactName ?: thread.address
    val bodyText = thread.body

    // Check for detected OTP
    val otpCode = remember(bodyText, thread.category) {
        if (thread.category == SmsCategory.OTP || bodyText.contains("OTP", ignoreCase = true) || bodyText.contains("code", ignoreCase = true)) {
            Regex("\\b\\d{4,8}\\b").find(bodyText)?.value
        } else null
    }

    // Check for business amount / debit / credit
    val (amountText, isDebit) = remember(bodyText) {
        val lower = bodyText.lowercase()
        val hasDebit = lower.contains("debit") || lower.contains("deducted") || lower.contains("sent") || lower.contains("paid") || lower.contains("spent")
        val hasCredit = lower.contains("credit") || lower.contains("received") || lower.contains("deposited") || lower.contains("added")
        if (hasDebit || hasCredit) {
            val amount = com.ripple.chat.ui.chat.extractAmount(lower)
            if (amount > 0) {
                val formatted = "₹${com.ripple.chat.ui.chat.formatAmountCompact(amount)}"
                Pair(formatted, hasDebit)
            } else Pair(null, null)
        } else Pair(null, null)
    }

    // Check for link / action URL
    val actionUrl = remember(bodyText) {
        Regex("https?://\\S+").find(bodyText)?.value
    }

    val rowContent = @Composable {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Shaped Avatar
            ShapedAvatar(
                name = displayName,
                photoUri = thread.contactPhotoUri,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Main Text Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    ) {
                        if (isUnread) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        HighlightedText(
                            text = displayName,
                            query = searchQuery,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Time
                    Text(
                        text = formatTime(thread.date),
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Preview (2 lines max)
                HighlightedText(
                    text = bodyText,
                    query = searchQuery,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Inline chips / action pills if detected
                if (otpCode != null || amountText != null || actionUrl != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (otpCode != null) {
                            // Tappable OTP Code Chip (yellow container)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSystemInDarkTheme()) DecorYellowDark else DecorYellowLight,
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("OTP", otpCode))
                                    BromeToast.show(context, "OTP $otpCode copied")
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy OTP",
                                        tint = Color(0xFF3A0B01),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = otpCode,
                                        color = Color(0xFF3A0B01),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }

                        if (amountText != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDebit == true) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${if (isDebit == true) "Debit" else "Credit"} $amountText",
                                    color = if (isDebit == true) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (actionUrl != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(actionUrl))
                                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.OpenInNew,
                                        contentDescription = "Link",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Open Link",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Unread Count Badge if > 1
            if (isUnread && unreadCount > 1) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 10.dp, bottomEnd = 8.dp, bottomStart = 10.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unreadCount.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    val containerColor = if (isUnread) MaterialTheme.colorScheme.surfaceContainer
                         else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
    val containerShape = if (isUnread) RoundedCornerShape(28.dp) else RoundedCornerShape(20.dp)

    Surface(
        shape = containerShape,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            rowContent()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. CHAT BUBBLE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChatBubble(
    messageText: String,
    timeText: String,
    isSentByMe: Boolean,
    deliveryStatusText: String? = null,
    isPrevSameSender: Boolean = false,
    isNextSameSender: Boolean = false,
    onUrlClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shape = remember(isSentByMe, isPrevSameSender, isNextSameSender) {
        if (isSentByMe) {
            val topEnd = if (isPrevSameSender) 6.dp else 24.dp
            val bottomEnd = 6.dp
            RoundedCornerShape(topStart = 24.dp, topEnd = topEnd, bottomEnd = bottomEnd, bottomStart = 24.dp)
        } else {
            val topStart = if (isPrevSameSender) 6.dp else 24.dp
            val bottomStart = 6.dp
            RoundedCornerShape(topStart = topStart, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = bottomStart)
        }
    }

    val bgColor = if (isSentByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
    val textColor = if (isSentByMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

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
            val linkColor = if (isSentByMe) textColor else MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .widthIn(min = 60.dp, max = 290.dp)
                    .clip(shape)
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

            if (!isNextSameSender) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = if (deliveryStatusText != null && isSentByMe) "$timeText · $deliveryStatusText" else timeText,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. SMART REPLY CHIP
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SmartReplyChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.height(36.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. FLOATING NAV PILL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FloatingNavPill(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier = Modifier.height(56.dp)
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
                            .size(width = 56.dp, height = 40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onTabSelected(index) }
                            .semantics { this.contentDescription = label },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9. EXPRESSIVE FAB
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ExpressiveFab(
    onClick: () -> Unit,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerShape = remember(isPressed) {
        if (isPressed) CircleShape
        else RoundedCornerShape(topStart = 20.dp, topEnd = 28.dp, bottomEnd = 20.dp, bottomStart = 28.dp)
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = cornerShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(56.dp)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .semantics { this.contentDescription = "New Conversation" }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
