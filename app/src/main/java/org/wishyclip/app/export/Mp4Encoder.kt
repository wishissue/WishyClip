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

    fun encodeMp4(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        frames: List<Bitmap>,
        audio: EncodedAudio? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        val encWidth = if (width % 2 != 0) width - 1 else width
        val encHeight = if (height % 2 != 0) height - 1 else height

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encWidth, encHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, encWidth * encHeight * 4)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val frameDurationUs = 1_000_000L / fps.coerceAtLeast(1)
        var videoSamples = 0L
        var audioPos = 0
        val audioInfo = MediaCodec.BufferInfo()

        // Writes queued audio up to [untilUs] so audio and video stay interleaved in the file.
        fun flushAudio(untilUs: Long) {
            val a = audio ?: return
            if (!muxerStarted || audioTrackIndex < 0) return
            while (audioPos < a.samples.size && a.samples[audioPos].ptsUs <= untilUs) {
                val smp = a.samples[audioPos++]
                audioInfo.set(0, smp.data.size, smp.ptsUs, smp.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
                muxer.writeSampleData(audioTrackIndex, java.nio.ByteBuffer.wrap(smp.data), audioInfo)
            }
        }

        // Returns true once the encoder reports end of stream.
        fun drain(timeoutUs: Long): Boolean {
            while (true) {
                val status = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    // The audio track must be added before the muxer starts.
                    if (audio != null) audioTrackIndex = muxer.addTrack(audio.format)
                    muxer.start()
                    muxerStarted = true
                } else if (status >= 0) {
                    val encodedData = codec.getOutputBuffer(status)
                    if (encodedData != null && muxerStarted) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0) {
                            bufferInfo.presentationTimeUs = videoSamples * frameDurationUs
                            flushAudio(bufferInfo.presentationTimeUs)
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            videoSamples++
                        }
                    }
                    codec.releaseOutputBuffer(status, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                } else {
                    return false
                }
            }
        }

        try {
            for (i in frames.indices) {
                val canvas = surface.lockCanvas(null)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(frames[i], null, Rect(0, 0, encWidth, encHeight), paint)
                surface.unlockCanvasAndPost(canvas)
                drain(10000)
                onProgress((i + 1).toFloat() / frames.size)
            }
            codec.signalEndOfInputStream()
            // Drain what is left in the encoder so the last frames are not lost.
            var done = false
            var guard = 0
            while (!done && guard++ < 500) done = drain(10000)
            // Any audio beyond the last video frame.
            flushAudio(Long.MAX_VALUE)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try {
                codec.stop()
                codec.release()
            } catch (e: Exception) {}
            try {
                if (muxerStarted) {
                    muxer.stop()
                }
                muxer.release()
            } catch (e: Exception) {}
        }
        return true
    }
}
