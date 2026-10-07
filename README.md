# WishaClip

![WishaClip Logo](app/src/main/res/drawable/ic_logo.png)

**WishaClip** is a free, open-source frame-by-frame 2D animation app for Android. No ads, no paywalls, no subscriptions.

> *WishaClip is an independent open-source project and is not affiliated with or endorsed by FlipaClip or Vblast.*

---

## Features

- **Drawing**: Pressure-sensitive strokes with a stabilizer, undo/redo, pinch zoom / pan / rotate, and 11 built-in brushes (Pen, Pencil, Marker, Airbrush, Calligraphy, Highlighter, Charcoal, Ink, Watercolor, Chalk, Pixel) plus the Eraser.
- **Tools**: Fill (with tolerance), Lasso (freeform select with move / resize / rotate), Line / Rectangle / Ellipse, Text, Eyedropper.
- **Mirror**: Symmetry drawing across centre lines (2-way or 4-way). Works with every brush, eraser, and shape tools.
- **Ruler**: Movable, rotatable straight edge with snapping.
- **Layers**: Add / delete / reorder, opacity, visibility, lock, blend modes (Normal, Multiply, Screen, Overlay, Darken, Lighten, Add), and merge down.
- **Animation**: Frame timeline, copy / paste frames, onion skin, adjustable FPS, image / video import as frames.
- **Portrait & Landscape**: Adaptive layouts for each orientation, left-handed mode support, and rotatable canvas viewports.
- **Custom Brushes & Fonts**: Import community brush packs (`.wbrush`, `.abr`, `.kpp`, `.gbr`) and custom font files (`.ttf`, `.otf`).
- **Audio & Voiceover**: Multi-track audio timeline with trimming, volume adjustments, split at playhead, and waveform lane.
- **Export**: High-performance MP4 video export (with audio mixing), animated GIFs, or PNG sequences.
- **Customizable Themes**: Studio Ink, Cloud, Midnight, AMOLED, Candy, Light, and Dark theme packs.

---

## Installation & APKs

Download the signed release APKs from the [GitHub Releases](https://github.com/wishissue/WishyClip/releases) page. Choose the correct APK for your device architecture:
- **`WishaClip-1.0.0-arm64-v8a-release.apk`**: Recommended for almost all modern Android phones and tablets (64-bit ARM).
- **`WishaClip-1.0.0-armeabi-v7a-release.apk`**: For older 32-bit ARM devices.
- **`WishaClip-1.0.0-universal-release.apk`**: Works on all architectures (larger file size).

---

## Building from Source

### Prerequisites
- Android Studio (Hedgehog or newer)
- JDK 17

### Build Steps
```bash
git clone https://github.com/wishissue/WishyClip.git
cd WishyClip
./gradlew assembleDebug testDebugUnitTest
```

---

## Architecture

WishaClip follows an MVVM architecture with clean separation of concerns:
- `canvas/`: Drawing canvas, stroke renderer, ruler, and layer blending.
- `brush/`: Brush file parsers and on-disk brush store.
- `data/`: Room database entities, DAOs, repository, and storage.
- `audio/`: Multi-track audio playback (`MultiTrackAudioPlayer`), voice recording, and waveform extraction.
- `export/`: MediaCodec MP4 encoder, audio mixer, animated GIF encoder, and foreground service.
- `ui/`: Jetpack Compose design system (`ui/design/`), reusable components (`ui/components/`), and screens (`ui/screens/`).

---

## License & Notices

Licensed under the [MIT License](LICENSE). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party library and asset attributions (Phosphor Icons, Media3 ExoPlayer, Coil, AndroidX).
