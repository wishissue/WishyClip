<div align="center">

# Wishy Clip

**Free, open-source frame-by-frame 2D animation for Android.**
No ads. No paywalls. No subscriptions. No tracking.

[![CI](https://github.com/wishissue/WishyClip/actions/workflows/ci.yml/badge.svg)](https://github.com/wishissue/WishyClip/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)

[Features](#features) · [Install](#install) · [Build](#build-from-source) · [Brushes](#importing-brushes) · [Architecture](#architecture) · [Roadmap](#roadmap) · [Contributing](#contributing)

</div>

<!--
  TODO: add screenshots. The files below are already reserved in docs/screenshots/.
  Uncomment once they exist:

  <p align="center">
    <img src="docs/screenshots/home.png" width="22%" alt="Home">
    <img src="docs/screenshots/editor_portrait.png" width="22%" alt="Editor, portrait">
    <img src="docs/screenshots/editor_landscape.png" width="40%" alt="Editor, landscape">
  </p>
-->

> *Wishy Clip is an independent open-source project and is not affiliated with or endorsed by FlipaClip or Vblast.*

## Why Wishy Clip?

- **Actually free.** Every feature is available to everyone, forever (Apache 2.0).
- **Private by design.** No analytics, no crash-reporting SDKs, no network uploads. Your projects stay on your device. See [PRIVACY.md](PRIVACY.md).
- **Built for stylus and touch.** Pressure sensitivity, stroke stabilizer, palm rejection, and a low-latency drawing surface.
- **Bring your own brushes.** Import community brushes from Krita, Photoshop, GIMP and Procreate.

## Features

### Drawing
- Pressure-sensitive strokes with an adjustable **stabilizer**, undo / redo, pinch zoom / pan / rotate, and 90° rotate buttons.
- **11 built-in brushes** plus the eraser: Pen, Pencil, Marker, Airbrush, Calligraphy, Highlighter, Charcoal, Ink, Watercolor, Chalk, Pixel Pen.
- **Imported tip brushes** with spacing, rotation, scatter and size jitter (see [Importing brushes](#importing-brushes)).

### Tools
| Tool | What it does |
|---|---|
| Fill | Bucket fill with adjustable tolerance |
| Lasso | Freeform selection with move / resize / rotate |
| Shapes | Line, rectangle, ellipse |
| Text | Place and scale text |
| Eyedropper | Samples the colour you *see* (layer opacity and blend modes included), then returns to your previous tool |
| Mirror | Left/right, top/bottom or 4-way symmetry. Works with every brush, the eraser and shapes |
| Ruler | Movable, rotatable straight edge; strokes started beside it snap to the edge |

### Layers
Add, delete and reorder layers; per-layer opacity, visibility and **lock**; **blend modes** (Normal, Multiply, Screen, Overlay, Darken, Lighten, Add); and **merge down**.

### Animation
- Frame timeline with copy / paste / duplicate / reorder, per-frame **exposure (hold) duration**, and **onion skin**.
- Playback at the project FPS.
- Import images or video as frames.
- **Audio:** import tracks, record voiceovers, and see synchronized waveforms on the timeline.

### Export
- **MP4** video (MediaCodec + MediaMuxer)
- **Animated GIF**
- **PNG sequence**

Exports run in a foreground service with a progress notification.

### Interface
- Portrait and landscape layouts that re-arrange tool bars, panels and the timeline (left-handed layout supported). Rotating the device keeps your canvas view.
- Themes: **Cloud** (light), **Midnight** (dark), AMOLED Black, Candy, plus community JSON theme packs. See [DESIGN.md](DESIGN.md).

## Install

**From a release (recommended):** download the latest signed APK from the
[Releases page](https://github.com/wishissue/WishyClip/releases) and open it on your device
(you may need to allow installs from your browser or file manager).

**Requirements:** Android 8.0 (API 26) or newer.

**Latest development build:** every push to `main` builds a debug APK in
[GitHub Actions](https://github.com/wishissue/WishyClip/actions/workflows/ci.yml). Open a run and download the `debug-apk` artifact.

## Build from source

**Prerequisites:** Android Studio (Hedgehog or newer) and JDK 17.

```bash
git clone https://github.com/wishissue/WishyClip.git
cd WishyClip

./gradlew assembleDebug        # build a debug APK
./gradlew testDebugUnitTest    # run the JVM unit tests
./gradlew lintDebug            # run Android lint
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Or just open the folder in Android Studio and press **Run**.

<details>
<summary><b>Tech stack</b></summary>

| | |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose, Material 3 |
| Persistence | Room (metadata), per-layer PNG files (pixels), DataStore (preferences) |
| Media | MediaCodec / MediaMuxer (MP4), Media3 ExoPlayer (audio) |
| Images | Coil (project thumbnails) |
| Concurrency | Kotlin coroutines |
| Build | Gradle 8.9, AGP 8.7.3, `minSdk` 26, `targetSdk` 35 |
| Testing | JUnit, Robolectric, kotlinx-coroutines-test |

Dependencies are managed in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
</details>

## Importing brushes

Wishy Clip can import tip-based brushes from community files. Limits: **20 MB** per file, **512 brushes** per file, tips up to **2048 px** (stored at up to 512 px). Colour is applied at paint time, so every tip becomes a one-colour mask.

| Format | Extension | What is imported |
|---|---|---|
| Wishy Brush | `.wbrush` | ZIP with `brush.json` (name, spacing, angle, rotateWithStroke, scatter, sizeJitter, flow) and `tip.png` |
| Krita | `.kpp`, `.bundle` | Embedded tip, name, spacing and angle; presets without a tip get a soft round tip |
| Photoshop | `.abr` | Sampled (bitmap) tips, v1/v2 and v6+ (v6+ is located heuristically); computed round brushes are skipped |
| GIMP | `.gbr`, `.gih` | Tip and spacing (first brush of a `.gih` pipe) |
| Procreate | `.brush`, `.brushset` | `Shape.png` tip only (no grain, no dynamics) |

> [!NOTE]
> The parsers are tested against files built from each format's documented layout. They have **not** been verified against a large corpus of real-world brush packs, so some files may be rejected or look different. Bug reports with sample files are very welcome.

Want to make your own `.wbrush`? See [BRUSHES.md](BRUSHES.md).

## Architecture

Wishy Clip uses **MVVM** with Jetpack Compose. Source lives in `app/src/main/java/org/wishyclip/app/`:

| Package | Responsibility |
|---|---|
| `canvas/` | Drawing surface, stroke renderer (path strokes, dab stamping, mirror), fill / lasso / onion skin, ruler, layer blending, undo |
| `brush/` | Dependency-free brush file parsers and on-disk brush store (unit-testable on the plain JVM) |
| `data/` | Room entities, DAOs, repository, PNG layer storage, brush library, importers |
| `audio/` | Audio track sync, voice recording, waveform extraction |
| `export/` | MP4 and GIF encoders, export foreground service |
| `model/` | Tools, blend modes, mirror modes, project presets |
| `ui/` | Design system (`ui/design/`), reusable components (`ui/components/`), screens (`ui/screens/`), view models |

**Data model:** `Project → Frames → Layers`. Room stores names, FPS, size, ordering, visibility and opacity; pixels are one PNG per layer under `filesDir/projects/{projectId}/layers/`.

**Memory:** only the current frame ±2 are kept in memory; others are saved and evicted. Undo history is bounded by a memory budget. Edits autosave after 1.5 s of inactivity, and on stop / exit.

For more depth, see [PLAN.md](PLAN.md) and [DESIGN.md](DESIGN.md).

## Roadmap

**Done**
- [x] Mirror, ruler, eyedropper
- [x] Layer lock, blend modes, merge down
- [x] Brush importers (`.wbrush`, Krita, Photoshop, GIMP, Procreate)
- [x] Portrait / landscape layouts that survive rotation
- [x] MP4, GIF and PNG-sequence export
- [x] Per-frame exposure duration
- [x] Studio multi-track timeline and contextual drawing controls

**Planned**
- [ ] Tiled sparse layer storage (the `TileStore` / `BitmapPool` / LZ4 code exists but isn't wired in yet; layers are still one PNG each)
- [ ] Transparent-background export
- [ ] Ruler variants (circle / ellipse / grid) and a movable mirror axis
- [ ] Stylus gesture shortcuts and custom shortcuts

Have an idea? [Open a feature request](https://github.com/wishissue/WishyClip/issues/new/choose).

## Contributing

Contributions of all kinds are welcome: bug reports, features, brush packs and themes.
Please read [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md) first.
For security issues, see [SECURITY.md](SECURITY.md).

## License & credits

Licensed under the [Apache License 2.0](LICENSE).
Third-party libraries, fonts (Inter, SIL OFL 1.1) and icons are credited in [ATTRIBUTIONS.md](ATTRIBUTIONS.md).
