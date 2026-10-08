package com.aravind.spectra.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aravind.spectra.notify.AppState
import com.aravind.spectra.notify.Notifier
import com.aravind.spectra.notify.ProgressHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("spectra_update", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Hidden)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String = UpdateChecker.currentVersion(app)

    private var job: Job? = null

    init {
        checkForUpdate()
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val info = try {
                UpdateChecker.fetchLatest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (info != null &&
                UpdateChecker.isNewer(info.version, currentVersion) &&
                prefs.getString("skipped_version", null) != info.version
            ) {
                _state.value = UpdateState.Available(info)
            }
        }
    }

    private fun currentInfo(): UpdateInfo? = when (val s = _state.value) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Ready -> s.info
        is UpdateState.Failed -> s.info
        is UpdateState.Hidden -> null
    }

    fun later() {
        job?.cancel()
        _state.value = UpdateState.Hidden
    }

    fun skipVersion() {
        currentInfo()?.let { prefs.edit().putString("skipped_version", it.version).apply() }
        later()
    }

    fun cancelDownload() {
        job?.cancel()
        currentInfo()?.let { _state.value = UpdateState.Available(it) }
    }

    fun startDownload() {
        val info = currentInfo() ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = UpdateState.Downloading(info, 0f)
            val app = getApplication<Application>()
            val hubId = ProgressHub.begin(app, ProgressHub.Kind.UPDATE, "Downloading Spectra v${info.version}", "Starting…")
            try {
                val file = UpdateInstaller.download(app, info) { p ->
                    if (_state.value is UpdateState.Downloading) _state.value = UpdateState.Downloading(info, p)
                    ProgressHub.update(app, ProgressHub.Kind.UPDATE, hubId, "${(p * 100).roundToInt()}%", p)
                }
                UpdateInstaller.verify(app, file)
                ProgressHub.end(app, ProgressHub.Kind.UPDATE, hubId)
                launchInstall(info, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                _state.value = UpdateState.Failed(info, msg)
                if (!AppState.foreground) Notifier.updateFailed(app, msg)
            } finally {
                ProgressHub.end(app, ProgressHub.Kind.UPDATE, hubId)
            }
        }
    }

    fun installAgain() {
        val s = _state.value
        if (s is UpdateState.Ready) launchInstall(s.info, s.file)
    }

    private fun launchInstall(info: UpdateInfo, file: File) {
        val app = getApplication<Application>()
        if (!AppState.foreground) {
            // Android won't open the installer from the background: a notification does it instead
            _state.value = UpdateState.Ready(info, file, needsPermission = false)
            Notifier.updateReady(app, info.version, file)
            return
        }
        val started = try {
            UpdateInstaller.install(app, file)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(info, e.message ?: e.toString())
            return
        }
        _state.value = UpdateState.Ready(info, file, needsPermission = !started)
    }
}
