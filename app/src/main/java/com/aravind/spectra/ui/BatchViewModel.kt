package com.aravind.spectra.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aravind.spectra.dsp.Severity
import com.aravind.spectra.dsp.VerdictEngine
import com.aravind.spectra.model.UiState
import com.aravind.spectra.notify.Notifier
import com.aravind.spectra.notify.ProgressHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BatchStatus { PENDING, RUNNING, DONE, FAILED }

data class BatchRow(
    val severity: Severity,
    val headline: String,
    val evidence: Int?,
    val estimatedSource: String?,
    val codec: String,
    val sampleRate: Int,
    val channels: Int,
    val declaredBits: Int?,
    val effectiveBits: Int?,
    val durationSec: Double,
    val sizeBytes: Long,
    val lufs: Double?,
    val lra: Double?,
    val dr: Double?,
    val peakDb: Double,
    val truePeakDb: Double,
    val clipRuns: Int,
    val hasEdge: Boolean,
    val cutoffHz: Double,
    val edgeStrengthDb: Double,
    val edgeDropDb: Double,
    val lock: Double?,
    val warnings: Int,
    val bitrateKbps: Int?,
    val encoder: String?
)

data class BatchItem(
    val uri: Uri,
    val path: String,
    val status: BatchStatus,
    val row: BatchRow?,
    val error: String?
)

data class BatchState(
    val visible: Boolean = false,
    val running: Boolean = false,
    val label: String = "",
    val items: List<BatchItem> = emptyList(),
    val current: Int = -1,
    val stage: String = "",
    val fileProgress: Float? = null
)

class BatchViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(BatchState())
    val state: StateFlow<BatchState> = _state.asStateFlow()

    private var job: Job? = null

    fun startFolder(tree: Uri) {
        val app = getApplication<Application>()
        try {
            app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // not persistable: the grant still lasts while the app runs
        }
        val folder = DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').substringAfterLast('/')
        start("Folder: ${folder.ifEmpty { "storage" }}") { FolderScanner.list(app, tree) }
    }

    fun startFiles(uris: List<Uri>) {
        val app = getApplication<Application>()
        start("${uris.size} selected files") { uris.map { it to displayName(app, it) } }
    }

    fun show() {
        _state.value = _state.value.copy(visible = true)
    }

    fun hide() {
        _state.value = _state.value.copy(visible = false)
    }

    fun stop() {
        job?.cancel()
    }

    fun close() {
        job?.cancel()
        _state.value = BatchState()
    }

    private fun start(label: String, discover: () -> List<Pair<Uri, String>>) {
        job?.cancel()
        val app = getApplication<Application>()
        job = viewModelScope.launch {
            _state.value = BatchState(visible = true, running = true, label = label, stage = "Looking for audio files…")
            val found = try {
                withContext(Dispatchers.IO) { discover() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (found.isEmpty()) {
                _state.value = BatchState(visible = true, running = false, label = label, stage = "No audio files found")
                return@launch
            }

            val items = found.map { BatchItem(it.first, it.second, BatchStatus.PENDING, null, null) }.toMutableList()
            val total = items.size
            val hubId = ProgressHub.begin(app, ProgressHub.Kind.BATCH, "Scanning $label", "0 / $total")
            try {
                for (i in 0 until total) {
                    items[i] = items[i].copy(status = BatchStatus.RUNNING)
                    publish(label, items, i, "Starting…", null)
                    try {
                        val s = AnalysisRunner.run(app, items[i].uri) { stage, p ->
                            publish(label, items, i, stage, p)
                            ProgressHub.update(app, ProgressHub.Kind.BATCH, hubId, "${i + 1} / $total · $stage", (i + (p ?: 0f)) / total)
                        }
                        items[i] = items[i].copy(status = BatchStatus.DONE, row = rowOf(s))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: OutOfMemoryError) {
                        items[i] = items[i].copy(status = BatchStatus.FAILED, error = "Out of memory")
                    } catch (e: Exception) {
                        items[i] = items[i].copy(status = BatchStatus.FAILED, error = e.message ?: e.toString())
                    }
                    publish(label, items, i, "", null)
                }
                val suspicious = items.count {
                    it.row != null && (it.row.severity == Severity.BAD || it.row.severity == Severity.WARN)
                }
                val failed = items.count { it.status == BatchStatus.FAILED }
                Notifier.scanDone(app, "$total files scanned · $suspicious suspicious · $failed failed")
            } finally {
                ProgressHub.end(app, ProgressHub.Kind.BATCH, hubId)
                val now = _state.value
                _state.value = now.copy(
                    items = now.items.map { if (it.status == BatchStatus.RUNNING) it.copy(status = BatchStatus.PENDING) else it },
                    running = false,
                    current = -1,
                    stage = "",
                    fileProgress = null
                )
            }
        }
    }

    private fun publish(label: String, items: List<BatchItem>, current: Int, stage: String, p: Float?) {
        _state.value = BatchState(
            visible = _state.value.visible,
            running = true,
            label = label,
            items = items.toList(),
            current = current,
            stage = stage,
            fileProgress = p
        )
    }

    private fun rowOf(s: UiState.Success): BatchRow {
        val a = s.analysis
        val v = s.verdict
        val lossless = VerdictEngine.isLossless(s.info.codecMime)
        return BatchRow(
            severity = v.severity,
            headline = v.headline,
            evidence = v.confidence,
            estimatedSource = v.estimatedSource,
            codec = codecName(s.info.codecMime),
            sampleRate = a.sampleRate,
            channels = a.channelCount,
            declaredBits = if (lossless) s.info.declaredBits else null,
            effectiveBits = if (lossless) a.effectiveBits else null,
            durationSec = a.durationSec,
            sizeBytes = s.fileSizeBytes,
            lufs = a.lufs,
            lra = a.lra,
            dr = a.dr,
            peakDb = a.peakDb,
            truePeakDb = a.truePeakDb,
            clipRuns = a.clipRuns,
            hasEdge = a.cutoff.hasEdge,
            cutoffHz = a.cutoff.cutoffHz,
            edgeStrengthDb = a.cutoff.strengthDb,
            edgeDropDb = a.cutoff.dropDb,
            lock = a.cutoff.lockRatio,
            warnings = v.findings.count { it.severity == Severity.WARN || it.severity == Severity.BAD },
            bitrateKbps = s.info.facts?.bitrateKbps,
            encoder = s.info.facts?.encoder
        )
    }

    private fun displayName(context: Context, uri: Uri): String {
        var name = uri.lastPathSegment ?: "file"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        return name
    }
}

/** Walks a folder picked with the system file picker (sub-folders included) and returns its audio files. */
object FolderScanner {
    private val AUDIO_EXT = setOf(
        "flac", "wav", "mp3", "m4a", "aac", "ogg", "oga", "opus", "wma",
        "aif", "aiff", "alac", "ape", "wv", "tta", "mka"
    )
    private const val MAX_FILES = 3000

    fun list(context: Context, tree: Uri): List<Pair<Uri, String>> {
        val resolver = context.contentResolver
        val out = ArrayList<Pair<Uri, String>>()

        fun walk(docId: String, prefix: String, depth: Int) {
            if (depth > 10 || out.size >= MAX_FILES) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val cols = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            resolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(id, "$prefix$name/", depth + 1)
                    } else if (mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AUDIO_EXT) {
                        out += DocumentsContract.buildDocumentUriUsingTree(tree, id) to "$prefix$name"
                    }
                }
            }
        }

        walk(DocumentsContract.getTreeDocumentId(tree), "", 0)
        out.sortBy { it.second.lowercase() }
        return out
    }
}

