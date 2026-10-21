package com.ripple.chat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.ripple.chat.utils.LinkSafetyAnalysis
import com.ripple.chat.utils.LinkSafetyChecker
import com.ripple.chat.utils.ScamRiskLevel

@Composable
fun LinkSafetyDialog(
    url: String,
    onDismiss: () -> Unit,
    onConfirmOpen: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val analysis: LinkSafetyAnalysis = remember(url) { LinkSafetyChecker.analyze(url) }

    fun copyToClipboard(textToCopy: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Link", textToCopy)
        clipboard.setPrimaryClip(clip)
        BromeToast.show(
            context = context,
            message = "Link copied to clipboard",
            icon = Icons.Rounded.ContentCopy,
            type = BromeToast.ToastType.SUCCESS
        )
    }

    fun launchBrowser(targetUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No web browser app available", Toast.LENGTH_SHORT).show()
        }
    }

    val isHighRisk = analysis.riskLevel == ScamRiskLevel.HIGH_RISK
    val isSuspicious = analysis.riskLevel == ScamRiskLevel.SUSPICIOUS

    val headerIcon = when {
        isHighRisk -> Icons.Rounded.GppMaybe
        isSuspicious -> Icons.Rounded.WarningAmber
        else -> Icons.Rounded.OpenInBrowser
    }

    val iconContainerColor = when {
        isHighRisk -> MaterialTheme.colorScheme.errorContainer
        isSuspicious -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val iconColor = when {
        isHighRisk -> MaterialTheme.colorScheme.error
        isSuspicious -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.primary
    }

    val titleText = when {
        isHighRisk -> "Potential Scam Link Detected"
        isSuspicious -> "Caution: Suspicious Link"
        else -> "Open in Browser?"
    }

    val badgeLabel = when {
        isHighRisk -> "HIGH RISK"
        isSuspicious -> "SUSPICIOUS"
        else -> "WEB LINK"
    }

    val badgeBgColor = when {
        isHighRisk -> MaterialTheme.colorScheme.errorContainer
        isSuspicious -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val badgeTextColor = when {
        isHighRisk -> MaterialTheme.colorScheme.onErrorContainer
        isSuspicious -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = headerIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = CircleShape,
                    color = badgeBgColor,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Host & URL Preview Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (analysis.isInsecureHttp) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = if (analysis.isInsecureHttp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = analysis.host,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { copyToClipboard(analysis.normalizedUrl) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy Link",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = analysis.normalizedUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Warnings section (for Suspicious or High-Risk links)
                if (analysis.warnings.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isHighRisk) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                               else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = if (isHighRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isHighRisk) "Security Concerns Detected:" else "Safety Notice:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHighRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }

                            analysis.warnings.forEach { warning ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isHighRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = warning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Never enter passwords, PINs, or banking details on unfamiliar or unverified sites.",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    // Safe confirmation note
                    Text(
                        text = "Make sure you trust the sender before visiting this web address in your browser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (isHighRisk) {
                // For high-risk, "Cancel" is the primary safe action
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = CircleShape
                ) {
                    Text(
                        text = "Cancel (Stay Safe)",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (isSuspicious) {
                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        if (onConfirmOpen != null) {
                            onConfirmOpen(analysis.normalizedUrl)
                        } else {
                            launchBrowser(analysis.normalizedUrl)
                        }
                    },
                    shape = CircleShape
                ) {
                    Text(
                        text = "Proceed to Browser",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = {
                        onDismiss()
                        if (onConfirmOpen != null) {
                            onConfirmOpen(analysis.normalizedUrl)
                        } else {
                            launchBrowser(analysis.normalizedUrl)
                        }
                    },
                    shape = CircleShape
                ) {
                    Text(
                        text = "Open in Browser",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            if (isHighRisk) {
                // Secondary dangerous action
                TextButton(
                    onClick = {
                        onDismiss()
                        if (onConfirmOpen != null) {
                            onConfirmOpen(analysis.normalizedUrl)
                        } else {
                            launchBrowser(analysis.normalizedUrl)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "Open Anyway",
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    )
}
