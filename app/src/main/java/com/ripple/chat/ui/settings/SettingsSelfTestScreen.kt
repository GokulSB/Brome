package com.ripple.chat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ripple.chat.data.BubbleThemeStyle
import com.ripple.chat.data.TypefaceStyle
import com.ripple.chat.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSelfTestScreen(
    onBack: () -> Unit
) {
    val appearance = LocalBromeAppearance.current
    val themeConfig = LocalThemeConfig.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings Self-Test", fontFamily = BricolageFontFamily, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Shapes & Corner Token Sample
            Text("1. SHAPES & CORNERS TOKEN", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Surface(
                shape = getTideCornerShape(0, baseRadius = appearance.cornerRadius.dp, keepOneSharp = appearance.keepOneSharpCorner),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Card Shape Token Preview", fontFamily = BricolageFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Corner radius: ${appearance.cornerRadius.toInt()}dp · Sharp corner: ${if (appearance.keepOneSharpCorner) "Enabled (10dp)" else "Disabled"}", fontFamily = FigtreeFontFamily, fontSize = 13.sp)
                }
            }

            // 2. Typography Token Sample
            Text("2. TYPOGRAPHY & TEXT SCALE TOKEN", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Typeface: ${appearance.typefaceStyle.name} (${appearance.textSizeScale.label} scale)", fontFamily = BricolageFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("The quick brown fox jumps over the lazy dog.", fontFamily = FigtreeFontFamily, fontSize = 14.sp)
                }
            }

            // 3. Bubble Theme Token Sample
            Text("3. BUBBLE THEME & COLOR TOKEN", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bubble Theme: ${appearance.bubbleTheme.name}", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    val isOutline = appearance.bubbleTheme == BubbleThemeStyle.OUTLINE
                    val sentColor = themeConfig?.senderBubbleColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                    val recColor = themeConfig?.receiverBubbleColor?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerHighest

                    // Received Bubble Sample
                    Box(
                        modifier = Modifier
                            .clip(
                                when (appearance.bubbleTheme) {
                                    BubbleThemeStyle.FLAT -> RoundedCornerShape(5.dp)
                                    BubbleThemeStyle.ROUND -> RoundedCornerShape(18.dp)
                                    BubbleThemeStyle.OUTLINE -> RoundedCornerShape(12.dp)
                                    else -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
                                }
                            )
                            .background(if (isOutline) Color.Transparent else recColor)
                            .then(if (isOutline) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)) else Modifier)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Received Message Sample", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    // Sent Bubble Sample
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(
                                when (appearance.bubbleTheme) {
                                    BubbleThemeStyle.FLAT -> RoundedCornerShape(5.dp)
                                    BubbleThemeStyle.ROUND -> RoundedCornerShape(18.dp)
                                    BubbleThemeStyle.OUTLINE -> RoundedCornerShape(12.dp)
                                    else -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
                                }
                            )
                            .background(if (isOutline) Color.Transparent else sentColor)
                            .then(if (isOutline) Modifier.border(1.5.dp, sentColor, RoundedCornerShape(12.dp)) else Modifier)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Sent Message Sample", fontSize = 13.sp, color = if (isOutline) sentColor else MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            // 4. Navigation Pill & Compose FAB Tokens
            Text("4. LAYOUT & NAVIGATION TOKENS", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pill Height: ${appearance.navPillHeight.heightDp}dp · FAB Size: ${appearance.fabSize.sizeDp}dp", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.height(appearance.navPillHeight.heightDp.dp * 0.7f).weight(1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Message, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        val fabCorner = (appearance.fabSize.sizeDp * 0.36f).dp
                        Surface(
                            shape = RoundedCornerShape(fabCorner),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(appearance.fabSize.sizeDp.dp * 0.7f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // 5. Search Bar Hint Token
            Text("5. SEARCH BAR HINT TOKEN", fontFamily = FigtreeFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Current search hint: \"${appearance.searchHintText}\"", fontFamily = FigtreeFontFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
