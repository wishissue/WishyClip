# FlipaClip vs. WishyClip Gap Analysis

- **Reference App**: FlipaClip (v3.x / Android reference)
- **Tested / Analyzed Date**: October 2024 / Current Development Iteration
- **Scope**: Behavioral, architectural, and capability gap analysis between FlipaClip and WishyClip (open-source Jetpack Compose / Kotlin 2D animation studio).

---

## 1. Drawing Tools and Brushes

| FlipaClip | WishyClip | Status | Effort |
| :--- | :--- | :--- | :--- |
| Variety of brush types (Pen, Pencil, Highlighter, Eraser, Water, Smudge, Blur) | 11 brushes (Pen, Pencil, Marker, Airbrush, Calligraphy, Highlighter, Charcoal, Ink, Watercolor, Chalk, Pixel) + custom brush importer (.wbrush, .abr, .kpp) | **Better than FlipaClip** | M |
| Brush size & opacity sliders with numeric feedback | Quick sliders & contextual size/opacity controls in `ContextToolPanel` & `BrushMenu` | **Has parity** | S |
| Stroke stabilization / smoothing | Adjustable stabilizer slider (0% to 100%) in `BrushMenu` | **Has parity** | S |
| Eraser with adjustable size and soft/hard edge | Eraser tool with variable size up to 120px | **Has parity** | S |
| Fill bucket with adjustable threshold/gap tolerance | Fill tool with adjustable gap tolerance (0-255) | **Has parity** | S |
| Lasso selection & transform (move, scale, rotate, flip) | Lasso tool with translation, 8-handle scaling, rotation stalk, and matrix baking | **Has parity** | M |
| Shape tools (line, rectangle, circle/oval) | Dedicated line, rectangle, and ellipse shape tools | **Has parity** | S |
| Text tool with font styles and sizing | Text tool with font scaling & placement | **Has parity** | S |
| Eyedropper tool | Eyedropper tool with auto-return | **Has parity** | S |
| Straight edge ruler / perspective guides | Straight edge adjustable ruler (`RulerState`) with snapping | **Has parity** | M |
| Mirror symmetry (horizontal & vertical axis) | 2-way and 4-way mirror symmetry modes | **Has parity** | M |
| Smudge / Blur / Blend brushes | Layer blend modes & custom brushes, but no direct pixel smudge tool | **Partial** | L |

---

## 2. Color System

| FlipaClip | WishyClip | Status | Effort |
| :--- | :--- | :--- | :--- |
| HSV color picker wheel & sliders | Custom floating popover color picker with 2D Saturation/Value canvas & Hue bar | **Has parity** | M |
| Saved palettes & preset swatches | Preset color swatches & custom recent/saved colors | **Has parity** | S |
| Recent color history | Color comparison preview (Initial vs Current) & Hex input | **Has parity** | S |
| Eyedropper color sampling | Eyedropper tool sampling composite visible pixels | **Has parity** | S |
| Color opacity control | Opacity slider on brush & color picker | **Has parity** | S |

---

## 3. Layer System

| FlipaClip | WishyClip | Status | Effort |
| :--- | :--- | :--- | :--- |
| Multiple layers (freemium limit up to 3 or 10+) | Unlimited or unconstrained layers per frame backed by Room & PNG store | **Better than FlipaClip** | M |
| Layer opacity & visibility toggle | Per-layer opacity slider, visibility toggle, and lock toggle | **Has parity** | S |
| Layer blend modes | 7 blend modes (Normal, Multiply, Screen, Overlay, Darken, Lighten, Add) | **Has parity** | M |
| Layer reordering (move up / move down) | Up/down action buttons in layer row | **Has parity** | S |
| Merge down / flatten layers | Merge down layer operation | **Has parity** | S |
| Import image as layer | Import image as layer (`image/*`) | **Has parity** | S |
| Alpha lock / protect transparency | Alpha lock per layer | **Missing** | M |
| Duplicate layer | Duplicate layer operation | **Missing** | S |

---

## 4. Timeline and Animation

