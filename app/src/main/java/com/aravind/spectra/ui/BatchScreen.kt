package com.aravind.spectra.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aravind.spectra.dsp.Severity
import com.aravind.spectra.ui.theme.SpectraColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/** Home-screen panel: start a folder scan or pick several files. */
@Composable
fun BatchLauncherUnit(vm: BatchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.startFolder(uri)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.startFiles(uris)
    }

    RackPanel("Batch scan", lamp = SpectraColors.Blue) {
        Text(
            "Analyze many tracks in a row: pick a folder (sub-folders included) or select several files. " +
                "Suspicious files are listed first, and everything can be saved as a CSV.",
            color = SpectraColors.Ink,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(12.dp))
        HwButton("SCAN FOLDER", Modifier.fillMaxWidth(), height = 50.dp) { folder.launch(null) }
        Spacer(Modifier.height(10.dp))
        HwButton("PICK FILES", Modifier.fillMaxWidth(), height = 50.dp) { files.launch(arrayOf("audio/*")) }
        if (state.items.isNotEmpty() && !state.visible) {
            Spacer(Modifier.height(10.dp))
            HwButton("OPEN LAST SCAN (${state.items.size})", Modifier.fillMaxWidth(), height = 44.dp, onClick = vm::show)
        }
    }
}

private fun rank(i: BatchItem): Int =
    if (i.status == BatchStatus.FAILED) 2
    else when (i.row?.severity) {
        Severity.BAD -> 0
        Severity.WARN -> 1
        Severity.INFO -> 3
        else -> 4
    }

@Composable
fun BatchScreen(vm: BatchViewModel, onOpen: (Uri) -> Unit) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val total = s.items.size
    val finished = s.items.count { it.status == BatchStatus.DONE || it.status == BatchStatus.FAILED }
    val failed = s.items.count { it.status == BatchStatus.FAILED }
    val waiting = s.items.count { it.status == BatchStatus.PENDING }
    val overall = if (total > 0) (finished + (s.fileProgress ?: 0f)) / total else 0f
    fun countOf(sev: Severity) = s.items.count { it.row?.severity == sev }

    RackPanel("Batch scan", lamp = if (s.running) SpectraColors.Blue else SpectraColors.Green) {
        GlassScreen {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Lcd(s.label, size = 13.sp, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                if (s.running && total > 0) {
                    Lcd("${min(finished + 1, total)} / $total · ${s.stage}", color = SpectraColors.PhosphorDim, size = 12.sp, maxLines = 2)
                    Spacer(Modifier.height(10.dp))
                    LedLadder(overall * 100.0, 0.0, 100.0, segments = 30, tint = SpectraColors.Phosphor)
                } else if (total == 0) {
                    Lcd(s.stage, color = SpectraColors.PhosphorDim, size = 12.sp)
                } else {
                    Lcd(
                        "Finished: ${finished - failed} analyzed, $failed failed" + if (waiting > 0) ", $waiting not done" else "",
                        color = SpectraColors.PhosphorDim,
                        size = 12.sp
                    )
                }
            }
        }
        if (total > 0) {
            Spacer(Modifier.height(10.dp))
            ReadoutGrid(
                listOf(
                    Readout("Likely fake", countOf(Severity.BAD).toString(), SpectraColors.Red),
                    Readout("Suspicious", countOf(Severity.WARN).toString(), SpectraColors.Amber),
                    Readout("Inconclusive", countOf(Severity.INFO).toString(), SpectraColors.Blue),
                    Readout("No lossy sign", countOf(Severity.GOOD).toString(), SpectraColors.Green)
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        if (s.running) {
            HwButton("STOP SCAN", Modifier.fillMaxWidth(), height = 46.dp, onClick = vm::stop)
        } else if (total > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HwButton("SAVE CSV", Modifier.weight(1f), height = 44.dp) {
                    scope.launch {
                        try {
                            val where = saveCsvToDownloads(context, batchCsv(s.items))
                            Toast.makeText(context, "CSV saved to $where", Toast.LENGTH_LONG).show()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not save: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                HwButton("SHARE CSV", Modifier.weight(1f), height = 44.dp) {
                    shareReportText(context, batchCsv(s.items))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HwButton("BACK", Modifier.weight(1f), height = 40.dp, onClick = vm::hide)
            HwButton("CLEAR", Modifier.weight(1f), height = 40.dp, onClick = vm::close)
        }
    }

    val rows = s.items
        .filter { it.status == BatchStatus.DONE || it.status == BatchStatus.FAILED }
        .sortedWith(compareBy<BatchItem>({ rank(it) }, { it.path.lowercase() }))
    if (rows.isNotEmpty()) {
        RackPanel("Results") {
            Text(
                if (s.running) "Tap a file for its full report once the scan is done." else "Tap a file for its full report.",
                color = SpectraColors.InkSoft,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            for (item in rows) {
                BatchRowView(item, enabled = item.status == BatchStatus.DONE && !s.running) { onOpen(item.uri) }
            }
        }
    }
}

@Composable
private fun BatchRowView(item: BatchItem, enabled: Boolean, onClick: () -> Unit) {
    val r = item.row
    val tone = when {
        r == null -> SpectraColors.Red
        else -> sevColor(r.severity)
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Led(tone, true, 10.dp, Modifier.padding(top = 4.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Engraved(item.path, size = 12.sp, spacing = 0.2.sp, maxLines = 2)
            if (r != null) {
                Text(
                    r.headline + (r.evidence?.let { " · evidence $it%" } ?: "") + (r.estimatedSource?.let { " · $it" } ?: ""),
                    color = SpectraColors.Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val depth = if (r.effectiveBits != null) " · ${r.declaredBits ?: "?"}/${r.effectiveBits} bit" else ""
                Text(
                    "${r.codec} · ${fmtHz(r.sampleRate.toDouble())} · ${r.channels} ch$depth · ${fmtLufs(r.lufs)} · " +
                        "DR ${r.dr?.roundToInt() ?: "—"} · " +
                        (if (r.hasEdge) "edge ${fmtHz(r.cutoffHz)}" else "no edge"),
                    color = SpectraColors.InkSoft,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            } else {
                Text(item.error ?: "Failed", color = SpectraColors.Red, fontSize = 12.sp)
            }
        }
    }
}
