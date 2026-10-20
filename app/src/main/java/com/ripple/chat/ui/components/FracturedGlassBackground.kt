package com.ripple.chat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FracturedGlassBackground(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().clearAndSetSemantics {}) {
        // Shard 1 — top-left
        Shard(
            points = listOf(Offset(0f, 0f), Offset(0.65f, 0f), Offset(0.38f, 1f), Offset(0f, 0.55f)),
            color = cs.primary,
            width = 260.dp,
            height = 220.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(x = (-40).dp, y = (-60).dp)
        )
        // Shard 2 — top-right
        Shard(
            points = listOf(Offset(1f, 0f), Offset(1f, 0.75f), Offset(0.28f, 1f), Offset(0.55f, 0.15f)),
            color = cs.tertiary,
            width = 230.dp,
            height = 260.dp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 60.dp, y = (-30).dp)
        )
        // Shard 3 — mid-left
        Shard(
            points = listOf(Offset(0.2f, 0f), Offset(1f, 0.25f), Offset(0.65f, 1f), Offset(0f, 0.65f)),
            color = cs.secondary,
            width = 200.dp,
            height = 180.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(x = (LocalConfiguration.current.screenWidthDp * 0.3f).dp, y = 280.dp)
        )
        // Shard 4 — bottom-left
        Shard(
            points = listOf(Offset(0f, 0.3f), Offset(0.6f, 0f), Offset(1f, 0.6f), Offset(0.3f, 1f)),
            color = cs.tertiary,
            width = 220.dp,
            height = 240.dp,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-50).dp, y = 70.dp)
        )
        // Shard 5 — bottom-right
        Shard(
            points = listOf(Offset(0.3f, 0f), Offset(1f, 0.2f), Offset(0.8f, 1f), Offset(0f, 0.8f)),
            color = cs.primary,
            width = 200.dp,
            height = 200.dp,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 40.dp, y = 40.dp)
        )
    }
}

@Composable
private fun Shard(
    points: List<Offset>,
    color: Color,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width, height)
            .graphicsLayer(alpha = 0.11f)
            .drawWithCache {
                val path = Path().apply {
                    points.forEachIndexed { i, p ->
                        val x = p.x * size.width
                        val y = p.y * size.height
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                onDrawBehind { drawPath(path, color) }
            }
    )
}
