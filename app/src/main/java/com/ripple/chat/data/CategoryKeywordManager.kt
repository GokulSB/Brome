package com.ripple.chat.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object CategoryKeywordManager {
    private const val PREFS_NAME = "category_keywords_prefs"
    private const val KEY_CUSTOM_KEYWORDS = "custom_keywords_json"

    private val _keywordsVersion = MutableStateFlow(0)
    val keywordsVersion: StateFlow<Int> = _keywordsVersion.asStateFlow()

    fun notifyKeywordsChanged() {
        _keywordsVersion.value += 1
    }

    // Default text classifier keywords for each category - clean, non-clashing main defaults
    val DEFAULT_KEYWORDS: Map<String, List<String>> = mapOf(
        "OTP" to listOf(
            "otp",
            "one time password",
            "verification code",
            "login code",
            "passcode",
            "auth code"
        ),
        "FINANCE" to listOf(
            "debited",
            "credited",
            "transferred",
            "withdrawn",
            "deposited",
            "bank balance",
            "upi",
            "neft",
            "imps",
            "credit card",
            "debit card"
        ),
        "SHOPPING" to listOf(
            "order",
            "ordered",
            "shipped",
            "out for delivery",
            "delivered",
            "package",
            "shipment",
            "amazon",
            "flipkart",
            "myntra"
        ),
        "PROMOTIONS" to listOf(
            "discount",
            "promocode",
            "coupon",
            "special offer",
            "flat off",
            "mega sale",
            "flash sale",
            "cashback"
        ),
        "TICKETS" to listOf(
            "ticket",
            "tickets",
            "booking",
            "booked",
            "reservation",
            "pnr",
            "boarding pass",
            "movie ticket",
            "bus ticket",
            "train ticket",
            "flight ticket",
            "bookmyshow",
            "redbus",
            "irctc",
            "pvr",
            "showtime"
        ),
        "REMINDERS" to listOf(
            "reminder",
            "overdue",
            "appointment",
            "schedule",
            "deadline",
            "pay before"
        ),
        "SUBSCRIPTIONS" to listOf(
            "subscription",
            "subscribed",
            "renewal",
            "membership",
            "monthly plan",
            "annual plan",
            "recurring"
        ),
        "IMPORTANT" to listOf(
            "urgent",
            "important",
            "security alert",
            "account blocked",
            "suspicious activity",
            "emergency",
            "mpin"
        ),
        "SPAM" to listOf(
            "lucky draw",
            "lottery",
            "cash prize",
            "earn money",
            "work from home",
            "casino",
            "1xbet",
            "double your money",
            "crypto investment",
            "betting"
        )
    )

    fun getDefaultKeywords(category: String): List<String> {
        return DEFAULT_KEYWORDS[category.uppercase()] ?: emptyList()
    }

    fun getUserCustomKeywords(context: Context, category: String): List<String> {
        val catKey = category.uppercase()
        val map = loadStoredMap(context)
        val list = map[catKey] ?: return emptyList()
        val defaults = DEFAULT_KEYWORDS[catKey] ?: emptyList()
        return list.filter { kw -> !defaults.any { it.equals(kw, ignoreCase = true) } }
    }

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadStoredMap(context: Context): MutableMap<String, MutableList<String>> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_CUSTOM_KEYWORDS, null) ?: return mutableMapOf()
        val result = mutableMapOf<String, MutableList<String>>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val cat = keys.next()
                val array = json.getJSONArray(cat)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                result[cat.uppercase()] = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun saveStoredMap(context: Context, map: Map<String, List<String>>) {
        val prefs = getPrefs(context)
        try {
            val json = JSONObject()
            for ((cat, list) in map) {
                val array = JSONArray()
                list.forEach { array.put(it) }
                json.put(cat.uppercase(), array)
            }
            prefs.edit().putString(KEY_CUSTOM_KEYWORDS, json.toString()).apply()
            notifyKeywordsChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Checks if keyword already exists in current category.
     */
    fun existsInCurrentCategory(context: Context, category: String, keyword: String): Boolean {
        val clean = keyword.trim().lowercase()
        if (clean.isBlank()) return false
        val current = getKeywords(context, category)
        return current.any { it.equals(clean, ignoreCase = true) }
    }

    /**
     * Finds if keyword matches any OTHER category's trigger words. Returns the conflicting category name if found.
     */
    fun findConflictCategory(context: Context, targetCategory: String, keyword: String): String? {
        val clean = keyword.trim().lowercase()
        if (clean.isBlank()) return null
        val targetUpper = targetCategory.uppercase()
        val all = getAllKeywords(context)
        for ((cat, list) in all) {
            if (cat.uppercase() != targetUpper && list.any { it.equals(clean, ignoreCase = true) }) {
                return cat
            }
        }
        return null
    }

    /**
     * Returns current active keywords for a given category (default keywords unless overridden by user).
     */
    fun getKeywords(context: Context, category: String): List<String> {
        val catKey = category.uppercase()
        val map = loadStoredMap(context)
        return map[catKey] ?: DEFAULT_KEYWORDS[catKey] ?: emptyList()
    }

    /**
     * Adds a keyword to a category. Returns true if added.
     */
    fun addKeyword(context: Context, category: String, keyword: String): Boolean {
        val clean = keyword.trim().lowercase()
        if (clean.isBlank()) return false

        val catKey = category.uppercase()
        val map = loadStoredMap(context)
        val currentList = (map[catKey] ?: DEFAULT_KEYWORDS[catKey] ?: emptyList()).toMutableList()

        if (currentList.any { it.equals(clean, ignoreCase = true) }) {
            return false // Already exists
        }

        currentList.add(clean)
        map[catKey] = currentList
        saveStoredMap(context, map)
        return true
    }

    /**
     * Removes a keyword from a category.
     */
    fun removeKeyword(context: Context, category: String, keyword: String): Boolean {
        val clean = keyword.trim().lowercase()
        val catKey = category.uppercase()
        val map = loadStoredMap(context)
        val currentList = (map[catKey] ?: DEFAULT_KEYWORDS[catKey] ?: emptyList()).toMutableList()

        val removed = currentList.removeAll { it.equals(clean, ignoreCase = true) }
        if (removed) {
            map[catKey] = currentList
            saveStoredMap(context, map)
        }
        return removed
    }

    /**
     * Resets a category's keywords to the default.
     */
    fun resetKeywords(context: Context, category: String) {
        val catKey = category.uppercase()
        val map = loadStoredMap(context)
        map.remove(catKey)
        saveStoredMap(context, map)
    }

    /**
     * Registers a custom category with its initial keywords.
     */
    fun registerCustomCategory(context: Context, categoryKey: String, initialKeywords: List<String>) {
        val catKey = categoryKey.uppercase()
        val map = loadStoredMap(context)
        val list = (map[catKey] ?: emptyList()).toMutableList()
        initialKeywords.forEach { kw ->
            val clean = kw.trim().lowercase()
            if (clean.isNotBlank() && !list.any { it.equals(clean, ignoreCase = true) }) {
                list.add(clean)
            }
        }
        map[catKey] = list
        saveStoredMap(context, map)
    }

    /**
     * Deletes a custom category and its associated keywords.
     */
    fun deleteCustomCategory(context: Context, categoryKey: String) {
        val catKey = categoryKey.uppercase()
        val map = loadStoredMap(context)
        map.remove(catKey)
        saveStoredMap(context, map)
    }

    /**
     * Returns all active keywords for all categories (including custom categories).
     */
    fun getAllKeywords(context: Context): Map<String, List<String>> {
        val map = loadStoredMap(context)
        val result = mutableMapOf<String, List<String>>()
        DEFAULT_KEYWORDS.keys.forEach { catKey ->
            result[catKey] = map[catKey] ?: DEFAULT_KEYWORDS[catKey] ?: emptyList()
        }
        map.keys.forEach { customKey ->
            if (!result.containsKey(customKey)) {
                result[customKey] = map[customKey] ?: emptyList()
            }
        }
        return result
    }
}
