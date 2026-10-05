# Changelog

All notable changes to Wishy Clip will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0-v1.0.0.html).

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
