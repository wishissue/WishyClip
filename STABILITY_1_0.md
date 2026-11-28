# WishyClip 1.0 stabilization — status report

This document separates what was **changed**, what was **verified**, and what is **still unverified**.
Environment limits: no Android SDK, no Gradle distribution (the egress proxy blocks services.gradle.org,
maven.google.com and repo.maven.apache.org), no Kotlin compiler, no emulator or device.
**Nothing in this change set has been compiled or run.** All Kotlin was checked by source reading and a
brace/paren balance script only.

## Changes made (by severity)

| # | Area | Problem found in source | Fix |
|---|------|------------------------|-----|
| 1 | Saving | Any exception in `saveAll()` (disk full, SQLite error) escaped `viewModelScope` and crashed the app; save errors were invisible | `saveAll()` never throws, returns success, tells the user once, leaves layers dirty and retries every 10 s |
| 2 | Saving | Snapshot bitmaps leaked whenever a write threw | `finally` frees snapshots; free-space pre-check |
| 3 | Eviction | A failed save during cache eviction freed unsaved pixels | Frame is put back in the cache and retried |
| 4 | Exit | Back pressed twice popped Home too; exit proceeded even if the save failed | Re-entrancy guard; editor stays open with a message if saving fails |
| 5 | Export start | Export silently never started if the save failed | User is told; export not started |
| 6 | Layer files | A non-atomic fallback copy could leave a partial PNG; an undecodable file was treated as blank and then overwritten by the next save | Atomic move; undecodable files quarantined as `*.png.corrupt`; leftover `.tmp` swept on open |
| 7 | Frame duplicate | If a pixel copy failed the frame was silently blank | Whole duplication is rolled back and reported |
| 8 | Project delete | Orphaned folders after an interrupted delete | Swept on start (only numeric folders with no DB row) |
| 9 | Audio import | Uncaught IO/Security exceptions crashed the app; partial copy and zero-length tracks were kept | Temp file + fsync + rename, size cap, duration check, cleanup, message |
| 10 | Audio split | Two separate DB writes | One transaction; errors reported |
| 11 | Audio delete | File never removed | Removed when no other clip shares it |
| 12 | Audio lane | Second half of a split clip drawn too short | Uses `durationMs` like player and mixer |
| 13 | Audio export | First sample smeared over the lead-in of any clip starting after frame 0 (click/DC offset) | Fill starts at the first sample |
| 14 | Audio export | OOM in mixer failed the whole export; decoder/extractor leaks | OOM drops only the soundtrack; leaks closed |
| 15 | MP4 | Timed-out drain still reported success (truncated file); timestamp drift | Success requires real end-of-stream; exact timestamps |
| 16 | Export output | Failed MediaStore copy left a hidden pending row and then wrote a second copy; on Android 8–9 a successful save was reported as failed | Row deleted on failure; `external-path` added; media scan |
| 17 | Export service | `stopSelf()` before `startForeground()` can crash; busy request dropped silently; `startForegroundService` unguarded | Reordered; notice shown; guarded |
| 18 | PNG sequence | `frame_%03d` sorts wrongly after 999 frames; compress result ignored | `%05d`; failure raises |
| 19 | Imports | Image-sequence import crashed on any non-cancel exception and could leave blank frames | Caught, bitmap freed, half-inserted frame removed |
| 20 | Brushes | Import parsed on main thread with no guard (OOM / StackOverflow crash); non-atomic writes; unbounded names | IO thread, `Throwable` guard, atomic writes, name/size limits |
| 21 | Project open | Any exception in bootstrap left a stuck spinner or crashed | Shows the existing load-error screen |
| 22 | New project | Only per-side limits: 4096×4096 ≈ 64 MB per layer | Total-pixel cap (≈8.8 MP) in one tested helper; errors surfaced |
| 23 | Privacy | `allowBackup=true` contradicted "nothing leaves your device" and could restore DB out of sync with PNGs | Disabled |
| 24 | Misc | Gallery folder misspelled `WishaClip` | `WishyClip` (existing exports remain where they are) |

## Drawing-engine fixes (second pass)

