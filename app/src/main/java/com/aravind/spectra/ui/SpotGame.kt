package com.aravind.spectra.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aravind.spectra.dsp.StreamAnalyzer
import com.aravind.spectra.dsp.VerdictEngine
import com.aravind.spectra.engage.Trophies
import com.aravind.spectra.ui.theme.SpectraColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One question: a spectrogram, and whether it was given a brick-wall low-pass (a lossy fingerprint). */
class GameRound(val image: ImageBitmap, val cliff: Boolean, val cutoffHz: Int, val measured: String)

sealed class GameState {
    object Idle : GameState()
    data class Building(val round: Int, val total: Int) : GameState()
    data class Asking(val round: Int, val total: Int, val score: Int, val streak: Int, val puzzle: GameRound) : GameState()
    data class Answered(
        val round: Int, val total: Int, val score: Int, val streak: Int,
        val puzzle: GameRound, val correct: Boolean, val gained: Int
    ) : GameState()
    data class Done(val score: Int, val correct: Int, val total: Int, val best: Int) : GameState()
}

/** Makes synthetic "music", filters it, and runs it through the real analyzer for a genuine spectrogram. */
object GameSynth {
    private const val SR = 44100
    private const val SECONDS = 6

    fun make(level: Int, rnd: Random): GameRound {
        val n = SR * SECONDS
        val raw = music(rnd, n)
        val cliff = rnd.nextBoolean()
        var fc = 0
        val y: DoubleArray
        if (cliff) {
            val options = when {
                level < 3 -> intArrayOf(16000, 17000)
                level < 6 -> intArrayOf(17500, 18000, 19000)
                else -> intArrayOf(19000, 19500)
            }
            fc = options[rnd.nextInt(options.size)]
            y = firLowpass(raw, fc.toDouble(), 801)
        } else if (level < 3) {
            y = firLowpass(raw, 21500.0, 401)
        } else {
            y = onePoles(raw, if (level < 6) 14000.0 else 16000.0, 4)
        }

        var peak = 1e-9
        for (v in y) if (abs(v) > peak) peak = abs(v)
        val g = 0.89 / peak
        val samples = FloatArray(n) { (y[it] * g).toFloat() }

        val an = StreamAnalyzer(SR, 1, n.toLong(), false)
        val buf = arrayOf(FloatArray(8192))
        var pos = 0
        while (pos < n) {
            val m = min(8192, n - pos)
            System.arraycopy(samples, pos, buf[0], 0, m)
            an.feed(buf, m)
            pos += m
        }
        val res = an.finish()
        val image = buildSpectrogramBitmap(res.views.getValue("all"), SR / 2.0, false, -100f, 0f)
        val c = res.cutoff
        val measured = if (c.hasEdge) {
            "Spectra measured a hard edge at ${String.format(Locale.US, "%.1f", c.cutoffHz / 1000.0)} kHz, " +
                "${String.format(Locale.US, "%.0f", c.strengthDb)} dB steep"
        } else {
            "Spectra found no hard edge: the spectrum fades smoothly"
        }
        return GameRound(image, cliff, fc, measured)
    }

    private fun music(rnd: Random, n: Int): DoubleArray {
        val x = DoubleArray(n)
        var pos = 0.0
        while (pos < SECONDS - 0.5) {
            val f0 = 110.0 + rnd.nextDouble() * 770.0
            val len = 0.3 + rnd.nextDouble() * 0.9
            val amp = 0.05 + rnd.nextDouble() * 0.2
            val i0 = (pos * SR).toInt()
            val i1 = min(n, ((pos + len) * SR).toInt())
            val nh = min(40, (20000.0 / f0).toInt())
            val phase = DoubleArray(nh) { rnd.nextDouble() * 2.0 * PI }
            for (i in i0 until i1) {
                val t = (i - i0).toDouble() / SR
                val env = exp(-t / (len * 0.5)) * (1.0 - exp(-t / 0.005))
                var s = 0.0
                for (h in 1..nh) s += sin(2.0 * PI * f0 * h * t + phase[h - 1]) / h.toDouble().pow(1.1)
                x[i] += s * env * amp
            }
            pos += 0.15 + rnd.nextDouble() * 0.45
        }

        val noise = DoubleArray(n) { rnd.nextGaussian() }
        var hit = 0.5
        while (hit < SECONDS - 0.2) {
            if (hit < 3.0 || hit > 3.8) { // a quiet passage in the middle
                val i0 = (hit * SR).toInt()
                val m = min(n - i0, (0.12 * SR).toInt())
                val g = 0.15 * (0.4 + 0.6 * rnd.nextDouble())
                for (k in 1 until m) {
                    val t = k.toDouble() / SR
                    x[i0 + k] += g * 0.5 * (noise[i0 + k] - noise[i0 + k - 1]) * exp(-t / 0.04)
                }
            }
            hit += 0.125
        }
        for (i in 0 until n) x[i] += rnd.nextGaussian() * 0.0004
        return x
    }

