package org.wishyclip.app.update

import org.wishyclip.app.BuildConfig

sealed interface CheckResult {
    data object UpToDate : CheckResult
    data class Available(val info: ReleaseInfo) : CheckResult
    data class Failed(val message: String) : CheckResult
}

/** Asks GitHub for the newest published (non-draft, non-pre-release) release and compares versions. */
class UpdateChecker(
    private val latestUrl: String = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
) {
    /** Blocking; call from a background thread. */
    fun check(currentVersion: String): CheckResult = try {
        val body = UpdateNetwork.fetchText(latestUrl)
        val info = body?.let { ReleaseParser.parse(it) }
        when {
            info == null -> CheckResult.UpToDate
            ReleaseParser.isNewer(info.version, currentVersion) -> CheckResult.Available(info)
            else -> CheckResult.UpToDate
        }
    } catch (e: UpdateException) {
        CheckResult.Failed(
            if (e.kind == UpdateException.Kind.NETWORK) "No internet connection." else "Could not check for updates."
        )
    } catch (e: Exception) {
        CheckResult.Failed("Could not check for updates.")
    }
}
