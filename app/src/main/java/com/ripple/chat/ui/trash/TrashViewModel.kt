package com.ripple.chat.ui.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.SmsRepository
import com.ripple.chat.data.TrashManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TrashViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SmsRepository(application)
    private val trashManager = TrashManager(application)

    private val _deletedThreads = MutableStateFlow<List<ChatThread>>(emptyList())
    val deletedThreads: StateFlow<List<ChatThread>> = _deletedThreads

    init {
        loadTrash()
    }

    fun loadTrash() {
        viewModelScope.launch {
            val deletedIds = trashManager.getDeletedThreadIds()
            if (deletedIds.isEmpty()) {
                _deletedThreads.value = emptyList()
            } else {
                val allThreads = repository.getConversations(deletedIds)
                _deletedThreads.value = allThreads
            }
        }
    }

    fun restoreThread(threadId: Long) {
        trashManager.restore(threadId)
        loadTrash()
    }

    fun permanentlyDeleteThread(threadId: Long) {
        viewModelScope.launch {
            repository.deleteConversation(threadId)
            trashManager.permanentlyDelete(threadId)
            loadTrash()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val ids = trashManager.getDeletedThreadIds()
            ids.forEach { threadId ->
                repository.deleteConversation(threadId)
            }
            trashManager.permanentlyDelete(ids)
            loadTrash()
        }
    }
}
