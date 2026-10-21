package com.ripple.chat.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.random.Random

val Context.bromeDataStore: DataStore<Preferences> by preferencesDataStore(name = "brome_settings")

enum class BubbleThemeStyle { TIDE, ROUND, FLAT, OUTLINE }
enum class TypefaceStyle { TIDE, ROUNDED, CLASSIC, SERIF, MONO, SYSTEM }
enum class TextSizeScale(val scale: Float, val label: String) { SMALL(0.9f, "Small"), DEFAULT(1.0f, "Default"), LARGE(1.15f, "Large") }

enum class BackgroundDesign(val label: String) {
    SHARDS("Shards"),
    BLOBS("Blobs"),
    WAVES("Waves"),
    SHAPES("Shapes"),
    CONTOURS("Contours"),
    DOTS("Dots"),
    MIST("Mist"),
    PLAIN("Plain"),
    AMOLED("AMOLED")
}

enum class GradientWashLevel(val label: String, val topAlpha: Float, val bottomAlpha: Float) {
    OFF("Off", 0.0f, 0.0f),
    MINOR("Minor", 0.07f, 0.05f),
    SOFT("Soft", 0.15f, 0.11f)
}

enum class NavPillHeight(val heightDp: Int) { COMPACT(49), DEFAULT(49), LARGE(64) }
enum class FabSize(val sizeDp: Int) { SMALL(47), DEFAULT(47), LARGE(62) }
enum class OtpAutoDeleteTime(val hours: Long, val label: String) { HOUR_1(1, "1 hour"), HOURS_6(6, "6 hours"), HOURS_24(24, "24 hours"), DAYS_3(72, "3 days"), NEVER(-1, "Never") }
enum class SpamAutoDeleteTime(val days: Long, val label: String) { DAY_1(1, "1 day"), DAYS_7(7, "7 days"), DAYS_30(30, "30 days"), NEVER(-1, "Never") }
enum class TrashAutoDeleteTime(val days: Long, val label: String) { DAYS_7(7, "7 days"), DAYS_15(15, "15 days"), DAYS_30(30, "30 days"), DAYS_60(60, "60 days") }

data class BromeAppearance(
    val bubbleTheme: BubbleThemeStyle = BubbleThemeStyle.TIDE,
    val typefaceStyle: TypefaceStyle = TypefaceStyle.TIDE,
    val textSizeScale: TextSizeScale = TextSizeScale.DEFAULT,
    val cornerRadius: Float = 30f,
    val keepOneSharpCorner: Boolean = true,
    val backgroundDesign: BackgroundDesign = BackgroundDesign.SHARDS,
    val backgroundSeed: Int = 4821,
    val gradientWashLevel: GradientWashLevel = GradientWashLevel.MINOR,
    val navPillHeight: NavPillHeight = NavPillHeight.DEFAULT,
    val fabSize: FabSize = FabSize.DEFAULT,
    val navPillHeightDp: Int = 49,
    val fabSizeDp: Int = 47,
    val searchHintText: String = "Search messages",
    val otpAutoDelete: OtpAutoDeleteTime = OtpAutoDeleteTime.HOURS_24,
    val spamAutoDelete: SpamAutoDeleteTime = SpamAutoDeleteTime.DAYS_7,
    val trashAutoDelete: TrashAutoDeleteTime = TrashAutoDeleteTime.DAYS_30,
    val languageCode: String = "system",
    val categoryGridColumns: Int = 2,
    val categoryGridRows: Int = 2,
    val selectionBarScale: Float = 1.0f
)

enum class IslandPeekLeft(val label: String) {
    AVATAR("Avatar"),
    ICON("Category icon"),
    NOTHING("Nothing")
}

enum class IslandPeekText(val label: String) {
    SMART("Smart"),
    SENDER("Sender"),
    PREVIEW("Preview"),
    CATEGORY("Category"),
    NOTHING("Nothing")
}

enum class IslandSound(val label: String) {
    USE_MESSAGE_SOUND("Use message sound"),
    SILENT("Silent");

