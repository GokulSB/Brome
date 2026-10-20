package com.ripple.chat.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ripple.chat.data.BackgroundDesign
import com.ripple.chat.data.GradientWashLevel
import com.ripple.chat.data.ThemeMode
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BromeBackground(
    design: BackgroundDesign = BackgroundDesign.SHARDS,
    seed: Int = 4821,
    washLevel: GradientWashLevel = GradientWashLevel.MINOR,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val themeConfig = LocalThemeConfig.current
    val dark = when (themeConfig?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }

    val fallbackBase = if (dark) Color(0xFF101612) else Color(0xFFF4FAFC)
    val fallbackPrimary = if (dark) Color(0xFFCDF564) else Color(0xFF00698B)
    val fallbackSecondary = if (dark) Color(0xFFB8A9FF) else Color(0xFF6B3FD4)
    val fallbackTertiary = if (dark) Color(0xFFFF8F6B) else Color(0xFFB4552A)
    val fallbackInk = if (dark) Color(0xFFFFFFFF) else Color(0xFF0F2127)

    val baseColor = if (design == BackgroundDesign.AMOLED && dark) Color.Black else (MaterialTheme.colorScheme.background.takeIf { it != Color.Unspecified } ?: fallbackBase)
    val primaryColor = MaterialTheme.colorScheme.primary.takeIf { it != Color.Unspecified } ?: fallbackPrimary
    val secondaryColor = MaterialTheme.colorScheme.secondary.takeIf { it != Color.Unspecified } ?: fallbackSecondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary.takeIf { it != Color.Unspecified } ?: fallbackTertiary
    val inkColor = MaterialTheme.colorScheme.onBackground.takeIf { it != Color.Unspecified } ?: fallbackInk

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
            .clearAndSetSemantics {}
    ) {
        Crossfade(
            targetState = Triple(design, seed, washLevel),
            animationSpec = tween(300),
            label = "bromeBackgroundCrossfade"
        ) { (curDesign, curSeed, curWash) ->
            Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val w = size.width
                            val h = size.height
                            val scale = minOf(w / 390f, h / 844f)

                            val rnd = java.util.Random(curSeed.toLong())

                            val drawBackgroundDesign: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                                when (curDesign) {
                                    BackgroundDesign.SHARDS -> {
                                        val count = 8
                                        val tintList = listOf(inkColor, primaryColor, tertiaryColor, secondaryColor)
                                        for (idx in 0 until count) {
                                            val cx = rnd.nextFloat() * w
                                            val cy = rnd.nextFloat() * h
                                            val r = (110f + rnd.nextFloat() * 190f) * scale
                                            val baseRot = rnd.nextFloat() * 2f * Math.PI.toFloat()
                                            val colorChoice = tintList[rnd.nextInt(tintList.size)]
                                            val alpha = if (colorChoice == inkColor) (if (dark) 0.07f else 0.06f) else (0.10f + rnd.nextFloat() * 0.02f)

                                            val path = Path()
                                            for (v in 0 until 3) {
                                                val jitter = (rnd.nextFloat() - 0.5f) * 0.6f
                                                val angle = baseRot + (v * 2f * Math.PI.toFloat() / 3f) + jitter
                                                val dist = r * (0.7f + rnd.nextFloat() * 0.6f)
                                                val vx = cx + dist * cos(angle)
                                                val vy = cy + dist * sin(angle)
                                                if (v == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
                                            }
                                            path.close()
                                            drawPath(path, color = colorChoice.copy(alpha = alpha))
                                        }
                                    }
                                    BackgroundDesign.BLOBS -> {
                                        val blobCenters = listOf(
                                            Offset(0.25f * w, 0.22f * h) to primaryColor,
                                            Offset(0.78f * w, 0.45f * h) to secondaryColor,
                                            Offset(0.22f * w, 0.70f * h) to tertiaryColor,
                                            Offset(0.75f * w, 0.88f * h) to primaryColor
                                        )
                                        val alpha = if (dark) 0.30f else 0.24f
                                        blobCenters.forEach { (center, col) ->
                                            val radius = (120f + rnd.nextFloat() * 30f) * scale
                                            val rot = (rnd.nextFloat() - 0.5f) * 60f
                                            rotate(rot, pivot = center) {
                                                drawCircle(
                                                    brush = Brush.radialGradient(
                                                        colors = listOf(col.copy(alpha = alpha), col.copy(alpha = 0f)),
                                                        center = center,
                                                        radius = radius
                                                    ),
                                                    radius = radius,
                                                    center = center
                                                )
                                            }
                                        }
                                    }
                                    BackgroundDesign.WAVES -> {
                                        val waveColors = listOf(primaryColor, secondaryColor, tertiaryColor, primaryColor)
                                        for (i in 0 until 4) {
                                            val baseY = (470f + 95f * i) / 844f * h
                                            val amp = (16f + rnd.nextFloat() * 18f) * scale
                                            val phase = rnd.nextFloat() * 2f * Math.PI.toFloat()
                                            val alpha = if (dark) (0.08f + 0.03f * i) else (0.09f + 0.03f * i)

                                            val path = Path()
                                            path.moveTo(0f, h)
                                            path.lineTo(0f, baseY)

                                            val steps = 80
                                            for (s in 0..steps) {
                                                val x = s * w / steps
                                                val angle = 2f * Math.PI.toFloat() * (x / w) + phase
                                                val y = baseY - (amp * sin(angle) + (amp * 0.5f) * sin(2f * angle))
                                                path.lineTo(x, y)
                                            }
                                            path.lineTo(w, h)
                                            path.close()

                                            drawPath(path, color = waveColors[i].copy(alpha = alpha))
                                        }
                                    }
                                    BackgroundDesign.SHAPES -> {
                                        val shapePolygons = listOf(Cookie8Shape, Flower5Shape, Clover3Shape, Sunny12Shape, Squircle4Shape)
                                        val shapeColors = listOf(primaryColor, secondaryColor, tertiaryColor, inkColor)

                                        for (i in 0 until 7) {
                                            val sz = (80f + rnd.nextFloat() * 110f) * scale
                                            val cx = rnd.nextFloat() * w
                                            val cy = rnd.nextFloat() * h
                                            val rot = rnd.nextFloat() * 360f
                                            val poly = shapePolygons[rnd.nextInt(shapePolygons.size)]
                                            val col = shapeColors[rnd.nextInt(shapeColors.size)]

                                            val outlineOnly = (i % 3 == 2)
                                            val alpha = if (outlineOnly) 0.18f else (if (dark) (0.08f + rnd.nextFloat() * 0.05f) else (0.09f + rnd.nextFloat() * 0.06f))

                                            val path = Path()
                                            val outline = poly.createOutline(Size(sz, sz), androidx.compose.ui.unit.LayoutDirection.Ltr, this)
                                            if (outline is androidx.compose.ui.graphics.Outline.Generic) {
                                                path.addPath(outline.path)
                                            }

                                            rotate(rot, pivot = Offset(cx, cy)) {
                                                val offset = Offset(cx - sz / 2f, cy - sz / 2f)
                                                drawContext.transform.translate(offset.x, offset.y)
                                                if (outlineOnly) {
                                                    drawPath(path, color = col.copy(alpha = alpha), style = Stroke(width = 1.4.dp.toPx()))
                                                } else {
                                                    drawPath(path, color = col.copy(alpha = alpha))
                                                }
                                                drawContext.transform.translate(-offset.x, -offset.y)
                                            }
                                        }
                                    }
                                    BackgroundDesign.CONTOURS -> {
                                        val center1 = Offset(0.30f * w, 0.35f * h)
                                        val center2 = Offset(0.70f * w, 0.68f * h)
                                        val strokePx = 1.2.dp.toPx()

                                        listOf(
                                            Triple(center1, inkColor, if (dark) 0.13f else 0.14f),
                                            Triple(center2, primaryColor, 0.20f)
                                        ).forEach { (center, col, alpha) ->
                                            for (k in 1..12) {
                                                val baseR = k * 34f * scale
                                                val path = Path()
                                                val steps = 120
                                                val phase1 = rnd.nextFloat() * 2f * Math.PI.toFloat()
                                                val phase2 = rnd.nextFloat() * 2f * Math.PI.toFloat()

                                                for (s in 0..steps) {
                                                    val angle = s * 2f * Math.PI.toFloat() / steps
                                                    val r = baseR * (1f + 0.08f * sin(3f * angle + phase1) + 0.05f * sin(5f * angle + phase2))
                                                    val rx = center.x + r * cos(angle)
                                                    val ry = center.y + r * sin(angle)
                                                    if (s == 0) path.moveTo(rx, ry) else path.lineTo(rx, ry)
                                                }
                                                path.close()
                                                drawPath(path, color = col.copy(alpha = alpha), style = Stroke(width = strokePx))
                                            }
                                        }
                                    }
                                    BackgroundDesign.DOTS -> {
                                        val dotRadius = 1.3.dp.toPx()
                                        val spacing = 18.dp.toPx()
                                        val cols = (w / spacing).toInt() + 1
                                        val rows = (h / spacing).toInt() + 1
                                        val maxDist = kotlin.math.hypot(w, h)
                                        val baseAlpha = if (dark) 0.40f else 0.34f

                                        for (i in 0..cols) {
                                            for (j in 0..rows) {
                                                val cx = i * spacing
                                                val cy = j * spacing
                                                val dist = kotlin.math.hypot(cx, cy)
                                                val mask = (1f - (dist / maxDist)).coerceIn(0f, 1f)
                                                val alpha = baseAlpha * mask
                                                if (alpha > 0.02f) {
                                                    drawCircle(
                                                        color = inkColor.copy(alpha = alpha),
                                                        radius = dotRadius,
                                                        center = Offset(cx, cy)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    BackgroundDesign.MIST -> {
                                        // Top-left primary
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(primaryColor.copy(alpha = if (dark) 0.20f else 0.18f), Color.Transparent),
                                                center = Offset(0f, 0f),
                                                radius = 430f * scale
                                            ),
                                            radius = 430f * scale,
                                            center = Offset(0f, 0f)
                                        )
                                        // Bottom-right tertiary
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(tertiaryColor.copy(alpha = if (dark) 0.15f else 0.14f), Color.Transparent),
                                                center = Offset(w, h),
                                                radius = 540f * scale
                                            ),
                                            radius = 540f * scale,
                                            center = Offset(w, h)
                                        )
                                        // Right-edge secondary
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(secondaryColor.copy(alpha = 0.10f), Color.Transparent),
                                                center = Offset(w, 0.5f * h),
                                                radius = 300f * scale
                                            ),
                                            radius = 300f * scale,
                                            center = Offset(w, 0.5f * h)
                                        )
                                    }
                                    BackgroundDesign.PLAIN, BackgroundDesign.AMOLED -> { /* Base color only */ }
                                }

                                // ── GRADIENT WASH LAYER (Applies on top of ANY design) ──
                                if (curWash != GradientWashLevel.OFF) {
                                    // Top primary wash
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(primaryColor.copy(alpha = curWash.topAlpha), Color.Transparent),
                                            startY = 0f,
                                            endY = 0.55f * h
                                        )
                                    )
                                    // Bottom tertiary wash
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, tertiaryColor.copy(alpha = curWash.bottomAlpha)),
                                            startY = 0.45f * h,
                                            endY = h
                                        )
                                    )
                                }
                            }

                            onDrawWithContent {
                                drawBackgroundDesign()
                            }
                        }
                )
        }

        content()
    }
}
