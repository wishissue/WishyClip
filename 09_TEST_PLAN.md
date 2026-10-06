# 09 — TEST PLAN & VERIFICATION MATRIX

## 1. Automated Test Strategy

All core logic (drawing undo/redo, frame reordering, project persistence, brush file parsing, layer blending, and audio waveform extraction) is tested via JVM Unit Tests using JUnit4, Robolectric, and Kotlin Coroutines Test.

Run tests via Gradle:
```bash
./gradlew app:testDebugUnitTest
```

---

## 2. Test Cases & Verification Matrix

| Test Suite | Test File | Target Functionality | Verification Criteria |
| :--- | :--- | :--- | :--- |
| **Undo Manager** | `UndoManagerTest.kt` | Push/pop undo stack, memory budget recycling, frame/layer dropping | 100% pass; verify bitmap `recycle()` calls |
| **Frame Ordering & Hold** | `FrameOrderingTest.kt` | Frame CRUD, position continuity, exposure hold count | 100% pass; verify position sequence remains dense |
| **Project Persistence** | `ProjectPersistenceTest.kt` | Room DB save/load, PNG layer bitmap write/read roundtrip | 100% pass; layer PNG files match pixel data |
| **Brush Parsing** | `BrushImportTest.kt` | `.wbrush`, `.kpp`, `.abr`, `.gbr`, `.brush` parser limits | 100% pass; tip bitmaps loaded without crash |
| **Layer Blending** | `LayerBlendingTest.kt` | 7 blend modes (Multiply, Screen, Overlay, etc.) | Pixel color math equals expected porter-duff blend |
| **Fill & Lasso** | `FillToolTest.kt`, `LassoAndShapesTest.kt` | Flood fill tolerance, bounding box transform | Region fill bounded by line art; lasso mask accurate |
| **Mirror & Ruler** | `MirrorAndDabTest.kt`, `RulerStateTest.kt` | 2-way/4-way symmetry, ruler snapping | Symmetric stroke coordinates generated accurately |
| **Exporter** | `ExporterTest.kt` | MP4, GIF, PNG zip encoders | Encoded media output exists and is non-empty |

---

## 3. Manual UI & Performance Verification Checklist
- [ ] **Compilation & Build**: `./gradlew assembleDebug` completes with 0 errors.
- [ ] **Unit Tests**: All JVM unit tests pass (`0 failures`).
- [ ] **Input-to-Ink Latency**: Drawing feel remains crisp and sub-25ms.
- [ ] **Screen Layouts**: Verified in Phone Portrait, Phone Landscape, and Tablet views.
- [ ] **Left-Handed Mode**: Tool rail moves to right side without clipping or UI overlapping.
- [ ] **Classic vs Studio Modes**: Timeline expands smoothly between compact strip and studio view.
- [ ] **Frame Exposure**: Holding drawing for 5 frames plays back smoothly at target FPS.
- [ ] **Keyboard Shortcuts**: B, E, F, L, T, R, M, O, Space, Arrow keys, Ctrl+Z, Ctrl+Shift+Z function as expected.
- [ ] **Crash Recovery**: Discarding or recovering orphaned project journal state functions safely.
- [ ] **License & Trademark Safety**: Zero copyrighted FlipaClip assets, logos, or icons present in codebase; all third-party vector icons recorded in `assets/ASSET_MANIFEST.md`.
