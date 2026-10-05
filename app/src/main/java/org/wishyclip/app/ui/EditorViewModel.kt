package org.wishyclip.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.wishyclip.app.WishyApp
import org.wishyclip.app.canvas.FrameData
import org.wishyclip.app.canvas.BitmapOps
import org.wishyclip.app.audio.ExoPlayerAudioTrackManager
import org.wishyclip.app.audio.WaveformExtractor
import org.wishyclip.app.canvas.GhostFrame
import org.wishyclip.app.canvas.LassoSelection
import org.wishyclip.app.canvas.LayerData
import org.wishyclip.app.canvas.LayerUi
import org.wishyclip.app.canvas.OnionSkinData
import org.wishyclip.app.canvas.OnionSkinSettings
import org.wishyclip.app.canvas.ScanlineFillTool
import org.wishyclip.app.canvas.StandardLassoTool
import org.wishyclip.app.canvas.StrokeRenderer
import org.wishyclip.app.canvas.UndoEntry
import org.wishyclip.app.canvas.UndoManager
import org.wishyclip.app.canvas.snapshot
import org.wishyclip.app.data.AudioTrackEntity
import org.wishyclip.app.data.FrameEntity
import org.wishyclip.app.data.Importer
import org.wishyclip.app.data.LayerEntity
import org.wishyclip.app.data.ProjectEntity
import org.wishyclip.app.model.Tool
import java.io.File
import java.io.FileOutputStream

class EditorViewModel(app: Application, private val projectId: Long) : AndroidViewModel(app) {

    private val wishy = app as WishyApp
    private val repo = wishy.repository
    private val store = repo.store
    private val settings = wishy.settings

    // ---- UI state ----
    var project by mutableStateOf<ProjectEntity?>(null)
        private set
    var frames by mutableStateOf<List<FrameEntity>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var layerUi by mutableStateOf<List<LayerUi>>(emptyList())
        private set
    var activeLayerIndex by mutableIntStateOf(0)
        private set
    var tool by mutableStateOf(Tool.PEN)
        private set
    var color by mutableIntStateOf(-16777216)
        private set
    var brushSize by mutableFloatStateOf(8f)
        private set
    var opacity by mutableFloatStateOf(1f)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var loadError by mutableStateOf(false)
        private set
    var onionSkinSettings by mutableStateOf(OnionSkinSettings())
        private set
    var fillTolerance by mutableIntStateOf(32)
        private set
    var activeLassoSelection by mutableStateOf<LassoSelection?>(null)
        private set
    var audioTracks by mutableStateOf<List<AudioTrackEntity>>(emptyList())
        private set
    var stabilizer by mutableFloatStateOf(0f)
        private set
    private var copiedFrameData: FrameData? = null
    val canPasteFrame get() = copiedFrameData != null

    /** Bumped whenever pixels or layer metadata change so the canvas redraws. */
    var revision by mutableIntStateOf(0)
        private set

    // ---- internals (main thread only, except where noted) ----
    private val cache = HashMap<Long, FrameData>() // frame id -> data; only current frame +-2
    private var undoManager = UndoManager(20)
    private val renderer = StrokeRenderer()
    private val lassoTool = StandardLassoTool()
    private val audioManager = ExoPlayerAudioTrackManager(app)
    private val saveMutex = Mutex()
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var saveTicket = 0
    private var playJob: Job? = null
    private var prefetchJob: Job? = null
    private var strokeLayer: LayerData? = null
    private var strokeFrameId: Long = -1L

