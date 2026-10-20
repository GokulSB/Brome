package com.ripple.chat.ui.chat

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.ripple.chat.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.ReminderAlarmManager
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.theme.*
import com.ripple.chat.ui.components.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredScreen(
    starredThreads: List<ChatThread>,
    onUnstarThread: (Long) -> Unit,
    onNavigateToChat: (String, String?, String?, String?, String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    chatsViewModel: ChatsViewModel = viewModel()
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    val isSelectionMode by chatsViewModel.isSelectionMode.collectAsState()
    val selectedThreadIds by chatsViewModel.selectedThreadIds.collectAsState()

    var showReminderDialog by remember { mutableStateOf(false) }

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

    val filteredList = remember(starredThreads, selectedFilter, searchQuery) {
        val catList = when (selectedFilter) {
            "Chats" -> starredThreads.filter { it.category == SmsCategory.PERSONAL }
            "Money" -> starredThreads.filter { it.category == SmsCategory.FINANCE }
            "Tickets" -> starredThreads.filter { it.category == SmsCategory.TICKETS }
            else -> starredThreads
        }
        if (searchQuery.isBlank()) catList else {
            catList.filter {
                it.contactName?.contains(searchQuery, ignoreCase = true) == true ||
                it.address.contains(searchQuery, ignoreCase = true) ||
                it.body.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Header Row
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.starred_title),
                            fontFamily = BricolageFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 40.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.starred_subtitle, starredThreads.size),
                            fontFamily = FigtreeFontFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        onClick = { isSearchVisible = !isSearchVisible },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.size(52.dp).pressScale()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isSearchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips Row
            if (!isSelectionMode) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        "All" to starredThreads.size,
                        "Chats" to starredThreads.count { it.category == SmsCategory.PERSONAL },
                        "Money" to starredThreads.count { it.category == SmsCategory.FINANCE },
                        "Tickets" to starredThreads.count { it.category == SmsCategory.TICKETS }
                    )

                    items(filters) { (filter, count) ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            onClick = { selectedFilter = filter },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.height(38.dp).pressScale()
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$filter · $count",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FigtreeFontFamily,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (filteredList.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(Cookie8Shape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.starred_empty),
                            fontFamily = BricolageFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the star icon on any message to keep it close.",
                            fontFamily = FigtreeFontFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val navPillHeightDp = LocalBromeAppearance.current.navPillHeightDp
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (navPillHeightDp + 48).dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(filteredList, key = { _, thread -> thread.threadId }) { index, thread ->
                        val isSelected = selectedThreadIds.contains(thread.threadId)

                        TideMessageRow(
                            thread = thread,
                            index = index,
                            searchQuery = searchQuery,
                            isSelected = isSelected,
                            isStarred = true,
                            onClick = {
                                if (isSelectionMode) {
                                    chatsViewModel.toggleSelection(thread.threadId)
                                } else {
                                    onNavigateToChat(thread.threadId.toString(), thread.address, thread.category.name, thread.contactName, thread.contactPhotoUri)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    chatsViewModel.setSelectionMode(true)
                                    chatsViewModel.toggleSelection(thread.threadId)
                                } else {
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
            TideSelectionActionBar(
                selectedCount = selectedThreadIds.size,
                allStarred = true,
                isStarredScreen = true,
                onStarClick = {
                    val idsToUnstar = selectedThreadIds
                    chatsViewModel.unstarThreads(idsToUnstar)
                    showUndo("Removed from Starred") {
                        chatsViewModel.starThreads(idsToUnstar)
                    }
                    chatsViewModel.clearSelection()
                },
                onArchiveClick = {
                    val idsToArchive = selectedThreadIds
                    chatsViewModel.archiveThreads(idsToArchive)
                    showUndo("Moved to Archive") {
                        chatsViewModel.unarchiveThreads(idsToArchive)
                    }
                    chatsViewModel.clearSelection()
                },
                onDeleteClick = {
                    val idsToDelete = selectedThreadIds.toSet()
                    chatsViewModel.deleteThreads(idsToDelete)
                    showUndo("Moved to Trash") {
                        chatsViewModel.restoreThreads(idsToDelete)
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

    if (showReminderDialog && selectedThreadIds.isNotEmpty()) {
        val idsToRemind = selectedThreadIds.toList()
        ReminderOptionDialog(
            onDismiss = { showReminderDialog = false },
            onSelectMinutes = { mins ->
                val alarmManager = ReminderAlarmManager(context)
                alarmManager.setRepeatingSnooze(idsToRemind, mins)
                com.ripple.chat.ui.components.BromeToast.show(context, "Reminder set for $mins mins")
                chatsViewModel.clearSelection()
                showReminderDialog = false
            }
        )
    }
}
