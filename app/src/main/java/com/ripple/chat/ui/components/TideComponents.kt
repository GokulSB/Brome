package com.ripple.chat.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Telephony
import com.ripple.chat.R
import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.MessageDeliveryStatus
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.theme.*
import com.ripple.chat.ui.AppStrings
import com.ripple.chat.ui.chat.extractAmount
import com.ripple.chat.ui.chat.formatAmountCompact
import com.ripple.chat.ui.chat.formatTime
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// 1. TOP ROW: SEARCH PILL + RECYCLE BIN + SETTINGS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TideTopBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onTrashClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearance = com.ripple.chat.theme.LocalBromeAppearance.current
    val hintText = appearance.searchHintText.ifBlank { stringResource(R.string.search_messages) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search Pill (52dp high, follows corner settings)
        Surface(
            shape = RoundedCornerShape(appearance.cornerRadius.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = hintText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FigtreeFontFamily,
                            fontSize = 15.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FigtreeFontFamily,
                            fontSize = 15.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onSearchChange("") }
                    )
                }
            }
        }

        // Trash Button (52dp, follows corner settings)
        Surface(
            onClick = onTrashClick,
            shape = RoundedCornerShape(appearance.cornerRadius.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(52.dp).pressScale()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.trash_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Settings Button (52dp, follows corner settings)
        Surface(
            onClick = onSettingsClick,
            shape = RoundedCornerShape(appearance.cornerRadius.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(52.dp).pressScale()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1B. SELECTION MODE TOP BAR
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TideSelectionTopBar(
    selectedCount: Int,
    isAllSelected: Boolean = false,
    onCloseClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearance = com.ripple.chat.theme.LocalBromeAppearance.current
    val haptic = LocalHapticFeedback.current
    val selectAllTooltip = if (isAllSelected) "Deselect all" else "Select all"
    val selectAllTooltipState = rememberTooltipState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCloseClick()
                },
                shape = RoundedCornerShape(appearance.cornerRadius.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .size(52.dp)
                    .pressScale()
                    .semantics {
                        role = Role.Button
                        contentDescription = "Exit Selection"
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Exit Selection",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "$selectedCount selected",
                fontFamily = BricolageFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                }
            )
        }

        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                PlainTooltip {
                    Text(
                        text = selectAllTooltip,
                        fontFamily = FigtreeFontFamily,
                        fontSize = 12.sp
                    )
                }
            },
            state = selectAllTooltipState
        ) {
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectAllClick()
                },
                shape = RoundedCornerShape(appearance.cornerRadius.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .size(52.dp)
                    .pressScale()
                    .semantics {
                        role = Role.Button
                        contentDescription = selectAllTooltip
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = isAllSelected,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)))
                                .togetherWith(fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.85f, animationSpec = tween(200)))
                        },
                        label = "TopBarSelectAllCrossfade"
                    ) { allSelectedState ->
                        Icon(
                            imageVector = if (allSelectedState) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                            contentDescription = if (allSelectedState) "Deselect all" else "Select all",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. CATEGORIES 2x2 GRID + SHOW MORE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TideCategoryGrid(
    counts: Map<String, Int>,
    selectedCategoryKey: String?,
    onCategoryClick: (String?) -> Unit,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val appearance = com.ripple.chat.theme.LocalBromeAppearance.current
    val themeConfig = com.ripple.chat.theme.LocalThemeConfig.current
    var isExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevronRotation"
    )

    val categoryOrder = themeConfig?.categoryOrder ?: com.ripple.chat.data.ThemeSettings.DEFAULT_CATEGORY_ORDER
    val enabledKeys = themeConfig?.enabledCategories ?: com.ripple.chat.data.ThemeSettings.DEFAULT_CATEGORIES
    val customCatsMap = remember(themeConfig?.customCategories) {
        themeConfig?.customCategories?.associateBy { it.id } ?: emptyMap()
    }

    // Preserve the user's arranged category order and filter by enabled categories
    val filteredCategories = remember(categoryOrder, enabledKeys) {
        categoryOrder.filter { enabledKeys.contains(it) }
    }

    val cols = appearance.categoryGridColumns.coerceIn(2, 4)
    val rowsCount = appearance.categoryGridRows.coerceIn(1, 3)
    val initialLimit = cols * rowsCount

    val visibleCategories = if (isExpanded) filteredCategories else filteredCategories.take(initialLimit)
    val rows = visibleCategories.chunked(cols)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (dimmed) 0.4f else 1f }
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.categories_header),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (filteredCategories.size > initialLimit) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !dimmed) { isExpanded = !isExpanded }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_more),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FigtreeFontFamily
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp).rotate(chevronRotation)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEachIndexed { rowIndex, rowCats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowCats.forEachIndexed { colIndex, catKey ->
                        val globalIndex = rowIndex * cols + colIndex
                        val isSelected = selectedCategoryKey == catKey
                        val customCat = customCatsMap[catKey]
                        val customCatColor = themeConfig?.categoryColors?.get(catKey) ?: customCat?.color
                        val customIcon = customCat?.let { com.ripple.chat.theme.CategoryIcons.getIcon(it.iconName) }

                        val catQuad = BromeCategoryColors.getColorsForCategory(
                            categoryKey = catKey,
                            isDark = dark,
                            customColorInt = customCatColor,
                            customIcon = customIcon
                        )
                        val newCount = counts[catKey] ?: 0

                        val catName = when {
                            customCat != null -> customCat.name
                            else -> AppStrings.categoryName(catKey)
                        }

                        val cardShape = rememberTideCornerShape(
                            index = globalIndex,
                            baseRadius = (appearance.cornerRadius * 0.8f).coerceAtLeast(12f).dp,
                            sharpRadius = ((appearance.cornerRadius * 0.8f) * 0.33f).coerceAtMost(10f).dp,
                            keepOneSharp = appearance.keepOneSharpCorner
                        )

                        val cardHeight = when (cols) {
                            4 -> 72.dp
                            3 -> 62.dp
                            else -> 68.dp
                        }

                        Surface(
                            onClick = {
                                if (!dimmed) {
                                    if (isExpanded) isExpanded = false
                                    onCategoryClick(if (isSelected) null else catKey)
                                }
                            },
                            shape = cardShape,
                            color = catQuad.container,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.5.dp, catQuad.accent) else null,
                            modifier = Modifier
                                .weight(1f)
                                .height(cardHeight)
                                .then(if (!dimmed) Modifier.pressScale() else Modifier)
                        ) {
                            if (cols == 4) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(catQuad.shape)
                                            .background(catQuad.accent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = catQuad.icon,
                                            contentDescription = null,
                                            tint = catQuad.onAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = catName,
                                        fontFamily = BricolageFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = catQuad.onContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    val countText = if (catKey == "UNREAD") "$newCount new" else "$newCount"
                                    Text(
                                        text = countText,
                                        fontFamily = FigtreeFontFamily,
                                        fontSize = 10.sp,
                                        color = catQuad.onContainer.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else if (cols == 3) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(catQuad.shape)
                                            .background(catQuad.accent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = catQuad.icon,
                                            contentDescription = null,
                                            tint = catQuad.onAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = catName,
                                            fontFamily = BricolageFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = catQuad.onContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val countText = if (catKey == "UNREAD") "$newCount new" else "$newCount msgs"
                                        Text(
                                            text = countText,
                                            fontFamily = FigtreeFontFamily,
                                            fontSize = 10.sp,
                                            color = catQuad.onContainer.copy(alpha = 0.75f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(catQuad.shape)
                                            .background(catQuad.accent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = catQuad.icon,
                                            contentDescription = null,
                                            tint = catQuad.onAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = catName,
                                            fontFamily = BricolageFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = catQuad.onContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val countText = if (catKey == "UNREAD") {
                                            "$newCount new"
                                        } else {
                                            if (newCount == 1) "1 message" else "$newCount messages"
                                        }
                                        Text(
                                            text = countText,
                                            fontFamily = FigtreeFontFamily,
                                            fontSize = 12.sp,
                                            color = catQuad.onContainer.copy(alpha = 0.75f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (rowCats.size < cols) {
                        repeat(cols - rowCats.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. MESSAGE CARD (84dp Min Height, Sharp Corner Rotation & Selection Mode)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TideMessageRow(
    thread: ChatThread,
    index: Int,
    searchQuery: String,
    isSelected: Boolean = false,
    isStarred: Boolean = false,
    isUnreadTab: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val dark = isSystemInDarkTheme()
    val isUnread = !thread.read
    val displayName = thread.contactName ?: thread.address
    val bodyText = thread.body

    val themeConfig = com.ripple.chat.theme.LocalThemeConfig.current
    val customCatColor = themeConfig?.categoryColors?.get(when (thread.category) {
        SmsCategory.PROMOTIONAL -> "PROMOTIONS"
        else -> thread.category.name
    })
    val catQuad = BromeCategoryColors.getColors(thread.category, dark, customCatColor)
    val cardShape = rememberTideCornerShape(index = index)

    // OTP detection
    val otpCode = remember(bodyText, thread.category) {
        if (thread.category == SmsCategory.OTP || bodyText.contains("OTP", ignoreCase = true)) {
            Regex("\\b\\d{4,8}\\b").find(bodyText)?.value
        } else null
    }

    // Transaction amount
    val (amountText, isDebit) = remember(bodyText) {
        val lower = bodyText.lowercase()
        val hasDebit = lower.contains("debit") || lower.contains("deducted") || lower.contains("sent") || lower.contains("paid")
        val hasCredit = lower.contains("credit") || lower.contains("received") || lower.contains("deposited")
        if (hasDebit || hasCredit) {
            val amount = extractAmount(lower)
            if (amount > 0) {
                Pair("${if (hasCredit) "+" else "−"}₹${formatAmountCompact(amount)}", hasDebit)
            } else Pair(null, null)
        } else Pair(null, null)
    }

    var isCopied by remember { mutableStateOf(false) }
    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(1200)
            isCopied = false
        }
    }

    val cardBg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isUnread -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        shape = cardShape,
        color = cardBg,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .pressScale()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .semantics {
                this.contentDescription = "Message from $displayName"
                this.customActions = listOf(
                    CustomAccessibilityAction("Select") { onLongClick(); true }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 48dp Avatar / Selected Check Morph
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(if (isSelected) Cookie8Shape else if (thread.category == SmsCategory.PERSONAL) Flower5Shape else catQuad.shape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else if (thread.category == SmsCategory.PERSONAL) MaterialTheme.colorScheme.primary else catQuad.accent),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (!thread.contactPhotoUri.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = thread.contactPhotoUri,
                        contentDescription = displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    if (thread.category == SmsCategory.PERSONAL) {
                        Text(
                            text = displayName.trim().firstOrNull()?.uppercase() ?: "?",
                            fontFamily = BricolageFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = catQuad.icon,
                            contentDescription = null,
                            tint = catQuad.onAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Line 1: Sender + Category Tag Pill + Time + Star/Accent Dot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    ) {
                        Text(
                            text = displayName,
                            fontFamily = BricolageFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Explicit UNREAD text pill if in UNREAD tab or unread message
                        if (isUnreadTab || isUnread) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(20.dp)
                            ) {
                                Text(
                                    text = "UNREAD",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Respective category context tag pill (e.g. TRANSACTION, TICKETS, etc.)
                        val contextCatLabel = when (thread.category) {
                            SmsCategory.FINANCE -> "TRANSACTION"
                            SmsCategory.PROMOTIONAL -> "PROMO"
                            else -> thread.category.name
                        }
                        Surface(
                            shape = CircleShape,
                            color = catQuad.container,
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text(
                                text = contextCatLabel.take(7).uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = catQuad.onContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatTime(thread.date),
                            fontSize = 12.sp,
                            fontFamily = FigtreeFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        if (isStarred) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = "Starred",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(catQuad.accent)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Line 2: Message preview / OTP code / Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (otpCode != null) {
                        val formattedCode = otpCode.chunked(3).joinToString(" ")
                        Text(
                            text = formattedCode,
                            fontFamily = BricolageFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = 1.5.sp,
                            color = catQuad.onContainer
                        )

                        Surface(
                            onClick = {
                                copyToClipboard(context, otpCode)
                                isCopied = true
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCopied) "Copied" else "Copy",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    } else {
                        val isOutgoing = thread.lastMessageIsFromMe || thread.type != Telephony.Sms.MESSAGE_TYPE_INBOX
                        val displayPreview = if (isOutgoing) "You: $bodyText" else bodyText

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isOutgoing) {
                                when (thread.deliveryStatus) {
                                    MessageDeliveryStatus.PENDING -> {
                                        Icon(
                                            imageVector = Icons.Rounded.Schedule,
                                            contentDescription = "Pending",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    MessageDeliveryStatus.SENT -> {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Sent",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
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
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Delivered",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp).offset(x = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    MessageDeliveryStatus.FAILED -> {
                                        Icon(
                                            imageVector = Icons.Rounded.ErrorOutline,
                                            contentDescription = "Failed",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                }
                            }
                            Text(
                                text = displayPreview,
                                fontFamily = FigtreeFontFamily,
                                fontSize = 14.sp,
                                color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (amountText != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = amountText,
                                fontFamily = BricolageFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isDebit == false) catQuad.accent else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isUnread) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val countText = if (thread.unreadCount > 99) "99+" else if (thread.unreadCount > 0) "${thread.unreadCount}" else "1"
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = countText,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. FLOATING SELECTION ACTION BAR (PART 1)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TideSelectionIconButton(
    icon: ImageVector,
    contentDescription: String,
    tooltipText: String,
    containerColor: Color,
    contentColor: Color,
    buttonSize: Dp,
    buttonWidth: Dp = buttonSize,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tooltipState = rememberTooltipState()
    val haptic = LocalHapticFeedback.current

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(
                    text = tooltipText,
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.sp
                )
            }
        },
        state = tooltipState
    ) {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            shape = CircleShape,
            color = containerColor,
            modifier = modifier
                .size(width = buttonWidth, height = buttonSize)
                .minimumInteractiveComponentSize()
                .pressScale()
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = icon,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)))
                            .togetherWith(fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.85f, animationSpec = tween(200)))
                    },
                    label = "SelectionBarIconTransition"
                ) { currentIcon ->
                    Icon(
                        imageVector = currentIcon,
                        contentDescription = contentDescription,
                        tint = contentColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Delete $count conversations?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = "These conversations will be moved to the recycle bin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = CircleShape
            ) {
                Text(
                    text = "Delete",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Medium
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. FLOATING SELECTION ACTION BAR (ICON-ONLY WITH SELECT ALL / DESELECT ALL)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TideSelectionActionBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    onSelectAllToggle: () -> Unit,
    allStarred: Boolean,
    isArchiveScreen: Boolean = false,
    isStarredScreen: Boolean = false,
    isSpamTab: Boolean = false,
    hasUnreadSelected: Boolean = false,
    onMarkReadClick: (() -> Unit)? = null,
    onMarkUnreadClick: (() -> Unit)? = null,
    onNotSpamClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onStarClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onOverflowClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appearance = com.ripple.chat.theme.LocalBromeAppearance.current
    val scale = appearance.selectionBarScale.coerceIn(0.70f, 1.0f)
    val t = ((scale - 0.70f) / 0.30f).coerceIn(0f, 1f)

    // Bar height does not reduce - stays constant at 68dp with CircleShape pill shape
    val barHeight = 68.dp

    // As scale reduces: buttons shrink, gaps reduce, icons shrink, shrinking horizontally towards center
    val buttonSize = (38f + 12f * t).dp
    val extraPillWidth = (12f + 8f * t).dp
    val pillWidth = buttonSize + extraPillWidth
    val gap = (3f + 5f * t).dp
    val horizontalPadding = (6f + 4f * t).dp
    val iconSize = (16f + 8f * t).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp,
            modifier = Modifier
                .height(barHeight)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Select all / Deselect all
                val selectAllIcon = if (isAllSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll
                val selectAllLabel = if (isAllSelected) "Deselect all" else "Select all"
                TideSelectionIconButton(
                    icon = selectAllIcon,
                    contentDescription = selectAllLabel,
                    tooltipText = selectAllLabel,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                    onClick = onSelectAllToggle
                )

                // 2. Mark read / unread
                val isMarkReadAction = hasUnreadSelected
                val readIcon = if (isMarkReadAction) Icons.Rounded.MarkEmailRead else Icons.Rounded.MarkEmailUnread
                val readLabel = if (isMarkReadAction) "Mark as read" else "Mark as unread"
                TideSelectionIconButton(
                    icon = readIcon,
                    contentDescription = readLabel,
                    tooltipText = readLabel,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                    onClick = {
                        if (isMarkReadAction) onMarkReadClick?.invoke() else onMarkUnreadClick?.invoke()
                    }
                )

                // 3. Star / Unstar
                val isStarred = allStarred || isStarredScreen
                val starIcon = if (isStarred) Icons.Rounded.Star else Icons.Rounded.StarOutline
                val starLabel = if (isStarred) "Unstar" else "Star"
                val starContentColor = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                TideSelectionIconButton(
                    icon = starIcon,
                    contentDescription = starLabel,
                    tooltipText = starLabel,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = starContentColor,
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                    onClick = onStarClick
                )

                // 4. Archive / Unarchive (Primary pill action, slightly wider)
                val archiveIcon = when {
                    isSpamTab -> Icons.Rounded.CheckCircle
                    isArchiveScreen -> Icons.Rounded.Unarchive
                    else -> Icons.Rounded.Archive
                }
                val archiveLabel = when {
                    isSpamTab -> "Not spam"
                    isArchiveScreen -> "Unarchive"
                    else -> "Archive"
                }
                TideSelectionIconButton(
                    icon = archiveIcon,
                    contentDescription = archiveLabel,
                    tooltipText = archiveLabel,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    buttonSize = buttonSize,
                    buttonWidth = pillWidth,
                    iconSize = iconSize,
                    onClick = {
                        if (isSpamTab) onNotSpamClick?.invoke() ?: onArchiveClick() else onArchiveClick()
                    }
                )

                // 5. Delete (Red error container circle)
                TideSelectionIconButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tooltipText = "Delete",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                    onClick = { onDeleteClick?.invoke() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. UNDO SNACKBAR

// ─────────────────────────────────────────────────────────────────────────────
// 5. UNDO SNACKBAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TideUndoSnackbar(
    message: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )

            TextButton(onClick = onUndo) {
                Text(
                    text = "Undo",
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. SPAM STRIP
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TideSpamStrip(
    blockedCount: Int = 9,
    onReviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val spamQuad = BromeCategoryColors.getColors(SmsCategory.SPAM, dark)

    Surface(
        shape = CircleShape,
        color = spamQuad.container,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(Sunny12Shape)
                        .background(spamQuad.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = spamQuad.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                val reviewText = if (blockedCount == 1) {
                    "1 spam message needs review"
                } else {
                    "$blockedCount spam messages need review"
                }
                Text(
                    text = reviewText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FigtreeFontFamily,
                    color = spamQuad.onContainer
                )
            }

            Surface(
                onClick = onReviewClick,
                shape = CircleShape,
                color = spamQuad.accent
            ) {
                Text(
                    text = "Review",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FigtreeFontFamily,
                    color = spamQuad.onAccent,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. NAV PILL & COMPOSE FAB
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TideFloatingNavAndFab(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onComposeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearance = com.ripple.chat.theme.LocalBromeAppearance.current
    val navHeight = appearance.navPillHeightDp.dp
    val fabSizeDp = appearance.fabSizeDp.dp
    val fabCornerDp = (appearance.fabSizeDp * 0.36f).dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Floating Nav Pill (content-width, surfaceContainer)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp,
            modifier = Modifier.height(navHeight)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val navItems = listOf(
                    Icons.Rounded.Message to stringResource(R.string.nav_messages),
                    Icons.Rounded.Star to stringResource(R.string.nav_starred),
                    Icons.Rounded.Contacts to stringResource(R.string.nav_contacts),
                    Icons.Rounded.Archive to stringResource(R.string.nav_archive)
                )

                val itemHeight = (navHeight - 12.dp).coerceAtLeast(32.dp)
                val selectedIconSize = (itemHeight * 0.52f).coerceIn(16.dp, 24.dp)
                val unselectedIconSize = (itemHeight * 0.46f).coerceIn(14.dp, 22.dp)

                navItems.forEachIndexed { index, (icon, label) ->
                    val isSelected = selectedIndex == index
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(width = itemHeight + 12.dp, height = itemHeight)
                                .pressScale()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(selectedIconSize)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(itemHeight)
                                .clip(CircleShape)
                                .clickable { onTabSelected(index) }
                                .pressScale(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(unselectedIconSize)
                            )
                        }
                    }
                }
            }
        }

        // Separate Compose FAB
        val isPressedState = remember { MutableInteractionSource() }
        val isPressed by isPressedState.collectIsPressedAsState()
        val rotationAngle by animateFloatAsState(if (isPressed) 12f else 0f, label = "fabRotate")
        val fabIconSize = (fabSizeDp * 0.40f).coerceIn(18.dp, 26.dp)

        Surface(
            onClick = onComposeClick,
            shape = RoundedCornerShape(fabCornerDp),
            color = MaterialTheme.colorScheme.tertiary,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(fabSizeDp)
                .pressScale(scaleFactor = 0.92f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.nav_compose),
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier
                        .size(fabIconSize)
                        .rotate(rotationAngle)
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Code", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        BromeToast.show(context, "Copied: $text")
    }
}
