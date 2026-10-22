package com.aravind.spectra.engage

import android.content.Context
import android.content.SharedPreferences
import com.aravind.spectra.dsp.Severity
import com.aravind.spectra.model.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

enum class Tier { BRONZE, SILVER, GOLD }

data class Trophy(val id: String, val title: String, val hint: String, val tier: Tier)

data class Stats(
    val tracks: Int = 0,
    val seconds: Long = 0L,
    val fakes: Int = 0,
    val batches: Int = 0,
    val bestDr: Float = -1f,
    val bestDrName: String = "",
    val loudest: Float = -100f,
    val loudestName: String = "",
    val maxRate: Int = 0,
    val clean: Int = 0
)

/** Medals and lifetime counters, kept on the phone only. */
object Trophies {

    val all: List<Trophy> = listOf(
        Trophy("first", "First Spin", "Analyze your first track", Tier.BRONZE),
        Trophy("ten", "Ten Down", "Analyze 10 tracks", Tier.BRONZE),
        Trophy("fifty", "Library Keeper", "Analyze 50 tracks", Tier.SILVER),
        Trophy("hundred", "Centurion", "Analyze 100 tracks", Tier.GOLD),
        Trophy("fake1", "Fake Hunter", "Catch a likely fake lossless file", Tier.SILVER),
        Trophy("fake5", "Sherlock", "Catch 5 likely fakes", Tier.GOLD),
        Trophy("dynamic", "Golden Ears", "Analyze a track with DR 14 or more", Tier.SILVER),
        Trophy("brick", "Brickwall Veteran", "Analyze a track with DR 5 or less", Tier.BRONZE),
        Trophy("hires", "Hi-Res Explorer", "Analyze a file at 88.2 kHz or higher", Tier.BRONZE),
        Trophy("clean", "Clean Bill", "Find a track with no lossy sign, no clipping and true peak below 0 dB", Tier.SILVER),
        Trophy("batch", "Bulk Inspector", "Finish a batch scan of 10 or more files", Tier.SILVER),
        Trophy("paper", "On Paper", "Export a report as PDF", Tier.BRONZE),
        Trophy("picture", "Picture Perfect", "Save a report image", Tier.BRONZE),
        Trophy("calibrated", "Calibrated", "Run the self-test and pass every check", Tier.SILVER),
        Trophy("marathon", "Marathon Listener", "Analyze 10 hours of audio in total", Tier.GOLD),
        Trophy("rookie", "Rookie Detective", "Finish a round of Spot the fake", Tier.BRONZE),
        Trophy("sharp", "Sharp Eyes", "Get 8 or more right in Spot the fake", Tier.GOLD)
    )

    private var prefs: SharedPreferences? = null

    private val _unlocked = MutableStateFlow<Set<String>>(emptySet())
    val unlocked: StateFlow<Set<String>> = _unlocked.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    /** Medals unlocked but not yet shown to the user. */
    private val _pending = MutableStateFlow<List<Trophy>>(emptyList())
    val pending: StateFlow<List<Trophy>> = _pending.asStateFlow()

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("spectra_trophies", Context.MODE_PRIVATE)
        prefs = p
        _stats.value = Stats(
            tracks = p.getInt("tracks", 0),
            seconds = p.getLong("seconds", 0L),
            fakes = p.getInt("fakes", 0),
            batches = p.getInt("batches", 0),
            bestDr = p.getFloat("best_dr", -1f),
            bestDrName = p.getString("best_dr_name", "") ?: "",
            loudest = p.getFloat("loudest", -100f),
            loudestName = p.getString("loudest_name", "") ?: "",
            maxRate = p.getInt("max_rate", 0),
            clean = p.getInt("clean", 0)
        )
        _unlocked.value = p.getStringSet("unlocked", emptySet())?.toSet() ?: emptySet()
    }

    @Synchronized
    fun reset(context: Context) {
        ensureLoaded(context)
        _stats.value = Stats()
        _unlocked.value = emptySet()
        _pending.value = emptyList()
        save()
    }

    @Synchronized
    fun dismissFirst() {
        _pending.value = _pending.value.drop(1)
    }

    private fun unlock(id: String) {
        if (id in _unlocked.value) return
        val t = all.firstOrNull { it.id == id } ?: return
        _unlocked.value = _unlocked.value + id
        _pending.value = _pending.value + t
    }

    private fun save() {
        val p = prefs ?: return
        val s = _stats.value
        p.edit()
            .putInt("tracks", s.tracks)
            .putLong("seconds", s.seconds)
            .putInt("fakes", s.fakes)
            .putInt("batches", s.batches)
            .putFloat("best_dr", s.bestDr)
            .putString("best_dr_name", s.bestDrName)
            .putFloat("loudest", s.loudest)
            .putString("loudest_name", s.loudestName)
            .putInt("max_rate", s.maxRate)
            .putInt("clean", s.clean)
            .putStringSet("unlocked", HashSet(_unlocked.value))
            .apply()
    }

    @Synchronized
    fun recordAnalysis(context: Context, s: UiState.Success) {
        ensureLoaded(context)
        val a = s.analysis
        val v = s.verdict
        val name = (s.tags.title ?: s.fileName).take(40)

        var st = _stats.value
        st = st.copy(
            tracks = st.tracks + 1,
            seconds = st.seconds + a.durationSec.toLong(),
            maxRate = max(st.maxRate, a.sampleRate)
        )
        if (v.severity == Severity.BAD) st = st.copy(fakes = st.fakes + 1)

        val dr = a.dr
        if (dr != null && dr.isFinite()) {
            if (dr.toFloat() > st.bestDr) st = st.copy(bestDr = dr.toFloat(), bestDrName = name)
            if (dr >= 14.0) unlock("dynamic")
            if (dr <= 5.0) unlock("brick")
        }
        val lufs = a.lufs
        if (lufs != null && lufs.isFinite() && lufs.toFloat() > st.loudest) {
            st = st.copy(loudest = lufs.toFloat(), loudestName = name)
        }
        if (v.severity == Severity.GOOD && a.clipRuns == 0 && a.truePeakDb <= 0.0) {
            st = st.copy(clean = st.clean + 1)
            unlock("clean")
        }
        _stats.value = st

        if (st.tracks >= 1) unlock("first")
        if (st.tracks >= 10) unlock("ten")
        if (st.tracks >= 50) unlock("fifty")
        if (st.tracks >= 100) unlock("hundred")
        if (st.fakes >= 1) unlock("fake1")
        if (st.fakes >= 5) unlock("fake5")
        if (st.maxRate >= 88200) unlock("hires")
        if (st.seconds >= 36000L) unlock("marathon")
        save()
    }

    @Synchronized
    fun recordBatchDone(context: Context, files: Int) {
        ensureLoaded(context)
        _stats.value = _stats.value.copy(batches = _stats.value.batches + 1)
        if (files >= 10) unlock("batch")
        save()
    }

    @Synchronized
    fun recordPdf(context: Context) {
        ensureLoaded(context)
        unlock("paper")
        save()
    }

    @Synchronized
    fun recordImage(context: Context) {
        ensureLoaded(context)
        unlock("picture")
        save()
    }

    @Synchronized
    fun recordSelfTest(context: Context, allPassed: Boolean) {
        ensureLoaded(context)
        if (allPassed) unlock("calibrated")
        save()
    }

    @Synchronized
    fun recordGame(context: Context, correct: Int, total: Int) {
        ensureLoaded(context)
        unlock("rookie")
        if (correct >= 8 && total >= 10) unlock("sharp")
        save()
    }
}
