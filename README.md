# Wishy Clip

![Wishy Clip Banner](app/src/main/res/drawable/ic_logo.xml)

**Wishy Clip** is a free, open-source frame-by-frame 2D animation app for Android. No ads, no paywalls, no subscriptions.

> *Wishy Clip is an independent open-source project and is not affiliated with or endorsed by FlipaClip or Vblast.*

---

## Features

- **Blazing Fast Drawing Pipeline**: Built on `SurfaceView`, unbuffered dispatch, and `MotionEventPredictor` for sub-25ms input-to-ink latency.
- **Tiled Sparse Layers**: 256x256 RGBA_8888 tile grid with LZ4 compression and bitmap pooling to comfortably support 500+ frames under tight memory limits.
- **Dual Layouts (Classic & Studio)**: Switch instantly between a minimalist full-screen stage and a professional multi-layer timeline with a time ruler and scrubbing.
- **Advanced Tools**: Brush, Eraser, Fill (with tolerance), Lasso (with full freeform transform/resize/rotate), Shapes, Text, Eyedropper, Ruler, and Mirror.
- **Extensible Brush Engine**: 12 built-in brushes plus batch importers for community brushes.

### Supported Brush Formats

| Format | Extension | Description |
|---|---|---|
| Wishy Brush | `.wbrush` | ZIP archive with JSON metadata and PNG tip |
| Krita Bundle | `.bundle` / `.kpp` | Krita brush presets and bundle packs |
| Photoshop Brush | `.abr` | Photoshop brush tip textures |
| GIMP Brush | `.gbr` / `.gih` | GIMP raster brush tips |
| Procreate Brush | `.brush` | Procreate shape and grain params |

- **Audio & Voiceover**: Import audio tracks, record voiceovers, and view synchronized waveforms directly on the timeline.
- **Export & Share**: Export high-performance MP4 videos (MediaCodec + MediaMuxer), animated GIFs, or PNG sequences.
- **Customizable Themes**: Light, Dark, AMOLED Black, Candy, and community JSON theme packs.

---

## Installation & APK

Download the latest signed release APK from the [GitHub Releases](https://github.com/wishyclip/wishyclip/releases) page.

---

## Building from Source

### Prerequisites
- Android Studio (Hedgehog or newer)
- JDK 17

### Build Steps
```bash
git clone https://github.com/wishyclip/wishyclip.git
cd wishyclip
./gradlew assembleDebug testDebugUnitTest
```

---

## Project Structure & Architecture

Wishy Clip follows an MVVM architecture with clean separation of concerns:
- `canvas/`: Low-latency drawing surface, tiled layer rendering, and stroke renderer.
- `data/`: Room database entities, DAOs, repository, and SQLite/LZ4 storage.
- `audio/`: ExoPlayer audio synchronization, voice recording, and waveform extraction.
- `export/`: MediaCodec MP4 encoder, animated GIF encoder, and foreground service.
- `ui/`: Jetpack Compose design system (`ui/design/`), reusable components (`ui/components/`), and screens (`ui/screens/`).

---

## Roadmap

- [x] High-performance tiled drawing pipeline
- [x] Classic & Studio timeline layouts
- [x] Multi-format brush importers
- [x] MP4 & GIF export
- [ ] Stylus gesture shortcuts and custom shortcuts

---

## License & Credits

Licensed under the [Apache License 2.0](LICENSE). See [ATTRIBUTIONS.md](ATTRIBUTIONS.md) for third-party library and icon attributions (Phosphor Icons, Material Symbols, Fluent Emoji).
