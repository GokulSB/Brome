package com.ripple.chat.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ripple.chat.theme.BricolageFontFamily
import com.ripple.chat.theme.FigtreeFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendProbeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var destination by remember { mutableStateOf("") }
    var textMessage by remember { mutableStateOf("Brome send probe test message") }

    val subscriptions by SendProbeManager.subscriptions.collectAsState()
    val selectedSubId by SendProbeManager.selectedSubId.collectAsState()
    val results by SendProbeManager.results.collectAsState()
    val logs by SendProbeManager.probeLogs.collectAsState()

    LaunchedEffect(Unit) {
        SendProbeManager.refreshSubscriptions(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Probe (Debug)", fontFamily = BricolageFontFamily, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { SendProbeManager.refreshSubscriptions(context) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh SIMs")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Destination Field
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("Destination Phone Number") },
                placeholder = { Text("+1234567890 or 9876543210") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Message Body Field
            OutlinedTextField(
                value = textMessage,
                onValueChange = { textMessage = it },
                label = { Text("Message Body") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // SIM Chooser
            Text(
                text = "Active SIM Subscriptions (${subscriptions.size}):",
                fontWeight = FontWeight.Bold,
                fontFamily = FigtreeFontFamily,
                color = MaterialTheme.colorScheme.primary
            )

            if (subscriptions.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No active SIM subscriptions found or READ_PHONE_STATE permission not granted.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subscriptions.forEach { sub ->
                        val isSelected = sub.subId == selectedSubId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { SendProbeManager.selectSubId(sub.subId) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { SendProbeManager.selectSubId(sub.subId) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Slot ${sub.slotIndex}: ${sub.carrierName} (${sub.displayName})",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "SubId: ${sub.subId} | ISO: ${sub.countryIso}",
                                        fontSize = 12.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // 4 Probe Buttons
            Text(
                text = "Send Probe Experiments:",
                fontWeight = FontWeight.Bold,
                fontFamily = FigtreeFontFamily,
                color = MaterialTheme.colorScheme.primary
            )

            // Button A: Brome Default Path
            Button(
                onClick = {
                    SendProbeManager.runProbeA(context, destination, textMessage, selectedSubId)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("A. Brome default path (SmsSender)")
            }

            // Button B: Minimal SmsManager
            Button(
                onClick = {
                    SendProbeManager.runProbeB(context, destination, textMessage, selectedSubId, buttonLabel = "B")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("B. Minimal SmsManager (sendTextMessage)")
            }

            // Button C: Same on Other SIM
            Button(
                onClick = {
                    SendProbeManager.runProbeC(context, destination, textMessage, selectedSubId)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("C. Minimal on Other SIM")
            }

            // Button D: System Messaging App
            Button(
                onClick = {
                    SendProbeManager.runProbeD(context, destination, textMessage)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("D. System app (Exclude Brome)")
            }

            HorizontalDivider()

            // Live Results Panel
            Text(
                text = "Live Results:",
                fontWeight = FontWeight.Bold,
                fontFamily = FigtreeFontFamily,
                color = MaterialTheme.colorScheme.primary
            )

            listOf("A", "B", "C", "D").forEach { btn ->
                val res = results[btn]
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Path $btn:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = res?.state ?: "Idle",
                                fontWeight = FontWeight.SemiBold,
                                color = when (res?.state) {
                                    "Sent", "Delivered", "App Opened" -> Color(0xFF2E7D32)
                                    "Failed", "Exception" -> MaterialTheme.colorScheme.error
                                    "Sending..." -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (res?.sentCode != null) {
                            Text("Sent Code: ${res.sentCode}", fontSize = 12.sp)
                        }
                        if (res?.deliveryStatus != null) {
                            Text("Delivery: ${res.deliveryStatus}", fontSize = 12.sp)
                        }
                        if (res?.exception != null) {
                            Text("Error/Exception: ${res.exception}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            HorizontalDivider()

            // Live Log Console
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BromeSend Logs (${logs.size}):",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FigtreeFontFamily,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
