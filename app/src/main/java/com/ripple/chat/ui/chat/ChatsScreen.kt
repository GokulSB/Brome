package com.ripple.chat.ui.chat

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.ripple.chat.R
import com.ripple.chat.ui.AppStrings
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.ReminderAlarmManager
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.data.CategoryKeywordManager
import com.ripple.chat.utils.CategoryClassifier
import com.ripple.chat.theme.*
import com.ripple.chat.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

enum class DashboardTab {
    ALL, MESSAGES, IMPORTANT, PROMOTIONS, FINANCE, OTP, TICKETS, REMINDERS, SUBSCRIPTIONS, SHOPPING, SPAM, OTHERS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    inboxThreads: List<ChatThread>,
    onNavigateToChat: (String, String?, String?, String?, String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: (String?) -> Unit,
    onNavigateToThemes: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToFinanceLedger: () -> Unit,
    onNavigateToNewMessage: () -> Unit = {},
    chatsViewModel: ChatsViewModel = viewModel()
) {
    val context = LocalContext.current
    val isLoading by chatsViewModel.isLoading.collectAsState()
    val otpBannerItems by chatsViewModel.otpBannerItems.collectAsState()
    val dismissedOtpIds by chatsViewModel.dismissedOtpIds.collectAsState()

    val starredThreadIds by chatsViewModel.starredThreadIds.collectAsState()
    val isSelectionMode by chatsViewModel.isSelectionMode.collectAsState()
    val selectedThreadIds by chatsViewModel.selectedThreadIds.collectAsState()

    val listState = rememberLazyListState()

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isDefaultSmsApp by remember {
        mutableStateOf(checkIsDefaultSms(context))
    }

    val defaultPrefs = remember { context.getSharedPreferences("brome_default_banner_prefs", Context.MODE_PRIVATE) }
    var isDefaultBannerDismissed by rememberSaveable {
        mutableStateOf(defaultPrefs.getBoolean("banner_dismissed", false))
    }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val isDefault = checkIsDefaultSms(context)
        isDefaultSmsApp = isDefault
        if (isDefault) {
            defaultPrefs.edit().putBoolean("banner_dismissed", true).apply()
            chatsViewModel.loadThreads()
        }
    }

