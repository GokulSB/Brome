package com.ripple.chat.ui.chat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.data.SmsRepository
import com.ripple.chat.data.TrashManager
import com.ripple.chat.data.OtpBannerManager
import com.ripple.chat.data.BromeSettings
import com.ripple.chat.data.BromeAppearance
import com.ripple.chat.data.OtpAutoDeleteTime
import com.ripple.chat.data.SpamAutoDeleteTime
import com.ripple.chat.data.TrashAutoDeleteTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class ChatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SmsRepository(application)
    private val trashManager = TrashManager(application)
    private val bromeSettings = BromeSettings(application)
    private var currentAppearance: BromeAppearance? = null
    private var isMarkingAsRead = false
    private var loadJob: Job? = null

    private val triagePrefs = application.getSharedPreferences("triage_prefs", Context.MODE_PRIVATE)
    private val starredPrefs = application.getSharedPreferences("starred_prefs", Context.MODE_PRIVATE)
    private val archivedPrefs = application.getSharedPreferences("archived_prefs", Context.MODE_PRIVATE)

    private val _starredThreadIds = MutableStateFlow<Set<Long>>(
        starredPrefs.getStringSet("starred_ids", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    )
    val starredThreadIds: StateFlow<Set<Long>> = _starredThreadIds.asStateFlow()

    private val _archivedThreadIds = MutableStateFlow<Set<Long>>(
        archivedPrefs.getStringSet("archived_ids", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    )
    val archivedThreadIds: StateFlow<Set<Long>> = _archivedThreadIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedThreadIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedThreadIds: StateFlow<Set<Long>> = _selectedThreadIds.asStateFlow()

    val otpBannerManager = OtpBannerManager()
    val otpBannerItems = otpBannerManager.items
    val dismissedOtpIds = otpBannerManager.dismissedIds

    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads.asStateFlow()

    private val _deletedThreads = MutableStateFlow<List<ChatThread>>(emptyList())
    val deletedThreads: StateFlow<List<ChatThread>> = _deletedThreads.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val newSmsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadThreads()
        }
    }

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            if (!isMarkingAsRead) {
                loadThreads()
            }
        }
    }

    private var isInternalDelete = false

    init {
        try {
            application.contentResolver.registerContentObserver(
                Uri.parse("content://sms"),
                true,
                contentObserver
            )
            val filter = android.content.IntentFilter("com.ripple.chat.NEW_SMS")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                application.registerReceiver(newSmsReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                application.registerReceiver(newSmsReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        viewModelScope.launch {
            trashManager.deletedThreads.collect {
                if (!isMarkingAsRead && !isInternalDelete) {
                    loadThreads()
                }
            }
        }

        viewModelScope.launch {
            bromeSettings.appearanceFlow.collect { appConfig ->
                currentAppearance = appConfig
                if (!isMarkingAsRead) {
                    loadThreads()
                }
            }
        }

        // Periodic loop: auto-delete cleanup every 60 seconds
        viewModelScope.launch {
            while (true) {
                delay(60000)
                val now = System.currentTimeMillis()
                performPeriodicAutoDelete(now)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().contentResolver.unregisterContentObserver(contentObserver)
            getApplication<Application>().unregisterReceiver(newSmsReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun performPeriodicAutoDelete(now: Long) {
        val appearance = currentAppearance ?: bromeSettings.appearanceFlow.first()
        val starredIds = _starredThreadIds.value
        val currentList = _threads.value
        val threadsToDelete = mutableSetOf<Long>()

        if (appearance.otpAutoDelete != OtpAutoDeleteTime.NEVER) {
            val maxOtpAgeMs = appearance.otpAutoDelete.hours * 3600 * 1000L
            currentList.filter { thread ->
                if (thread.threadId in starredIds) return@filter false
                val isOtp = thread.category == SmsCategory.OTP || com.ripple.chat.utils.OtpParser.parseOtp(thread.body, thread.date) != null
                val msgTime = if (thread.date < 100_000_000_000L) thread.date * 1000L else thread.date
                isOtp && (now - msgTime >= maxOtpAgeMs)
            }.forEach { threadsToDelete.add(it.threadId) }
        }

        if (appearance.spamAutoDelete != SpamAutoDeleteTime.NEVER) {
            val maxSpamAgeMs = appearance.spamAutoDelete.days * 24 * 3600 * 1000L
            currentList.filter { thread ->
                if (thread.threadId in starredIds) return@filter false
                val isSpam = thread.category == SmsCategory.SPAM
                val msgTime = if (thread.date < 100_000_000_000L) thread.date * 1000L else thread.date
                isSpam && (now - msgTime >= maxSpamAgeMs)
            }.forEach { threadsToDelete.add(it.threadId) }
        }

        if (threadsToDelete.isNotEmpty()) {
            isInternalDelete = true
            try {
                trashManager.softDelete(threadsToDelete)
            } finally {
                isInternalDelete = false
            }
            _threads.value = currentList.filter { it.threadId !in threadsToDelete }
            otpBannerManager.scanThreads(_threads.value)
        }

        val trashAgeMs = appearance.trashAutoDelete.days * 24 * 3600 * 1000L
        trashManager.cleanupOldTrashWithCustomAge(repository, trashAgeMs)
    }

    fun loadThreads() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(50L) // Debounce rapid ContentObserver triggers
            _isLoading.value = true
            com.ripple.chat.data.ConversationRebuilder.rebuild(getApplication())
            val now = System.currentTimeMillis()
            val allThreads = repository.getConversations().toMutableList()
            val deletedIds = trashManager.getDeletedThreadIds()
            val appearance = currentAppearance ?: bromeSettings.appearanceFlow.first()
            val starredIds = _starredThreadIds.value

            val expiredIds = mutableSetOf<Long>()
            val validThreads = mutableListOf<ChatThread>()

            for (thread in allThreads) {
                if (thread.threadId in deletedIds) continue

                // 1. Check OTP auto-delete rule
                if (appearance.otpAutoDelete != OtpAutoDeleteTime.NEVER && thread.threadId !in starredIds) {
                    val isOtp = thread.category == SmsCategory.OTP || com.ripple.chat.utils.OtpParser.parseOtp(thread.body, thread.date) != null
                    val maxOtpAgeMs = appearance.otpAutoDelete.hours * 3600 * 1000L
                    val msgTime = if (thread.date < 100_000_000_000L) thread.date * 1000L else thread.date
                    if (isOtp && (now - msgTime >= maxOtpAgeMs)) {
                        expiredIds.add(thread.threadId)
                        continue
                    }
                }

                // 2. Check Spam auto-delete rule
                if (appearance.spamAutoDelete != SpamAutoDeleteTime.NEVER && thread.threadId !in starredIds) {
                    val isSpam = thread.category == SmsCategory.SPAM
                    val maxSpamAgeMs = appearance.spamAutoDelete.days * 24 * 3600 * 1000L
                    val msgTime = if (thread.date < 100_000_000_000L) thread.date * 1000L else thread.date
                    if (isSpam && (now - msgTime >= maxSpamAgeMs)) {
                        expiredIds.add(thread.threadId)
                        continue
                    }
                }

                validThreads.add(thread)
            }

            _threads.value = validThreads
            otpBannerManager.scanThreads(_threads.value)
            _isLoading.value = false

            if (expiredIds.isNotEmpty()) {
                isInternalDelete = true
                try {
                    trashManager.softDelete(expiredIds)
                } finally {
                    isInternalDelete = false
                }
            }

            // Recycle Bin auto-cleaning
            val trashAgeMs = appearance.trashAutoDelete.days * 24 * 3600 * 1000L
            trashManager.cleanupOldTrashWithCustomAge(repository, trashAgeMs)
        }
    }

    // Selection mode state methods
    fun setSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) {
            _selectedThreadIds.value = emptySet()
        }
    }

    fun toggleSelection(threadId: Long) {
        val current = _selectedThreadIds.value
        val next = if (current.contains(threadId)) current - threadId else current + threadId
        _selectedThreadIds.value = next
        if (next.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun selectAll(ids: List<Long>) {
        _selectedThreadIds.value = ids.toSet()
        _isSelectionMode.value = true
    }

    fun clearSelection() {
        _selectedThreadIds.value = emptySet()
        _isSelectionMode.value = false
    }

    // Star & Archive methods
    fun starThreads(threadIds: Set<Long>) {
        val newSet = _starredThreadIds.value + threadIds
        _starredThreadIds.value = newSet
        starredPrefs.edit().putStringSet("starred_ids", newSet.map { it.toString() }.toSet()).apply()
    }

    fun unstarThreads(threadIds: Set<Long>) {
        val newSet = _starredThreadIds.value - threadIds
        _starredThreadIds.value = newSet
        starredPrefs.edit().putStringSet("starred_ids", newSet.map { it.toString() }.toSet()).apply()
    }

    fun archiveThreads(threadIds: Set<Long>) {
        val newSet = _archivedThreadIds.value + threadIds
        _archivedThreadIds.value = newSet
        archivedPrefs.edit().putStringSet("archived_ids", newSet.map { it.toString() }.toSet()).apply()
    }

    fun unarchiveThreads(threadIds: Set<Long>) {
        val newSet = _archivedThreadIds.value - threadIds
        _archivedThreadIds.value = newSet
        archivedPrefs.edit().putStringSet("archived_ids", newSet.map { it.toString() }.toSet()).apply()
    }

    fun dismissOtpBanner(id: String) {
        otpBannerManager.dismissItem(id)
    }

    fun deleteThread(threadId: Long) {
        trashManager.softDelete(threadId)
        _threads.value = _threads.value.filter { it.threadId != threadId }
    }

    fun deleteThreads(threadIds: Set<Long>) {
        trashManager.softDelete(threadIds)
        _threads.value = _threads.value.filter { it.threadId !in threadIds }
    }

    fun restoreThreads(threadIds: Set<Long>) {
        trashManager.restore(threadIds)
        loadThreads()
    }

    fun markAsNonSpam(threadIds: Set<Long>) {
        if (threadIds.isEmpty()) return
        val currentThreads = _threads.value
        val targets = currentThreads.filter { it.threadId in threadIds }
        val addresses = targets.map { it.address }.toSet()
        repository.markAsNonSpam(threadIds, addresses)

        val customKeywords = com.ripple.chat.data.CategoryKeywordManager.getAllKeywords(getApplication())
        _threads.value = currentThreads.map { thread ->
            if (thread.threadId in threadIds) {
                val newCategory = com.ripple.chat.utils.CategoryClassifier.classify(
                    thread.address,
                    thread.body,
                    thread.contactName,
                    customKeywords,
                    isExplicitlyNotSpam = true
                )
                thread.copy(category = newCategory)
            } else thread
        }
        viewModelScope.launch {
            loadThreads()
        }
    }

    fun unmarkAsNonSpam(threadIds: Set<Long>) {
        if (threadIds.isEmpty()) return
        val currentThreads = _threads.value
        val targets = currentThreads.filter { it.threadId in threadIds }
        val addresses = targets.map { it.address }.toSet()
        repository.unmarkAsNonSpam(threadIds, addresses)
        viewModelScope.launch {
            loadThreads()
        }
    }

    fun markAsRead(threadId: Long) {
        _threads.value = _threads.value.map {
            if (it.threadId == threadId) it.copy(read = true, unreadCount = 0) else it
        }
        viewModelScope.launch {
            repository.markAsRead(threadId)
            loadThreads()
        }
    }

    fun markAsUnread(threadId: Long) {
        _threads.value = _threads.value.map {
            if (it.threadId == threadId) it.copy(read = false, unreadCount = 1) else it
        }
        viewModelScope.launch {
            repository.markAsUnread(threadId)
            loadThreads()
        }
    }

    fun markAllAsRead(threadIds: List<Long>) {
        if (threadIds.isEmpty()) return
        val idSet = threadIds.toSet()
        _threads.value = _threads.value.map {
            if (it.threadId in idSet) it.copy(read = true, unreadCount = 0) else it
        }
        viewModelScope.launch {
            repository.markAllAsRead(threadIds)
            loadThreads()
        }
    }

    fun markAllAsUnread(threadIds: List<Long>) {
        if (threadIds.isEmpty()) return
        val idSet = threadIds.toSet()
        _threads.value = _threads.value.map {
            if (it.threadId in idSet) it.copy(read = false, unreadCount = 1) else it
        }
        viewModelScope.launch {
            repository.markAllAsUnread(threadIds)
            loadThreads()
        }
    }

    private val _ignoredTriageIds = MutableStateFlow<Set<Long>>(
        triagePrefs.getStringSet("ignored_ids", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    )
    val ignoredTriageIds: StateFlow<Set<Long>> = _ignoredTriageIds.asStateFlow()

    fun ignoreTriageItem(threadId: Long) {
        val newSet = _ignoredTriageIds.value + threadId
        _ignoredTriageIds.value = newSet
        triagePrefs.edit().putStringSet("ignored_ids", newSet.map { it.toString() }.toSet()).apply()
    }
}
