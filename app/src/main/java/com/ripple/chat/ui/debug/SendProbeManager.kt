package com.ripple.chat.ui.debug

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ripple.chat.receiver.SendProbeReceiver
import com.ripple.chat.receiver.SmsStatusReceiver
import com.ripple.chat.sms.SendResult
import com.ripple.chat.sms.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubItem(
    val subId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val countryIso: String
)

data class ProbeResult(
    val button: String,
    val state: String = "Idle",
    val sentCode: String? = null,
    val deliveryStatus: String? = null,
    val exception: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

object SendProbeManager {
    private const val TAG = "BromeSend"

    private val _subscriptions = MutableStateFlow<List<SubItem>>(emptyList())
    val subscriptions: StateFlow<List<SubItem>> = _subscriptions.asStateFlow()

    private val _selectedSubId = MutableStateFlow<Int?>(null)
    val selectedSubId: StateFlow<Int?> = _selectedSubId.asStateFlow()

    private val _results = MutableStateFlow<Map<String, ProbeResult>>(
        mapOf(
            "A" to ProbeResult("A"),
            "B" to ProbeResult("B"),
            "C" to ProbeResult("C"),
            "D" to ProbeResult("D")
        )
    )
    val results: StateFlow<Map<String, ProbeResult>> = _results.asStateFlow()

    private val _probeLogs = MutableStateFlow<List<String>>(emptyList())
    val probeLogs: StateFlow<List<String>> = _probeLogs.asStateFlow()

    private var activeA_MsgId: Long? = null
    private var isReceiverRegistered = false

    fun toHex(s: String): String {
        return s.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02X".format(it) }
    }

    fun addLog(msg: String) {
        Log.d(TAG, msg)
        _probeLogs.value = (_probeLogs.value + msg).takeLast(100)
    }

    fun selectSubId(subId: Int) {
        _selectedSubId.value = subId
    }

    fun refreshSubscriptions(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            addLog("Probe: READ_PHONE_STATE not granted; cannot list subscriptions")
            return
        }

        try {
            val sm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                context.getSystemService(SubscriptionManager::class.java)
            } else null
            val activeList = sm?.activeSubscriptionInfoList ?: emptyList()

            val items = activeList.map { info ->
                SubItem(
                    subId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    carrierName = info.carrierName?.toString() ?: "Unknown",
                    displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                    countryIso = info.countryIso?.uppercase() ?: ""
                )
            }
            _subscriptions.value = items

            val defaultSmsSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SubscriptionManager.getDefaultSmsSubscriptionId()
            } else -1

            if (_selectedSubId.value == null || items.none { it.subId == _selectedSubId.value }) {
                val defaultMatch = items.find { it.subId == defaultSmsSubId }
                _selectedSubId.value = defaultMatch?.subId ?: items.firstOrNull()?.subId
            }
            addLog("Probe: Found ${items.size} active subscription(s): ${items.map { "Slot ${it.slotIndex}: ${it.carrierName} (subId=${it.subId})" }}")
        } catch (e: Exception) {
            addLog("Probe: Error fetching subscriptions: ${e.message}")
        }
    }

    fun registerStatusReceiverIfNeeded(context: Context) {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter(SmsStatusReceiver.ACTION_SMS_STATUS_CHANGED)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val msgId = intent.getLongExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, -1L)
                    val status = intent.getStringExtra(SmsStatusReceiver.EXTRA_DELIVERY_STATUS) ?: ""
                    val reason = intent.getStringExtra(SmsStatusReceiver.EXTRA_ERROR_REASON)

                    if (msgId == activeA_MsgId) {
                        val current = _results.value["A"] ?: ProbeResult("A")
                        when (status) {
                            "SENT" -> {
                                updateResult("A", current.copy(state = "Sent", sentCode = "RESULT_OK (Success)"))
                                addLog("Probe A status broadcast: SENT (all parts OK)")
                            }
                            "DELIVERED" -> {
                                updateResult("A", current.copy(state = "Delivered", deliveryStatus = "STATUS_COMPLETE"))
                                addLog("Probe A status broadcast: DELIVERED")
                            }
                            "FAILED" -> {
                                updateResult("A", current.copy(state = "Failed", sentCode = reason ?: "FAILED", exception = reason))
                                addLog("Probe A status broadcast: FAILED ($reason)")
                            }
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not register status receiver for probe: ${e.message}")
        }
    }

    private fun updateResult(button: String, res: ProbeResult) {
        _results.value = _results.value.toMutableMap().apply {
            put(button, res)
        }
    }

    fun onProbeSent(button: String, resultCode: Int, errorReason: String, logMsg: String) {
        val current = _results.value[button] ?: ProbeResult(button)
        val newState = if (resultCode == android.app.Activity.RESULT_OK) "Sent" else "Failed"
        updateResult(button, current.copy(state = newState, sentCode = errorReason))
        addLog(logMsg)
    }

    fun onProbeDelivered(button: String, status: Int, statusText: String, logMsg: String) {
        val current = _results.value[button] ?: ProbeResult(button)
        updateResult(button, current.copy(state = "Delivered", deliveryStatus = statusText))
        addLog(logMsg)
    }

    // --- BUTTON A: Brome Default Path ---
    fun runProbeA(context: Context, destination: String, text: String, subId: Int?) {
        registerStatusReceiverIfNeeded(context)
        val subInfo = _subscriptions.value.find { it.subId == subId }
        val slot = subInfo?.slotIndex ?: -1
        val carrier = subInfo?.carrierName ?: "Unknown"
        val hex = toHex(destination)

        addLog("=== PROBE A: Brome Default Path ===")
        addLog("Probe A: dest='$destination' [hex: $hex], subId=$subId, slot=$slot, carrier=$carrier, method=SmsSender.send")
        updateResult("A", ProbeResult("A", state = "Sending...", sentCode = null, deliveryStatus = null))

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sender = SmsSender(context)
                val sendResult = sender.send(rawTo = destination, body = text, subId = subId)
                when (sendResult) {
                    is SendResult.Queued -> {
                        activeA_MsgId = sendResult.messageId
                        addLog("Probe A: Queued msgId=${sendResult.messageId}, threadId=${sendResult.threadId}. Awaiting radio callbacks...")
                        updateResult("A", ProbeResult("A", state = "Queued (msgId=${sendResult.messageId})", sentCode = "Pending radio ack"))
                    }
                    is SendResult.Failed -> {
                        addLog("Probe A: Immediate failure: ${sendResult.reason}")
                        updateResult("A", ProbeResult("A", state = "Failed", sentCode = sendResult.reason, exception = sendResult.reason))
                    }
                    is SendResult.NoPermission -> {
                        addLog("Probe A: Aborted - SEND_SMS permission missing")
                        updateResult("A", ProbeResult("A", state = "Failed", exception = "No SEND_SMS permission"))
                    }
                    is SendResult.NotDefaultApp -> {
                        addLog("Probe A: Aborted - Brome is not default SMS app")
                        updateResult("A", ProbeResult("A", state = "Failed", exception = "Not default SMS app"))
                    }
                    is SendResult.InvalidNumber -> {
                        addLog("Probe A: Aborted - Invalid destination number")
                        updateResult("A", ProbeResult("A", state = "Failed", exception = "Invalid destination number"))
                    }
                    is SendResult.EmptyBody -> {
                        addLog("Probe A: Aborted - Empty message body")
                        updateResult("A", ProbeResult("A", state = "Failed", exception = "Empty body"))
                    }
                }
            } catch (e: Exception) {
                addLog("Probe A Exception: ${e.message}")
                updateResult("A", ProbeResult("A", state = "Exception", exception = e.toString()))
            }
        }
    }

    // --- BUTTON B: Minimal SmsManager ---
    fun runProbeB(context: Context, destination: String, text: String, subId: Int?, buttonLabel: String = "B") {
        val destinationDigitsOnly = destination.filter { it.isDigit() || it == '+' }
        val hex = toHex(destinationDigitsOnly)
        val subInfo = _subscriptions.value.find { it.subId == subId }
        val slot = subInfo?.slotIndex ?: -1
        val carrier = subInfo?.carrierName ?: "Unknown"

        addLog("=== PROBE $buttonLabel: Minimal SmsManager ===")
        addLog("Probe $buttonLabel: dest='$destinationDigitsOnly' [hex: $hex], subId=$subId, slot=$slot, carrier=$carrier")
        updateResult(buttonLabel, ProbeResult(buttonLabel, state = "Sending...", sentCode = null, deliveryStatus = null))

        try {
            val sm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && subId != null && subId >= 0) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subId != null && subId >= 0) {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val smscAddress = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    sm.smscAddress ?: "Unavailable"
                } else "Unavailable (<API30)"
            } catch (e: Exception) {
                "Unavailable (${e.message})"
            }
            addLog("Probe $buttonLabel: SMSC Address: $smscAddress, method=sendTextMessage")

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val reqBase = if (buttonLabel == "B") 1000 else 2000
            val sentPi = PendingIntent.getBroadcast(
                context,
                reqBase + 1,
                Intent(context, SendProbeReceiver::class.java).apply {
                    action = SendProbeReceiver.ACTION_PROBE_SENT
                    putExtra(SendProbeReceiver.EXTRA_PROBE_BUTTON, buttonLabel)
                    putExtra(SendProbeReceiver.EXTRA_SUB_ID, subId ?: -1)
                    putExtra(SendProbeReceiver.EXTRA_DESTINATION, destinationDigitsOnly)
                },
                flags
            )

            val deliveredPi = PendingIntent.getBroadcast(
                context,
                reqBase + 2,
                Intent(context, SendProbeReceiver::class.java).apply {
                    action = SendProbeReceiver.ACTION_PROBE_DELIVERED
                    putExtra(SendProbeReceiver.EXTRA_PROBE_BUTTON, buttonLabel)
                    putExtra(SendProbeReceiver.EXTRA_SUB_ID, subId ?: -1)
                    putExtra(SendProbeReceiver.EXTRA_DESTINATION, destinationDigitsOnly)
                },
                flags
            )

            addLog("Probe $buttonLabel: Calling sm.sendTextMessage directly...")
            sm.sendTextMessage(destinationDigitsOnly, null, text, sentPi, deliveredPi)
            addLog("Probe $buttonLabel: sm.sendTextMessage invocation returned without exception")
        } catch (e: Exception) {
            addLog("Probe $buttonLabel Exception: ${e.message}")
            updateResult(buttonLabel, ProbeResult(buttonLabel, state = "Exception", exception = e.toString()))
        }
    }

    // --- BUTTON C: Same as B on Other SIM ---
    fun runProbeC(context: Context, destination: String, text: String, currentSubId: Int?) {
        val subs = _subscriptions.value
        val otherSub = subs.find { it.subId != currentSubId }
        if (otherSub == null) {
            addLog("Probe C: Phone does not have a second active SIM (found ${subs.size} active SIMs)")
            updateResult("C", ProbeResult("C", state = "Skipped", exception = "No 2nd active SIM"))
            return
        }
        runProbeB(context, destination, text, otherSub.subId, buttonLabel = "C")
    }

    // --- BUTTON D: System Messaging App ---
    fun runProbeD(context: Context, destination: String, text: String) {
        val hex = toHex(destination)
        addLog("=== PROBE D: System App ===")
        addLog("Probe D: Opening system app for dest='$destination' [hex: $hex]")

        try {
            val baseIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$destination")).apply {
                putExtra("sms_body", text)
            }
            val pm = context.packageManager
            val activities = pm.queryIntentActivities(baseIntent, 0)
            val externalIntents = activities
                .filter { it.activityInfo.packageName != context.packageName }
                .map { info ->
                    Intent(baseIntent).apply {
                        setClassName(info.activityInfo.packageName, info.activityInfo.name)
                        setPackage(info.activityInfo.packageName)
                    }
                }

            if (externalIntents.isNotEmpty()) {
                val chooser = Intent.createChooser(externalIntents.first(), "Send with external SMS app").apply {
                    if (externalIntents.size > 1) {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, externalIntents.drop(1).toTypedArray())
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                addLog("Probe D: Launched external app chooser with ${externalIntents.size} alternative app(s)")
                updateResult("D", ProbeResult("D", state = "App Opened", sentCode = "External App Chooser Shown"))
            } else {
                val fallback = Intent.createChooser(baseIntent, "Send with external SMS app").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                addLog("Probe D: Launched standard chooser")
                updateResult("D", ProbeResult("D", state = "App Opened", sentCode = "Chooser Shown"))
            }
        } catch (e: Exception) {
            addLog("Probe D Exception: ${e.message}")
            updateResult("D", ProbeResult("D", state = "Exception", exception = e.toString()))
        }
    }
}
