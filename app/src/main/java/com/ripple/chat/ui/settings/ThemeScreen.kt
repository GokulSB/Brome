package com.ripple.chat.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ripple.chat.data.ThemeMode
import com.ripple.chat.data.ThemeSettings
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val themeSettings = remember { ThemeSettings(context) }
    val bromeSettings = remember { com.ripple.chat.data.BromeSettings(context) }
    val themeConfig by themeSettings.themeConfigFlow.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    if (themeConfig == null) return // Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        // --- Top Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Theme Settings",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // --- Bottom Container ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = themeConfig!!.cornerRadius.dp, topEnd = themeConfig!!.cornerRadius.dp)),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 32.dp, horizontal = 24.dp)
            ) {
                ThemeSettingsContent(themeSettings, bromeSettings, themeConfig!!, coroutineScope)
            }
        }
    }
}

@Composable
fun ThemeSettingsContent(
    themeSettings: ThemeSettings,
    bromeSettings: com.ripple.chat.data.BromeSettings,
    themeConfig: com.ripple.chat.data.ThemeConfig,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Dynamic Color Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(themeConfig.cornerRadius.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Match wallpaper colors", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Uses Material You colors from your device wallpaper. Turn off to use Brome's default palette.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = themeConfig.useDynamicColor,
                    onCheckedChange = { checked ->
                        coroutineScope.launch { themeSettings.setUseDynamicColor(checked) }
                    }
                )
            }
        }

                Spacer(modifier = Modifier.height(24.dp))

                // RGB Circular Color Selector
                if (!themeConfig.useDynamicColor) {
                    Text("Custom App Color", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp, start = 8.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ColorWheel(
                            modifier = Modifier.size(250.dp),
                            onColorSelected = { colorInt ->
                                coroutineScope.launch { themeSettings.setCustomSeedColor(colorInt) }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Selected: ", fontWeight = FontWeight.SemiBold)
                        Box(modifier = Modifier.size(32.dp).background(Color(themeConfig.customSeedColor), CircleShape))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Theme Mode
                Text("Theme Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp, start = 8.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(themeConfig.cornerRadius.dp))
                        .padding(vertical = 8.dp)
                ) {
                    ThemeModeRow("System Default", ThemeMode.SYSTEM, themeConfig.themeMode) {
                        coroutineScope.launch { themeSettings.setThemeMode(ThemeMode.SYSTEM) }
                    }
                    ThemeModeRow("Light Mode", ThemeMode.LIGHT, themeConfig.themeMode) {
                        coroutineScope.launch { themeSettings.setThemeMode(ThemeMode.LIGHT) }
                    }
                    ThemeModeRow("Dark Mode", ThemeMode.DARK, themeConfig.themeMode) {
                        coroutineScope.launch { themeSettings.setThemeMode(ThemeMode.DARK) }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Corner Radius Slider
                Text("Corner Radius", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp, start = 8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(themeConfig.cornerRadius.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text("Adjust the roundness of containers", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = themeConfig.cornerRadius,
                        onValueChange = {
                            coroutineScope.launch {
                                themeSettings.setCornerRadius(it)
                                bromeSettings.setCornerRadius(it)
                            }
                        },
                        valueRange = 0f..48f,
                        steps = 23
                    )
                }
    }
}
// End of ThemeSettingsContent

@Composable
fun ThemeModeRow(label: String, mode: ThemeMode, currentMode: ThemeMode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        RadioButton(selected = mode == currentMode, onClick = onClick)
    }
}

@Composable
fun ColorWheel(
    modifier: Modifier = Modifier,
    onColorSelected: (Int) -> Unit
) {
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    handleTouch(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat(), onColorSelected)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    handleTouch(offset.x, offset.y, size.width.toFloat(), size.height.toFloat(), onColorSelected)
                }
            }
    ) {
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        
        val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
        drawCircle(
            brush = Brush.sweepGradient(colors, center = Offset(cx, cy)),
            radius = r,
            center = Offset(cx, cy)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = Offset(cx, cy),
                radius = r
            ),
            radius = r,
            center = Offset(cx, cy)
        )
    }
}

private fun handleTouch(x: Float, y: Float, width: Float, height: Float, onColorSelected: (Int) -> Unit) {
    val r = min(width, height) / 2f
    val cx = width / 2f
    val cy = height / 2f
    
    val dx = x - cx
    val dy = y - cy
    val d = sqrt(dx*dx + dy*dy)
    
    if (d <= r) {
        var theta = atan2(dy.toDouble(), dx.toDouble()) * 180 / Math.PI
        if (theta < 0) theta += 360
        
        val h = theta.toFloat()
        val s = (d / r)
        val v = 1f
        val hsv = floatArrayOf(h, s, v)
        val colorInt = android.graphics.Color.HSVToColor(hsv)
        onColorSelected(colorInt)
    }
}
