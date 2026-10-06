# 01 — PROJECT AUDIT

## 1. Executive Summary & Stack
WishyClip is a modern, 100% Kotlin open-source frame-by-frame 2D animation application for Android (minSdk 26, targetSdk 35). It builds with Gradle 8.9, AGP 8.7.3, and Kotlin 2.0.21.

- **UI Framework**: Jetpack Compose + Material 3 (BOM 2024.12.01).
- **Architecture**: MVVM with `AndroidViewModel`, StateFlow, and Compose state observables.
- **Navigation**: Jetpack Navigation Compose (`NavHost`).
- **Database / Metadata**: Room 2.6.1 with KSP annotation processing.
- **Preferences**: Jetpack DataStore (Preferences).
- **Audio Subsystem**: AndroidX Media3 ExoPlayer + `AudioRecord` for voiceovers + custom waveform extraction.
- **Export Pipeline**: Hardware `MediaCodec` + `MediaMuxer` MP4 encoder, custom `AnimatedGifEncoder`, and PNG sequence writer.

---

## 2. Technical Audit Matrix

| Subsystem | Existing Implementation | Architectural Status | Action Required |
| :--- | :--- | :--- | :--- |
| **Framework & Language** | Kotlin 2.0.21, Android SDK 35 | Excellent, modern Kotlin standard | **Preserve** |
| **UI Architecture** | Compose screens & components | Robust, token-driven via `WishyTokens` | **Preserve & Expand** |
| **Navigation** | `WishyNavHost` (home, editor, settings, new_project, export, design_gallery) | Clean, parameter-driven routes | **Preserve & Add Shortcut/Help overlays** |
| **Drawing/Canvas** | `DrawingSurfaceView` (AndroidView) + `StrokeRenderer` with dirty-rect clipping | Sub-25ms ink latency, 11 brushes, mirror, ruler, custom tip brush import | **Preserve core ink engine; enhance overlay controls** |
| **Timeline System** | `TimelineStrip` (LazyRow of `TimelineFrameCell`) | Functional frame switching, add, duplicate, delete | **Replace with Classic vs Studio timeline layout & Frame Hold Exposure model** |
| **Layer System** | `LayerPanel` & `LayerRow`, 7 blend modes, lock, visibility, opacity, merge down | Room DB + PNG per layer | **Preserve data layer; optimize UI panel toggle & density** |
| **State Management** | `EditorViewModel` (~52KB) managing frame cache, undo stack, and tools | Centralized, reactive state | **Preserve state flow; decouple UI workspace layout state** |
| **Theme System** | `WishyTokens`, `WishyTheme`, 5 presets (Light, Dark, AMOLED, Candy, FlipDark), JSON importer | Clean token separation | **Preserve; set dark neutral foundation as primary default** |
| **Icon System** | `WishyIcons.kt` with Cute & Clean packs | Vector drawables in `res/drawable/` | **Standardize on Tabler/Lucide icons system** |
| **Input & Stylus** | Pressure sensitivity, tilt, historical motion batching, palm rejection | Production grade | **Preserve** |
| **Autosave & Recovery** | Debounced 1.5s save, ON_STOP lifecycle sync, safe mutex | Crash-safe | **Add startup project recovery dialog** |
| **Undo / Redo** | `UndoManager` with bounded memory stack (64 MB budget) | Project-aware undo across drawing & layer ops | **Preserve & extend to timeline hold changes** |
| **Export Pipeline** | Foreground `ExportService` (MP4, GIF, PNG sequence) | Fully working | **Preserve** |
| **Testing** | 15 JVM unit test files (20 tests, 100% passing) | High confidence core tests | **Preserve & add workspace/timeline tests** |

---

## 3. What Works & Must Be Preserved
1. **Ultra Low-Latency Ink Rendering**: `DrawingSurfaceView` with unbuffered dispatch and dirty-rect bitmap clipping handles 1080p drawing seamlessly at 60 FPS.
2. **Brush Engine & Custom Importer**: Support for `.wbrush`, `.kpp`, `.abr`, `.gbr`, `.brush` formats.
3. **Advanced Canvas Modifiers**: Mirror symmetry (2-way / 4-way), straight edge ruler, fill with gap tolerance, freeform lasso.
4. **Layer Blending & Lock**: Blending modes (Normal, Multiply, Screen, Overlay, Darken, Lighten, Add), alpha lock, and merge down.
5. **Multi-format Export Engine**: Background service handling MP4, GIF, and PNG zip exports.

---

## 4. What Is Incomplete & Must Be Replaced or Added
1. **Frame Exposure / Hold Duration**: Currently, holding a drawing across frames requires duplicating the bitmap data. Must introduce Frame Exposure duration (holding drawing for $N$ timeline slots without data duplication).
2. **Classic vs Studio Workspace Modes**: Need an explicit workspace mode switcher:
   - **Classic Mode**: Canvas-first, minimal floating toolbar, compact timeline.
   - **Studio Mode**: Expanded multi-track timeline (layers row, audio track, frame duration handles, onion skin controls).
3. **Quick-Adjust Brush Gestures**: Fast slider / drag overlays for Size and Opacity directly accessible on canvas with live pixel previews (e.g. "18 px", "42%").
4. **Shortcuts & Accessibility Overlay**: Keyboard shortcuts map panel (B, E, F, L, T, R, O, Space, Arrow keys, Ctrl+Z) and touch target accessibility improvements.
5. **Unused / Draft Assets Cleanup**: Standardize all icon vectors under `assets/icons/` and map them through `WishyIcons.kt`.

---

## 5. Technical Risks & Mitigations
- **Risk 1: Canvas Space Saturation on Small Screens**
  - *Mitigation*: Collapsible tool rail, auto-hiding panels, and floating context controls that auto-collapse during drawing.
- **Risk 2: Timeline Memory Overhead during Frame Scrubbing**
  - *Mitigation*: Maintain bounded $\pm 2$ frame bitmap cache in `EditorViewModel`, rendering lightweight thumbnail cached downsamples for timeline cells.
- **Risk 3: Audio/Video Sync Drift during Playback**
  - *Mitigation*: Decouple audio playback clock (ExoPlayer) from frame rendering loop using hardware frame timestamp synchronization.
