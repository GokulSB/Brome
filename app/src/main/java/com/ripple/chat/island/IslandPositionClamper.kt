package com.ripple.chat.island

/**
 * Calculates on-screen clamped position for Dynamic Island overlay.
 * Clamps the position so the left and right edges keep at least 8dp
 * from screen edges, and the top never goes above status bar inset or behind cutout.
 */
object IslandPositionClamper {

    data class ClampedPosition(
        val xDp: Float,
        val yDp: Float
    )

    fun clamp(
        screenWidthDp: Float,
        screenHeightDp: Float,
        statusBarTopInsetDp: Float,
        cutoutHeightDp: Float,
        pillWidthDp: Float,
        pillHeightDp: Float,
        scaleMultiplier: Float = 1.0f,
        isAroundCamera: Boolean = true,
        userOffsetX: Int = 0,
        userOffsetY: Int = 0
    ): ClampedPosition {
        val scaledWidth = pillWidthDp * scaleMultiplier
        val scaledHeight = pillHeightDp * scaleMultiplier

        // Clamping X: Relative to screen center (Gravity.CENTER_HORIZONTAL)
        // Pill center is screenWidthDp / 2 + X
        // Left edge: (screenWidthDp / 2) - (scaledWidth / 2) + X >= 8dp
        // Right edge: (screenWidthDp / 2) + (scaledWidth / 2) + X <= screenWidthDp - 8dp
        val maxAbsX = ((screenWidthDp - scaledWidth) / 2f - 8f).coerceAtLeast(0f)
        val clampedX = userOffsetX.toFloat().coerceIn(-maxAbsX, maxAbsX)

        // Clamping Y: Relative to top of screen (Gravity.TOP)
        val baseY = if (isAroundCamera) 0f else (statusBarTopInsetDp + 8f)
        val minY = if (isAroundCamera) 0f else maxOf(statusBarTopInsetDp, cutoutHeightDp)
        val maxY = (screenHeightDp - scaledHeight - 8f).coerceAtLeast(minY)
        val clampedY = (baseY + userOffsetY.toFloat()).coerceIn(minY, maxY)

        return ClampedPosition(
            xDp = clampedX,
            yDp = clampedY
        )
    }
}