    val requestDefaultSms: () -> Unit = {
        if (checkIsDefaultSms(context)) {
            isDefaultSmsApp = true
            defaultPrefs.edit().putBoolean("banner_dismissed", true).apply()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    defaultSmsLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                } else {
                    isDefaultSmsApp = true
                }
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                }
                defaultSmsLauncher.launch(intent)
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
            defaultSmsLauncher.launch(intent)
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.READ_SMS] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        hasSmsPermission = isGranted
        if (isGranted) chatsViewModel.loadThreads()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                hasSmsPermission = isGranted
                val isDefault = checkIsDefaultSms(context)
                isDefaultSmsApp = isDefault
                if (isGranted) chatsViewModel.loadThreads()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_SMS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_CONTACTS)
        }
        if (needed.isNotEmpty()) {
            smsPermissionLauncher.launch(needed.toTypedArray())
        } else {
            chatsViewModel.loadThreads()
        }
    }

    val sortedThreads = remember(inboxThreads) {
        inboxThreads.sortedByDescending { it.date }
    }

    val isContactMessage: (ChatThread) -> Boolean = remember {
        { thread ->
            val name = thread.contactName?.trim()
            val addr = thread.address.trim()
            val digits = addr.replace(Regex("[^0-9+]"), "")
            !name.isNullOrBlank() && 
            !name.equals(addr, ignoreCase = true) && 
            !name.equals(digits, ignoreCase = true)
        }
    }

    val isPersonalConversation: (ChatThread) -> Boolean = remember {
        { thread ->
            val clean = com.ripple.chat.sms.AddressNormalizer.cleanAndSanitize(thread.address)
            val isAlpha = com.ripple.chat.sms.AddressNormalizer.isAlphanumericSender(clean)
            val digits = clean.filter { it.isDigit() }
            val isStandardPhone = !isAlpha && digits.length >= 7
            val hasContact = isContactMessage(thread)
            val isNotSpam = thread.category != SmsCategory.SPAM

            hasContact ||
            thread.category == SmsCategory.PERSONAL ||
            (thread.hasOutgoing && !isAlpha) ||
            (thread.lastMessageIsFromMe && !isAlpha) ||
            (isStandardPhone && isNotSpam)
        }
    }

    val chats = sortedThreads.filter { it.category == SmsCategory.PERSONAL }
    val personal = sortedThreads.filter { isPersonalConversation(it) }
    val finance = sortedThreads.filter { it.category == SmsCategory.FINANCE }
    val otp = sortedThreads.filter { it.category == SmsCategory.OTP || com.ripple.chat.utils.OtpParser.parseOtp(it.body, it.date) != null }
    val promotions = sortedThreads.filter { it.category == SmsCategory.PROMOTIONAL }
    val shopping = sortedThreads.filter { it.category == SmsCategory.SHOPPING }
    val tickets = sortedThreads.filter { it.category == SmsCategory.TICKETS }
    val reminders = sortedThreads.filter { it.category == SmsCategory.REMINDERS }
    val subscriptions = sortedThreads.filter { it.category == SmsCategory.SUBSCRIPTIONS }
    val spam = sortedThreads.filter { it.category == SmsCategory.SPAM }

    val themeConfig = com.ripple.chat.theme.LocalThemeConfig.current
    val customCats = themeConfig?.customCategories ?: emptyList()
    val keywordsVersion by CategoryKeywordManager.keywordsVersion.collectAsState()
    val allActiveKeywords = remember(themeConfig, keywordsVersion) {
        CategoryKeywordManager.getAllKeywords(context)
    }

    val matchesCustomCategory: (ChatThread, String) -> Boolean = remember(customCats, allActiveKeywords) {
        { thread, catKey ->
            val cat = customCats.firstOrNull { it.id == catKey }
            if (cat == null) false
            else {
                val keywords = allActiveKeywords[catKey] ?: cat.defaultKeywords
                keywords.any { kw ->
                    CategoryClassifier.matchesCategory(thread.address, thread.body, thread.contactName, kw)
                }
            }
        }
    }

    var selectedCategoryKey by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showOtpScreenView by remember { mutableStateOf(false) }

    var showReminderDialog by remember { mutableStateOf(false) }
    var spamThreadForAction by remember { mutableStateOf<ChatThread?>(null) }

    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var undoAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val showUndo: (String, () -> Unit) -> Unit = { msg, undo ->
        snackbarMessage = msg
        undoAction = undo
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            delay(4000)
            snackbarMessage = null
            undoAction = null
        }
    }

    BackHandler(enabled = isSelectionMode) {
        chatsViewModel.clearSelection()
    }

    val categoryCounts = remember(sortedThreads, customCats, allActiveKeywords) {
        val counts = mutableMapOf<String, Int>()
        fun countUnread(list: List<ChatThread>): Int {
            return list.filter { !it.read }.sumOf { if (it.unreadCount > 0) it.unreadCount else 1 }
        }
        counts["UNREAD"] = countUnread(sortedThreads.filter { it.category != SmsCategory.SPAM })
        counts["PERSONAL"] = personal.size
        counts["OTP"] = otp.size
        counts["FINANCE"] = finance.size
        counts["PROMOTIONS"] = promotions.size
        counts["SHOPPING"] = shopping.size
        counts["TICKETS"] = tickets.size
        counts["REMINDERS"] = reminders.size
        counts["SUBSCRIPTIONS"] = subscriptions.size
        counts["IMPORTANT"] = sortedThreads.count { it.category == SmsCategory.IMPORTANT }
        counts["SPAM"] = spam.size
        customCats.forEach { cat ->
            counts[cat.id] = sortedThreads.count { matchesCustomCategory(it, cat.id) }
        }
        counts
    }

    val listToShow = when (selectedCategoryKey) {
        null -> sortedThreads
        "PERSONAL" -> personal
        "UNREAD" -> sortedThreads.filter { !it.read && it.category != SmsCategory.SPAM }
        "FINANCE" -> finance
        "OTP" -> otp
        "PROMOTIONS" -> promotions
        "SHOPPING" -> shopping
        "TICKETS" -> tickets
        "REMINDERS" -> reminders
        "SUBSCRIPTIONS" -> subscriptions
        "IMPORTANT" -> sortedThreads.filter { it.category == SmsCategory.IMPORTANT }
        "SPAM" -> spam
        else -> {
            if (customCats.any { it.id == selectedCategoryKey }) {
                sortedThreads.filter { matchesCustomCategory(it, selectedCategoryKey!!) }
            } else {
                sortedThreads
            }
        }
    }

    val filteredList = if (searchQuery.isBlank()) listToShow else {
        listToShow.filter {
            it.contactName?.contains(searchQuery, ignoreCase = true) == true ||
            it.address.contains(searchQuery, ignoreCase = true) ||
            it.body.contains(searchQuery, ignoreCase = true)
        }
    }

    if (showOtpScreenView) {
        OtpScreen(
            otpItems = otpBannerItems,
            onBack = { showOtpScreenView = false },
            onNavigateToChat = { threadId, address ->
                onNavigateToChat(threadId.toString(), address, "OTP", null, null)
            }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // ── TOP ROW: NORMAL OR SELECTION MODE ──
                if (isSelectionMode) {
                    TideSelectionTopBar(
                        selectedCount = selectedThreadIds.size,
                        onCloseClick = {
                            chatsViewModel.clearSelection()
                        },
                        onSelectAllClick = {
                            chatsViewModel.selectAll(filteredList.map { it.threadId })
                        }
                    )
                } else {
                    TideTopBar(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onTrashClick = onNavigateToTrash,
                        onSettingsClick = onNavigateToSettings
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── DEFAULT SMS APP PROMPT BANNER ──
                if (!isSelectionMode && !isDefaultSmsApp && !isDefaultBannerDismissed) {
                    val appearance = LocalBromeAppearance.current
                    Surface(
                        shape = RoundedCornerShape(appearance.cornerRadius.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable { requestDefaultSms() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sms,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Set Brome as default SMS app",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontFamily = FigtreeFontFamily
                                )
                                Text(
                                    text = "Required to send & receive messages reliably",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    fontFamily = FigtreeFontFamily
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(appearance.cornerRadius.dp),
                                color = MaterialTheme.colorScheme.primary,
                                onClick = { requestDefaultSms() }
                            ) {
                                Text(
                                    text = "Set Default",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontFamily = FigtreeFontFamily
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    isDefaultBannerDismissed = true
                                    defaultPrefs.edit().putBoolean("banner_dismissed", true).apply()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // ── OTP BANNER CARD ──
                if (!isSelectionMode) {
                    VariantBOtpBannerSection(
                        items = otpBannerItems,
                        dismissedIds = dismissedOtpIds,
                        onDismiss = { id -> chatsViewModel.dismissOtpBanner(id) },
                        onNavigateToChat = { threadId, address ->
                            onNavigateToChat(threadId.toString(), address, "OTP", null, null)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── CATEGORIES 2x2 GRID (DIMMED IF SELECTION MODE) ──
                if (themeConfig?.enableCategorization != false) {
                    TideCategoryGrid(
                        counts = categoryCounts,
                        selectedCategoryKey = selectedCategoryKey,
                        dimmed = isSelectionMode,
                        onCategoryClick = { catKey ->
                            if (!isSelectionMode) {
                                if (catKey == "OTP" && selectedCategoryKey == "OTP") {
                                    showOtpScreenView = true
                                } else {
                                    selectedCategoryKey = catKey
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── RECENT LABEL & FINANCE VIEW BUTTON ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val categoryLabel = when {
                        selectedCategoryKey == null -> stringResource(R.string.recent)
                        customCats.any { it.id == selectedCategoryKey } -> customCats.first { it.id == selectedCategoryKey }.name.uppercase()
                        else -> AppStrings.categoryName(selectedCategoryKey!!).uppercase()
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = categoryLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (filteredList.isNotEmpty()) {
                            val allSelected = isSelectionMode && selectedThreadIds.size >= filteredList.size
                            IconButton(
                                onClick = {
                                    if (allSelected) {
                                        chatsViewModel.clearSelection()
                                    } else {
                                        chatsViewModel.setSelectionMode(true)
                                        chatsViewModel.selectAll(filteredList.map { it.threadId })
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                    contentDescription = if (allSelected) "Deselect All" else "Select All",
                                    tint = if (allSelected || isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isSelectionMode && (selectedCategoryKey == "FINANCE" || selectedCategoryKey == null)) {
                            Surface(
                                onClick = onNavigateToFinanceLedger,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.height(32.dp).pressScale()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Visibility,
                                        contentDescription = "Open Finance Page",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.finance_button),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = FigtreeFontFamily
                                    )
                                }
                            }
                        }

                        if (selectedCategoryKey != null && !isSelectionMode) {
                            Text(
                                text = stringResource(R.string.see_all),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { selectedCategoryKey = null }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── MESSAGE LIST CARDS (84dp MIN HIGH) ──
                val navPillHeightDp = LocalBromeAppearance.current.navPillHeightDp
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { chatsViewModel.loadThreads() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = (navPillHeightDp + 48).dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Spam Strip if spam present - shown ON TOP of all messages
                        if (spam.isNotEmpty() && selectedCategoryKey == null && !isSelectionMode) {
                            item(key = "spam_review_strip") {
                                TideSpamStrip(
                                    blockedCount = spam.size,
                                    onReviewClick = { selectedCategoryKey = "SPAM" },
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        itemsIndexed(filteredList, key = { _, thread -> thread.threadId }) { index, thread ->
                            val isSelected = selectedThreadIds.contains(thread.threadId)
                            val isStarred = starredThreadIds.contains(thread.threadId)

                            val scrollDimModifier = Modifier.graphicsLayer {
                                val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == thread.threadId }
                                if (itemInfo != null) {
                                    val topOffset = itemInfo.offset.toFloat()
                                    val fadeThresholdPx = if (itemInfo.size > 0) itemInfo.size.toFloat() else 240f
                                    if (topOffset < 0f) {
                                        val progress = (1f + topOffset / fadeThresholdPx).coerceIn(0f, 1f)
                                        alpha = 0.35f + 0.65f * progress
                                        scaleX = 0.96f + 0.04f * progress
                                        scaleY = 0.96f + 0.04f * progress
                                    } else {
                                        alpha = 1.0f
                                        scaleX = 1.0f
                                        scaleY = 1.0f
                                    }
                                }
                            }

                            TideMessageRow(
                                thread = thread,
                                index = index,
                                searchQuery = searchQuery,
                                isSelected = isSelected,
                                isStarred = isStarred,
                                isUnreadTab = (selectedCategoryKey == "UNREAD"),
                                modifier = scrollDimModifier,
                                onClick = {
                                    if (isSelectionMode) {
                                        chatsViewModel.toggleSelection(thread.threadId)
                                    } else {
                                        onNavigateToChat(thread.threadId.toString(), thread.address, thread.category.name, thread.contactName, thread.contactPhotoUri)
                                    }
                                },
                                onLongClick = {
                                    if (selectedCategoryKey == "SPAM" && !isSelectionMode) {
                                        spamThreadForAction = thread
                                    } else {
                                        if (!isSelectionMode) {
                                            chatsViewModel.setSelectionMode(true)
                                        }
                                        chatsViewModel.toggleSelection(thread.threadId)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // FLOATING SELECTION ACTION BAR (PART 1)
            AnimatedVisibility(
                visible = isSelectionMode && selectedThreadIds.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val selectedList = filteredList.filter { it.threadId in selectedThreadIds }
                val allStarred = selectedList.all { it.threadId in starredThreadIds }
                val isSpam = selectedCategoryKey == "SPAM" || (selectedList.isNotEmpty() && selectedList.all { it.category == SmsCategory.SPAM })
                val hasUnreadSelected = selectedList.any { !it.read }

                TideSelectionActionBar(
                    selectedCount = selectedThreadIds.size,
                    allStarred = allStarred,
                    isSpamTab = isSpam,
                    hasUnreadSelected = hasUnreadSelected,
                    onMarkReadClick = {
                        val idsToProcess = selectedThreadIds.toList()
                        chatsViewModel.markAllAsRead(idsToProcess)
                        showUndo("Marked as read") {
                            chatsViewModel.markAllAsUnread(idsToProcess)
                        }
                        chatsViewModel.clearSelection()
                    },
                    onMarkUnreadClick = {
                        val idsToProcess = selectedThreadIds.toList()
                        chatsViewModel.markAllAsUnread(idsToProcess)
                        showUndo("Marked as unread") {
                            chatsViewModel.markAllAsRead(idsToProcess)
                        }
                        chatsViewModel.clearSelection()
                    },
                    onNotSpamClick = {
                        val idsToProcess = selectedThreadIds
                        chatsViewModel.markAsNonSpam(idsToProcess)
                        showUndo("Marked as not spam") {
                            chatsViewModel.unmarkAsNonSpam(idsToProcess)
                        }
                        chatsViewModel.clearSelection()
                    },
                    onDeleteClick = {
                        val idsToProcess = selectedThreadIds.toSet()
                        chatsViewModel.deleteThreads(idsToProcess)
                        showUndo("Moved to Trash") {
                            chatsViewModel.restoreThreads(idsToProcess)
                        }
                        chatsViewModel.clearSelection()
                    },
                    onStarClick = {
                        val idsToProcess = selectedThreadIds
                        if (allStarred) {
                            chatsViewModel.unstarThreads(idsToProcess)
                            showUndo("Removed from Starred") {
                                chatsViewModel.starThreads(idsToProcess)
                            }
                        } else {
                            chatsViewModel.starThreads(idsToProcess)
                            showUndo("Starred") {
                                chatsViewModel.unstarThreads(idsToProcess)
                            }
                        }
                        chatsViewModel.clearSelection()
                    },
                    onArchiveClick = {
                        val idsToProcess = selectedThreadIds
                        chatsViewModel.archiveThreads(idsToProcess)
                        showUndo("Moved to Archive") {
                            chatsViewModel.unarchiveThreads(idsToProcess)
                        }
                        chatsViewModel.clearSelection()
                    },
                    onOverflowClick = {
                        showReminderDialog = true
                    }
                )
            }

            // UNDO SNACKBAR
            AnimatedVisibility(
                visible = snackbarMessage != null && !isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .semantics { this.liveRegion = LiveRegionMode.Polite }
            ) {
                snackbarMessage?.let { msg ->
                    TideUndoSnackbar(
                        message = msg,
                        onUndo = {
                            undoAction?.invoke()
                            snackbarMessage = null
                            undoAction = null
                        }
                    )
                }
            }
        }
    }

    // REMINDER DIALOG (Requirement 2b)
    if (showReminderDialog && selectedThreadIds.isNotEmpty()) {
        val idsToRemind = selectedThreadIds.toList()
        ReminderOptionDialog(
            onDismiss = { showReminderDialog = false },
            onSelectMinutes = { mins ->
                val alarmManager = ReminderAlarmManager(context)
                alarmManager.setRepeatingSnooze(idsToRemind, mins)
                BromeToast.show(context, "Reminder set for $mins mins")
                chatsViewModel.clearSelection()
                showReminderDialog = false
            }
        )
    }

    // SPAM LONG PRESS ACTION DIALOG
    if (spamThreadForAction != null) {
        val targetSpam = spamThreadForAction!!
        AlertDialog(
            onDismissRequest = { spamThreadForAction = null },
            icon = {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = targetSpam.contactName ?: targetSpam.address,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = targetSpam.body.take(120),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        onClick = {
                            val targetId = targetSpam.threadId
                            chatsViewModel.markAsNonSpam(setOf(targetId))
                            showUndo("Marked as not spam") {
                                chatsViewModel.unmarkAsNonSpam(setOf(targetId))
                            }
                            spamThreadForAction = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mark as Not Spam",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            chatsViewModel.deleteThread(targetSpam.threadId)
                            spamThreadForAction = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            val targetId = targetSpam.threadId
                            spamThreadForAction = null
                            chatsViewModel.setSelectionMode(true)
                            chatsViewModel.toggleSelection(targetId)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Checklist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Select Multiple",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { spamThreadForAction = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun ReminderOptionDialog(
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Set Reminder", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Select when you want to be notified about these messages:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val options = listOf(
                    15 to "In 15 minutes",
                    60 to "In 1 hour",
                    180 to "In 3 hours",
                    720 to "Tomorrow 9:00 AM"
                )

                options.forEach { (mins, label) ->
                    Surface(
                        onClick = {
                            onSelectMinutes(mins)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
