package com.ripple.chat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.ui.chat.HighlightedText
import com.ripple.chat.ui.chat.formatTime
import androidx.compose.foundation.shape.CircleShape

@Composable
fun MessageCard(
    modifier: Modifier = Modifier,
    thread: ChatThread,
    surfaceColor: Color,
    cornerRadius: Dp,
    searchQuery: String = "",
    onNavigateToProfile: () -> Unit = {},
    categoryColors: Map<String, Int> = emptyMap(),
    showCategorySignal: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val haloColor = when (thread.category) {
        SmsCategory.PROMOTIONAL -> null
        SmsCategory.OTP, SmsCategory.FINANCE -> MaterialTheme.colorScheme.tertiary
        SmsCategory.PERSONAL -> MaterialTheme.colorScheme.secondary
        SmsCategory.SPAM, SmsCategory.IMPORTANT -> MaterialTheme.colorScheme.error
        SmsCategory.SHOPPING, SmsCategory.TICKETS, SmsCategory.REMINDERS, SmsCategory.SUBSCRIPTIONS ->
            categoryColors[thread.category.name]?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
        SmsCategory.OTHERS -> categoryColors["OTHERS"]?.let { Color(it) } ?: Color(0xFF78909C)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clip(RoundedCornerShape(18.dp))
    ) {
        Box {
            if (showCategorySignal) {
                haloColor?.let { color ->
                    Box(
                        Modifier
                            .size(110.dp)
                            .offset(x = (-26).dp, y = (-26).dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.35f), color.copy(alpha = 0f))
                            ),
                            shape = CircleShape
                        )
                )
                }
            }
            Column(Modifier.padding(15.dp)) {
                val displayName = thread.contactName ?: thread.address
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        HighlightedText(
                            text = displayName,
                            query = searchQuery,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTime(thread.date),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                HighlightedText(
                    text = thread.body,
                    query = searchQuery,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    actions()
                }
            }
        }
    }
}

@Composable
fun TagItem(tag: String, categoryColors: Map<String, Int> = emptyMap()) {
    val defaultColor = Color(0xFFD84A3B)
    val colorInt = categoryColors[tag] ?: categoryColors[tag.uppercase()]
    val tagColor = if (colorInt != null) Color(colorInt) else defaultColor
    Box(
        modifier = Modifier
            .drawBehind {
                drawRoundRect(
                    color = tagColor.copy(alpha = 0.6f),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
                    ),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
            }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = tag.uppercase(),
            color = tagColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun TriageCard(
    thread: com.ripple.chat.data.ChatThread,
    otpCode: String?,
    expiryTime: Long?,
    isDelivery: Boolean = false,
    trackUrl: String? = null,
    onImportant: () -> Unit,
    onIgnore: () -> Unit,
    onSpam: () -> Unit,
    onExpired: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var remainingTime by androidx.compose.runtime.mutableStateOf(if (expiryTime != null) expiryTime - System.currentTimeMillis() else 1L)
    
    androidx.compose.runtime.LaunchedEffect(expiryTime) {
        if (expiryTime != null) {
            while (remainingTime > 0) {
                kotlinx.coroutines.delay(1000)
                remainingTime = expiryTime - System.currentTimeMillis()
            }
            onExpired()
        }
    }

    val backgroundBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    )

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(backgroundBrush)
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val greenAccent = androidx.compose.ui.graphics.Color(0xFF2E7D32)
                    Icon(
                        imageVector = if (isDelivery) Icons.Rounded.LocalShipping else Icons.Rounded.Security,
                        contentDescription = null,
                        tint = if (isDelivery) greenAccent else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDelivery) "DELIVERY UPDATE" else "ACTION REQUIRED",
                        color = if (isDelivery) greenAccent else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    
                    if (expiryTime != null) {
                        val mins = (remainingTime / 60000).coerceAtLeast(0)
                        val secs = ((remainingTime % 60000) / 1000).coerceAtLeast(0)
                        val timerText = String.format("%02d:%02d", mins, secs)
                        
                        Text(
                            text = timerText,
                            color = if (remainingTime < 60000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        val displayName = thread.contactName ?: thread.address
                        Text(
                            text = displayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = thread.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    if (otpCode != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("OTP", otpCode)
                                clipboard.setPrimaryClip(clip)
                                com.ripple.chat.ui.components.BromeToast.show(context, "Copied")
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = otpCode,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isDelivery) {
                        val greenAccent = androidx.compose.ui.graphics.Color(0xFF2E7D32)
                        val phoneNumber = com.ripple.chat.utils.MessageParser.extractPhoneNumber(thread.body)
                        
                        if (trackUrl != null) {
                            androidx.compose.material3.Button(
                                onClick = { 
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(trackUrl))
                                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                modifier = Modifier.weight(2f).height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = greenAccent
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Track Package", fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        } else if (phoneNumber != null) {
                            androidx.compose.material3.Button(
                                onClick = { 
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + phoneNumber))
                                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                modifier = Modifier.weight(2f).height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = greenAccent
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Call Executive", fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                        
                        androidx.compose.material3.FilledTonalButton(
                            onClick = onIgnore,
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Dismiss", fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    } else {
                        androidx.compose.material3.Button(
                            onClick = onImportant,
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Important", fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        
                        androidx.compose.material3.FilledTonalButton(
                            onClick = onIgnore,
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Ignore", fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        
                        androidx.compose.material3.TextButton(
                            onClick = onSpam,
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Spam", fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
