package org.wishyclip.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import java.io.File as JavaFile
import android.provider.OpenableColumns
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
import org.wishyclip.app.canvas.DabBrush
import org.wishyclip.app.canvas.FrameData
import org.wishyclip.app.canvas.BitmapOps
import org.wishyclip.app.audio.ExoPlayerAudioTrackManager
import org.wishyclip.app.audio.WaveformExtractor
import org.wishyclip.app.canvas.GhostFrame
import org.wishyclip.app.brush.BrushLimits
import org.wishyclip.app.brush.StoredBrush
import org.wishyclip.app.canvas.LassoSelection
import org.wishyclip.app.canvas.LayerBlending
import org.wishyclip.app.canvas.RulerState
import org.wishyclip.app.canvas.LiveStroke
import org.wishyclip.app.canvas.SelectionHit
import org.wishyclip.app.canvas.TextRaster
import org.wishyclip.app.canvas.TextSpec
import org.wishyclip.app.canvas.selectionHandleSlop
import org.wishyclip.app.canvas.selectionRotateOffset
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
import org.wishyclip.app.data.FontLibrary
import org.wishyclip.app.data.FontOption
import org.wishyclip.app.data.FrameEntity
import org.wishyclip.app.data.Importer
import org.wishyclip.app.data.LayerEntity
import org.wishyclip.app.data.ProjectEntity
import org.wishyclip.app.model.LayerBlend
import org.wishyclip.app.model.MirrorMode
import org.wishyclip.app.model.Tool
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

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
    var textEditorOpen by mutableStateOf(false)
        private set
    var textEditorInitial by mutableStateOf("")
        private set
    var audioTracks by mutableStateOf<List<AudioTrackEntity>>(emptyList())
        private set
    var stabilizer by mutableFloatStateOf(0f)
        private set
    var mirrorMode by mutableStateOf(MirrorMode.OFF)
        private set
    var rulerVisible by mutableStateOf(false)
        private set
    var ruler by mutableStateOf<RulerState?>(null)
        private set
    var customBrushes by mutableStateOf<List<StoredBrush>>(emptyList())
        private set
    var activeCustomBrushId by mutableStateOf<String?>(null)
        private set

    /** Short transient status text (e.g. "This layer is locked"); the editor shows it briefly. */
    var message by mutableStateOf<String?>(null)
        private set
    private var messageTicket = 0
    private var toolBeforeEyedropper: Tool? = null
    private var copiedFrameData: FrameData? = null
    val canPasteFrame get() = copiedFrameData != null

    /** Bumped whenever pixels or layer metadata change so the canvas redraws. */
    var revision by mutableIntStateOf(0)
        private set

    // ---- internals (main thread only, except where noted) ----
    private val cache = HashMap<Long, FrameData>() // frame id -> data; only current frame +-2
    private var undoManager = UndoManager(40, 64L * 1024 * 1024)
    private val renderer = StrokeRenderer()
    private val lassoTool = StandardLassoTool()
    private val audioManager = org.wishyclip.app.audio.MultiTrackAudioPlayer(app)
    private val saveMutex = Mutex()
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var saveTicket = 0
    private var playJob: Job? = null
    private var prefetchJob: Job? = null
    private enum class Gesture { NONE, SELECTING, MOVE, SCALE, ROTATE, TEXT_TAP }
    private var gesture = Gesture.NONE
    private var gestureLastX = 0f
    private var gestureLastY = 0f
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureMoved = 0f
    private var textPendingX = 0f
    private var textPendingY = 0f
    private var textEditingExisting = false
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
        customBrushes = withContext(Dispatchers.IO) { wishy.brushLibrary.list() }
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

    fun currentLayers(): List<LayerData> = currentFrameData()?.layers?.map {
        LayerData(it.id, it.name, it.visible, it.opacity, it.bitmap, it.locked, it.blendMode).apply {
            version = it.version
            savedVersion = it.savedVersion
        }
    } ?: emptyList()

    private fun activeLayer(): LayerData? = currentFrameData()?.layers?.getOrNull(activeLayerIndex)

    private suspend fun ensureLoaded(frameId: Long): FrameData {
        cache[frameId]?.let { return it }
        val p = project ?: throw IllegalStateException("project not loaded")
        // Taking saveMutex means a pending save of this frame finishes before we read its files.
        val data = saveMutex.withLock {
            withContext(Dispatchers.IO) {
                val entities = repo.layers(frameId)
                val layers = entities.map {
                    LayerData(
                        it.id, it.name, it.visible, it.opacity,
                        store.load(projectId, it.id, p.width, p.height),
                        it.locked, LayerBlend.from(it.blendMode)
                    )
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
        commitLassoSelection()
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
                LayerEntity(
                    frameId = 0, position = i, name = l.name, visible = l.visible, opacity = l.opacity,
                    locked = l.locked, blendMode = l.blendMode.name
                )
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

    fun updateFrameExposure(index: Int, newDuration: Int) {
        val clamped = newDuration.coerceIn(1, 120)
        val frame = frames.getOrNull(index) ?: return
        viewModelScope.launch {
            repo.updateFrameExposure(frame.id, clamped)
            frames = repo.frames(projectId)
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

    /** Timeline position of a frame, counting holds. Audio start positions use this unit. */
    private fun timelineFrameOf(index: Int): Int =
        frames.take(index.coerceAtLeast(0)).sumOf { it.exposureDuration.coerceAtLeast(1) }

    fun togglePlay() {
        commitLassoSelection()
        if (isPlaying) stopPlay() else startPlay()
    }

    private fun startPlay() {
        if (strokeLayer != null || frames.size < 2) return
        isPlaying = true
        audioManager.sync(audioTracks, timelineFrameOf(currentIndex), (project?.fps ?: 12).coerceAtLeast(1))
        playJob = viewModelScope.launch {
            val fps = (project?.fps ?: 12).coerceAtLeast(1)
            val frameMs = 1000L / fps
            var holdTicks = 0
            while (isActive && isPlaying) {
                val t0 = SystemClock.elapsedRealtime()
                val currentFrame = frames.getOrNull(currentIndex)
                val requiredHold = currentFrame?.exposureDuration ?: 1
                holdTicks++
                if (holdTicks >= requiredHold) {
                    holdTicks = 0
                    val next = (currentIndex + 1) % frames.size
                    gotoFrame(next)
                    audioManager.sync(audioTracks, timelineFrameOf(next), fps)
                }
                val spent = SystemClock.elapsedRealtime() - t0
                delay((frameMs - spent).coerceAtLeast(1L))
            }
        }
    }

    private fun stopPlay() {
        isPlaying = false
        audioManager.pauseAll()
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
        if (t != tool) commitLassoSelection()
        if (t == Tool.EYEDROPPER && tool != Tool.EYEDROPPER) toolBeforeEyedropper = tool
        tool = t
        // Built-in brushes top out at 60 px; a size dialled up for a stamp brush must not carry over.
        if (t != Tool.CUSTOM && brushSize > 60f) brushSize = 60f
    }

    fun selectCustomBrush(id: String) {
        if (customBrushes.none { it.id == id }) return
        activeCustomBrushId = id
        selectTool(Tool.CUSTOM)
        // A dab the size of a pen line (8 px) hides the tip's shape entirely, which is why
        // imported brushes looked like ordinary strokes. Start at a size where the tip is visible.
        if (brushSize < 24f) {
            brushSize = 40f
            persistBrush()
        }
    }

    /** The stamp used for a brush's menu preview (same object type the canvas draws with). */
    fun customDab(brush: StoredBrush): DabBrush? = wishy.brushLibrary.dab(brush)

    /** Live edit of a brush's shape settings; call [commitCustomBrush] when the user lets go. */
    fun updateCustomBrush(updated: StoredBrush) {
        customBrushes = customBrushes.map { if (it.id == updated.id) updated else it }
    }

    fun commitCustomBrush() {
        val brush = customBrushes.firstOrNull { it.id == activeCustomBrushId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                wishy.brushLibrary.update(brush)
            } catch (e: Exception) {
                // The in-memory settings still apply for this session.
            }
        }
    }

    fun showMessage(text: String) {
        message = text
        val ticket = ++messageTicket
        viewModelScope.launch {
            delay(2500)
            if (ticket == messageTicket) message = null
        }
    }

    // ---------------------------------------------------------------- mirror / ruler

    fun cycleMirror() {
        mirrorMode = mirrorMode.next()
        showMessage(mirrorMode.label)
    }

    fun toggleRuler() {
        val p = project ?: return
        if (!rulerVisible && ruler == null) ruler = RulerState.default(p.width, p.height)
        rulerVisible = !rulerVisible
        showMessage(if (rulerVisible) "Ruler on: start a stroke beside it to snap" else "Ruler off")
    }

    fun updateRuler(r: RulerState) {
        ruler = r
    }

    /** The ruler to show/snap to, or null when it is switched off. */
    fun activeRuler(): RulerState? = if (rulerVisible) ruler else null

    // ---------------------------------------------------------------- imported brushes

    fun importBrushFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            var added = 0
            var lastError: String? = null
            var first: StoredBrush? = null
            for (uri in uris) {
                val picked = withContext(Dispatchers.IO) {
                    try {
                        val name = app.contentResolver
                            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else null }
                            ?: uri.lastPathSegment ?: "brush"
                        val bytes = app.contentResolver.openInputStream(uri)?.use {
                            readLimited(it, BrushLimits.MAX_FILE_BYTES)
                        }
                        PickedFile(name, bytes)
                    } catch (e: Exception) {
                        PickedFile("brush", null)
                    }
                }
                val name = picked.name
                val bytes = picked.bytes
                if (bytes == null) {
                    lastError = "Could not read that file (maximum 20 MB)."
                    continue
                }
                val outcome = wishy.brushLibrary.import(name, bytes)
                if (outcome.error != null) lastError = outcome.error
                added += outcome.added.size
                if (first == null) first = outcome.added.firstOrNull()
            }
            customBrushes = withContext(Dispatchers.IO) { wishy.brushLibrary.list() }
            first?.let { selectCustomBrush(it.id) }
            showMessage(
                if (added > 0) "Imported $added brush${if (added == 1) "" else "es"}"
                else lastError ?: "No brushes were imported"
            )
        }
    }

    fun deleteCustomBrush(id: String) {
        wishy.brushLibrary.delete(id)
        customBrushes = wishy.brushLibrary.list()
        if (activeCustomBrushId == id) {
            activeCustomBrushId = null
            if (tool == Tool.CUSTOM) tool = Tool.PEN
        }
    }

    private class PickedFile(val name: String, val bytes: ByteArray?)

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun updateColor(argb: Int) {
        color = argb
        persistBrush()
        activeLassoSelection?.let { if (it.isText) rerasterText(it) }
    }

    fun updateBrushSize(v: Float) {
        brushSize = v
    }

    fun updateOpacity(v: Float) {
        opacity = v
        activeLassoSelection?.let { if (it.isText) rerasterText(it) }
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

    /**
     * Eyedropper: takes the colour you actually see at (x, y) (all visible layers, with their
     * opacity and blend modes, over white paper), then returns to the tool used before it.
     */
    fun pickColorAt(x: Float, y: Float) {
        if (isPlaying) return
        val p = project ?: return
        val ix = x.toInt()
        val iy = y.toInt()
        if (ix !in 0 until p.width || iy !in 0 until p.height) return
        val px = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(px)
            c.drawColor(android.graphics.Color.WHITE)
            val paint = Paint()
            val src = Rect(ix, iy, ix + 1, iy + 1)
            val dst = Rect(0, 0, 1, 1)
            for (layer in currentLayers()) {
                if (!layer.visible || layer.opacity <= 0f || layer.bitmap.isRecycled) continue
                paint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                LayerBlending.apply(paint, layer.blendMode)
                c.drawBitmap(layer.bitmap, src, dst, paint)
            }
            updateColor(px.getPixel(0, 0) or 0xFF000000.toInt())
        } finally {
            px.recycle()
        }
        toolBeforeEyedropper?.let { tool = it }
        toolBeforeEyedropper = null
    }

    fun executeFill(x: Float, y: Float) {
        if (isPlaying) return
        val layer = activeLayer() ?: return
        if (!layer.visible) return
        if (layer.locked) { showMessage("This layer is locked"); return }
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

    /** Adds the picked image as a NEW layer on the current frame (it never overwrites existing art). */
    fun importImageAsLayer(uri: Uri) {
        val p = project ?: return
        viewModelScope.launch {
            val bmp = Importer.importImage(getApplication(), uri, p.width, p.height)
            if (bmp == null) {
                showMessage("Could not open that image")
                return@launch
            }
            val fd = currentFrameData()
            if (fd == null) {
                bmp.recycle()
                return@launch
            }
            commitLassoSelection()
            val entity = repo.addLayer(fd.frameId, fd.layers.size, "Image ${fd.layers.size + 1}")
            val layer = LayerData(entity.id, entity.name, true, 1f, bmp)
            layer.version++ // makes the pixels dirty so they are written on the next save
            fd.layers.add(layer)
            fd.metaVersion++
            activeLayerIndex = fd.layers.lastIndex
            refreshLayerUi()
            revision++
            scheduleSave()
            showMessage("Added image as a new layer")
        }
    }

    /** Drops an image onto the canvas as a floating selection: drag, scale and rotate it, tap outside to commit. */
    fun importImageForPlacement(uri: Uri) {
        val p = project ?: return
        viewModelScope.launch {
            val layer = activeLayer()
            if (layer == null || !layer.visible || layer.locked) {
                showMessage("Pick an unlocked, visible layer first")
                return@launch
            }
            val bmp = Importer.importImageTight(
                getApplication(), uri, (p.width * 0.7f).toInt(), (p.height * 0.7f).toInt()
            ) ?: run { showMessage("Could not open that image"); return@launch }
            commitLassoSelection()
            selectTool(Tool.LASSO)
            val cx = p.width / 2f
            val cy = p.height / 2f
            val bounds = RectF(cx - bmp.width / 2f, cy - bmp.height / 2f, cx + bmp.width / 2f, cy + bmp.height / 2f)
            activeLassoSelection = LassoSelection(bmp, bounds, Path())
            revision++
        }
    }

    fun importImageSequence(uris: List<Uri>) {
        val p = project ?: return
        viewModelScope.launch {
            var pos = frames.size // advanced per frame: `frames` itself is only refreshed after the loop
            var imported = 0
            for (uri in uris) {
                val bmp = Importer.importImage(getApplication(), uri, p.width, p.height) ?: continue
                val templates = listOf(LayerEntity(frameId = 0, position = 0, name = "Imported", visible = true, opacity = 1f))
                val (newFrame, _) = repo.insertFrame(projectId, pos, templates)
                pos++
                imported++
                val fd = ensureLoaded(newFrame.id)
                if (fd.layers.isNotEmpty()) {
                    BitmapOps.replace(fd.layers[0].bitmap, bmp)
                    fd.layers[0].version++
                }
                bmp.recycle()
            }
            frames = repo.frames(projectId)
            if (imported > 0 && frames.isNotEmpty()) {
                gotoFrame(frames.lastIndex)
            }
            repo.touch(projectId)
            showMessage(
                when {
                    imported == 0 -> "Could not open the selected images"
                    imported < uris.size -> "Imported $imported of ${uris.size} images"
                    else -> "Imported $imported frame${if (imported == 1) "" else "s"}"
                }
            )
        }
    }

    fun importVideoAsFrames(uri: Uri) {
        val p = project ?: return
        viewModelScope.launch {
            val fps = p.fps
            val extracted = Importer.importVideoFrames(getApplication(), uri, p.width, p.height, fps)
            if (extracted.isEmpty()) {
                showMessage("Could not read any frames from that video")
                return@launch
            }
            var pos = frames.size // see importImageSequence: keeps the frames in playback order
            for (bmp in extracted) {
                val templates = listOf(LayerEntity(frameId = 0, position = 0, name = "Video Frame", visible = true, opacity = 1f))
                val (newFrame, _) = repo.insertFrame(projectId, pos, templates)
                pos++
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
            showMessage(
                if (extracted.size >= Importer.MAX_VIDEO_FRAMES) "Imported the first ${extracted.size} frames (limit)"
                else "Imported ${extracted.size} frame${if (extracted.size == 1) "" else "s"}"
            )
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
                startFrame = timelineFrameOf(currentIndex),
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
                startFrame = timelineFrameOf(currentIndex),
                durationMs = duration
            )
            repo.addAudioTrack(entity)
            audioTracks = repo.getAudioTracks(projectId)
            revision++
        }
    }

    /** Live volume while dragging the slider (no database write). */
    fun setAudioVolumeLive(trackId: Long, volume: Float) = audioManager.setVolume(trackId, volume)

    /** Cuts the clip at the playhead into two clips, like Split in a video editor. */
    fun splitAudioTrackAtPlayhead(track: AudioTrackEntity) {
        val fps = (project?.fps ?: 12).coerceAtLeast(1)
        val here = timelineFrameOf(currentIndex)
        val offsetMs = (here - track.startFrame) * 1000L / fps
        if (offsetMs <= 100L || offsetMs >= track.durationMs - 100L) {
            showMessage("Move the playhead inside the clip to split it")
            return
        }
        viewModelScope.launch {
            repo.updateAudioTrack(track.copy(durationMs = offsetMs))
            repo.addAudioTrack(
                track.copy(
                    id = 0,
                    startFrame = here,
                    trimStartMs = track.trimStartMs + offsetMs,
                    durationMs = track.durationMs - offsetMs
                )
            )
            audioTracks = repo.getAudioTracks(projectId)
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
        audioManager.remove(id)
        viewModelScope.launch {
            repo.deleteAudioTrack(id)
            audioTracks = repo.getAudioTracks(projectId)
            revision++
        }
    }

    fun setAudioTrackStart(track: AudioTrackEntity, startFrame: Int) {
        updateAudioTrack(track.copy(startFrame = startFrame.coerceAtLeast(0)))
    }

    // ---------------------------------------------------------------- floating selection (lasso / text)

    private fun handleSlop() = selectionHandleSlop(project?.width ?: 1280)
    private fun rotateOffset() = selectionRotateOffset(project?.width ?: 1280)

    fun moveLassoSelection(dx: Float, dy: Float) {
        val sel = activeLassoSelection ?: return
        sel.translateX += dx
        sel.translateY += dy
        revision++
    }

    fun scaleLassoSelection(sx: Float, sy: Float) {
        val sel = activeLassoSelection ?: return
        val spec = sel.text
        if (spec != null) {
            // Text is re-rendered at the new size so it stays crisp instead of stretching.
            spec.sizePx = (spec.sizePx * sx).coerceIn(12f, 1500f)
            rerasterText(sel)
            return
        }
        sel.scaleX = (sel.scaleX * sx).coerceIn(0.1f, 10f)
        sel.scaleY = (sel.scaleY * sy).coerceIn(0.1f, 10f)
        revision++
    }

    fun rotateLassoSelection(degrees: Float) {
        val sel = activeLassoSelection ?: return
        sel.rotation += degrees
        revision++
    }

    /** Stamps the floating selection into the active layer as one undoable step. */
    fun commitLassoSelection() {
        val sel = activeLassoSelection ?: return
        activeLassoSelection = null
        gesture = Gesture.NONE
        val layer = activeLayer()
        val frameId = frames.getOrNull(currentIndex)?.id
        if (layer == null || frameId == null) {
            sel.release()
            revision++
            return
        }

        val matrix = sel.getMatrix()
        val dest = RectF(0f, 0f, sel.pixels.width.toFloat(), sel.pixels.height.toFloat())
        matrix.mapRect(dest)
        val full = Rect(0, 0, layer.bitmap.width, layer.bitmap.height)
        val drawRect = Rect(
            floor(dest.left).toInt() - 2, floor(dest.top).toInt() - 2,
            ceil(dest.right).toInt() + 2, ceil(dest.bottom).toInt() + 2
        )
        val visible = drawRect.intersect(full)
        val lift = sel.liftRect
        val patchRect: Rect? = when {
            visible && lift != null -> drawRect.also { it.union(lift) }
            visible -> drawRect
            lift != null -> Rect(lift)
            else -> null
        }

        if (patchRect != null) {
            // Patch = the layer as it was *before* the lift, over everything this commit touches.
            val patch = BitmapOps.copyRect(layer.bitmap, patchRect)
            val before = sel.liftBefore
            if (before != null && lift != null) {
                BitmapOps.putAt(patch, before, lift.left - patchRect.left, lift.top - patchRect.top)
            }
            if (visible) {
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                Canvas(layer.bitmap).drawBitmap(sel.pixels, matrix, paint)
            }
            undoManager.push(UndoEntry(frameId, layer.id, patch, patchRect))
            layer.version++
            syncUndoFlags()
            scheduleSave()
        }
        sel.release()
        revision++
    }

    /** Puts a lassoed region back exactly where it came from; discards a text object. */
    fun cancelLassoSelection() {
        val sel = activeLassoSelection ?: return
        activeLassoSelection = null
        gesture = Gesture.NONE
        val layer = activeLayer()
        val before = sel.liftBefore
        val lift = sel.liftRect
        if (layer != null && before != null && lift != null) {
            BitmapOps.putAt(layer.bitmap, before, lift.left, lift.top)
        }
        sel.release()
        revision++
    }

    /** Deletes the floating selection (the lifted pixels stay erased). Undoable. */
    fun deleteLassoSelection() {
        val sel = activeLassoSelection ?: return
        activeLassoSelection = null
        gesture = Gesture.NONE
        val layer = activeLayer()
        val frameId = frames.getOrNull(currentIndex)?.id
        val before = sel.liftBefore
        val lift = sel.liftRect
        if (layer != null && frameId != null && before != null && lift != null) {
            undoManager.push(UndoEntry(frameId, layer.id, before, Rect(lift)))
            sel.liftBefore = null // ownership moved to the undo stack
            layer.version++
            syncUndoFlags()
            scheduleSave()
        }
        sel.release()
        revision++
    }

    /** Preview of the lasso loop while it is being drawn. */
    fun lassoPreviewPath(): Path? = if (gesture == Gesture.SELECTING) lassoTool.currentPath else null

    /** Scratch overlay of the stroke in progress, drawn above its layer. */
    fun activeStrokeOverlay(): LiveStroke? {
        val layer = strokeLayer ?: return null
        val overlay = renderer.overlay() ?: return null
        return LiveStroke(layer.id, overlay)
    }

    // ---- text

    /** Key of the font used for new text (see [FontOption.key]). */
    var activeFontName by mutableStateOf(FontLibrary.DEFAULT_KEY)
    var availableFonts by mutableStateOf(FontLibrary.BUILT_IN)
        private set

    private val fontLibrary get() = wishy.fonts

    init {
        refreshFonts()
    }

    private fun refreshFonts() {
        viewModelScope.launch {
            availableFonts = withContext(Dispatchers.IO) { fontLibrary.list() }
            if (availableFonts.none { it.key == activeFontName }) activeFontName = FontLibrary.DEFAULT_KEY
        }
    }

    /** Imports one or more font files picked by the user and selects the first new one. */
    fun importFonts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            var added = 0
            var first: FontOption? = null
            var lastError: String? = null
            for (uri in uris) {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val name = app.contentResolver
                            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else null }
                            ?: uri.lastPathSegment
                        val bytes = app.contentResolver.openInputStream(uri)?.use {
                            readLimited(it, FontLibrary.MAX_FONT_BYTES)
                        }
                        if (bytes == null) {
                            org.wishyclip.app.data.FontImportResult(null, "Could not read that file (maximum 25 MB).")
                        } else {
                            fontLibrary.import(name, bytes)
                        }
                    } catch (e: Exception) {
                        org.wishyclip.app.data.FontImportResult(null, "Could not read that file.")
                    }
                }
                val option = result.option
                if (option != null) {
                    if (!result.alreadyImported) added++
                    if (first == null) first = option
                } else if (result.error != null) {
                    lastError = result.error
                }
            }
            availableFonts = withContext(Dispatchers.IO) { fontLibrary.list() }
            val picked = first
            if (picked != null) {
                activeFontName = picked.key
                showMessage(
                    if (added > 0) "Imported font: ${picked.label}" else "${picked.label} is already imported"
                )
            } else {
                showMessage(lastError ?: "No fonts were imported")
            }
        }
    }

    fun deleteFont(key: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { fontLibrary.delete(key) }
            availableFonts = withContext(Dispatchers.IO) { fontLibrary.list() }
            if (activeFontName == key) activeFontName = FontLibrary.DEFAULT_KEY
        }
    }

    /** Typeface for the font picker's live previews; null means the default font. */
    fun fontTypeface(key: String): Typeface? = fontLibrary.typeface(key)

    private fun getTypeface(fontName: String): Typeface? = fontLibrary.typeface(fontName)

    private fun defaultTextSize(): Float = ((project?.height ?: 720) / 8f).coerceAtLeast(40f)

    private fun openTextEditorAt(x: Float, y: Float) {
        textPendingX = x
        textPendingY = y
        textEditingExisting = false
        textEditorInitial = ""
        textEditorOpen = true
    }

    /** Opens the editor for the text object that is currently selected. */
    fun editActiveText() {
        val spec = activeLassoSelection?.text ?: return
        textEditingExisting = true
        textEditorInitial = spec.text
        activeFontName = spec.fontName
        textEditorOpen = true
    }

    fun dismissTextEditor() {
        textEditorOpen = false
    }

    fun confirmText(text: String) {
        textEditorOpen = false
        if (text.isBlank()) return
        if (textEditingExisting) {
            val sel = activeLassoSelection ?: return
            val spec = sel.text ?: return
            spec.text = text
            spec.fontName = activeFontName
            rerasterText(sel)
            return
        }
        val layer = activeLayer() ?: return
        if (!layer.visible) return
        commitLassoSelection()
        val spec = TextSpec(text, defaultTextSize(), activeFontName)
        val bmp = TextRaster.render(spec.text, color, spec.sizePx, opacity, getTypeface(spec.fontName))
        val bounds = RectF(
            textPendingX - bmp.width / 2f, textPendingY - bmp.height / 2f,
            textPendingX + bmp.width / 2f, textPendingY + bmp.height / 2f
        )
        activeLassoSelection = LassoSelection(bmp, bounds, Path(), text = spec)
        revision++
    }

    /** Re-renders a text selection (new text, size, color) keeping its centre and rotation. */
    private fun rerasterText(sel: LassoSelection) {
        val spec = sel.text ?: return
        val cx = sel.bounds.centerX() + sel.translateX
        val cy = sel.bounds.centerY() + sel.translateY
        val fresh = TextRaster.render(spec.text, color, spec.sizePx, opacity, getTypeface(spec.fontName))
        // The old bitmap is left to the GC: it may still be referenced by a frame being drawn.
        sel.pixels = fresh
        sel.bounds = RectF(cx - fresh.width / 2f, cy - fresh.height / 2f, cx + fresh.width / 2f, cy + fresh.height / 2f)
        sel.translateX = 0f
        sel.translateY = 0f
        sel.scaleX = 1f
        sel.scaleY = 1f
        revision++
    }

    fun updateStabilizer(value: Float) {
        stabilizer = value.coerceIn(0f, 1f)
    }

    fun copyFrame() {
        val fd = currentFrameData() ?: return
        val copiedLayers = fd.layers.map { l ->
            LayerData(l.id, l.name, l.visible, l.opacity, l.bitmap.snapshot(), l.locked, l.blendMode)
        }
        copiedFrameData = FrameData(fd.frameId, copiedLayers.toMutableList())
        revision++
    }

    fun pasteFrame() {
        val copy = copiedFrameData ?: return
        viewModelScope.launch {
            val templates = copy.layers.mapIndexed { i, l ->
                LayerEntity(
                    frameId = 0, position = i, name = l.name, visible = l.visible, opacity = l.opacity,
                    locked = l.locked, blendMode = l.blendMode.name
                )
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
        if (isPlaying || strokeLayer != null || gesture != Gesture.NONE) return
        smoothedX = x
        smoothedY = y

        val sel = activeLassoSelection
        if (sel != null) {
            val hit = sel.hitTest(x, y, handleSlop(), rotateOffset())
            if (hit != SelectionHit.NONE) {
                gesture = when (hit) {
                    SelectionHit.SCALE -> Gesture.SCALE
                    SelectionHit.ROTATE -> Gesture.ROTATE
                    else -> Gesture.MOVE
                }
                gestureLastX = x
                gestureLastY = y
                gestureMoved = 0f
                return
            }
            // Touching empty canvas drops the selection in place, then the touch continues normally.
            commitLassoSelection()
        }

        if (tool != Tool.EYEDROPPER && activeLayer()?.locked == true) {
            showMessage("This layer is locked")
            return
        }

        if (tool == Tool.TEXT) {
            gesture = Gesture.TEXT_TAP
            gestureStartX = x
            gestureStartY = y
            gestureMoved = 0f
            return
        }
        if (tool == Tool.FILL) {
            executeFill(x, y)
            return
        }
        if (tool == Tool.EYEDROPPER) {
            pickColorAt(x, y)
            return
        }
        val layer = activeLayer() ?: return
        if (!layer.visible) return
        val frameId = frames.getOrNull(currentIndex)?.id ?: return
        strokeLayer = layer
        strokeFrameId = frameId
        if (tool == Tool.LASSO) {
            gesture = Gesture.SELECTING
            lassoTool.begin(x, y)
            revision++
            return
        }
        val dab = if (tool == Tool.CUSTOM) {
            customBrushes.firstOrNull { it.id == activeCustomBrushId }?.let { wishy.brushLibrary.dab(it) }
        } else null
        if (tool == Tool.CUSTOM && dab == null) {
            showMessage("This brush's tip is missing - delete it and import it again")
        }
        renderer.begin(layer.bitmap, tool, color, brushSize, opacity, x, y, pressure, tilt, mirrorMode, dab)
        revision++
    }

    fun strokeMove(x: Float, y: Float, pressure: Float = 1f, tilt: Float = 0f) {
        when (gesture) {
            Gesture.MOVE -> {
                moveLassoSelection(x - gestureLastX, y - gestureLastY)
                gestureMoved += hypot(x - gestureLastX, y - gestureLastY)
                gestureLastX = x
                gestureLastY = y
                return
            }
            Gesture.SCALE -> {
                val sel = activeLassoSelection ?: return
                val (cx, cy) = sel.center(rotateOffset())
                val d0 = hypot(gestureLastX - cx, gestureLastY - cy)
                val d1 = hypot(x - cx, y - cy)
                if (d0 > 1f && d1 > 1f) scaleLassoSelection(d1 / d0, d1 / d0)
                gestureLastX = x
                gestureLastY = y
                return
            }
            Gesture.ROTATE -> {
                val sel = activeLassoSelection ?: return
                val (cx, cy) = sel.center(rotateOffset())
                val a0 = atan2(gestureLastY - cy, gestureLastX - cx)
                val a1 = atan2(y - cy, x - cx)
                var delta = Math.toDegrees((a1 - a0).toDouble()).toFloat()
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                rotateLassoSelection(delta)
                gestureLastX = x
                gestureLastY = y
                return
            }
            Gesture.TEXT_TAP -> {
                gestureMoved = hypot(x - gestureStartX, y - gestureStartY)
                return
            }
            else -> {}
        }
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
        if (gesture == Gesture.SELECTING) {
            lassoTool.addPoint(curX, curY)
        } else {
            renderer.moveTo(curX, curY, pressure, tilt)
        }
        revision++
    }

    fun strokeEnd() {
        when (gesture) {
            Gesture.MOVE -> {
                gesture = Gesture.NONE
                // A tap (no drag) on a text object opens it for editing.
                if (gestureMoved < TAP_SLOP && activeLassoSelection?.isText == true) editActiveText()
                return
            }
            Gesture.SCALE, Gesture.ROTATE -> {
                gesture = Gesture.NONE
                return
            }
            Gesture.TEXT_TAP -> {
                gesture = Gesture.NONE
                if (gestureMoved < TAP_SLOP) openTextEditorAt(gestureStartX, gestureStartY)
                return
            }
            else -> {}
        }
        val layer = strokeLayer ?: return
        strokeLayer = null

        if (gesture == Gesture.SELECTING) {
            gesture = Gesture.NONE
            // Lifting does not touch version/undo/save: that all happens when it is committed.
            activeLassoSelection = lassoTool.end(layer.bitmap)
            revision++
            return
        }

        val patch = renderer.finish()
        if (patch != null) {
            undoManager.push(UndoEntry(strokeFrameId, layer.id, patch.bitmap, patch.rect))
            layer.version++
            syncUndoFlags()
            scheduleSave()
        }
        revision++
    }

    /** The touch turned into a pinch/pan: throw the half-drawn stroke away instead of committing it. */
    fun strokeCancel() {
        val wasTransform = gesture == Gesture.MOVE || gesture == Gesture.SCALE ||
            gesture == Gesture.ROTATE || gesture == Gesture.TEXT_TAP
        if (wasTransform) {
            gesture = Gesture.NONE
            return
        }
        if (strokeLayer == null) return
        strokeLayer = null
        gesture = Gesture.NONE
        renderer.cancel()
        revision++
    }

    // ---------------------------------------------------------------- undo / redo

    fun undo() {
        if (strokeLayer != null) return
        if (activeLassoSelection != null) {
            cancelLassoSelection()
            return
        }
        applyHistory(true)
    }

    fun redo() {
        if (strokeLayer != null) return
        if (activeLassoSelection != null) {
            cancelLassoSelection()
        }
        applyHistory(false)
    }

    private fun applyHistory(isUndo: Boolean) {
        val entry = (if (isUndo) undoManager.popUndo() else undoManager.popRedo()) ?: return
        syncUndoFlags()
        viewModelScope.launch {
            val frameData = ensureLoaded(entry.frameId)
            val layer = frameData.layers.firstOrNull { it.id == entry.layerId }
            if (layer == null) {
                entry.bitmap.recycle()
                syncUndoFlags()
                return@launch
            }
            val rect = entry.rect
            val counterpart = if (rect != null) {
                UndoEntry(entry.frameId, entry.layerId, BitmapOps.copyRect(layer.bitmap, rect), rect)
            } else {
                UndoEntry(entry.frameId, entry.layerId, layer.bitmap.snapshot())
            }
            if (isUndo) undoManager.pushRedo(counterpart) else undoManager.pushUndoKeepRedo(counterpart)
            if (rect != null) BitmapOps.putAt(layer.bitmap, entry.bitmap, rect.left, rect.top)
            else BitmapOps.replace(layer.bitmap, entry.bitmap)
            entry.bitmap.recycle()
            layer.version++

            val idx = frames.indexOfFirst { it.id == entry.frameId }
            if (idx >= 0 && idx != currentIndex) {
                currentIndex = idx
                refreshLayerUi()
            }

            syncUndoFlags()
            revision++
            scheduleSave()
        }
    }

    private fun syncUndoFlags() {
        canUndo = undoManager.canUndo
        canRedo = undoManager.canRedo
    }

    // ---------------------------------------------------------------- layers (current frame)

    private fun refreshLayerUi() {
        layerUi = currentFrameData()?.layers?.map {
            LayerUi(it.id, it.name, it.visible, it.opacity, it.locked, it.blendMode)
        } ?: emptyList()
    }

    fun selectLayer(index: Int) {
        if (index != activeLayerIndex) commitLassoSelection()
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

    fun duplicateLayer(index: Int) {
        val fd = currentFrameData() ?: return
        val source = fd.layers.getOrNull(index) ?: return
        viewModelScope.launch {
            val entity = repo.addLayer(fd.frameId, fd.layers.size, "${source.name} (Copy)")
            val bmp = source.bitmap.snapshot()
            val newLayer = LayerData(entity.id, entity.name, source.visible, source.opacity, bmp, source.locked, source.blendMode)
            fd.layers.add(index + 1, newLayer)
            fd.metaVersion++
            activeLayerIndex = index + 1
            refreshLayerUi()
            revision++
            scheduleSave()
        }
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

    fun setLayerLocked(index: Int, locked: Boolean) {
        val fd = currentFrameData() ?: return
        val layer = fd.layers.getOrNull(index) ?: return
        layer.locked = locked
        fd.metaVersion++
        refreshLayerUi()
        revision++
        scheduleSave()
    }

    fun setLayerBlend(index: Int, blend: LayerBlend) {
        val fd = currentFrameData() ?: return
        val layer = fd.layers.getOrNull(index) ?: return
        layer.blendMode = blend
        fd.metaVersion++
        refreshLayerUi()
        revision++
        scheduleSave()
    }

    /** Flattens the layer at [index] into the one below it (cannot be undone). */
    fun mergeLayerDown(index: Int) {
        val fd = currentFrameData() ?: return
        if (strokeLayer != null || index !in 1..fd.layers.lastIndex) return
        commitLassoSelection()
        val upper = fd.layers[index]
        val lower = fd.layers[index - 1]
        if (lower.locked) {
            showMessage("The layer below is locked")
            return
        }
        if (upper.blendMode != LayerBlend.NORMAL) {
            showMessage("Set the blend mode to Normal to merge")
            return
        }
        if (upper.visible && upper.opacity > 0f) {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            paint.alpha = (upper.opacity * 255f).toInt().coerceIn(0, 255)
            Canvas(lower.bitmap).drawBitmap(upper.bitmap, 0f, 0f, paint)
            lower.version++
        }
        fd.layers.removeAt(index)
        undoManager.dropLayer(upper.id)
        undoManager.dropLayer(lower.id)
        syncUndoFlags()
        if (!upper.bitmap.isRecycled) upper.bitmap.recycle()
        fd.metaVersion++
        activeLayerIndex = (if (activeLayerIndex >= index) activeLayerIndex - 1 else activeLayerIndex)
            .coerceIn(0, fd.layers.lastIndex)
        refreshLayerUi()
        revision++
        viewModelScope.launch { repo.deleteLayer(projectId, upper.id) }
        scheduleSave()
        showMessage("Layers merged")
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
            LayerEntity(
                id = l.id, frameId = fd.frameId, position = i, name = l.name, visible = l.visible,
                opacity = l.opacity, locked = l.locked, blendMode = l.blendMode.name
            )
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
            LayerBlending.apply(paint, l.blendMode)
            canvas.drawBitmap(l.bitmap, null, dst, paint)
        }
        return bmp
    }

    /** Called when the host activity stops: pause playback and flush to disk. */
    fun onHostStop() {
        commitLassoSelection()
        stopPlay()
        viewModelScope.launch { saveAll() }
    }

    fun saveAndExit(onDone: () -> Unit) {
        commitLassoSelection()
        stopPlay()
        viewModelScope.launch {
            saveAll()
            onDone()
        }
    }

    override fun onCleared() {
        commitLassoSelection()
        renderer.release()
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

private const val TAP_SLOP = 12f

class EditorViewModelFactory(
    private val app: Application,
    private val projectId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(app, projectId) as T
}