    companion object {
        val UseMessageSound = USE_MESSAGE_SOUND
        val Silent = SILENT
    }
}

typealias IslandSettings = DynamicIslandSettings

data class DynamicIslandSettings(
    val enabled: Boolean = false,
    val showFor: Set<String> = setOf("MESSAGES", "PERSONAL", "OTP", "FINANCE"),
    val duration: Int = 5,
    val size: String = "DEFAULT",
    val position: String = "CAMERA",
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val peekLeft: IslandPeekLeft = IslandPeekLeft.AVATAR,
    val peekText: IslandPeekText = IslandPeekText.SMART,
    val peekMaxChars: Int = 20,
    val expAvatar: Boolean = true,
    val expTag: Boolean = true,
    val expTime: Boolean = true,
    val expText: Boolean = true,
    val expOtp: Boolean = true,
    val expTxn: Boolean = true,
    val expLines: Int = 2,
    val actReply: Boolean = true,
    val actCopy: Boolean = true,
    val actRead: Boolean = true,
    val actExpand: Boolean = true,
    val hideContent: Boolean = false,
    val skipWhenOpen: Boolean = true,
    val respectDnd: Boolean = true,
    val keepSilentCopy: Boolean = false,
    val sound: IslandSound = IslandSound.USE_MESSAGE_SOUND
)

class BromeSettings(private val context: Context) {
    private object K {
        val BUBBLE_THEME = stringPreferencesKey("brome_bubble_theme")
        val TYPEFACE_STYLE = stringPreferencesKey("brome_typeface_style")
        val TEXT_SIZE_SCALE = stringPreferencesKey("brome_text_size_scale")
        val CORNER_RADIUS = floatPreferencesKey("brome_corner_radius")
        val KEEP_ONE_SHARP_CORNER = booleanPreferencesKey("brome_keep_one_sharp_corner")
        val BACKGROUND_DESIGN = stringPreferencesKey("brome_background_design")
        val BACKGROUND_SEED = intPreferencesKey("brome_background_seed")
        val GRADIENT_WASH_LEVEL = stringPreferencesKey("brome_gradient_wash_level")
        val NAV_PILL_HEIGHT = stringPreferencesKey("brome_nav_pill_height")
        val FAB_SIZE = stringPreferencesKey("brome_fab_size")
        val NAV_PILL_HEIGHT_DP = intPreferencesKey("brome_nav_pill_height_dp")
        val FAB_SIZE_DP = intPreferencesKey("brome_fab_size_dp")
        val SEARCH_HINT_TEXT = stringPreferencesKey("brome_search_hint_text")
        val OTP_AUTO_DELETE = stringPreferencesKey("brome_otp_auto_delete")
        val SPAM_AUTO_DELETE = stringPreferencesKey("brome_spam_auto_delete")
        val TRASH_AUTO_DELETE = stringPreferencesKey("brome_trash_auto_delete")
        val LANGUAGE_CODE = stringPreferencesKey("brome_language_code")
        val CATEGORY_GRID_COLUMNS = intPreferencesKey("category_grid_columns")
        val CATEGORY_GRID_ROWS = intPreferencesKey("category_grid_rows")
        val SELECTION_BAR_SCALE = floatPreferencesKey("brome_selection_bar_scale")

