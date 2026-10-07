package org.wishyclip.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Importer {

    /** Most frames pulled from one video, so a long clip cannot fill the device. */
    const val MAX_VIDEO_FRAMES = 60

    /** Largest power-of-two shrink that still leaves the decoded image at least target-sized. */
    internal fun sampleSizeFor(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) sample *= 2
        return sample
    }

    /** Matrix that makes a photo upright according to its EXIF orientation, or null if already upright. */
    private fun orientationMatrix(orientation: Int): Matrix? {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(270f)
                m.postScale(-1f, 1f)
            }
            else -> return null
        }
        return m
    }

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
                val resolver = context.contentResolver

                // Read the size first and decode at a reduced size: a 48 MP photo must not be
                // decoded at full resolution just to be scaled down to a 1280x720 canvas.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
                }
                var original: Bitmap = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return@withContext null

                // Phone photos are often stored sideways with an EXIF flag; honour it.
                val orientation = try {
                    resolver.openInputStream(uri)?.use {
                        ExifInterface(it).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                        )
                    } ?: ExifInterface.ORIENTATION_NORMAL
                } catch (e: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }
                val matrix = orientationMatrix(orientation)
                if (matrix != null) {
                    val upright = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                    if (upright !== original) {
                        original.recycle()
                        original = upright
                    }
                }

                val scaled = scaleToFit(original, targetWidth, targetHeight)
                if (scaled !== original && !original.isRecycled) {
                    original.recycle()
                }
                scaled
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
        }

    suspend fun importVideoFrames(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        fps: Int,
        maxFrames: Int = MAX_VIDEO_FRAMES
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
                val frameBmp = retriever.getFrameAtTime(currentUs, MediaMetadataRetriever.OPTION_CLOSEST)
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
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
        list
    }
}
