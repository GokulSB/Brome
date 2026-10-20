package com.ripple.chat.ui.components

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object BromeToast {
    enum class ToastType {
        SUCCESS,
        INFO,
        WARNING,
        ERROR
    }

    data class ToastInfo(
        val message: String,
        val type: ToastType = ToastType.INFO,
        val icon: ImageVector? = null,
        val id: Long = System.currentTimeMillis()
    )

    private val _currentToast = MutableStateFlow<ToastInfo?>(null)
    val currentToast = _currentToast.asStateFlow()

    fun show(
        context: Context,
        message: String,
        icon: ImageVector? = null,
        type: ToastType? = null
    ) {
        val resolvedType = type ?: inferToastType(message)
        _currentToast.value = ToastInfo(
            message = message,
            type = resolvedType,
            icon = icon,
            id = System.currentTimeMillis()
        )

        // If no Compose subscriber is active (e.g. background service), fallback to system toast
        if (_currentToast.subscriptionCount.value == 0) {
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
                    } catch (ignored: Exception) {}
                }
            } catch (ignored: Exception) {}
        }
    }

    fun show(
        message: String,
        icon: ImageVector? = null,
        type: ToastType? = null
    ) {
        val resolvedType = type ?: inferToastType(message)
        _currentToast.value = ToastInfo(
            message = message,
            type = resolvedType,
            icon = icon,
            id = System.currentTimeMillis()
        )
    }

    private fun inferToastType(message: String): ToastType {
        val lower = message.lowercase()
        return when {
            lower.contains("error") || lower.contains("fail") -> ToastType.ERROR
            lower.contains("already exists") || lower.contains("conflict") || lower.contains("please enter") || lower.contains("warning") -> ToastType.WARNING
            lower.contains("copied") || lower.contains("created") || lower.contains("saved") || lower.contains("success") || lower.contains("up to date") -> ToastType.SUCCESS
            else -> ToastType.INFO
        }
    }

    fun isDarkTheme(context: Context): Boolean {
        val prefs = context.getSharedPreferences("brome_theme_prefs", Context.MODE_PRIVATE)
        val modeStr = prefs.getString("theme_mode", "SYSTEM")
        return when (modeStr) {
            "LIGHT" -> false
            "DARK" -> true
            else -> {
                val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
}

@Composable
fun BromeToastHost(modifier: Modifier = Modifier) {
    val toastInfo by BromeToast.currentToast.collectAsState()
    var visibleToast by remember { mutableStateOf<BromeToast.ToastInfo?>(null) }

    LaunchedEffect(toastInfo) {
        if (toastInfo != null) {
            visibleToast = toastInfo
            delay(2400)
            if (visibleToast?.id == toastInfo?.id) {
                visibleToast = null
            }
        }
    }

    AnimatedVisibility(
        visible = visibleToast != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f)
        ) + fadeIn(animationSpec = tween(180)) + scaleIn(
            initialScale = 0.88f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f)
        ) + fadeOut(animationSpec = tween(150)) + scaleOut(
            targetScale = 0.88f,
            animationSpec = tween(150)
        ),
        modifier = modifier
            .zIndex(100f)
            .imePadding()
            .navigationBarsPadding()
            .padding(bottom = 96.dp, start = 20.dp, end = 20.dp)
    ) {
        visibleToast?.let { toast ->
            val toastType = toast.type
            val icon = toast.icon ?: when (toastType) {
                BromeToast.ToastType.SUCCESS -> {
                    if (toast.message.contains("copy", ignoreCase = true)) {
                        Icons.Rounded.ContentCopy
                    } else {
                        Icons.Rounded.CheckCircle
                    }
                }
                BromeToast.ToastType.WARNING -> Icons.Rounded.WarningAmber
                BromeToast.ToastType.ERROR -> Icons.Rounded.ErrorOutline
                BromeToast.ToastType.INFO -> {
                    if (toast.message.contains("reminder", ignoreCase = true)) {
                        Icons.Rounded.NotificationsActive
                    } else if (toast.message.contains("remov", ignoreCase = true) || toast.message.contains("delet", ignoreCase = true)) {
                        Icons.Rounded.DeleteOutline
                    } else {
                        Icons.Rounded.Info
                    }
                }
            }

            val (iconContainerColor, iconColor) = when (toastType) {
                BromeToast.ToastType.SUCCESS -> Pair(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                BromeToast.ToastType.WARNING -> Pair(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer
                )
                BromeToast.ToastType.ERROR -> Pair(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
                BromeToast.ToastType.INFO -> Pair(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        visibleToast = null
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = iconContainerColor,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = toast.message,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