| # | Problem found in source | Fix |
|---|------------------------|-----|
| 25 | 46 coroutines in `EditorViewModel` had no exception handling: a full disk during add/delete frame or layer, any SQLite error, or OOM killed the app and the unsaved drawing | All launches go through `launchSafe` (`CoroutineExceptionHandler` shows a message; edits stay in memory and autosave keeps retrying) |
| 26 | Undo/redo popped the step *before* the async work: a failed frame reload lost the step (and could crash); two quick taps could apply out of order after eviction | Steps run serialized under a mutex, the opposite step is registered only after the pixels were restored, a failure puts the step back |
| 27 | `UndoManager.pushUndoKeepRedo` never enforced the stack limit | Limit and byte budget enforced |
| 28 | Fill allocated two canvas-sized stacks (~115 MB working memory at 4096x2160) and silently dropped spans when full; OOM leaked both copies | One growable packed stack; OOM caught and copies freed |
| 29 | `Bitmap.snapshot()` ended in `copy(...)!!` — low memory became a NullPointerException | Throws `OutOfMemoryError`, which callers handle |
| 30 | `StrokeRenderer` kept drawing/restoring on a bitmap recycled mid-stroke ("trying to use a recycled bitmap") | Stroke is dropped safely |
| 31 | `pasteFrame` never scheduled a save and left a blank frame row if filling it failed | Saves, and rolls back on failure |

| 32 | If the gesture handler restarted or was cancelled mid-stroke (rotation, `enabled` toggling), the stroke was never ended, so undo/redo and frame/layer deletion stayed blocked for the session | Stroke is always closed in a `finally` (what was drawn is kept) |
| 33 | `DrawingSurfaceView.kt` was dead code (never referenced, never drew strokes, locked a canvas on every touch) | Deleted, with its `input-motionprediction` dependency |
| 34 | `LayerBlending` cache was a plain `HashMap` used by the UI thread and the export thread concurrently | `ConcurrentHashMap`; concurrency test added |
| 35 | Text bitmaps up to 4096x4096 (64 MB) and unbounded text length; OOM on the UI thread in text confirm/re-raster | 2048 px / 2000 chars cap; failures show a message and keep the previous text |
| 36 | Merging a layer on the *other* frames ignored its blend mode, unlike the source frame | Blend mode applied |

Checked and found OK: `saveLayers` is an `@Update` (a late save cannot resurrect a deleted layer row);
`BrushPaints` clamps blur radii (no `BlurMaskFilter` crash); dab stepping terminates (spacing >= 1 px);
eraser backup/restore; shape and mirror geometry (mirror about the canvas centre, dirty rects mirrored too);
onion-skin ghosts skip recycled layers.

## Docs changed
README and `fastlane` no longer advertise: icon packs, tiled sparse layers / "500+
frames", the "sub-25 ms" latency figure, "12 built-ins" (now 11). README's *Known limitations* lists what is
not connected. To bring a feature back, restore its line once it works end to end.

## Verification status

**Executed:** `ProjectLimits.fit` logic ported to Python and checked on ~200,000 randomized and edge cases
(0 violations). This validates the algorithm, not the Kotlin.

**Also executed:** the new packed-stack flood-fill algorithm re-implemented in Python on a comb that forces the
stack past its initial size (max depth 2099): all cells filled, no wall damage.

**Tests written, NOT run:** `ProjectLimitsTest`, `BitmapStoreSafetyTest` (+ `RepositorySafetyTest`),
`BrushStoreSafetyTest`, three new `UndoManagerTest` cases, two new `FillToolTest` cases, `TextRasterTest`, `LayerBlendingTest`, `ProjectFolderTest`, `ProjectFolderSyncTest`, `GifQuantizerTest`, `MirrorAndDabTest` additions. Run `./gradlew testDebugUnitTest` first. Specifically unverified:
- whether Robolectric's `Bitmap.compress` throws on a recycled bitmap (`failedSaveKeepsThePreviousValidFile`);
- every changed Kotlin file compiles (smart casts, imports, Room KSP for the new `allIds` query).

**Statically inspected only:** all fixes above, and the export, audio, import and persistence paths.

## Known issues NOT fixed (need a decision or device testing)

- **Canvas engine: reviewed by reading** (third pass): `DrawingCanvas` (gestures, transform, drawing),
  `StrokeRenderer` (all of it, incl. dab stamping, shapes, mirror), `BrushPaints`, `DabBrush`, `OnionSkin` and
  `getOnionSkinData`, `LayerBlending`, `TextRaster`, text confirm/re-raster, and the whole cross-frame layer
  machinery (`mirrorAdd/Delete/Insert/Move/Merge`, meta mirroring, `settleLayerMirrors`). **Fourth pass (final):** `LassoTool`
  (transform matrix, hit-testing, lift), `RulerState`, `CanvasViewState`, `PlaybackClock`, `BitmapPool` and
  `DabPreview` were read in full. Fixed: lasso lift cleared with an anti-aliased paint although the copy used a hard
  clip (left a faint fringe in neither place); `BitmapPool` could hand one bitmap to two users after a double recycle
  and would crash on an immutable one. The rest was correct. Nothing here was run.
