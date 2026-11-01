# Changelog

## Unreleased

### Redesign (WishIssue UI Design Guidelines)
- New default themes **Wishy** (light) and **Wishy Dark**, built from the shared WishIssue tokens (primary `#1560D4` / `#A8C8FF`). With no saved choice the app follows the system light/dark setting. Existing themes stay available and a saved choice is kept.
- Interface font is now Plus Jakarta Sans; the logo wordmark uses Bagel Fat One (Latin subsets, OFL).
- Panels (top bar, tool dock, timeline, pop-overs) are flat, solid surfaces with a hairline outline instead of translucent "glass"; smaller corner radius.
- Tool buttons are 48dp. Keyboard focus is now visible on every button.

### Fixes
- **Palm rejection** setting did nothing; it now makes fingers navigate only (stylus or mouse draws), with an explanation.
- **Haptic feedback** setting did nothing; it now turns the press haptics on/off.
- **Keyboard shortcuts** listed in the app (B E F L T R M O, Space, arrows, Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y) were not implemented; they are now.
- Deleting a project had no confirmation; it now asks first.
- App name showed as "WishaClip" in the UI; now "WishyClip".
- Settings, New project and Gallery screens drew under the status and gesture bars; onboarding buttons sat under the gesture bar; New project fields could be covered by the keyboard.
- Onboarding page was lost on rotation.
- White flash on launch in dark mode (window background now follows system light/dark).
- Canvas painted a fixed grey over the theme's canvas backdrop.
- Double-tap tool buttons (eraser, lasso, fill, shapes, text) could not be activated with TalkBack.
- The theme list in Settings was a hand-copied duplicate of the built-in list; it now reads the single source.

- Round 2: sliders are 44dp tall, expose value/range to TalkBack, and the thumb is no longer clipped at the ends.
- Round 2: timecode and the audio playhead now count held frames (exposure) instead of frame index.
- Round 2: removed double-tap-to-undo (it only ever fired during playback, undoing by accident).
- Round 2: icon pack was not Compose state, so the choice did not update icons or the Settings highlight until something else recomposed.
- Round 2: new "Follow system light/dark" option in Settings > Theme.

