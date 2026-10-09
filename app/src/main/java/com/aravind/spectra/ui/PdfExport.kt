package com.aravind.spectra.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.aravind.spectra.dsp.Severity
import com.aravind.spectra.model.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val M = 36f

private const val C_INK = 0xFF23262B.toInt()
private const val C_TEXT = 0xFF333840.toInt()
private const val C_GRAY = 0xFF6B717B.toInt()
private const val C_LINE = 0xFFD5D8DD.toInt()
private const val C_RED = 0xFFD93025.toInt()
private const val C_AMBER = 0xFFE08A00.toInt()
private const val C_BLUE = 0xFF2F7FD1.toInt()
private const val C_GREEN = 0xFF1E9E55.toInt()

private class RowBox(
    val item: BatchItem,
    val path: StaticLayout,
    val l1: StaticLayout,
    val l2: StaticLayout?,
    val h: Float
)

private fun pdfPaint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        textAlign = align
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

private fun pdfLayout(text: String, paint: TextPaint, width: Float, maxLines: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.1f)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

private fun drawLayoutAt(c: Canvas, l: StaticLayout, x: Float, y: Float) {
    c.save()
    c.translate(x, y)
    l.draw(c)
    c.restore()
}

private fun sevInt(s: Severity?): Int = when (s) {
    Severity.BAD -> C_RED
    Severity.WARN -> C_AMBER
    Severity.INFO -> C_BLUE
    Severity.GOOD -> C_GREEN
    null -> C_GRAY
}

private fun rankOf(i: BatchItem): Int =
    if (i.status == BatchStatus.FAILED) 2
    else when (i.row?.severity) {
        Severity.BAD -> 0
        Severity.WARN -> 1
        Severity.INFO -> 3
        else -> 4
    }

private fun headlineOf(r: BatchRow): String =
    r.headline + (r.evidence?.let { " · evidence $it%" } ?: "") + (r.estimatedSource?.let { " · $it" } ?: "")

private fun detailOf(r: BatchRow): String {
    val bits = if (r.effectiveBits != null) " · ${r.declaredBits ?: "?"}/${r.effectiveBits} bit" else ""
    return "${r.codec} · ${fmtHz(r.sampleRate.toDouble())} · ${r.channels} ch$bits · ${fmtLufs(r.lufs)} · " +
        "LRA ${r.lra?.let { f1(it) } ?: "—"} · DR ${r.dr?.roundToInt() ?: "—"} · TP ${f1(r.truePeakDb)} dB · " +
        "clip runs ${r.clipRuns} · " + (if (r.hasEdge) "edge ${fmtHz(r.cutoffHz)}" else "no edge")
}

private fun rowBox(item: BatchItem, textW: Float): RowBox {
    val r = item.row
    val path = pdfLayout(item.path, pdfPaint(9f, C_INK, bold = true), textW, 2)
    val l1 = if (r != null) pdfLayout(headlineOf(r), pdfPaint(8.5f, C_TEXT), textW, 2)
    else pdfLayout(item.error ?: "Failed", pdfPaint(8.5f, C_RED), textW, 2)
    val l2 = if (r != null) pdfLayout(detailOf(r), pdfPaint(7.5f, C_GRAY), textW, 3) else null
    return RowBox(item, path, l1, l2, path.height + l1.height + (l2?.height ?: 0) + 13f)
}

private fun drawRow(c: Canvas, b: RowBox, top: Float) {
    val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    dot.color = if (b.item.row == null) C_RED else sevInt(b.item.row.severity)
    c.drawCircle(M + 6f, top + 6f, 3.6f, dot)
    var y = top
    drawLayoutAt(c, b.path, M + 18f, y)
    y += b.path.height + 1f
    drawLayoutAt(c, b.l1, M + 18f, y)
    y += b.l1.height
    b.l2?.let { drawLayoutAt(c, it, M + 18f, y) }
    val line = Paint()
    line.color = C_LINE
    line.strokeWidth = 0.5f
    c.drawLine(M, top + b.h - 5f, PAGE_W - M, top + b.h - 5f, line)
}

private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

private fun safeName(s: String): String =
    s.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(40).ifEmpty { "report" }

object PdfExport {

