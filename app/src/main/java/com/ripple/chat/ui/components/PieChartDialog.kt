package com.ripple.chat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun PieChartDialog(
    title: String,
    data: Map<String, Double>,
    onDismiss: () -> Unit,
    onItemClick: ((String) -> Unit)? = null
) {
    // Generate distinct colors for pie slices based on hash code
    val colors = data.keys.toList().map { key ->
        val hash = key.hashCode()
        Color(
            red = (hash and 0xFF) / 255f,
            green = ((hash shr 8) and 0xFF) / 255f,
            blue = ((hash shr 16) and 0xFF) / 255f
        ).let { color ->
            // Mix with white to get pastel colors and ensure they are bright enough for dark mode if needed
            Color(
                red = (color.red + 1f) / 2f,
                green = (color.green + 1f) / 2f,
                blue = (color.blue + 1f) / 2f
            )
        }
    }

    val total = data.values.sum()
    
    var animationPlayed by remember { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    
    LaunchedEffect(Unit) {
        animationPlayed = true
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (total <= 0.0 || data.isEmpty()) {
                    Text(
                        text = "No data available",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    // Pie Chart
                    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(200.dp)) {
                            var startAngle = -90f
                            data.entries.forEachIndexed { index, entry ->
                                val sweepAngle = ((entry.value / total) * 360f).toFloat() * animationProgress
                                drawArc(
                                    color = colors[index],
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = true,
                                    size = size
                                )
                                startAngle += sweepAngle
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    // Legend
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(data.entries.toList().mapIndexed { index, entry -> index to entry }) { (index, entry) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(colors[index])
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = entry.key,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatAmountFull(entry.value),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (onItemClick != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    androidx.compose.material3.IconButton(
                                        onClick = { onItemClick(entry.key) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Rounded.Visibility,
                                            contentDescription = "View messages",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

private fun formatAmountFull(amount: Double): String {
    return String.format(java.util.Locale.US, "%,.2f", amount)
}
