package org.wishyclip.app.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Why an update step failed; shown to the user in plain words. */
class UpdateException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind { NETWORK, BAD_RESPONSE, CHECKSUM, TOO_LARGE, CANCELLED }
}

/** The only code in the app that talks to the internet. HTTPS to GitHub hosts only, with timeouts and size caps. */
internal object UpdateNetwork {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_REDIRECTS = 5
    const val MAX_JSON_BYTES = 1_000_000
    const val MAX_APK_BYTES = 400L * 1024 * 1024
    private const val USER_AGENT = "WishyClip-Updater"

    /** Opens [url], following redirects only to trusted GitHub hosts. Caller must disconnect. */
    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            if (!ReleaseParser.isTrustedDownloadUrl(current) && !current.startsWith("https://api.github.com/")) {
                throw UpdateException(UpdateException.Kind.BAD_RESPONSE, "Untrusted address")
            }
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val next = c.getHeaderField("Location")
                c.disconnect()
                if (next.isNullOrBlank()) throw UpdateException(UpdateException.Kind.BAD_RESPONSE, "Bad redirect")
                current = URL(URL(current), next).toString()
            } else {
                return c
            }
        }
        throw UpdateException(UpdateException.Kind.BAD_RESPONSE, "Too many redirects")
    }

    /** Returns the body text, or null for HTTP 404 (no release published yet). */
    fun fetchText(url: String): String? {
        val c = try { open(url) } catch (e: UpdateException) { throw e } catch (e: IOException) {
            throw UpdateException(UpdateException.Kind.NETWORK, "No connection")
        }
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode != 200) throw UpdateException(UpdateException.Kind.BAD_RESPONSE, "Server answered ${c.responseCode}")
            return c.inputStream.use { readLimited(it, MAX_JSON_BYTES) }.toString(Charsets.UTF_8.name())
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException(UpdateException.Kind.NETWORK, "Connection lost")
        } finally {
            c.disconnect()
        }
    }

    /**
     * Downloads to [target] (written as `.part` first, renamed when complete) and returns the SHA-256 of
     * what was written. [isCancelled] is polled so a cancelled download stops promptly.
     */
    fun download(url: String, target: File, onProgress: (done: Long, total: Long) -> Unit, isCancelled: () -> Boolean): String {
        val part = File(target.parentFile, target.name + ".part")
        part.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        val c = try { open(url) } catch (e: UpdateException) { throw e } catch (e: IOException) {
            throw UpdateException(UpdateException.Kind.NETWORK, "No connection")
        }
        try {
            if (c.responseCode != 200) throw UpdateException(UpdateException.Kind.BAD_RESPONSE, "Server answered ${c.responseCode}")
            val total = c.contentLengthLong
            if (total > MAX_APK_BYTES) throw UpdateException(UpdateException.Kind.TOO_LARGE, "File is too large")
            var done = 0L
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) throw UpdateException(UpdateException.Kind.CANCELLED, "Cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        done += n
                        if (done > MAX_APK_BYTES) throw UpdateException(UpdateException.Kind.TOO_LARGE, "File is too large")
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        onProgress(done, total)
                    }
                    out.fd.sync()
                }
            }
            if (total >= 0 && done != total) throw UpdateException(UpdateException.Kind.NETWORK, "Download was cut off")
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw UpdateException(UpdateException.Kind.BAD_RESPONSE, "Could not save the file")
            return digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: UpdateException) {
            part.delete()
            throw e
        } catch (e: IOException) {
            part.delete()
            throw UpdateException(UpdateException.Kind.NETWORK, "Connection lost")
        } finally {
            c.disconnect()
        }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArrayOutputStream {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > limit) throw UpdateException(UpdateException.Kind.TOO_LARGE, "Answer too large")
            out.write(buf, 0, n)
        }
        return out
    }
}
