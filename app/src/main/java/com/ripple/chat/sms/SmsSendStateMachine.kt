package com.ripple.chat.sms

object SmsSendStateMachine {

    enum class MessageState {
        SENDING,
        SENT,
        DELIVERED,
        FAILED
    }

    // Standard Android Telephony & Activity result codes
    const val RESULT_OK = -1
    const val RESULT_ERROR_GENERIC_FAILURE = 1
    const val RESULT_ERROR_RADIO_OFF = 2
    const val RESULT_ERROR_NULL_PDU = 3
    const val RESULT_ERROR_NO_SERVICE = 4
    const val RESULT_ERROR_LIMIT_EXCEEDED = 5
    const val RESULT_ERROR_SHORT_CODE_NOT_ALLOWED = 7
    const val RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED = 8

    // Delivery report status
    const val STATUS_COMPLETE = 0

    fun mapResultCodeToErrorReason(resultCode: Int): String? {
        return when (resultCode) {
            RESULT_OK -> null
            RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
            RESULT_ERROR_RADIO_OFF -> "Airplane mode"
            RESULT_ERROR_NULL_PDU -> "Invalid number or message"
            RESULT_ERROR_NO_SERVICE -> "No service"
            RESULT_ERROR_LIMIT_EXCEEDED -> "SMS limit exceeded"
            RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "Short code not allowed"
            RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Short code never allowed"
            else -> "Error code $resultCode"
        }
    }

    class Tracker(
        val totalParts: Int,
        var currentState: MessageState = MessageState.SENDING,
        val completedParts: MutableSet<Int> = mutableSetOf(),
        var failureReason: String? = null
    ) {
        fun onPartSent(partIndex: Int, resultCode: Int): MessageState {
            if (currentState == MessageState.FAILED) {
                return MessageState.FAILED
            }

            val error = mapResultCodeToErrorReason(resultCode)
            if (error != null) {
                failureReason = error
                currentState = MessageState.FAILED
                return MessageState.FAILED
            }

            completedParts.add(partIndex)
            if (completedParts.size >= totalParts && failureReason == null) {
                currentState = MessageState.SENT
                return MessageState.SENT
            }

            return currentState
        }

        fun onDeliveryReport(status: Int): MessageState {
            if (status == STATUS_COMPLETE && (currentState == MessageState.SENT || currentState == MessageState.SENDING)) {
                currentState = MessageState.DELIVERED
            }
            return currentState
        }
    }
}
