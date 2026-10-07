package org.wishyclip.app.export

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

object Mp4Encoder {

    /** Convenience wrapper: every bitmap is shown for exactly one frame. */
    fun encodeMp4(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        frames: List<Bitmap>,
        audio: EncodedAudio? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean = encodeStreaming(
        outputFile, width, height, fps, frames.size,
        holdOf = { 1 }, frameAt = { frames[it] }, audio = audio, onProgress = onProgress
    )

    /**
     * Encodes [frameCount] frames without holding them all in memory. [frameAt] is called once per
     * frame, in order, and its bitmap is drawn immediately (it may be reused/overwritten by the
     * caller on the next call). [holdOf] says for how many video frames each one is shown.
     */
    fun encodeStreaming(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        frameCount: Int,
        holdOf: (Int) -> Int,
        frameAt: (Int) -> Bitmap,
        audio: EncodedAudio? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        if (frameCount <= 0) return false
        val safeFps = fps.coerceAtLeast(1)
        val encWidth = if (width % 2 != 0) width - 1 else width
        val encHeight = if (height % 2 != 0) height - 1 else height
        if (encWidth <= 0 || encHeight <= 0) return false

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encWidth, encHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (encWidth * encHeight * 4).coerceAtLeast(500_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, safeFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var ok = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val info = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / safeFps
            var track = -1
            var audioTrackIndex = -1
            var written = 0L // samples written so far
            var audioPos = 0
            val audioInfo = MediaCodec.BufferInfo()

            fun flushAudio(untilUs: Long) {
                val a = audio ?: return
                val m = muxer ?: return
                if (!muxerStarted || audioTrackIndex < 0) return
                while (audioPos < a.samples.size && a.samples[audioPos].ptsUs <= untilUs) {
                    val smp = a.samples[audioPos++]
                    audioInfo.set(0, smp.data.size, smp.ptsUs, smp.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
                    m.writeSampleData(audioTrackIndex, java.nio.ByteBuffer.wrap(smp.data), audioInfo)
                }
            }

            // Pulls finished samples out of the encoder. With [untilEnd] it waits for end-of-stream.
            fun drain(untilEnd: Boolean) {
                var idle = 0
                while (true) {
                    val status = codec.dequeueOutputBuffer(info, if (untilEnd) 10_000L else 0L)
                    when {
                        status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!untilEnd) return
                            if (++idle > 500) return // ~5 s without output: give up rather than hang
                        }
                        status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer!!.addTrack(codec.outputFormat)
                            if (audio != null) audioTrackIndex = muxer!!.addTrack(audio.format)
                            muxer!!.start()
                            muxerStarted = true
                        }
                        status >= 0 -> {
                            idle = 0
                            val data = codec.getOutputBuffer(status)
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (data != null && info.size > 0 && muxerStarted) {
                                data.position(info.offset)
                                data.limit(info.offset + info.size)
                                info.presentationTimeUs = written * frameDurationUs
                                flushAudio(info.presentationTimeUs)
                                muxer!!.writeSampleData(track, data, info)
                                written++
                            }
                            codec.releaseOutputBuffer(status, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val dst = Rect(0, 0, encWidth, encHeight)
            for (i in 0 until frameCount) {
                val bmp = frameAt(i)
                repeat(holdOf(i).coerceAtLeast(1)) {
                    val canvas = surface.lockCanvas(null)
                    try {
                        canvas.drawBitmap(bmp, null, dst, paint)
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }
                    drain(untilEnd = false)
                }
                onProgress((i + 1).toFloat() / frameCount)
            }
            codec.signalEndOfInputStream()
            drain(untilEnd = true) // the encoder still holds the last few frames: flush them
            flushAudio(Long.MAX_VALUE)
            ok = muxerStarted && written > 0
        } catch (e: Exception) {
            e.printStackTrace()
            ok = false
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) { ok = false }
            try { muxer?.release() } catch (_: Exception) {}
        }
        if (!ok) outputFile.delete()
        return ok
    }
}