    private fun bessel0(x: Double): Double {
        var sum = 1.0
        var term = 1.0
        val q = x * x / 4.0
        var k = 1
        while (k < 60) {
            term *= q / (k.toDouble() * k)
            sum += term
            if (term < 1e-14 * sum) break
            k++
        }
        return sum
    }

    /** Steep Kaiser-window low-pass: a clean brick wall at [fc]. */
    private fun firLowpass(x: DoubleArray, fc: Double, taps: Int): DoubleArray {
        val m = taps - 1
        val beta = 10.0
        val i0b = bessel0(beta)
        val fn = fc / SR
        val h = DoubleArray(taps)
        var sum = 0.0
        for (i in 0 until taps) {
            val t = i - m / 2.0
            val sinc = if (abs(t) < 1e-12) 2.0 * fn else sin(2.0 * PI * fn * t) / (PI * t)
            val r = 2.0 * i / m - 1.0
            h[i] = sinc * bessel0(beta * sqrt(max(0.0, 1.0 - r * r))) / i0b
            sum += h[i]
        }
        for (i in 0 until taps) h[i] /= sum
        val out = DoubleArray(x.size)
        for (i in x.indices) {
            var s = 0.0
            val kmax = min(i, m)
            for (k in 0..kmax) s += h[k] * x[i - k]
            out[i] = s
        }
        return out
    }

