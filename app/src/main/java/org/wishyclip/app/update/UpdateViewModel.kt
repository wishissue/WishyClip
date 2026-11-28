package org.wishyclip.app.update

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wishyclip.app.BuildConfig
import org.wishyclip.app.data.SettingsStore

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    /** Only after a check the user asked for. */
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val done: Long, val total: Long) : UpdateState
    data class Ready(val info: ReleaseInfo, val apk: File) : UpdateState
    /** Android needs the one-time "install unknown apps" permission for this app. */
    data class NeedsPermission(val info: ReleaseInfo, val apk: File) : UpdateState
    /** The new APK is signed with a different key than the installed app: Android cannot update over it. */
    data class KeyMismatch(val info: ReleaseInfo) : UpdateState
    data class Failed(val message: String, val info: ReleaseInfo?) : UpdateState
}

/**
 * Activity-wide update flow: check GitHub, ask, download, verify, hand to the system installer.
 *
 * "Later" only hides the prompt for this run of the app; the next start asks again.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore(app.applicationContext)
    private val checker = UpdateChecker()
    private var job: Job? = null
    @Volatile private var cancelRequested = false

    var state: UpdateState by mutableStateOf(UpdateState.Idle)
        private set

    /** True after the user pressed "Later": the dialog stays hidden until the next start or a manual check. */
    var promptHidden by mutableStateOf(false)
        private set

    var autoCheck by mutableStateOf(true)
        private set

    /** The system installer was already opened once for this download (do not pop it open again by itself). */
    var installLaunched by mutableStateOf(false)
        private set

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val supported: Boolean get() = BuildConfig.SELF_UPDATE && !BuildConfig.DEBUG

    init {
        cleanLeftovers()
        viewModelScope.launch {
            autoCheck = settings.updateChecks.first()
            if (supported && autoCheck) check(manual = false)
        }
    }

    fun updateAutoCheck(enabled: Boolean) {
        autoCheck = enabled
        viewModelScope.launch { settings.saveUpdateChecks(enabled) }
    }

    fun checkNow() {
        promptHidden = false
        check(manual = true)
    }

    private fun check(manual: Boolean) {
        if (!supported || state is UpdateState.Downloading || state is UpdateState.Checking) return
        job?.cancel()
        if (manual) state = UpdateState.Checking
        job = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { checker.check(currentVersion) }
            state = when (result) {
                is CheckResult.Available -> UpdateState.Available(result.info)
                CheckResult.UpToDate -> if (manual) UpdateState.UpToDate else UpdateState.Idle
                // A failed check at start-up is silent: no internet must never nag.
                is CheckResult.Failed -> if (manual) UpdateState.Failed(result.message, null) else UpdateState.Idle
            }
        }
    }

    fun later() { promptHidden = true }

    fun dismissMessage() {
        if (state is UpdateState.UpToDate || state is UpdateState.Failed || state is UpdateState.KeyMismatch) state = UpdateState.Idle
    }

    fun startDownload() {
        val info = when (val s = state) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info ?: return
            else -> return
        }
        promptHidden = false
        installLaunched = false
        cancelRequested = false
        state = UpdateState.Downloading(info, 0, info.apkSize)
        job = viewModelScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) { downloadAndVerify(info) }
                finishDownload(info, apk)
            } catch (e: CancellationException) {
                state = UpdateState.Available(info)
                throw e
            } catch (e: UpdateException) {
                state = if (e.kind == UpdateException.Kind.CANCELLED) UpdateState.Available(info)
                else UpdateState.Failed(friendly(e), info)
            } catch (e: Exception) {
                state = UpdateState.Failed("The update could not be downloaded.", info)
            }
        }
    }

    fun cancelDownload() { cancelRequested = true }

    private fun downloadAndVerify(info: ReleaseInfo): File {
        val dir = UpdateInstaller.updatesDir(getApplication())
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, info.apkName)
        val actualHash = UpdateNetwork.download(
            info.apkUrl, target,
            onProgress = { done, total -> state = UpdateState.Downloading(info, done, if (total > 0) total else info.apkSize) },
            isCancelled = { cancelRequested }
        )
        val expected = info.sha256Url?.let { url ->
            UpdateNetwork.fetchText(url)?.let { ReleaseParser.parseSha256(it, info.apkName) }
        }
        if (info.sha256Url != null && expected == null) {
            target.delete()
            throw UpdateException(UpdateException.Kind.CHECKSUM, "The checksum file could not be read")
        }
        if (expected != null && expected != actualHash) {
            target.delete()
            throw UpdateException(UpdateException.Kind.CHECKSUM, "The downloaded file is damaged")
        }
        return target
    }

    private fun finishDownload(info: ReleaseInfo, apk: File) {
        when (ApkVerifier.verify(getApplication(), apk)) {
            VerifyResult.OK -> state = if (UpdateInstaller.canInstall(getApplication())) UpdateState.Ready(info, apk)
            else UpdateState.NeedsPermission(info, apk)
            VerifyResult.DIFFERENT_KEY -> { apk.delete(); state = UpdateState.KeyMismatch(info) }
            VerifyResult.NOT_NEWER -> { apk.delete(); state = UpdateState.UpToDate }
            VerifyResult.WRONG_PACKAGE, VerifyResult.UNREADABLE -> {
                apk.delete()
                state = UpdateState.Failed("The downloaded file is not a valid WishyClip update.", info)
            }
        }
    }

    /** Called when the app comes back to the front: the user may just have granted the install permission. */
    fun refreshPermission() {
        val s = state
        if (s is UpdateState.NeedsPermission && UpdateInstaller.canInstall(getApplication())) {
            state = UpdateState.Ready(s.info, s.apk)
        }
    }

    fun markInstallLaunched() { installLaunched = true }

    fun installIntent(): Intent? {
        val s = state as? UpdateState.Ready ?: return null
        return UpdateInstaller.installIntent(getApplication(), s.apk)
    }

    fun permissionIntent(): Intent = UpdateInstaller.permissionIntent(getApplication())

    private fun cleanLeftovers() {
        // A finished update leaves the old download in the cache; it is never needed again.
        runCatching { UpdateInstaller.updatesDir(getApplication()).listFiles()?.forEach { it.delete() } }
    }

    private fun friendly(e: UpdateException): String = when (e.kind) {
        UpdateException.Kind.NETWORK -> "No internet connection, or it was lost. Try again."
        UpdateException.Kind.CHECKSUM -> "The downloaded file was damaged, so it was discarded. Try again."
        UpdateException.Kind.TOO_LARGE -> "The update file is larger than expected, so it was not downloaded."
        UpdateException.Kind.BAD_RESPONSE -> "GitHub did not give the update file. Try again later."
        UpdateException.Kind.CANCELLED -> "Cancelled."
    }
}
