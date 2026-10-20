package com.ripple.chat.island

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ripple.chat.data.DynamicIslandSettings
import com.ripple.chat.data.IslandPeekLeft
import com.ripple.chat.data.IslandPeekText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun calculateCompactWidth(
    message: IslandMessage,
    settings: DynamicIslandSettings,
    queueSize: Int,
    isAroundCamera: Boolean,
    cutoutWidthDp: Dp
): Dp {
    val hasLeft = settings.peekLeft != IslandPeekLeft.NOTHING
    val peekString = formatPeekText(
        message = message,
        peekText = settings.peekText,
        maxChars = settings.peekMaxChars,
        hideContent = settings.hideContent
    )
    val hasText = peekString.isNotEmpty()

    return if (!hasText && !hasLeft) {
        48.dp
    } else if (!hasText && hasLeft) {
        if (isAroundCamera) 96.dp else 52.dp
    } else {
        val leftWidth = if (hasLeft) 32.dp else 0.dp
        val middleGap = if (isAroundCamera) cutoutWidthDp.coerceIn(24.dp, 44.dp) else 0.dp
        val queueBadge = if (queueSize > 1) 36.dp else 0.dp
        val textEstWidth = (peekString.length * 8.5f).dp
        val basePadding = 24.dp
        val calculated = basePadding + leftWidth + middleGap + textEstWidth + queueBadge
        val minWidth = if (isAroundCamera) 130.dp else 96.dp
        calculated.coerceIn(minWidth, 330.dp)
    }
}

fun estimateExpandedHeight(
    message: IslandMessage?,
    settings: DynamicIslandSettings
): Dp {
    if (message == null) return 220.dp
    return when (message.islandCategory) {
        IslandCategory.OTP -> 226.dp
        IslandCategory.TRANSACTIONS -> 216.dp
        else -> 200.dp
    }
}

@Composable
fun IslandMorph(
    expanded: Boolean,
    compactSize: DpSize,
    fullSize: DpSize,
    areAnimationsEnabled: Boolean = true,
    compact: @Composable () -> Unit,
    full: @Composable () -> Unit,
    onCollapsed: () -> Unit,
) {
    val morph = remember { Animatable(if (expanded) 1f else 0f) }
    var hasExpandedOnce by remember { mutableStateOf(expanded) }

    LaunchedEffect(expanded) {
        if (expanded) {
            hasExpandedOnce = true
            android.util.Log.d("IslandAnim", "IslandMorph: expand animation starting from ${morph.value} to 1.0 at ${System.currentTimeMillis()}")
            if (!areAnimationsEnabled) {
                morph.snapTo(1f)
            } else {
                morph.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f)
                )
            }
            android.util.Log.d("IslandAnim", "IslandMorph: expand animation completed at ${morph.value} at ${System.currentTimeMillis()}")
        } else if (hasExpandedOnce) {
            android.util.Log.d("IslandAnim", "IslandMorph: collapse animation starting from ${morph.value} to 0.0 at ${System.currentTimeMillis()}")
            if (!areAnimationsEnabled) {
                morph.snapTo(0f)
            } else {
                morph.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f)
                )
            }
            android.util.Log.d("IslandAnim", "IslandMorph: collapse animation completed at ${morph.value} at ${System.currentTimeMillis()}")
            onCollapsed()
        }
    }

    Box(
        modifier = Modifier
            .size(fullSize)
            .drawWithContent {
                val p = morph.value
                val w = lerp(compactSize.width.toPx(), size.width, p)
                val h = lerp(compactSize.height.toPx(), size.height, p)
                val r = lerp(18.dp.toPx(), 34.dp.toPx(), p)
                val left = (size.width - w) / 2f
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(left, 0f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(r, r)
                )
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = 0f,
                            right = left + w,
                            bottom = h,
                            radiusX = r,
                            radiusY = r
                        )
                    )
                }
                clipPath(path) {
                    this@drawWithContent.drawContent()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(compactSize.width)
                .height(compactSize.height)
                .graphicsLayer {
                    val p = morph.value
                    alpha = (1f - p * 2.5f).coerceIn(0f, 1f)
                }
        ) {
            compact()
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(fullSize.width)
                .graphicsLayer {
                    val p = morph.value
                    alpha = ((p - 0.3f) / 0.5f).coerceIn(0f, 1f)
                    translationY = (1f - p) * -8.dp.toPx()
                }
        ) {
            full()
        }
    }
}

