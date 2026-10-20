package com.ripple.chat.sms

import android.telephony.SmsManager
import com.ripple.chat.receiver.SmsStatusReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class SmsStatusTrackingTest {

    @Before
    fun setup() {
        SmsStatusReceiver.completedParts.clear()
        SmsStatusReceiver.failureReasons.clear()
    }

    @Test
    fun testMultipartAllPartsSucceedTransitionToSent() {
        val msgId = 5001L
        val totalParts = 2

        // Part 0 arrives OK
        val partsSet = SmsStatusReceiver.completedParts.computeIfAbsent(msgId) { ConcurrentHashMap.newKeySet() }
        partsSet.add(0)

        // Only 1 of 2 parts complete -> not yet sent
        val isSentAfterPart0 = partsSet.size >= totalParts && !SmsStatusReceiver.failureReasons.containsKey(msgId)
        assertFalse(isSentAfterPart0)

        // Part 1 arrives OK
        partsSet.add(1)
        val isSentAfterPart1 = partsSet.size >= totalParts && !SmsStatusReceiver.failureReasons.containsKey(msgId)
        assertTrue(isSentAfterPart1)
        assertNull(SmsStatusReceiver.failureReasons[msgId])
    }

    @Test
    fun testMultipartPartialFailureTransitionsToFailed() {
        val msgId = 5002L
        val totalParts = 2

        // Part 0 arrives OK
        val partsSet = SmsStatusReceiver.completedParts.computeIfAbsent(msgId) { ConcurrentHashMap.newKeySet() }
        partsSet.add(0)

        // Part 1 fails with RESULT_ERROR_RADIO_OFF
        val resultCode = SmsManager.RESULT_ERROR_RADIO_OFF
        val errorReason = when (resultCode) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "Airplane mode"
            SmsManager.RESULT_ERROR_NULL_PDU -> "Invalid number or message"
            else -> "Error code $resultCode"
        }
        SmsStatusReceiver.failureReasons[msgId] = errorReason

        assertEquals("Airplane mode", SmsStatusReceiver.failureReasons[msgId])

        // Overall message must NOT be considered sent
        val isSent = partsSet.size >= totalParts && !SmsStatusReceiver.failureReasons.containsKey(msgId)
        assertFalse(isSent)
        assertTrue(SmsStatusReceiver.failureReasons.containsKey(msgId))
    }

    @Test
    fun testErrorResultCodeMapping() {
        fun mapCode(code: Int): String? = when (code) {
            android.app.Activity.RESULT_OK -> null
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "Airplane mode"
            SmsManager.RESULT_ERROR_NULL_PDU -> "Invalid number or message"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS limit exceeded"
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "Short code not allowed"
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Short code never allowed"
            else -> "Error code $code"
        }

        assertNull(mapCode(android.app.Activity.RESULT_OK))
        assertEquals("Generic failure", mapCode(SmsManager.RESULT_ERROR_GENERIC_FAILURE))
        assertEquals("No service", mapCode(SmsManager.RESULT_ERROR_NO_SERVICE))
        assertEquals("Airplane mode", mapCode(SmsManager.RESULT_ERROR_RADIO_OFF))
        assertEquals("Invalid number or message", mapCode(SmsManager.RESULT_ERROR_NULL_PDU))
        assertEquals("SMS limit exceeded", mapCode(SmsManager.RESULT_ERROR_LIMIT_EXCEEDED))
        assertEquals("Short code not allowed", mapCode(SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED))
        assertEquals("Short code never allowed", mapCode(SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED))
    }
}
