package com.aravind.spectra.ui

import kotlin.math.roundToInt

fun fmtBytes(n: Long): String = when {
    n > 1_000_000 -> "%.1f MB".format(n / 1_000_000.0)
    n > 1_000 -> "%.0f KB".format(n / 1_000.0)
    else -> "$n B"
}

fun fmtDurationMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

fun fmtDurationSec(sec: Double): String {
    if (!sec.isFinite()) return "—"
    val m = (sec / 60).toInt()
    val s = (sec % 60).roundToInt()
    return "%d:%02d".format(m, s)
}

fun fmtHz(hz: Double?): String {
    if (hz == null) return "—"
    return if (hz >= 1000) "%.1f kHz".format(hz / 1000).replace(".0 ", " ") else "${hz.roundToInt()} Hz"
}

fun fmtDb(db: Double?): String = if (db == null || !db.isFinite()) "—" else "%.2f dB".format(db)
