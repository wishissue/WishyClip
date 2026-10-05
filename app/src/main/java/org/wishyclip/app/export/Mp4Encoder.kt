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
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val frameDurationUs = 1_000_000L / fps.coerceAtLeast(1)

        try {
            for (i in frames.indices) {
                val canvas = surface.lockCanvas(null)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(frames[i], null, Rect(0, 0, encWidth, encHeight), paint)
                surface.unlockCanvasAndPost(canvas)

                while (true) {
                    val status = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (status >= 0) {
                        val encodedData = codec.getOutputBuffer(status)
                        if (encodedData != null && muxerStarted) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size != 0) {
                                bufferInfo.presentationTimeUs = i * frameDurationUs
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            }
                            codec.releaseOutputBuffer(status, false)
                        }
                    } else {
                        break
                    }
                }
                onProgress((i + 1).toFloat() / frames.size)
            }
            codec.signalEndOfInputStream()
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