    /** A gentle, wall-less roll-off: [poles] cascaded one-pole low-passes. */
    private fun onePoles(x: DoubleArray, fc: Double, poles: Int): DoubleArray {
        val a = 1.0 - exp(-2.0 * PI * fc / SR)
        var cur = x
        repeat(poles) {
            val y = DoubleArray(cur.size)
            var s = 0.0
            for (i in cur.indices) {
                s += a * (cur[i] - s)
                y[i] = s
            }
            cur = y
        }
        return cur
    }
}

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("spectra_game", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<GameState>(GameState.Idle)
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val total = 10
    private var seeds = Random()
    private var prefetch: Deferred<GameRound>? = null
    private var job: Job? = null
    private var done = 0
    private var score = 0
    private var streak = 0
    private var correct = 0

    val best: Int get() = prefs.getInt("best", 0)

    fun start() {
        job?.cancel()
        prefetch?.cancel()
        seeds = Random(System.nanoTime())
        done = 0
        score = 0
        streak = 0
        correct = 0
        job = viewModelScope.launch {
            _state.value = GameState.Building(1, total)
            val seed = seeds.nextLong()
            val first = withContext(Dispatchers.Default) { GameSynth.make(0, Random(seed)) }
            show(first)
        }
    }

    private fun show(round: GameRound) {
        _state.value = GameState.Asking(done + 1, total, score, streak, round)
        val nextIndex = done + 1
        if (nextIndex < total) {
            val seed = seeds.nextLong()
            prefetch = viewModelScope.async(Dispatchers.Default) { GameSynth.make(nextIndex, Random(seed)) }
        } else {
            prefetch = null
        }
    }

    fun answer(saysCliff: Boolean) {
        val s = _state.value as? GameState.Asking ?: return
        val ok = saysCliff == s.puzzle.cliff
        val gained = if (ok) 10 + 2 * min(streak, 5) else 0
        if (ok) {
            score += gained
            streak += 1
            correct += 1
        } else {
            streak = 0
        }
        _state.value = GameState.Answered(s.round, total, score, streak, s.puzzle, ok, gained)
    }

    fun next() {
        if (_state.value !is GameState.Answered) return
        done += 1
        if (done >= total) {
            finish()
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            val pending = prefetch
            if (pending == null || !pending.isCompleted) _state.value = GameState.Building(done + 1, total)
            val seed = seeds.nextLong()
            val ready = try {
                pending?.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val round = ready ?: withContext(Dispatchers.Default) { GameSynth.make(done, Random(seed)) }
            show(round)
        }
    }

    private fun finish() {
        val bestNow = max(prefs.getInt("best", 0), score)
        prefs.edit().putInt("best", bestNow).apply()
        Trophies.recordGame(getApplication<Application>(), correct, total)
        _state.value = GameState.Done(score, correct, total, bestNow)
    }

    fun close() {
        job?.cancel()
        prefetch?.cancel()
        _state.value = GameState.Idle
    }
}

private fun rankOf(correct: Int): String = when {
    correct >= 9 -> "GOLDEN EARS"
    correct >= 7 -> "SHARP DETECTIVE"
    correct >= 5 -> "APPRENTICE"
    else -> "ROOKIE"
}

@Composable
private fun GameHeader(round: Int, total: Int, score: Int, streak: Int) {
    Lcd("ROUND $round/$total · SCORE $score · STREAK $streak", color = SpectraColors.PhosphorDim, size = 12.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun GameSpectrogram(puzzle: GameRound, animate: Boolean) {
    GlassScreen {
        Row(Modifier.fillMaxWidth().height(190.dp).padding(10.dp)) {
            Column(
                Modifier.width(36.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                for (label in listOf("22k", "16.5k", "11k", "5.5k", "0")) {
                    Text(label, color = SpectraColors.PhosphorDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.width(6.dp))
            val wipe = if (animate) Modifier.revealWipe(key = puzzle) else Modifier
            Image(
                puzzle.image,
                null,
                Modifier.weight(1f).fillMaxHeight().then(wipe),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Low
            )
        }
    }
}

/** Home-screen mini-game: ten spectrograms, "brick wall or natural fade?" */
@Composable
fun SpotGameUnit(vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val s = state

    RackPanel("Spot the fake", lamp = SpectraColors.Amber, lampPulse = s is GameState.Building) {
        when (s) {
            is GameState.Idle -> {
                Text(
                    "Ten spectrograms, one question each: is there a brick wall (a lossy encode), or does the " +
                        "spectrum fade away naturally? Build a streak for bonus points.",
                    color = SpectraColors.Ink,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (vm.best > 0) {
                    Spacer(Modifier.height(8.dp))
                    Engraved("BEST SCORE  ${vm.best}", size = 11.sp, spacing = 1.5.sp)
                }
                Spacer(Modifier.height(12.dp))
                HwButton("START", Modifier.fillMaxWidth(), height = 50.dp, onClick = vm::start)
            }
            is GameState.Building -> {
                GlassScreen {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Lcd(rememberTyped("BUILDING ROUND ${s.round} / ${s.total}"), size = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        ScanBar()
                        Spacer(Modifier.height(8.dp))
                        Lcd("Making a fresh spectrogram on your phone…", color = SpectraColors.PhosphorDim, size = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                HwButton("QUIT", Modifier.fillMaxWidth(), height = 40.dp, onClick = vm::close)
            }
            is GameState.Asking -> {
                GameHeader(s.round, s.total, s.score, s.streak)
                GameSpectrogram(s.puzzle, animate = true)
                Spacer(Modifier.height(12.dp))
                HwButton("BRICK WALL · LOSSY", Modifier.fillMaxWidth(), height = 50.dp) { vm.answer(true) }
                Spacer(Modifier.height(10.dp))
                HwButton("FADES NATURALLY", Modifier.fillMaxWidth(), height = 50.dp) { vm.answer(false) }
            }
            is GameState.Answered -> {
                GameHeader(s.round, s.total, s.score, s.streak)
                GameSpectrogram(s.puzzle, animate = false)
                Spacer(Modifier.height(10.dp))
                GlassScreen {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Lcd(
                            if (s.correct) "CORRECT  +${s.gained}" else "NOT QUITE",
                            color = if (s.correct) SpectraColors.Green else SpectraColors.Red,
                            size = 16.sp,
                            weight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Lcd(
                            if (s.puzzle.cliff) {
                                "It had a brick wall at ${String.format(Locale.US, "%.1f", s.puzzle.cutoffHz / 1000.0)} kHz"
                            } else {
                                "It faded away naturally"
                            },
                            size = 13.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Lcd(s.puzzle.measured, color = SpectraColors.PhosphorDim, size = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (s.puzzle.cliff) {
                                "A flat horizontal wall is the fingerprint of a lossy encoder " +
                                    "(${VerdictEngine.guessLossyBitrate(s.puzzle.cutoffHz.toDouble())}): " +
                                    "everything above its cutoff is simply thrown away."
                            } else {
                                "Real recordings fade away gradually. A fade, not a wall, is what a genuine " +
                                    "full-bandwidth file looks like."
                            },
                            color = SpectraColors.Phosphor.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HwButton(if (s.round >= s.total) "FINISH" else "NEXT ROUND", Modifier.fillMaxWidth(), height = 50.dp, onClick = vm::next)
            }
            is GameState.Done -> {
                GlassScreen {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Lcd(rankOf(s.correct), color = SpectraColors.Amber, size = 18.sp, weight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Lcd("${s.correct} / ${s.total} right · score ${s.score}", size = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Lcd("Best score ${s.best}", color = SpectraColors.PhosphorDim, size = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HwButton("PLAY AGAIN", Modifier.weight(1f), height = 46.dp, onClick = vm::start)
                    HwButton("CLOSE", Modifier.weight(1f), height = 46.dp, onClick = vm::close)
                }
            }
        }
    }
}
