package org.wishyclip.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReleaseParserTest {
    private fun release(
        tag: String = "v1.2.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """
            {"name":"WishyClip-1.2.0.apk","size":123456,"browser_download_url":"https://github.com/wishissue/WishyClip/releases/download/v1.2.0/WishyClip-1.2.0.apk"},
            {"name":"WishyClip-1.2.0.apk.sha256","size":90,"browser_download_url":"https://github.com/wishissue/WishyClip/releases/download/v1.2.0/WishyClip-1.2.0.apk.sha256"}
        """.trimIndent()
    ) = """
        {"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"body":"Fixes things",
         "html_url":"https://github.com/wishissue/WishyClip/releases/tag/$tag","assets":[$assets]}
    """.trimIndent()

    @Test fun readsANormalRelease() {
        val info = ReleaseParser.parse(release())!!
        assertEquals("1.2.0", info.version)
        assertEquals("WishyClip-1.2.0.apk", info.apkName)
        assertEquals(123456L, info.apkSize)
        assertNotNull(info.sha256Url)
        assertTrue(info.sha256Url!!.endsWith("WishyClip-1.2.0.apk.sha256"))
        assertEquals("Fixes things", info.notes)
    }

    @Test fun hyphenatedApkNamesAreAccepted() {
        val n = "WishyClip-1.2.6-universal-release.apk"
        val a = """{"name":"$n","size":1,"browser_download_url":"https://github.com/wishissue/WishyClip/releases/download/v1.2.6/$n"}"""
        assertEquals(n, ReleaseParser.parse(release(tag = "v1.2.6", assets = a))!!.apkName)
    }

    @Test fun releaseWithoutAnyUsableApkParsesToNull() {
        val a = """{"name":"notes.txt","size":1,"browser_download_url":"https://github.com/x/y/releases/download/v/notes.txt"}"""
        assertNull(ReleaseParser.parse(release(assets = a)))
    }

    @Test fun draftsAndPrereleasesAreNeverOffered() {
        assertNull(ReleaseParser.parse(release(draft = true)))
        assertNull(ReleaseParser.parse(release(prerelease = true)))
    }

    @Test fun suffixedTagsAreNeverOffered() {
        assertNull(ReleaseParser.parse(release(tag = "v1.2.0-rc1")))
        assertNull(ReleaseParser.parse(release(tag = "test-run5")))
    }

    @Test fun debugAndTestApksAreIgnored() {
        val onlyDebug = """{"name":"WishyClip-test-1.2.0-run5-debug.apk","size":1,"browser_download_url":"https://github.com/x/y/releases/download/t/a.apk"}"""
        assertNull(ReleaseParser.parse(release(assets = onlyDebug)))
        val wrongName = """{"name":"app-release.apk","size":1,"browser_download_url":"https://github.com/x/y/releases/download/t/app-release.apk"}"""
        assertNull(ReleaseParser.parse(release(assets = wrongName)))
    }

    @Test fun downloadsFromOtherHostsOrHttpAreIgnored() {
        val evil = """{"name":"WishyClip-1.2.0.apk","size":1,"browser_download_url":"https://evil.example.com/WishyClip-1.2.0.apk"}"""
        assertNull(ReleaseParser.parse(release(assets = evil)))
        val http = """{"name":"WishyClip-1.2.0.apk","size":1,"browser_download_url":"http://github.com/x/WishyClip-1.2.0.apk"}"""
        assertNull(ReleaseParser.parse(release(assets = http)))
        assertFalse(ReleaseParser.isTrustedDownloadUrl("https://github.com.evil.example/x"))
        assertTrue(ReleaseParser.isTrustedDownloadUrl("https://objects.githubusercontent.com/abc"))
        assertTrue(ReleaseParser.isTrustedDownloadUrl("https://release-assets.githubusercontent.com/abc"))
    }

    @Test fun garbageIsNotARelease() {
        assertNull(ReleaseParser.parse("not json"))
        assertNull(ReleaseParser.parse("{}"))
        assertNull(ReleaseParser.parse("""{"tag_name":"v1.0.0"}"""))
    }

    @Test fun missingChecksumIsAllowedButReported() {
        val apkOnly = """{"name":"WishyClip-1.2.0.apk","size":1,"browser_download_url":"https://github.com/x/y/releases/download/v/WishyClip-1.2.0.apk"}"""
        assertNull(ReleaseParser.parse(release(assets = apkOnly))!!.sha256Url)
    }

    @Test fun versionComparison() {
        assertTrue(ReleaseParser.isNewer("1.2.0", "1.1.0"))
        assertTrue(ReleaseParser.isNewer("1.10.0", "1.9.9"))
        assertTrue(ReleaseParser.isNewer("2.0", "1.9.9"))
        assertFalse(ReleaseParser.isNewer("1.1.0", "1.1.0"))
        assertFalse(ReleaseParser.isNewer("1.1", "1.1.0"))
        assertFalse(ReleaseParser.isNewer("1.0.9", "1.1.0"))
        // the installed version may carry a suffix (debug builds); only the numbers count
        assertTrue(ReleaseParser.isNewer("1.2.0", "1.1.0-debug"))
        assertFalse(ReleaseParser.isNewer("garbage", "1.1.0"))
        assertFalse(ReleaseParser.isNewer("1.2.0", "garbage"))
    }

    @Test fun checksumFileFormats() {
        val h = "a".repeat(64)
        assertEquals(h, ReleaseParser.parseSha256("$h  WishyClip-1.2.0.apk\n", "WishyClip-1.2.0.apk"))
        assertEquals(h, ReleaseParser.parseSha256("$h *WishyClip-1.2.0.apk", "WishyClip-1.2.0.apk"))
        assertEquals(h, ReleaseParser.parseSha256(h, "WishyClip-1.2.0.apk"))
        assertNull(ReleaseParser.parseSha256("$h  other.apk", "WishyClip-1.2.0.apk"))
        assertNull(ReleaseParser.parseSha256("tooshort  WishyClip-1.2.0.apk", "WishyClip-1.2.0.apk"))
    }

    @Test fun signerRule() {
        assertTrue(ApkVerifier.signersCompatible(setOf("a"), setOf("a")))
        assertTrue(ApkVerifier.signersCompatible(setOf("old"), setOf("old", "new"))) // rotated key keeps the old one
        assertFalse(ApkVerifier.signersCompatible(setOf("a"), setOf("b")))
        assertFalse(ApkVerifier.signersCompatible(emptySet(), setOf("a")))
        assertFalse(ApkVerifier.signersCompatible(setOf("a"), emptySet()))
    }
}
