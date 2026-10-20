package com.ripple.chat.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ripple.chat.data.BromeSettings
import com.ripple.chat.data.DynamicIslandSettings
import com.ripple.chat.data.IslandPeekLeft
import com.ripple.chat.data.IslandPeekText
import com.ripple.chat.data.IslandSound
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.island.CompactIslandContent
import com.ripple.chat.island.DynamicIslandService
import com.ripple.chat.island.IslandMessage
import com.ripple.chat.island.IslandNotifier
import com.ripple.chat.island.IslandVisualDefaults
import com.ripple.chat.theme.Clover3Shape
import com.ripple.chat.theme.Cookie8Shape
import com.ripple.chat.theme.Flower5Shape
import com.ripple.chat.theme.Sunny12Shape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class IslandSubPage {
    POSITION_AND_CONTENT
}

enum class PositionContentBlock {
    POSITION,
    PEEK_MODE,
    EXPANDED_VIEW
}

fun triggerSampleIsland(
    context: Context,
    type: String, // "BANK", "OTP", "LONG_TEXT"
    isExpanded: Boolean
) {
    if (Settings.canDrawOverlays(context)) {
        val intent = Intent(context, DynamicIslandService::class.java).apply {
            action = DynamicIslandService.ACTION_SHOW_SAMPLE
            putExtra(DynamicIslandService.EXTRA_SAMPLE_TYPE, type)
            putExtra(DynamicIslandService.EXTRA_SAMPLE_EXPANDED, isExpanded)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicIslandDetailPage(
    bromeSettings: BromeSettings,
    islandSettings: DynamicIslandSettings,
    coroutineScope: CoroutineScope,
    onBack: () -> Unit
) {
    var activeSubPage by remember { mutableStateOf<IslandSubPage?>(null) }
    var targetBlock by remember { mutableStateOf(PositionContentBlock.POSITION) }

    if (activeSubPage == IslandSubPage.POSITION_AND_CONTENT) {
        PositionAndContentSettingsPage(
            bromeSettings = bromeSettings,
            islandSettings = islandSettings,
            initialBlock = targetBlock,
            coroutineScope = coroutineScope,
            onBack = { activeSubPage = null }
        )
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Re-check permission onResume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val allowed = Settings.canDrawOverlays(context)
                hasOverlayPermission = allowed
                if (!allowed && islandSettings.enabled) {
                    coroutineScope.launch {
                        bromeSettings.setIslandEnabled(false)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission Explanation Dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Text(
                    text = "Display over other apps",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default
                )
            },
            text = {
                Text(
                    "Allow Brome to display over other apps? Brome uses this to show the island at the top of the screen when a message arrives. You can turn it off any time."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    }
                ) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Not now")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(
            title = "Dynamic island",
            subtitle = "A pill at the top of the screen when a message arrives",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // Master Card with Live Mini Preview
            MasterIslandPreviewCard(
                islandEnabled = islandSettings.enabled && hasOverlayPermission,
                onToggle = { enable ->
                    if (enable) {
                        if (!hasOverlayPermission) {
                            showPermissionDialog = true
                        } else {
                            coroutineScope.launch {
                                bromeSettings.setIslandEnabled(true)
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            bromeSettings.setIslandEnabled(false)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            val isIslandActive = islandSettings.enabled && hasOverlayPermission

            if (isIslandActive) {
                // Permission Status Row
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Display over other apps: Allowed",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Manage", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Block: Show For (Category Chips)
                SettingBlock(
                    title = "Show for",
                    description = "Choose which categories trigger the island notification",
                    icon = Icons.Rounded.Category,
                    iconShape = Clover3Shape,
                    cardShape = RoundedCornerShape(20.dp)
                ) {
                    val categories = listOf(
                        "MESSAGES" to "Messages",
                        "PERSONAL" to "Personal",
                        "OTP" to "OTP",
                        "FINANCE" to "Transactions",
                        "PROMOTIONAL" to "Promotions",
                        "SPAM" to "Spam"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.chunked(2).forEach { rowCats ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowCats.forEach { (key, label) ->
                                    val isChecked = key in islandSettings.showFor
                                    CategoryFilterChip(
                                        label = label,
                                        selected = isChecked,
                                        onClick = {
                                            val newSet = islandSettings.showFor.toMutableSet()
                                            if (isChecked) newSet.remove(key) else newSet.add(key)
                                            coroutineScope.launch {
                                                bromeSettings.setIslandShowFor(newSet)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowCats.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Block: Duration
                SettingBlock(
                    title = "Duration",
                    description = "How long the compact pill stays before disappearing",
                    icon = Icons.Rounded.Timer,
                    iconShape = Sunny12Shape,
                    cardShape = RoundedCornerShape(20.dp)
                ) {
                    val durations = listOf(
                        3 to "3 s",
                        5 to "5 s",
                        8 to "8 s",
                        -1 to "Until dismissed"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        durations.chunked(2).forEach { rowDurs ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowDurs.forEach { (sec, label) ->
                                    val isSelected = islandSettings.duration == sec
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            coroutineScope.launch {
                                                bromeSettings.setIslandDuration(sec)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowDurs.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Block: Size
                SettingBlock(
                    title = "Size",
                    description = "Scale the island pill dimensions",
                    icon = Icons.Rounded.Crop,
                    iconShape = Cookie8Shape,
                    cardShape = RoundedCornerShape(20.dp)
                ) {
                    val sizes = listOf(
                        "COMPACT" to "Compact (0.9×)",
                        "DEFAULT" to "Default (1×)",
                        "LARGE" to "Large (1.1×)"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sizes.forEach { (key, label) ->
                            val isSelected = islandSettings.size.uppercase() == key
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    coroutineScope.launch {
                                        bromeSettings.setIslandSize(key)
                                        triggerSampleIsland(context, "BANK", isExpanded = false)
                                    }
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Block: Position and content (Nav rows to subpage)
                SettingBlock(
                    title = "Position and content",
                    description = "Configure screen placement, peek layout, and expanded details",
                    icon = Icons.Rounded.DashboardCustomize,
                    iconShape = Cookie8Shape,
                    cardShape = RoundedCornerShape(20.dp)
                ) {
                    IslandNavRow(
                        title = "Position",
                        subtitle = "Camera alignment, horizontal & vertical offsets",
                        icon = Icons.Rounded.Tune,
                        onClick = {
                            targetBlock = PositionContentBlock.POSITION
                            activeSubPage = IslandSubPage.POSITION_AND_CONTENT
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    IslandNavRow(
                        title = "Peek mode",
                        subtitle = "Avatar style, smart text, length limits",
                        icon = Icons.Rounded.Visibility,
                        onClick = {
                            targetBlock = PositionContentBlock.PEEK_MODE
                            activeSubPage = IslandSubPage.POSITION_AND_CONTENT
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    IslandNavRow(
                        title = "Expanded view",
                        subtitle = "Visible elements, message lines, OTP details",
                        icon = Icons.Rounded.AspectRatio,
                        onClick = {
                            targetBlock = PositionContentBlock.EXPANDED_VIEW
                            activeSubPage = IslandSubPage.POSITION_AND_CONTENT
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Block: Actions and Behavior
                SettingBlock(
                    title = "Actions and behavior",
                    description = "Choose which interactive controls are available on the island",
                    icon = Icons.Rounded.TouchApp,
                    iconShape = Flower5Shape,
                    cardShape = RoundedCornerShape(20.dp)
                ) {
                    IslandSwitchRow(
                        title = "Expand on tap",
                        subtitle = "Tapping the pill springs out the expanded message card",
                        checked = islandSettings.actExpand,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { bromeSettings.setIslandActExpand(checked) }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    IslandSwitchRow(
                        title = "Reply action",
                        subtitle = "Inline SMS reply field directly from the island",
                        checked = islandSettings.actReply,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { bromeSettings.setIslandActReply(checked) }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    IslandSwitchRow(
                        title = "Copy code action",
                        subtitle = "One-tap OTP copy with sensitive clipboard flag",
                        checked = islandSettings.actCopy,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { bromeSettings.setIslandActCopy(checked) }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    IslandSwitchRow(
                        title = "Mark read action",
                        subtitle = "Dismisses the notification and marks the message as read",
                        checked = islandSettings.actRead,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { bromeSettings.setIslandActRead(checked) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Block: Privacy
                SettingBlock(
                    title = "Privacy",
                    description = "Safeguard sensitive content and manage distraction",
                    icon = Icons.Rounded.Security,
                    iconShape = Sunny12Shape,
                    cardShape = RoundedCornerShape(20.dp)
                ) {
                    IslandSwitchRow(
                        title = "Hide message content",
                        subtitle = "Shows only sender or category name; hides text & codes until tapped",
                        checked = islandSettings.hideContent,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { bromeSettings.setIslandHideContent(checked) }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    IslandSwitchRow(
                        title = "Skip when app open",
                        subtitle = "Shows small in-app banner instead of overlay when Brome is open",
                        checked = islandSettings.skipWhenOpen,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { bromeSettings.setIslandSkipWhenOpen(checked) }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    IslandSwitchRow(
                        title = "Respect Do Not Disturb",
                        subtitle = "Suppress island notifications when DND mode is turned on",
                        checked = islandSettings.respectDnd,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { bromeSettings.setIslandRespectDnd(checked) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Block: Notification handling
                SettingBlock(
                    title = "Notification handling",
                    description = "The island replaces the standard notification while it can show",
                    icon = Icons.Rounded.Notifications,
                    iconShape = Cookie8Shape,
                    cardShape = RoundedCornerShape(20.dp)
                ) {
                    IslandSwitchRow(
                        title = "Keep a silent copy",
                        subtitle = "Adds a quiet notification to the shade, with no sound or pop-up",
                        checked = islandSettings.keepSilentCopy,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { bromeSettings.setIslandKeepSilentCopy(checked) }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = "Sound and vibration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Default,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose whether the island plays the message sound and vibrates",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = islandSettings.sound == IslandSound.USE_MESSAGE_SOUND,
                            onClick = {
                                coroutineScope.launch { bromeSettings.setIslandSound(IslandSound.USE_MESSAGE_SOUND) }
                            },
                            label = { Text("Use message sound") },
                            leadingIcon = if (islandSettings.sound == IslandSound.USE_MESSAGE_SOUND) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = islandSettings.sound == IslandSound.SILENT,
                            onClick = {
                                coroutineScope.launch { bromeSettings.setIslandSound(IslandSound.SILENT) }
                            },
                            label = { Text("Silent") },
                            leadingIcon = if (islandSettings.sound == IslandSound.SILENT) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "If the island cannot show (permission, locked screen, Do Not Disturb, category off) you get the standard notification instead.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Show Preview Button
                var previewCategoryIndex by remember { mutableIntStateOf(0) }
                Button(
                    onClick = {
                        val sampleCategories = listOf(
                            SmsCategory.OTP to ("Google" to "Your verification code is 849201. Do not share."),
                            SmsCategory.FINANCE to ("HDFC Bank" to "Rs 1,450.00 debited from A/c **4120 at Swiggy on 16-Oct."),
                            SmsCategory.PERSONAL to ("Sarah Jenkins" to "Hey, are you free for lunch today at 1?"),
                            SmsCategory.PROMOTIONAL to ("Zomato" to "Flat 50% OFF up to Rs 100 on your next 3 orders!"),
                            SmsCategory.SPAM to ("Lucky Draw" to "You won 10,00,000 cash prize! Click link to claim now.")
                        )
                        val (cat, pair) = sampleCategories[previewCategoryIndex % sampleCategories.size]
                        previewCategoryIndex++

                        val sampleMsg = IslandMessage(
                            context = context,
                            address = pair.first,
                            senderName = pair.first,
                            body = pair.second,
                            category = cat,
                            otpCode = if (cat == SmsCategory.OTP) "849201" else null,
                            transactionAmount = if (cat == SmsCategory.FINANCE) "₹1,450.00" else null,
                            merchant = if (cat == SmsCategory.FINANCE) "Swiggy" else null,
                            accountInfo = if (cat == SmsCategory.FINANCE) "A/c **4120" else null
                        )

                        IslandNotifier.onMessageReceived(sampleMsg)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Show preview", fontWeight = FontWeight.Bold)
                }
            } else {
                // When switch is OFF: Short explanation
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Dynamic island is turned off",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Turn on the switch above to enable pill notifications around the camera cutout and configure categories, duration, size, and actions.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Standard Notification Note
            Text(
                text = "When the island shows, the standard notification is not sent. If the island cannot show, you still get the standard notification.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Subpage for Fine-Tuning Island Position, Peek Mode, and Expanded View
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionAndContentSettingsPage(
    bromeSettings: BromeSettings,
    islandSettings: DynamicIslandSettings,
    initialBlock: PositionContentBlock,
    coroutineScope: CoroutineScope,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var selectedQuickJump by remember { mutableStateOf(initialBlock) }

    LaunchedEffect(initialBlock) {
        when (initialBlock) {
            PositionContentBlock.POSITION -> scrollState.animateScrollTo(0)
            PositionContentBlock.PEEK_MODE -> scrollState.animateScrollTo(550)
            PositionContentBlock.EXPANDED_VIEW -> scrollState.animateScrollTo(1200)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(
            title = "Position and content",
            subtitle = "Fine-tune island placement and information density",
            onBack = onBack
        )

        // Quick-jump chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedQuickJump == PositionContentBlock.POSITION,
                onClick = {
                    selectedQuickJump = PositionContentBlock.POSITION
                    coroutineScope.launch { scrollState.animateScrollTo(0) }
                },
                label = { Text("Position", fontSize = 12.sp, fontWeight = if (selectedQuickJump == PositionContentBlock.POSITION) FontWeight.Bold else FontWeight.Normal) }
            )
            FilterChip(
                selected = selectedQuickJump == PositionContentBlock.PEEK_MODE,
                onClick = {
                    selectedQuickJump = PositionContentBlock.PEEK_MODE
                    coroutineScope.launch { scrollState.animateScrollTo(550) }
                },
                label = { Text("Peek mode", fontSize = 12.sp, fontWeight = if (selectedQuickJump == PositionContentBlock.PEEK_MODE) FontWeight.Bold else FontWeight.Normal) }
            )
            FilterChip(
                selected = selectedQuickJump == PositionContentBlock.EXPANDED_VIEW,
                onClick = {
                    selectedQuickJump = PositionContentBlock.EXPANDED_VIEW
                    coroutineScope.launch { scrollState.animateScrollTo(1200) }
                },
                label = { Text("Expanded view", fontSize = 12.sp, fontWeight = if (selectedQuickJump == PositionContentBlock.EXPANDED_VIEW) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            // ==========================================
            // BLOCK 1: POSITION
            // ==========================================
            SettingBlock(
                title = "Position",
                description = "Adjust horizontal and vertical alignment of the pill",
                icon = Icons.Rounded.LocationSearching,
                iconShape = Clover3Shape,
                cardShape = RoundedCornerShape(20.dp)
            ) {
                // Mini phone live preview strip
                MiniPhonePositionPreview(
                    offsetX = islandSettings.offsetX,
                    offsetY = islandSettings.offsetY,
                    isAroundCamera = islandSettings.position.uppercase() == "CAMERA",
                    onDragDelta = { deltaX, deltaY ->
                        val newX = (islandSettings.offsetX + deltaX.roundToInt()).coerceIn(-100, 100)
                        val newY = (islandSettings.offsetY + deltaY.roundToInt()).coerceIn(0, 120)
                        coroutineScope.launch {
                            bromeSettings.setIslandPosition("CUSTOM")
                            bromeSettings.setIslandOffsetX(newX)
                            bromeSettings.setIslandOffsetY(newY)
                            triggerSampleIsland(context, "BANK", isExpanded = false)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Horizontal slider (-100 to +100 dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Horizontal offset", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val readoutX = when {
                        islandSettings.offsetX < 0 -> "${-islandSettings.offsetX} dp left"
                        islandSettings.offsetX == 0 -> "0 dp · centered"
                        else -> "${islandSettings.offsetX} dp right"
                    }
                    Text(readoutX, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = islandSettings.offsetX.toFloat(),
                    onValueChange = { value ->
                        val newX = value.roundToInt()
                        coroutineScope.launch {
                            bromeSettings.setIslandPosition("CUSTOM")
                            bromeSettings.setIslandOffsetX(newX)
                            triggerSampleIsland(context, "BANK", isExpanded = false)
                        }
                    },
                    valueRange = -100f..100f,
                    steps = 199,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Vertical slider (0 to 120 dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vertical offset", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${islandSettings.offsetY} dp down", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = islandSettings.offsetY.toFloat(),
                    onValueChange = { value ->
                        val newY = value.roundToInt()
                        coroutineScope.launch {
                            bromeSettings.setIslandPosition("CUSTOM")
                            bromeSettings.setIslandOffsetY(newY)
                            triggerSampleIsland(context, "BANK", isExpanded = false)
                        }
                    },
                    valueRange = 0f..120f,
                    steps = 119,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Preset Chips
                val isCustom = islandSettings.offsetX != 0 || islandSettings.offsetY != 0 || islandSettings.position.uppercase() == "CUSTOM"
                val isCamera = islandSettings.position.uppercase() == "CAMERA" && !isCustom
                val isTopCenter = islandSettings.position.uppercase() == "TOP_CENTER" && !isCustom

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isCamera,
                        onClick = {
                            coroutineScope.launch {
                                bromeSettings.setIslandPosition("CAMERA")
                                bromeSettings.resetIslandPosition()
                                triggerSampleIsland(context, "BANK", isExpanded = false)
                            }
                        },
                        label = { Text("Around camera", fontSize = 11.sp, fontWeight = if (isCamera) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isTopCenter,
                        onClick = {
                            coroutineScope.launch {
                                bromeSettings.setIslandPosition("TOP_CENTER")
                                bromeSettings.resetIslandPosition()
                                triggerSampleIsland(context, "BANK", isExpanded = false)
                            }
                        },
                        label = { Text("Top center", fontSize = 11.sp, fontWeight = if (isTopCenter) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isCustom,
                        onClick = {},
                        label = { Text("Custom", fontSize = 11.sp, fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reset position button
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            bromeSettings.resetIslandPosition()
                            triggerSampleIsland(context, "BANK", isExpanded = false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(19.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset position", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "The island always stays inside the screen and clear of the status bar.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // BLOCK 2: PEEK MODE
            // ==========================================
            SettingBlock(
                title = "Peek mode",
                description = "Configure what the small compact pill displays",
                icon = Icons.Rounded.Visibility,
                iconShape = Flower5Shape,
                cardShape = RoundedCornerShape(20.dp)
            ) {
                Text("Left side", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val leftOptions = listOf(
                        IslandPeekLeft.AVATAR to "Avatar",
                        IslandPeekLeft.ICON to "Category icon",
                        IslandPeekLeft.NOTHING to "Nothing"
                    )
                    leftOptions.forEach { (option, label) ->
                        val isSelected = islandSettings.peekLeft == option
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    bromeSettings.setIslandPeekLeft(option)
                                    triggerSampleIsland(context, "BANK", isExpanded = false)
                                }
                            },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Text", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                val textOptions = listOf(
                    IslandPeekText.SMART to "Smart",
                    IslandPeekText.SENDER to "Sender",
                    IslandPeekText.PREVIEW to "Preview",
                    IslandPeekText.CATEGORY to "Category",
                    IslandPeekText.NOTHING to "Nothing"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    textOptions.take(3).forEach { (option, label) ->
                        val isSelected = islandSettings.peekText == option
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    bromeSettings.setIslandPeekText(option)
                                    triggerSampleIsland(context, "BANK", isExpanded = false)
                                }
                            },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    textOptions.drop(3).forEach { (option, label) ->
                        val isSelected = islandSettings.peekText == option
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    bromeSettings.setIslandPeekText(option)
                                    triggerSampleIsland(context, "BANK", isExpanded = false)
                                }
                            },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val textDesc = when (islandSettings.peekText) {
                    IslandPeekText.SMART -> "OTP code for OTP, amount for transactions, sender name for others"
                    IslandPeekText.SENDER -> "Sender name or address as received (e.g. JM-KOTAKB-S)"
                    IslandPeekText.PREVIEW -> "Start of message text (code is your OTP, amount & merchant, etc.)"
                    IslandPeekText.CATEGORY -> "Category name (OTP, Transactions, Promotions, Spam, Messages)"
                    IslandPeekText.NOTHING -> "No text — pill becomes a compact icon capsule"
                }
                Text(
                    text = textDesc,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Max text length", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(12, 20, 30).forEach { count ->
                        val isSelected = islandSettings.peekMaxChars == count
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    bromeSettings.setIslandPeekMaxChars(count)
                                    triggerSampleIsland(context, "BANK", isExpanded = false)
                                }
                            },
                            label = { Text("$count chars", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Longer text ends with an ellipsis (…) and the pill never grows past 330dp.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Live Preview Example Block
                Text("Live preview example", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                val sampleBankMsg = remember {
                    IslandMessage(
                        id = 888801L,
                        address = "JM-KOTAKB-S",
                        senderName = "JM-KOTAKB-S",
                        body = "Sent Rs.2,450.00 from Kotak Bank AC X9102 to Dinner on 16-Oct-26. UPI Ref 429104812.",
                        category = SmsCategory.FINANCE,
                        transactionAmount = "−₹2,450",
                        merchant = "Dinner",
                        accountInfo = "Kotak Bank X9102"
                    )
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = IslandVisualDefaults.PillBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    CompactIslandContent(
                        message = sampleBankMsg,
                        settings = islandSettings,
                        isAroundCamera = false,
                        cutoutWidthDp = 0.dp,
                        queuedMoreCount = 0
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Example: JM-KOTAKB-S bank alert rendered with current peek settings.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "If Hide message content is on, it overrides these settings to show only sender or category.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // BLOCK 3: EXPANDED VIEW
            // ==========================================
            SettingBlock(
                title = "Expanded view",
                description = "Choose which elements are displayed on the full island card",
                icon = Icons.Rounded.AspectRatio,
                iconShape = Sunny12Shape,
                cardShape = RoundedCornerShape(20.dp)
            ) {
                IslandSwitchRow(
                    title = "Avatar",
                    subtitle = "40dp shaped avatar in header",
                    checked = islandSettings.expAvatar,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            bromeSettings.setIslandExpAvatar(checked)
                            triggerSampleIsland(context, "BANK", isExpanded = true)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                IslandSwitchRow(
                    title = "Category tag",
                    subtitle = "Tag chip showing message category (OTP, Transactions, etc.)",
                    checked = islandSettings.expTag,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            bromeSettings.setIslandExpTag(checked)
                            triggerSampleIsland(context, "BANK", isExpanded = true)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                IslandSwitchRow(
                    title = "Time",
                    subtitle = "Show 'now' or relative timestamp in subtitle",
                    checked = islandSettings.expTime,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            bromeSettings.setIslandExpTime(checked)
                            triggerSampleIsland(context, "BANK", isExpanded = true)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                IslandSwitchRow(
                    title = "Message text",
                    subtitle = "Preview text for messages, promotions, and spam",
                    checked = islandSettings.expText,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            bromeSettings.setIslandExpText(checked)
                            triggerSampleIsland(context, "LONG_TEXT", isExpanded = true)
                        }
                    }
                )

                if (islandSettings.expText) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Message lines", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    val linesOptions = listOf(
                        1 to "1",
                        2 to "2 (Default)",
                        4 to "4",
                        -1 to "Full"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        linesOptions.forEach { (lines, label) ->
                            val isSelected = islandSettings.expLines == lines
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    coroutineScope.launch {
                                        bromeSettings.setIslandExpLines(lines)
                                        triggerSampleIsland(context, "LONG_TEXT", isExpanded = true)
                                    }
                                },
                                label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                IslandSwitchRow(
                    title = "OTP code and expiry",
                    subtitle = "Large 40sp code, countdown bar and timer",
                    checked = islandSettings.expOtp,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            bromeSettings.setIslandExpOtp(checked)
                            triggerSampleIsland(context, "OTP", isExpanded = true)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                IslandSwitchRow(
                    title = "Amount and details",
                    subtitle = "Large amount and merchant/account line for transactions",
                    checked = islandSettings.expTxn,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            bromeSettings.setIslandExpTxn(checked)
                            triggerSampleIsland(context, "BANK", isExpanded = true)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The header and action row are always shown. Hide message content (Privacy) still wins over these switches.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Interactive mini phone preview for positioning
 */
@Composable
fun MiniPhonePositionPreview(
    offsetX: Int,
    offsetY: Int,
    isAroundCamera: Boolean,
    onDragDelta: (Float, Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF161616),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount.x * 0.7f, dragAmount.y * 0.7f)
                }
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Dashed Crosshair Lines
            val crosshairColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = 20.dp.toPx()
                // Vertical centerline
                drawLine(
                    color = crosshairColor,
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                // Horizontal baseline
                drawLine(
                    color = crosshairColor,
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // Camera Cutout Dot
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
            )

            // Scaled mini pill
            val previewX = (offsetX * 0.45f).dp
            val previewBaseY = if (isAroundCamera) 6.dp else 18.dp
            val previewY = previewBaseY + (offsetY * 0.45f).dp

            Box(
                modifier = Modifier
                    .offset(x = previewX, y = previewY)
                    .width(64.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White.copy(alpha = 0.8f))
                    )
                }
            }

            // Hint text at bottom
            Text(
                text = "Drag the pill or use the sliders",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            )
        }
    }
}

/**
 * Clickable Navigation row for subpages
 */
@Composable
fun IslandNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun MasterIslandPreviewCard(
    islandEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Mini Preview of the Black Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = IslandVisualDefaults.PillBackground,
                modifier = Modifier
                    .width(180.dp)
                    .height(34.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(Clover3Shape)
                            .background(Color(0xFF3A3369)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = Color(0xFFB8A9FF),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "849 201",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (islandEnabled) Color(0xFF4CAF50) else Color(0xFF757575))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Switch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show island notifications",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "A pill at the top of the screen when a message arrives",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = islandEnabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

@Composable
fun CategoryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        modifier = modifier
    )
}

@Composable
fun IslandSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
