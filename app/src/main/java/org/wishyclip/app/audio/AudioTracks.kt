package org.wishyclip.app.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

data class AudioClip(val id: Long, val uri: String, val startFrame: Int, val volume: Float)

interface AudioTrackManager {
    suspend fun addClip(projectId: Long, sourceUri: String): AudioClip?
    fun playFrom(frameIndex: Int, fps: Int)
    fun pause()
    fun release()
}

class ExoPlayerAudioTrackManager(private val context: Context) : AudioTrackManager {
    private var player: ExoPlayer? = null
    private var currentFilePath: String? = null

    override suspend fun addClip(projectId: Long, sourceUri: String): AudioClip? = null

    fun prepare(filePath: String, volume: Float = 1f) {
        if (player == null) {
            player = ExoPlayer.Builder(context).build()
        }
        if (currentFilePath != filePath) {
            currentFilePath = filePath
            val file = File(filePath)
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            player?.setMediaItem(mediaItem)
            player?.prepare()
        }
        player?.volume = volume
    }

    override fun playFrom(frameIndex: Int, fps: Int) {
        val p = player ?: return
        val startMs = (frameIndex * 1000L / fps)
        p.seekTo(startMs)
        p.playWhenReady = true
    }

    fun playAtOffset(startMs: Long, volume: Float) {
        val p = player ?: return
        p.volume = volume
        p.seekTo(startMs.coerceAtLeast(0L))
        p.playWhenReady = true
    }

    override fun pause() {
        player?.playWhenReady = false
    }

    override fun release() {
        player?.release()
        player = null
        currentFilePath = null
    }
}

/**
 * Records the microphone to an AAC .m4a file. One recording at a time.
 * A recording that fails to start or stop cleanly never leaves a half-written file behind.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    /** Called on the main thread when [MAX_DURATION_MS] is reached and recording stopped by itself. */
    var onLimitReached: (() -> Unit)? = null

    /** Returns the file being written, or null when recording could not start (no mic, busy, no permission). */
    fun startRecording(outputDir: File): File? {
        if (recorder != null) return null
        var mr: MediaRecorder? = null
        val file = File(outputDir, "track_${System.currentTimeMillis()}_${(0..9999).random()}.m4a")
        try {
            outputDir.mkdirs()
            mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(44_100)
            mr.setAudioEncodingBitRate(128_000)
            mr.setAudioChannels(1)
            mr.setMaxDuration(MAX_DURATION_MS)
            mr.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onLimitReached?.invoke() }
                }
            }
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            outputFile = file
            return file
        } catch (e: Exception) {
            try { mr?.release() } catch (_: Exception) {}
            recorder = null
            outputFile = null
            file.delete()
            return null
        }
    }

    /** Stops and returns the finished file, or null when nothing usable was recorded (the file is deleted). */
    fun stopRecording(): File? {
        val mr = recorder ?: return null
        val file = outputFile
        recorder = null
        outputFile = null
        var ok = true
        try {
            mr.stop()
        } catch (e: RuntimeException) {
            ok = false // stop() throws when no valid audio data was captured (stopped immediately)
        } finally {
            try { mr.release() } catch (_: Exception) {}
        }
        if (file == null) return null
        if (!ok || !file.isFile || file.length() <= 0L) {
            file.delete()
            return null
        }
        return file
    }

    /** Stops and throws the recording away (leaving the editor). */
    fun cancel() {
        stopRecording()?.delete()
    }

    fun release() = cancel()

    companion object {
        const val MAX_DURATION_MS = 10 * 60 * 1000
    }
}

