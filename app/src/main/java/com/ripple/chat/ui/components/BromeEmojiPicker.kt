package com.ripple.chat.ui.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ripple.chat.theme.LocalBromeAppearance

enum class EmojiCategory(val label: String, val icon: ImageVector) {
    RECENT("Recent", Icons.Rounded.Schedule),
    SMILEYS("Smileys", Icons.Rounded.SentimentSatisfiedAlt),
    PEOPLE("People", Icons.Rounded.People),
    ANIMALS("Nature", Icons.Rounded.Pets),
    FOOD("Food", Icons.Rounded.Restaurant),
    ACTIVITIES("Activities", Icons.Rounded.Celebration),
    SYMBOLS("Symbols", Icons.Rounded.Favorite)
}

object EmojiData {
    val DEFAULT_RECENTS = listOf(
        "❤️", "👍", "😂", "🔥", "🙏", "😊", "😍", "🎉",
        "✨", "👏", "🙌", "😎", "🥳", "🥺", "💯", "💖",
        "🤣", "😉", "🤔", "😘", "😁", "🥰", "💪", "🚀"
    )

    val SMILEYS = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
        "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
        "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪",
        "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨",
        "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥",
        "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
        "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯",
        "🤠", "🥳", "🥸", "😎", "🤓", "🧐", "😕", "😟",
        "🙁", "😮", "😯", "😲", "😳", "🥺", "😦", "😧",
        "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣",
        "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠",
        "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👻",
        "👽", "👾", "🤖"
    )

    val PEOPLE = listOf(
        "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏",
        "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆",
        "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛",
        "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️",
        "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂",
        "🦻", "👃", "🧠", "🫀", "🫁", "🦷", "🦴", "👀",
        "👁️", "👅", "👄", "👶", "🧒", "👦", "👧", "🧑",
        "👱", "👨", "🧔", "👩", "🧓", "👴", "👵", "👮",
        "🕵️", "💂", "👷", "🤴", "👸", "👳", "👲", "🧕"
    )

    val ANIMALS = listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
        "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵",
        "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤",
        "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗",
        "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜",
        "🦟", "🦗", "🕷️", "🦂", "🐢", "🐍", "🦎", "🦖",
        "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠",
        "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆",
        "🦓", "🦍", "🦧", "🦣", "🐘", "🦛", "🦏", "🐪",
        "🌸", "💮", "🏵️", "🌹", "🥀", "🌺", "🌻", "🌼",
        "🌷", "🌱", "🪴", "🌲", "🌳", "🌴", "🌵", "🌾",
        "🌿", "☘️", "🍀", "🍁", "🍂", "🍃", "🍄", "🌰"
    )

    val FOOD = listOf(
        "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇",
        "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥",
        "🥝", "🍅", "🥑", "🥦", "🥬", "🥒", "🌶️", "🌽",
        "🥕", "🧄", "🧅", "🥔", "🍠", "🥐", "🥯", "🍞",
        "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇",
        "🥓", "🥩", "🍗", "🍖", "🌭", "🍔", "🍟", "🍕",
        "🥪", "🥙", "🌮", "🌯", "🥗", "🥘", "🍝", "🍜",
        "🍲", "🍛", "🍣", "🍱", "🥟", "🦪", "🍤", "🍙",
        "🍚", "🍘", "🍢", "🥮", "🍡", "🍧", "🍨", "🍦",
        "🥧", "🧁", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫",
        "🍿", "🍩", "🍪", "☕", "🍵", "🧃", "🥤", "🧋",
        "🍶", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹"
    )

    val ACTIVITIES = listOf(
        "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
        "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
        "🏏", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊",
        "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️", "🥌", "🎿",
        "⛷️", "🏂", "🏋️", "🤼", "🤸", "⛹️", "🤺", "🤾",
        "🏌️", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣", "🧗",
        "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️",
        "🎪", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁",
        "🎷", "🎺", "🎸", "🪕", "🎻", "🎲", "🎯", "🎳",
        "🎮", "🎰", "🧩", "🎉", "🎊", "🎈", "🎁", "🎂"
    )

    val SYMBOLS = listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
        "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
        "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️",
        "✡️", "🔯", "🕎", "☯️", "☦️", "🛐", "⛎", "♈",
        "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐",
        "♑", "♒", "♓", "🆔", "⚛️", "✴️", "❇️", "‼️",
        "⁉️", "❓", "❔", "❕", "❗", "💤", "💥", "💯",
        "🔥", "✨", "🌟", "⭐", "⚡", "☀️", "🌙", "☁️"
    )

    fun getEmojis(category: EmojiCategory, recents: List<String>): List<String> {
        return when (category) {
            EmojiCategory.RECENT -> recents
            EmojiCategory.SMILEYS -> SMILEYS
            EmojiCategory.PEOPLE -> PEOPLE
            EmojiCategory.ANIMALS -> ANIMALS
            EmojiCategory.FOOD -> FOOD
            EmojiCategory.ACTIVITIES -> ACTIVITIES
            EmojiCategory.SYMBOLS -> SYMBOLS
        }
    }
}

@Composable
fun BromeEmojiPicker(
    onEmojiSelect: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appearance = LocalBromeAppearance.current
    val prefs = remember { context.getSharedPreferences("brome_emoji_prefs", Context.MODE_PRIVATE) }

    var recentEmojis by remember {
        val saved = prefs.getString("recent_emojis", null)
        val list = if (!saved.isNullOrBlank()) {
            saved.split(",").filter { it.isNotBlank() }
        } else {
            EmojiData.DEFAULT_RECENTS
        }
        mutableStateOf(list)
    }

    var selectedCategory by remember { mutableStateOf(EmojiCategory.RECENT) }

    val onSelect: (String) -> Unit = { emoji ->
        onEmojiSelect(emoji)
        val updated = (listOf(emoji) + recentEmojis.filter { it != emoji }).take(32)
        recentEmojis = updated
        prefs.edit().putString("recent_emojis", updated.joinToString(",")).apply()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(270.dp),
        shape = RoundedCornerShape(topStart = appearance.cornerRadius.dp, topEnd = appearance.cornerRadius.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EmojiCategory.values().forEach { cat ->
                        val isSelected = cat == selectedCategory
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = cat.label,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Backspace button
                Surface(
                    onClick = onBackspace,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = "Backspace",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 1.dp
            )

            // Current Category Label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedCategory.label.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
            }

            // Emoji Grid
            val emojis = remember(selectedCategory, recentEmojis) {
                EmojiData.getEmojis(selectedCategory, recentEmojis)
            }

            if (emojis.isEmpty() && selectedCategory == EmojiCategory.RECENT) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent emojis yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(emojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
