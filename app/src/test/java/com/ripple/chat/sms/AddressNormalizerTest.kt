package com.ripple.chat.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressNormalizerTest {

    @Test
    fun testNationalFormatIndia() {
        val result = AddressNormalizer.normalizeWithRegion("9876543210", "IN")
        assertEquals("+919876543210", result)
    }

    @Test
    fun testInternationalWithSpacesAndDashes() {
        val result = AddressNormalizer.normalizeWithRegion("+91 98765 43210", "IN")
        assertEquals("+919876543210", result)

        val resultDashes = AddressNormalizer.normalizeWithRegion("+91-98765-43210", "IN")
        assertEquals("+919876543210", resultDashes)
    }

    @Test
    fun testAlphanumericSenderIdsRejected() {
        assertFalse(AddressNormalizer.isSendable("VK-KOTAKB"))
        assertFalse(AddressNormalizer.isSendable("GOOGLE"))
        assertFalse(AddressNormalizer.isSendable("JM-HDFCBK-S"))
        assertTrue(AddressNormalizer.isAlphanumericSender("VK-KOTAKB"))
        assertTrue(AddressNormalizer.isAlphanumericSender("GOOGLE"))

        assertNull(AddressNormalizer.normalizeWithRegion("VK-KOTAKB", "IN"))
        assertNull(AddressNormalizer.normalizeWithRegion("GOOGLE", "IN"))
        assertNull(AddressNormalizer.normalizeWithRegion("JM-HDFCBK-S", "IN"))
    }

    @Test
    fun testLeadingTrunkZeroStripped() {
        val result = AddressNormalizer.normalizeWithRegion("09876543210", "IN")
        assertEquals("+919876543210", result)
    }

    @Test
    fun testPunctuationHeavyNumber() {
        val result = AddressNormalizer.normalizeWithRegion("(987) 654-3210", "US")
        assertEquals("+19876543210", result)
    }

    @Test
    fun testCanonicalKeyDeduplication() {
        val k1 = AddressNormalizer.canonicalKey("+919876543210")
        val k2 = AddressNormalizer.canonicalKey("09876543210")
        val k3 = AddressNormalizer.canonicalKey("9876543210")
        val k4 = AddressNormalizer.canonicalKey("+91 98765-43210")

        assertEquals("9876543210", k1)
        assertEquals("9876543210", k2)
        assertEquals("9876543210", k3)
        assertEquals("9876543210", k4)
        assertEquals(k1, k2)
        assertEquals(k2, k3)
        assertEquals(k3, k4)
    }

    @Test
    fun testCanonicalKeyForAlphanumericSender() {
        val key = AddressNormalizer.canonicalKey("vk-kotakb")
        assertEquals("VK-KOTAKB", key)
    }

    @Test
    fun testCleanAndSanitizeStripsInvisibleMarks() {
        // \u200E = LTR mark, \u200F = RTL mark, \u00A0 = non-breaking space, \u200B = zero-width space, \uFEFF = BOM
        val dirty = "\u200E+\u200F91\u00A098765\u200B43210\uFEFF"
        val cleaned = AddressNormalizer.cleanAndSanitize(dirty)
        assertEquals("+91 9876543210", cleaned)
    }

    @Test
    fun testNormalizeWithInvisibleUnicodePreservesLeadingPlus() {
        // Bug 2 root cause: leading \u200E before + made startsWith("+") false, stripping the +
        val dirtyInternational = "\u200E+91 98765 43210"
        val normalized = AddressNormalizer.normalizeWithRegion(dirtyInternational, "IN")
        assertEquals("+919876543210", normalized)

        val dirtyUS = "\u200E+1 (555) 234-5678"
        val normalizedUS = AddressNormalizer.normalizeWithRegion(dirtyUS, "US")
        assertEquals("+15552345678", normalizedUS)
    }

    @Test
    fun testCanonicalKeyWithInvisibleUnicodeMatchesClean() {
        val dirty = "\u200E+91\u00A098765\u200F43210"
        val clean = "+919876543210"
        assertEquals("9876543210", AddressNormalizer.canonicalKey(dirty))
        assertEquals(AddressNormalizer.canonicalKey(clean), AddressNormalizer.canonicalKey(dirty))
    }
}
