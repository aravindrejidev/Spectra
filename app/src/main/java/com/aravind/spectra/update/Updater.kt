package com.aravind.spectra.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long
)

sealed class UpdateState {
    object Hidden : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState()
    data class Ready(val info: UpdateInfo, val file: File, val needsPermission: Boolean) : UpdateState()
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState()
}

object UpdateChecker {
    private const val REPO = "aravindrejidev/Spectra"

    fun currentVersion(context: Context): String {
        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "0"
        } catch (e: Exception) {
            "0"
        }
    }

    /** Latest published GitHub release that carries an .apk, or null. */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Spectra-App")
            if (conn.responseCode != 200) return@withContext null

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name", "")
            if (tag.isEmpty()) return@withContext null

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var url: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            val apk = url ?: return@withContext null
            if (apk.isEmpty()) return@withContext null

            UpdateInfo(tag, tag.trim().trimStart('v', 'V'), json.optString("body", ""), apk, size)
        } finally {
            conn.disconnect()
        }
    }

    private fun parts(v: String): List<Int> =
        v.trim().trimStart('v', 'V').split('.', '-', '+')
            .takeWhile { it.toIntOrNull() != null }
            .map { it.toInt() }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Turns GitHub markdown into plain text for the popup. */
    fun cleanNotes(md: String): String =
        md.replace("\r", "").lines().joinToString("\n") { raw ->
            var l = raw.trimEnd()
            val t = l.trimStart()
            if (t.startsWith("#")) {
                l = t.trimStart('#').trim()
            } else if (t.startsWith("- ") || t.startsWith("* ")) {
                l = "• " + t.drop(2)
            }
            l.replace("**", "").replace("__", "").replace("`", "")
        }.trim()
}

object UpdateInstaller {

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates")
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "Spectra-v${info.version}.apk")

            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Spectra-App")
                conn.connect()
                check(conn.responseCode in 200..299) { "Server answered ${conn.responseCode}" }

                val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else info.sizeBytes
                var done = 0L
                var lastPct = -1
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct / 100f)
                                }
                            }
                        }
                    }
                }
                check(info.sizeBytes <= 0 || out.length() == info.sizeBytes) { "Download incomplete, try again" }
            } finally {
                conn.disconnect()
            }
            out
        }

    /** Makes sure the downloaded file really is a Spectra APK. */
    fun verify(context: Context, file: File) {
        @Suppress("DEPRECATION")
        val pi = context.packageManager.getPackageArchiveInfo(file.path, 0)
        check(pi != null && pi.packageName == context.packageName) { "The downloaded file is not a Spectra update" }
    }

    /** Returns false when the user must first allow installs from this app (settings were opened). */
    fun install(context: Context, file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
