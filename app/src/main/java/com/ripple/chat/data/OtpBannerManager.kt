package com.ripple.chat.data

import com.ripple.chat.utils.OtpParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OtpBannerItem(
    val id: String,
    val threadId: Long,
    val sender: String,
    val code: String,
    val expiresAt: Long,
    val durationMs: Long,
    val usedDefaultExpiry: Boolean,
    val receivedAt: Long,
    val fullText: String
)

class OtpBannerManager {
    private val _items = MutableStateFlow<List<OtpBannerItem>>(emptyList())
    val items: StateFlow<List<OtpBannerItem>> = _items.asStateFlow()

    private val _dismissedIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedIds: StateFlow<Set<String>> = _dismissedIds.asStateFlow()

    fun processMessage(threadId: Long, sender: String, fullText: String, receivedAt: Long) {
        val parseResult = OtpParser.parseOtp(fullText, receivedAt) ?: return
        val id = "$sender-${parseResult.code}"

        val currentList = _items.value.toMutableList()
        val existingIndex = currentList.indexOfFirst {
            it.sender.equals(sender, ignoreCase = true) && it.code == parseResult.code
        }

        if (existingIndex != -1) {
            val existing = currentList[existingIndex]
            if (receivedAt - existing.receivedAt <= 60_000L) {
                // Deduplication within 60s: update existing card
                currentList[existingIndex] = existing.copy(
                    expiresAt = parseResult.expiresAt,
                    durationMs = parseResult.durationMs,
                    receivedAt = receivedAt,
                    fullText = fullText
                )
                _items.value = currentList
                return
            }
        }

        val newItem = OtpBannerItem(
            id = id,
            threadId = threadId,
            sender = sender,
            code = parseResult.code,
            expiresAt = parseResult.expiresAt,
            durationMs = parseResult.durationMs,
            usedDefaultExpiry = parseResult.usedDefaultExpiry,
            receivedAt = receivedAt,
            fullText = fullText
        )

        currentList.removeAll { it.id == id }
        currentList.add(0, newItem)
        _items.value = currentList
    }

    fun dismissItem(id: String) {
        _dismissedIds.value = _dismissedIds.value + id
    }

    fun scanThreads(threads: List<ChatThread>) {
        val now = System.currentTimeMillis()
        val validThreadIds = threads.map { it.threadId }.toSet()
        _items.value = _items.value.filter { it.threadId in validThreadIds }

        threads.forEach { thread ->
            if (now - thread.date <= 20 * 60 * 1000L) {
                val sender = thread.contactName ?: thread.address
                processMessage(thread.threadId, sender, thread.body, thread.date)
            }
        }
    }
}