### v1.1 stable work (see docs/UPDATE_PLAN_v1.1.md)
- **Sparse layers (Phase 1):** a layer that has never been painted now holds no bitmap at all, so 21 empty layers at 1080p cost 0 bytes instead of about 165 MiB per frame. The bitmap is created on first paint; loading skips layers that have no file; drawing, saving, thumbnails, merge, clear and copy/paste all skip empty layers.
- **Memory budget:** the frame cache now stays within 40% of the heap limit (always keeping the current frame and its neighbours), and prefetch stops early instead of loading frames that would be evicted straight away.
- **Layer limit:** 64 layers per frame with a clear message (was unlimited, ending in out-of-memory).
- `android:largeHeap="true"` for drawing apps with big canvases.
- New project screen now explains memory per painted layer instead of a fixed 5-frame figure.
- **Starter brush pack (Phase 3, first part):** 16 original brushes generated in code (Graphite HB / 6B, Sketch pencil, Colour pencil, Technical pen, Ink liner, Brush pen, Dry ink, Charcoal stick, Pastel, Gouache, Oil flat, Bristle, Splatter, Stipple, Spray grain). Installed once into the brush library on first launch of this version; users can tune or delete them. No third-party brush files or textures are used.
- **Playback / 60 fps (Phase 2, part):** playback pacing moved to a tested `PlaybackClock` on an absolute schedule. The old timer waited `1000 / fps` whole milliseconds, which ran up to 4% fast (24 fps gave 24.4, 48 fps gave 50, 60 fps gave 62.5) and drifted. Playback also has its own look-ahead loader that is no longer cancelled every tick (the old prefetch was cancelled each frame at high fps, so frames loaded on the playback path and stuttered); the cache keeps 6 frames ahead of the playhead while playing.
- **Out-of-memory guard:** starting a stroke on a layer that cannot get memory now shows a message instead of crashing.
- A duplicate frame-load race no longer leaks bitmaps.
- Drawing on a hidden layer now says so instead of silently doing nothing.
- Color picker hex field accepts only hex digits (max 7 characters).
- Dark theme: unselected timeline frame numbers used a fixed grey with poor contrast; project-card placeholder icon ignored the theme and was announced twice by TalkBack. Default tokens (used outside a theme) are now Wishy light instead of the old purple baseline.
- CI also runs on the `wishissue-redesign` branch and can be started manually.
- **Dab brushes respond to pressure with flow:** pencils, charcoal, pastel and spray (flow below 0.9) now also get darker as you press harder, not only bigger. Solid inks and paints change size only.
- **Strings (Phase 5, part):** 98 hardcoded `Text(...)` labels across 18 UI files moved into `strings.xml` (94 strings, `ui_` prefix), ready for translation. Content descriptions, dialog titles and `Text` calls with templates are still hardcoded.
- **Layers are now shared by all frames (Phase 1.6):** adding, deleting, reordering, duplicating, merging, hiding, locking, blending, changing opacity of, or renaming a layer on one frame now applies to every frame, as in other animation apps. It builds on sparse layers (an empty layer costs no memory), so adding a layer to a 200-frame animation is cheap. Only the drawings stay per frame: duplicating a layer copies pixels on the current frame and gives other frames an empty layer with the same settings; "Merge down" flattens on every frame (loading frames one at a time without keeping them in memory). Frames whose layer stack differs in size (older projects) are left alone instead of guessed at. Changes run in order in the background; property changes are debounced so dragging opacity does not write to every frame.
- **Rename layer** (new): button in the selected layer row.
- **Deleting or merging a layer now asks first**, because it now affects every frame.
- Layer list uses stable keys (rows no longer mix up state when reordered); header now reads "Layers (all frames)".
- Wishy themes use calmer corners (10/16/24 dp instead of 12/20/32) and a flatter panel shadow.
- Added `docs/QA_CHECKLIST_v1.1.md`: what to test on a device, since none of this has run on one yet.
- Color picker: a typed hex or tapped swatch is kept exactly (no one-off drift from the HSV round trip).
- 99 more hardcoded labels (icon descriptions, dialog titles and buttons, slider names, section headers) moved to `strings.xml`.
- Version 1.1.0 (versionCode 2) and a store changelog (`fastlane/.../changelogs/2.txt`).
- Added `docs/FIRST_BUILD_NOTES.md` (what was and was not checked, where to look first, how to revert pieces).
- **Layer settings sync is lossless:** the copy of layer settings (visibility, opacity, name, lock, blend) to other frames is now finished before the editor is left or an export starts, takes its snapshot when it runs, and is never cancelled half way. Previously a change made less than half a second before leaving could be lost, and quick edits on two frames could overwrite each other with stale values.
- **Exports are much faster with many layers:** blank (never painted) layers are skipped instead of allocating a full-size empty bitmap per layer per frame, and each painted layer needs one allocation less.
- **Back button:** it closed the whole editor even with a dialog, color picker, brush menu or layers panel open. It now closes the open dialog or panel first.
- **New project:** a custom canvas size of 0 or a huge number was accepted and would crash when the project opened. Sides are now limited to 64 to 4096 px, with a numeric keyboard and digits only; the project name is limited to 60 characters; the Create button can no longer be double-tapped into two projects.
- Dialog buttons now have a button role for TalkBack.
- **Paint bucket:** a fill finishing after its frame had been evicted or its layer deleted could write into a freshly created blank bitmap (the old "is recycled" check stopped working once layers became sparse); it now checks the layer is still loaded. Fill also shows a message instead of crashing when memory runs out.
- **Database safety:** removed Room's destructive fallback. With it, any future schema change without a migration silently deleted every project's frames and layers (the PNG files stayed behind, orphaned). A missing migration now fails loudly during development. Every version bump must add a `Migration`.
- **GIF export timing:** each frame's delay was rounded to hundredths of a second on its own, so animations ran off-speed (24 fps played as 25; in my arithmetic a 30 fps GIF came out 10% too slow). The running total is now rounded, so the GIF lasts as long as the animation.
- **Data-loss bug fixed:** a duplicated layer was never marked as needing a save, so it came back blank after switching frames or restarting.

### Removed (unreferenced code)
- Legacy `ui/HomeScreen.kt`, `ui/Theme.kt` (a second, conflicting `WishyTheme`), `ui/ColorPickerDialog.kt`.
- Orphaned `ExportScreen` and its `export/{projectId}` route (nothing navigated to it, and it showed "Project <id>" as the name). Export is done from the editor menu.


All notable changes to Wishy Clip will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0-v1.0.0.html).

## [Unreleased]