- **Drawing behaviours fixed in the last batch:** with palm rejection on, one finger now pans the canvas; the live
  stroke preview uses the layer's blend mode; a mirrored custom-brush stamp is a true reflection (the tip bitmap is
  flipped, not just rotated); the stroke overlay is cleared once per stroke over the touched area instead of
  several times over the full bitmap. Tests: `MirrorAndDabTest` (mirrored tip, overlay clean). The pan and the
  blend preview are UI behaviour with no unit test: check them on a device.
- **Known behaviours left as they are:** "Clear frame" also clears locked layers and creates one undo step per
  layer (several taps to undo); merging a hidden layer discards its pixels; layer delete/merge cannot be undone
  (both ask for confirmation first).
- **Audio recording (new):** `VoiceRecorder` rewritten (mono 44.1 kHz AAC, 10 minute limit, never leaves a
  half-written file), permission requested on first use, Record/Stop in the audio dialog, stops when the app is left,
  refuses to start under 50 MB free, empty recordings are discarded, a clip is only stored when its file plays.
  **Device test needed** (microphone busy, permission denied, rotation while recording, killing the app).
- **Audio trim (new):** Trim start and Length sliders for the selected clip (the file is never cut; the player
  and the MP4 mixer already honour `trimStartMs`/`durationMs`). Not unit-tested.
- **Audio clip position** is a timeline slot (a frame position), length is real time. Inserting or deleting frames
  before a clip does not move it, and changing fps keeps it on the same frame.
- **Icon packs** and **"500+ frames / tiled sparse layers"** are advertised but are stubs (`TileStore` unused).
  Remove the claims or implement them.
- **Video import** takes only the first 60 frames (limit kept on purpose). Frames are now decoded at the canvas size
  (`getScaledFrameAtTime`, API 27+, aspect ratio kept) instead of full resolution. Unverified on a device.
- **GIF quality:** images with more than 256 colours now use a per-frame median-cut palette (was a fixed 3-3-2
  palette; ported and checked in Python, new `GifQuantizerTest`). There is still no dithering, and each frame picks
  its own palette, so smooth gradients can band and colours can shift slightly between frames.
- Project delete while an import/export runs is now refused (the busy overlay already blocks the screen).
- DataStore now has a corruption handler (defaults) and read errors yield defaults.
- `README`/`fastlane` counts of built-in brushes disagree (11 / 12 / 16).
- `build.gradle.kts` comment saying media3 is "not yet used" is stale (playback is real).

## Device testing still required
Foreground-service export on Android 12–15 (notification permission, backgrounding), MediaStore saves on
Android 8–9 vs 10+, MP4/AAC output on several encoders, audio sync on a real timeline, low-storage behaviour,
process kill during save (confirm `.tmp` sweep and previous file survive), rotation/uiMode changes, and
memory behaviour with large canvases.

## External project folders (new)

Projects are mirrored into a folder the user chooses (Storage Access Framework) and can be re-imported from it
after reinstalling. Code: `data/folder/` (`FolderStore`, `ProjectManifest` + codec, `ProjectFolderWriter`,
`ProjectFolderSync`, `FolderNames`), Room migration 4 to 5 (`uuid`, `folderName`), UI in `ProjectFolderCard`,
Home, New Project and Settings.

Design rules: layer/audio files immutable and named by SHA-256; manifest written last (`.tmp` -> `.bak` -> `.json`,
reader takes the highest valid revision); never write into a folder with a newer `formatVersion`, a damaged
manifest, or another project's uuid; conflicts (folder changed since last sync) are never overwritten silently;
import refuses incomplete projects unless the user asks for a repaired copy; only files matching the app's own
naming are ever garbage-collected.

**Tests written, NOT run:** `ProjectFolderTest` (writer, crash points, conflict, newer version, damaged manifest,
foreign folder, truncated upload, garbage collection, codec, names) and `ProjectFolderSyncTest` (save, same-name
folders, no-op sync, reinstall and import with pixels intact, duplicate import refused, rename renames the folder,
hand-renamed folder kept, conflict then keep-app-version, no folder chosen, export of earlier projects, delete
options, missing and corrupted layer files, newer-version folder, unrelated folders). `FolderAccessTest` covers the folder label and the plain-directory store. Not covered by tests:
`SafFolderStore` (needs a real documents provider), the permission handling in `FolderAccess.open`, all UI.

**Device testing required for this feature:** folder picker on several providers (internal storage, SD card, Drive /
other DocumentsProviders: `createFile` naming, `listFiles` speed, delete), persisted permission after reboot,
reinstall flow ("Lost" state, re-pick, import), migration 4 to 5 on a real v4 database, large projects on slow
storage, killing the app during a save, two devices on one synced folder (conflict dialog).

