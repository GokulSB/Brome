package com.ripple.chat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class CustomCategory(
    val id: String,
    val name: String,
    val color: Int,
    val iconName: String,
    val defaultKeywords: List<String> = emptyList()
)

data class ThemeConfig(
    val useDynamicColor: Boolean,
    val customSeedColor: Int,
    val themeMode: ThemeMode,
    val cornerRadius: Float,
    val enableCategorization: Boolean,
    val enabledCategories: Set<String>,
    val senderBubbleColor: Int?,
    val receiverBubbleColor: Int?,
    val categoryColors: Map<String, Int>,
    val categoryOrder: List<String> = ThemeSettings.DEFAULT_CATEGORY_ORDER,
    val customCategories: List<CustomCategory> = emptyList()
)

class ThemeSettings(private val context: Context) {
    companion object {
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        val CUSTOM_SEED_COLOR = intPreferencesKey("custom_seed_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CORNER_RADIUS = floatPreferencesKey("corner_radius")
        val ENABLE_CATEGORIZATION = booleanPreferencesKey("enable_categorization")
        val ENABLED_CATEGORIES = stringSetPreferencesKey("enabled_categories")
        val SENDER_BUBBLE_COLOR = intPreferencesKey("sender_bubble_color")
        val RECEIVER_BUBBLE_COLOR = intPreferencesKey("receiver_bubble_color")
        val CATEGORY_COLORS = stringPreferencesKey("category_colors")
        val CATEGORY_ORDER = stringPreferencesKey("category_order")
        val CUSTOM_CATEGORIES = stringPreferencesKey("custom_categories")
        
        // Default brand color: Blue
        const val DEFAULT_COLOR = 0xFF2196F3.toInt()
        const val DEFAULT_CORNER_RADIUS = 24f
        val DEFAULT_CATEGORIES = setOf("UNREAD", "PERSONAL", "FINANCE", "PROMOTIONS", "OTP", "TICKETS", "REMINDERS", "SUBSCRIPTIONS", "SHOPPING", "IMPORTANT", "SPAM")
        val DEFAULT_CATEGORY_ORDER = listOf("UNREAD", "PERSONAL", "OTP", "FINANCE", "PROMOTIONS", "SHOPPING", "TICKETS", "REMINDERS", "SUBSCRIPTIONS", "IMPORTANT", "SPAM")

        // Fixed, non-dynamic colors for categories that fall outside the 4 M3
        // roles (primary/secondary/tertiary/error already cover Personal/Banking/Flagged).
        // These stay constant regardless of wallpaper seed, same principle as `error`.
        // User overrides from CATEGORY_COLORS always win over these.
        val DEFAULT_CATEGORY_COLORS = mapOf(
            "PERSONAL" to 0xFF4CAF50.toInt(),      // green
            "OTP" to 0xFF00ACC1.toInt(),           // cyan
            "SHOPPING" to 0xFF4DD8C4.toInt(),      // teal
            "TICKETS" to 0xFFC792EA.toInt(),       // lavender
            "REMINDERS" to 0xFFFFB03A.toInt(),     // amber-orange
            "SUBSCRIPTIONS" to 0xFF5B9DFF.toInt(), // sky blue
            "IMPORTANT" to 0xFFFF5722.toInt(),     // deep orange
            "SPAM" to 0xFFE53935.toInt(),          // red
            "OTHERS" to 0xFF78909C.toInt()         // blue grey
        )
    }

    val themeConfigFlow: Flow<ThemeConfig> = context.dataStore.data
        .map { preferences ->
            val useDynamic = preferences[USE_DYNAMIC_COLOR] ?: true
            val seedColor = preferences[CUSTOM_SEED_COLOR] ?: DEFAULT_COLOR
            val modeStr = preferences[THEME_MODE] ?: ThemeMode.SYSTEM.name
            val mode = try { ThemeMode.valueOf(modeStr) } catch (e: Exception) { ThemeMode.SYSTEM }
            val radius = preferences[CORNER_RADIUS] ?: DEFAULT_CORNER_RADIUS
            val categorization = preferences[ENABLE_CATEGORIZATION] ?: true
            val savedCategories = preferences[ENABLED_CATEGORIES]
            val categories = if (savedCategories != null) {
                if (!savedCategories.contains("PERSONAL") && !preferences.contains(booleanPreferencesKey("migrated_personal_category"))) {
                    savedCategories + "PERSONAL"
                } else {
                    savedCategories
                }
            } else {
                DEFAULT_CATEGORIES
            }
            
            // If the preference is not set (null), we will let the UI fall back to the default material colors
            val senderColor = preferences[SENDER_BUBBLE_COLOR]
            val receiverColor = preferences[RECEIVER_BUBBLE_COLOR]
            
            val colorsStr = preferences[CATEGORY_COLORS] ?: "{}"
            val colorsJson = try { JSONObject(colorsStr) } catch (e: Exception) { JSONObject() }
            // Start from fixed defaults, then let any user-saved override win.
            val catColors = DEFAULT_CATEGORY_COLORS.toMutableMap()
            colorsJson.keys().forEach { key ->
                catColors[key] = colorsJson.getInt(key)
            }

            val customCatsStr = preferences[CUSTOM_CATEGORIES] ?: "[]"
            val customCats = try {
                val arr = org.json.JSONArray(customCatsStr)
                val list = mutableListOf<CustomCategory>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val kwArr = obj.optJSONArray("keywords") ?: org.json.JSONArray()
                    val kwList = mutableListOf<String>()
                    for (j in 0 until kwArr.length()) {
                        kwList.add(kwArr.getString(j))
                    }
                    list.add(
                        CustomCategory(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            color = obj.getInt("color"),
                            iconName = obj.optString("icon", "Category"),
                            defaultKeywords = kwList
                        )
                    )
                }
                list
            } catch (e: Exception) {
                emptyList<CustomCategory>()
            }

            val orderStr = preferences[CATEGORY_ORDER]
            val storedOrder = if (orderStr != null) {
                try {
                    val arr = org.json.JSONArray(orderStr)
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    list
                } catch (e: Exception) {
                    DEFAULT_CATEGORY_ORDER
                }
            } else {
                DEFAULT_CATEGORY_ORDER
            }

            val finalOrder = storedOrder.toMutableList()
            DEFAULT_CATEGORY_ORDER.forEach { cat ->
                if (!finalOrder.contains(cat)) {
                    finalOrder.add(cat)
                }
            }
            customCats.forEach { cat ->
                if (!finalOrder.contains(cat.id)) {
                    finalOrder.add(cat.id)
                }
            }

            // Ensure custom category colors are in catColors
            customCats.forEach { cat ->
                if (!catColors.containsKey(cat.id)) {
                    catColors[cat.id] = cat.color
                }
            }
            
            ThemeConfig(
                useDynamicColor = useDynamic,
                customSeedColor = seedColor,
                themeMode = mode,
                cornerRadius = radius,
                enableCategorization = categorization,
                enabledCategories = categories,
                senderBubbleColor = senderColor,
                receiverBubbleColor = receiverColor,
                categoryColors = catColors,
                categoryOrder = finalOrder,
                customCategories = customCats
            )
        }

