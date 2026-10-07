package org.wishyclip.app.export

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.wishyclip.app.WishyApp
import org.wishyclip.app.canvas.LayerBlending
import org.wishyclip.app.data.BitmapStore
import org.wishyclip.app.data.BusyTracker
import org.wishyclip.app.data.LayerEntity
import org.wishyclip.app.model.LayerBlend
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

class ExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastNotifiedPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val projectId = intent?.getLongExtra("projectId", -1L) ?: -1L
        val formatStr = intent?.getStringExtra("format") ?: "MP4"
        val format = try {
            ExportFormat.valueOf(formatStr)
        } catch (e: IllegalArgumentException) {
            ExportFormat.MP4
        }
        val frameIndex = intent?.getIntExtra("frameIndex", -1) ?: -1

        if (projectId < 0L) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Exporting Animation")
            .setContentText("Preparing export...")
            .setSmallIcon(R.drawable.ic_menu_save)
            .setProgress(100, 0, false)
            .setOngoing(true)
            .build()

        // Must be called for every startForegroundService(), even if we then ignore the request.
        startForeground(NOTIFICATION_ID, notification)

        if (job?.isActive == true) return START_NOT_STICKY // one export at a time

        job = scope.launch { runExport(projectId, format, frameIndex) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runExport(projectId: Long, format: ExportFormat, frameIndex: Int) {
        var outputFile: File? = null
        try {
            BusyTracker.show(
                OWNER, "Exporting ${format.label}", "Preparing…", 0f,
                onCancel = { ExportService.cancel() }
            )
            outputFile = performExport(projectId, format, frameIndex)

            BusyTracker.update(OWNER, "Saving to your device…", 1f)
            val savedUri = saveToPublicStorage(outputFile, format)
            val fileUri = savedUri ?: try {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", outputFile)
            } catch (e: Exception) {
                null
            }
            // The gallery now holds its own copy; do not leave a second one in the cache forever.
            if (savedUri?.authority == MediaStore.AUTHORITY) outputFile.delete()

            showCompletionNotification(fileUri, outputFile.name, format)
            BusyTracker.postNotice("Export complete: ${outputFile.name}")
        } catch (e: CancellationException) {
            outputFile?.delete()
            BusyTracker.postNotice("Export cancelled")
        } catch (e: Throwable) {
            e.printStackTrace()
            val reason = if (e is OutOfMemoryError) "Not enough memory. Try a smaller canvas or fewer frames."
            else e.message ?: "Unknown error"
            showFailureNotification(reason)
            BusyTracker.postNotice("Export failed: $reason")
        } finally {
            BusyTracker.hide(OWNER)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Renders the project into a file in the cache folder and returns it. Throws on failure. */
    private suspend fun performExport(projectId: Long, format: ExportFormat, frameIndex: Int): File {
        val job = currentCoroutineContext().job
        val wishy = applicationContext as WishyApp
        val repo = wishy.repository
        val store = repo.store
        val project = repo.getProject(projectId) ?: throw IllegalStateException("Project not found")
        val allFrames = repo.frames(projectId)
        if (allFrames.isEmpty()) throw IllegalStateException("This project has no frames")

        // Only the picked frame for a single-frame export; otherwise everything.
        val frames = if (format == ExportFormat.PNG_CURRENT_FRAME) {
            listOf(allFrames[frameIndex.coerceIn(allFrames.indices)])
        } else allFrames
        val layerLists = frames.map { repo.layers(it.id) }
        val n = frames.size
        val w = project.width
        val h = project.height

        val exportDir = File(cacheDir, "exports").apply { mkdirs() }
        val base = safeFileName(project.name)
        val outputFile = when (format) {
            ExportFormat.MP4 -> File(exportDir, "$base.mp4")
            ExportFormat.GIF -> File(exportDir, "$base.gif")
            ExportFormat.PNG_SEQUENCE -> File(exportDir, "${base}_sequence.zip")
            ExportFormat.PNG_CURRENT_FRAME -> File(exportDir, "${base}_frame.png")
        }

        fun report(done: Int, fraction: Float) {
            BusyTracker.update(OWNER, "Rendering frame $done of $n", fraction)
            updateProgress((fraction * 100).toInt())
        }

        // One scratch bitmap is reused for every frame: frames are rendered, encoded and discarded
        // one at a time instead of keeping the whole animation in memory.
        val scratch = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            when (format) {
                ExportFormat.MP4 -> {
                    val fps = project.fps.coerceAtLeast(1)
                    val videoFrames = frames.sumOf { it.exposureDuration.coerceAtLeast(1) }
                    BusyTracker.update(OWNER, "Mixing audio…")
                    val audio = AudioMixer.mixAndEncode(
                        repo.getAudioTracks(projectId), fps, videoFrames * 1_000_000L / fps
                    )
                    job.ensureActive()
                    val ok = Mp4Encoder.encodeStreaming(
                        outputFile = outputFile,
                        width = w,
                        height = h,
                        fps = fps,
                        frameCount = n,
                        holdOf = { frames[it].exposureDuration },
                        frameAt = { i ->
                            job.ensureActive() // the encoder turns this into a clean abort
                            composeFrame(scratch, store, projectId, w, h, layerLists[i])
                            scratch
                        },
                        audio = audio,
                        onProgress = { p -> report((p * n).roundToInt(), p) }
                    )
                    job.ensureActive() // a cancel surfaces as "encoder failed"; report it as a cancel
                    if (!ok) throw IOException("The video encoder failed on this device")
                }
                ExportFormat.GIF -> {
                    val encoder = AnimatedGifEncoder()
                    val fps = project.fps.coerceAtLeast(1)
                    FileOutputStream(outputFile).use { os ->
                        encoder.start(os)
                        for (i in 0 until n) {
                            job.ensureActive()
                            composeFrame(scratch, store, projectId, w, h, layerLists[i])
                            // Honour each frame's hold time so the GIF lasts as long as the preview.
                            encoder.setDelay(1000 * frames[i].exposureDuration.coerceAtLeast(1) / fps)
                            encoder.addFrame(scratch)
                            report(i + 1, (i + 1f) / n)
                        }
                        encoder.finish()
                    }
                }
                ExportFormat.PNG_SEQUENCE -> {
                    ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                        for (i in 0 until n) {
                            job.ensureActive()
                            composeFrame(scratch, store, projectId, w, h, layerLists[i])
                            zos.putNextEntry(ZipEntry("frame_${String.format(Locale.ROOT, "%03d", i + 1)}.png"))
                            scratch.compress(Bitmap.CompressFormat.PNG, 100, zos)
                            zos.closeEntry()
                            report(i + 1, (i + 1f) / n)
                        }
                    }
                }
                ExportFormat.PNG_CURRENT_FRAME -> {
                    composeFrame(scratch, store, projectId, w, h, layerLists[0])
                    FileOutputStream(outputFile).use { os ->
                        if (!scratch.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                            throw IOException("Could not write the PNG")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            outputFile.delete() // never leave a truncated file behind
            throw e
        } finally {
            if (!scratch.isRecycled) scratch.recycle()
        }
        return outputFile
    }

    /** Draws the visible layers of one frame onto white, the same way the canvas does (opacity + blend mode). */
    private fun composeFrame(
        into: Bitmap,
        store: BitmapStore,
        projectId: Long,
        w: Int,
        h: Int,
        layers: List<LayerEntity>
    ) {
        val canvas = Canvas(into)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val dst = Rect(0, 0, w, h)
        for (l in layers) {
            if (!l.visible) continue
            paint.alpha = (l.opacity * 255f).roundToInt().coerceIn(0, 255)
            LayerBlending.apply(paint, LayerBlend.from(l.blendMode)) // was ignored: exports looked different from the canvas
            val layerBmp = store.load(projectId, l.id, w, h)
            canvas.drawBitmap(layerBmp, null, dst, paint)
            layerBmp.recycle()
        }
    }

    /** Project names may contain '/' or other characters that are not valid in file names. */
    private fun safeFileName(name: String): String {
        val cleaned = name.map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }
            .joinToString("").trim().take(60)
        return cleaned.ifEmpty { "WishyClip" }
    }

    private fun showFailureNotification(reason: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Export failed")
            .setContentText(reason)
            .setSmallIcon(R.drawable.ic_menu_save)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 2, notification)
    }

    private fun saveToPublicStorage(outputFile: File, format: ExportFormat): Uri? {
        val mimeType = when (format) {
            ExportFormat.MP4 -> "video/mp4"
            ExportFormat.GIF -> "image/gif"
            ExportFormat.PNG_SEQUENCE -> "application/zip"
            ExportFormat.PNG_CURRENT_FRAME -> "image/png"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, outputFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    val subDir = when (format) {
                        ExportFormat.MP4 -> Environment.DIRECTORY_MOVIES
                        ExportFormat.GIF, ExportFormat.PNG_CURRENT_FRAME -> Environment.DIRECTORY_PICTURES
                        ExportFormat.PNG_SEQUENCE -> Environment.DIRECTORY_DOWNLOADS
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$subDir/WishaClip")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collectionUri = when (format) {
                    ExportFormat.MP4 -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    ExportFormat.GIF, ExportFormat.PNG_CURRENT_FRAME -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    ExportFormat.PNG_SEQUENCE -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val uri = resolver.insert(collectionUri, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        outputFile.inputStream().use { it.copyTo(os) }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    return uri
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return try {
            val publicDirType = when (format) {
                ExportFormat.MP4 -> Environment.DIRECTORY_MOVIES
                ExportFormat.GIF, ExportFormat.PNG_CURRENT_FRAME -> Environment.DIRECTORY_PICTURES
                ExportFormat.PNG_SEQUENCE -> Environment.DIRECTORY_DOWNLOADS
            }
            val targetDir = File(Environment.getExternalStoragePublicDirectory(publicDirType), "WishaClip").apply { mkdirs() }
            val destFile = File(targetDir, outputFile.name)
            outputFile.copyTo(destFile, overwrite = true)
            FileProvider.getUriForFile(this, "$packageName.fileprovider", destFile)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", outputFile)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun updateProgress(progress: Int) {
        if (progress == lastNotifiedPercent) return // one notification update per percent is plenty
        lastNotifiedPercent = progress
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Exporting Animation")
            .setContentText("Processing ($progress%)")
            .setSmallIcon(R.drawable.ic_menu_save)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(uri: Uri?, fileName: String, format: ExportFormat) {
        val mimeType = when (format) {
            ExportFormat.MP4 -> "video/mp4"
            ExportFormat.GIF -> "image/gif"
            ExportFormat.PNG_SEQUENCE -> "application/zip"
            ExportFormat.PNG_CURRENT_FRAME -> "image/png"
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Export Complete!")
            .setContentText("Saved $fileName to device")
            .setSmallIcon(R.drawable.ic_menu_save)
            .setAutoCancel(true)
            .setOngoing(false)

        if (uri != null) {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.setContentIntent(pendingIntent)
        }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 1, notificationBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Export Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "export_channel"
        private const val NOTIFICATION_ID = 1001
        private const val OWNER = "export"

        @Volatile private var job: Job? = null

        /** Cancels the running export (called from the loading overlay's Cancel button). */
        fun cancel() { job?.cancel() }

        private val ExportFormat.label: String
            get() = when (this) {
                ExportFormat.MP4 -> "video"
                ExportFormat.GIF -> "GIF"
                ExportFormat.PNG_SEQUENCE -> "PNG sequence"
                ExportFormat.PNG_CURRENT_FRAME -> "frame"
            }

        /** [frameIndex] only matters for [ExportFormat.PNG_CURRENT_FRAME]. */
        fun start(context: Context, projectId: Long, format: ExportFormat, frameIndex: Int = -1) {
            val intent = Intent(context, ExportService::class.java).apply {
                putExtra("projectId", projectId)
                putExtra("format", format.name)
                putExtra("frameIndex", frameIndex)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
