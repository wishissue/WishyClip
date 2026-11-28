package org.wishyclip.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
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
import kotlinx.coroutines.CancellationException
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
import org.wishyclip.app.data.BusyTracker
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
    /** Bumped whenever a frame preview file was (re)written, so the timeline reloads the images. */
    var frameThumbVersion by mutableIntStateOf(0)
        private set

    var revision by mutableIntStateOf(0)
        private set

    // ---- internals (main thread only, except where noted) ----
    private val cache = HashMap<Long, FrameData>() // frame id -> data; only current frame +-2
    private var undoManager = UndoManager(40, 64L * 1024 * 1024)
    private val renderer = StrokeRenderer()
    private val lassoTool = StandardLassoTool()
    private val audioManager = org.wishyclip.app.audio.MultiTrackAudioPlayer(app)
    private val saveMutex = Mutex()
    /** Serialises the "mirror this layer change to the other frames" jobs so they run in order. */
    private val layerMirrorMutex = Mutex()
    private val pendingMetaSources = LinkedHashSet<Long>()
    private var metaTicket = 0
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var saveTicket = 0
    private var playJob: Job? = null
    private var playPrefetchJob: Job? = null
    private var prefetchJob: Job? = null
    private enum class Gesture { NONE, SELECTING, MOVE, SCALE, ROTATE, TEXT_TAP, EYEDROP }
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

    private val safeHandler = CoroutineExceptionHandler { _, e ->
        if (e !is CancellationException) {
            showMessage(
                if (e is OutOfMemoryError) "Not enough memory for that action"
                else "That action failed. Your drawing is still open and will keep being saved."
            )
        }
    }

    init {
        launchSafe {
            try {
                bootstrap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Opening failed (corrupt database row, out of memory ...). Show the error screen instead of crashing.
                loaded = false
                loadError = true
            }
        }
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
        // First run of a new pack version: install the starter brushes once (users can delete them).
        if (settings.starterBrushVersion.first() < org.wishyclip.app.brush.StarterBrushes.VERSION) {
            withContext(Dispatchers.IO) { wishy.brushLibrary.installStarterPack() }
            settings.saveStarterBrushVersion(org.wishyclip.app.brush.StarterBrushes.VERSION)
        }
        customBrushes = try {
            withContext(Dispatchers.IO) { wishy.brushLibrary.list() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList() // a broken brush library must not stop the project from opening
        }
        // Remove half-written files left by a crash during an earlier save (the real files are untouched).
        withContext(Dispatchers.IO) { try { store.sweepTempFiles(projectId) } catch (_: Exception) {} }
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
        LayerData(it.id, it.name, it.visible, it.opacity, it.peekBitmap(), it.locked, it.blendMode).apply {
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
                        // No file = blank layer: keep it empty (no bitmap) until it is painted.
                        if (store.hasLayer(projectId, it.id)) store.load(projectId, it.id, p.width, p.height) else null,
                        it.locked, LayerBlend.from(it.blendMode),
                        allocator = { Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888) }
                    )
                }
                FrameData(frameId, layers.toMutableList())
            }
        }
        val existing = cache[frameId]
        if (existing != null) {
            data.release() // lost a race with another loader: free the duplicate pixels
            return existing
        }
        cache[frameId] = data
        return data
    }

    /** Pixel memory the frame cache may hold: 40% of the app heap limit (the rest is undo, UI, exports). */
    private fun cacheBudgetBytes(): Long = (Runtime.getRuntime().maxMemory() * 0.4).toLong()

    private fun trimCache() {
        val maxRange = maxOf(2, onionSkinSettings.framesBefore, onionSkinSettings.framesAfter)
        val keep = HashSet<Long>()
        for (i in (currentIndex - maxRange)..(currentIndex + maxRange)) {
            frames.getOrNull(i)?.let { keep.add(it.id) }
        }
        // While playing, also keep the frames about to be shown (wrapping around at the end).
        if (isPlaying && frames.isNotEmpty()) {
            for (d in 1..PLAY_AHEAD) frames.getOrNull((currentIndex + d) % frames.size)?.let { keep.add(it.id) }
        }
        // Memory budget: if the kept frames hold too many pixels, drop the farthest ones first,
        // but always keep the current frame and its direct neighbours (onion skin, quick flipping).
        var used = cache.values.filter { it.frameId in keep }.sumOf { it.allocatedBytes() }
        if (used > cacheBudgetBytes()) {
            val byDistance = cache.values
                .filter { it.frameId in keep }
                .sortedByDescending { Math.abs(frames.indexOfFirst { f -> f.id == it.frameId } - currentIndex) }
            for (fd in byDistance) {
                if (used <= cacheBudgetBytes()) break
                val distance = Math.abs(frames.indexOfFirst { f -> f.id == fd.frameId } - currentIndex)
                if (distance <= 1) continue
                keep.remove(fd.frameId)
                used -= fd.allocatedBytes()
            }
        }
        val evicted = cache.values.filter { it.frameId !in keep }
        if (evicted.isEmpty()) return
        for (fd in evicted) {
            cache.remove(fd.frameId)
        }
        syncUndoFlags()
        launchSafe {
            for (fd in evicted) {
                try {
                    persistFrame(fd)
                    fd.release()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Saving failed: put the frame back in the cache instead of freeing unsaved pixels.
                    if (!cache.containsKey(fd.frameId)) cache[fd.frameId] = fd
                    lastSaveFailed = true
                    retrySaveLater()
                }
            }
        }
    }

    private fun prefetch(center: Int) {
        prefetchJob?.cancel()
        prefetchJob = launchSafe {
            val maxRange = maxOf(2, onionSkinSettings.framesBefore, onionSkinSettings.framesAfter)
            for (d in 1..maxRange) {
                // Beyond the direct neighbours, stop loading when the pixel budget is already used up.
                if (d > 1 && cache.values.sumOf { it.allocatedBytes() } > cacheBudgetBytes()) break
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
        launchSafe { gotoFrame(index) }
    }

    private suspend fun gotoFrame(index: Int) {
        val target = frames.getOrNull(index) ?: return
        val fd = ensureLoaded(target.id)
        currentIndex = index
        activeLayerIndex = activeLayerIndex.coerceIn(0, (fd.layers.size - 1).coerceAtLeast(0))
        refreshLayerUi()
        revision++
        trimCache()
        if (!isPlaying) prefetch(index) // playback has its own, uncancelled look-ahead loader
    }

    fun addFrame() {
        if (strokeLayer != null) return
        stopPlay()
        launchSafe {
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
        launchSafe {
            val source = frames.getOrNull(currentIndex) ?: return@launchSafe
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
        launchSafe {
            if (frames.size <= 1) {
                clearFrameInternal()
                return@launchSafe
            }
            val frame = frames.getOrNull(currentIndex) ?: return@launchSafe
            val evictedFd = cache.remove(frame.id)
            undoManager.dropFrame(frame.id)
            syncUndoFlags()
            evictedFd?.release()
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
        launchSafe {
            repo.moveFrame(projectId, from, to)
            frames = repo.frames(projectId)
            currentIndex = to
            revision++
            trimCache()
            prefetch(to)
        }
    }

    /** Moves the frame at [from] to position [to] (drag-reorder in the timeline) and selects it there. */
    fun moveFrameTo(from: Int, to: Int) {
        if (strokeLayer != null) return
        if (from !in frames.indices || to !in frames.indices || from == to) return
        stopPlay()
        launchSafe {
            repo.moveFrame(projectId, from, to)
            frames = repo.frames(projectId)
            currentIndex = to
            revision++
            trimCache()
            prefetch(to)
        }
    }

    /**
     * For every frame id, the layer indices (bottom = 0) that have saved pixels. The Studio timeline
     * uses it to draw one track per layer, filled where that layer has a drawing.
     */
    suspend fun layerContentMap(): Map<Long, Set<Int>> {
        val snapshot = frames
        val pid = projectId
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            snapshot.associate { f ->
                f.id to repo.layers(f.id)
                    .mapIndexedNotNull { i, l -> if (store.hasLayer(pid, l.id)) i else null }
                    .toSet()
            }
        }
    }

    fun updateFrameExposure(index: Int, newDuration: Int) {
        val clamped = newDuration.coerceIn(1, 120)
        val frame = frames.getOrNull(index) ?: return
        launchSafe {
            repo.updateFrameExposure(frame.id, clamped)
            frames = repo.frames(projectId)
        }
    }

    private fun clearFrameInternal() {
        val fd = currentFrameData() ?: return
        val frameId = fd.frameId
        for (layer in fd.layers) {
            if (!layer.allocated) continue // already blank, nothing to clear or undo
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
        // Look-ahead loader: runs for the whole playback and is NOT cancelled on every tick (the normal
        // prefetch is, which at 60 fps meant frames never finished loading and the tick had to wait).
        playPrefetchJob = launchSafe {
            while (isActive && isPlaying) {
                val size = frames.size
                if (size > 0) {
                    for (d in 1..PLAY_AHEAD) {
                        if (!isPlaying) break
                        // Past the next frame, stop when the pixel budget is already used up.
                        if (d > 1 && cache.values.sumOf { it.allocatedBytes() } > cacheBudgetBytes()) break
                        frames.getOrNull((currentIndex + d) % size)?.let { ensureLoaded(it.id) }
                    }
                }
                delay(6)
            }
        }
        playJob = launchSafe {
            val fps = (project?.fps ?: 12).coerceAtLeast(1)
            // Absolute schedule (see PlaybackClock). The old code waited 1000 / fps whole milliseconds
            // (24 fps -> 41 ms, 60 fps -> 16 ms), which played up to 4% too fast and drifted.
            val clock = org.wishyclip.app.canvas.PlaybackClock(fps, SystemClock.elapsedRealtimeNanos())
            var holdTicks = 0
            while (isActive && isPlaying) {
                val currentFrame = frames.getOrNull(currentIndex)
                val requiredHold = currentFrame?.exposureDuration ?: 1
                holdTicks++
                if (holdTicks >= requiredHold) {
                    holdTicks = 0
                    val next = (currentIndex + 1) % frames.size
                    gotoFrame(next)
                    audioManager.sync(audioTracks, timelineFrameOf(next), fps)
                }
                delay(clock.delayAfterTick(SystemClock.elapsedRealtimeNanos()))
            }
        }
    }

    private fun stopPlay() {
        isPlaying = false
        audioManager.pauseAll()
        playJob?.cancel()
        playJob = null
        playPrefetchJob?.cancel()
        playPrefetchJob = null
    }

    // ---------------------------------------------------------------- tools

    fun toggleOnionSkin() {
        val next = onionSkinSettings.copy(enabled = !onionSkinSettings.enabled)
        updateOnionSkinSettings(next)
    }

    fun updateOnionSkinSettings(s: OnionSkinSettings) {
        onionSkinSettings = s
        launchSafe { settings.saveOnionSettings(s) }
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
        launchSafe(Dispatchers.IO) {
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
        launchSafe {
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
        launchSafe {
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
                // Parsing untrusted archives is CPU/memory heavy: keep it off the main thread, and treat any
                // failure (including OutOfMemoryError / StackOverflowError) as "this file is unusable".
                val outcome = try {
                    withContext(Dispatchers.IO) { wishy.brushLibrary.import(name, bytes) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    lastError = "Could not import \"$name\": the file is damaged or too complex."
                    continue
                }
                if (outcome.error != null) lastError = outcome.error
                added += outcome.added.size
                if (first == null) first = outcome.added.firstOrNull()
            }
            customBrushes = try {
                withContext(Dispatchers.IO) { wishy.brushLibrary.list() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                customBrushes
            }
            first?.let { selectCustomBrush(it.id) }
            showMessage(
                if (added > 0) "Imported $added brush${if (added == 1) "" else "es"}"
                else lastError ?: "No brushes were imported"
            )
        }
    }

    fun deleteCustomBrush(id: String) {
        launchSafe {
            withContext(Dispatchers.IO) { wishy.brushLibrary.delete(id) }
            customBrushes = withContext(Dispatchers.IO) { wishy.brushLibrary.list() }
            if (activeCustomBrushId == id) {
                activeCustomBrushId = null
                if (tool == Tool.CUSTOM) tool = Tool.PEN
            }
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
        launchSafe { settings.saveBrush(c, s, o) }
    }

    // ---------------------------------------------------------------- drawing

    fun updateFillTolerance(tolerance: Int) {
        fillTolerance = tolerance.coerceIn(0, 255)
    }

    /**
     * Eyedropper: takes the colour you actually see at (x, y) (all visible layers, with their
     * opacity and blend modes, over white paper), then returns to the tool used before it.
     */
    /** One-shot pick: sample the pixel and finish at once (kept for callers that only tap). */
    fun pickColorAt(x: Float, y: Float) {
        sampleEyedropper(x, y)
        finishEyedropper()
    }

    /**
     * Eyedropper sampling while the finger is down: the brush color follows the pixel under the finger,
     * so dragging across the canvas shows each color as it passes. Nothing is saved or switched yet;
     * [finishEyedropper] settles on the last sampled pixel when the finger lifts. A point outside the
     * canvas is ignored, so lifting there keeps the last pixel that was on the canvas.
     */
    private fun sampleEyedropper(x: Float, y: Float) {
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
                if (!layer.visible || layer.opacity <= 0f || !layer.allocated) continue
                paint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                LayerBlending.apply(paint, layer.blendMode)
                c.drawBitmap(layer.bitmap, src, dst, paint)
            }
            color = px.getPixel(0, 0) or 0xFF000000.toInt()
        } finally {
            px.recycle()
        }
    }

    /** Ends an eyedropper touch: saves the last sampled color and returns to the previous tool. */
    private fun finishEyedropper() {
        updateColor(color)
        toolBeforeEyedropper?.let { tool = it }
        toolBeforeEyedropper = null
    }

    /**
     * True while [layer] is still part of the loaded frame [frameId]. After eviction or deletion a layer's
     * bitmap getter would quietly create a new blank bitmap, so "is the bitmap recycled" no longer tells.
     */
    private fun isLive(frameId: Long, layer: LayerData): Boolean =
        cache[frameId]?.layers?.any { it === layer } == true

    fun executeFill(x: Float, y: Float) {
        if (isPlaying) return
        val layer = activeLayer() ?: return
        if (!layer.visible) return
        if (layer.locked) { showMessage("This layer is locked"); return }
        val frameId = frames.getOrNull(currentIndex)?.id ?: return

        // Everything that touches the layer, the undo stack or UI state must stay on the main thread
        // (a fill on a background thread raced with strokes, undo and layer deletion, and could crash on
        // a recycled bitmap). Only the flood computation runs in the background, on a private copy.
        val fillColor = color
        val tolerance = fillTolerance
        val startVersion = layer.version
        val fx = x.toInt()
        val fy = y.toInt()
        launchSafe {
            val before: Bitmap
            val work: Bitmap?
            try {
                before = layer.bitmap.snapshot()
                work = layer.bitmap.copy(Bitmap.Config.ARGB_8888, true)
            } catch (e: OutOfMemoryError) {
                showMessage("Not enough memory to fill this layer")
                return@launchSafe
            }
            if (work == null) {
                before.recycle()
                return@launchSafe
            }
            val changed = try {
                withContext(Dispatchers.Default) {
                    ScanlineFillTool().fill(work, fx, fy, fillColor, tolerance)
                }
            } catch (e: CancellationException) {
                before.recycle(); work.recycle()
                throw e
            } catch (e: Throwable) {
                // Typically OutOfMemoryError from the fill's working arrays: free the copies and report.
                before.recycle(); work.recycle()
                showMessage(if (e is OutOfMemoryError) "Not enough memory to fill this layer" else "Fill failed")
                return@launchSafe
            }
            // The layer may have been edited, deleted or evicted while the fill was computed.
            if (!changed || !isLive(frameId, layer) || layer.version != startVersion) {
                before.recycle()
                work.recycle()
                return@launchSafe
            }
            BitmapOps.replace(layer.bitmap, work)
            work.recycle()
            undoManager.push(UndoEntry(frameId, layer.id, before))
            layer.version++
            syncUndoFlags()
            revision++
            scheduleSave()
        }
    }

    /** Adds the picked image as a NEW layer on the current frame (it never overwrites existing art). */
    fun importImageAsLayer(uri: Uri) {
        val p = project ?: return
        launchSafe {
            BusyTracker.show("import-layer", "Importing image", "Decoding…")
            val bmp = try {
                Importer.importImage(getApplication(), uri, p.width, p.height)
            } finally {
                BusyTracker.hide("import-layer")
            }
            if (bmp == null) {
                showMessage("Could not open that image")
                return@launchSafe
            }
            val fd = currentFrameData()
            if (fd == null) {
                bmp.recycle()
                return@launchSafe
            }
            commitLassoSelection()
            val countBefore = fd.layers.size
            val entity = repo.addLayer(fd.frameId, fd.layers.size, "Image ${fd.layers.size + 1}")
            val layer = LayerData(entity.id, entity.name, true, 1f, bmp)
            layer.version++ // makes the pixels dirty so they are written on the next save
            fd.layers.add(layer)
            fd.metaVersion++
            activeLayerIndex = fd.layers.lastIndex
            refreshLayerUi()
            revision++
            scheduleSave()
            // The picture lives on this frame; other frames get an empty layer so the stack stays shared.
            mirrorLayerOp { mirrorAdd(fd.frameId, countBefore, entity.name) }
            showMessage("Added image as a new layer")
        }
    }

    /** Drops an image onto the canvas as a floating selection: drag, scale and rotate it, tap outside to commit. */
    fun importImageForPlacement(uri: Uri) {
        val p = project ?: return
        launchSafe {
            val layer = activeLayer()
            if (layer == null || !layer.visible || layer.locked) {
                showMessage("Pick an unlocked, visible layer first")
                return@launchSafe
            }
            val bmp = Importer.importImageTight(
                getApplication(), uri, (p.width * 0.7f).toInt(), (p.height * 0.7f).toInt()
            ) ?: run { showMessage("Could not open that image"); return@launchSafe }
            commitLassoSelection()
            selectTool(Tool.LASSO)
            val cx = p.width / 2f
            val cy = p.height / 2f
            val bounds = RectF(cx - bmp.width / 2f, cy - bmp.height / 2f, cx + bmp.width / 2f, cy + bmp.height / 2f)
            activeLassoSelection = LassoSelection(bmp, bounds, Path()).also { it.isImported = true }
            revision++
        }
    }

    /**
     * Writes a frame that was just created by an import to disk and drops it from memory. Imported
     * frames are not in [frames] yet, so nothing can be looking at them, and keeping all of them
     * cached (full-size, unsaved) until the import ends is what ran long imports out of memory.
     */
    private suspend fun flushAndEvict(fd: FrameData) {
        try {
            persistFrame(fd)
        } catch (e: Throwable) {
            // Not saved: keep the pixels in memory (the cache keeps retrying) instead of freeing them.
            throw e
        }
        cache.remove(fd.frameId)
        fd.release()
    }

    private var importJob: Job? = null

    /** Cancels a running image/video import; frames already added stay in the project. */
    fun cancelImport() { importJob?.cancel() }

    fun importImageSequence(uris: List<Uri>) {
        val p = project ?: return
        if (importJob?.isActive == true) { showMessage("Another import is still running"); return }
        importJob = launchSafe {
            val owner = "import-images"
            var pos = frames.size // advanced per frame: `frames` itself is only refreshed after the loop
            var imported = 0
            var cancelled = false
            var failed: String? = null
            BusyTracker.show(owner, "Importing images", "0 of ${uris.size}", 0f, onCancel = { cancelImport() })
            try {
                for ((i, uri) in uris.withIndex()) {
                    val bmp = Importer.importImage(getApplication(), uri, p.width, p.height)
                    if (bmp != null) {
                        var inserted: FrameEntity? = null
                        try {
                            val templates = listOf(LayerEntity(frameId = 0, position = 0, name = "Imported", visible = true, opacity = 1f))
                            val (newFrame, _) = repo.insertFrame(projectId, pos, templates)
                            inserted = newFrame
                            val fd = ensureLoaded(newFrame.id)
                            if (fd.layers.isNotEmpty()) {
                                BitmapOps.replace(fd.layers[0].bitmap, bmp)
                                fd.layers[0].version++
                            }
                            flushAndEvict(fd)
                            pos++
                            imported++
                            inserted = null // fully stored
                        } finally {
                            if (!bmp.isRecycled) bmp.recycle()
                            // A frame whose pixels never reached the disk would be a silent blank frame: remove it.
                            inserted?.let { half ->
                                withContext(kotlinx.coroutines.NonCancellable) {
                                    cache.remove(half.id)?.release()
                                    try { repo.deleteFrame(half) } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                    BusyTracker.update(owner, "${i + 1} of ${uris.size}", (i + 1f) / uris.size)
                }
            } catch (e: CancellationException) {
                cancelled = true
            } catch (e: Exception) {
                failed = "Import stopped: ${e.message ?: "could not store an image"}"
            } catch (e: OutOfMemoryError) {
                failed = "Import stopped: not enough memory"
            } finally {
                BusyTracker.hide(owner)
                // Runs even when cancelled, so frames that were already inserted show up in the timeline.
                withContext(kotlinx.coroutines.NonCancellable) {
                    frames = repo.frames(projectId)
                    if (imported > 0 && frames.isNotEmpty()) gotoFrame(frames.lastIndex)
                    repo.touch(projectId)
                }
            }
            showMessage(
                when {
                    failed != null -> "$failed ($imported added)"
                    cancelled -> "Import cancelled ($imported added)"
                    imported == 0 -> "Could not open the selected images"
                    imported < uris.size -> "Imported $imported of ${uris.size} images"
                    else -> "Imported $imported frame${if (imported == 1) "" else "s"}"
                }
            )
        }
    }

    fun importVideoAsFrames(uri: Uri) {
        val p = project ?: return
        if (importJob?.isActive == true) { showMessage("Another import is still running"); return }
        importJob = launchSafe {
            val owner = "import-video"
            var pos = frames.size // see importImageSequence: keeps the frames in playback order
            var imported = 0
            var cancelled = false
            BusyTracker.show(owner, "Importing video", "Reading video…", null, onCancel = { cancelImport() })
            try {
                // Frames are stored one by one as they are decoded instead of all being held in memory.
                Importer.importVideoFramesStreaming(getApplication(), uri, p.width, p.height, p.fps) { _, total, bmp ->
                    try {
                        val templates = listOf(LayerEntity(frameId = 0, position = 0, name = "Video Frame", visible = true, opacity = 1f))
                        val (newFrame, _) = repo.insertFrame(projectId, pos, templates)
                        pos++
                        imported++
                        val fd = ensureLoaded(newFrame.id)
                        if (fd.layers.isNotEmpty()) {
                            BitmapOps.replace(fd.layers[0].bitmap, bmp)
                            fd.layers[0].version++
                        }
                        flushAndEvict(fd)
                    } finally {
                        bmp.recycle()
                    }
                    BusyTracker.update(owner, "Frame $imported of $total", imported.toFloat() / total)
                }
            } catch (e: CancellationException) {
                cancelled = true
            } finally {
                BusyTracker.hide(owner)
                withContext(kotlinx.coroutines.NonCancellable) {
                    frames = repo.frames(projectId)
                    if (imported > 0 && frames.isNotEmpty()) gotoFrame(frames.lastIndex)
                    repo.touch(projectId)
                }
            }
            showMessage(
                when {
                    cancelled -> "Import cancelled ($imported frames added)"
                    imported == 0 -> "Could not read any frames from that video"
                    imported >= Importer.MAX_VIDEO_FRAMES -> "Imported the first $imported frames (limit)"
                    else -> "Imported $imported frame${if (imported == 1) "" else "s"}"
                }
            )
        }
    }

    fun addAudioTrackFromUri(uri: Uri, name: String = "Imported Track") {
        val startFrame = timelineFrameOf(currentIndex)
        launchSafe(Dispatchers.IO) {
            val audioDir = File(getApplication<Application>().filesDir, "projects/$projectId/audio").apply { mkdirs() }
            val destFile = File(audioDir, "track_${System.currentTimeMillis()}_${(0..9999).random()}.m4a")
            val partial = File(audioDir, destFile.name + ".part")
            var failure: String? = null
            try {
                val input = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Could not open that audio file")
                input.use { src ->
                    FileOutputStream(partial).use { out ->
                        var total = 0L
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = src.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_AUDIO_IMPORT_BYTES) throw java.io.IOException("That audio file is too large")
                            out.write(buf, 0, n)
                        }
                        out.flush()
                        out.fd.sync()
                    }
                }
                if (!partial.renameTo(destFile)) throw java.io.IOException("Could not store the audio file")
                val duration = WaveformExtractor.getAudioDurationMs(destFile.absolutePath)
                if (duration <= 0L) throw java.io.IOException("That file does not contain playable audio")
                repo.addAudioTrack(
                    AudioTrackEntity(
                        projectId = projectId,
                        filePath = destFile.absolutePath,
                        name = name,
                        startFrame = startFrame,
                        durationMs = duration
                    )
                )
            } catch (e: CancellationException) {
                partial.delete(); destFile.delete()
                throw e
            } catch (e: Exception) {
                failure = e.message ?: "Could not import that audio file"
            } catch (e: OutOfMemoryError) {
                failure = "Not enough memory to import that audio file"
            }
            if (failure != null) {
                // Never leave a half-copied file or a track row that points at nothing.
                partial.delete(); destFile.delete()
            }
            val tracks = try { repo.getAudioTracks(projectId) } catch (e: Exception) { null }
            val failureMessage: String? = failure // immutable copy: a captured var cannot be smart-cast
            withContext(Dispatchers.Main) {
                if (tracks != null) audioTracks = tracks
                revision++
                if (failureMessage != null) showMessage(failureMessage)
            }
        }
    }

    private val voiceRecorder = org.wishyclip.app.audio.VoiceRecorder(app)
    private var recordingStartFrame = 0

    /** True while the microphone is being recorded; drives the Record/Stop button. */
    var isRecording by mutableStateOf(false)
        private set

    /** SystemClock.elapsedRealtime() when the current recording began (for the on-screen timer). */
    var recordingStartedAt by mutableStateOf(0L)
        private set

    /** Starts recording at the playhead. The caller must already hold the microphone permission. */
    fun startVoiceRecording() {
        if (isRecording) return
        if (isPlaying) togglePlay()
        val dir = File(getApplication<Application>().filesDir, "projects/$projectId/audio")
        if (repo.store.usableSpaceBytes() < MIN_FREE_BYTES_FOR_RECORDING) {
            showMessage("Not enough free storage to record")
            return
        }
        voiceRecorder.onLimitReached = {
            showMessage("Recording stopped at the 10 minute limit")
            stopVoiceRecording()
        }
        recordingStartFrame = timelineFrameOf(currentIndex)
        if (voiceRecorder.startRecording(dir) == null) {
            showMessage("Could not start recording. Is the microphone in use by another app?")
            return
        }
        recordingStartedAt = android.os.SystemClock.elapsedRealtime()
        isRecording = true
    }

    /** Stops the recording and adds it as a clip starting where recording began. */
    fun stopVoiceRecording() {
        if (!isRecording) return
        isRecording = false
        val file = voiceRecorder.stopRecording()
        if (file == null) {
            showMessage("Nothing was recorded")
            return
        }
        addRecordedVoiceTrack(file, recordingStartFrame)
    }

    private fun addRecordedVoiceTrack(recordedFile: File, startFrame: Int) {
        launchSafe(Dispatchers.IO) {
            var failure: String? = null
            try {
                val duration = WaveformExtractor.getAudioDurationMs(recordedFile.absolutePath)
                if (duration <= 0L) throw java.io.IOException("The recording is empty")
                repo.addAudioTrack(
                    AudioTrackEntity(
                        projectId = projectId,
                        filePath = recordedFile.absolutePath,
                        name = "Voice ${audioTracks.size + 1}",
                        startFrame = startFrame,
                        durationMs = duration
                    )
                )
            } catch (e: CancellationException) {
                recordedFile.delete()
                throw e
            } catch (e: Exception) {
                failure = e.message ?: "Could not save the recording"
            }
            if (failure != null) recordedFile.delete() // never keep a file no clip points at
            val failureMessage: String? = failure
            val tracks = try { repo.getAudioTracks(projectId) } catch (e: Exception) { null }
            withContext(Dispatchers.Main) {
                if (tracks != null) audioTracks = tracks
                revision++
                if (failureMessage != null) showMessage(failureMessage)
            }
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
        launchSafe {
            try {
                // One transaction: a failure between the two writes must not leave a shortened clip with no second half.
                repo.splitAudioTrack(
                    track.copy(durationMs = offsetMs),
                    track.copy(
                        id = 0,
                        startFrame = here,
                        trimStartMs = track.trimStartMs + offsetMs,
                        durationMs = track.durationMs - offsetMs
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Could not split the clip")
            }
            refreshAudioTracks()
        }
    }

    private suspend fun refreshAudioTracks() {
        try {
            audioTracks = repo.getAudioTracks(projectId)
            revision++
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showMessage("Could not read the audio tracks")
        }
    }

    fun updateAudioTrack(track: AudioTrackEntity) {
        launchSafe {
            try {
                repo.updateAudioTrack(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Could not update the audio track")
            }
            refreshAudioTracks()
        }
    }

    fun deleteAudioTrack(id: Long) {
        audioManager.remove(id)
        val doomed = audioTracks.firstOrNull { it.id == id }
        launchSafe {
            try {
                repo.deleteAudioTrack(id)
                // Split halves share one file, so only delete it when no other track still uses it.
                if (doomed != null) {
                    val others = repo.getAudioTracks(projectId)
                    if (others.none { it.filePath == doomed.filePath }) {
                        withContext(Dispatchers.IO) { File(doomed.filePath).delete() }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Could not delete the audio track")
            }
            refreshAudioTracks()
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

    /**
     * Scales the floating picture uniformly (aspect kept) so its rotated outline fits inside the
     * canvas ([cover] = false) or covers it completely ([cover] = true), and centres it.
     */
    fun fitLassoSelectionToCanvas(cover: Boolean) {
        val sel = activeLassoSelection ?: return
        val p = project ?: return
        val w = sel.pixels.width.toFloat()
        val h = sel.pixels.height.toFloat()
        val t = Math.toRadians(sel.rotation.toDouble())
        val c = Math.abs(Math.cos(t)).toFloat()
        val s = Math.abs(Math.sin(t)).toFloat()
        val boxW = (w * c + h * s).coerceAtLeast(1f)
        val boxH = (w * s + h * c).coerceAtLeast(1f)
        val kx = p.width / boxW
        val ky = p.height / boxH
        val k = (if (cover) maxOf(kx, ky) else minOf(kx, ky)).coerceIn(0.1f, 10f)
        sel.scaleX = k
        sel.scaleY = k
        sel.translateX = p.width / 2f - w / 2f - sel.bounds.left
        sel.translateY = p.height / 2f - h / 2f - sel.bounds.top
        revision++
    }

    /** Restores the picture's original proportions, keeping its overall size. */
    fun resetLassoProportions() {
        val sel = activeLassoSelection ?: return
        val k = Math.sqrt((sel.scaleX * sel.scaleY).toDouble()).toFloat().coerceIn(0.1f, 10f)
        sel.scaleX = k
        sel.scaleY = k
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
        launchSafe {
            availableFonts = withContext(Dispatchers.IO) { fontLibrary.list() }
            if (availableFonts.none { it.key == activeFontName }) activeFontName = FontLibrary.DEFAULT_KEY
        }
    }

    /** Imports one or more font files picked by the user and selects the first new one. */
    fun importFonts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        launchSafe {
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
        launchSafe {
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
            val previous = spec.text
            spec.text = text.take(TextRaster.MAX_CHARS)
            spec.fontName = activeFontName
            if (!rerasterText(sel)) spec.text = previous
            return
        }
        val layer = activeLayer() ?: return
        if (!layer.visible) return
        commitLassoSelection()
        val spec = TextSpec(text.take(TextRaster.MAX_CHARS), defaultTextSize(), activeFontName)
        val bmp = try {
            TextRaster.render(spec.text, color, spec.sizePx, opacity, getTypeface(spec.fontName))
        } catch (e: Throwable) {
            showMessage("Could not render that text (too large for the available memory)")
            return
        }
        val bounds = RectF(
            textPendingX - bmp.width / 2f, textPendingY - bmp.height / 2f,
            textPendingX + bmp.width / 2f, textPendingY + bmp.height / 2f
        )
        activeLassoSelection = LassoSelection(bmp, bounds, Path(), text = spec)
        revision++
    }

    /** Re-renders a text selection (new text, size, color) keeping its centre and rotation. */
    private fun rerasterText(sel: LassoSelection): Boolean {
        val spec = sel.text ?: return false
        val cx = sel.bounds.centerX() + sel.translateX
        val cy = sel.bounds.centerY() + sel.translateY
        val fresh = try {
            TextRaster.render(spec.text, color, spec.sizePx, opacity, getTypeface(spec.fontName))
        } catch (e: Throwable) {
            showMessage("Could not render that text (too large for the available memory)")
            return false // the previous bitmap stays valid
        }
        // The old bitmap is left to the GC: it may still be referenced by a frame being drawn.
        sel.pixels = fresh
        sel.bounds = RectF(cx - fresh.width / 2f, cy - fresh.height / 2f, cx + fresh.width / 2f, cy + fresh.height / 2f)
        sel.translateX = 0f
        sel.translateY = 0f
        sel.scaleX = 1f
        sel.scaleY = 1f
        revision++
        return true
    }

    fun updateStabilizer(value: Float) {
        stabilizer = value.coerceIn(0f, 1f)
    }

    fun copyFrame() {
        val fd = currentFrameData() ?: return
        val copiedLayers = fd.layers.map { it.deepCopy() }
        copiedFrameData = FrameData(fd.frameId, copiedLayers.toMutableList())
        revision++
    }

    fun pasteFrame() {
        val copy = copiedFrameData ?: return
        launchSafe {
            val templates = copy.layers.mapIndexed { i, l ->
                LayerEntity(
                    frameId = 0, position = i, name = l.name, visible = l.visible, opacity = l.opacity,
                    locked = l.locked, blendMode = l.blendMode.name
                )
            }
            val pos = currentIndex + 1
            val (newFrame, _) = repo.insertFrame(projectId, pos, templates)
            try {
                val fd = ensureLoaded(newFrame.id)
                for (i in copy.layers.indices) {
                    if (i in fd.layers.indices) {
                        val src = copy.layers[i]
                        if (src.allocated) {
                            BitmapOps.replace(fd.layers[i].bitmap, src.bitmap)
                            fd.layers[i].version++
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // The new frame row exists but its pixels could not be filled in: remove it again instead of
                // leaving a silent blank frame, and say why.
                cache.remove(newFrame.id)?.release()
                try { repo.deleteFrame(newFrame) } catch (_: Exception) {}
                frames = repo.frames(projectId)
                showMessage(if (e is OutOfMemoryError) "Not enough memory to paste the frame" else "Could not paste the frame")
                return@launchSafe
            }
            frames = repo.frames(projectId)
            gotoFrame(pos)
            repo.touch(projectId)
            scheduleSave() // the pasted pixels are dirty until written
        }
    }

    fun updateProjectFps(fps: Int) {
        val newFps = fps.coerceIn(1, 60)
        project = project?.copy(fps = newFps)
        launchSafe { repo.updateProjectFps(projectId, newFps) }
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
            // Sample while dragging; the color is chosen from the last pixel touched on release.
            gesture = Gesture.EYEDROP
            sampleEyedropper(x, y)
            return
        }
        val layer = activeLayer() ?: return
        if (!layer.visible) {
            showMessage("This layer is hidden. Show it to draw on it.")
            return
        }
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
        try {
            // First touch on an empty (sparse) layer allocates its bitmap here.
            renderer.begin(layer.bitmap, tool, color, brushSize, opacity, x, y, pressure, tilt, mirrorMode, dab)
        } catch (e: OutOfMemoryError) {
            strokeLayer = null
            showMessage("Not enough memory to draw on this layer. Close other apps or use a smaller canvas.")
            return
        }
        revision++
    }

    fun strokeMove(x: Float, y: Float, pressure: Float = 1f, tilt: Float = 0f) {
        when (gesture) {
            Gesture.EYEDROP -> {
                sampleEyedropper(x, y)
                return
            }
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
            Gesture.EYEDROP -> {
                gesture = Gesture.NONE
                finishEyedropper()
                return
            }
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
        if (gesture == Gesture.EYEDROP) {
            // Cancelled (e.g. a second finger turned it into a pinch): keep what was sampled, leave the tool.
            gesture = Gesture.NONE
            finishEyedropper()
            return
        }
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

    /** Undo/redo steps run one at a time, in tap order, even when a frame must first be reloaded from disk. */
    private val historyMutex = Mutex()

    private fun applyHistory(isUndo: Boolean) {
        if (!(if (isUndo) undoManager.canUndo else undoManager.canRedo)) return
        launchSafe {
            historyMutex.withLock {
                val entry = (if (isUndo) undoManager.popUndo() else undoManager.popRedo()) ?: return@withLock
                syncUndoFlags()
                var counterpart: UndoEntry? = null
                try {
                    val frameData = ensureLoaded(entry.frameId)
                    val layer = frameData.layers.firstOrNull { it.id == entry.layerId }
                    if (layer == null) {
                        // The layer no longer exists (deleted or merged): the step is obsolete.
                        entry.bitmap.recycle()
                        syncUndoFlags()
                        return@withLock
                    }
                    val rect = entry.rect
                    counterpart = if (rect != null) {
                        UndoEntry(entry.frameId, entry.layerId, BitmapOps.copyRect(layer.bitmap, rect), rect)
                    } else {
                        UndoEntry(entry.frameId, entry.layerId, layer.bitmap.snapshot())
                    }
                    if (rect != null) BitmapOps.putAt(layer.bitmap, entry.bitmap, rect.left, rect.top)
                    else BitmapOps.replace(layer.bitmap, entry.bitmap)
                    // Only after the pixels were really restored does the opposite step become available.
                    if (isUndo) undoManager.pushRedo(counterpart) else undoManager.pushUndoKeepRedo(counterpart)
                    counterpart = null
                    entry.bitmap.recycle()
                    layer.version++

                    val idx = frames.indexOfFirst { it.id == entry.frameId }
                    if (idx >= 0 && idx != currentIndex) {
                        currentIndex = idx
                        refreshLayerUi()
                    }
                    revision++
                    scheduleSave()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Do not lose the step: put it back so the user can try again.
                    counterpart?.let { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
                    if (!entry.bitmap.isRecycled) {
                        if (isUndo) undoManager.pushUndoKeepRedo(entry) else undoManager.pushRedo(entry)
                    }
                    showMessage(if (e is OutOfMemoryError) "Not enough memory to ${if (isUndo) "undo" else "redo"}"
                                else "Could not ${if (isUndo) "undo" else "redo"}")
                } finally {
                    syncUndoFlags()
                }
            }
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
        if (fd.layers.size >= MAX_LAYERS) {
            showMessage("Layer limit reached ($MAX_LAYERS per frame)")
            return
        }
        val countBefore = fd.layers.size
        launchSafe {
            val entity = repo.addLayer(fd.frameId, fd.layers.size, "Layer ${fd.layers.size + 1}")
            // Sparse: the new layer holds no memory until something is drawn on it.
            fd.layers.add(
                LayerData(
                    entity.id, entity.name, true, 1f, null,
                    allocator = { Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888) }
                )
            )
            fd.metaVersion++
            activeLayerIndex = fd.layers.lastIndex
            refreshLayerUi()
            revision++
            scheduleSave()
            mirrorLayerOp { mirrorAdd(fd.frameId, countBefore, entity.name) }
        }
    }

    fun deleteLayer(index: Int) {
        val fd = currentFrameData() ?: return
        if (fd.layers.size <= 1 || index !in fd.layers.indices || strokeLayer != null) return
        val countBefore = fd.layers.size
        val layer = fd.layers.removeAt(index)
        undoManager.dropLayer(layer.id)
        syncUndoFlags()
        layer.release()
        fd.metaVersion++
        activeLayerIndex = activeLayerIndex.coerceIn(0, fd.layers.lastIndex)
        refreshLayerUi()
        revision++
        launchSafe { repo.deleteLayer(projectId, layer.id) }
        scheduleSave()
        mirrorLayerOp { mirrorDelete(fd.frameId, countBefore, index) }
    }

    fun duplicateLayer(index: Int) {
        val fd = currentFrameData() ?: return
        val source = fd.layers.getOrNull(index) ?: return
        if (fd.layers.size >= MAX_LAYERS) {
            showMessage("Layer limit reached ($MAX_LAYERS per frame)")
            return
        }
        val countBefore = fd.layers.size
        launchSafe {
            val entity = repo.addLayer(fd.frameId, fd.layers.size, "${source.name} (Copy)")
            val bmp = source.peekBitmap()?.snapshot()
            val newLayer = LayerData(
                entity.id, entity.name, source.visible, source.opacity, bmp, source.locked, source.blendMode,
                source.allocator
            )
            // BUG FIX: the copy was never marked dirty, so its pixels were not written to disk and the
            // duplicate came back blank after the frame was evicted or the app restarted.
            if (bmp != null) newLayer.version++
            fd.layers.add(index + 1, newLayer)
            fd.metaVersion++
            activeLayerIndex = index + 1
            refreshLayerUi()
            revision++
            scheduleSave()
            // Other frames get an empty copy with the same settings (pixels are only copied on this frame).
            val snap = LayerMetaSnap(entity.name, source.visible, source.opacity, source.locked, source.blendMode)
            mirrorLayerOp { mirrorInsert(fd.frameId, countBefore, index + 1, snap) }
        }
    }

    /** Renames a layer in every frame. */
    fun renameLayer(index: Int, name: String) {
        val fd = currentFrameData() ?: return
        val layer = fd.layers.getOrNull(index) ?: return
        val clean = name.trim().take(40)
        if (clean.isEmpty() || clean == layer.name) return
        layer.name = clean
        fd.metaVersion++
        refreshLayerUi()
        scheduleSave()
        mirrorLayerMeta()
    }

    fun setLayerVisible(index: Int, visible: Boolean) {
        val fd = currentFrameData() ?: return
        val layer = fd.layers.getOrNull(index) ?: return
        layer.visible = visible
        fd.metaVersion++
        refreshLayerUi()
        revision++
        scheduleSave()
        mirrorLayerMeta()
    }

    fun setLayerOpacity(index: Int, value: Float) {
        val fd = currentFrameData() ?: return
        val layer = fd.layers.getOrNull(index) ?: return
        layer.opacity = value.coerceIn(0f, 1f)
        fd.metaVersion++
        refreshLayerUi()
        revision++
        scheduleSave()
        mirrorLayerMeta()
    }

    fun setLayerLocked(index: Int, locked: Boolean) {
        val fd = currentFrameData() ?: return
        val layer = fd.layers.getOrNull(index) ?: return
        layer.locked = locked
        fd.metaVersion++
        refreshLayerUi()
        revision++
        scheduleSave()
        mirrorLayerMeta()
    }

    fun setLayerBlend(index: Int, blend: LayerBlend) {
        val fd = currentFrameData() ?: return
        val layer = fd.layers.getOrNull(index) ?: return
        layer.blendMode = blend
        fd.metaVersion++
        refreshLayerUi()
        revision++
        scheduleSave()
        mirrorLayerMeta()
    }

    /** Flattens the layer at [index] into the one below it (cannot be undone). */
    fun mergeLayerDown(index: Int) {
        val fd = currentFrameData() ?: return
        if (strokeLayer != null || index !in 1..fd.layers.lastIndex) return
        commitLassoSelection()
        val countBefore = fd.layers.size
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
        if (upper.visible && upper.opacity > 0f && upper.allocated) {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            paint.alpha = (upper.opacity * 255f).toInt().coerceIn(0, 255)
            Canvas(lower.bitmap).drawBitmap(upper.bitmap, 0f, 0f, paint)
            lower.version++
        }
        fd.layers.removeAt(index)
        undoManager.dropLayer(upper.id)
        undoManager.dropLayer(lower.id)
        syncUndoFlags()
        upper.release()
        fd.metaVersion++
        activeLayerIndex = (if (activeLayerIndex >= index) activeLayerIndex - 1 else activeLayerIndex)
            .coerceIn(0, fd.layers.lastIndex)
        refreshLayerUi()
        revision++
        launchSafe { repo.deleteLayer(projectId, upper.id) }
        scheduleSave()
        showMessage("Layers merged")
        mirrorLayerOp { mirrorMerge(fd.frameId, countBefore, index) }
    }

    /** [delta] +1 moves the layer up (towards the top of the stack), -1 down. */
    fun moveLayer(index: Int, delta: Int) {
        val fd = currentFrameData() ?: return
        val target = index + delta
        if (index !in fd.layers.indices || target !in fd.layers.indices) return
        val countBefore = fd.layers.size
        val layer = fd.layers.removeAt(index)
        fd.layers.add(target, layer)
        if (activeLayerIndex == index) activeLayerIndex = target
        fd.metaVersion++
        refreshLayerUi()
        revision++
        scheduleSave()
        mirrorLayerOp { mirrorMove(fd.frameId, countBefore, index, target) }
    }

    // ------------------------------------------------ layers shared by all frames
    //
    // A layer is one stack position across the whole animation: adding, deleting, moving, hiding,
    // renaming ... a layer on one frame applies to every frame. Empty layers cost no memory (sparse
    // layers), so this is cheap. A frame only follows when its stack has the same size as the one
    // that changed (older projects may differ; those frames are left alone instead of guessed at).

    private data class LayerMetaSnap(
        val name: String,
        val visible: Boolean,
        val opacity: Float,
        val locked: Boolean,
        val blendMode: LayerBlend
    )

    /** Runs a mirroring step after earlier ones (FIFO), off the caller's path. */
    private fun mirrorLayerOp(block: suspend () -> Unit) {
        launchSafe {
            layerMirrorMutex.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showMessage("Could not update layers on other frames")
                }
            }
        }
    }

    /** Other frames whose layer stack still has [expectedCount] layers. */
    private suspend fun framesMirroring(sourceFrameId: Long, expectedCount: Int): List<FrameEntity> =
        frames.filter { f ->
            f.id != sourceFrameId &&
                (cache[f.id]?.layers?.size ?: repo.layers(f.id).size) == expectedCount
        }

    private suspend fun mirrorAdd(sourceFrameId: Long, countBefore: Int, name: String) {
        val p = project ?: return
        for (f in framesMirroring(sourceFrameId, countBefore)) {
            val entity = repo.addLayer(f.id, countBefore, name)
            cache[f.id]?.let { fd ->
                fd.layers.add(
                    LayerData(
                        entity.id, entity.name, true, 1f, null,
                        allocator = { Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888) }
                    )
                )
                fd.metaVersion++
            }
        }
        scheduleSave()
    }

    private suspend fun mirrorDelete(sourceFrameId: Long, countBefore: Int, index: Int) {
        for (f in framesMirroring(sourceFrameId, countBefore)) {
            val cached = cache[f.id]
            if (cached != null) {
                val victim = cached.layers.getOrNull(index) ?: continue
                cached.layers.removeAt(index)
                undoManager.dropLayer(victim.id)
                victim.release()
                cached.metaVersion++
                repo.deleteLayer(projectId, victim.id)
            } else {
                val rows = repo.layers(f.id).sortedBy { it.position }
                val victim = rows.getOrNull(index) ?: continue
                repo.deleteLayer(projectId, victim.id)
                repo.saveLayers(rows.filter { it.id != victim.id }.mapIndexed { i, e -> e.copy(position = i) })
            }
        }
        syncUndoFlags()
        scheduleSave()
    }

    private suspend fun mirrorInsert(sourceFrameId: Long, countBefore: Int, at: Int, snap: LayerMetaSnap) {
        val p = project ?: return
        for (f in framesMirroring(sourceFrameId, countBefore)) {
            val entity = repo.addLayer(f.id, countBefore, snap.name)
            val cached = cache[f.id]
            if (cached != null) {
                val layer = LayerData(
                    entity.id, snap.name, snap.visible, snap.opacity, null, snap.locked, snap.blendMode,
                    allocator = { Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888) }
                )
                cached.layers.add(at.coerceIn(0, cached.layers.size), layer)
                cached.metaVersion++
            } else {
                val rows = repo.layers(f.id).sortedBy { it.position }.toMutableList()
                val created = rows.firstOrNull { it.id == entity.id } ?: continue
                rows.remove(created)
                rows.add(
                    at.coerceIn(0, rows.size),
                    created.copy(
                        visible = snap.visible, opacity = snap.opacity, locked = snap.locked,
                        blendMode = snap.blendMode.name
                    )
                )
                repo.saveLayers(rows.mapIndexed { i, e -> e.copy(position = i) })
            }
        }
        scheduleSave()
    }

    private suspend fun mirrorMove(sourceFrameId: Long, countBefore: Int, from: Int, to: Int) {
        for (f in framesMirroring(sourceFrameId, countBefore)) {
            val cached = cache[f.id]
            if (cached != null) {
                if (from !in cached.layers.indices || to !in cached.layers.indices) continue
                cached.layers.add(to, cached.layers.removeAt(from))
                cached.metaVersion++
            } else {
                val rows = repo.layers(f.id).sortedBy { it.position }.toMutableList()
                if (from !in rows.indices || to !in rows.indices) continue
                rows.add(to, rows.removeAt(from))
                repo.saveLayers(rows.mapIndexed { i, e -> e.copy(position = i) })
            }
        }
        scheduleSave()
    }

    /** Flattens [index] into the layer below on every other frame (loads each frame in turn). */
    private suspend fun mirrorMerge(sourceFrameId: Long, countBefore: Int, index: Int) {
        for (f in framesMirroring(sourceFrameId, countBefore)) {
            val fd = ensureLoaded(f.id)
            if (index !in 1..fd.layers.lastIndex) continue
            val upper = fd.layers[index]
            val lower = fd.layers[index - 1]
            if (upper.visible && upper.opacity > 0f && upper.allocated) {
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                paint.alpha = (upper.opacity * 255f).toInt().coerceIn(0, 255)
                LayerBlending.apply(paint, upper.blendMode) // same look as on screen / in the source frame
                Canvas(lower.bitmap).drawBitmap(upper.bitmap, 0f, 0f, paint)
                lower.version++
            }
            fd.layers.removeAt(index)
            undoManager.dropLayer(upper.id)
            undoManager.dropLayer(lower.id)
            upper.release()
            fd.metaVersion++
            repo.deleteLayer(projectId, upper.id)
            persistFrame(fd)
            // Do not let a long merge pull the whole animation into memory.
            val distance = Math.abs(frames.indexOfFirst { it.id == f.id } - currentIndex)
            if (distance > 2) cache.remove(f.id)?.release()
        }
        syncUndoFlags()
    }

    /**
     * Copies the current frame's layer settings (name, visibility, opacity, lock, blend) to all frames.
     * Debounced: dragging an opacity slider must not write to every frame on every change. The snapshot is
     * taken when the copy RUNS (not when it was requested), and runs are never cancelled half way, so quick
     * edits on two different frames cannot overwrite each other with stale values.
     */
    private fun mirrorLayerMeta() {
        val sourceId = currentFrameData()?.frameId ?: return
        pendingMetaSources.add(sourceId)
        metaTicket += 1
        val ticket = metaTicket
        launchSafe {
            delay(350)
            if (ticket == metaTicket) runPendingMetaMirrors()
        }
    }

    /** Runs every pending settings copy now, after any earlier queued layer operations (FIFO). */
    private suspend fun runPendingMetaMirrors() {
        layerMirrorMutex.withLock {
            while (pendingMetaSources.isNotEmpty()) {
                val sourceId = pendingMetaSources.first()
                pendingMetaSources.remove(sourceId)
                try {
                    val snaps = metaSnapsOf(sourceId) ?: continue
                    mirrorMetaNow(sourceId, snaps)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showMessage("Could not update layers on other frames")
                }
            }
        }
    }

    /** Layer settings of [frameId] as they are right now (memory if loaded, otherwise the database). */
    private suspend fun metaSnapsOf(frameId: Long): List<LayerMetaSnap>? {
        cache[frameId]?.let { fd ->
            return fd.layers.map { LayerMetaSnap(it.name, it.visible, it.opacity, it.locked, it.blendMode) }
        }
        val rows = repo.layers(frameId).sortedBy { it.position }
        if (rows.isEmpty()) return null
        return rows.map { LayerMetaSnap(it.name, it.visible, it.opacity, it.locked, LayerBlend.from(it.blendMode)) }
    }

    /** Finishes all pending layer syncing; call before leaving the editor or exporting. */
    private suspend fun settleLayerMirrors() {
        runPendingMetaMirrors()
    }

    private suspend fun mirrorMetaNow(sourceId: Long, snaps: List<LayerMetaSnap>) {
        for (f in frames) {
            if (f.id == sourceId) continue
            val cached = cache[f.id]
            if (cached != null) {
                if (cached.layers.size != snaps.size) continue
                var changed = false
                cached.layers.forEachIndexed { i, l ->
                    val s = snaps[i]
                    if (l.name != s.name || l.visible != s.visible || l.opacity != s.opacity ||
                        l.locked != s.locked || l.blendMode != s.blendMode
                    ) {
                        l.name = s.name; l.visible = s.visible; l.opacity = s.opacity
                        l.locked = s.locked; l.blendMode = s.blendMode
                        changed = true
                    }
                }
                if (changed) cached.metaVersion++
            } else {
                val rows = repo.layers(f.id).sortedBy { it.position }
                if (rows.size != snaps.size) continue
                val updated = rows.mapIndexed { i, e ->
                    val s = snaps[i]
                    e.copy(
                        name = s.name, visible = s.visible, opacity = s.opacity,
                        locked = s.locked, blendMode = s.blendMode.name
                    )
                }
                if (updated != rows) repo.saveLayers(updated)
            }
        }
        refreshLayerUi()
        revision++
        scheduleSave()
    }

    // ---------------------------------------------------------------- saving


    /**
     * Every background job of the editor runs through here. An exception that nobody caught (a full disk
     * during add/delete frame, a SQLite error, out of memory ...) used to terminate the whole app and
     * with it the unsaved drawing; now it becomes a message and the in-memory edits stay alive and keep
     * being saved by the retrying autosave.
     */
    private fun launchSafe(
        context: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ): Job = viewModelScope.launch(context + safeHandler, block = block)

    private fun scheduleSave() {
        saveTicket += 1
        val ticket = saveTicket
        launchSafe {
            delay(1500)
            if (ticket == saveTicket) saveAll()
        }
    }

    /** True while a save keeps failing; the dirty versions stay set so the next save retries everything. */
    private var lastSaveFailed = false

    /**
     * Writes every dirty frame. Never throws (except cancellation): a failed write leaves the layer dirty,
     * keeps the previous valid file on disk (BitmapStore replaces files atomically) and tells the user once.
     * @return true when everything that was dirty has been written.
     */
    private suspend fun saveAll(): Boolean {
        var wrote = false
        var allOk = true
        for (fd in cache.values.toList()) {
            try {
                if (persistFrame(fd)) wrote = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                allOk = false
            } catch (e: OutOfMemoryError) {
                allOk = false
            }
        }
        if (wrote) {
            try {
                repo.touch(projectId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The pixels are safe; only the "last modified" time was not updated.
            }
        }
        if (!allOk && !lastSaveFailed) {
            showMessage("Could not save your latest changes. Free up storage; the app will keep retrying.")
        }
        lastSaveFailed = !allOk
        if (!allOk) retrySaveLater()
        return allOk
    }

    private fun retrySaveLater() {
        val ticket = ++saveTicket
        launchSafe {
            delay(10_000)
            if (ticket == saveTicket) saveAll()
        }
    }

    /** Writes dirty layers (as PNG) and layer metadata. @return true if anything was written. */
    private suspend fun persistFrame(fd: FrameData): Boolean {
        val dirtyLayers = fd.layers.filter { it.dirty }
        val metaDirty = fd.metaVersion != fd.metaSavedVersion
        if (dirtyLayers.isEmpty() && !metaDirty) return false
        val pid = projectId
        // Copies are taken on the calling (main) thread so the IO thread never reads a bitmap being drawn on.
        val copies = dirtyLayers.filter { it.allocated }.map { Triple(it, it.version, it.bitmap.snapshot()) }
        val metaVersion = fd.metaVersion
        val metas = fd.layers.mapIndexed { i, l ->
            LayerEntity(
                id = l.id, frameId = fd.frameId, position = i, name = l.name, visible = l.visible,
                opacity = l.opacity, locked = l.locked, blendMode = l.blendMode.name
            )
        }
        val isFirst = frames.firstOrNull()?.id == fd.frameId
        val thumb = if (dirtyLayers.isNotEmpty()) composeThumb(fd) else null
        try {
            saveMutex.withLock {
                withContext(Dispatchers.IO) {
                    // Refuse early when the volume is nearly full: better a clear message than a half-written set.
                    val needed = copies.sumOf { it.third.byteCount.toLong() } / 2 + 1_000_000L
                    if (copies.isNotEmpty() && store.usableSpaceBytes() < needed) {
                        throw java.io.IOException("Not enough free storage to save")
                    }
                    for ((layer, _, bmp) in copies) {
                        store.save(pid, layer.id, bmp)
                    }
                    repo.saveLayers(metas)
                    if (thumb != null) {
                        if (isFirst) store.saveThumb(pid, thumb)
                        store.saveFrameThumb(pid, fd.frameId, thumb)
                    }
                }
            }
        } finally {
            // Snapshots are always freed, also when a write failed (a failing save is retried with new ones).
            for ((_, _, bmp) in copies) if (!bmp.isRecycled) bmp.recycle()
            if (thumb != null && !thumb.isRecycled) thumb.recycle()
        }
        if (thumb != null) frameThumbVersion++
        for ((layer, version, _) in copies) layer.savedVersion = version
        // Empty layers have nothing to write (and no file): they are simply up to date.
        dirtyLayers.filter { !it.allocated }.forEach { it.savedVersion = it.version }
        fd.metaSavedVersion = metaVersion
        return true
    }

    /** The preview image of a frame for the timeline (may not exist yet for blank frames). */
    fun frameThumbFile(frameId: Long): JavaFile = store.frameThumbFile(projectId, frameId)

    /**
     * Writes preview images for frames that have drawing but no preview yet (projects made before
     * previews existed, duplicated / pasted / imported frames). Blank frames get none, which the
     * timeline draws as an empty dashed cell.
     */
    fun backfillFrameThumbs() {
        val p = project ?: return
        val ids = frames.map { it.id }
        launchSafe(Dispatchers.IO) {
            val tw = 320
            val th = (tw.toLong() * p.height / p.width).toInt().coerceAtLeast(1)
            var wrote = false
            for (fid in ids) {
                if (store.frameThumbFile(projectId, fid).exists()) continue
                val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                val dst = Rect(0, 0, tw, th)
                var hasContent = false
                for (e in repo.layers(fid)) {
                    if (!e.visible) continue
                    val src = store.decodeOrNull(projectId, e.id) ?: continue
                    paint.alpha = (e.opacity * 255f).toInt().coerceIn(0, 255)
                    LayerBlending.apply(paint, LayerBlend.from(e.blendMode))
                    canvas.drawBitmap(src, null, dst, paint)
                    src.recycle()
                    hasContent = true
                }
                if (hasContent) {
                    store.saveFrameThumb(projectId, fid, bmp)
                    wrote = true
                }
                bmp.recycle()
            }
            if (wrote) withContext(Dispatchers.Main) { frameThumbVersion++ }
        }
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
            if (!l.visible || !l.allocated) continue
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
        launchSafe {
            settleLayerMirrors()
            saveAll()
            wishy.syncScheduler.requestNow(projectId)
        }
    }

    /**
     * Writes pending edits to disk, then runs [onDone]. Export reads the saved layer files, and normal
     * saving is debounced by 1.5 s, so without this the newest strokes could be missing from an export.
     */
    fun saveThen(onDone: () -> Unit) {
        commitLassoSelection()
        stopPlay()
        launchSafe {
            settleLayerMirrors()
            if (saveAll()) onDone()
            else showMessage("Could not save, so the export was not started. Free up storage and try again.")
        }
    }

    private var exiting = false

    /**
     * Saves, then calls [onDone] exactly once. A second call while one is in flight is ignored (double back
     * press used to pop the Home screen too). If saving fails the editor stays open so no work is lost.
     */
    fun saveAndExit(onDone: () -> Unit) {
        if (exiting) return
        exiting = true
        commitLassoSelection()
        stopPlay()
        launchSafe {
            settleLayerMirrors()
            if (saveAll()) {
                wishy.syncScheduler.requestNow(projectId)
                onDone()
            } else {
                exiting = false
                showMessage("Could not save. Free up storage, then leave the editor again.")
            }
        }
    }

    override fun onCleared() {
        voiceRecorder.cancel() // an unfinished recording is discarded, never left half-written
        commitLassoSelection()
        renderer.release()
        audioManager.release()
        val remaining = cache.values.toList()
        // Last chance to write pending edits. Each frame is guarded separately so one failure
        // cannot prevent the others from being saved, and nothing here may throw.
        for (fd in remaining) {
            try {
                runBlocking { persistFrame(fd) }
            } catch (e: Throwable) {
                // Nothing sensible to do while the ViewModel is being destroyed; the old files are intact.
            }
        }
        for (fd in remaining) {
            fd.release()
        }
        cache.clear()
        undoManager.clear()
        super.onCleared()
    }
}

private const val TAP_SLOP = 12f

/** Upper bound for one imported audio file (about 3 hours of 128 kbit/s audio). */
/** Refuse to start recording with less free space than this (10 minutes of AAC is about 10 MB). */
private const val MIN_FREE_BYTES_FOR_RECORDING = 50L * 1024 * 1024

private const val MAX_AUDIO_IMPORT_BYTES = 200L * 1024 * 1024

/** Layers per frame. Empty layers are free, but each painted one costs a full bitmap per cached frame. */
private const val MAX_LAYERS = 64

/** Frames kept loaded ahead of the playhead during playback. */
private const val PLAY_AHEAD = 6

class EditorViewModelFactory(
    private val app: Application,
    private val projectId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(app, projectId) as T
}
