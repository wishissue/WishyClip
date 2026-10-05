package org.wishyclip.app.export

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.wishyclip.app.WishyApp
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val projectId = intent?.getLongExtra("projectId", -1L) ?: -1L
        val formatStr = intent?.getStringExtra("format") ?: "MP4"
        val format = ExportFormat.valueOf(formatStr)

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

        startForeground(NOTIFICATION_ID, notification)

        CoroutineScope(Dispatchers.IO).launch {
            performExport(projectId, format)
        }

        return START_NOT_STICKY
    }

    private suspend fun performExport(projectId: Long, format: ExportFormat) {
        val wishy = applicationContext as WishyApp
        val repo = wishy.repository
        val store = repo.store
        val project = repo.getProject(projectId) ?: return
        val frames = repo.frames(projectId)

        val compositeFrames = mutableListOf<Bitmap>()
        for (i in frames.indices) {
            val f = frames[i]
            val layers = repo.layers(f.id)
            val bmp = Bitmap.createBitmap(project.width, project.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val dst = Rect(0, 0, project.width, project.height)
            for (l in layers) {
                if (!l.visible) continue
                paint.alpha = (l.opacity * 255f).toInt()
                val layerBmp = store.load(projectId, l.id, project.width, project.height)
                canvas.drawBitmap(layerBmp, null, dst, paint)
                layerBmp.recycle()
            }
            compositeFrames.add(bmp)
            updateProgress(((i + 1) * 50 / frames.size.coerceAtLeast(1)))
        }

        val exportDir = File(cacheDir, "exports").apply { mkdirs() }
        val outputFile = when (format) {
            ExportFormat.MP4 -> File(exportDir, "${project.name}.mp4")
            ExportFormat.GIF -> File(exportDir, "${project.name}.gif")
            ExportFormat.PNG_SEQUENCE -> File(exportDir, "${project.name}_sequence.zip")
            ExportFormat.PNG_CURRENT_FRAME -> File(exportDir, "${project.name}_frame.png")
        }

        when (format) {
            ExportFormat.MP4 -> {
                Mp4Encoder.encodeMp4(
                    outputFile = outputFile,
                    width = project.width,
                    height = project.height,
                    fps = project.fps,
                    frames = compositeFrames,
                    onProgress = { p -> updateProgress(50 + (p * 50).toInt()) }
                )
            }
            ExportFormat.GIF -> {
                val encoder = AnimatedGifEncoder()
                FileOutputStream(outputFile).use { os ->
                    encoder.start(os)
                    encoder.setDelay(1000 / project.fps.coerceAtLeast(1))
                    for (i in compositeFrames.indices) {
                        encoder.addFrame(compositeFrames[i])
                        updateProgress(50 + ((i + 1) * 50 / compositeFrames.size))
                    }
                    encoder.finish()
                }
            }
            ExportFormat.PNG_SEQUENCE -> {
                ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                    for (i in compositeFrames.indices) {
                        val entry = ZipEntry("frame_${String.format("%03d", i + 1)}.png")
                        zos.putNextEntry(entry)
                        compositeFrames[i].compress(Bitmap.CompressFormat.PNG, 100, zos)
                        zos.closeEntry()
                        updateProgress(50 + ((i + 1) * 50 / compositeFrames.size))
                    }
                }
            }
            ExportFormat.PNG_CURRENT_FRAME -> {
                FileOutputStream(outputFile).use { os ->
                    compositeFrames.firstOrNull()?.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
            }
        }

        compositeFrames.forEach { if (!it.isRecycled) it.recycle() }

        val shareUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            outputFile
        )

        showCompletionNotification(shareUri, format)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun updateProgress(progress: Int) {
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

    private fun showCompletionNotification(uri: Uri, format: ExportFormat) {
        val mimeType = when (format) {
            ExportFormat.MP4 -> "video/mp4"
            ExportFormat.GIF -> "image/gif"
            ExportFormat.PNG_SEQUENCE -> "application/zip"
            ExportFormat.PNG_CURRENT_FRAME -> "image/png"
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent.createChooser(shareIntent, "Share Export"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Export Complete!")
            .setContentText("Tap to share your animation")
            .setSmallIcon(R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 1, notification)

        val chooser = Intent.createChooser(shareIntent, "Share Export").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(chooser)
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

        fun start(context: Context, projectId: Long, format: ExportFormat) {
            val intent = Intent(context, ExportService::class.java).apply {
                putExtra("projectId", projectId)
                putExtra("format", format.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
