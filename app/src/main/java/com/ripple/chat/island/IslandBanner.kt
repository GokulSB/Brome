package com.ripple.chat.island

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun IslandInAppBannerHost(
    onNavigateToThread: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentBanner by IslandBannerManager.currentBanner.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentBanner?.id) {
        if (currentBanner != null) {
            delay(5000)
            IslandBannerManager.dismiss()
        }
    }

    AnimatedVisibility(
        visible = currentBanner != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        currentBanner?.let { message ->
            val theme = IslandVisualDefaults.themeFor(message.islandCategory)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable {
                        IslandBannerManager.dismiss()
                        if (message.threadId > 0) {
                            onNavigateToThread(message.threadId)
                        } else {
                            IslandActions.openConversation(
                                context = context,
                                address = message.address,
                                threadId = message.threadId,
                                notificationId = message.notificationId
                            )
                        }
                    },
                shape = RoundedCornerShape(22.dp),
                color = IslandVisualDefaults.PillBackground,
                contentColor = IslandVisualDefaults.PillTextColor,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IslandShapedAvatar(
                            theme = theme,
                            size = 32.dp,
                            iconSize = 18.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = message.senderName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Default,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = theme.containerColor
                                ) {
                                    Text(
                                        text = theme.tagText,
                                        color = theme.accentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            val subtitle = when (message.islandCategory) {
                                IslandCategory.OTP -> message.otpCode?.let { "OTP: $it" } ?: message.body
                                IslandCategory.TRANSACTIONS -> message.transactionAmount ?: message.body
                                else -> message.body
                            }
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { IslandBannerManager.dismiss() },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222222))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close banner",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Optional Quick Action
                    if (message.islandCategory == IslandCategory.OTP && message.otpCode != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    IslandActions.copyOtpCode(
                                        context = context,
                                        code = message.otpCode,
                                        notificationId = message.notificationId
                                    )
                                    coroutineScope.launch {
                                        delay(800)
                                        IslandBannerManager.dismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.accentColor,
                                    contentColor = Color(0xFF1F1A3A)
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy code", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
