package org.wishyclip.app.data.folder

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * WishyClip project folder format, version 1.
 *
 * ```
 * <chosen folder>/<Project name>/
 *     wishyclip-project.json    the manifest (everything except pixels and audio)
 *     wishyclip-project.bak     the previous manifest (kept so a crash can never leave no valid one)
 *     wishyclip-project.tmp     only present while a save is in progress
 *     layers/<sha256>.png       one PNG per distinct layer picture, named by its content hash
 *     audio/<name>              imported audio files
 *     thumbnail.png             preview
 *     README.txt                explains this layout to humans
 * ```
 *
 * Rules that make it safe across app versions:
 *  - Layer and audio files are never modified after they are written; a changed layer is a NEW file. So a
 *    crash can never leave a half-overwritten picture, and the previous revision stays intact until the
 *    new manifest is complete.
 *  - The manifest is written last. A reader takes the valid manifest with the highest revision out of
 *    .json / .tmp / .bak.
 *  - Unknown JSON fields are ignored by readers. A manifest with a HIGHER formatVersion than the app knows is
 *    never read or modified (the app says "made by a newer version"), so an old build can not damage a
 *    project written by a newer one.
 *  - Layers are listed bottom to top, as in the editor.
 */
object ProjectFormat {
    const val FORMAT_NAME = "wishyclip-project"
    const val SUPPORTED_VERSION = 1

    const val MANIFEST = "wishyclip-project.json"
    const val MANIFEST_TMP = "wishyclip-project.tmp"
    const val MANIFEST_BAK = "wishyclip-project.bak"
    const val LAYERS_DIR = "layers"
    const val AUDIO_DIR = "audio"
    const val THUMBNAIL = "thumbnail.png"
    const val README = "README.txt"

    val MANIFEST_NAMES = listOf(MANIFEST, MANIFEST_TMP, MANIFEST_BAK)

    /** Layer file names are a SHA-256 in lower-case hex. Anything else is not ours and is never deleted. */
    val LAYER_FILE = Regex("^[0-9a-f]{64}\\.png$")

    /** Audio files this app creates (track_<millis>[_<n>].<ext>); only these are ever garbage-collected. */
    val OWN_AUDIO_FILE = Regex("^track_[0-9_]+\\.[A-Za-z0-9]{1,8}$")

    /** Names allowed inside a manifest: no path separators, no "..", no hidden files. */
    val SAFE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._ -]{0,119}$")

    fun isSafeName(name: String): Boolean = SAFE_NAME.matches(name) && !name.contains("..")

    // Sanity limits for files that come from outside the app.
    const val MAX_MANIFEST_BYTES = 32 * 1024 * 1024
    const val MAX_FRAMES = 20_000
    const val MAX_LAYERS_PER_FRAME = 256
    const val MAX_AUDIO_TRACKS = 256
    const val MAX_SIDE = 16_384
    const val MAX_PIXELS = 50_000_000L
}

data class ManifestLayer(
    val name: String,
    val visible: Boolean,
    val opacity: Float,
    val locked: Boolean,
    val blendMode: String,
    /** File name inside layers/, or null for a layer that was never painted (blank). */
    val file: String?
)

data class ManifestFrame(val exposure: Int, val layers: List<ManifestLayer>)

data class ManifestAudio(
    val file: String,
    val name: String,
    val startFrame: Int,
    val trimStartMs: Long,
    val durationMs: Long,
    val volume: Float
)

data class ProjectManifest(
    val formatVersion: Int,
    val uuid: String,
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /** Increases by one with every committed save. */
    val revision: Long,
    val savedAt: Long,
    val writtenBy: String,
    val frames: List<ManifestFrame>,
    val audio: List<ManifestAudio>
) {
    /** True when everything the user can see is identical (ignores revision and timestamps). */
    fun sameContentAs(other: ProjectManifest): Boolean =
        copy(revision = 0, savedAt = 0, updatedAt = 0, writtenBy = "") ==
            other.copy(revision = 0, savedAt = 0, updatedAt = 0, writtenBy = "")

    fun layerFiles(): Set<String> = frames.flatMap { f -> f.layers.mapNotNull { it.file } }.toSet()
    fun audioFiles(): Set<String> = audio.map { it.file }.toSet()
}

sealed interface DecodeResult {
    data class Ok(val manifest: ProjectManifest) : DecodeResult
    /** Written by a newer WishyClip: do not read, do not modify. */
    data class TooNew(val version: Int) : DecodeResult
    data class Invalid(val reason: String) : DecodeResult
}

object ManifestCodec {