        // Dynamic Island Preferences
        val ISLAND_ENABLED = booleanPreferencesKey("island_enabled")
        val ISLAND_SHOW_FOR = stringSetPreferencesKey("island_show_for")
        val ISLAND_DURATION = intPreferencesKey("island_duration")
        val ISLAND_SIZE = stringPreferencesKey("island_size")
        val ISLAND_POSITION = stringPreferencesKey("island_position")
        val ISLAND_OFFSET_X = intPreferencesKey("island_offset_x_dp")
        val ISLAND_OFFSET_Y = intPreferencesKey("island_offset_y_dp")
        val ISLAND_PEEK_LEFT = stringPreferencesKey("island_peek_left")
        val ISLAND_PEEK_TEXT = stringPreferencesKey("island_peek_text")
        val ISLAND_PEEK_MAX_CHARS = intPreferencesKey("island_peek_max_chars")
        val ISLAND_EXP_AVATAR = booleanPreferencesKey("island_exp_avatar")
        val ISLAND_EXP_TAG = booleanPreferencesKey("island_exp_tag")
        val ISLAND_EXP_TIME = booleanPreferencesKey("island_exp_time")
        val ISLAND_EXP_TEXT = booleanPreferencesKey("island_exp_text")
        val ISLAND_EXP_OTP = booleanPreferencesKey("island_exp_otp")
        val ISLAND_EXP_TXN = booleanPreferencesKey("island_exp_txn")
        val ISLAND_EXP_LINES = intPreferencesKey("island_exp_lines")
        val ISLAND_ACT_REPLY = booleanPreferencesKey("island_act_reply")
        val ISLAND_ACT_COPY = booleanPreferencesKey("island_act_copy")
        val ISLAND_ACT_READ = booleanPreferencesKey("island_act_read")
        val ISLAND_ACT_EXPAND = booleanPreferencesKey("island_act_expand")
        val ISLAND_HIDE_CONTENT = booleanPreferencesKey("island_hide_content")
        val ISLAND_SKIP_WHEN_OPEN = booleanPreferencesKey("island_skip_when_open")
        val ISLAND_RESPECT_DND = booleanPreferencesKey("island_respect_dnd")
        val ISLAND_KEEP_SILENT_COPY = booleanPreferencesKey("island_keep_silent_copy")
        val ISLAND_SOUND = stringPreferencesKey("island_sound")
    }

    val appearanceFlow: Flow<BromeAppearance> = context.bromeDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val navEnum = tryEnum(prefs[K.NAV_PILL_HEIGHT], NavPillHeight.DEFAULT)
            val fabEnum = tryEnum(prefs[K.FAB_SIZE], FabSize.DEFAULT)
            val navDp = prefs[K.NAV_PILL_HEIGHT_DP] ?: 49
            val fabDp = prefs[K.FAB_SIZE_DP] ?: 47

