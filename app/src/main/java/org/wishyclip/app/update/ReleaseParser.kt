package org.wishyclip.app.update

import java.net.URI
import java.util.Locale
import org.json.JSONObject

/** One published GitHub release that the app could update to. */
data class ReleaseInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Download address of `<apk name>.sha256`, when the release has one. */
    val sha256Url: String?
)

/**
 * Pure rules for reading GitHub's "latest release" answer. No Android classes, so it is unit-tested.
 *
 * Only a normal release is ever offered: drafts, pre-releases (the debug test builds are published
 * as pre-releases), tags with a suffix like `-rc1`, and APKs whose name says "debug" or "test" are ignored.
 */
object ReleaseParser {
    private val apkName = Regex("^WishyClip-[0-9][0-9A-Za-z.]*\\.apk$")
    private const val MAX_NOTES = 2000

    fun parse(json: String): ReleaseInfo? {
        val root = try { JSONObject(json) } catch (e: Exception) { return null }
        if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) return null
        val tag = root.optString("tag_name", "").trim()
        val version = versionFromTag(tag) ?: return null
        val pageUrl = root.optString("html_url", "")
        val assets = root.optJSONArray("assets") ?: return null

        var apk: JSONObject? = null
        val shaByName = HashMap<String, String>()
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            val url = a.optString("browser_download_url", "")
            if (!isTrustedDownloadUrl(url)) continue
            if (name.endsWith(".apk.sha256")) shaByName[name] = url
            else if (apk == null && apkName.matches(name) && !name.contains("debug", true) && !name.contains("test", true)) apk = a
        }
        val chosen = apk ?: return null
        val name = chosen.optString("name")
        val sha = shaByName["$name.sha256"]
        return ReleaseInfo(
            tag = tag,
            version = version,
            notes = root.optString("body", "").trim().take(MAX_NOTES),
            pageUrl = pageUrl.takeIf { it.startsWith("https://github.com/") } ?: "https://github.com",
            apkName = name,
            apkUrl = chosen.optString("browser_download_url"),
            apkSize = chosen.optLong("size", -1L),
            sha256Url = sha
        )
    }

    /** "v1.2.0" -> "1.2.0"; null for anything that is not a plain numeric version (so `1.2.0-rc1` is never offered). */
    fun versionFromTag(tag: String): String? {
        val t = tag.removePrefix("v").removePrefix("V")
        return if (Regex("^[0-9]+(\\.[0-9]+){0,3}$").matches(t)) t else null
    }

    /** Numeric parts of a version; anything after `-` or `+` (like "-debug") is ignored. */
    fun parts(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        val out = ArrayList<Int>()
        for (p in core.split('.')) out.add(p.toIntOrNull() ?: return null)
        return out
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate) ?: return false
        val b = parts(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Reads `<hash>  <file name>` (sha256sum format) or a bare hash. */
    fun parseSha256(text: String, fileName: String): String? {
        for (line in text.lineSequence()) {
            val bits = line.trim().split(Regex("\\s+"))
            if (bits.isEmpty()) continue
            val hash = bits[0].lowercase(Locale.ROOT)
            if (!Regex("^[0-9a-f]{64}$").matches(hash)) continue
            val named = bits.getOrNull(1)?.removePrefix("*")
            if (named == null || named == fileName) return hash
        }
        return null
    }

    /** Release files may only come from GitHub itself, over https. */
    fun isTrustedDownloadUrl(url: String): Boolean {
        val uri = try { URI(url) } catch (e: Exception) { return false }
        if (uri.scheme != "https") return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == "github.com" || host.endsWith(".github.com") || host.endsWith(".githubusercontent.com")
    }
}
