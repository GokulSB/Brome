package com.ripple.chat.island

import com.ripple.chat.data.SmsCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IslandQueueManagerTest {

    @Before
    fun setup() {
        IslandQueueManager.clear()
    }

    @Test
    fun testQueue_singleMessage_enqueuesProperly() {
        val msg = IslandMessage(
            id = 100L,
            address = "+123456789",
            senderName = "Alice",
            body = "Hello",
            category = SmsCategory.PERSONAL
        )
        IslandQueueManager.enqueue(msg)

        assertEquals(1, IslandQueueManager.messages.value.size)
        assertEquals(0, IslandQueueManager.currentIndex.value)
        assertEquals(msg, IslandQueueManager.currentMessage)
    }

    @Test
    fun testQueue_multipleMessages_orderAndLatestFocus() {
        val msg1 = IslandMessage(id = 1L, address = "111", senderName = "Alice", body = "Hi", category = SmsCategory.PERSONAL)
        val msg2 = IslandMessage(id = 2L, address = "222", senderName = "Bob", body = "OTP 123", category = SmsCategory.OTP)
        val msg3 = IslandMessage(id = 3L, address = "333", senderName = "Bank", body = "Rs 500 debited", category = SmsCategory.FINANCE)

        IslandQueueManager.enqueue(msg1)
        assertEquals(1, IslandQueueManager.messages.value.size)
        assertEquals(msg1, IslandQueueManager.currentMessage)

        IslandQueueManager.enqueue(msg2)
        assertEquals(2, IslandQueueManager.messages.value.size)
        // Switches to newest arrived
        assertEquals(1, IslandQueueManager.currentIndex.value)
        assertEquals(msg2, IslandQueueManager.currentMessage)

        IslandQueueManager.enqueue(msg3)
        assertEquals(3, IslandQueueManager.messages.value.size)
        assertEquals(2, IslandQueueManager.currentIndex.value)
        assertEquals(msg3, IslandQueueManager.currentMessage)
    }

    @Test
    fun testQueue_pagingThroughQueue() {
        val msg1 = IslandMessage(id = 1L, address = "111", senderName = "Alice", body = "Hi", category = SmsCategory.PERSONAL)
        val msg2 = IslandMessage(id = 2L, address = "222", senderName = "Bob", body = "Bye", category = SmsCategory.PERSONAL)

        IslandQueueManager.enqueue(msg1)
        IslandQueueManager.enqueue(msg2)

        assertEquals(1, IslandQueueManager.currentIndex.value) // on msg2
        IslandQueueManager.pagePrevious()
        assertEquals(0, IslandQueueManager.currentIndex.value) // on msg1
        assertEquals(msg1, IslandQueueManager.currentMessage)

        IslandQueueManager.pageNext()
        assertEquals(1, IslandQueueManager.currentIndex.value) // back to msg2
        assertEquals(msg2, IslandQueueManager.currentMessage)
    }

    @Test
    fun testQueue_dismissCurrent_promotesNext() {
        val msg1 = IslandMessage(id = 1L, address = "111", senderName = "Alice", body = "Msg 1", category = SmsCategory.PERSONAL)
        val msg2 = IslandMessage(id = 2L, address = "222", senderName = "Bob", body = "Msg 2", category = SmsCategory.PERSONAL)

        IslandQueueManager.enqueue(msg1)
        IslandQueueManager.enqueue(msg2)

        var emptyCallbackCalled = false

        // Dismiss current (msg2)
        IslandQueueManager.dismissCurrent {
            emptyCallbackCalled = true
        }

        assertEquals(false, emptyCallbackCalled)
        assertEquals(1, IslandQueueManager.messages.value.size)
        assertEquals(msg1, IslandQueueManager.currentMessage)

        // Dismiss remaining (msg1)
        IslandQueueManager.dismissCurrent {
            emptyCallbackCalled = true
        }

        assertEquals(true, emptyCallbackCalled)
        assertEquals(0, IslandQueueManager.messages.value.size)
        assertNull(IslandQueueManager.currentMessage)
    }
}