    /** One track: the skeuomorphic report as A4 pages. Returns where the PDF was saved. */
    suspend fun saveReportPdf(
        context: Context,
        s: UiState.Success,
        view: String,
        logScale: Boolean,
        minDb: Float
    ): String = withContext(Dispatchers.Default) {
        val pages = ReportImage.renderPages(context, s, view, logScale, minDb)
        val doc = PdfDocument()
        try {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            for ((i, bmp) in pages.withIndex()) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, i + 1).create())
                page.canvas.drawBitmap(bmp, null, Rect(0, 0, PAGE_W, PAGE_H), paint)
                doc.finishPage(page)
                bmp.recycle()
            }
            saveToDownloads(context, "Spectra-report-${safeName(s.tags.title ?: s.fileName)}-${stamp()}.pdf") {
                doc.writeTo(it)
            }
        } finally {
            doc.close()
        }
    }

    /** A batch scan as a text PDF (searchable, small), suspicious files first. */
    suspend fun saveBatchPdf(context: Context, label: String, items: List<BatchItem>): String =
        withContext(Dispatchers.Default) {
            val done = items.filter { it.status == BatchStatus.DONE || it.status == BatchStatus.FAILED }
            if (done.isEmpty()) error("Nothing to export yet")

            val textW = PAGE_W - 2 * M - 18f
            val boxes = done
                .sortedWith(compareBy<BatchItem>({ rankOf(it) }, { it.path.lowercase() }))
                .map { rowBox(it, textW) }

            val firstTop = M + 78f
            val laterTop = M + 10f
            val limit = PAGE_H - M - 22f
            val pages = ArrayList<ArrayList<RowBox>>()
            var cur = ArrayList<RowBox>()
            var y = firstTop
            for (b in boxes) {
                if (y + b.h > limit && cur.isNotEmpty()) {
                    pages += cur
                    cur = ArrayList()
                    y = laterTop
                }
                cur += b
                y += b.h
            }
            pages += cur

            val fake = done.count { it.row?.severity == Severity.BAD }
            val suspicious = done.count { it.row?.severity == Severity.WARN }
            val inconclusive = done.count { it.row?.severity == Severity.INFO }
            val clean = done.count { it.row?.severity == Severity.GOOD }
            val failed = done.count { it.status == BatchStatus.FAILED }
            val whenStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

            val doc = PdfDocument()
            try {
                for ((idx, rows) in pages.withIndex()) {
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, idx + 1).create())
                    val c = page.canvas
                    var top = laterTop
                    if (idx == 0) {
                        c.drawText("SPECTRA BATCH REPORT", M, M + 18f, pdfPaint(18f, C_INK, bold = true))
                        c.drawText("$label · $whenStr · ${done.size} files", M, M + 36f, pdfPaint(9f, C_GRAY))
                        c.drawText(
                            "Likely fake $fake · Suspicious $suspicious · Inconclusive $inconclusive · " +
                                "No lossy sign $clean · Failed $failed",
                            M, M + 54f, pdfPaint(9f, C_TEXT)
                        )
                        val rule = Paint()
                        rule.color = C_INK
                        rule.strokeWidth = 1f
                        c.drawLine(M, M + 62f, PAGE_W - M, M + 62f, rule)
                        top = firstTop
                    }
                    for (b in rows) {
                        drawRow(c, b, top)
                        top += b.h
                    }
                    c.drawText(
                        "Spectra · page ${idx + 1} / ${pages.size}",
                        PAGE_W / 2f, PAGE_H - 20f, pdfPaint(7.5f, C_GRAY, align = Paint.Align.CENTER)
                    )
                    doc.finishPage(page)
                }
                saveToDownloads(context, "Spectra-scan-${stamp()}.pdf") { doc.writeTo(it) }
            } finally {
                doc.close()
            }
        }

    private suspend fun saveToDownloads(context: Context, name: String, write: (OutputStream) -> Unit): String =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/Spectra")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create the file")
                val out = resolver.openOutputStream(uri) ?: error("Could not open the file")
                out.use { write(it) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Download/Spectra/$name"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val f = File(dir, name)
                f.outputStream().use { write(it) }
                f.absolutePath
            }
        }
}