// ---------------------------------------------------------------- CSV

private fun esc(s: String): String =
    if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

private fun num(x: Double?, digits: Int = 2): String =
    if (x == null || !x.isFinite()) "" else String.format(Locale.US, "%.${digits}f", x)

fun batchCsv(items: List<BatchItem>): String = buildString {
    appendLine(
        "path,status,verdict,evidence_pct,estimated_source,codec,sample_rate_hz,channels,declared_bits," +
            "effective_bits,duration_s,size_bytes,lufs,lra_lu,dr,sample_peak_db,true_peak_db,clip_runs," +
            "edge_found,edge_hz,edge_steepness_db,edge_drop_db,hf_persistence,warnings,bitrate_kbps,encoder,error"
    )
    for (item in items) {
        val r = item.row
        appendLine(
            listOf(
                esc(item.path),
                item.status.name.lowercase(),
                esc(r?.headline ?: ""),
                r?.evidence?.toString() ?: "",
                esc(r?.estimatedSource ?: ""),
                esc(r?.codec ?: ""),
                r?.sampleRate?.toString() ?: "",
                r?.channels?.toString() ?: "",
                r?.declaredBits?.toString() ?: "",
                r?.effectiveBits?.toString() ?: "",
                num(r?.durationSec, 1),
                r?.sizeBytes?.toString() ?: "",
                num(r?.lufs, 1),
                num(r?.lra, 1),
                num(r?.dr, 1),
                num(r?.peakDb, 2),
                num(r?.truePeakDb, 2),
                r?.clipRuns?.toString() ?: "",
                if (r == null) "" else if (r.hasEdge) "yes" else "no",
                if (r != null && r.hasEdge) num(r.cutoffHz, 0) else "",
                if (r != null && r.hasEdge) num(r.edgeStrengthDb, 1) else "",
                if (r != null && r.hasEdge) num(r.edgeDropDb, 1) else "",
                num(r?.lock, 2),
                r?.warnings?.toString() ?: "",
                r?.bitrateKbps?.toString() ?: "",
                esc(r?.encoder ?: ""),
                esc(item.error ?: "")
            ).joinToString(",")
        )
    }
}

/** Saves the CSV to Download/Spectra. Returns where it went. */
suspend fun saveCsvToDownloads(context: Context, text: String): String = withContext(Dispatchers.IO) {
    val name = "Spectra-scan-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.csv"
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Spectra")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create the file")
        val out = resolver.openOutputStream(uri) ?: error("Could not open the file")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "Download/Spectra/$name"
    } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val f = File(dir, name)
        f.writeText(text)
        f.absolutePath
    }
}