object WaveformExtractor {
    fun getAudioDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            time?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    fun getAudioDurationMs(filePath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            time?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    fun extractWaveform(audioFile: File, samplesCount: Int = 100): FloatArray {
        val result = FloatArray(samplesCount)
        if (!audioFile.exists() || audioFile.length() <= 0) return result

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(audioFile.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex >= 0 && format != null) {
                extractor.selectTrack(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val info = MediaCodec.BufferInfo()
                var isEOS = false
                val pcmPeaks = ArrayList<Float>()

                while (!isEOS && pcmPeaks.size < samplesCount * 20) {
                    val inIdx = codec.dequeueInputBuffer(5000L)
                    if (inIdx >= 0) {
                        val inputBuf = codec.getInputBuffer(inIdx)
                        if (inputBuf != null) {
                            val sampleSize = extractor.readSampleData(inputBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    var outIdx = codec.dequeueOutputBuffer(info, 5000L)
                    while (outIdx >= 0) {
                        val outputBuf = codec.getOutputBuffer(outIdx)
                        if (outputBuf != null && info.size > 0) {
                            outputBuf.position(info.offset)
                            outputBuf.limit(info.offset + info.size)
                            val shortBuf = outputBuf.asShortBuffer()
                            var maxVal = 0
                            while (shortBuf.hasRemaining()) {
                                val s = abs(shortBuf.get().toInt())
                                if (s > maxVal) maxVal = s
                            }
                            pcmPeaks.add((maxVal / 32768f).coerceIn(0.05f, 1f))
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEOS = true
                            break
                        }
                        outIdx = codec.dequeueOutputBuffer(info, 0L)
                    }
                }

                if (pcmPeaks.isNotEmpty()) {
                    val chunkSize = pcmPeaks.size.toFloat() / samplesCount
                    for (i in 0 until samplesCount) {
                        val start = (i * chunkSize).toInt().coerceIn(0, pcmPeaks.lastIndex)
                        val end = ((i + 1) * chunkSize).toInt().coerceIn(start + 1, pcmPeaks.size)
                        var maxPeak = 0f
                        for (j in start until end) {
                            if (pcmPeaks[j] > maxPeak) maxPeak = pcmPeaks[j]
                        }
                        result[i] = maxPeak.coerceIn(0.05f, 1f)
                    }
                    return result
                }
            }
        } catch (e: Exception) {
            for (i in 0 until samplesCount) {
                result[i] = 0.2f + 0.6f * ((i * 7) % 10) / 10f
            }
        } finally {
            try { extractor.release() } catch (e: Exception) {}
        }
        return result
    }
}

object NoOpAudioTrackManager : AudioTrackManager {
    override suspend fun addClip(projectId: Long, sourceUri: String): AudioClip? = null
    override fun playFrom(frameIndex: Int, fps: Int) {}
    override fun pause() {}
    override fun release() {}
}


/**
 * Plays every audio clip of a project at once, one ExoPlayer per clip, kept in step with the timeline.
 * Call [sync] whenever the playhead frame changes during playback. Must be used on the main thread.
 */
class MultiTrackAudioPlayer(private val context: Context) {
    private class Slot(val player: ExoPlayer, val path: String)

    private val slots = HashMap<Long, Slot>()
    private var lastFrame = -1

    private fun slotFor(track: org.wishyclip.app.data.AudioTrackEntity): Slot {
        val existing = slots[track.id]
        if (existing != null && existing.path == track.filePath) return existing
        existing?.player?.release()
        val p = ExoPlayer.Builder(context).build()
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(track.filePath))))
        p.prepare()
        return Slot(p, track.filePath).also { slots[track.id] = it }
    }

    private fun clipFrames(track: org.wishyclip.app.data.AudioTrackEntity, fps: Int): Int =
        (track.durationMs * fps / 1000L).toInt().coerceAtLeast(1)

    /** Starts, seeks or stops each clip so it matches [frame]. */
    fun sync(tracks: List<org.wishyclip.app.data.AudioTrackEntity>, frame: Int, fps: Int) {
        // The playhead jumped back (the animation looped): stop everything so each clip below is
        // re-seeked to its own position. Otherwise a clip that is longer than the animation just
        // keeps playing and drifts out of sync with the picture on every loop.
        if (frame < lastFrame) slots.values.forEach { it.player.playWhenReady = false }
        lastFrame = frame
        val liveIds = tracks.map { it.id }.toSet()
        slots.keys.filter { it !in liveIds }.forEach { remove(it) }
        for (t in tracks) {
            val slot = slotFor(t)
            val active = frame >= t.startFrame && frame < t.startFrame + clipFrames(t, fps) && t.volume > 0f
            slot.player.volume = t.volume
            if (active) {
                // playWhenReady (not isPlaying): isPlaying is false while a clip is still buffering,
                // which made every frame change seek again and kept the audio from ever starting.
                if (!slot.player.playWhenReady) {
                    val posMs = (frame - t.startFrame) * 1000L / fps + t.trimStartMs
                    slot.player.seekTo(posMs.coerceAtLeast(0L))
                    slot.player.playWhenReady = true
                }
            } else if (slot.player.playWhenReady) {
                slot.player.playWhenReady = false
            }
        }
    }

    /** Applies a volume change immediately, even while playing. */
    fun setVolume(trackId: Long, volume: Float) {
        slots[trackId]?.player?.volume = volume.coerceIn(0f, 1f)
    }

    fun pauseAll() {
        lastFrame = -1
        slots.values.forEach { it.player.playWhenReady = false }
    }

    fun remove(trackId: Long) {
        slots.remove(trackId)?.player?.release()
    }

    fun release() {
        slots.values.forEach { it.player.release() }
        slots.clear()
    }
}
