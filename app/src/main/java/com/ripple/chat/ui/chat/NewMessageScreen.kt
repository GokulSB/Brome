package com.ripple.chat.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ripple.chat.ui.contacts.ContactItem
import com.ripple.chat.ui.contacts.ContactsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    onNavigateToChat: (String, String?, String?, String?, String?) -> Unit,
    onBack: () -> Unit,
    viewModel: ContactsViewModel = viewModel()
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val selectedContacts = remember { mutableStateListOf<ContactItem>() }
    var selectedTab by remember { mutableStateOf("All") }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
        if (isGranted) viewModel.loadContacts()
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        } else {
            viewModel.loadContacts()
        }
    }

    val filteredContacts = remember(contacts, searchQuery, selectedTab) {
        val tabFiltered = when(selectedTab) {
            "Phone" -> contacts.filter { it.accountTypes.any { t -> t.contains("phone") || t.contains("device") || t == "null" } }
            "SIM" -> contacts.filter { it.accountTypes.any { t -> t.contains("sim") } }
            "WhatsApp" -> contacts.filter { it.accountTypes.any { t -> t.contains("whatsapp") } }
            "Email" -> contacts.filter { it.accountTypes.any { t -> t.contains("google") || t.contains("email") } }
            else -> contacts
        }
        
        if (searchQuery.isBlank()) tabFiltered else {
            tabFiltered.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.number.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        com.ripple.chat.ui.components.FracturedGlassBackground()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Search row: Back arrow + rounded square search box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        ),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search contacts...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 16.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    )
                }
            }

            // Filter chips row below search
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    Triple("All", Icons.Rounded.Contacts, contacts.size),
                    Triple("Phone", Icons.Rounded.Phone, contacts.count { it.accountTypes.any { t -> t.contains("phone") || t.contains("device") || t == "null" } }),
                    Triple("SIM", Icons.Rounded.SimCard, contacts.count { it.accountTypes.any { t -> t.contains("sim") } }),
                    Triple("WhatsApp", Icons.Rounded.Chat, contacts.count { it.accountTypes.any { t -> t.contains("whatsapp") } }),
                    Triple("Email", Icons.Rounded.Email, contacts.count { it.accountTypes.any { t -> t.contains("google") || t.contains("email") } })
                )
                items(tabs) { (label, icon, count) ->
                    FilterChip(
                        selected = selectedTab == label,
                        onClick = { selectedTab = label },
                        label = { Text("$label ($count)", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // Selected contacts chips
            if (selectedContacts.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedContacts) { contact ->
                        InputChip(
                            selected = true,
                            onClick = { selectedContacts.remove(contact) },
                            label = { Text(contact.name, fontSize = 13.sp) },
                            trailingIcon = {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // Contacts list
            if (hasPermission) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredContacts, key = { it.id }) { contact ->
                        val isSelected = selectedContacts.contains(contact)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    if (isSelected) selectedContacts.remove(contact)
                                    else selectedContacts.add(contact)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!contact.photoUri.isNullOrEmpty()) {
                                coil.compose.AsyncImage(
                                    model = android.net.Uri.parse(contact.photoUri),
                                    contentDescription = contact.name,
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(contact.name.firstOrNull()?.toString() ?: "?", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(contact.number, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = "Select",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Permission required to read Contacts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // FAB
        if (selectedContacts.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    val numbers = selectedContacts.mapNotNull { 
                        com.ripple.chat.utils.MessageParser.cleanAddress(it.number)
                    }
                    val numbersStr = numbers.joinToString(";")
                    val contactName = if (selectedContacts.size == 1) selectedContacts.first().name else selectedContacts.joinToString(", ") { it.name }
                    val photoUri = if (selectedContacts.size == 1) selectedContacts.first().photoUri else null
                    val threadId = try {
                        android.provider.Telephony.Threads.getOrCreateThreadId(context, numbers.toSet())
                    } catch (e: Exception) {
                        -1L
                    }
                    val targetChatId = if (threadId > 0) threadId.toString() else numbersStr
                    onNavigateToChat(targetChatId, numbersStr, "PERSONAL", contactName, photoUri)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Start Chat")
            }
        }
    }
}

@Composable
fun FilterRailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, isActive: Boolean, onClick: () -> Unit, activeTint: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(if (isActive) activeTint.copy(alpha = 0.2f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = if (isActive) activeTint else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        if (count > 0) {
            Text(count.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        }
    }
}
