package com.ripple.chat.ui.chat

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ripple.chat.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.ReminderAlarmManager
import com.ripple.chat.theme.*
import com.ripple.chat.ui.components.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    archivedThreads: List<ChatThread>,
    onUnarchiveThread: (Long) -> Unit,
    onNavigateToChat: (String, String?, String?, String?, String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    chatsViewModel: ChatsViewModel = viewModel()
) {
    val context = LocalContext.current
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
                        chatsViewModel.selectAll(archivedThreads.map { it.threadId })
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
                            text = stringResource(R.string.archive_title),
                            fontFamily = BricolageFontFamily,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                            fontSize = 40.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.archive_subtitle),
                            fontFamily = FigtreeFontFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (archivedThreads.isEmpty()) {
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
                                Icons.Rounded.Archive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.archive_empty),
                            fontFamily = BricolageFontFamily,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
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
                    itemsIndexed(archivedThreads, key = { _, thread -> thread.threadId }) { index, thread ->
                        val isSelected = selectedThreadIds.contains(thread.threadId)

                        TideMessageRow(
                            thread = thread,
                            index = index,
                            searchQuery = "",
                            isSelected = isSelected,
                            isStarred = false,
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

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Swipe or select a conversation to move it back to your inbox",
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

        // FLOATING SELECTION ACTION BAR (PART 1)
        AnimatedVisibility(
            visible = isSelectionMode && selectedThreadIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TideSelectionActionBar(
                selectedCount = selectedThreadIds.size,
                allStarred = false,
                isArchiveScreen = true,
                onStarClick = {
                    chatsViewModel.clearSelection()
                },
                onArchiveClick = {
                    val idsToUnarchive = selectedThreadIds
                    chatsViewModel.unarchiveThreads(idsToUnarchive)
                    showUndo("Moved to Inbox") {
                        chatsViewModel.archiveThreads(idsToUnarchive)
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