    fun encode(m: ProjectManifest): String {
        val root = JSONObject()
        root.put("format", ProjectFormat.FORMAT_NAME)
        root.put("formatVersion", m.formatVersion)
        root.put("uuid", m.uuid)
        root.put("name", m.name)
        root.put("width", m.width)
        root.put("height", m.height)
        root.put("fps", m.fps)
        root.put("createdAt", m.createdAt)
        root.put("updatedAt", m.updatedAt)
        root.put("revision", m.revision)
        root.put("savedAt", m.savedAt)
        root.put("writtenBy", m.writtenBy)
        val frames = JSONArray()
        for (f in m.frames) {
            val fo = JSONObject()
            fo.put("exposure", f.exposure)
            val layers = JSONArray()
            for (l in f.layers) {
                val lo = JSONObject()
                lo.put("name", l.name)
                lo.put("visible", l.visible)
                lo.put("opacity", l.opacity.toDouble())
                lo.put("locked", l.locked)
                lo.put("blendMode", l.blendMode)
                lo.put("file", l.file ?: JSONObject.NULL)
                layers.put(lo)
            }
            fo.put("layers", layers)
            frames.put(fo)
        }
        root.put("frames", frames)
        val audio = JSONArray()
        for (a in m.audio) {
            val ao = JSONObject()
            ao.put("file", a.file)
            ao.put("name", a.name)
            ao.put("startFrame", a.startFrame)
            ao.put("trimStartMs", a.trimStartMs)
            ao.put("durationMs", a.durationMs)
            ao.put("volume", a.volume.toDouble())
            audio.put(ao)
        }
        root.put("audio", audio)
        return root.toString(2)
    }

    /** Parses and strictly validates untrusted manifest text. Never throws. */
    fun decode(text: String): DecodeResult {
        if (text.length > ProjectFormat.MAX_MANIFEST_BYTES) return DecodeResult.Invalid("manifest too large")
        return try {
            decodeUnsafe(text)
        } catch (e: JSONException) {
            DecodeResult.Invalid("not valid JSON: ${e.message}")
        } catch (e: RuntimeException) {
            DecodeResult.Invalid("unreadable manifest: ${e.message}")
        }
    }

