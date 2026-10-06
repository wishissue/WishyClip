package org.wishyclip.app.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import org.wishyclip.app.data.AudioTrackEntity
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** One encoded AAC access unit, ready to hand to the muxer. */
class AudioSample(val data: ByteArray, val ptsUs: Long, val flags: Int)

class EncodedAudio(val format: MediaFormat, val samples: List<AudioSample>)

/**
 * Mixes every audio clip of a project into one stereo track and encodes it as AAC for the MP4 export.
 * Each clip honours its start position, trim in/out points and volume, exactly like the editor preview.
 * Resampling is nearest-neighbour, which is fine for animation soundtracks and voice.
 */
object AudioMixer {
    private const val RATE = 44100
    private const val CHANNELS = 2
    private const val TIMEOUT_US = 10_000L

    /** Returns null when there is nothing audible to export or encoding fails (video is still exported). */
    fun mixAndEncode(tracks: List<AudioTrackEntity>, fps: Int, totalDurationUs: Long): EncodedAudio? {
        val audible = tracks.filter { it.volume > 0f && it.durationMs > 0L && File(it.filePath).exists() }
        if (audible.isEmpty() || totalDurationUs <= 0L) return null
        return try {
            val totalFrames = (totalDurationUs * RATE / 1_000_000L).toInt().coerceAtLeast(1)
            val mix = IntArray(totalFrames * CHANNELS)
            for (t in audible) {
                try {
                    mixClip(mix, t, fps)
                } catch (e: Exception) {
                    e.printStackTrace() // a broken clip is skipped, the rest still exports
                }
            }
            encodeAac(mix)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun mixClip(mix: IntArray, track: AudioTrackEntity, fps: Int) {
        val extractor = MediaExtractor()
        extractor.setDataSource(track.filePath)
        var audioIndex = -1
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioIndex = i
                break
            }
        }
        if (audioIndex < 0) { extractor.release(); return }
        extractor.selectTrack(audioIndex)
        val inFormat = extractor.getTrackFormat(audioIndex)
        val mime = inFormat.getString(MediaFormat.KEY_MIME) ?: run { extractor.release(); return }

        val trimStartUs = track.trimStartMs * 1000L
        val trimEndUs = trimStartUs + track.durationMs * 1000L
        val clipStartUs = track.startFrame.toLong() * 1_000_000L / fps.coerceAtLeast(1)
        extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(inFormat, null, null, 0)
        decoder.start()

        var srcRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var srcChannels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var lastDst = -1
        val gain = track.volume.coerceIn(0f, 1f)
        val totalFrames = mix.size / CHANNELS

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0 || extractor.sampleTime > trimEndUs) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decoder.outputFormat
                        srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        srcChannels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIdx >= 0 -> {
                        val out = decoder.getOutputBuffer(outIdx)
                        if (out != null && info.size > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val shorts = out.asShortBuffer()
                            val frameCount = shorts.remaining() / srcChannels.coerceAtLeast(1)
                            for (i in 0 until frameCount) {
                                val tUs = info.presentationTimeUs + i * 1_000_000L / srcRate
                                if (tUs < trimStartUs) continue
                                if (tUs >= trimEndUs) { outputDone = true; break }
                                val dstUs = clipStartUs + (tUs - trimStartUs)
                                val dst = (dstUs * RATE / 1_000_000L).toInt()
                                if (dst >= totalFrames) { outputDone = true; break }
                                if (dst <= lastDst) continue // downsampling: one write per output sample
                                val l = shorts.get(i * srcChannels).toInt()
                                val r = if (srcChannels >= 2) shorts.get(i * srcChannels + 1).toInt() else l
                                val lg = (l * gain).toInt()
                                val rg = (r * gain).toInt()
                                // Hold the sample over any gap (upsampling) so there are no clicks.
                                for (d in max(lastDst + 1, 0)..dst) {
                                    mix[d * CHANNELS] += lg
                                    mix[d * CHANNELS + 1] += rg
                                }
                                lastDst = dst
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            try { decoder.stop() } catch (_: Exception) {}
            decoder.release()
            extractor.release()
        }
    }

    private fun encodeAac(mix: IntArray): EncodedAudio? {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val samples = ArrayList<AudioSample>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var pos = 0 // index into mix (shorts)
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = encoder.getInputBuffer(inIdx)!!
                        buf.clear()
                        val ptsUs = (pos / CHANNELS).toLong() * 1_000_000L / RATE
                        val count = min(buf.capacity() / 2, mix.size - pos)
                        if (count <= 0) {
                            encoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val sb = buf.asShortBuffer()
                            for (i in 0 until count) {
                                sb.put(mix[pos + i].coerceIn(-32768, 32767).toShort())
                            }
                            encoder.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                            pos += count
                        }
                    }
                }
                val outIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = encoder.outputFormat
                    outIdx >= 0 -> {
                        val out = encoder.getOutputBuffer(outIdx)
                        if (out != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            out.get(bytes)
                            samples.add(AudioSample(bytes, info.presentationTimeUs, info.flags))
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
        }
        val f = outFormat ?: return null
        return EncodedAudio(f, samples)
    }
}