    suspend fun setEnabledCategories(categories: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[ENABLED_CATEGORIES] = categories
        }
    }

    suspend fun setUseDynamicColor(useDynamic: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_DYNAMIC_COLOR] = useDynamic
        }
    }

    suspend fun setCustomSeedColor(color: Int) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_SEED_COLOR] = color
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

    suspend fun setCornerRadius(radius: Float) {
        context.dataStore.edit { preferences ->
            preferences[CORNER_RADIUS] = radius
        }
    }

    suspend fun setEnableCategorization(enable: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_CATEGORIZATION] = enable
        }
    }
    
    suspend fun setSenderBubbleColor(color: Int?) {
        context.dataStore.edit { preferences ->
            if (color == null) preferences.remove(SENDER_BUBBLE_COLOR)
            else preferences[SENDER_BUBBLE_COLOR] = color
        }
    }

    suspend fun setReceiverBubbleColor(color: Int?) {
        context.dataStore.edit { preferences ->
            if (color == null) preferences.remove(RECEIVER_BUBBLE_COLOR)
            else preferences[RECEIVER_BUBBLE_COLOR] = color
        }
    }

    suspend fun setCategoryColor(category: String, colorInt: Int?) {
        context.dataStore.edit { preferences ->
            val currentStr = preferences[CATEGORY_COLORS] ?: "{}"
            val json = try { JSONObject(currentStr) } catch(e: Exception) { JSONObject() }
            if (colorInt == null) {
                json.remove(category)
            } else {
                json.put(category, colorInt)
            }
            preferences[CATEGORY_COLORS] = json.toString()
        }
    }

    suspend fun setCategoryOrder(order: List<String>) {
        context.dataStore.edit { preferences ->
            val arr = org.json.JSONArray()
            order.forEach { arr.put(it) }
            preferences[CATEGORY_ORDER] = arr.toString()
        }
    }

    suspend fun moveCategory(fromIndex: Int, toIndex: Int) {
        context.dataStore.edit { preferences ->
            val orderStr = preferences[CATEGORY_ORDER]
            val list = if (orderStr != null) {
                try {
                    val arr = org.json.JSONArray(orderStr)
                    MutableList(arr.length()) { arr.getString(it) }
                } catch (e: Exception) {
                    DEFAULT_CATEGORY_ORDER.toMutableList()
                }
            } else {
                DEFAULT_CATEGORY_ORDER.toMutableList()
            }
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                val arr = org.json.JSONArray()
                list.forEach { arr.put(it) }
                preferences[CATEGORY_ORDER] = arr.toString()
            }
        }
    }

    suspend fun addCustomCategory(cat: CustomCategory) {
        context.dataStore.edit { preferences ->
            // 1. Add to custom_categories
            val currentStr = preferences[CUSTOM_CATEGORIES] ?: "[]"
            val arr = try { org.json.JSONArray(currentStr) } catch (e: Exception) { org.json.JSONArray() }
            val obj = org.json.JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("color", cat.color)
                put("icon", cat.iconName)
                val kwArr = org.json.JSONArray()
                cat.defaultKeywords.forEach { kwArr.put(it) }
                put("keywords", kwArr)
            }
            arr.put(obj)
            preferences[CUSTOM_CATEGORIES] = arr.toString()

            // 2. Add to category_order
            val orderStr = preferences[CATEGORY_ORDER]
            val orderList = if (orderStr != null) {
                try {
                    val orderArr = org.json.JSONArray(orderStr)
                    MutableList(orderArr.length()) { orderArr.getString(it) }
                } catch (e: Exception) {
                    DEFAULT_CATEGORY_ORDER.toMutableList()
                }
            } else {
                DEFAULT_CATEGORY_ORDER.toMutableList()
            }
            if (!orderList.contains(cat.id)) {
                orderList.add(cat.id)
                val newOrderArr = org.json.JSONArray()
                orderList.forEach { newOrderArr.put(it) }
                preferences[CATEGORY_ORDER] = newOrderArr.toString()
            }

            // 3. Add to enabled_categories
            val currentEnabled = preferences[ENABLED_CATEGORIES] ?: DEFAULT_CATEGORIES
            preferences[ENABLED_CATEGORIES] = currentEnabled + cat.id

            // 4. Add to category_colors
            val colorsStr = preferences[CATEGORY_COLORS] ?: "{}"
            val colorsJson = try { JSONObject(colorsStr) } catch(e: Exception) { JSONObject() }
            colorsJson.put(cat.id, cat.color)
            preferences[CATEGORY_COLORS] = colorsJson.toString()
        }
        CategoryKeywordManager.registerCustomCategory(context, cat.id, cat.defaultKeywords)
    }

    suspend fun removeCustomCategory(categoryId: String) {
        context.dataStore.edit { preferences ->
            // 1. Remove from custom_categories
            val currentStr = preferences[CUSTOM_CATEGORIES] ?: "[]"
            try {
                val arr = org.json.JSONArray(currentStr)
                val newArr = org.json.JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getString("id") != categoryId) {
                        newArr.put(obj)
                    }
                }
                preferences[CUSTOM_CATEGORIES] = newArr.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Remove from category_order
            val orderStr = preferences[CATEGORY_ORDER]
            if (orderStr != null) {
                try {
                    val orderArr = org.json.JSONArray(orderStr)
                    val newOrderArr = org.json.JSONArray()
                    for (i in 0 until orderArr.length()) {
                        val k = orderArr.getString(i)
                        if (k != categoryId) {
                            newOrderArr.put(k)
                        }
                    }
                    preferences[CATEGORY_ORDER] = newOrderArr.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. Remove from enabled_categories
            val currentEnabled = preferences[ENABLED_CATEGORIES] ?: DEFAULT_CATEGORIES
            preferences[ENABLED_CATEGORIES] = currentEnabled - categoryId

            // 4. Remove from category_colors
            val colorsStr = preferences[CATEGORY_COLORS] ?: "{}"
            try {
                val colorsJson = JSONObject(colorsStr)
                colorsJson.remove(categoryId)
                preferences[CATEGORY_COLORS] = colorsJson.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        CategoryKeywordManager.deleteCustomCategory(context, categoryId)
    }
}