    init {
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        val p = repo.getProject(projectId)
        if (p == null) {
            loadError = true
            return
        }
        project = p
        val bytes = p.width.toLong() * p.height.toLong() * 4L
        undoManager = UndoManager((96_000_000L / bytes).toInt().coerceIn(5, 30))

        var list = repo.frames(projectId)
        if (list.isEmpty()) {
            val template = LayerEntity(frameId = 0, position = 0, name = "Layer 1", visible = true, opacity = 1f)
            val created = repo.insertFrame(projectId, 0, listOf(template))
            list = listOf(created.first)
        }
        frames = list
        color = settings.brushColor.first()
        brushSize = settings.brushSize.first()
        opacity = settings.brushOpacity.first()
        onionSkinSettings = settings.onionSettings.first()
        audioTracks = repo.getAudioTracks(projectId)
        currentIndex = 0
        ensureLoaded(list[0].id)
        refreshLayerUi()
        loaded = true
        revision++
        prefetch(0)
    }

    // ---------------------------------------------------------------- cache

    private fun currentFrameData(): FrameData? =
        frames.getOrNull(currentIndex)?.let { cache[it.id] }

    fun currentLayers(): List<LayerData> = currentFrameData()?.layers ?: emptyList()

    private fun activeLayer(): LayerData? = currentFrameData()?.layers?.getOrNull(activeLayerIndex)

    private suspend fun ensureLoaded(frameId: Long): FrameData {
        cache[frameId]?.let { return it }
        val p = project ?: throw IllegalStateException("project not loaded")
        // Taking saveMutex means a pending save of this frame finishes before we read its files.
        val data = saveMutex.withLock {
            withContext(Dispatchers.IO) {
                val entities = repo.layers(frameId)
                val layers = entities.map {
                    LayerData(it.id, it.name, it.visible, it.opacity, store.load(projectId, it.id, p.width, p.height))
                }
                FrameData(frameId, layers.toMutableList())
            }
        }
        val existing = cache[frameId]
        if (existing != null) return existing
        cache[frameId] = data
        return data
    }