@Composable
fun DynamicIslandOverlay(
    message: IslandMessage,
    queue: List<IslandMessage>,
    currentIndex: Int,
    settings: DynamicIslandSettings,
    cutoutWidthDp: Dp,
    topInsetDp: Dp,
    onRequestFocusForReply: (Boolean) -> Unit,
    onRequestExpand: ((expandedHeightDp: Dp, onReadyToMorph: () -> Unit) -> Unit)? = null,
    onCollapsed: (() -> Unit)? = null,
    onPagePrevious: () -> Unit,
    onPageNext: () -> Unit,
    onDismiss: () -> Unit,
    onOpenConversation: (IslandMessage) -> Unit,
    onCopyOtp: (IslandMessage) -> Unit,
    onMarkRead: (IslandMessage) -> Unit,
    onDeleteSpam: (IslandMessage) -> Unit,
    onSendReply: (IslandMessage, String) -> Unit,
    onPositionUpdate: ((pillWidthDp: Float, pillHeightDp: Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Respect system "Remove animations" setting
    val areAnimationsEnabled = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) > 0f
        } catch (e: Exception) {
            true
        }
    }

    // Expand on tap setting
    val sampleState by IslandQueueManager.sampleState.collectAsState()
    val isShowingSample = IslandQueueManager.isShowingSample
    val initialExpandState = if (isShowingSample) {
        sampleState
    } else if (!settings.actExpand) {
        IslandState.EXPANDED
    } else {
        IslandState.COMPACT
    }

    var currentState by remember(message.id, if (isShowingSample) sampleState else null) {
        mutableStateOf(initialExpandState)
    }
    var isMorphExpanded by remember(message.id) {
        mutableStateOf(initialExpandState != IslandState.COMPACT)
    }

    var isUserInteracting by remember { mutableStateOf(false) }
    var actionFeedbackText by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }
    val replyFocusRequester = remember { FocusRequester() }

    // Scale from settings
    val scaleMultiplier = when (settings.size.uppercase()) {
        "COMPACT" -> 0.9f
        "LARGE" -> 1.1f
        else -> 1.0f
    }

    // Auto-dismiss timer logic
    LaunchedEffect(message.id, currentState, isUserInteracting, settings.duration) {
        if (settings.duration > 0) {
            // Auto dismisses in compact mode, or if expand on tap is disabled
            val shouldAutoDismiss = (currentState == IslandState.COMPACT || !settings.actExpand) &&
                    currentState != IslandState.REPLYING &&
                    !isUserInteracting &&
                    actionFeedbackText == null

            if (shouldAutoDismiss) {
                delay(settings.duration * 1000L)
                onDismiss()
            }
        }
    }

    // When entering reply mode, request window focus so keyboard opens
    LaunchedEffect(currentState) {
        val inReply = currentState == IslandState.REPLYING
        onRequestFocusForReply(inReply)
        if (inReply) {
            delay(100)
            try {
                replyFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Top position: Around camera vs Top center
    val isAroundCamera = settings.position.uppercase() == "CAMERA" && cutoutWidthDp > 0.dp
    val targetCompactWidth = remember(message, settings, queue.size, isAroundCamera, cutoutWidthDp) {
        calculateCompactWidth(
            message = message,
            settings = settings,
            queueSize = queue.size,
            isAroundCamera = isAroundCamera,
            cutoutWidthDp = cutoutWidthDp
        )
    }

    var measuredExpandedHeightDp by remember(message.id) {
        mutableStateOf(estimateExpandedHeight(message, settings))
    }

    fun requestExpand() {
        if (isMorphExpanded) return
        if (settings.actExpand) {
            android.os.Trace.beginSection("IslandExpand")
            android.util.Log.d("IslandAnim", "Requesting expand to ${measuredExpandedHeightDp} at ${System.currentTimeMillis()}")
            onRequestExpand?.invoke(measuredExpandedHeightDp) {
                currentState = IslandState.EXPANDED
                isMorphExpanded = true
            } ?: run {
                currentState = IslandState.EXPANDED
                isMorphExpanded = true
            }
        } else {
            onOpenConversation(message)
        }
    }

    fun requestCollapse() {
        if (!isMorphExpanded) return
        android.util.Log.d("IslandAnim", "Requesting collapse at ${System.currentTimeMillis()}")
        currentState = IslandState.COMPACT
        isMorphExpanded = false
    }

    LaunchedEffect(sampleState) {
        if (isShowingSample) {
            if (sampleState == IslandState.EXPANDED && !isMorphExpanded) {
                requestExpand()
            } else if (sampleState == IslandState.COMPACT && isMorphExpanded) {
                requestCollapse()
            }
        }
    }

    // Drag offset for swipe-up dismiss
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .scale(scaleMultiplier)
            .graphicsLayer {
                translationY = dragOffsetY
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY = (dragOffsetY + dragAmount.y).coerceAtMost(0f)
                    },
                    onDragEnd = {
                        if (dragOffsetY < -30f) {
                            onDismiss()
                        }
                        dragOffsetY = 0f
                    }
                )
            }
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Dynamic island notification from ${message.senderName}"
            },
        contentAlignment = Alignment.TopCenter
    ) {
        val queuedCount = queue.size

        CompositionLocalProvider(LocalContentColor provides IslandVisualDefaults.PillTextColor) {
            IslandMorph(
                expanded = isMorphExpanded,
                compactSize = DpSize(targetCompactWidth, 36.dp),
                fullSize = DpSize(330.dp, measuredExpandedHeightDp),
                areAnimationsEnabled = areAnimationsEnabled,
                compact = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (!isMorphExpanded) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        requestExpand()
                                    }
                                } else Modifier
                            )
                    ) {
                        CompactIslandContent(
                            message = message,
                            settings = settings,
                            isAroundCamera = isAroundCamera,
                            cutoutWidthDp = cutoutWidthDp,
                            queuedMoreCount = (queuedCount - 1).coerceAtLeast(0)
                        )
                    }
                },
                full = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .onSizeChanged { size ->
                                val hDp = with(density) { size.height.toDp() }
                                if (hDp > 50.dp && hDp != measuredExpandedHeightDp) {
                                    measuredExpandedHeightDp = hDp
                                }
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isMorphExpanded && currentState == IslandState.EXPANDED) {
                                    onOpenConversation(message)
                                }
                            }
                    ) {
                        if (currentState == IslandState.REPLYING) {
                            ReplyingIslandContent(
                                senderName = message.senderName,
                                replyText = replyText,
                                onReplyTextChange = { replyText = it },
                                focusRequester = replyFocusRequester,
                                actionFeedbackText = actionFeedbackText,
                                onCancel = {
                                    currentState = IslandState.EXPANDED
                                },
                                onSend = {
                                    if (replyText.isNotBlank()) {
                                        actionFeedbackText = "Sent"
                                        val textToSend = replyText
                                        replyText = ""
                                        onSendReply(message, textToSend)
                                        coroutineScope.launch {
                                            delay(300)
                                            onDismiss()
                                        }
                                    }
                                }
                            )
                        } else {
                            ExpandedIslandContent(
                                message = message,
                                settings = settings,
                                queueSize = queuedCount,
                                currentIndex = currentIndex,
                                actionFeedbackText = actionFeedbackText,
                                onPagePrevious = onPagePrevious,
                                onPageNext = onPageNext,
                                onClose = onDismiss,
                                onReplyClick = {
                                    currentState = IslandState.REPLYING
                                },
                                onCopyOtpClick = {
                                    actionFeedbackText = "Copied"
                                    onCopyOtp(message)
                                    coroutineScope.launch {
                                        delay(1000)
                                        onDismiss()
                                    }
                                },
                                onMarkReadClick = {
                                    actionFeedbackText = "Read"
                                    onMarkRead(message)
                                    coroutineScope.launch {
                                        delay(300)
                                        onDismiss()
                                    }
                                },
                                onDeleteSpamClick = {
                                    actionFeedbackText = "Deleted"
                                    onDeleteSpam(message)
                                    coroutineScope.launch {
                                        delay(300)
                                        onDismiss()
                                    }
                                },
                                onOpenClick = {
                                    onOpenConversation(message)
                                    onDismiss()
                                }
                            )
                        }
                    }
                },
                onCollapsed = {
                    onCollapsed?.invoke()
                }
            )
        }
    }
}

