package com.ripple.chat.island

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object IslandQueueManager {
    private val _messages = MutableStateFlow<List<IslandMessage>>(emptyList())
    val messages: StateFlow<List<IslandMessage>> = _messages.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _sampleMessage = MutableStateFlow<IslandMessage?>(null)
    val sampleMessage: StateFlow<IslandMessage?> = _sampleMessage.asStateFlow()

    private val _sampleState = MutableStateFlow(IslandState.COMPACT)
    val sampleState: StateFlow<IslandState> = _sampleState.asStateFlow()

    val isShowingSample: Boolean
        get() = _messages.value.isEmpty() && _sampleMessage.value != null

    val currentMessage: IslandMessage?
        get() {
            val list = _messages.value
            val idx = _currentIndex.value
            return if (list.isNotEmpty() && idx in list.indices) {
                list[idx]
            } else {
                _sampleMessage.value
            }
        }

    fun setSample(message: IslandMessage, state: IslandState = IslandState.COMPACT) {
        if (_messages.value.isEmpty()) {
            _sampleMessage.value = message
            _sampleState.value = state
        }
    }

    fun expandSample() {
        _sampleState.value = IslandState.EXPANDED
    }

    fun collapseSample() {
        _sampleState.value = IslandState.COMPACT
    }

    fun clearSample() {
        _sampleMessage.value = null
    }

    fun enqueue(message: IslandMessage) {
        _sampleMessage.value = null // A real incoming message replaces the sample immediately
        val currentList = _messages.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == message.id }
        if (existingIndex >= 0) {
            currentList[existingIndex] = message
        } else {
            currentList.add(message)
        }
        _messages.value = currentList
        // Always focus on latest if new arrives
        _currentIndex.value = currentList.lastIndex
    }

    fun dismissCurrent(onQueueEmpty: () -> Unit) {
        val currentList = _messages.value.toMutableList()
        val idx = _currentIndex.value
        if (currentList.isNotEmpty() && idx in currentList.indices) {
            currentList.removeAt(idx)
            _messages.value = currentList
            if (currentList.isEmpty()) {
                _currentIndex.value = 0
                onQueueEmpty()
            } else {
                _currentIndex.value = (_currentIndex.value).coerceIn(0, currentList.lastIndex)
            }
        } else {
            _messages.value = emptyList()
            _currentIndex.value = 0
            onQueueEmpty()
        }
    }

    fun pageNext() {
        val list = _messages.value
        if (list.size > 1) {
            _currentIndex.value = (_currentIndex.value + 1) % list.size
        }
    }

    fun pagePrevious() {
        val list = _messages.value
        if (list.size > 1) {
            _currentIndex.value = if (_currentIndex.value - 1 < 0) list.lastIndex else _currentIndex.value - 1
        }
    }

    fun clear() {
        _messages.value = emptyList()
        _currentIndex.value = 0
    }
}
