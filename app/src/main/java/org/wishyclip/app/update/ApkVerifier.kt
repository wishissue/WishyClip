package org.wishyclip.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

enum class VerifyResult { OK, UNREADABLE, WRONG_PACKAGE, NOT_NEWER, DIFFERENT_KEY }

/**
 * Checks a downloaded APK before the system installer sees it. Android would refuse a package signed with a
 * different key anyway ("App not installed"); checking first lets the app explain what happened and keeps
 * the user's projects in mind (they live in the project folder, not in the app).
 */
object ApkVerifier {
    fun verify(context: Context, apk: File): VerifyResult {
        val pm = context.packageManager
        val candidate = archiveInfo(pm, apk) ?: return VerifyResult.UNREADABLE
        if (candidate.packageName != context.packageName) return VerifyResult.WRONG_PACKAGE
        val installed = try {
            installedInfo(pm, context.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            return VerifyResult.UNREADABLE
        }
        if (PackageInfoCompat.getLongVersionCode(candidate) <= PackageInfoCompat.getLongVersionCode(installed)) {
            return VerifyResult.NOT_NEWER
        }
        val a = signerHashes(installed)
        val b = signerHashes(candidate)
        if (b.isEmpty()) return VerifyResult.UNREADABLE
        return if (signersCompatible(a, b)) VerifyResult.OK else VerifyResult.DIFFERENT_KEY
    }

    /** The two packages share a signing certificate (a rotated key keeps the old one in its history). */
    fun signersCompatible(installed: Set<String>, candidate: Set<String>): Boolean =
        installed.isNotEmpty() && candidate.isNotEmpty() && installed.any { it in candidate }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return try { pm.getPackageArchiveInfo(apk.absolutePath, flags) } catch (e: Exception) { null }
    }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, pkg: String): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return pm.getPackageInfo(pkg, flags)
    }

    @Suppress("DEPRECATION")
    private fun signerHashes(info: PackageInfo): Set<String> {
        val out = HashSet<String>()
        if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo ?: return out
            val sigs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            sigs?.forEach { out.add(sha256Hex(it.toByteArray())) }
        } else {
            info.signatures?.forEach { out.add(sha256Hex(it.toByteArray())) }
        }
        return out
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
