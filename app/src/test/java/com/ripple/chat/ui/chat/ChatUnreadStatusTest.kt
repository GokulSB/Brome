package com.ripple.chat.ui.chat

import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.SmsCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChatUnreadStatusTest {

    @Test
    fun testChatThreadReadAndUnreadCounts() {
        val thread1 = ChatThread(
            threadId = 1L,
            address = "Sender1",
            body = "Hello",
            date = 1000L,
            read = false,
            type = 1,
            category = SmsCategory.PERSONAL,
            unreadCount = 3
        )

        assertFalse(thread1.read)
        assertEquals(3, thread1.unreadCount)

        // Mark as read
        val readThread = thread1.copy(read = true, unreadCount = 0)
        assertTrue(readThread.read)
        assertEquals(0, readThread.unreadCount)

        // Mark as unread
        val unreadThread = readThread.copy(read = false, unreadCount = 1)
        assertFalse(unreadThread.read)
        assertEquals(1, unreadThread.unreadCount)
    }

    @Test
    fun testCategoryUnreadCountCalculation() {
        val threads = listOf(
            ChatThread(1L, "Bank", "OTP 1234", 1000L, read = false, type = 1, category = SmsCategory.OTP, unreadCount = 2),
            ChatThread(2L, "Bank", "OTP 5678", 2000L, read = true, type = 1, category = SmsCategory.OTP, unreadCount = 0),
            ChatThread(3L, "HDFC", "Debited INR 500", 3000L, read = false, type = 1, category = SmsCategory.FINANCE, unreadCount = 1),
            ChatThread(4L, "Spam", "Win lottery", 4000L, read = false, type = 1, category = SmsCategory.SPAM, unreadCount = 1)
        )

        fun countUnread(list: List<ChatThread>): Int {
            return list.filter { !it.read }.sumOf { if (it.unreadCount > 0) it.unreadCount else 1 }
        }

        // UNREAD card count (excluding spam)
        val unreadCount = countUnread(threads.filter { it.category != SmsCategory.SPAM })
        assertEquals(3, unreadCount) // 2 from OTP thread1 + 1 from Finance thread3

        // OTP card count
        val otpCount = countUnread(threads.filter { it.category == SmsCategory.OTP })
        assertEquals(2, otpCount)

        // FINANCE card count
        val financeCount = countUnread(threads.filter { it.category == SmsCategory.FINANCE })
        assertEquals(1, financeCount)

        // SPAM card count
        val spamCount = countUnread(threads.filter { it.category == SmsCategory.SPAM })
        assertEquals(1, spamCount)
    }

    @Test
    fun testDynamicIslandUsesSystemFont() {
        // Verify no occurrences of DefaultBricolageFontFamily in island package
        val islandDir = File("src/main/java/com/ripple/chat/island")
        val ktFiles = islandDir.walk().filter { it.extension == "kt" }.toList()
        assertTrue("Island package files should exist", ktFiles.isNotEmpty())

        for (file in ktFiles) {
            val content = file.readText()
            assertFalse(
                "File ${file.name} should not reference DefaultBricolageFontFamily, must use system font",
                content.contains("DefaultBricolageFontFamily")
            )
        }
    }
}