| FlipaClip | WishyClip | Status | Effort |
| :--- | :--- | :--- | :--- |
| Horizontal frame strip with thumbnails | `TimelineStrip` with frame cells, index numbers, and active indicator | **Has parity** | S |
| Onion skinning with adjustable before/after frames and opacity | Onion skin settings (frames before/after, opacity, custom tint colors) | **Has parity** | M |
| Add, duplicate, delete frames | Add frame, duplicate frame, delete/clear frame | **Has parity** | S |
| Copy and paste frames | Copy and paste frame operations | **Has parity** | S |
| Frame hold / exposure duration (holding drawing across multiple frames) | Frame exposure duration metadata on frames | **Partial** | L |
| Frame reordering by drag-and-drop | Frame reordering via buttons/copy-paste (no direct drag reorder on strip yet) | **Partial** | M |
| Adjustable frame rate (FPS from 1 to 30/60) | Adjustable project FPS (1 to 60 FPS) | **Has parity** | S |
| Playback controls (play, pause, loop, scrub) | Play, pause, skip forward/back, and timeline scrubbing | **Has parity** | S |

---

## 5. Audio Subsystem

| FlipaClip | WishyClip | Status | Effort |
| :--- | :--- | :--- | :--- |
| Multiple audio tracks | Multiple audio tracks (`AudioTrackEntity`) with start frame offset | **Has parity** | M |
| Audio recording (voiceover) | Audio voiceover recording (`AudioRecord`) | **Has parity** | M |
| Audio trimming and volume control | Audio track management & volume | **Partial** | M |
| Audio waveform visualization on timeline | Basic track row without detailed amplitude waveform rendering | **Partial** | L |

---

## 6. Canvas & Viewport

| FlipaClip | WishyClip | Status | Effort |
| :--- | :--- | :--- | :--- |
| Pinch zoom and pan | Pinch-to-zoom (0.2x to 8x) and smooth panning via `CanvasViewState` | **Has parity** | S |
| Canvas rotation | Free canvas rotation & quick 90° rotation buttons | **Has parity** | S |
| Reset view | Reset view button with live rotation angle display | **Better than FlipaClip** | S |
| Fullscreen / Hide UI mode | Hide interface mode with floating restore button | **Better than FlipaClip** | S |
| Gestures (two-finger tap to undo) | Two-finger tap quick undo gesture in editor | **Has parity** | S |

---

## 7. Interaction Model & Workspace

| FlipaClip | WishyClip | Status | Effort |
| :--- | :--- | :--- | :--- |
| Tool options popup / double-tap | Double-tap tool button to open options; single tap selects tool | **Better than FlipaClip** | S |
| Menu dismissal on outside tap | Outside-tap scrim dismissing open menus | **Has parity** | S |
| Draggable floating palettes / docks | `Modifier.draggablePanel()` on tool rail, context bar, brush menu, layers panel, and timeline | **Better than FlipaClip** | M |
| Left-handed mode | Left-handed mode toggle (mirrors tool rail position) | **Has parity** | S |
| Portrait vs. Landscape layout | Adaptive layout (vertical dock in landscape, horizontal capsule in portrait) | **Has parity** | M |

---

## 8. Import & Export

| FlipaClip | WishyClip | Status | Effort |
| :--- | :--- | :--- | :--- |
| MP4 video export | MP4 video export (MediaCodec + H.264 + Audio muxing) | **Has parity** | M |
| Animated GIF export | Animated GIF export (`AnimatedGifEncoder`) | **Has parity** | M |
| PNG sequence export (ZIP) | PNG sequence export (ZIP archive) | **Has parity** | M |
| Image/video import as frames | Import image sequence and video file as frames | **Has parity** | M |
| Project backup / restore (`.fcproject` equivalent) | SQLite Room database storage with autosave & crash recovery | **Has parity** | M |
| Transparent background export | Transparent background export option | **Missing** | M |

---

## 9. Projects Screen

| FlipaClip | WishyClip | Status | Effort |
| :--- | :--- | :--- | :--- |
| Project grid with thumbnails | Grid of project cards with live generated thumbnails via Coil | **Has parity** | S |
| Rename project | Rename project dialog from card options | **Has parity** | S |
| Duplicate project | Project duplication | **Missing** | S |
| Folders / organization | Project folder grouping | **Missing** | M |
| Canvas size presets | Canvas size presets (Square, Landscape, Portrait, Custom) | **Has parity** | S |

