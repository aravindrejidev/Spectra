package com.aravind.spectra.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User settings, kept on the phone. Values are Compose state, so screens update when they change. */
object AppSettings {
    private var prefs: SharedPreferences? = null

    // Appearance
    var reduceMotion by mutableStateOf(false)
    var typewriter by mutableStateOf(true)
    var headerShine by mutableStateOf(true)
    var haptics by mutableStateOf(true)
    var showTrophies by mutableStateOf(true)
    var showGame by mutableStateOf(true)
    var showTips by mutableStateOf(true)
    var showSelfTest by mutableStateOf(true)

    // Analysis
    var decoderMode by mutableIntStateOf(0) // 0 auto, 1 FFmpeg only, 2 system only
    var exactTruePeak by mutableStateOf(true)
    var clipMode by mutableIntStateOf(1) // 0 sensitive, 1 normal, 2 strict
    var defaultLogScale by mutableStateOf(false)
    var defaultFloorDb by mutableFloatStateOf(-100f)
    var scanSubfolders by mutableStateOf(true)

    // Notifications and updates
    var notifyResults by mutableStateOf(true)
    var notifyUpdates by mutableStateOf(true)
    var checkOnLaunch by mutableStateOf(true)
    var dailyCheck by mutableStateOf(true)

    /** Samples at or above this level count as clipped. */
    val clipThreshold: Double
        get() = when (clipMode) {
            0 -> 0.99
            2 -> 0.9999
            else -> 0.999
        }

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (prefs == null) load(context)
    }

    @Synchronized
    fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences("spectra_settings", Context.MODE_PRIVATE)
        prefs = p
        reduceMotion = p.getBoolean("reduce_motion", false)
        typewriter = p.getBoolean("typewriter", true)
        headerShine = p.getBoolean("header_shine", true)
        haptics = p.getBoolean("haptics", true)
        showTrophies = p.getBoolean("show_trophies", true)
        showGame = p.getBoolean("show_game", true)
        showTips = p.getBoolean("show_tips", true)
        showSelfTest = p.getBoolean("show_selftest", true)
        decoderMode = p.getInt("decoder_mode", 0)
        exactTruePeak = p.getBoolean("exact_tp", true)
        clipMode = p.getInt("clip_mode", 1)
        defaultLogScale = p.getBoolean("default_log", false)
        defaultFloorDb = p.getFloat("default_floor", -100f)
        scanSubfolders = p.getBoolean("scan_subfolders", true)
        notifyResults = p.getBoolean("notify_results", true)
        notifyUpdates = p.getBoolean("notify_updates", true)
        checkOnLaunch = p.getBoolean("check_on_launch", true)
        dailyCheck = p.getBoolean("daily_check", true)
    }

    fun save() {
        val p = prefs ?: return
        p.edit()
            .putBoolean("reduce_motion", reduceMotion)
            .putBoolean("typewriter", typewriter)
            .putBoolean("header_shine", headerShine)
            .putBoolean("haptics", haptics)
            .putBoolean("show_trophies", showTrophies)
            .putBoolean("show_game", showGame)
            .putBoolean("show_tips", showTips)
            .putBoolean("show_selftest", showSelfTest)
            .putInt("decoder_mode", decoderMode)
            .putBoolean("exact_tp", exactTruePeak)
            .putInt("clip_mode", clipMode)
            .putBoolean("default_log", defaultLogScale)
            .putFloat("default_floor", defaultFloorDb)
            .putBoolean("scan_subfolders", scanSubfolders)
            .putBoolean("notify_results", notifyResults)
            .putBoolean("notify_updates", notifyUpdates)
            .putBoolean("check_on_launch", checkOnLaunch)
            .putBoolean("daily_check", dailyCheck)
            .apply()
    }
}
