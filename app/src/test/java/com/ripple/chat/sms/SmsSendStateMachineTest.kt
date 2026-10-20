package com.ripple.chat.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSendStateMachineTest {

    @Test
    fun testDestinationDigitsWithOptionalPlus() {
        val raw1 = "+91 98765-43210"
        val normalized1 = AddressNormalizer.normalizeWithRegion(raw1, "IN") ?: ""
        val clean1 = if (normalized1.startsWith("+")) {
            "+" + normalized1.drop(1).filter { it.isDigit() }
        } else {
            normalized1.filter { it.isDigit() }
        }
        assertEquals("+919876543210", clean1)

        val raw2 = "(555) 234-5678"
        val normalized2 = AddressNormalizer.normalizeWithRegion(raw2, "US") ?: ""
        val clean2 = if (normalized2.startsWith("+")) {
            "+" + normalized2.drop(1).filter { it.isDigit() }
        } else {
            normalized2.filter { it.isDigit() }
        }
        assertEquals("+15552345678", clean2)
    }

    @Test
    fun testMultipartThresholdDetection() {
        // GSM 7-bit character detection: ascii letters, numbers, common punctuation
        fun isGsm7Bit(s: String): Boolean {
            return s.all { it.code in 0x20..0x7E || it == '\n' || it == '\r' }
        }

        fun calculateParts(text: String): Int {
            val is7Bit = isGsm7Bit(text)
            val singleLimit = if (is7Bit) 160 else 70
            val multiLimit = if (is7Bit) 153 else 67

            return when {
                text.length <= singleLimit -> 1
                else -> (text.length + multiLimit - 1) / multiLimit
            }
        }

        // Short standard text
        assertEquals(1, calculateParts("Hello world!"))
        // Exactly 160 chars ASCII
        assertEquals(1, calculateParts("A".repeat(160)))
        // 161 chars ASCII -> 2 parts
        assertEquals(2, calculateParts("A".repeat(161)))

        // Short message with Emoji -> UCS-2 encoding (limit 70)
        assertEquals(1, calculateParts("Hello! 😀"))
        // 71 chars with emoji -> 2 parts
        assertEquals(2, calculateParts("😀" + "A".repeat(70)))
    }

    @Test
    fun testSinglePartTransitionSendingToSentToDelivered() {
        val tracker = SmsSendStateMachine.Tracker(totalParts = 1)
        assertEquals(SmsSendStateMachine.MessageState.SENDING, tracker.currentState)
        assertNull(tracker.failureReason)

        // Radio acknowledges transmission
        val sentState = tracker.onPartSent(0, SmsSendStateMachine.RESULT_OK)
        assertEquals(SmsSendStateMachine.MessageState.SENT, sentState)
        assertEquals(SmsSendStateMachine.MessageState.SENT, tracker.currentState)
        assertNull(tracker.failureReason)

        // Carrier network delivers message to recipient handset
        val deliveredState = tracker.onDeliveryReport(SmsSendStateMachine.STATUS_COMPLETE)
        assertEquals(SmsSendStateMachine.MessageState.DELIVERED, deliveredState)
        assertEquals(SmsSendStateMachine.MessageState.DELIVERED, tracker.currentState)
    }

    @Test
    fun testSendingToFailedOnEachErrorCode() {
        val errorMappings = mapOf(
            SmsSendStateMachine.RESULT_ERROR_GENERIC_FAILURE to "Generic failure",
            SmsSendStateMachine.RESULT_ERROR_RADIO_OFF to "Airplane mode",
            SmsSendStateMachine.RESULT_ERROR_NULL_PDU to "Invalid number or message",
            SmsSendStateMachine.RESULT_ERROR_NO_SERVICE to "No service",
            SmsSendStateMachine.RESULT_ERROR_LIMIT_EXCEEDED to "SMS limit exceeded",
            SmsSendStateMachine.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED to "Short code not allowed",
            SmsSendStateMachine.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED to "Short code never allowed",
            99 to "Error code 99"
        )

        for ((code, expectedReason) in errorMappings) {
            val tracker = SmsSendStateMachine.Tracker(totalParts = 1)
            assertEquals(SmsSendStateMachine.MessageState.SENDING, tracker.currentState)

            val state = tracker.onPartSent(0, code)
            assertEquals(SmsSendStateMachine.MessageState.FAILED, state)
            assertEquals(SmsSendStateMachine.MessageState.FAILED, tracker.currentState)
            assertEquals(expectedReason, tracker.failureReason)
        }
    }

    @Test
    fun testMultipartAggregationPart1OkPart2FailedEqualsWholeFailed() {
        val tracker = SmsSendStateMachine.Tracker(totalParts = 2)
        assertEquals(SmsSendStateMachine.MessageState.SENDING, tracker.currentState)

        // Part 0 arrives OK
        val state1 = tracker.onPartSent(0, SmsSendStateMachine.RESULT_OK)
        assertEquals(SmsSendStateMachine.MessageState.SENDING, state1)
        assertFalse(tracker.currentState == SmsSendStateMachine.MessageState.SENT)

        // Part 1 fails with AIRPLANE MODE / RADIO_OFF
        val state2 = tracker.onPartSent(1, SmsSendStateMachine.RESULT_ERROR_RADIO_OFF)
        assertEquals(SmsSendStateMachine.MessageState.FAILED, state2)
        assertEquals(SmsSendStateMachine.MessageState.FAILED, tracker.currentState)
        assertEquals("Airplane mode", tracker.failureReason)

        // Even if part 0 receives another callback or delivery report, whole message remains FAILED
        tracker.onDeliveryReport(SmsSendStateMachine.STATUS_COMPLETE)
        assertEquals(SmsSendStateMachine.MessageState.FAILED, tracker.currentState)
    }

    @Test
    fun testMultipartAggregationAllPartsOkTransitionsToSent() {
        val tracker = SmsSendStateMachine.Tracker(totalParts = 3)

        // Part 0 arrives OK -> still SENDING
        assertEquals(SmsSendStateMachine.MessageState.SENDING, tracker.onPartSent(0, SmsSendStateMachine.RESULT_OK))
        assertEquals(1, tracker.completedParts.size)

        // Part 1 arrives OK -> still SENDING
        assertEquals(SmsSendStateMachine.MessageState.SENDING, tracker.onPartSent(1, SmsSendStateMachine.RESULT_OK))
        assertEquals(2, tracker.completedParts.size)

        // Part 2 arrives OK -> transitions to SENT
        assertEquals(SmsSendStateMachine.MessageState.SENT, tracker.onPartSent(2, SmsSendStateMachine.RESULT_OK))
        assertEquals(SmsSendStateMachine.MessageState.SENT, tracker.currentState)
        assertNull(tracker.failureReason)

        // Network confirms delivery -> transitions to DELIVERED
        assertEquals(SmsSendStateMachine.MessageState.DELIVERED, tracker.onDeliveryReport(SmsSendStateMachine.STATUS_COMPLETE))
        assertEquals(SmsSendStateMachine.MessageState.DELIVERED, tracker.currentState)
    }
}
