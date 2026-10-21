package com.ripple.chat.utils

import org.junit.Assert.*
import org.junit.Test

class LinkSafetyCheckerTest {

    @Test
    fun testExtractLinks() {
        val text = "Please visit https://github.com/GokulSB/Brome for updates! Also check www.google.com and bit.ly/test."
        val links = LinkSafetyChecker.extractLinks(text)

        assertEquals(3, links.size)
        assertEquals("https://github.com/GokulSB/Brome", links[0].url)
        assertEquals("www.google.com", links[1].url)
        assertEquals("bit.ly/test", links[2].url)
    }

    @Test
    fun testSafeUrl() {
        val analysis = LinkSafetyChecker.analyze("https://github.com/GokulSB/Brome")
        assertEquals(ScamRiskLevel.LOW_RISK, analysis.riskLevel)
        assertEquals("github.com", analysis.host)
        assertTrue(analysis.warnings.isEmpty())
    }

    @Test
    fun testRawIpAddress() {
        val analysis = LinkSafetyChecker.analyze("http://192.168.1.100/admin")
        assertEquals(ScamRiskLevel.HIGH_RISK, analysis.riskLevel)
        assertTrue(analysis.isIpAddress)
        assertTrue(analysis.warnings.any { it.contains("numeric IP address") })
    }

    @Test
    fun testBrandImpersonation() {
        val analysis = LinkSafetyChecker.analyze("https://paypal-verify-account.xyz/login")
        assertEquals(ScamRiskLevel.HIGH_RISK, analysis.riskLevel)
        assertTrue(analysis.isImpersonation)
        assertTrue(analysis.warnings.any { it.contains("PayPal") })
    }

    @Test
    fun testLegitimateBrandDomain() {
        val analysis = LinkSafetyChecker.analyze("https://www.paypal.com/signin")
        assertEquals(ScamRiskLevel.LOW_RISK, analysis.riskLevel)
        assertFalse(analysis.isImpersonation)
    }

    @Test
    fun testUrlShortener() {
        val analysis = LinkSafetyChecker.analyze("https://bit.ly/3AbCd1")
        assertEquals(ScamRiskLevel.SUSPICIOUS, analysis.riskLevel)
        assertTrue(analysis.isShortener)
        assertTrue(analysis.warnings.any { it.contains("shortener") })
    }

    @Test
    fun testDangerousApkDownload() {
        val analysis = LinkSafetyChecker.analyze("https://update-service.net/app.apk")
        assertEquals(ScamRiskLevel.HIGH_RISK, analysis.riskLevel)
        assertTrue(analysis.isExecutable)
        assertTrue(analysis.warnings.any { it.contains(".apk") })
    }

    @Test
    fun testPunycodeSpoofing() {
        val analysis = LinkSafetyChecker.analyze("https://xn--pple-43d.com")
        assertEquals(ScamRiskLevel.HIGH_RISK, analysis.riskLevel)
        assertTrue(analysis.warnings.any { it.contains("punycode") })
    }

    @Test
    fun testNormalizeUrl() {
        assertEquals("https://www.google.com", LinkSafetyChecker.normalizeUrl("www.google.com"))
        assertEquals("https://example.com", LinkSafetyChecker.normalizeUrl("example.com"))
        assertEquals("http://insecure.site", LinkSafetyChecker.normalizeUrl("http://insecure.site"))
        assertEquals("https://secure.site", LinkSafetyChecker.normalizeUrl("https://secure.site"))
    }
}