/**
 * Compact Pill (210x36dp around camera or 176x36dp top center, 18dp corners)
 */
@Composable
fun CompactIslandContent(
    message: IslandMessage,
    settings: DynamicIslandSettings,
    isAroundCamera: Boolean,
    cutoutWidthDp: Dp,
    queuedMoreCount: Int = 0
) {
    val compactRecomp = remember { intArrayOf(0) }
    SideEffect {
        compactRecomp[0]++
        android.util.Log.d("IslandAnim", "CompactIslandContent recomposition #${compactRecomp[0]} at ${System.currentTimeMillis()}")
    }
    val theme = IslandVisualDefaults.themeFor(message.islandCategory)

    val peekString = remember(message, settings.peekText, settings.peekMaxChars, settings.hideContent) {
        formatPeekText(
            message = message,
            peekText = settings.peekText,
            maxChars = settings.peekMaxChars,
            hideContent = settings.hideContent
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left slot: Avatar, Category icon, or Nothing
        when (settings.peekLeft) {
            IslandPeekLeft.AVATAR -> {
                IslandShapedAvatar(
                    theme = theme,
                    size = 24.dp,
                    iconSize = 13.dp
                )
            }
            IslandPeekLeft.ICON -> {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = theme.icon,
                        contentDescription = null,
                        tint = theme.accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IslandPeekLeft.NOTHING -> {
                // Nothing drawn on the left
            }
        }

        val hasLeft = settings.peekLeft != IslandPeekLeft.NOTHING
        val hasText = peekString.isNotEmpty()

        // Middle gap matching camera cutout width
        if (isAroundCamera && hasLeft && hasText) {
            Spacer(modifier = Modifier.width(cutoutWidthDp.coerceIn(24.dp, 44.dp)))
        } else if (hasLeft && hasText) {
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Right slot: peek text
        if (hasText) {
            Text(
                text = peekString,
                color = IslandVisualDefaults.PillTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // +N more badge if queued messages exist
        if (queuedMoreCount > 0) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF2A2A2A),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "+$queuedMoreCount",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Expanded Island Card (330dp wide, 34dp corners)
 */
@Composable
fun ExpandedIslandContent(
    message: IslandMessage,
    settings: DynamicIslandSettings,
    queueSize: Int,
    currentIndex: Int,
    actionFeedbackText: String?,
    onPagePrevious: () -> Unit,
    onPageNext: () -> Unit,
    onClose: () -> Unit,
    onReplyClick: () -> Unit,
    onCopyOtpClick: () -> Unit,
    onMarkReadClick: () -> Unit,
    onDeleteSpamClick: () -> Unit,
    onOpenClick: () -> Unit
) {
    val expandedRecomp = remember { intArrayOf(0) }
    SideEffect {
        expandedRecomp[0]++
        android.util.Log.d("IslandAnim", "ExpandedIslandContent recomposition #${expandedRecomp[0]} at ${System.currentTimeMillis()}")
    }
    val theme = IslandVisualDefaults.themeFor(message.islandCategory)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header Row: Avatar, Name + Tag, Queue paging, Close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (settings.expAvatar) {
                IslandShapedAvatar(
                    theme = theme,
                    size = 40.dp,
                    iconSize = 22.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.senderName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (settings.expTag) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = theme.containerColor
                        ) {
                            Text(
                                text = theme.tagText,
                                color = theme.accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (settings.expTime) "now" else theme.tagText,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }

            // Paging indicator for Queue (+N more)
            if (queueSize > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = onPagePrevious,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = "Previous message",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "${currentIndex + 1} of $queueSize",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = onPageNext,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Next message",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 40dp Close Button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222222))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss island notification",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Body Content by Category
        if (actionFeedbackText != null) {
            // Action feedback banner (Copied, Read, Deleted, Sent)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = theme.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = actionFeedbackText,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        } else {
            var bodyShown = false
            when (message.islandCategory) {
                IslandCategory.OTP -> {
                    if (settings.expOtp || settings.hideContent) {
                        OtpBodySection(
                            otpCode = message.otpCode ?: "------",
                            hideContent = settings.hideContent,
                            totalDurationSeconds = message.otpExpirySeconds
                        )
                        bodyShown = true
                    }
                }
                IslandCategory.TRANSACTIONS -> {
                    if (settings.expTxn || settings.hideContent) {
                        TransactionBodySection(
                            amount = message.transactionAmount ?: "Transaction alert",
                            merchant = message.merchant ?: message.senderName,
                            accountInfo = message.accountInfo,
                            hideContent = settings.hideContent,
                            rawBody = message.body
                        )
                        bodyShown = true
                    }
                }
                else -> {
                    if (settings.expText || settings.hideContent) {
                        StandardMessageBodySection(
                            body = message.body,
                            hideContent = settings.hideContent,
                            maxLines = if (settings.expLines > 0) settings.expLines else Int.MAX_VALUE
                        )
                        bodyShown = true
                    }
                }
            }

            if (bodyShown) {
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Action Buttons Row (40dp height, 48dp touch target)
            IslandActionButtonsRow(
                message = message,
                settings = settings,
                theme = theme,
                onReplyClick = onReplyClick,
                onCopyOtpClick = onCopyOtpClick,
                onMarkReadClick = onMarkReadClick,
                onDeleteSpamClick = onDeleteSpamClick,
                onOpenClick = onOpenClick
            )
        }
    }
}

/**
 * OTP Body: Large 40sp code, animated ticking countdown bar turning red in last 30s
 */
@Composable
fun OtpBodySection(
    otpCode: String,
    hideContent: Boolean,
    totalDurationSeconds: Int
) {
    val displayCode = if (hideContent) "••••••" else otpCode

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = displayCode,
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Default,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(vertical = 2.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        OtpCountdownIndicator(totalDurationSeconds = totalDurationSeconds)
    }
}

@Composable
fun OtpCountdownIndicator(
    totalDurationSeconds: Int
) {
    var remainingSeconds by remember(totalDurationSeconds) { mutableIntStateOf(totalDurationSeconds) }

    LaunchedEffect(totalDurationSeconds) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val progress = (remainingSeconds.toFloat() / totalDurationSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    val isNearExpiry = remainingSeconds <= 30
    val progressColor = if (isNearExpiry) Color(0xFFFF5252) else Color(0xFFB8A9FF)

    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = progressColor,
            trackColor = Color(0xFF2E2A4A),
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Expires in %d:%02d".format(minutes, seconds),
            color = if (isNearExpiry) Color(0xFFFF5252) else Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            fontWeight = if (isNearExpiry) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Transaction Body: 38sp amount, merchant and account details
 */
@Composable
fun TransactionBodySection(
    amount: String,
    merchant: String,
    accountInfo: String?,
    hideContent: Boolean,
    rawBody: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (hideContent) {
            Text(
                text = "Transaction details hidden",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp
            )
        } else {
            Text(
                text = amount,
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Default
            )

            Spacer(modifier = Modifier.height(4.dp))

            val subtitle = if (!accountInfo.isNullOrBlank()) {
                "$merchant · $accountInfo"
            } else {
                merchant
            }
            Text(
                text = subtitle,
                color = Color(0xFF6FE0D0),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = rawBody,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Standard Message / Promotion / Spam Body (up to 2 lines)
 */
@Composable
fun StandardMessageBodySection(
    body: String,
    hideContent: Boolean,
    maxLines: Int = 2
) {
    val textToDisplay = if (hideContent) "New message received" else body
    Text(
        text = textToDisplay,
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 15.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 20.sp
    )
}

/**
 * Island Action Buttons Row (40dp high buttons, 48dp minimum touch target)
 */
@Composable
fun IslandActionButtonsRow(
    message: IslandMessage,
    settings: DynamicIslandSettings,
    theme: IslandCategoryTheme,
    onReplyClick: () -> Unit,
    onCopyOtpClick: () -> Unit,
    onMarkReadClick: () -> Unit,
    onDeleteSpamClick: () -> Unit,
    onOpenClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (message.islandCategory) {
            IslandCategory.OTP -> {
                if (settings.actCopy) {
                    IslandActionButton(
                        label = "Copy code",
                        icon = Icons.Rounded.ContentCopy,
                        containerColor = theme.accentColor,
                        contentColor = Color(0xFF1F1A3A),
                        isPrimary = true,
                        onClick = onCopyOtpClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    IslandActionButton(
                        label = "Open",
                        icon = Icons.Rounded.OpenInNew,
                        containerColor = theme.accentColor,
                        contentColor = Color(0xFF1F1A3A),
                        isPrimary = true,
                        onClick = onOpenClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            IslandCategory.TRANSACTIONS -> {
                IslandActionButton(
                    label = "Open",
                    icon = Icons.Rounded.OpenInNew,
                    containerColor = theme.accentColor,
                    contentColor = Color(0xFF0C2422),
                    isPrimary = true,
                    onClick = onOpenClick,
                    modifier = Modifier.weight(1f)
                )
                if (settings.actRead) {
                    IslandActionButton(
                        label = "Mark read",
                        icon = Icons.Rounded.Done,
                        containerColor = Color(0xFF222222),
                        contentColor = Color.White,
                        isPrimary = false,
                        onClick = onMarkReadClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            IslandCategory.PROMOTIONS -> {
                IslandActionButton(
                    label = "Open",
                    icon = Icons.Rounded.OpenInNew,
                    containerColor = theme.accentColor,
                    contentColor = Color(0xFF2C130B),
                    isPrimary = true,
                    onClick = onOpenClick,
                    modifier = Modifier.weight(1f)
                )
                if (settings.actRead) {
                    IslandActionButton(
                        label = "Mark read",
                        icon = Icons.Rounded.Done,
                        containerColor = Color(0xFF222222),
                        contentColor = Color.White,
                        isPrimary = false,
                        onClick = onMarkReadClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            IslandCategory.SPAM -> {
                IslandActionButton(
                    label = "Delete",
                    icon = Icons.Rounded.Delete,
                    containerColor = theme.accentColor,
                    contentColor = Color(0xFF330E10),
                    isPrimary = true,
                    onClick = onDeleteSpamClick,
                    modifier = Modifier.weight(1f)
                )
                if (settings.actRead) {
                    IslandActionButton(
                        label = "Mark read",
                        icon = Icons.Rounded.Done,
                        containerColor = Color(0xFF222222),
                        contentColor = Color.White,
                        isPrimary = false,
                        onClick = onMarkReadClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            IslandCategory.MESSAGES -> {
                if (settings.actReply) {
                    IslandActionButton(
                        label = "Reply",
                        icon = Icons.Rounded.Reply,
                        containerColor = theme.accentColor,
                        contentColor = Color(0xFF141F00),
                        isPrimary = true,
                        onClick = onReplyClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    IslandActionButton(
                        label = "Open",
                        icon = Icons.Rounded.OpenInNew,
                        containerColor = theme.accentColor,
                        contentColor = Color(0xFF141F00),
                        isPrimary = true,
                        onClick = onOpenClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (settings.actRead) {
                    IslandActionButton(
                        label = "Mark read",
                        icon = Icons.Rounded.Done,
                        containerColor = Color(0xFF222222),
                        contentColor = Color.White,
                        isPrimary = false,
                        onClick = onMarkReadClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 40dp High Button with 48dp minimum touch target
 */
@Composable
fun IslandActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(20.dp),
            color = containerColor,
            contentColor = contentColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Replying mode: Inline TextField with Send Button
 */
@Composable
fun ReplyingIslandContent(
    senderName: String,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    focusRequester: FocusRequester,
    actionFeedbackText: String?,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Cancel reply",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Reply to $senderName",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Default,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (actionFeedbackText != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionFeedbackText,
                    color = Color(0xFFCDF564),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // Text Input Box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF1E1E1E)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (replyText.isEmpty()) {
                            Text(
                                text = "Type a message...",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = replyText,
                            onValueChange = onReplyTextChange,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(Color.White),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onSend,
                        enabled = replyText.isNotBlank(),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (replyText.isNotBlank()) Color(0xFFCDF564) else Color(0xFF333333)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send SMS",
                            tint = if (replyText.isNotBlank()) Color(0xFF141F00) else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Category-shaped Avatar using fixed shapes and colors
 */
@Composable
fun IslandShapedAvatar(
    theme: IslandCategoryTheme,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val icon = when (theme.category) {
        IslandCategory.OTP -> Icons.Rounded.Lock
        IslandCategory.TRANSACTIONS -> Icons.Rounded.AccountBalance
        IslandCategory.PROMOTIONS -> Icons.Rounded.LocalOffer
        IslandCategory.SPAM -> Icons.Rounded.Shield
        IslandCategory.MESSAGES -> Icons.Rounded.Person
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(theme.shape)
            .background(theme.containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.accentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
