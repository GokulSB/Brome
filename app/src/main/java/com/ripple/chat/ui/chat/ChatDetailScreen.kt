package com.ripple.chat.ui.chat

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ripple.chat.data.MessageDeliveryStatus
import com.ripple.chat.sms.AddressNormalizer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ripple.chat.data.ThemeMode
import com.ripple.chat.data.ThemeSettings
import com.ripple.chat.theme.*
import com.ripple.chat.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatId: String,
    address: String?,
    category: String? = null,
    contactName: String? = null,
    photoUri: String? = null,
    onBack: () -> Unit,
    viewModel: ChatDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val themeSettings = remember { ThemeSettings(context) }
    val themeConfig by themeSettings.themeConfigFlow.collectAsState(initial = null)
    val currentThemeMode = themeConfig?.themeMode ?: ThemeMode.SYSTEM

    var hasSendPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var inputText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showEmojiPicker by remember { mutableStateOf(false) }
    val textFieldInteractionSource = remember { MutableInteractionSource() }
    var pendingUrlForSafetyDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(textFieldInteractionSource) {
        textFieldInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release || interaction is FocusInteraction.Focus) {
                showEmojiPicker = false
            }
        }
    }

    BackHandler(enabled = showEmojiPicker) {
        showEmojiPicker = false
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasSendPermission = results[Manifest.permission.SEND_SMS] == true
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    val requestRoleOrSend: (() -> Unit) -> Unit = { onReadyToSend ->
        val hasSmsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val hasPhonePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }

        if (!isDefault) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = context.getSystemService(RoleManager::class.java)
                val intent = rm?.createRequestRoleIntent(RoleManager.ROLE_SMS)
                if (intent != null) roleLauncher.launch(intent)
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                roleLauncher.launch(intent)
            }
        } else if (!hasSmsPerm || !hasPhonePerm) {
            val needed = mutableListOf<String>()
            if (!hasSmsPerm) needed.add(Manifest.permission.SEND_SMS)
            if (!hasPhonePerm) needed.add(Manifest.permission.READ_PHONE_STATE)
            permissionsLauncher.launch(needed.toTypedArray())
        } else {
            onReadyToSend()
        }
    }

    LaunchedEffect(Unit) {
        val needsSend = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED
        val needsPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
        if (needsSend || needsPhone) {
            val needed = mutableListOf<String>()
            if (needsSend) needed.add(Manifest.permission.SEND_SMS)
            if (needsPhone) needed.add(Manifest.permission.READ_PHONE_STATE)
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    val messages by viewModel.messages.collectAsState()
    val contactInfo by viewModel.contactInfo.collectAsState()
    val participants by viewModel.participants.collectAsState()
    val selectedSim by viewModel.selectedSim.collectAsState()

    val initialScrollTarget by viewModel.initialScrollTarget.collectAsState()
    val firstUnreadIndex by viewModel.firstUnreadIndex.collectAsState()

    val listState = rememberLazyListState()
    var hasInitiallyScrolled by remember(chatId) { mutableStateOf(false) }
    var prevMessageCount by remember(chatId) { mutableIntStateOf(0) }

    DisposableEffect(chatId) {
        onDispose {
            viewModel.resetScrollState()
        }
    }

    LaunchedEffect(chatId) {
        viewModel.loadMessages(chatId, address, contactName, photoUri)
    }

    LaunchedEffect(initialScrollTarget, messages.size) {
        val target = initialScrollTarget ?: if (messages.isNotEmpty()) messages.size - 1 else null
        if (!hasInitiallyScrolled && target != null && messages.isNotEmpty()) {
            val safeIndex = target.coerceIn(0, messages.size - 1)
            listState.scrollToItem(safeIndex)
            hasInitiallyScrolled = true
            prevMessageCount = messages.size
        } else if (hasInitiallyScrolled && messages.size > prevMessageCount) {
            listState.animateScrollToItem(messages.size - 1)
            prevMessageCount = messages.size
        } else if (hasInitiallyScrolled) {
            prevMessageCount = messages.size
        }
    }

    val displayName = contactInfo?.name ?: contactName ?: address ?: "Conversation"
    val subtitleText = if (address?.any { it.isLetter() } == true || category?.equals("FINANCE", ignoreCase = true) == true) "Business sender" else "Mobile"

    // Check if sending messages should be disabled (Bank, Courier/Delivery, Shopping, Service Shortcodes)
    val isGenuineContact = contactInfo?.name != null &&
                           contactInfo?.name != contactInfo?.number &&
                           contactInfo?.name?.isNotBlank() == true

    val addrLower = (address ?: contactInfo?.number ?: "").lowercase()
    val catUpper = (category ?: "").uppercase()

    val isBankOrFinancial = catUpper == "FINANCE" || catUpper == "OTP" ||
        addrLower.containsAny("bank", "bk", "hdfc", "icici", "sbi", "axis", "kotak", "indus", "paytm", "citi", "scb")

    val isCourierOrDelivery = catUpper == "TICKETS" ||
        addrLower.containsAny("courier", "delhivery", "dtdc", "bluedart", "ecom", "fedex", "post", "delivery", "shipping", "track")

    val isShoppingOrPromotional = catUpper == "SHOPPING" || catUpper == "PROMOTIONAL" || catUpper == "PROMOTIONS" || catUpper == "OFFERS" ||
        addrLower.containsAny("amazon", "flipkart", "myntra", "ajio", "meesho", "zomato", "swiggy", "mcdonl", "domino", "kfc")

    val isAlphanumericSender = (address ?: "").any { it.isLetter() }
    val isSendable = remember(address, contactInfo?.number) {
        val target = address ?: contactInfo?.number
        AddressNormalizer.isSendable(target)
    }

    val canReply = isSendable && (isGenuineContact || (!isAlphanumericSender && !isBankOrFinancial && !isCourierOrDelivery && !isShoppingOrPromotional))

    val appearance = LocalBromeAppearance.current

    BromeBackground(
        design = appearance.backgroundDesign,
        seed = appearance.backgroundSeed,
        washLevel = appearance.gradientWashLevel
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── HEADER ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button (follows corner settings)
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(appearance.cornerRadius.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Pill style container for contact name and phone number (follows corner settings)
                    Surface(
                        shape = RoundedCornerShape(appearance.cornerRadius.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VariantBShapedAvatar(
                                name = displayName,
                                photoUri = contactInfo?.photoUri ?: photoUri,
                                size = 40.dp
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val displayPhone = contactInfo?.number?.takeIf { it.isNotBlank() } ?: address ?: subtitleText
                                Text(
                                    text = displayPhone,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // More Options Button (follows corner settings)
                    Surface(
                        onClick = { /* More options */ },
                        shape = RoundedCornerShape(appearance.cornerRadius.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ── MESSAGES BODY (Dynamic Theme Card Container) ──
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(appearance.cornerRadius.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (hasInitiallyScrolled || messages.isEmpty()) 1f else 0f
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showEmojiPicker = false
                                keyboardController?.hide()
                            },
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        itemsIndexed(messages, key = { _, it -> it.id }) { index, message ->
                            val showDateHeader = index == 0 || !isSameCalendarDay(messages[index - 1].date, message.date)
                            if (showDateHeader) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Small Pill Date Label
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = formatMessageDateHeader(message.date),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            if (firstUnreadIndex != null && index == firstUnreadIndex) {
                                UnreadMessagesDivider()
                            }

                            val isPrevSame = index > 0 && messages[index - 1].isSentByMe == message.isSentByMe
                            val isNextSame = index < messages.size - 1 && messages[index + 1].isSentByMe == message.isSentByMe

                            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                            val timeText = sdf.format(Date(message.date))

                            VariantBChatBubble(
                                messageText = message.body,
                                timeText = timeText,
                                isSentByMe = message.isSentByMe,
                                deliveryStatus = if (message.isSentByMe) message.deliveryStatus else null,
                                failureReason = message.failureReason,
                                onRetry = if (message.isSentByMe && message.deliveryStatus == MessageDeliveryStatus.FAILED) {
                                    { viewModel.retryMessage(message) }
                                } else null,
                                isPrevSameSender = isPrevSame,
                                isNextSameSender = isNextSame,
                                onUrlClick = { clickedUrl ->
                                    pendingUrlForSafetyDialog = clickedUrl
                                }
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                // ── SMART REPLIES (Colored chips: Green, Yellow, Rose, 40dp tall) ──
                if (canReply && messages.isNotEmpty()) {
                    val smartReplies = listOf(
                        "Thanks!" to VariantBGreen,
                        "Sounds good👍" to VariantBYellow,
                        "I'll call you later" to VariantBRose
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(smartReplies) { (suggestion, accent) ->
                            Surface(
                                onClick = {
                                    requestRoleOrSend {
                                        viewModel.sendMessage(suggestion, chatId, address)
                                    }
                                },
                                shape = AsymmetricPillShape,
                                color = accent.container,
                                modifier = Modifier.height(40.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = suggestion,
                                        color = accent.onContainer,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // ── INPUT BAR OR DISABLED REPLY BANNER ──
                if (!canReply) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .navigationBarsPadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "You can't reply to this sender",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FigtreeFontFamily
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (!showEmojiPicker) {
                                            keyboardController?.hide()
                                            showEmojiPicker = true
                                        } else {
                                            showEmojiPicker = false
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showEmojiPicker) Icons.Rounded.Keyboard else Icons.Rounded.SentimentSatisfiedAlt,
                                        contentDescription = if (showEmojiPicker) "Keyboard" else "Emoji",
                                        tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    interactionSource = textFieldInteractionSource,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (inputText.isEmpty()) {
                                                Text(
                                                    "Message",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )

                                val availableSims = viewModel.getAvailableSims()
                                val currentSimLabel = availableSims.find { it.first == selectedSim }?.second ?: "SIM $selectedSim"

                                Surface(
                                    onClick = {
                                        val canPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                                        if (!canPhone) {
                                            permissionsLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
                                        }
                                        val sims = viewModel.getAvailableSims()
                                        val nextSim = if (sims.size > 1) {
                                            val idx = sims.indexOfFirst { it.first == selectedSim }
                                            val nextIdx = (if (idx >= 0) idx + 1 else 0) % sims.size
                                            sims[nextIdx].first
                                        } else {
                                            sims.firstOrNull()?.first ?: 1
                                        }
                                        viewModel.setSelectedSim(nextSim)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = currentSimLabel,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                                        .clickable {
                                            if (inputText.isNotBlank()) {
                                                val textToSend = inputText
                                                requestRoleOrSend {
                                                    viewModel.sendMessage(textToSend, chatId, address)
                                                    inputText = ""
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Send",
                                        tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showEmojiPicker,
                            enter = androidx.compose.animation.expandVertically() + fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + fadeOut()
                        ) {
                            BromeEmojiPicker(
                                onEmojiSelect = { emoji ->
                                    inputText += emoji
                                },
                                onBackspace = {
                                    if (inputText.isNotEmpty()) {
                                        if (inputText.length >= 2 && Character.isSurrogatePair(inputText[inputText.length - 2], inputText[inputText.length - 1])) {
                                            inputText = inputText.substring(0, inputText.length - 2)
                                        } else {
                                            inputText = inputText.substring(0, inputText.length - 1)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    pendingUrlForSafetyDialog?.let { targetUrl ->
        LinkSafetyDialog(
            url = targetUrl,
            onDismiss = { pendingUrlForSafetyDialog = null }
        )
    }
}

private fun String.containsAny(vararg keywords: String): Boolean {
    return keywords.any { this.contains(it) }
}

fun formatMessageDateHeader(timestamp: Long): String {
    val messageCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val nowCal = Calendar.getInstance()

    val isSameYear = messageCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
    val isSameDay = isSameYear && messageCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
    if (isSameDay) return "Today"

    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = messageCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                      messageCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "Yesterday"

    return if (isSameYear) {
        SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

fun isSameCalendarDay(t1: Long, t2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
           c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun UnreadMessagesDivider(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            thickness = 1.dp
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .height(28.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MarkEmailUnread,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Unread messages",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            thickness = 1.dp
        )
    }
}

