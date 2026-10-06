package org.wishyclip.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Importer {

    /** Scales [src] to fit [targetWidth] x [targetHeight] while centered. */
    fun scaleToFit(src: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val srcWidth = src.width.toFloat()
        val srcHeight = src.height.toFloat()

        val scale = minOf(targetWidth / srcWidth, targetHeight / srcHeight)
        val dx = (targetWidth - srcWidth * scale) / 2f
        val dy = (targetHeight - srcHeight * scale) / 2f

        val matrix = Matrix()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, matrix, paint)
        return result
    }

    /** Decodes an image and scales it down (never up) to fit the box, keeping its own aspect ratio. */
    suspend fun importImageTight(context: Context, uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val original = BitmapFactory.decodeStream(stream) ?: return@withContext null
                    val scale = minOf(maxWidth / original.width.toFloat(), maxHeight / original.height.toFloat(), 1f)
                    if (scale >= 1f) original
                    else {
                        val w = (original.width * scale).toInt().coerceAtLeast(1)
                        val h = (original.height * scale).toInt().coerceAtLeast(1)
                        val out = Bitmap.createScaledBitmap(original, w, h, true)
                        if (out !== original) original.recycle()
                        out
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun importImage(context: Context, uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val original = BitmapFactory.decodeStream(stream) ?: return@withContext null
                    val scaled = scaleToFit(original, targetWidth, targetHeight)
                    if (original != scaled && !original.isRecycled) {
                        original.recycle()
                    }
                    scaled
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun importVideoFrames(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        fps: Int,
        maxFrames: Int = 60
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Bitmap>()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationMsStr?.toLongOrNull() ?: 3000L
            val intervalUs = (1_000_000L / fps.coerceAtLeast(1))
            val totalUs = durationMs * 1000L

            var currentUs = 0L
            var count = 0
            while (currentUs < totalUs && count < maxFrames) {
                val frameBmp = retriever.getFrameAtTime(currentUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frameBmp != null) {
                    val scaled = scaleToFit(frameBmp, targetWidth, targetHeight)
                    if (frameBmp != scaled && !frameBmp.isRecycled) {
                        frameBmp.recycle()
                    }
                    list.add(scaled)
                }
                currentUs += intervalUs
                count++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
        list
    }
}