### Changed (UI redesign: "Cloud")
- New default **Cloud** (light) and **Midnight** (dark) themes: soft pastel palette, rounded corners, airy spacing.
- Bigger controls everywhere: 56dp tool buttons, 52dp minimum touch targets, 64dp Play button.
- Floating glass panels for the top bar, tool dock, timeline and pop-overs.
- Springy press animation and light haptics on all buttons.
- Timeline: two-row compact layout on phones (no more overflow), one-row on wide screens; bigger frame cells and exposure +/- buttons; one big Play button.
- Top bar: Play moved to the timeline; project title hidden on narrow phones to make room for bigger buttons.
- Home: iOS-style large title, big "New Animation" button, friendlier empty state, rounder project cards.
- Settings: theme picker is now a scrollable row of chips.
- Typography: bundled Inter (SIL OFL 1.1).

### Added
- Mirror drawing (left/right, top/bottom, 4-way) for brushes, eraser, shapes and imported brushes.
- Ruler: movable / rotatable straight edge with stroke snapping.
- Imported tip brushes with a dab engine (spacing, angle, rotate-with-stroke, scatter, size jitter) and importers for `.wbrush`, `.kpp`, `.bundle`, `.abr`, `.gbr`, `.gih`, `.brush`, `.brushset`.
- Layer lock, layer blend modes (Normal, Multiply, Screen, Overlay, Darken, Lighten, Add) and merge down. Room schema v3 with a real 2 -> 3 migration.
- Canvas rotate-90 buttons and Reset View; canvas view survives device rotation.
- New icons: Charcoal, Ink, Watercolor, Chalk, Pixel Pen, custom brush, Mirror, Ruler, Rotate left/right, Import, Merge, Blend.

### Changed
- The eyedropper now samples the composited colour (layer opacity and blend modes included) and returns to the previous tool.
- Rotating the device no longer recreates the activity; floating panels are height-limited and scroll in short windows.
- Tool-options title uses the tool's display name.

### Fixed
- Documentation no longer claims features that were not implemented (tiled layer storage, 12 brushes, importers) before this release. Tiled storage is still not wired in.

## [1.0.0] - 2025-02-25

### Added
- High-performance drawing pipeline using `SurfaceView`, `MotionEventPredictor`, and unbuffered dispatch for sub-25ms input-to-ink latency.
- Tiled sparse layers (256x256 RGBA_8888 tiles) with LZ4 compression and bitmap pooling.
- Dual layout modes: Classic (full-screen stage with floating tools and mini timeline) and Studio (multi-layer rows, time ruler, playback scrubber).
- Advanced tool suite: Pen, Pencil, Marker, Eraser, Airbrush, Calligraphy, Highlighter, Fill (with tolerance), Lasso (with full transform handles), Shapes, Text, Eyedropper, Ruler, and Mirror.
- Extensible brush engine supporting 12 built-ins and multi-format community brush importers (.wbrush, Krita .bundle/.kpp, .abr, .gbr/.gih, Procreate .brush).
- Audio track importing, voice recording, and waveform strip synchronization with timeline playback.
- Export service supporting MP4 video (MediaCodec + MediaMuxer), animated GIF, and PNG sequences with Android share sheet integration.
- Complete Design System with Light, Dark, AMOLED Black, and Candy theme packs plus custom JSON theme importing.

## Unreleased

### Performance
- Strokes are now drawn incrementally (one curve segment per input event) onto a reusable
  overlay instead of restoring and redrawing the whole stroke on every event. Long and fast
  strokes no longer slow down as they grow.
- Undo stores only the rectangle a stroke touched (eraser: only the touched 128px cells), not a
  full-canvas copy per stroke. Undo history is also capped at 64 MB.
- Canvas draw path no longer allocates Paint/Matrix/Rect objects every frame.

### Fixes
- Pinch zoom/pan/rotate now works continuously (the gesture handler was restarted on every step).
- Strokes drawn while zoomed and panned landed in the wrong place (pan was not divided by zoom).
- Second finger landing no longer commits a stray dot; stylus ignores resting palms.

### Lasso & Text
- Lasso selections can now be moved, scaled and rotated with on-canvas handles, committed with
  Done, restored with Put back, or deleted. Touching empty canvas drops the selection in place.
  A dashed preview shows the loop while drawing.
- Text is now an editable floating object: tap to place, drag/scale/rotate, tap it again (or Edit)
  to change the words; color and opacity changes apply live. It only merges into the layer on Done.
