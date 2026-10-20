package com.ripple.chat.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TrashManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: TrashManager? = null

        operator fun invoke(context: Context): TrashManager {
            return instance ?: synchronized(this) {
                instance ?: TrashManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("trash_prefs", Context.MODE_PRIVATE)
    private val permanentPrefs: SharedPreferences = context.getSharedPreferences("permanently_deleted_prefs", Context.MODE_PRIVATE)

    private val _deletedThreads = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val deletedThreads: StateFlow<Map<Long, Long>> = _deletedThreads

    init {
        loadDeletedThreads()
    }

    private fun loadDeletedThreads() {
        val allEntries = prefs.all
        val map = mutableMapOf<Long, Long>()
        for ((key, value) in allEntries) {
            val threadId = key.toLongOrNull()
            val timestamp = value as? Long
            if (threadId != null && timestamp != null) {
                map[threadId] = timestamp
            }
        }
        _deletedThreads.value = map
    }

    fun softDelete(threadId: Long) {
        softDelete(listOf(threadId))
    }

    fun softDelete(threadIds: Collection<Long>) {
        if (threadIds.isEmpty()) return
        val timestamp = System.currentTimeMillis()
        val editor = prefs.edit()
        for (id in threadIds) {
            editor.putLong(id.toString(), timestamp)
        }
        editor.apply()

        val currentMap = _deletedThreads.value.toMutableMap()
        for (id in threadIds) {
            currentMap[id] = timestamp
        }
        _deletedThreads.value = currentMap
    }

    fun restore(threadId: Long) {
        restore(listOf(threadId))
    }

    fun restore(threadIds: Collection<Long>) {
        if (threadIds.isEmpty()) return
        val editor = prefs.edit()
        val permEditor = permanentPrefs.edit()
        for (id in threadIds) {
            editor.remove(id.toString())
            permEditor.remove(id.toString())
        }
        editor.apply()
        permEditor.apply()

        val currentMap = _deletedThreads.value.toMutableMap()
        for (id in threadIds) {
            currentMap.remove(id)
        }
        _deletedThreads.value = currentMap
    }

    fun permanentlyDelete(threadId: Long) {
        permanentlyDelete(listOf(threadId))
    }

    fun permanentlyDelete(threadIds: Collection<Long>) {
        if (threadIds.isEmpty()) return
        val timestamp = System.currentTimeMillis()
        val permEditor = permanentPrefs.edit()
        val trashEditor = prefs.edit()
        for (id in threadIds) {
            permEditor.putLong(id.toString(), timestamp)
            trashEditor.remove(id.toString())
        }
        permEditor.apply()
        trashEditor.apply()

        val currentMap = _deletedThreads.value.toMutableMap()
        for (id in threadIds) {
            currentMap.remove(id)
        }
        _deletedThreads.value = currentMap
    }

    fun isPermanentlyDeleted(threadId: Long): Boolean {
        return permanentPrefs.contains(threadId.toString())
    }

    fun getPermanentlyDeletedIds(): Set<Long> {
        return permanentPrefs.all.keys.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun getDeletedThreadIds(): Set<Long> {
        return _deletedThreads.value.keys + getPermanentlyDeletedIds()
    }

    fun getDeletionTimestamp(threadId: Long): Long? {
        return _deletedThreads.value[threadId] ?: permanentPrefs.all[threadId.toString()] as? Long
    }

    fun isDeleted(threadId: Long): Boolean {
        return _deletedThreads.value.containsKey(threadId) || isPermanentlyDeleted(threadId)
    }

    suspend fun cleanupOldTrash(repository: SmsRepository) {
        cleanupOldTrashWithCustomAge(repository, 30L * 24 * 60 * 60 * 1000L)
    }

    suspend fun cleanupOldTrashWithCustomAge(repository: SmsRepository, maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<Long>()

        for ((threadId, timestamp) in _deletedThreads.value) {
            if (now - timestamp > maxAgeMs) {
                repository.deleteConversation(threadId)
                toRemove.add(threadId)
            }
        }

        if (toRemove.isNotEmpty()) {
            permanentlyDelete(toRemove)
        }
    }
}
