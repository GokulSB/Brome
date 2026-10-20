package com.ripple.chat.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageParserTest {

    @Test
    fun testCleanAddressPreservesLeadingPlus() {
        val result = MessageParser.cleanAddress("+919876543210")
        assertEquals("+919876543210", result)
    }

    @Test
    fun testCleanAddressWithSpacesAndDashes() {
        val result = MessageParser.cleanAddress("+91 98765-43210")
        assertEquals("+919876543210", result)
    }

    @Test
    fun testCleanAddressWithTwelveDigitIndianNumberMissingPlus() {
        val result = MessageParser.cleanAddress("919876543210")
        assertEquals("+919876543210", result)
    }

    @Test
    fun testCleanAddressWithLeadingSpaceFromNavigationDecoding() {
        val result = MessageParser.cleanAddress(" 919876543210")
        assertEquals("+919876543210", result)
    }

    @Test
    fun testCleanAddressTenDigitDomestic() {
        val result = MessageParser.cleanAddress("9876543210")
        assertEquals("9876543210", result)
    }

    @Test
    fun testCleanAddressElevenDigitWithTrunkZero() {
        val result = MessageParser.cleanAddress("09876543210")
        assertEquals("9876543210", result)
    }

    @Test
    fun testCleanAddressWithAlphanumericSender() {
        val result = MessageParser.cleanAddress("VM-HDFCBK")
        assertEquals("VM-HDFCBK", result)
    }

    @Test
    fun testCleanAddressNullOrBlank() {
        assertNull(MessageParser.cleanAddress(null))
        assertNull(MessageParser.cleanAddress(""))
        assertNull(MessageParser.cleanAddress("   "))
    }

    @Test
    fun testCleanAddressMultipleAddresses() {
        val result = MessageParser.cleanAddress("+91 98765 43210; 919876543211")
        assertEquals("+919876543210,+919876543211", result)
    }
}
