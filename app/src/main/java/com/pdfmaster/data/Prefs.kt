package com.pdfmaster.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ReadingMode { NORMAL, NIGHT, SEPIA }

/** Small on-device settings and usage counters. Nothing here leaves the phone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("pdfmaster", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.valueOf(sp.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(sp.getBoolean(KEY_DYNAMIC, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _appLock = MutableStateFlow(sp.getBoolean(KEY_LOCK, false))
    val appLock: StateFlow<Boolean> = _appLock.asStateFlow()

    private val _debugPro = MutableStateFlow(sp.getBoolean(KEY_DEBUG_PRO, false))
    val debugPro: StateFlow<Boolean> = _debugPro.asStateFlow()

    fun setThemeMode(mode: ThemeMode) { sp.edit().putString(KEY_THEME, mode.name).apply(); _themeMode.value = mode }
    fun setDynamicColor(on: Boolean) { sp.edit().putBoolean(KEY_DYNAMIC, on).apply(); _dynamicColor.value = on }
    fun setAppLock(on: Boolean) { sp.edit().putBoolean(KEY_LOCK, on).apply(); _appLock.value = on }
    fun setDebugPro(on: Boolean) { sp.edit().putBoolean(KEY_DEBUG_PRO, on).apply(); _debugPro.value = on }

    var readingMode: ReadingMode
        get() = ReadingMode.valueOf(sp.getString(KEY_READING, ReadingMode.NORMAL.name)!!)
        set(value) = sp.edit().putString(KEY_READING, value.name).apply()

    var cachedPro: Boolean
        get() = sp.getBoolean(KEY_CACHED_PRO, false)
        set(value) = sp.edit().putBoolean(KEY_CACHED_PRO, value).apply()

    /** Uses of a daily-limited tool today. */
    fun usesToday(toolId: String): Int {
        val key = "uses:$toolId:${LocalDate.now()}"
        return sp.getInt(key, 0)
    }

    fun recordUse(toolId: String) {
        val key = "uses:$toolId:${LocalDate.now()}"
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
    }

    fun trialUsed(toolId: String): Boolean = sp.getBoolean("trial:$toolId", false)
    fun markTrialUsed(toolId: String) = sp.edit().putBoolean("trial:$toolId", true).apply()

    fun bookmarks(path: String): Set<Int> =
        sp.getStringSet("bm:$path", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()

    fun setBookmarks(path: String, pages: Set<Int>) =
        sp.edit().putStringSet("bm:$path", pages.map { it.toString() }.toSet()).apply()

    fun lastPage(path: String): Int = sp.getInt("last:$path", 0)
    fun setLastPage(path: String, page: Int) = sp.edit().putInt("last:$path", page).apply()

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_LOCK = "app_lock"
        const val KEY_DEBUG_PRO = "debug_pro"
        const val KEY_READING = "reading_mode"
        const val KEY_CACHED_PRO = "cached_pro"
    }
}
