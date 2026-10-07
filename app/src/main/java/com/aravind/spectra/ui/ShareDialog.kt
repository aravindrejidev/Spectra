package com.aravind.spectra.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aravind.spectra.model.UiState
import com.aravind.spectra.ui.theme.SpectraColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

fun shareReportText(context: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Spectra report")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(i, "Share report"))
}

/** Themed popup behind the SHARE button: text, or a full-report image saved to the gallery. */
@Composable
fun ShareDialog(
    s: UiState.Success,
    view: String,
    logScale: Boolean,
    minDb: Float,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            RackPanel("Share report", lamp = SpectraColors.Blue) {
                if (busy) {
                    GlassScreen {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Lcd("RENDERING IMAGE", size = 14.sp)
                            Spacer(Modifier.height(10.dp))
                            Lcd("Building the full report, a few seconds…", color = SpectraColors.PhosphorDim, size = 12.sp)
                        }
                    }
                } else {
                    HwButton("SHARE AS TEXT", Modifier.fillMaxWidth(), height = 52.dp) {
                        shareReportText(context, buildReport(s))
                        onDismiss()
                    }
                    Spacer(Modifier.height(12.dp))
                    HwButton("SAVE AS IMAGE", Modifier.fillMaxWidth(), height = 52.dp) {
                        busy = true
                        scope.launch {
                            try {
                                val bmp = ReportImage.render(context, s, view, logScale, minDb)
                                val where = ReportImage.saveToGallery(context, bmp, s.tags.title ?: s.fileName)
                                Toast.makeText(context, "Report image saved to $where", Toast.LENGTH_LONG).show()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not save the image: ${e.message}", Toast.LENGTH_LONG).show()
                            } catch (e: OutOfMemoryError) {
                                Toast.makeText(context, "Not enough memory to build the image", Toast.LENGTH_LONG).show()
                            }
                            busy = false
                            onDismiss()
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HwButton("CANCEL", Modifier.fillMaxWidth(), height = 40.dp, onClick = onDismiss)
                }
            }
        }
    }
}
