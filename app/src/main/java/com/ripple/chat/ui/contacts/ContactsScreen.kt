package com.ripple.chat.ui.contacts

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ripple.chat.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ripple.chat.theme.*
import com.ripple.chat.ui.components.VariantBShapedAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactItem(
    val id: Long,
    val name: String,
    val number: String,
    val photoUri: String? = null,
    val isFavorite: Boolean = false,
    val accountTypes: Set<String> = emptySet()
)

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val _contacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = _contacts.asStateFlow()

    private val _favorites = MutableStateFlow<List<ContactItem>>(emptyList())
    val favorites: StateFlow<List<ContactItem>> = _favorites.asStateFlow()

    fun toggleFavorite(contactId: Long) {
        val prefs = getApplication<Application>().getSharedPreferences("ripplechat_favorites", Context.MODE_PRIVATE)
        val favs = prefs.getStringSet("favs", emptySet())?.toMutableSet() ?: mutableSetOf()
        val idStr = contactId.toString()
        if (favs.contains(idStr)) favs.remove(idStr) else favs.add(idStr)
        prefs.edit().putStringSet("favs", favs).apply()
        loadContacts()
    }

    fun loadContacts() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val accountTypesMap = mutableMapOf<Long, MutableSet<String>>()
                try {
                    val rawUri = ContactsContract.RawContacts.CONTENT_URI
                    val rawProj = arrayOf(
                        ContactsContract.RawContacts.CONTACT_ID,
                        ContactsContract.RawContacts.ACCOUNT_TYPE
                    )
                    context.contentResolver.query(rawUri, rawProj, null, null, null)?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
                        val typeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                        while (cursor.moveToNext()) {
                            val contactId = cursor.getLong(idIdx)
                            val type = cursor.getString(typeIdx) ?: "phone"
                            val types = accountTypesMap.getOrPut(contactId) { mutableSetOf() }
                            types.add(type.lowercase())
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val result = mutableListOf<ContactItem>()
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
                )

                val prefs = context.getSharedPreferences("ripplechat_favorites", Context.MODE_PRIVATE)
                val favs = prefs.getStringSet("favs", emptySet()) ?: emptySet()

                try {
                    context.contentResolver.query(uri, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
                        val contactIdIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                        val thumbIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                        while (cursor.moveToNext()) {
                            val photoStr = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                            val thumbStr = if (thumbIdx >= 0) cursor.getString(thumbIdx) else null
                            val resolvedPhoto = photoStr ?: thumbStr

                            result.add(
                                ContactItem(
                                    id = cursor.getLong(idIdx),
                                    name = cursor.getString(nameIdx) ?: "Unknown",
                                    number = cursor.getString(numberIdx) ?: "",
                                    photoUri = resolvedPhoto,
                                    isFavorite = favs.contains(cursor.getLong(idIdx).toString()),
                                    accountTypes = accountTypesMap[cursor.getLong(contactIdIdx)] ?: emptySet()
                                )
                            )
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                result.distinctBy { it.number.replace(Regex("[^0-9+]"), "") }
            }
            _contacts.value = list
            _favorites.value = list.filter { it.isFavorite }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onNavigateToChat: (String, String?, String?, String?) -> Unit,
    viewModel: ContactsViewModel = viewModel()
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
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

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts else {
            contacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) || it.number.contains(searchQuery)
            }
        }
    }

    val frequentContacts = remember(contacts) { contacts.take(5) }
    val alphabet = remember { ('A'..'Z').toList() }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(10.dp))

            // Title "Contacts" + Add Contact Circle Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.contacts_title),
                    fontFamily = BricolageFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 40.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    onClick = { /* Add contact */ },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(52.dp).pressScale()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PersonAdd,
                            contentDescription = "Add Contact",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Pill (52dp)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search contacts",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FigtreeFontFamily,
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FigtreeFontFamily,
                                fontSize = 15.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermission) {
                // Permission Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Contacts Permission Required",
                            fontFamily = BricolageFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Brome needs permission to display your contacts and start conversations easily.",
                            fontFamily = FigtreeFontFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                            shape = CircleShape,
                            modifier = Modifier.height(48.dp).pressScale()
                        ) {
                            Text("Allow", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1f)) {
                        // FREQUENT Row
                        if (frequentContacts.isNotEmpty() && searchQuery.isBlank()) {
                            Text(
                                text = "FREQUENT",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                items(frequentContacts, key = { "freq_${it.id}" }) { contact ->
                                    val firstName = contact.name.split(" ").firstOrNull() ?: contact.name

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(60.dp)
                                            .clickable {
                                                val clean = com.ripple.chat.utils.MessageParser.cleanAddress(contact.number) ?: contact.number
                                                val threadId = try { android.provider.Telephony.Threads.getOrCreateThreadId(context, clean) } catch (_: Exception) { -1L }
                                                val targetId = if (threadId > 0) threadId.toString() else clean
                                                onNavigateToChat(targetId, clean, contact.name, contact.photoUri)
                                            }
                                            .pressScale()
                                    ) {
                                        VariantBShapedAvatar(
                                            name = contact.name,
                                            photoUri = contact.photoUri,
                                            size = 56.dp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = firstName,
                                            fontSize = 12.sp,
                                            fontFamily = FigtreeFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "ALL CONTACTS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // ALL CONTACTS List (64dp rows)
                        val navPillHeightDp = LocalBromeAppearance.current.navPillHeightDp
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = (navPillHeightDp + 48).dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(filteredContacts, key = { _, c -> c.id }) { index, contact ->
                                val maskedNumber = maskPhoneNumber(contact.number)

                                Surface(
                                    onClick = {
                                        val clean = com.ripple.chat.utils.MessageParser.cleanAddress(contact.number) ?: contact.number
                                        val threadId = try { android.provider.Telephony.Threads.getOrCreateThreadId(context, clean) } catch (_: Exception) { -1L }
                                        val targetId = if (threadId > 0) threadId.toString() else clean
                                        onNavigateToChat(targetId, clean, contact.name, contact.photoUri)
                                    },
                                    shape = getTideCornerShape(index, baseRadius = 24.dp, sharpRadius = 10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth().height(64.dp).pressScale()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        VariantBShapedAvatar(
                                            name = contact.name,
                                            photoUri = contact.photoUri,
                                            size = 48.dp
                                        )

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = contact.name,
                                                fontFamily = BricolageFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = maskedNumber,
                                                fontFamily = FigtreeFontFamily,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Surface(
                                            onClick = {
                                                val clean = com.ripple.chat.utils.MessageParser.cleanAddress(contact.number) ?: contact.number
                                                val threadId = try { android.provider.Telephony.Threads.getOrCreateThreadId(context, clean) } catch (_: Exception) { -1L }
                                                val targetId = if (threadId > 0) threadId.toString() else clean
                                                onNavigateToChat(targetId, clean, contact.name, contact.photoUri)
                                            },
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.Message,
                                                    contentDescription = "Message",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // A-Z Fast Scroll Rail
                    Column(
                        modifier = Modifier
                            .width(20.dp)
                            .fillMaxHeight()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        alphabet.forEach { letter ->
                            Text(
                                text = letter.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable {
                                        val idx = filteredContacts.indexOfFirst {
                                            it.name.startsWith(letter, ignoreCase = true)
                                        }
                                        if (idx != -1) {
                                            coroutineScope.launch { listState.scrollToItem(idx) }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun maskPhoneNumber(num: String): String {
    val clean = num.trim()
    if (clean.length < 7) return clean
    val start = clean.take(3)
    val end = clean.takeLast(2)
    return "$start•• ••$end"
}
