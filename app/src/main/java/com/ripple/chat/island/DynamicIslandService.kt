package com.ripple.chat.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ripple.chat.R
import com.ripple.chat.data.BromeSettings
import com.ripple.chat.data.DynamicIslandSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class DynamicIslandService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        const val ACTION_SHOW = "com.ripple.chat.island.ACTION_SHOW"
        const val ACTION_SHOW_SAMPLE = "com.ripple.chat.island.ACTION_SHOW_SAMPLE"
        const val ACTION_EXPAND = "com.ripple.chat.island.ACTION_EXPAND"
        const val ACTION_COLLAPSE = "com.ripple.chat.island.ACTION_COLLAPSE"
        const val EXTRA_SAMPLE_TYPE = "com.ripple.chat.island.EXTRA_SAMPLE_TYPE"
        const val EXTRA_SAMPLE_EXPANDED = "com.ripple.chat.island.EXTRA_SAMPLE_EXPANDED"
        private const val CHANNEL_ID = "brome_island_channel"
        private const val FGS_NOTIFICATION_ID = 2002

        @Volatile
        var isAttached: Boolean = false
            internal set

        private val attachmentCallbacks = java.util.concurrent.CopyOnWriteArrayList<CompletableDeferred<Boolean>>()

        fun registerAttachmentCallback(deferred: CompletableDeferred<Boolean>) {
            if (isAttached) {
                deferred.complete(true)
            } else {
                attachmentCallbacks.add(deferred)
            }
        }

        internal fun notifyAttached(success: Boolean) {
            val iterator = attachmentCallbacks.iterator()
            while (iterator.hasNext()) {
                val deferred = iterator.next()
                deferred.complete(success)
            }
            attachmentCallbacks.clear()
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var isViewAttached = false
    private lateinit var windowParams: WindowManager.LayoutParams

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sampleDismissJob: Job? = null
    private var cutoutWidthDp by mutableStateOf(0.dp)
    private var topInsetDp by mutableStateOf(0.dp)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            notifyAttached(false)
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent?.action
        if (action == ACTION_EXPAND) {
            android.os.Trace.beginSection("IslandExpand")
            android.util.Log.d("IslandAnim", "ACTION_EXPAND received at ${System.currentTimeMillis()}")
            IslandQueueManager.expandSample()
            return START_NOT_STICKY
        }
        if (action == ACTION_COLLAPSE) {
            android.util.Log.d("IslandAnim", "ACTION_COLLAPSE received at ${System.currentTimeMillis()}")
            IslandQueueManager.collapseSample()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            val bromeSettings = BromeSettings(this@DynamicIslandService)
            val settings = bromeSettings.islandSettingsFlow.first()

            if (!settings.enabled && action != ACTION_SHOW_SAMPLE) {
                notifyAttached(false)
                stopSelf()
                return@launch
            }

            if (action == ACTION_SHOW_SAMPLE) {
                if (IslandQueueManager.messages.value.isEmpty()) {
                    val sampleType = intent.getStringExtra(EXTRA_SAMPLE_TYPE) ?: "BANK"
                    val isExpanded = intent.getBooleanExtra(EXTRA_SAMPLE_EXPANDED, false)

                    val sampleMsg = when (sampleType) {
                        "OTP" -> IslandMessage(
                            id = 999902L,
                            address = "Google",
                            senderName = "Google",
                            body = "849201 is your Google verification code. Do not share it.",
                            category = com.ripple.chat.data.SmsCategory.OTP,
                            otpCode = "849201",
                            otpExpirySeconds = 300,
                            notificationId = 9902
                        )
                        "LONG_TEXT" -> IslandMessage(
                            id = 999903L,
                            address = "Sarah Jenkins",
                            senderName = "Sarah Jenkins",
                            body = "Hey! Are you still joining us for dinner tonight? We're heading over to the Italian place around 7:30. Let me know if you want me to reserve an extra seat for you!",
                            category = com.ripple.chat.data.SmsCategory.PERSONAL,
                            notificationId = 9903
                        )
                        else -> IslandMessage(
                            id = 999901L,
                            address = "JM-KOTAKB-S",
                            senderName = "JM-KOTAKB-S",
                            body = "Sent Rs.2,450.00 from Kotak Bank AC X9102 to Dinner on 16-Oct-26. UPI Ref 429104812.",
                            category = com.ripple.chat.data.SmsCategory.FINANCE,
                            transactionAmount = "−₹2,450",
                            merchant = "Dinner",
                            accountInfo = "Kotak Bank X9102",
                            notificationId = 9901
                        )
                    }

                    IslandQueueManager.setSample(
                        message = sampleMsg,
                        state = if (isExpanded) IslandState.EXPANDED else IslandState.COMPACT
                    )

                    sampleDismissJob?.cancel()
                    sampleDismissJob = serviceScope.launch {
                        delay(15000)
                        if (IslandQueueManager.isShowingSample) {
                            IslandQueueManager.clearSample()
                            removeOverlayAndStop()
                        }
                    }
                }
            } else {
                // Real message incoming - cancel sample timer
                sampleDismissJob?.cancel()
            }

            attachOrUpdateOverlay(settings)
        }

        return START_NOT_STICKY
    }

    private fun attachOrUpdateOverlay(settings: DynamicIslandSettings) {
        if (composeView == null) {
            val view = ComposeView(this).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewTreeLifecycleOwner(this@DynamicIslandService)
                setViewTreeSavedStateRegistryOwner(this@DynamicIslandService)
                setViewTreeViewModelStoreOwner(this@DynamicIslandService)

                // Detect Display Cutout
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setOnApplyWindowInsetsListener { _, insets ->
                        val cutout = insets.displayCutout
                        if (cutout != null) {
                            val rect = cutout.boundingRects.firstOrNull { it.top == 0 }
                            if (rect != null) {
                                val density = resources.displayMetrics.density
                                cutoutWidthDp = (rect.width() / density).dp
                                topInsetDp = (rect.bottom / density).dp
                            }
                        }
                        insets
                    }
                }

                setContent {
                    val messages by IslandQueueManager.messages.collectAsState()
                    val currentIndex by IslandQueueManager.currentIndex.collectAsState()
                    val sampleMsg by IslandQueueManager.sampleMessage.collectAsState()
                    val bromeSettings = remember { BromeSettings(this@DynamicIslandService) }
                    val currentSettings by bromeSettings.islandSettingsFlow.collectAsState(initial = settings)

                    val currentMsg = if (messages.isNotEmpty() && currentIndex in messages.indices) {
                        messages[currentIndex]
                    } else {
                        sampleMsg
                    }

                    LaunchedEffect(currentSettings.offsetX, currentSettings.offsetY, currentSettings.position, currentSettings.size) {
                        updateOverlayPositionAndScale(currentSettings)
                    }

                    if (currentMsg != null) {
                        DynamicIslandOverlay(
                            message = currentMsg,
                            queue = messages,
                            currentIndex = currentIndex,
                            settings = currentSettings,
                            cutoutWidthDp = cutoutWidthDp,
                            topInsetDp = topInsetDp,
                            onRequestFocusForReply = { needsFocus ->
                                setWindowFocusable(needsFocus)
                            },
                            onRequestExpand = { targetHeightDp, onReadyToMorph ->
                                handleRequestExpand(currentSettings, targetHeightDp, onReadyToMorph)
                            },
                            onCollapsed = {
                                handleCollapsed(currentSettings, currentMsg, messages.size)
                            },
                            onPagePrevious = {
                                IslandQueueManager.pagePrevious()
                            },
                            onPageNext = {
                                IslandQueueManager.pageNext()
                            },
                            onDismiss = {
                                dismissIsland()
                            },
                            onOpenConversation = { msg ->
                                IslandActions.openConversation(
                                    context = this@DynamicIslandService,
                                    address = msg.address,
                                    threadId = msg.threadId,
                                    notificationId = msg.notificationId
                                )
                                dismissIsland()
                            },
                            onCopyOtp = { msg ->
                                msg.otpCode?.let { code ->
                                    IslandActions.copyOtpCode(
                                        context = this@DynamicIslandService,
                                        code = code,
                                        notificationId = msg.notificationId
                                    )
                                }
                            },
                            onMarkRead = { msg ->
                                serviceScope.launch {
                                    IslandActions.markAsRead(
                                        context = this@DynamicIslandService,
                                        address = msg.address,
                                        notificationId = msg.notificationId,
                                        threadId = msg.threadId
                                    )
                                }
                            },
                            onDeleteSpam = { msg ->
                                serviceScope.launch {
                                    IslandActions.deleteSpam(
                                        context = this@DynamicIslandService,
                                        address = msg.address,
                                        notificationId = msg.notificationId,
                                        threadId = msg.threadId
                                    )
                                }
                            },
                            onSendReply = { msg, text ->
                                serviceScope.launch {
                                    IslandActions.sendReply(
                                        context = this@DynamicIslandService,
                                        address = msg.address,
                                        body = text,
                                        notificationId = msg.notificationId
                                    )
                                }
                            }
                        )
                    }
                }
            }

            val dm = resources.displayMetrics
            val density = dm.density
            val scaleMultiplier = when (settings.size.uppercase()) {
                "COMPACT" -> 0.9f
                "LARGE" -> 1.1f
                else -> 1.0f
            }
            val isAroundCamera = settings.position.uppercase() == "CAMERA" && cutoutWidthDp > 0.dp
            val clamped = IslandPositionClamper.clamp(
                screenWidthDp = dm.widthPixels / density,
                screenHeightDp = dm.heightPixels / density,
                statusBarTopInsetDp = topInsetDp.value,
                cutoutHeightDp = topInsetDp.value,
                pillWidthDp = 330f,
                pillHeightDp = 36f,
                scaleMultiplier = scaleMultiplier,
                isAroundCamera = isAroundCamera,
                userOffsetX = settings.offsetX,
                userOffsetY = settings.offsetY
            )

            val isInitiallyExpanded = if (IslandQueueManager.isShowingSample) {
                IslandQueueManager.sampleState.value == IslandState.EXPANDED
            } else {
                !settings.actExpand
            }

            val initialMsg = IslandQueueManager.currentMessage
            val compactWidth = if (initialMsg != null) {
                calculateCompactWidth(
                    message = initialMsg,
                    settings = settings,
                    queueSize = IslandQueueManager.messages.value.size,
                    isAroundCamera = isAroundCamera,
                    cutoutWidthDp = cutoutWidthDp
                )
            } else {
                210.dp
            }

            val marginDp = 4f
            val initialWidthDp = if (isInitiallyExpanded) 330f else compactWidth.value
            val initialHeightDp = if (isInitiallyExpanded) estimateExpandedHeight(initialMsg, settings).value else 36f
            val initialWidthPx = (((initialWidthDp + marginDp) * scaleMultiplier) * density).toInt()
            val initialHeightPx = (((initialHeightDp + marginDp) * scaleMultiplier) * density).toInt()

            windowParams = WindowManager.LayoutParams(
                initialWidthPx,
                initialHeightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                alpha = 1.0f
                x = (clamped.xDp * density).toInt()
                y = (clamped.yDp * density).toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    preferredRefreshRate = 120f
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }

            view.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                val w = r - l
                val h = b - t
                val ow = or - ol
                val oh = ob - ot
                if (w != ow || h != oh) {
                    android.util.Log.d("IslandAnim", "ComposeView layout changed: ${ow}x${oh} -> ${w}x${h} at ${System.currentTimeMillis()}")
                }
            }

            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    android.util.Log.d("IslandAnim", "ComposeView ACTION_DOWN at (${event.x}, ${event.y}) raw(${event.rawX}, ${event.rawY})")
                }
                false
            }

            try {
                android.util.Log.d("IslandAnim", "WindowManager.addView at ${System.currentTimeMillis()}: x=${windowParams.x}, y=${windowParams.y}, w=${windowParams.width}, h=${windowParams.height}")
                windowManager.addView(view, windowParams)
                composeView = view
                isViewAttached = true
                isAttached = true
                notifyAttached(true)
            } catch (e: Exception) {
                e.printStackTrace()
                isViewAttached = false
                isAttached = false
                notifyAttached(false)
                stopSelf()
            }
        } else {
            isViewAttached = true
            isAttached = true
            notifyAttached(true)
        }
    }

    private fun handleRequestExpand(
        settings: DynamicIslandSettings,
        targetExpandedHeightDp: androidx.compose.ui.unit.Dp,
        onReadyToMorph: () -> Unit
    ) {
        if (!isViewAttached || composeView == null) {
            onReadyToMorph()
            return
        }
        val dm = resources.displayMetrics
        val density = dm.density
        val scaleMultiplier = when (settings.size.uppercase()) {
            "COMPACT" -> 0.9f
            "LARGE" -> 1.1f
            else -> 1.0f
        }
        val marginDp = 4f
        val targetW = (((330f + marginDp) * scaleMultiplier) * density).toInt()
        val targetH = (((targetExpandedHeightDp.value + marginDp) * scaleMultiplier) * density).toInt()

        if (windowParams.width != targetW || windowParams.height != targetH) {
            windowParams.width = targetW
            windowParams.height = targetH
            try {
                android.util.Log.d("IslandAnim", "WindowManager.updateViewLayout (expand to ${targetW}x${targetH}) at ${System.currentTimeMillis()}")
                windowManager.updateViewLayout(composeView, windowParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        composeView?.doOnPreDraw {
            onReadyToMorph()
        } ?: composeView?.post {
            onReadyToMorph()
        } ?: onReadyToMorph()
    }

    private fun handleCollapsed(
        settings: DynamicIslandSettings,
        currentMsg: IslandMessage?,
        queueSize: Int
    ) {
        if (!isViewAttached || composeView == null) return
        val dm = resources.displayMetrics
        val density = dm.density
        val scaleMultiplier = when (settings.size.uppercase()) {
            "COMPACT" -> 0.9f
            "LARGE" -> 1.1f
            else -> 1.0f
        }
        val isAroundCamera = settings.position.uppercase() == "CAMERA" && cutoutWidthDp > 0.dp
        val compactWidth = if (currentMsg != null) {
            calculateCompactWidth(
                message = currentMsg,
                settings = settings,
                queueSize = queueSize,
                isAroundCamera = isAroundCamera,
                cutoutWidthDp = cutoutWidthDp
            )
        } else {
            210.dp
        }
        val marginDp = 4f
        val targetW = (((compactWidth.value + marginDp) * scaleMultiplier) * density).toInt()
        val targetH = (((36f + marginDp) * scaleMultiplier) * density).toInt()

        if (windowParams.width != targetW || windowParams.height != targetH) {
            windowParams.width = targetW
            windowParams.height = targetH
            try {
                android.util.Log.d("IslandAnim", "WindowManager.updateViewLayout (collapsed to ${targetW}x${targetH}) at ${System.currentTimeMillis()}")
                windowManager.updateViewLayout(composeView, windowParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateOverlayPositionAndScale(settings: DynamicIslandSettings) {
        if (!isViewAttached || composeView == null) return
        val dm = resources.displayMetrics
        val density = dm.density
        val screenWidthDp = dm.widthPixels / density
        val screenHeightDp = dm.heightPixels / density

        val scaleMultiplier = when (settings.size.uppercase()) {
            "COMPACT" -> 0.9f
            "LARGE" -> 1.1f
            else -> 1.0f
        }

        val isAroundCamera = settings.position.uppercase() == "CAMERA" && cutoutWidthDp > 0.dp

        val clamped = IslandPositionClamper.clamp(
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            statusBarTopInsetDp = topInsetDp.value,
            cutoutHeightDp = topInsetDp.value,
            pillWidthDp = 330f,
            pillHeightDp = 36f,
            scaleMultiplier = scaleMultiplier,
            isAroundCamera = isAroundCamera,
            userOffsetX = settings.offsetX,
            userOffsetY = settings.offsetY
        )

        val newX = (clamped.xDp * density).toInt()
        val newY = (clamped.yDp * density).toInt()

        if (windowParams.x != newX || windowParams.y != newY) {
            windowParams.x = newX
            windowParams.y = newY
            try {
                android.util.Log.d("IslandAnim", "WindowManager.updateViewLayout (position update: x=${newX}, y=${newY}) at ${System.currentTimeMillis()}")
                windowManager.updateViewLayout(composeView, windowParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setWindowFocusable(focusable: Boolean) {
        if (!isViewAttached || composeView == null) return
        val currentlyFocusable = (windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0
        if (currentlyFocusable == focusable) return
        try {
            if (focusable) {
                windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            android.util.Log.d("IslandAnim", "WindowManager.updateViewLayout (focusable=$focusable) at ${System.currentTimeMillis()}")
            windowManager.updateViewLayout(composeView, windowParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissIsland() {
        sampleDismissJob?.cancel()
        if (IslandQueueManager.isShowingSample) {
            IslandQueueManager.clearSample()
            removeOverlayAndStop()
        } else {
            IslandQueueManager.dismissCurrent {
                removeOverlayAndStop()
            }
        }
    }

    private fun removeOverlayAndStop() {
        serviceScope.launch(Dispatchers.Main) {
            try {
                if (isViewAttached && composeView != null) {
                    android.util.Log.d("IslandAnim", "WindowManager.removeView at ${System.currentTimeMillis()}")
                    windowManager.removeViewImmediate(composeView)
                    composeView = null
                    isViewAttached = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isAttached = false
            notifyAttached(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun initForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dynamic Island",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Active dynamic island notification"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Brome Island")
            .setContentText("Active")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    FGS_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                )
            } else {
                startForeground(FGS_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            if (isViewAttached && composeView != null) {
                windowManager.removeView(composeView)
                composeView = null
                isViewAttached = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isAttached = false
        notifyAttached(false)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