**Known limitations:** permission to the folder is lost when the app is uninstalled (Android rule), so the folder is
picked again; a project rename renames its folder only if the folder still has the old project's name; folder sync is one-way per save (app -> folder) plus
explicit import; edits made by hand inside a folder are not read back unless imported.

## UI polish pass (untested on a device)

Done in code, **never compiled or run** (no Android SDK in the build environment):

- Accessibility: 48 dp touch targets on colour swatches, brush menu rows, timeline cells, frame-rate chips and restore-UI button; roles/state descriptions for chips, radio rows and switches (whole settings row toggles); canvas content description; live "recording" badge with elapsed time and a Stop button.
- Home: "Saved to folder / Saving… / Not saved to folder" badge on each project card, progress bar with text while a folder is being set up or a project imported, search and Recent/Name sort (shown from 4 projects), "no match" state, snackbars instead of toasts, **Undo** after "Remove from app" (re-imports the kept folder), and a warning before creating a project while the save folder is unreachable.
- New project: save-folder card is now the first step, size presets are radio rows with a shape preview and localized labels, canvas limits and memory estimate are shown up front.
- Editor: floating panels size themselves to the canvas width (small landscape phones, tablets, foldables); Back while recording stops and keeps the take instead of leaving.
- Dialogs: destructive confirms (delete frame/layer, delete project) use the danger colour; default OK/Cancel are localized.
- Onboarding: fourth page explains the save folder.
- Strings: ~70 hard-coded English strings moved to `strings.xml` (and plurals). Still English: messages produced inside ViewModels and the Settings category titles.
- Assets: all icons are Material Symbols Rounded (Apache-2.0) already in the app, except `ic_folder.xml`, a simple original glyph. No new third-party assets were added.

## Magnifier, slider style, one-screen New project (untested on a device)

- **Magnifier (loupe):** while a stroke is down, a round zoomed view (3x) of the exact spot under the pen/finger appears in the top corner farther from the pen, with a ring showing the brush size. It redraws the real scene (layers, onion skin, live stroke), so what you see is what you get. Setting: Settings → Input → "Magnifier while drawing" (on by default). It is hidden for finger touches that only pan/zoom (palm rejection) and disappears when a second finger starts a pinch. It draws the scene twice per frame while stroking: check performance on low-end devices.
- **Sliders:** every slider in the app goes through `WishySlider`, which now has one look from theme tokens: thick rounded track, round ringed thumb, theme-coloured readout.
- **New project:** one screen, no scrolling on a normal phone. A live preview shows the canvas shape to scale, its pixel size, memory warning and a dot row stepping at the chosen frame rate; presets are a single row of shape cards (1:1, 16:9, 9:16, Custom); the save-folder card is a compact one-line row. Landscape / wide windows use two panes (preview left, controls right, scroll only as a last resort).
- New files: `canvas/LoupeLayout.kt` (+ `LoupeLayoutTest`). The only JVM-testable part is the corner choice; the drawing itself needs a device.

## In-app updates and the release system (untested on a device or on GitHub)

What was **actually run** here: the Python release tools' tests (key creation/pinning/refusal with the real `keytool`, secret scanner, version rules) and the refusal paths of the scripts. What was **only written and statically checked**: all Kotlin (`update/`), the Gradle signing changes, the workflows, `apksigner`/`aapt2`/`gh` steps of `build_release.py`. Nothing was compiled; no real release or update was ever performed.

- **Updater:** checks `releases/latest` on GitHub at start (switchable), dialog Update / Later (Later = asked again next start), downloads with progress, verifies SHA-256 (if the release has the file), package name, higher versionCode and *same signing certificate* before opening Android's installer; explains clearly when the key differs. Only github.com hosts over https; size caps; never installs silently.
- **Signing:** keys outside the repo (`~/.wishyclip-signing`), CI can never sign or build release (Gradle throws), debug builds use a shared public key and the id `org.wishyclip.app.debug` so they cannot conflict with real installs. `release.yml` (which signed on GitHub with secrets) was removed; `test-build.yml` publishes debug pre-releases only.
- **For agents:** `AGENT_RELEASE_GUIDE.md` + `AGENTS.md`; scripts in `tools/release/`.
- **Needs checking on a real phone:** the permission page flow on Android 8–14, installing over the previous release, the key-mismatch dialog, that `app-universal-release.apk` is the file name AGP produces (the script falls back to any signed release APK), and that GitHub's `latest` endpoint behaves as described.
- **Behaviour change:** the app now requests INTERNET and REQUEST_INSTALL_PACKAGES (README/PRIVACY updated). For stores that forbid self-updating, build with `-Pwishyclip.selfUpdate=false` (the code is disabled; the manifest permissions still need removing for such a build).