            BromeAppearance(
                bubbleTheme = tryEnum(prefs[K.BUBBLE_THEME], BubbleThemeStyle.TIDE),
                typefaceStyle = tryEnum(prefs[K.TYPEFACE_STYLE], TypefaceStyle.TIDE),
                textSizeScale = tryEnum(prefs[K.TEXT_SIZE_SCALE], TextSizeScale.DEFAULT),
                cornerRadius = prefs[K.CORNER_RADIUS] ?: 30f,
                keepOneSharpCorner = prefs[K.KEEP_ONE_SHARP_CORNER] ?: true,
                backgroundDesign = tryEnum(prefs[K.BACKGROUND_DESIGN], BackgroundDesign.SHARDS),
                backgroundSeed = prefs[K.BACKGROUND_SEED] ?: 4821,
                gradientWashLevel = tryEnum(prefs[K.GRADIENT_WASH_LEVEL], GradientWashLevel.MINOR),
                navPillHeight = navEnum,
                fabSize = fabEnum,
                navPillHeightDp = navDp,
                fabSizeDp = fabDp,
                searchHintText = prefs[K.SEARCH_HINT_TEXT] ?: "Search messages",
                otpAutoDelete = tryEnum(prefs[K.OTP_AUTO_DELETE], OtpAutoDeleteTime.HOURS_24),
                spamAutoDelete = tryEnum(prefs[K.SPAM_AUTO_DELETE], SpamAutoDeleteTime.DAYS_7),
                trashAutoDelete = tryEnum(prefs[K.TRASH_AUTO_DELETE], TrashAutoDeleteTime.DAYS_30),
                languageCode = prefs[K.LANGUAGE_CODE] ?: "system",
                categoryGridColumns = (prefs[K.CATEGORY_GRID_COLUMNS] ?: 2).coerceIn(2, 4),
                categoryGridRows = (prefs[K.CATEGORY_GRID_ROWS] ?: 2).coerceIn(1, 3),
                selectionBarScale = (prefs[K.SELECTION_BAR_SCALE] ?: 1.0f).coerceIn(0.70f, 1.0f)
            ).also { Log.d("BromeSettings", "emit appearance=$it") }
        }
        .distinctUntilChanged()

    val islandSettingsFlow: Flow<DynamicIslandSettings> = context.bromeDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val defaultPeekText = if (prefs[K.ISLAND_PEEK_TEXT] == null && prefs[K.ISLAND_ENABLED] == true) {
                IslandPeekText.SENDER
            } else {
                tryEnum(prefs[K.ISLAND_PEEK_TEXT], IslandPeekText.SMART)
            }

            DynamicIslandSettings(
                enabled = prefs[K.ISLAND_ENABLED] ?: false,
                showFor = prefs[K.ISLAND_SHOW_FOR] ?: setOf("MESSAGES", "PERSONAL", "OTP", "FINANCE"),
                duration = prefs[K.ISLAND_DURATION] ?: 5,
                size = prefs[K.ISLAND_SIZE] ?: "DEFAULT",
                position = prefs[K.ISLAND_POSITION] ?: "CAMERA",
                offsetX = prefs[K.ISLAND_OFFSET_X] ?: 0,
                offsetY = prefs[K.ISLAND_OFFSET_Y] ?: 0,
                peekLeft = tryEnum(prefs[K.ISLAND_PEEK_LEFT], IslandPeekLeft.AVATAR),
                peekText = defaultPeekText,
                peekMaxChars = prefs[K.ISLAND_PEEK_MAX_CHARS] ?: 20,
                expAvatar = prefs[K.ISLAND_EXP_AVATAR] ?: true,
                expTag = prefs[K.ISLAND_EXP_TAG] ?: true,
                expTime = prefs[K.ISLAND_EXP_TIME] ?: true,
                expText = prefs[K.ISLAND_EXP_TEXT] ?: true,
                expOtp = prefs[K.ISLAND_EXP_OTP] ?: true,
                expTxn = prefs[K.ISLAND_EXP_TXN] ?: true,
                expLines = prefs[K.ISLAND_EXP_LINES] ?: 2,
                actReply = prefs[K.ISLAND_ACT_REPLY] ?: true,
                actCopy = prefs[K.ISLAND_ACT_COPY] ?: true,
                actRead = prefs[K.ISLAND_ACT_READ] ?: true,
                actExpand = prefs[K.ISLAND_ACT_EXPAND] ?: true,
                hideContent = prefs[K.ISLAND_HIDE_CONTENT] ?: false,
                skipWhenOpen = prefs[K.ISLAND_SKIP_WHEN_OPEN] ?: true,
                respectDnd = prefs[K.ISLAND_RESPECT_DND] ?: true,
                keepSilentCopy = prefs[K.ISLAND_KEEP_SILENT_COPY] ?: false,
                sound = tryEnum(prefs[K.ISLAND_SOUND], IslandSound.USE_MESSAGE_SOUND)
            )
        }
        .distinctUntilChanged()

    suspend fun setIslandEnabled(enabled: Boolean) = write("island_enabled", enabled) { it[K.ISLAND_ENABLED] = enabled }
    suspend fun setIslandShowFor(categories: Set<String>) = write("island_show_for", categories) { it[K.ISLAND_SHOW_FOR] = categories }
    suspend fun setIslandDuration(duration: Int) = write("island_duration", duration) { it[K.ISLAND_DURATION] = duration }
    suspend fun setIslandSize(size: String) = write("island_size", size) { it[K.ISLAND_SIZE] = size }
    suspend fun setIslandPosition(position: String) = write("island_position", position) { it[K.ISLAND_POSITION] = position }
    suspend fun setIslandOffsetX(offset: Int) = write("island_offset_x_dp", offset) { it[K.ISLAND_OFFSET_X] = offset.coerceIn(-100, 100) }
    suspend fun setIslandOffsetY(offset: Int) = write("island_offset_y_dp", offset) { it[K.ISLAND_OFFSET_Y] = offset.coerceIn(0, 120) }
    suspend fun resetIslandPosition() = write("reset_island_pos", 0) {
        it[K.ISLAND_OFFSET_X] = 0
        it[K.ISLAND_OFFSET_Y] = 0
    }
    suspend fun setIslandPeekLeft(left: IslandPeekLeft) = write("island_peek_left", left) { it[K.ISLAND_PEEK_LEFT] = left.name }
    suspend fun setIslandPeekText(text: IslandPeekText) = write("island_peek_text", text) { it[K.ISLAND_PEEK_TEXT] = text.name }
    suspend fun setIslandPeekMaxChars(chars: Int) = write("island_peek_max_chars", chars) { it[K.ISLAND_PEEK_MAX_CHARS] = chars }
    suspend fun setIslandExpAvatar(enabled: Boolean) = write("island_exp_avatar", enabled) { it[K.ISLAND_EXP_AVATAR] = enabled }
    suspend fun setIslandExpTag(enabled: Boolean) = write("island_exp_tag", enabled) { it[K.ISLAND_EXP_TAG] = enabled }
    suspend fun setIslandExpTime(enabled: Boolean) = write("island_exp_time", enabled) { it[K.ISLAND_EXP_TIME] = enabled }
    suspend fun setIslandExpText(enabled: Boolean) = write("island_exp_text", enabled) { it[K.ISLAND_EXP_TEXT] = enabled }
    suspend fun setIslandExpOtp(enabled: Boolean) = write("island_exp_otp", enabled) { it[K.ISLAND_EXP_OTP] = enabled }
    suspend fun setIslandExpTxn(enabled: Boolean) = write("island_exp_txn", enabled) { it[K.ISLAND_EXP_TXN] = enabled }
    suspend fun setIslandExpLines(lines: Int) = write("island_exp_lines", lines) { it[K.ISLAND_EXP_LINES] = lines }
    suspend fun setIslandActReply(enabled: Boolean) = write("island_act_reply", enabled) { it[K.ISLAND_ACT_REPLY] = enabled }
    suspend fun setIslandActCopy(enabled: Boolean) = write("island_act_copy", enabled) { it[K.ISLAND_ACT_COPY] = enabled }
    suspend fun setIslandActRead(enabled: Boolean) = write("island_act_read", enabled) { it[K.ISLAND_ACT_READ] = enabled }
    suspend fun setIslandActExpand(enabled: Boolean) = write("island_act_expand", enabled) { it[K.ISLAND_ACT_EXPAND] = enabled }
    suspend fun setIslandHideContent(hide: Boolean) = write("island_hide_content", hide) { it[K.ISLAND_HIDE_CONTENT] = hide }
    suspend fun setIslandSkipWhenOpen(skip: Boolean) = write("island_skip_when_open", skip) { it[K.ISLAND_SKIP_WHEN_OPEN] = skip }
    suspend fun setIslandRespectDnd(respect: Boolean) = write("island_respect_dnd", respect) { it[K.ISLAND_RESPECT_DND] = respect }
    suspend fun setIslandKeepSilentCopy(keep: Boolean) = write("island_keep_silent_copy", keep) { it[K.ISLAND_KEEP_SILENT_COPY] = keep }
    suspend fun setIslandSound(sound: IslandSound) = write("island_sound", sound) { it[K.ISLAND_SOUND] = sound.name }

    suspend fun setBubbleTheme(style: BubbleThemeStyle) = write("bubble_theme", style) { it[K.BUBBLE_THEME] = style.name }
    suspend fun setTypefaceStyle(style: TypefaceStyle) = write("typeface_style", style) { it[K.TYPEFACE_STYLE] = style.name }
    suspend fun setTextSizeScale(scale: TextSizeScale) = write("text_size_scale", scale) { it[K.TEXT_SIZE_SCALE] = scale.name }
    suspend fun setCornerRadius(radius: Float) = write("corner_radius", radius) { it[K.CORNER_RADIUS] = radius.coerceIn(8f, 40f) }
    suspend fun setKeepOneSharpCorner(keep: Boolean) = write("keep_one_sharp_corner", keep) { it[K.KEEP_ONE_SHARP_CORNER] = keep }
    suspend fun setBackgroundDesign(design: BackgroundDesign) = write("background_design", design) { it[K.BACKGROUND_DESIGN] = design.name }
    suspend fun setBackgroundSeed(seed: Int) = write("background_seed", seed) { it[K.BACKGROUND_SEED] = seed }
    suspend fun setGradientWashLevel(level: GradientWashLevel) = write("gradient_wash_level", level) { it[K.GRADIENT_WASH_LEVEL] = level.name }
    suspend fun setNavPillHeight(height: NavPillHeight) = write("nav_pill_height", height) {
        it[K.NAV_PILL_HEIGHT] = height.name
        it[K.NAV_PILL_HEIGHT_DP] = height.heightDp
    }
    suspend fun setFabSize(size: FabSize) = write("fab_size", size) {
        it[K.FAB_SIZE] = size.name
        it[K.FAB_SIZE_DP] = size.sizeDp
    }
    suspend fun setNavPillHeightDp(heightDp: Int) = write("nav_pill_height_dp", heightDp) {
        val clamped = heightDp.coerceIn(44, 68)
        it[K.NAV_PILL_HEIGHT_DP] = clamped
        val mappedEnum = when {
            clamped <= 52 -> NavPillHeight.COMPACT
            clamped <= 58 -> NavPillHeight.DEFAULT
            else -> NavPillHeight.LARGE
        }
        it[K.NAV_PILL_HEIGHT] = mappedEnum.name
    }
    suspend fun setFabSizeDp(sizeDp: Int) = write("fab_size_dp", sizeDp) {
        val clamped = sizeDp.coerceIn(42, 66)
        it[K.FAB_SIZE_DP] = clamped
        val mappedEnum = when {
            clamped <= 50 -> FabSize.SMALL
            clamped <= 58 -> FabSize.DEFAULT
            else -> FabSize.LARGE
        }
        it[K.FAB_SIZE] = mappedEnum.name
    }
    suspend fun setSearchHintText(hint: String) = write("search_hint_text", hint) { it[K.SEARCH_HINT_TEXT] = hint.take(30) }
    suspend fun setOtpAutoDelete(time: OtpAutoDeleteTime) = write("otp_auto_delete", time) { it[K.OTP_AUTO_DELETE] = time.name }
    suspend fun setSpamAutoDelete(time: SpamAutoDeleteTime) = write("spam_auto_delete", time) { it[K.SPAM_AUTO_DELETE] = time.name }
    suspend fun setTrashAutoDelete(time: TrashAutoDeleteTime) = write("trash_auto_delete", time) { it[K.TRASH_AUTO_DELETE] = time.name }
    suspend fun setLanguageCode(code: String) = write("language_code", code) { it[K.LANGUAGE_CODE] = code }
    suspend fun setCategoryGridColumns(cols: Int) = write("category_grid_columns", cols) { it[K.CATEGORY_GRID_COLUMNS] = cols.coerceIn(2, 4) }
    suspend fun setCategoryGridRows(rows: Int) = write("category_grid_rows", rows) { it[K.CATEGORY_GRID_ROWS] = rows.coerceIn(1, 3) }
    suspend fun setSelectionBarScale(scale: Float) = write("selection_bar_scale", scale) {
        it[K.SELECTION_BAR_SCALE] = scale.coerceIn(0.70f, 1.0f)
    }

    private suspend fun write(name: String, value: Any, block: (MutablePreferences) -> Unit) {
        Log.d("BromeSettings", "write $name=$value")
        context.bromeDataStore.edit(block)
    }

    private inline fun <reified T : Enum<T>> tryEnum(value: String?, default: T): T {
        if (value == null) return default
        return try { enumValueOf<T>(value) } catch (e: Exception) { default }
    }
}
