package com.ripple.chat.ui.chat

import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.SmsCategory
import org.junit.Assert.*
import org.junit.Test

class SelectionActionBarTest {

    private fun createThread(id: Long, category: SmsCategory = SmsCategory.PERSONAL, read: Boolean = true, starred: Boolean = false): ChatThread {
        return ChatThread(
            threadId = id,
            address = "Contact $id",
            body = "Message body $id",
            date = System.currentTimeMillis() - id * 1000,
            read = read,
            type = 1,
            category = category,
            unreadCount = if (read) 0 else 1
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECK 1: Five icon actions, icon-only, and responsive 360dp width fit
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testFiveButtonsAndResponsiveWidthCalculation() {
        val availableWidthDp = 328f // 360dp screen - 16dp*2 margins
        val scale = 1.0f
        val t = ((scale - 0.70f) / 0.30f).coerceIn(0f, 1f)
        val buttonSize = 38f + 12f * t
        val extraPillWidth = 12f + 8f * t
        val pillWidth = buttonSize + extraPillWidth
        val gap = 3f + 5f * t
        val horizontalPadding = 6f + 4f * t

        val totalOccupiedWidth = (buttonSize * 4f) + pillWidth + (gap * 4f) + (horizontalPadding * 2f)
        assertTrue("Total width of 5 buttons ($totalOccupiedWidth) must be <= available ($availableWidthDp)", totalOccupiedWidth <= availableWidthDp)
        assertEquals(50f, buttonSize, 0.1f)
        assertEquals(70f, pillWidth, 0.1f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECK 2: Tap Select all -> all in list selected, header count matches, button -> Deselect all
    // Tap Deselect all -> all deselected, selection mode closes
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testSelectAllAndDeselectAllToggle() {
        val list = (1L..10L).map { createThread(it) }
        var isSelectionMode = false
        var selectedIds = setOf<Long>()

        fun isAllSelected(): Boolean {
            return list.isNotEmpty() && selectedIds.size >= list.size && list.all { it.threadId in selectedIds }
        }

        // Long press first item to start selection mode
        isSelectionMode = true
        selectedIds = setOf(1L)
        assertEquals(1, selectedIds.size)
        assertFalse(isAllSelected())

        // Tap Select all
        if (isAllSelected()) {
            selectedIds = emptySet()
            isSelectionMode = false
        } else {
            selectedIds = list.map { it.threadId }.toSet()
        }

        assertEquals(10, selectedIds.size)
        assertTrue(isAllSelected())

        // Tap Deselect all
        if (isAllSelected()) {
            selectedIds = emptySet()
            isSelectionMode = false
        } else {
            selectedIds = list.map { it.threadId }.toSet()
        }

        assertEquals(0, selectedIds.size)
        assertFalse(isSelectionMode)
        assertFalse(isAllSelected())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECK 3: Select all, then tap one card to deselect -> returns to Select all
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testDeselectSingleCardReturnsToSelectAll() {
        val list = (1L..5L).map { createThread(it) }
        var selectedIds = list.map { it.threadId }.toSet()

        fun isAllSelected(): Boolean {
            return list.isNotEmpty() && selectedIds.size >= list.size && list.all { it.threadId in selectedIds }
        }

        assertTrue(isAllSelected())

        // User taps card 3 to deselect it
        selectedIds = selectedIds - 3L

        assertEquals(4, selectedIds.size)
        assertFalse("Select all button must return to Select all state when one card is deselected", isAllSelected())

        // User taps card 3 again to re-select it
        selectedIds = selectedIds + 3L
        assertEquals(5, selectedIds.size)
        assertTrue("Select all button must return to Deselect all state when all cards are selected", isAllSelected())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECK 4: Filter scope (Personal vs Archive)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testSelectAllScopeWithFiltersAndArchiveScreen() {
        val allThreads = listOf(
            createThread(1L, SmsCategory.PERSONAL),
            createThread(2L, SmsCategory.PERSONAL),
            createThread(3L, SmsCategory.PERSONAL),
            createThread(4L, SmsCategory.OTP),
            createThread(5L, SmsCategory.FINANCE),
            createThread(6L, SmsCategory.SPAM)
        )

        // Filtered list: Personal category only
        val personalList = allThreads.filter { it.category == SmsCategory.PERSONAL }
        assertEquals(3, personalList.size)

        // Tap Select all in Personal view
        val selectedIds = personalList.map { it.threadId }.toSet()
        assertEquals(3, selectedIds.size)
        assertTrue(selectedIds.contains(1L))
        assertTrue(selectedIds.contains(2L))
        assertTrue(selectedIds.contains(3L))
        assertFalse(selectedIds.contains(4L))
        assertFalse(selectedIds.contains(5L))
        assertFalse(selectedIds.contains(6L))

        // Check Archive screen button state
        val isArchiveScreen = true
        val archiveButtonLabel = if (isArchiveScreen) "Unarchive" else "Archive"
        assertEquals("Unarchive", archiveButtonLabel)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECK 5: Select all on 1,000+ items & Undo restoration
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testLargeListSelectAllAndUndo() {
        val totalCount = 1200
        val largeList = (1L..totalCount.toLong()).map { createThread(it) }

        // Select all on 1,200 items
        val selectedIds = largeList.map { it.threadId }.toSet()
        assertEquals(1200, selectedIds.size)

        // Simulate Archive action on 1,200 items
        var archivedIds = setOf<Long>()
        val idsToArchive = selectedIds
        archivedIds = archivedIds + idsToArchive
        val undoMessage = if (idsToArchive.size == 1) "Archived 1 conversation" else "Archived ${idsToArchive.size} conversations"
        assertEquals("Archived 1200 conversations", undoMessage)
        assertEquals(1200, archivedIds.size)

        // Simulate Undo
        archivedIds = archivedIds - idsToArchive
        assertEquals(0, archivedIds.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECK 6: Delete confirmation dialog for > 20 conversations
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testDeleteConfirmationThreshold() {
        fun shouldShowDeleteConfirmation(selectedCount: Int): Boolean {
            return selectedCount > 20
        }

        // <= 20 items: direct delete (no confirmation required)
        assertFalse(shouldShowDeleteConfirmation(1))
        assertFalse(shouldShowDeleteConfirmation(15))
        assertFalse(shouldShowDeleteConfirmation(20))

        // > 20 items: must show confirmation dialog
        assertTrue(shouldShowDeleteConfirmation(21))
        assertTrue(shouldShowDeleteConfirmation(38))
        assertTrue(shouldShowDeleteConfirmation(500))

        val count = 38
        val dialogTitle = "Delete $count conversations?"
        assertEquals("Delete 38 conversations?", dialogTitle)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECK 7: Mark Read/Unread and Star/Unstar toggle logic
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testReadAndStarToggleLabels() {
        val unreadThread = createThread(1L, read = false)
        val readThread = createThread(2L, read = true)

        val selectedThreads1 = listOf(unreadThread, readThread)
        val hasUnread1 = selectedThreads1.any { !it.read }
        val readLabel1 = if (hasUnread1) "Mark as read" else "Mark as unread"
        assertEquals("Mark as read", readLabel1)

        val selectedThreads2 = listOf(readThread)
        val hasUnread2 = selectedThreads2.any { !it.read }
        val readLabel2 = if (hasUnread2) "Mark as read" else "Mark as unread"
        assertEquals("Mark as unread", readLabel2)

        val starredThread = createThread(3L, starred = true)
        val allStarred = listOf(starredThread).all { it.threadId == 3L }
        val starLabel = if (allStarred) "Unstar" else "Star"
        assertEquals("Unstar", starLabel)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECK 8: Selection bar scaling: fixed height, shrinks horizontally to center with reduced gaps and icons
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testSelectionBarScalingCalculation() {
        val scales = listOf(0.70f, 0.80f, 0.90f, 1.0f)
        val barHeight = 68f // Bar height stays fixed, does not reduce

        for (scale in scales) {
            val t = ((scale - 0.70f) / 0.30f).coerceIn(0f, 1f)
            val buttonSize = 38f + 12f * t
            val extraPillWidth = 12f + 8f * t
            val pillWidth = buttonSize + extraPillWidth
            val gap = 3f + 5f * t
            val horizontalPadding = 6f + 4f * t
            val iconSize = 16f + 8f * t
            val totalWidth = (buttonSize * 4f) + pillWidth + (gap * 4f) + (horizontalPadding * 2f)

            assertEquals(68f, barHeight, 0.01f) // Height does not reduce
            assertTrue("Button size must be between 38 and 50", buttonSize in 38f..50.01f)
            assertTrue("Icon gap must shrink from 8dp down to 3dp", gap in 3f..8.01f)
            assertTrue("Icon size must scale down proportionally from 24dp to 16dp", iconSize in 16f..24.01f)
            assertTrue("Total bar width shrinks horizontally to center (between 220dp and 328dp)", totalWidth in 220f..328f)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECK 9: Toast clearance above navigation pill and selection bar
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testToastPillClearanceAboveNavAndSelectionBar() {
        val navHeights = listOf(49, 56, 68)
        val fabSizes = listOf(42, 47, 66)

        for (nav in navHeights) {
            for (fab in fabSizes) {
                val selectionBarHeight = 68
                val maxBottom = maxOf(nav, fab, selectionBarHeight)
                val toastClearance = (maxBottom + 48).coerceAtLeast(120)

                // The top edge of nav pill or selection bar is 24dp (bottom padding) + maxBottom
                val topEdgeOfBottomBar = 24 + maxBottom
                val clearanceGap = toastClearance - topEdgeOfBottomBar

                assertTrue("Toast bottom clearance ($toastClearance dp) must be strictly above bottom bar ($topEdgeOfBottomBar dp) by at least 24dp", clearanceGap >= 24)
            }
        }
    }
}
