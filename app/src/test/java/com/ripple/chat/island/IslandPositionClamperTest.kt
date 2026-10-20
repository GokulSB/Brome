package com.ripple.chat.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandPositionClamperTest {

    @Test
    fun testDefaultPosition_aroundCamera() {
        val result = IslandPositionClamper.clamp(
            screenWidthDp = 390f,
            screenHeightDp = 844f,
            statusBarTopInsetDp = 38f,
            cutoutHeightDp = 32f,
            pillWidthDp = 210f,
            pillHeightDp = 36f,
            scaleMultiplier = 1.0f,
            isAroundCamera = true,
            userOffsetX = 0,
            userOffsetY = 0
        )

        assertEquals(0f, result.xDp, 0.01f)
        assertEquals(0f, result.yDp, 0.01f)
    }

    @Test
    fun testDefaultPosition_topCenter() {
        val result = IslandPositionClamper.clamp(
            screenWidthDp = 390f,
            screenHeightDp = 844f,
            statusBarTopInsetDp = 38f,
            cutoutHeightDp = 32f,
            pillWidthDp = 176f,
            pillHeightDp = 36f,
            scaleMultiplier = 1.0f,
            isAroundCamera = false,
            userOffsetX = 0,
            userOffsetY = 0
        )

        assertEquals(0f, result.xDp, 0.01f)
        // Base Y is statusBarTopInsetDp (38) + 8 = 46dp
        assertEquals(46f, result.yDp, 0.01f)
    }

    @Test
    fun testHorizontalClamping_keepsAtLeast8dpFromEdges() {
        // Screen width 390, pill width 210
        // Left edge: (390/2) - (210/2) + X >= 8 -> 90 + X >= 8 -> X >= -82
        // Right edge: 90 - X >= 8 -> X <= 82
        val leftExtreme = IslandPositionClamper.clamp(
            screenWidthDp = 390f,
            screenHeightDp = 844f,
            statusBarTopInsetDp = 38f,
            cutoutHeightDp = 32f,
            pillWidthDp = 210f,
            pillHeightDp = 36f,
            userOffsetX = -100,
            userOffsetY = 0
        )
        assertEquals(-82f, leftExtreme.xDp, 0.01f)

        val rightExtreme = IslandPositionClamper.clamp(
            screenWidthDp = 390f,
            screenHeightDp = 844f,
            statusBarTopInsetDp = 38f,
            cutoutHeightDp = 32f,
            pillWidthDp = 210f,
            pillHeightDp = 36f,
            userOffsetX = 100,
            userOffsetY = 0
        )
        assertEquals(82f, rightExtreme.xDp, 0.01f)
    }

    @Test
    fun testHorizontalClamping_withScaling() {
        // Large scale (1.1x): scaled width is 210 * 1.1 = 231dp
        // Max X: (390 - 231) / 2 - 8 = 79.5 - 8 = 71.5dp
        val scaledLarge = IslandPositionClamper.clamp(
            screenWidthDp = 390f,
            screenHeightDp = 844f,
            statusBarTopInsetDp = 38f,
            cutoutHeightDp = 32f,
            pillWidthDp = 210f,
            pillHeightDp = 36f,
            scaleMultiplier = 1.1f,
            userOffsetX = 100,
            userOffsetY = 0
        )
        assertEquals(71.5f, scaledLarge.xDp, 0.01f)
    }

    @Test
    fun testVerticalClamping_keepsClearOfStatusBarAndCutout() {
        val topCenterClamped = IslandPositionClamper.clamp(
            screenWidthDp = 390f,
            screenHeightDp = 844f,
            statusBarTopInsetDp = 48f,
            cutoutHeightDp = 40f,
            pillWidthDp = 176f,
            pillHeightDp = 36f,
            isAroundCamera = false,
            userOffsetX = 0,
            userOffsetY = 0
        )
        assertTrue(topCenterClamped.yDp >= 48f)
    }

    @Test
    fun testVerticalClamping_keepsAtLeast8dpFromBottom() {
        // Screen height 844, pill height 180 (expanded)
        // Max Y <= 844 - 180 - 8 = 656dp
        val result = IslandPositionClamper.clamp(
            screenWidthDp = 390f,
            screenHeightDp = 844f,
            statusBarTopInsetDp = 48f,
            cutoutHeightDp = 40f,
            pillWidthDp = 330f,
            pillHeightDp = 180f,
            isAroundCamera = false,
            userOffsetX = 0,
            userOffsetY = 120
        )
        assertTrue(result.yDp <= 844f - 180f - 8f)
    }
}
