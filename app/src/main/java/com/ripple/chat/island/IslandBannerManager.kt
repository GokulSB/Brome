package com.ripple.chat.island

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object IslandBannerManager {
    private val _currentBanner = MutableStateFlow<IslandMessage?>(null)
    val currentBanner: StateFlow<IslandMessage?> = _currentBanner.asStateFlow()

    fun postBanner(message: IslandMessage) {
        _currentBanner.value = message
    }

    fun dismiss() {
        _currentBanner.value = null
    }
}