---

## 10. Look and Feel

| FlipaClip | WishyClip | Status | Effort |
| :--- | :--- | :--- | :--- |
| Custom dark art studio UI | Studio Ink (`FlipDarkTokens`), Cloud, Midnight, AMOLED, Candy themes with `GlassSurface` frosted cards | **Better than FlipaClip** | M |
| Consistent icon set | Phosphor Icons (bold weight) standardized across all UI controls | **Has parity** | S |
| Typography | Inter font family with iOS-style large titles | **Has parity** | S |
| Zero Material Design stock look | Custom flat components, hair-line borders, custom sliders, custom dialogs | **Better than FlipaClip** | M |

---

## 11. Performance & Stability

| FlipaClip | WishyClip | Status | Effort |
| :--- | :--- | :--- | :--- |
| High performance ink rendering | Low-latency ink rendering (`DrawingSurfaceView` + dirty rect clipping at 60 FPS) | **Has parity** | S |
| Frame memory caching | Bounded $\pm 2$ frame bitmap memory cache with disk persistence | **Has parity** | M |
| Undo/redo depth | Bounded memory undo stack (64MB budget) with cross-frame support | **Has parity** | M |
| Autosave & crash recovery | 1.5s debounced autosave and orphan checkpoint recovery dialog | **Better than FlipaClip** | M |

---

## Top 10 Gaps (Ranked by Priority & Size)

1. **Audio Waveform Rendering**: Visual amplitude waveform display on the audio timeline. (Size: **M**)
2. **Frame Exposure / Hold Duration**: Setting a single drawing to hold across multiple frames without bitmap duplication. (Size: **L**)
3. **Layer Alpha Lock & Duplicate Layer**: Protecting transparency and duplicating layers directly from the layer row. (Size: **S**)
4. **Timeline Drag-and-Drop Reordering**: Direct drag reordering of frames on the timeline strip. (Size: **M**)
5. **Project Duplication & Folders**: Ability to duplicate existing projects and organize them into folders on the home screen. (Size: **M**)
6. **Transparent Background Export**: Option to export MP4/GIF/PNG with transparency. (Size: **M**)
7. **Vector Shape Edit Handles**: Post-creation transformation handles for lines, rectangles, and ellipses. (Size: **L**)
8. **Smudge / Blur Pixel Tool**: Direct pixel distortion/smudge tool for raster artwork. (Size: **L**)
9. **Project Cloud Backup / Export (.wishy format)**: Single-file archive export/import for sharing projects. (Size: **M**)
10. **Advanced Keyframing / Motion Tweens**: Basic object motion tweening between keyframes. (Size: **L**)

---

## Things WishyClip Does That FlipaClip Doesn't

1. **Draggable Floating Palettes**: Every floating panel (tool rail, context bar, brush menu, layers panel, timeline) can be long-press dragged anywhere on screen and persists its position.
2. **Custom Brush Importer**: Native support for importing external brush packs (`.wbrush`, `.abr`, `.kpp`, `.gbr`).
3. **Multi-Theme Studio Architecture**: Comprehensive design system tokens with 7 built-in themes (Studio Ink, Cloud, Midnight, AMOLED, Candy, Light, Dark) and runtime JSON theme importing.
4. **4-Way Mirror Symmetry**: Advanced symmetrical drawing modes beyond simple horizontal/vertical splitting.
5. **Robust Crash Recovery**: Automatic checkpoint journal scanning and recovery dialog on startup.

---

## Recommended Order of Work (Updated Roadmap)

1. **Phase 1: Layer Enhancements** (Alpha lock, duplicate layer).
2. **Phase 2: Timeline & Frame Exposure** (Frame hold exposure duration, drag-and-drop frame reordering).
3. **Phase 3: Audio Waveform & Trimming** (Visual waveform rendering on timeline).
4. **Phase 4: Project Management** (Project duplication, folders, single-file archive export).
5. **Phase 5: Advanced Editing & Export** (Transparent background export, vector shape handles, smudge brush).
