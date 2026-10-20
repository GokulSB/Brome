package com.ripple.chat.ui.chat

import android.app.Application
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ripple.chat.data.MessageDeliveryStatus
import com.ripple.chat.receiver.SmsStatusReceiver
import com.ripple.chat.sms.AddressNormalizer
import com.ripple.chat.sms.SendResult
import com.ripple.chat.sms.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val id: Long,
    val body: String,
    val date: Long,
    val isSentByMe: Boolean,
    val simSlot: Int? = null,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    val failureReason: String? = null,
    val isRead: Boolean = true
)

data class ContactInfo(
    val name: String,
    val number: String,
    val photoUri: String? = null
)

class ChatDetailViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val ACTION_SMS_SENT = "com.ripple.chat.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.ripple.chat.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
    }

    private val smsSender = SmsSender(application)

    private var currentThreadId: String? = null
    private var currentAddress: String? = null
    private var currentContactName: String? = null
    private var currentPhotoUri: String? = null

    private val contentObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            super.onChange(selfChange, uri)
            val tid = currentThreadId
            val addr = currentAddress
            if (!tid.isNullOrEmpty() || !addr.isNullOrEmpty()) {
                loadMessages(tid ?: "", addr, currentContactName, currentPhotoUri)
            }
        }
    }

    private val smsStatusReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val action = intent?.action ?: return
            val msgId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
                .takeIf { it != -1L }
                ?: intent.getLongExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, -1L)
            if (msgId == -1L) return

            when (action) {
                SmsStatusReceiver.ACTION_SMS_STATUS_CHANGED -> {
                    val statusStr = intent.getStringExtra(SmsStatusReceiver.EXTRA_DELIVERY_STATUS)
                    val errorReason = intent.getStringExtra(SmsStatusReceiver.EXTRA_ERROR_REASON)
                    val status = when (statusStr) {
                        "DELIVERED" -> MessageDeliveryStatus.DELIVERED
                        "SENT" -> MessageDeliveryStatus.SENT
                        "FAILED" -> MessageDeliveryStatus.FAILED
                        else -> MessageDeliveryStatus.PENDING
                    }
                    updateMessageStatus(msgId, status, errorReason)
                }
                ACTION_SMS_SENT, SmsStatusReceiver.ACTION_SMS_SENT -> {
                    val isSuccess = resultCode == android.app.Activity.RESULT_OK
                    val errorDesc = if (!isSuccess) {
                        when (resultCode) {
                            android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
                            android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
                            android.telephony.SmsManager.RESULT_ERROR_NULL_PDU -> "Invalid number or message"
                            android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF -> "Airplane mode"
                            else -> "Error code $resultCode"
                        }
                    } else null
                    android.util.Log.d("ChatDetailViewModel", "Sent callback result: $resultCode ($errorDesc) for msg: $msgId")
                    updateMessageStatus(msgId, if (isSuccess) MessageDeliveryStatus.SENT else MessageDeliveryStatus.FAILED, errorDesc)
                }
                ACTION_SMS_DELIVERED, SmsStatusReceiver.ACTION_SMS_DELIVERED -> {
                    updateMessageStatus(msgId, MessageDeliveryStatus.DELIVERED, null)
                }
            }
        }
    }

    private fun updateMessageStatus(msgId: Long, status: MessageDeliveryStatus, failureReason: String? = null) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == msgId) {
                msg.copy(
                    deliveryStatus = status,
                    failureReason = failureReason ?: if (status == MessageDeliveryStatus.FAILED) msg.failureReason ?: "Send failed" else null
                )
            } else {
                msg
            }
        }
    }

    init {
        val app = application
        app.contentResolver.registerContentObserver(
            android.net.Uri.parse("content://sms"),
            true,
            contentObserver
        )

        val statusFilter = android.content.IntentFilter().apply {
            addAction(ACTION_SMS_SENT)
            addAction(ACTION_SMS_DELIVERED)
            addAction(SmsStatusReceiver.ACTION_SMS_STATUS_CHANGED)
            addAction(SmsStatusReceiver.ACTION_SMS_SENT)
            addAction(SmsStatusReceiver.ACTION_SMS_DELIVERED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(smsStatusReceiver, statusFilter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            app.registerReceiver(smsStatusReceiver, statusFilter)
        }
        initSimSelection()
    }

    override fun onCleared() {
        super.onCleared()
        val app = getApplication<Application>()
        try {
            app.contentResolver.unregisterContentObserver(contentObserver)
        } catch (e: Exception) {}
        try {
            app.unregisterReceiver(smsStatusReceiver)
        } catch (e: Exception) {}
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _initialScrollTarget = MutableStateFlow<Int?>(null)
    val initialScrollTarget: StateFlow<Int?> = _initialScrollTarget.asStateFlow()

    private val _firstUnreadIndex = MutableStateFlow<Int?>(null)
    val firstUnreadIndex: StateFlow<Int?> = _firstUnreadIndex.asStateFlow()

    private var hasComputedInitialScroll = false

    fun resetScrollState() {
        hasComputedInitialScroll = false
        _initialScrollTarget.value = null
        _firstUnreadIndex.value = null
    }
    
    private val _contactInfo = MutableStateFlow<ContactInfo?>(null)
    val contactInfo: StateFlow<ContactInfo?> = _contactInfo.asStateFlow()

    private val _participants = MutableStateFlow<List<ContactInfo>>(emptyList())
    val participants: StateFlow<List<ContactInfo>> = _participants.asStateFlow()

    private val _selectedSim = MutableStateFlow(1)
    val selectedSim: StateFlow<Int> = _selectedSim.asStateFlow()

    fun setSelectedSim(sim: Int) {
        _selectedSim.value = sim
        val numericThreadId = currentThreadId?.toLongOrNull()
        if (numericThreadId != null && numericThreadId > 0) {
            val subId = getSubIdForSim(sim)
            com.ripple.chat.data.ThreadSimManager(getApplication()).saveSimForThread(numericThreadId, sim, subId)
        }
    }

    fun loadMessages(
        threadId: String, 
        fallbackAddress: String? = null,
        initialContactName: String? = null,
        initialPhotoUri: String? = null
    ) {
        if (fallbackAddress != null) currentAddress = fallbackAddress
        if (initialContactName != null) currentContactName = initialContactName
        if (initialPhotoUri != null) currentPhotoUri = initialPhotoUri

        val effectiveAddress = fallbackAddress ?: currentAddress
        val effectiveContactName = initialContactName ?: currentContactName
        val effectivePhotoUri = initialPhotoUri ?: currentPhotoUri

        if (threadId.isEmpty() && effectiveAddress.isNullOrBlank()) return
        val context = getApplication<Application>()
        val cleanedFallback = com.ripple.chat.utils.MessageParser.cleanAddress(effectiveAddress) ?: effectiveAddress

        // Immediately show initial contact info if passed from previous screen
        if (!effectiveContactName.isNullOrBlank()) {
            val initInfo = ContactInfo(
                name = effectiveContactName,
                number = cleanedFallback ?: "",
                photoUri = effectivePhotoUri
            )
            _contactInfo.value = initInfo
            _participants.value = listOf(initInfo)
        }

        var numericThreadId = threadId.toLongOrNull()
        if ((numericThreadId == null || numericThreadId <= 0) && !cleanedFallback.isNullOrBlank()) {
            try {
                val addrs = cleanedFallback.split(Regex("[,;]+")).map { it.trim() }.filter { it.isNotEmpty() }
                val normalizedAddrs = addrs.map { addr ->
                    if (!addr.any { it.isLetter() }) {
                        addr.replace(Regex("[^0-9+]"), "").ifBlank { addr }
                    } else addr
                }.toSet()
                val resolvedId = android.provider.Telephony.Threads.getOrCreateThreadId(context, normalizedAddrs)
                if (resolvedId > 0) {
                    numericThreadId = resolvedId
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val effectiveThreadId = numericThreadId?.toString() ?: threadId
        if (currentThreadId != effectiveThreadId) {
            currentThreadId = effectiveThreadId
            resetScrollState()
        }
        
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val result = mutableListOf<ChatMessage>()
                val uri = Telephony.Sms.CONTENT_URI
                val projection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.SUBSCRIPTION_ID,
                    Telephony.Sms.STATUS,
                    Telephony.Sms.READ
                )
                val selection: String
                val selectionArgs: Array<String>?
                if (numericThreadId != null && numericThreadId > 0) {
                    selection = "${Telephony.Sms.THREAD_ID} = ?"
                    selectionArgs = arrayOf(numericThreadId.toString())
                } else if (!cleanedFallback.isNullOrBlank()) {
                    val candidates = mutableListOf<String>()
                    candidates.add(cleanedFallback)
                    val digits = cleanedFallback.replace(Regex("[^0-9+]"), "")
                    if (digits.isNotBlank() && digits !in candidates) candidates.add(digits)
                    val digitsOnly = cleanedFallback.filter { it.isDigit() }
                    if (digitsOnly.isNotBlank() && digitsOnly !in candidates) candidates.add(digitsOnly)

                    val clauses = mutableListOf<String>()
                    val args = mutableListOf<String>()
                    for (cand in candidates) {
                        clauses.add("${Telephony.Sms.ADDRESS} = ?")
                        args.add(cand)
                    }
                    if (digitsOnly.length >= 7) {
                        val searchNum = if (digitsOnly.length >= 10) digitsOnly.takeLast(10) else digitsOnly
                        clauses.add("${Telephony.Sms.ADDRESS} LIKE ?")
                        args.add("%$searchNum")
                    }
                    selection = "(${clauses.joinToString(" OR ")})"
                    selectionArgs = args.toTypedArray()
                } else {
                    // NEVER query null selection (which dumps all SMS across phone into chat)
                    selection = "1 = 0"
                    selectionArgs = null
                }
                
                var fetchedAddress: String? = null
                
                // Map subId to simSlot (1 or 2)
                val subIdToSlotMap = mutableMapOf<Int, Int>()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, 
                        android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        val subscriptionManager = 
                            context.getSystemService(android.telephony.SubscriptionManager::class.java)
                        subscriptionManager?.activeSubscriptionInfoList?.forEach { info ->
                            subIdToSlotMap[info.subscriptionId] = info.simSlotIndex + 1
                        }
                    }
                }
                
                try {
                    context.contentResolver.query(
                        uri, projection, selection, selectionArgs, Telephony.Sms.DEFAULT_SORT_ORDER
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                        val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                        val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                        val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                        val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                        val subIdIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
                        val statusIdx = cursor.getColumnIndex(Telephony.Sms.STATUS)
                        val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)

                        val readStatusPrefs = context.getSharedPreferences("brome_read_status_prefs", android.content.Context.MODE_PRIVATE)

                        while (cursor.moveToNext()) {
                            val msgId = cursor.getLong(idIdx)
                            val body = cursor.getString(bodyIdx) ?: ""
                            val date = cursor.getLong(dateIdx)
                            val type = cursor.getInt(typeIdx)
                            
                            if (fetchedAddress == null) {
                                fetchedAddress = cursor.getString(addrIdx)
                            }
                            
                            val isSent = type != Telephony.Sms.MESSAGE_TYPE_INBOX
                            val rawRead = if (readIdx >= 0 && !cursor.isNull(readIdx)) cursor.getInt(readIdx) else 1
                            var isMsgRead = if (isSent) true else (rawRead != 0)
                            if (!isSent && numericThreadId != null && readStatusPrefs.contains("read_state_$numericThreadId")) {
                                val overrideRead = readStatusPrefs.getBoolean("read_state_$numericThreadId", isMsgRead)
                                val readTime = readStatusPrefs.getLong("read_time_$numericThreadId", 0L)
                                if (overrideRead) {
                                    if (readTime > 0L && date > readTime) {
                                        isMsgRead = false
                                    }
                                } else {
                                    isMsgRead = false
                                }
                            }

                            val subId = if (subIdIdx >= 0 && !cursor.isNull(subIdIdx)) cursor.getInt(subIdIdx) else -1
                            val simSlot = subIdToSlotMap[subId]
                            
                            val statusVal = if (statusIdx >= 0 && !cursor.isNull(statusIdx)) cursor.getInt(statusIdx) else -1
                            val deliveryStatus = when {
                                !isSent -> MessageDeliveryStatus.DELIVERED
                                statusVal == Telephony.Sms.STATUS_COMPLETE -> MessageDeliveryStatus.DELIVERED
                                statusVal == Telephony.Sms.STATUS_PENDING || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX -> MessageDeliveryStatus.PENDING
                                statusVal == Telephony.Sms.STATUS_FAILED || type == Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageDeliveryStatus.FAILED
                                else -> MessageDeliveryStatus.SENT
                            }
                            val failReason = if (deliveryStatus == MessageDeliveryStatus.FAILED) {
                                SmsStatusReceiver.failureReasons[msgId] ?: "Failed to send"
                            } else null
                            
                            result.add(ChatMessage(msgId, body, date, isSent, simSlot, deliveryStatus, failReason, isMsgRead))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (fetchedAddress == null) {
                    fetchedAddress = cleanedFallback
                } else {
                    fetchedAddress = com.ripple.chat.utils.MessageParser.cleanAddress(fetchedAddress) ?: fetchedAddress
                }
                if (fetchedAddress.isNullOrBlank() && numericThreadId != null && numericThreadId > 0) {
                    fetchedAddress = getAddressForThread(context, numericThreadId)
                }
                if (!fetchedAddress.isNullOrBlank()) {
                    currentAddress = fetchedAddress
                }
                
                // Fetch contact info
                if (fetchedAddress != null) {
                    val addrList = fetchedAddress!!.split(Regex("[,;]+")).map { com.ripple.chat.utils.MessageParser.cleanAddress(it.trim()) ?: it.trim() }.filter { it.isNotEmpty() }
                    val pList = mutableListOf<ContactInfo>()
                    
                    for (addr in addrList) {
                        var cName: String? = if (addrList.size == 1 && !initialContactName.isNullOrBlank()) initialContactName else null
                        var cPhoto: String? = if (addrList.size == 1 && !initialPhotoUri.isNullOrBlank()) initialPhotoUri else null
                        
                        if ((cName == null || cPhoto == null) && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            // 1. PhoneLookup with clean address
                            val lookupUri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(addr))
                            val proj = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME, android.provider.ContactsContract.PhoneLookup.PHOTO_URI)
                            try {
                                context.contentResolver.query(lookupUri, proj, null, null, null)?.use { cur ->
                                    if (cur.moveToFirst()) {
                                        if (cName == null) cName = cur.getString(0)
                                        if (cPhoto == null) cPhoto = cur.getString(1)
                                    }
                                }
                            } catch (e: Exception) { e.printStackTrace() }

                            // 2. PhoneLookup with normalized digits
                            if (cName == null) {
                                val digits = addr.replace(Regex("[^0-9+]"), "")
                                if (digits.isNotEmpty() && digits != addr) {
                                    val digitsUri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(digits))
                                    try {
                                        context.contentResolver.query(digitsUri, proj, null, null, null)?.use { cur ->
                                            if (cur.moveToFirst()) {
                                                cName = cur.getString(0)
                                                if (cPhoto == null) cPhoto = cur.getString(1)
                                            }
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }

                            // 3. CommonDataKinds.Phone fallback with last 10 digits
                            if (cName == null) {
                                val digitsOnly = addr.filter { it.isDigit() }
                                val searchNumber = if (digitsOnly.length >= 10) digitsOnly.takeLast(10) else digitsOnly
                                if (searchNumber.length >= 7) {
                                    try {
                                        val phoneUri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                        val phoneProj = arrayOf(
                                            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                            android.provider.ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                                        )
                                        context.contentResolver.query(
                                            phoneUri,
                                            phoneProj,
                                            "${android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                                            arrayOf("%$searchNumber%"),
                                            null
                                        )?.use { cur ->
                                            if (cur.moveToFirst()) {
                                                cName = cur.getString(0)
                                                if (cPhoto == null) cPhoto = cur.getString(1)
                                            }
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        }
                        pList.add(ContactInfo(name = cName ?: addr, number = addr, photoUri = cPhoto))
                    }
                    
                    if (pList.isNotEmpty()) {
                        _participants.value = pList
                        _contactInfo.value = pList.firstOrNull()
                    }
                }

                result.sortedBy { it.date }
            }
            val currentPending = _messages.value.filter { it.isSentByMe && it.deliveryStatus == MessageDeliveryStatus.PENDING }
            val existingIds = list.map { it.id }.toSet()
            val pendingPreserved = currentPending.filter { pending ->
                pending.id !in existingIds && list.none { it.isSentByMe && it.body == pending.body && Math.abs(it.date - pending.date) < 15000L }
            }
            val sortedList = (list + pendingPreserved).sortedBy { it.date }
            if (sortedList.isNotEmpty() && !hasComputedInitialScroll) {
                hasComputedInitialScroll = true
                val unreadIndex = sortedList.indexOfFirst { !it.isRead }.takeIf { it != -1 }
                _firstUnreadIndex.value = unreadIndex
                _initialScrollTarget.value = unreadIndex ?: (sortedList.size - 1).coerceAtLeast(0)
            }
            _messages.value = sortedList

            // Mark as read in DB and preferences AFTER recording initial unread positions
            if (numericThreadId != null && numericThreadId > 0) {
                withContext(Dispatchers.IO) {
                    try {
                        val readValues = android.content.ContentValues().apply {
                            put(Telephony.Sms.READ, 1)
                            put(Telephony.Sms.SEEN, 1)
                        }
                        context.contentResolver.update(
                            Telephony.Sms.CONTENT_URI,
                            readValues,
                            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                            arrayOf(numericThreadId.toString())
                        )
                        val convUri = android.net.Uri.parse("content://sms/conversations/$numericThreadId")
                        context.contentResolver.update(convUri, readValues, "${Telephony.Sms.READ} = 0", null)
                        com.ripple.chat.data.SmsRepository(context).markAsRead(numericThreadId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (!cleanedFallback.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val readValues = android.content.ContentValues().apply {
                            put(Telephony.Sms.READ, 1)
                            put(Telephony.Sms.SEEN, 1)
                        }
                        context.contentResolver.update(
                            Telephony.Sms.CONTENT_URI,
                            readValues,
                            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
                            arrayOf(cleanedFallback)
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun sendMessage(body: String, threadId: String, fallbackAddress: String? = null) {
        if (body.isBlank()) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            val addressCandidates = when {
                !fallbackAddress.isNullOrBlank() -> listOf(fallbackAddress)
                !currentAddress.isNullOrBlank() -> listOf(currentAddress!!)
                contactInfo.value?.number?.isNotBlank() == true -> listOf(contactInfo.value!!.number)
                _participants.value.isNotEmpty() -> _participants.value.map { it.number }.distinct()
                else -> emptyList()
            }
            var resolvedAddresses = addressCandidates.flatMap { it.split(Regex("[,;]+")) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            if (resolvedAddresses.isEmpty()) {
                val numTid = currentThreadId?.toLongOrNull() ?: threadId.toLongOrNull()
                if (numTid != null && numTid > 0) {
                    val addrFromThread = getAddressForThread(context, numTid)
                    if (!addrFromThread.isNullOrBlank()) {
                        resolvedAddresses = listOf(addrFromThread)
                        currentAddress = addrFromThread
                    }
                }
            }
            if (resolvedAddresses.isEmpty()) {
                android.util.Log.e("ChatDetailViewModel", "Cannot send message: no recipient address found for threadId=$threadId")
                return@launch
            }

            val simSlot = _selectedSim.value
            val subId = getSubIdForSim(simSlot)

            for (rawAddress in resolvedAddresses) {
                val clean = com.ripple.chat.utils.MessageParser.cleanAddress(rawAddress) ?: rawAddress.trim()
                if (!AddressNormalizer.isSendable(clean)) {
                    android.util.Log.w("ChatDetailViewModel", "Cannot send to non-sendable recipient: $clean")
                    continue
                }

                val tempMsgId = System.currentTimeMillis() + (0..999).random()
                val optimisticMsg = ChatMessage(
                    id = tempMsgId,
                    body = body,
                    date = System.currentTimeMillis(),
                    isSentByMe = true,
                    simSlot = simSlot,
                    deliveryStatus = MessageDeliveryStatus.PENDING
                )
                _messages.value = _messages.value + optimisticMsg

                val result = smsSender.send(
                    rawTo = clean,
                    body = body,
                    subId = subId
                )

                when (result) {
                    is SendResult.Queued -> {
                        if (result.threadId > 0) {
                            currentThreadId = result.threadId.toString()
                        }
                        _messages.value = _messages.value.map {
                            if (it.id == tempMsgId) it.copy(id = result.messageId) else it
                        }
                        val queuedId = result.messageId
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(60000L)
                            val cur = _messages.value.find { it.id == queuedId }
                            if (cur != null && cur.deliveryStatus == MessageDeliveryStatus.PENDING) {
                                android.util.Log.w("ChatDetailViewModel", "SMS send confirmation timed out after 60s for msg $queuedId")
                                updateMessageStatus(queuedId, MessageDeliveryStatus.FAILED, "No response from network (timed out)")
                            }
                        }
                    }
                    is SendResult.Failed -> {
                        _messages.value = _messages.value.map {
                            if (it.id == tempMsgId) it.copy(
                                id = result.messageId,
                                deliveryStatus = MessageDeliveryStatus.FAILED,
                                failureReason = result.reason
                            ) else it
                        }
                    }
                    is SendResult.NoPermission -> {
                        _messages.value = _messages.value.map {
                            if (it.id == tempMsgId) it.copy(
                                deliveryStatus = MessageDeliveryStatus.FAILED,
                                failureReason = "No SEND_SMS permission"
                            ) else it
                        }
                    }
                    is SendResult.NotDefaultApp -> {
                        _messages.value = _messages.value.map {
                            if (it.id == tempMsgId) it.copy(
                                deliveryStatus = MessageDeliveryStatus.FAILED,
                                failureReason = "Brome is not default SMS app"
                            ) else it
                        }
                    }
                    is SendResult.InvalidNumber -> {
                        _messages.value = _messages.value.map {
                            if (it.id == tempMsgId) it.copy(
                                deliveryStatus = MessageDeliveryStatus.FAILED,
                                failureReason = "Invalid phone number"
                            ) else it
                        }
                    }
                    is SendResult.EmptyBody -> {
                        _messages.value = _messages.value.filter { it.id != tempMsgId }
                    }
                }
            }
        }
    }

    fun retryMessage(message: ChatMessage) {
        val targetAddr = currentAddress ?: contactInfo.value?.number ?: return
        val simSlot = _selectedSim.value
        val subId = getSubIdForSim(simSlot)

        updateMessageStatus(message.id, MessageDeliveryStatus.PENDING, null)

        viewModelScope.launch {
            val result = smsSender.send(
                rawTo = targetAddr,
                body = message.body,
                subId = subId,
                retryMessageId = message.id
            )

            when (result) {
                is SendResult.Failed -> {
                    updateMessageStatus(message.id, MessageDeliveryStatus.FAILED, result.reason)
                }
                is SendResult.NoPermission -> {
                    updateMessageStatus(message.id, MessageDeliveryStatus.FAILED, "No SEND_SMS permission")
                }
                is SendResult.NotDefaultApp -> {
                    updateMessageStatus(message.id, MessageDeliveryStatus.FAILED, "Brome is not default SMS app")
                }
                is SendResult.InvalidNumber -> {
                    updateMessageStatus(message.id, MessageDeliveryStatus.FAILED, "Invalid phone number")
                }
                is SendResult.Queued -> {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(60000L)
                        val cur = _messages.value.find { it.id == message.id }
                        if (cur != null && cur.deliveryStatus == MessageDeliveryStatus.PENDING) {
                            updateMessageStatus(message.id, MessageDeliveryStatus.FAILED, "No response from network (timed out)")
                        }
                    }
                }
                else -> {}
            }
        }
    }

    private fun getAddressForThread(context: android.content.Context, threadId: Long): String? {
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                Telephony.Sms.DEFAULT_SORT_ORDER
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val addr = cursor.getString(0)
                    if (!addr.isNullOrBlank()) return addr
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun initSimSelection() {
        val context = getApplication<Application>()
        val numericThreadId = currentThreadId?.toLongOrNull()
        val threadSimManager = com.ripple.chat.data.ThreadSimManager(context)

        // 1. Check if user already chose a SIM for this thread
        if (numericThreadId != null && numericThreadId > 0) {
            val savedSlot = threadSimManager.getSlotForThread(numericThreadId)
            if (savedSlot != null && savedSlot > 0) {
                _selectedSim.value = savedSlot
                return
            }
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val subscriptionManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    context.getSystemService(android.telephony.SubscriptionManager::class.java)
                } else null

                val defaultSmsSubId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId()
                } else -1

                val activeList = subscriptionManager?.activeSubscriptionInfoList
                if (!activeList.isNullOrEmpty()) {
                    val defaultInfo = if (defaultSmsSubId >= 0 && defaultSmsSubId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        activeList.find { it.subscriptionId == defaultSmsSubId }
                    } else null

                    if (defaultInfo != null) {
                        _selectedSim.value = defaultInfo.simSlotIndex + 1
                    } else {
                        val firstActive = activeList.firstOrNull()
                        if (firstActive != null) {
                            _selectedSim.value = firstActive.simSlotIndex + 1
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getAvailableSims(): List<Pair<Int, String>> {
        val context = getApplication<Application>()
        val result = mutableListOf<Pair<Int, String>>()
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val subscriptionManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    context.getSystemService(android.telephony.SubscriptionManager::class.java)
                } else null
                subscriptionManager?.activeSubscriptionInfoList?.forEach { info ->
                    val slot = info.simSlotIndex + 1
                    val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    val display = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                    val name = carrier ?: display ?: "SIM $slot"
                    result.add(slot to name)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (result.isEmpty()) {
            result.add(1 to "SIM 1")
        }
        return result.distinctBy { it.first }
    }
    
    private fun getSubIdForSim(simSlot: Int): Int {
        val context = getApplication<Application>()
        var activeList: List<android.telephony.SubscriptionInfo>? = null
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val subscriptionManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    context.getSystemService(android.telephony.SubscriptionManager::class.java)
                } else null
                activeList = subscriptionManager?.activeSubscriptionInfoList
                if (!activeList.isNullOrEmpty()) {
                    val targetSlotIndex = simSlot - 1
                    val match = activeList.find { it.simSlotIndex == targetSlotIndex }
                        ?: activeList.find { it.simSlotIndex + 1 == simSlot }
                        ?: activeList.firstOrNull { it.subscriptionId >= 0 && it.subscriptionId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                    if (match != null && match.subscriptionId >= 0 && match.subscriptionId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        return match.subscriptionId
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val defaultSmsId = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId()
            } else -1
        } catch (_: Exception) { -1 }

        if (defaultSmsId >= 0 && defaultSmsId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return defaultSmsId
        }

        val defaultSubId = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.telephony.SubscriptionManager.getDefaultSubscriptionId()
            } else -1
        } catch (_: Exception) { -1 }

        if (defaultSubId >= 0 && defaultSubId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return defaultSubId
        }

        val firstAvailable = activeList?.firstOrNull { it.subscriptionId >= 0 && it.subscriptionId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID }?.subscriptionId
        if (firstAvailable != null && firstAvailable >= 0) {
            return firstAvailable
        }

        return -1
    }
}