    private fun trimCache() {
        val maxRange = maxOf(2, onionSkinSettings.framesBefore, onionSkinSettings.framesAfter)
        val keep = HashSet<Long>()
        for (i in (currentIndex - maxRange)..(currentIndex + maxRange)) {
            frames.getOrNull(i)?.let { keep.add(it.id) }
        }
        val evicted = cache.values.filter { it.frameId !in keep }
        if (evicted.isEmpty()) return
        for (fd in evicted) {
            cache.remove(fd.frameId)
            undoManager.dropFrame(fd.frameId)
        }
        syncUndoFlags()
        viewModelScope.launch {
            for (fd in evicted) {
                persistFrame(fd)
                fd.layers.forEach { layer ->
                    if (!layer.bitmap.isRecycled) {
                        layer.bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun prefetch(center: Int) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            val maxRange = maxOf(2, onionSkinSettings.framesBefore, onionSkinSettings.framesAfter)
            for (d in 1..maxRange) {
                frames.getOrNull(center + d)?.let { ensureLoaded(it.id) }
                frames.getOrNull(center - d)?.let { ensureLoaded(it.id) }
            }
            trimCache()
        }
    }

    // ---------------------------------------------------------------- frames

    fun selectFrame(index: Int) {
        if (strokeLayer != null) return
        if (isPlaying) stopPlay()
        if (index !in frames.indices) return
        viewModelScope.launch { gotoFrame(index) }
    }

    private suspend fun gotoFrame(index: Int) {
        val target = frames.getOrNull(index) ?: return
        val fd = ensureLoaded(target.id)
        currentIndex = index
        activeLayerIndex = activeLayerIndex.coerceIn(0, (fd.layers.size - 1).coerceAtLeast(0))
        refreshLayerUi()
        revision++
        trimCache()
        prefetch(index)
    }

    fun addFrame() {
        if (strokeLayer != null) return
        stopPlay()
        viewModelScope.launch {
            val templates = currentFrameData()?.layers?.mapIndexed { i, l ->
                LayerEntity(frameId = 0, position = i, name = l.name, visible = l.visible, opacity = l.opacity)
            } ?: listOf(LayerEntity(frameId = 0, position = 0, name = "Layer 1", visible = true, opacity = 1f))
            val position = currentIndex + 1
            repo.insertFrame(projectId, position, templates)
            frames = repo.frames(projectId)
            gotoFrame(position)
            repo.touch(projectId)
        }
    }

    fun duplicateFrame() {
        if (strokeLayer != null) return
        stopPlay()
        viewModelScope.launch {
            val source = frames.getOrNull(currentIndex) ?: return@launch
            currentFrameData()?.let { persistFrame(it) } // files must be current before copying
            val position = currentIndex + 1
            repo.duplicateFrame(source, position)
            frames = repo.frames(projectId)
            gotoFrame(position)
            repo.touch(projectId)
        }
    }

    fun deleteFrame() {
        if (strokeLayer != null) return
        stopPlay()
        viewModelScope.launch {
            if (frames.size <= 1) {
                clearFrameInternal()
                return@launch
            }
            val frame = frames.getOrNull(currentIndex) ?: return@launch
            val evictedFd = cache.remove(frame.id)
            undoManager.dropFrame(frame.id)
            syncUndoFlags()
            evictedFd?.layers?.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
            repo.deleteFrame(frame)
            frames = repo.frames(projectId)
            val newIndex = currentIndex.coerceAtMost(frames.lastIndex)
            currentIndex = newIndex
            gotoFrame(newIndex)
            repo.touch(projectId)
        }
    }

    /** Moves the current frame by [delta] positions (-1 = earlier, +1 = later). */
    fun moveFrame(delta: Int) {
        if (strokeLayer != null) return
        stopPlay()
        val from = currentIndex
        val to = from + delta
        if (to !in frames.indices) return
        viewModelScope.launch {
            repo.moveFrame(projectId, from, to)
            frames = repo.frames(projectId)
            currentIndex = to
            revision++
            trimCache()
            prefetch(to)
        }
    }

    private fun clearFrameInternal() {
        val fd = currentFrameData() ?: return
        val frameId = fd.frameId
        for (layer in fd.layers) {
            undoManager.push(UndoEntry(frameId, layer.id, layer.bitmap.snapshot()))
            BitmapOps.clear(layer.bitmap)
            layer.version++
        }
        syncUndoFlags()
        revision++
        scheduleSave()
    }

    // ---------------------------------------------------------------- playback

    fun togglePlay() {
        if (isPlaying) stopPlay() else startPlay()
    }

    private fun startPlay() {
        if (strokeLayer != null || frames.size < 2) return
        isPlaying = true
        val track = audioTracks.firstOrNull()
        if (track != null) {
            val fps = (project?.fps ?: 12).coerceAtLeast(1)
            val startMs = (currentIndex - track.startFrame) * 1000L / fps + track.trimStartMs
            if (startMs in track.trimStartMs..(track.trimStartMs + track.durationMs)) {
                audioManager.prepare(track.filePath, track.volume)
                audioManager.playAtOffset(startMs, track.volume)
            }
        }
        playJob = viewModelScope.launch {
            val fps = (project?.fps ?: 12).coerceAtLeast(1)
            val frameMs = 1000L / fps
            while (isActive && isPlaying) {
                val t0 = SystemClock.elapsedRealtime()
                val next = (currentIndex + 1) % frames.size
                gotoFrame(next)
                val spent = SystemClock.elapsedRealtime() - t0
                delay((frameMs - spent).coerceAtLeast(1L))
            }
        }
    }

    private fun stopPlay() {
        isPlaying = false
        audioManager.pause()
        playJob?.cancel()
        playJob = null
    }

    // ---------------------------------------------------------------- tools

    fun toggleOnionSkin() {
        val next = onionSkinSettings.copy(enabled = !onionSkinSettings.enabled)
        updateOnionSkinSettings(next)
    }

    fun updateOnionSkinSettings(s: OnionSkinSettings) {
        onionSkinSettings = s
        viewModelScope.launch { settings.saveOnionSettings(s) }
        revision++
        prefetch(currentIndex)
    }

    fun getOnionSkinData(): OnionSkinData? {
        val s = onionSkinSettings
        if (!s.enabled) return null

        val before = mutableListOf<GhostFrame>()
        for (d in 1..s.framesBefore) {
            val idx = currentIndex - d
            val frame = frames.getOrNull(idx) ?: continue
            val fd = cache[frame.id] ?: continue
            before.add(GhostFrame(distance = d, layers = fd.layers))
        }
        before.sortByDescending { it.distance }

        val after = mutableListOf<GhostFrame>()
        for (d in 1..s.framesAfter) {
            val idx = currentIndex + d
            val frame = frames.getOrNull(idx) ?: continue
            val fd = cache[frame.id] ?: continue
            after.add(GhostFrame(distance = d, layers = fd.layers))
        }
        after.sortByDescending { it.distance }

        return OnionSkinData(s, before, after)
    }

    fun selectTool(t: Tool) {
        tool = t
    }

    fun updateColor(argb: Int) {
        color = argb
        persistBrush()
    }

    fun updateBrushSize(v: Float) {
        brushSize = v
    }

    fun updateOpacity(v: Float) {
        opacity = v
    }

    fun persistBrush() {
        val c = color
        val s = brushSize
        val o = opacity
        viewModelScope.launch { settings.saveBrush(c, s, o) }
    }

    // ---------------------------------------------------------------- drawing

    fun updateFillTolerance(tolerance: Int) {
        fillTolerance = tolerance.coerceIn(0, 255)
    }

    fun executeFill(x: Float, y: Float) {
        if (isPlaying) return
        val layer = activeLayer() ?: return
        if (!layer.visible) return
        val frameId = frames.getOrNull(currentIndex)?.id ?: return

        viewModelScope.launch(Dispatchers.Default) {
            val before = layer.bitmap.snapshot()
            val filler = ScanlineFillTool()
            val changed = filler.fill(layer.bitmap, x.toInt(), y.toInt(), color, fillTolerance)
            if (changed) {
                undoManager.push(UndoEntry(frameId, layer.id, before))
                layer.version++
                syncUndoFlags()
                revision++
                scheduleSave()
            } else {
                before.recycle()
            }
        }
    }

    fun importImageAsLayer(uri: Uri) {
        val p = project ?: return
        viewModelScope.launch {
            val bmp = Importer.importImage(getApplication(), uri, p.width, p.height) ?: return@launch
            val fd = currentFrameData() ?: return@launch
            val before = activeLayer()?.bitmap?.snapshot()
            val layer = activeLayer()
            if (layer != null) {
                if (before != null) {
                    undoManager.push(UndoEntry(fd.frameId, layer.id, before))
                }
                BitmapOps.replace(layer.bitmap, bmp)
                bmp.recycle()
                layer.version++
            } else {
                val entity = repo.addLayer(fd.frameId, fd.layers.size, "Image Layer")
                fd.layers.add(LayerData(entity.id, entity.name, true, 1f, bmp))
            }
            refreshLayerUi()
            revision++
            scheduleSave()
        }
    }

    fun importImageSequence(uris: List<Uri>) {
        val p = project ?: return
        viewModelScope.launch {
            for (uri in uris) {
                val bmp = Importer.importImage(getApplication(), uri, p.width, p.height) ?: continue
                val templates = listOf(LayerEntity(frameId = 0, position = 0, name = "Imported", visible = true, opacity = 1f))
                val pos = frames.size
                val (newFrame, _) = repo.insertFrame(projectId, pos, templates)
                val fd = ensureLoaded(newFrame.id)
                if (fd.layers.isNotEmpty()) {
                    BitmapOps.replace(fd.layers[0].bitmap, bmp)
                    fd.layers[0].version++
                }
                bmp.recycle()
            }
            frames = repo.frames(projectId)
            if (frames.isNotEmpty()) {
                gotoFrame(frames.lastIndex)
            }
            repo.touch(projectId)
        }
    }

    fun importVideoAsFrames(uri: Uri) {
        val p = project ?: return
        viewModelScope.launch {
            val fps = p.fps
            val extracted = Importer.importVideoFrames(getApplication(), uri, p.width, p.height, fps)
            for (bmp in extracted) {
                val templates = listOf(LayerEntity(frameId = 0, position = 0, name = "Video Frame", visible = true, opacity = 1f))
                val pos = frames.size
                val (newFrame, _) = repo.insertFrame(projectId, pos, templates)
                val fd = ensureLoaded(newFrame.id)
                if (fd.layers.isNotEmpty()) {
                    BitmapOps.replace(fd.layers[0].bitmap, bmp)
                    fd.layers[0].version++
                }
                bmp.recycle()
            }
            frames = repo.frames(projectId)
            if (frames.isNotEmpty()) {
                gotoFrame(frames.lastIndex)
            }
            repo.touch(projectId)
        }
    }

    fun addAudioTrackFromUri(uri: Uri, name: String = "Imported Track") {
        viewModelScope.launch(Dispatchers.IO) {
            val audioDir = File(getApplication<Application>().filesDir, "projects/$projectId/audio").apply { mkdirs() }
            val destFile = File(audioDir, "track_${System.currentTimeMillis()}.m4a")
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            val duration = WaveformExtractor.getAudioDurationMs(getApplication(), uri)
            val entity = AudioTrackEntity(
                projectId = projectId,
                filePath = destFile.absolutePath,
                name = name,
                startFrame = currentIndex,
                durationMs = duration
            )
            repo.addAudioTrack(entity)
            audioTracks = repo.getAudioTracks(projectId)
            revision++
        }
    }

    fun addRecordedVoiceTrack(recordedFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val duration = WaveformExtractor.getAudioDurationMs(recordedFile.absolutePath)
            val entity = AudioTrackEntity(
                projectId = projectId,
                filePath = recordedFile.absolutePath,
                name = "Voice Record",
                startFrame = currentIndex,
                durationMs = duration
            )
            repo.addAudioTrack(entity)
            audioTracks = repo.getAudioTracks(projectId)
            revision++
        }
    }

    fun updateAudioTrack(track: AudioTrackEntity) {
        viewModelScope.launch {
            repo.updateAudioTrack(track)
            audioTracks = repo.getAudioTracks(projectId)
            revision++
        }
    }

    fun deleteAudioTrack(id: Long) {
        viewModelScope.launch {
            repo.deleteAudioTrack(id)
            audioTracks = repo.getAudioTracks(projectId)
            revision++
        }
    }

    fun moveLassoSelection(dx: Float, dy: Float) {
        val sel = activeLassoSelection ?: return
        sel.translateX += dx
        sel.translateY += dy
        revision++
    }

    fun scaleLassoSelection(sx: Float, sy: Float) {
        val sel = activeLassoSelection ?: return
        sel.scaleX = (sel.scaleX * sx).coerceIn(0.1f, 10f)
        sel.scaleY = (sel.scaleY * sy).coerceIn(0.1f, 10f)
        revision++
    }

    fun rotateLassoSelection(degrees: Float) {
        val sel = activeLassoSelection ?: return
        sel.rotation += degrees
        revision++
    }

    fun commitLassoSelection() {
        val sel = activeLassoSelection ?: return
        val layer = activeLayer() ?: return
        val frameId = frames.getOrNull(currentIndex)?.id ?: return

        val before = layer.bitmap.snapshot()
        val canvas = Canvas(layer.bitmap)
        val matrix = sel.getMatrix()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sel.pixels, matrix, paint)

        if (!sel.pixels.isRecycled) sel.pixels.recycle()
        activeLassoSelection = null

        undoManager.push(UndoEntry(frameId, layer.id, before))
        layer.version++
        syncUndoFlags()
        revision++
        scheduleSave()
    }

    fun deleteLassoSelection() {
        val sel = activeLassoSelection ?: return
        if (!sel.pixels.isRecycled) sel.pixels.recycle()
        activeLassoSelection = null
        revision++
        scheduleSave()
    }

    fun drawTextAt(text: String, x: Float, y: Float) {
        val layer = activeLayer() ?: return
        val frameId = frames.getOrNull(currentIndex)?.id ?: return
        val before = layer.bitmap.snapshot()

        val canvas = Canvas(layer.bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@EditorViewModel.color
            textSize = (brushSize * 4f).coerceAtLeast(16f)
            alpha = (opacity * 255f).toInt()
        }
        canvas.drawText(text, x, y, paint)

        undoManager.push(UndoEntry(frameId, layer.id, before))
        layer.version++
        syncUndoFlags()
        revision++
        scheduleSave()
    }

    fun updateStabilizer(value: Float) {
        stabilizer = value.coerceIn(0f, 1f)
    }

    fun copyFrame() {
        val fd = currentFrameData() ?: return
        val copiedLayers = fd.layers.map { l ->
            LayerData(l.id, l.name, l.visible, l.opacity, l.bitmap.snapshot())
        }
        copiedFrameData = FrameData(fd.frameId, copiedLayers.toMutableList())
        revision++
    }

    fun pasteFrame() {
        val copy = copiedFrameData ?: return
        viewModelScope.launch {
            val templates = copy.layers.mapIndexed { i, l ->
                LayerEntity(frameId = 0, position = i, name = l.name, visible = l.visible, opacity = l.opacity)
            }
            val pos = currentIndex + 1
            val (newFrame, _) = repo.insertFrame(projectId, pos, templates)
            val fd = ensureLoaded(newFrame.id)
            for (i in copy.layers.indices) {
                if (i in fd.layers.indices) {
                    BitmapOps.replace(fd.layers[i].bitmap, copy.layers[i].bitmap)
                    fd.layers[i].version++
                }
            }
            frames = repo.frames(projectId)
            gotoFrame(pos)
            repo.touch(projectId)
        }
    }

    fun updateProjectFps(fps: Int) {
        val newFps = fps.coerceIn(1, 60)
        project = project?.copy(fps = newFps)
        viewModelScope.launch { repo.updateProjectFps(projectId, newFps) }
        revision++
    }

    fun strokeStart(x: Float, y: Float, pressure: Float = 1f, tilt: Float = 0f) {
        if (isPlaying || strokeLayer != null) return
        smoothedX = x
        smoothedY = y
        if (activeLassoSelection != null) {
            commitLassoSelection()
        }
        if (tool == Tool.FILL) {
            executeFill(x, y)
            return
        }
        if (tool == Tool.LASSO) {
            val layer = activeLayer() ?: return
            if (!layer.visible) return
            val frameId = frames.getOrNull(currentIndex)?.id ?: return
            strokeLayer = layer
            strokeFrameId = frameId
            val before = layer.bitmap.snapshot()
            undoManager.push(UndoEntry(frameId, layer.id, before))
            lassoTool.begin(x, y)
            return
        }
        val layer = activeLayer() ?: return
        if (!layer.visible) return
        val frameId = frames.getOrNull(currentIndex)?.id ?: return
        strokeLayer = layer
        strokeFrameId = frameId
        renderer.begin(layer.bitmap.snapshot(), x, y, pressure, tilt)
        renderStroke()
    }

    fun strokeMove(x: Float, y: Float, pressure: Float = 1f, tilt: Float = 0f) {
        if (strokeLayer == null) return
        val curX: Float
        val curY: Float
        if (stabilizer > 0f) {
            val factor = (1f - stabilizer * 0.85f).coerceIn(0.05f, 1f)
            smoothedX += (x - smoothedX) * factor
            smoothedY += (y - smoothedY) * factor
            curX = smoothedX
            curY = smoothedY
        } else {
            curX = x
            curY = y
        }
        if (tool == Tool.LASSO) {
            lassoTool.addPoint(curX, curY)
            return
        }
        renderer.moveTo(curX, curY, pressure, tilt)
        renderStroke()
    }

    fun strokeEnd() {
        val layer = strokeLayer ?: return
        strokeLayer = null
        if (tool == Tool.LASSO) {
            val sel = lassoTool.end(layer.bitmap)
            activeLassoSelection = sel
            layer.version++
            syncUndoFlags()
            revision++
            scheduleSave()
            return
        }
        val before = renderer.finish()
        if (before != null) {
            undoManager.push(UndoEntry(strokeFrameId, layer.id, before))
        }
        layer.version++
        syncUndoFlags()
        revision++
        scheduleSave()
    }

    private fun renderStroke() {
        val layer = strokeLayer ?: return
        renderer.render(layer.bitmap, tool, color, brushSize, opacity)
        revision++
    }

    // ---------------------------------------------------------------- undo / redo

    fun undo() {
        if (strokeLayer == null) applyHistory(true)
    }

    fun redo() {
        if (strokeLayer == null) applyHistory(false)
    }

    private fun applyHistory(isUndo: Boolean) {
        while (true) {
            val entry = (if (isUndo) undoManager.popUndo() else undoManager.popRedo()) ?: break
            val layer = cache[entry.frameId]?.layers?.firstOrNull { it.id == entry.layerId }
            if (layer == null) {
                entry.bitmap.recycle()
                continue
            }
            val counterpart = UndoEntry(entry.frameId, entry.layerId, layer.bitmap.snapshot())
            if (isUndo) undoManager.pushRedo(counterpart) else undoManager.pushUndoKeepRedo(counterpart)
            BitmapOps.replace(layer.bitmap, entry.bitmap)
            entry.bitmap.recycle()
            layer.version++
            val idx = frames.indexOfFirst { it.id == entry.frameId }
            if (idx >= 0 && idx != currentIndex) {
                viewModelScope.launch { gotoFrame(idx) }
            }
            break
        }
        syncUndoFlags()
        revision++
        scheduleSave()
    }

    private fun syncUndoFlags() {
        canUndo = undoManager.canUndo
        canRedo = undoManager.canRedo
    }

    // ---------------------------------------------------------------- layers (current frame)

    private fun refreshLayerUi() {
        layerUi = currentFrameData()?.layers?.map { LayerUi(it.id, it.name, it.visible, it.opacity) } ?: emptyList()
    }

    fun selectLayer(index: Int) {
        if (index in layerUi.indices) activeLayerIndex = index
    }

    fun addLayer() {
        val fd = currentFrameData() ?: return
        val p = project ?: return
        viewModelScope.launch {
            val entity = repo.addLayer(fd.frameId, fd.layers.size, "Layer ${fd.layers.size + 1}")
            val bmp = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
            fd.layers.add(LayerData(entity.id, entity.name, true, 1f, bmp))
            fd.metaVersion++
            activeLayerIndex = fd.layers.lastIndex
            refreshLayerUi()
            revision++
            scheduleSave()
        }
    }

    fun deleteLayer(index: Int) {
        val fd = currentFrameData() ?: return
        if (fd.layers.size <= 1 || index !in fd.layers.indices || strokeLayer != null) return
        val layer = fd.layers.removeAt(index)
        undoManager.dropLayer(layer.id)
        syncUndoFlags()
        if (!layer.bitmap.isRecycled) {
            layer.bitmap.recycle()
        }
        fd.metaVersion++
        activeLayerIndex = activeLayerIndex.coerceIn(0, fd.layers.lastIndex)
        refreshLayerUi()
        revision++
        viewModelScope.launch { repo.deleteLayer(projectId, layer.id) }
        scheduleSave()
    }

    fun setLayerVisible(index: Int, visible: Boolean) {
        val fd = currentFrameData() ?: return
        val layer = fd.layers.getOrNull(index) ?: return
        layer.visible = visible
        fd.metaVersion++
        refreshLayerUi()
        revision++
        scheduleSave()
    }

    fun setLayerOpacity(index: Int, value: Float) {
        val fd = currentFrameData() ?: return
        val layer = fd.layers.getOrNull(index) ?: return
        layer.opacity = value.coerceIn(0f, 1f)
        fd.metaVersion++
        refreshLayerUi()
        revision++
        scheduleSave()
    }

    /** [delta] +1 moves the layer up (towards the top of the stack), -1 down. */
    fun moveLayer(index: Int, delta: Int) {
        val fd = currentFrameData() ?: return
        val target = index + delta
        if (index !in fd.layers.indices || target !in fd.layers.indices) return
        val layer = fd.layers.removeAt(index)
        fd.layers.add(target, layer)
        if (activeLayerIndex == index) activeLayerIndex = target
        fd.metaVersion++
        refreshLayerUi()
        revision++
        scheduleSave()
    }

    // ---------------------------------------------------------------- saving

    private fun scheduleSave() {
        saveTicket += 1
        val ticket = saveTicket
        viewModelScope.launch {
            delay(1500)
            if (ticket == saveTicket) saveAll()
        }
    }

    private suspend fun saveAll() {
        var wrote = false
        for (fd in cache.values.toList()) {
            if (persistFrame(fd)) wrote = true
        }
        if (wrote) repo.touch(projectId)
    }

    /** Writes dirty layers (as PNG) and layer metadata. @return true if anything was written. */
    private suspend fun persistFrame(fd: FrameData): Boolean {
        val dirtyLayers = fd.layers.filter { it.dirty }
        val metaDirty = fd.metaVersion != fd.metaSavedVersion
        if (dirtyLayers.isEmpty() && !metaDirty) return false
        val pid = projectId
        // Copies are taken on the calling (main) thread so the IO thread never reads a bitmap being drawn on.
        val copies = dirtyLayers.map { Triple(it, it.version, it.bitmap.snapshot()) }
        val metaVersion = fd.metaVersion
        val metas = fd.layers.mapIndexed { i, l ->
            LayerEntity(id = l.id, frameId = fd.frameId, position = i, name = l.name, visible = l.visible, opacity = l.opacity)
        }
        val isFirst = frames.firstOrNull()?.id == fd.frameId
        val thumb = if (isFirst && dirtyLayers.isNotEmpty()) composeThumb(fd) else null
        saveMutex.withLock {
            withContext(Dispatchers.IO) {
                for ((layer, _, bmp) in copies) {
                    store.save(pid, layer.id, bmp)
                    bmp.recycle()
                }
                repo.saveLayers(metas)
                if (thumb != null) {
                    store.saveThumb(pid, thumb)
                    thumb.recycle()
                }
            }
        }
        for ((layer, version, _) in copies) layer.savedVersion = version
        fd.metaSavedVersion = metaVersion
        return true
    }

    private fun composeThumb(fd: FrameData): Bitmap? {
        val p = project ?: return null
        val tw = 320
        val th = (tw.toLong() * p.height / p.width).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val dst = Rect(0, 0, tw, th)
        for (l in fd.layers) {
            if (!l.visible) continue
            paint.alpha = (l.opacity * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(l.bitmap, null, dst, paint)
        }
        return bmp
    }

    /** Called when the host activity stops: pause playback and flush to disk. */
    fun onHostStop() {
        stopPlay()
        viewModelScope.launch { saveAll() }
    }

    fun saveAndExit(onDone: () -> Unit) {
        stopPlay()
        viewModelScope.launch {
            saveAll()
            onDone()
        }
    }

    override fun onCleared() {
        audioManager.release()
        val remaining = cache.values.toList()
        try {
            runBlocking { for (fd in remaining) persistFrame(fd) }
        } catch (e: Exception) {
            // Nothing sensible to do while the ViewModel is being destroyed.
        }
        for (fd in remaining) {
            fd.layers.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        }
        cache.clear()
        undoManager.clear()
        super.onCleared()
    }
}

class EditorViewModelFactory(
    private val app: Application,
    private val projectId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(app, projectId) as T
}