    private fun decodeUnsafe(text: String): DecodeResult {
        val root = JSONObject(text)
        if (root.optString("format") != ProjectFormat.FORMAT_NAME) return DecodeResult.Invalid("not a WishyClip project")
        val version = root.optInt("formatVersion", -1)
        if (version < 1) return DecodeResult.Invalid("missing format version")
        if (version > ProjectFormat.SUPPORTED_VERSION) return DecodeResult.TooNew(version)

        val uuid = root.optString("uuid")
        if (!Regex("^[A-Za-z0-9-]{8,64}$").matches(uuid)) return DecodeResult.Invalid("bad project id")
        val width = root.optInt("width", 0)
        val height = root.optInt("height", 0)
        if (width !in 1..ProjectFormat.MAX_SIDE || height !in 1..ProjectFormat.MAX_SIDE ||
            width.toLong() * height > ProjectFormat.MAX_PIXELS
        ) return DecodeResult.Invalid("bad canvas size ${width}x$height")
        val fps = root.optInt("fps", 0)
        if (fps !in 1..240) return DecodeResult.Invalid("bad frame rate")

        val framesJson = root.optJSONArray("frames") ?: return DecodeResult.Invalid("no frames")
        if (framesJson.length() < 1 || framesJson.length() > ProjectFormat.MAX_FRAMES) {
            return DecodeResult.Invalid("bad frame count ${framesJson.length()}")
        }
        val frames = ArrayList<ManifestFrame>(framesJson.length())
        for (i in 0 until framesJson.length()) {
            val fo = framesJson.optJSONObject(i) ?: return DecodeResult.Invalid("frame $i is not an object")
            val layersJson = fo.optJSONArray("layers") ?: return DecodeResult.Invalid("frame $i has no layers")
            if (layersJson.length() < 1 || layersJson.length() > ProjectFormat.MAX_LAYERS_PER_FRAME) {
                return DecodeResult.Invalid("frame $i has ${layersJson.length()} layers")
            }
            val layers = ArrayList<ManifestLayer>(layersJson.length())
            for (j in 0 until layersJson.length()) {
                val lo = layersJson.optJSONObject(j) ?: return DecodeResult.Invalid("layer $i/$j is not an object")
                val file = if (lo.isNull("file")) null else lo.optString("file").ifEmpty { null }
                if (file != null && !ProjectFormat.LAYER_FILE.matches(file)) {
                    return DecodeResult.Invalid("layer $i/$j has an unsafe file name")
                }
                layers.add(
                    ManifestLayer(
                        name = lo.optString("name", "Layer").take(200),
                        visible = lo.optBoolean("visible", true),
                        opacity = lo.optDouble("opacity", 1.0).toFloat().let { if (it.isNaN()) 1f else it.coerceIn(0f, 1f) },
                        locked = lo.optBoolean("locked", false),
                        blendMode = lo.optString("blendMode", "NORMAL").take(32),
                        file = file
                    )
                )
            }
            frames.add(ManifestFrame(exposure = fo.optInt("exposure", 1).coerceIn(1, 1000), layers = layers))
        }

        val audioJson = root.optJSONArray("audio") ?: JSONArray()
        if (audioJson.length() > ProjectFormat.MAX_AUDIO_TRACKS) return DecodeResult.Invalid("too many audio tracks")
        val audio = ArrayList<ManifestAudio>(audioJson.length())
        for (i in 0 until audioJson.length()) {
            val ao = audioJson.optJSONObject(i) ?: return DecodeResult.Invalid("audio $i is not an object")
            val file = ao.optString("file")
            if (!ProjectFormat.isSafeName(file)) return DecodeResult.Invalid("audio $i has an unsafe file name")
            audio.add(
                ManifestAudio(
                    file = file,
                    name = ao.optString("name", file).take(200),
                    startFrame = ao.optInt("startFrame", 0).coerceAtLeast(0),
                    trimStartMs = ao.optLong("trimStartMs", 0L).coerceAtLeast(0L),
                    durationMs = ao.optLong("durationMs", 0L).coerceAtLeast(0L),
                    volume = ao.optDouble("volume", 1.0).toFloat().let { if (it.isNaN()) 1f else it.coerceIn(0f, 1f) }
                )
            )
        }

        return DecodeResult.Ok(
            ProjectManifest(
                formatVersion = version,
                uuid = uuid,
                name = root.optString("name", "Untitled").take(200).ifBlank { "Untitled" },
                width = width,
                height = height,
                fps = fps,
                createdAt = root.optLong("createdAt", 0L),
                updatedAt = root.optLong("updatedAt", 0L),
                revision = root.optLong("revision", 0L).coerceAtLeast(0L),
                savedAt = root.optLong("savedAt", 0L),
                writtenBy = root.optString("writtenBy", "").take(80),
                frames = frames,
                audio = audio
            )
        )
    }
}

/** What a project folder currently holds. */
sealed interface FolderManifest {
    class Found(val manifest: ProjectManifest) : FolderManifest
    class TooNew(val version: Int) : FolderManifest
    /** A manifest file exists but none of them is readable. */
    object Damaged : FolderManifest
    object None : FolderManifest
}

/** Reads manifests out of a project folder, tolerating a save that was interrupted at any point. */
object ManifestReader {

    fun readBest(dir: FolderStore): FolderManifest {
        val present = dir.listFiles().keys
        val candidates = ProjectFormat.MANIFEST_NAMES.filter { it in present }
        if (candidates.isEmpty()) return FolderManifest.None
        var best: ProjectManifest? = null
        var tooNew: Int? = null
        for (name in candidates) {
            val text = try {
                dir.openRead(name)?.use { readLimited(it) }
            } catch (e: Exception) {
                null
            } ?: continue
            when (val r = ManifestCodec.decode(text)) {
                is DecodeResult.Ok -> {
                    val b = best
                    if (b == null || r.manifest.revision > b.revision ||
                        (r.manifest.revision == b.revision && r.manifest.savedAt > b.savedAt)
                    ) best = r.manifest
                }
                is DecodeResult.TooNew -> tooNew = r.version
                is DecodeResult.Invalid -> Unit
            }
        }
        best?.let { return FolderManifest.Found(it) }
        tooNew?.let { return FolderManifest.TooNew(it) }
        return FolderManifest.Damaged
    }

    /** The manifest that was current before the latest commit (the .bak), if it is valid. */
    fun readBackup(dir: FolderStore): ProjectManifest? {
        if (ProjectFormat.MANIFEST_BAK !in dir.listFiles().keys) return null
        val text = try {
            dir.openRead(ProjectFormat.MANIFEST_BAK)?.use { readLimited(it) }
        } catch (e: Exception) {
            null
        } ?: return null
        return (ManifestCodec.decode(text) as? DecodeResult.Ok)?.manifest
    }

    private fun readLimited(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > ProjectFormat.MAX_MANIFEST_BYTES) throw java.io.IOException("manifest too large")
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
