package com.aravind.spectra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aravind.spectra.ui.theme.SpectraColors
import com.aravind.spectra.update.UpdateChecker
import com.aravind.spectra.update.UpdateState
import com.aravind.spectra.update.UpdateViewModel
import kotlin.math.roundToInt

/** Shows the themed "update available" popup when the updater finds a newer release. */
@Composable
fun UpdatePrompt(vm: UpdateViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val s = state
    if (s is UpdateState.Hidden) return

    Dialog(
        onDismissRequest = { if (s !is UpdateState.Downloading) vm.later() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            RackPanel("Update available", lamp = SpectraColors.Amber) {
                when (s) {
                    is UpdateState.Available -> {
                        VersionPlate(vm.currentVersion, s.info.version)
                        Spacer(Modifier.height(12.dp))
                        NotesPlate(s.info.notes)
                        if (s.info.sizeBytes > 0) {
                            Spacer(Modifier.height(8.dp))
                            Engraved("DOWNLOAD SIZE  ${fmtBytes(s.info.sizeBytes)}", size = 11.sp, spacing = 1.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        HwButton("DOWNLOAD & INSTALL", Modifier.fillMaxWidth(), height = 52.dp, onClick = vm::startDownload)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HwButton("DON'T REMIND", Modifier.weight(1f), height = 40.dp, onClick = vm::skipVersion)
                            HwButton("LATER", Modifier.weight(1f), height = 40.dp, onClick = vm::later)
                        }
                    }
                    is UpdateState.Downloading -> {
                        GlassScreen {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Lcd("DOWNLOADING v${s.info.version}", size = 14.sp)
                                Spacer(Modifier.height(12.dp))
                                LedLadder((s.progress * 100f).toDouble(), 0.0, 100.0, segments = 30, tint = SpectraColors.Phosphor)
                                Spacer(Modifier.height(8.dp))
                                Lcd("${(s.progress * 100f).roundToInt()} %", color = SpectraColors.PhosphorDim, size = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        HwButton("CANCEL", Modifier.fillMaxWidth(), onClick = vm::cancelDownload)
                    }
                    is UpdateState.Ready -> {
                        GlassScreen {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Lcd(
                                    if (s.needsPermission) "ALLOW INSTALLS FIRST" else "READY TO INSTALL",
                                    color = SpectraColors.Amber,
                                    size = 14.sp,
                                    weight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (s.needsPermission) {
                                        "Settings just opened. Turn on the switch for Spectra, come back here and tap INSTALL."
                                    } else {
                                        "Android's installer should open now. If it didn't, tap INSTALL."
                                    },
                                    color = SpectraColors.Phosphor.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        HwButton("INSTALL", Modifier.fillMaxWidth(), height = 52.dp, onClick = vm::installAgain)
                        Spacer(Modifier.height(10.dp))
                        HwButton("LATER", Modifier.fillMaxWidth(), height = 40.dp, onClick = vm::later)
                    }
                    is UpdateState.Failed -> {
                        GlassScreen {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Lcd("UPDATE FAILED", color = SpectraColors.Red, size = 14.sp, weight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Lcd(s.message, color = SpectraColors.Amber, size = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HwButton("RETRY", Modifier.weight(1f), onClick = vm::startDownload)
                            HwButton("LATER", Modifier.weight(1f), onClick = vm::later)
                        }
                    }
                    is UpdateState.Hidden -> Unit
                }
            }
        }
    }
}

@Composable
private fun VersionPlate(current: String, latest: String) {
    GlassScreen {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Lcd("CURRENT", color = SpectraColors.PhosphorDim, size = 10.sp)
                Lcd("v$current", size = 18.sp, weight = FontWeight.Bold)
            }
            Lcd("→", color = SpectraColors.Amber, size = 24.sp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Lcd("NEW", color = SpectraColors.PhosphorDim, size = 10.sp)
                Lcd("v$latest", color = SpectraColors.Amber, size = 18.sp, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NotesPlate(notes: String) {
    Engraved("WHAT'S NEW", size = 11.sp, spacing = 1.5.sp)
    Spacer(Modifier.height(6.dp))
    GlassScreen {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            Text(
                UpdateChecker.cleanNotes(notes).ifBlank { "No release notes." },
                color = SpectraColors.Phosphor.copy(alpha = 0.9f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
