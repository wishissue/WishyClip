# Changelog

All notable changes to Wishy Clip will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0-v1.0.0.html).

## [Unreleased]

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
