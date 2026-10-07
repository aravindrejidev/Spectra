package com.aravind.spectra.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aravind.spectra.dsp.SelfTest
import com.aravind.spectra.dsp.TestResult
import com.aravind.spectra.ui.theme.SpectraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

sealed class SelfTestState {
    object Idle : SelfTestState()
    data class Running(val done: Int, val total: Int, val current: String) : SelfTestState()
    data class Done(val results: List<TestResult>, val seconds: Double) : SelfTestState()
}

class SelfTestViewModel : ViewModel() {
    private val _state = MutableStateFlow<SelfTestState>(SelfTestState.Idle)
    val state: StateFlow<SelfTestState> = _state.asStateFlow()

    fun start() {
        if (_state.value is SelfTestState.Running) return
        viewModelScope.launch {
            val total = SelfTest.count
            _state.value = SelfTestState.Running(0, total, "")
            val t0 = SystemClock.elapsedRealtime()
            val results = withContext(Dispatchers.Default) {
                SelfTest.run { done, name -> _state.value = SelfTestState.Running(done, total, name) }
            }
            _state.value = SelfTestState.Done(results, (SystemClock.elapsedRealtime() - t0) / 1000.0)
        }
    }
}

private fun resultsText(s: SelfTestState.Done): String = buildString {
    val passed = s.results.count { it.pass }
    appendLine("Spectra self-test: $passed/${s.results.size} passed (${String.format(Locale.US, "%.1f", s.seconds)} s)")
    for (r in s.results) {
        appendLine("[${if (r.pass) "PASS" else "FAIL"}] ${r.name}: ${r.measured} (expected ${r.expected})")
    }
}

/** Idle-screen panel that runs the known-answer accuracy tests on this phone. */
@Composable
fun SelfTestUnit(vm: SelfTestViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val s = state
    val lamp = when (s) {
        is SelfTestState.Idle -> SpectraColors.Amber
        is SelfTestState.Running -> SpectraColors.Blue
        is SelfTestState.Done -> if (s.results.all { it.pass }) SpectraColors.Green else SpectraColors.Red
    }

    RackPanel("Self-test", lamp = lamp) {
        when (s) {
            is SelfTestState.Idle -> {
                Text(
                    "Feeds test signals with known answers through the real analyzer on this phone and checks " +
                        "loudness, true peak, clipping, dynamic range, stereo, bit depth and cutoff detection. " +
                        "Takes about 20 seconds.",
                    color = SpectraColors.Ink,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                HwButton("RUN SELF-TEST", Modifier.fillMaxWidth(), height = 50.dp, onClick = vm::start)
            }
            is SelfTestState.Running -> {
                GlassScreen {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Lcd("TEST ${min(s.done + 1, s.total)} / ${s.total}", size = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Lcd(s.current, color = SpectraColors.PhosphorDim, size = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        LedLadder(s.done * 100.0 / s.total, 0.0, 100.0, segments = 30, tint = SpectraColors.Phosphor)
                    }
                }
            }
            is SelfTestState.Done -> {
                val passed = s.results.count { it.pass }
                val allOk = passed == s.results.size
                GlassScreen {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Lcd(
                            if (allOk) "ALL TESTS PASSED" else "SOME TESTS FAILED",
                            color = if (allOk) SpectraColors.Green else SpectraColors.Red,
                            size = 16.sp,
                            weight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Lcd(
                            "$passed / ${s.results.size} passed in ${String.format(Locale.US, "%.1f", s.seconds)} s",
                            color = SpectraColors.PhosphorDim,
                            size = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                for (r in s.results) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Led(if (r.pass) SpectraColors.Green else SpectraColors.Red, true, 10.dp, Modifier.padding(top = 3.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Engraved(r.name, size = 12.sp, spacing = 0.3.sp)
                            Text(
                                "${r.measured}   (expected ${r.expected})",
                                color = SpectraColors.Ink,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    HwButton("SHARE RESULTS", Modifier.weight(1f), height = 44.dp) {
                        shareReportText(context, resultsText(s))
                    }
                    HwButton("RUN AGAIN", Modifier.weight(1f), height = 44.dp, onClick = vm::start)
                }
            }
        }
    }
}
