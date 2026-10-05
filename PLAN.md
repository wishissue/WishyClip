# Wishy Clip: Plan

Free, open-source (Apache 2.0) frame-by-frame 2D animation app for Android. No ads, no paywalls.

## Stack
Kotlin 2.0.21, Jetpack Compose + Material 3 (BOM 2024.12.01), AGP 8.7.3, Gradle 8.9, minSdk 26,
compile/target 35. Room (KSP) for metadata, DataStore for brush prefs, Coil for project thumbnails,
Media3 ExoPlayer declared for future audio, coroutines. Version catalog: `gradle/libs.versions.toml`.

## Architecture (MVVM)
```
org.wishyclip.app
  WishyApp / MainActivity   service locator + NavHost (home, editor/{projectId})
  model/    Tool, Presets
  data/     Room entities+DAOs, ProjectRepository, BitmapStore (PNG files), SettingsStore
  canvas/   DrawingCanvas (Compose), StrokeRenderer, BrushPaints, UndoManager, FrameData,
            stubs: OnionSkin, FillTool, LassoTool
  ui/       Home/Editor screens, HomeViewModel, EditorViewModel, ColorPickerDialog, Theme
  audio/    stub: AudioTrackManager
  export/   stub: Exporter
```

### Data model
Project > Frames (ordered by `position`) > Layers (ordered by `position`, 0 = bottom).
Room stores names/fps/size/order/visibility/opacity. Pixels are one PNG per layer at
`filesDir/projects/{projectId}/layers/{layerId}.png`; a missing file means a blank layer.
`projects/{id}/thumb.png` is the home-screen thumbnail (frame 1 composite).
Layers belong to a single frame: a new frame copies the layer *structure* (names, visibility,
opacity) of the current frame with blank pixels.

### Memory
`EditorViewModel.cache` holds `FrameData` only for current frame +-2. Navigating evicts others
(dirty ones are saved first) and prefetches neighbours. Undo snapshots are bounded (~96 MB budget,
5-30 steps); entries for evicted frames are dropped.

### Drawing
Pointer events -> bitmap coordinates -> `StrokeRenderer`. Each move resets the layer to the
pre-stroke snapshot and redraws the whole stroke once (uniform opacity, no overlap darkening).
The snapshot becomes the undo entry. The canvas composable redraws on `EditorViewModel.revision`.

### Autosave
Debounced 1.5 s after every edit, plus on ON_STOP, on back/exit, and as a last resort in
`onCleared`. Dirty tracking uses per-layer `version/savedVersion` counters. Writes go through a
mutex; loading a frame waits on the same mutex so it never reads half-saved files.

### Playback
A coroutine advances `currentIndex` at the project FPS (compensating for load time) and loops.
Drawing is disabled while playing.

## Feature checklist
- [x] Project list, new project (name, size preset, FPS), delete
- [x] Editor: pen, pencil, marker, eraser, size, opacity, color picker (HSV)
- [x] Undo / redo
- [x] Layer panel (add, delete, reorder, visibility, opacity)
- [x] Timeline: add, duplicate, delete, reorder (move left/right), select
- [x] Play / pause at project FPS
- [x] Autosave, +-2 frame bitmap cache
- [ ] TODO onion skin (`canvas/OnionSkin.kt`)
- [ ] TODO fill (`canvas/FillTool.kt`)
- [ ] TODO lasso (`canvas/LassoTool.kt`)
- [ ] TODO audio (`audio/AudioTracks.kt`)
- [ ] TODO export MP4/GIF/PNG (`export/Exporter.kt`)
- [ ] Later: zoom/pan, drag-to-reorder timeline, frame thumbnails, project-wide layers, rename project,
      stylus pressure, copy/paste frames, tests, real icons
