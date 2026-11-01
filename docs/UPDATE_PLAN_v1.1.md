# WishyClip v1.1 "Stable" update plan

Status: Phase 1 steps 1, 4, 5 are implemented on branch `wishissue-redesign` (sparse layers, memory budget, layer cap, New project estimate). Still unbuilt and untested on a device. Phase 3 part 1 (16 original starter brushes) is implemented; the pack compiles and passes its checks on a plain JVM, tips were rendered and inspected. Phase 2 partly done (tested pacing clock, uncancelled look-ahead loader); Phase 4 partly done (OOM guard on stroke start, loader race). NOT done, by design of risk: tiled storage, composite cache (likely unnecessary: the lag came from memory, and inactive layer textures are cheap to draw, but this is unmeasured), (project-wide layers are done as mirrored per-frame stacks, see CHANGELOG; a true single-table layer model would still need a migration); pressure/grain dynamics in the dab engine; Procreate grain/dynamics import (needs binary-plist parsing and real sample files we may legally use); moving the remaining strings (text built from variables, a few dialog bodies) to resources; redesign of the layers panel / brush menu / export dialog; instrumented and performance tests.
Trigger: user review (layers lag at ~21 layers; wants more/better brushes, e.g. Procreate-style pencils and inks) plus a code audit.

Evidence labels used below:
- **[Verified]** read directly in the source.
- **[Reported]** from a user review, not reproduced by us.
- **[Suspected]** follows from the code but needs profiling on a device.
- **[Fixed on branch]** fixed in branch `wishissue-redesign`, still unbuilt and untested.

## 1. Focus areas

1. Layer performance and memory (the review's main complaint)
2. Playback and 60 fps
3. Brush quality and quantity
4. Stability, data safety, crash handling
5. UI redesign completion and accessibility
6. Build, test and release pipeline

## 2. Problems in the current version

### A. Layers, memory, performance (highest priority)
| # | Problem | Evidence |
|---|---|---|
| A1 | Every layer is a full-canvas `ARGB_8888` bitmap, even when empty. 1920x1080 = 7.9 MiB per layer. | [Verified] `addLayer` allocates `Bitmap.createBitmap(w, h)` immediately |
| A2 | Frame cache keeps the current frame plus/minus 2 (more if onion settings are larger). 21 layers x 5 frames x 7.9 MiB is about 830 MiB. | [Verified] `trimCache`, `prefetch`; arithmetic is ours |
| A3 | No `largeHeap` and no memory budget or cap on layer count, so heavy projects can lag then crash with OutOfMemory. | [Verified] manifest has no `largeHeap`; no cap found |
| A4 | Each redraw composites every visible layer at full size, plus every onion-skin ghost layer. Cost grows with layer count. | [Verified] per-layer `drawBitmap` loops in `DrawingCanvas.kt`, `OnionSkin.kt` |
| A5 | `TileStore` (256px tiles, LZ4) exists but nothing uses it. Layers are never sparse. | [Verified] no references outside its own file |
| A6 | (Fixed on branch: layer stacks are mirrored across frames.) Adding a layer only touched the current frame. New frames copy the layer list, but I found no code that adds a layer to existing frames, so layer structure can differ per frame. | [Verified] `addLayer` uses only the current `FrameData`; [Suspected] for the wider behaviour |
| A7 | The New project memory estimate multiplies by 5 frames but assumes one layer, so it understates real use. | [Verified] `estBytes = w * h * 4 * 5` |
| A8 | Saving evicted frames and closing the editor do disk work on the main/viewModel path (`runBlocking` in `onCleared`). Mostly cheap after the stop flush, but a stall risk on big projects. | [Verified] code; [Suspected] impact |
| A9 | Reported lag from 21 layers with no drawings. | [Reported]; consistent with A1 to A4 |

### B. Playback and fps
| # | Problem | Evidence |
|---|---|---|
| B1 | fps goes to 60 (a selling point), but each tick composites all layers, so 60 fps is unlikely to hold with many layers. | [Suspected]; needs profiling |
| B2 | Timecode and audio playhead ignored held frames. | [Fixed on branch] |

### C. Brushes
| # | Problem | Evidence |
|---|---|---|
| C1 | Only 11 brushes plus eraser are built in. | [Verified] `BRUSHES.md` |
| C2 | Procreate `.brush` import reads only `Shape.png`. Grain textures, dynamics and plist parameters are ignored, so imported brushes look flat. Krita and Photoshop imports are similarly partial. | [Verified] `BRUSHES.md` |
| C3 | Users asked for brushes like those James Julier uses. Those are his commercial Procreate packs; we cannot copy or bundle them (see section 6). | [Reported] |

### D. Bugs and gaps already found
Fixed on branch (unbuilt): palm rejection and haptics toggles did nothing; advertised keyboard shortcuts missing; no delete-project confirmation; "WishaClip" typo in UI; screens drawn under system bars; onboarding page lost on rotation; white flash in dark mode; canvas painted over themed backdrop; double-tap tool buttons unusable with TalkBack; sliders too small and not accessible; icon pack not reactive; accidental double-tap undo during playback; dead legacy files and an orphan export route.

Still open:
| # | Problem |
|---|---|
| D1 | Many UI strings are hardcoded English (not in `strings.xml`), so no translation is possible. |
| D2 | Export folder, README and release asset names still say "WishaClip". Changing the export folder moves where users find old exports, so it needs a migration decision. |
| D3 | Color picker, layers panel, brush menu and export service have not been audited. |
| D4 | The app has never been built or run by us. First build may reveal type errors in the redesign branch. |
| D5 | Palm rejection is stylus/mouse-only drawing; that semantic is our interpretation. |
| D6 | No performance, memory or crash test exists for large projects. |

## 3. Plan (execute in this order; each phase ships behind a green build)

### Phase 0: Baseline (before any feature work)
- Build the redesign branch; fix compile errors; run unit tests.
- Add a debug-only "stress project" generator: 1080p, 12 / 24 / 60 fps, 1 / 21 / 64 layers, 50 frames.
- Record baselines on at least one low-end and one mid-range device: heap use, draw time per frame, playback dropped frames, time to open project.
- Gate: app builds, opens, draws, exports on device.

### Phase 1: Layer engine
1. **Sparse layers:** an empty layer allocates nothing; a bitmap/tiles appear on first paint. The review's exact case (21 empty layers) becomes about zero extra memory.
2. **Tiled storage:** wire in `TileStore` so only touched 256px tiles are held; compress unused tiles.
3. **Composite cache:** flatten "layers below active" and "layers above active" into cached bitmaps while drawing, so drawing cost does not depend on layer count. Rebuild only when a non-active layer changes.
4. **Memory budget:** derive cache window and onion range from available heap; shrink under pressure (`onTrimMemory`); never recycle a bitmap that is still being drawn.
5. **Layer cap and warnings:** soft cap with a clear message based on estimated memory, not a silent crash. Fix the New project estimate to include layers.
6. **Layer model decision (needs your call):** keep per-frame layers (current) or move to project-wide layers with per-frame cels, like FlipaClip. The second is better for animators but needs a database and file migration with rollback.
- Gate: stress project (21 layers, 1080p) stays within the heap budget and draw time target below; no data loss in migration tests.

### Phase 2: Playback and 60 fps
- Background pre-flatten of frames into a playback cache; frame pacing with Choreographer; drop to cached frames when the cache is cold.
- Gate: measured dropped frames at 60 fps on the stress project; if a device cannot hold 60, say so in the UI instead of stuttering.

### Phase 3: Brushes
1. **Original brush pack (about 25 to 30 new brushes), made by us:** graphite pencils (HB, 6B, soft, textured), technical pen, G-pen/ink with pressure taper, brush pen, dry ink, charcoal, conte, gouache, oil flat, spray, splatter, texture and fill brushes. Each with pressure, speed and tilt dynamics and an original grain texture.
2. **Better imports:** Procreate `.brush` grain and basic dynamics; Krita/ABR dynamics where feasible. Users can import packs they own.
3. **Brush UI:** categories, live stroke preview, favourites, per-brush saved size and opacity, stabilization setting.
- Gate: each brush renders correctly at 60 fps input on mid-range device; import tests with sample files we are allowed to use.

### Phase 4: Stability
- Atomic file writes, crash-safe autosave, recovery of last session after a crash.
- Catch `OutOfMemoryError` around bitmap allocation and degrade gracefully.
- Remove main-thread blocking; lifecycle tests (rotate, background, low memory, process death).
- Room migration tests; export tests (GIF, MP4, audio) on large projects.

### Phase 5: UI completion
- Redesign color picker, layers panel, brush menu, export dialog to the WishIssue guidelines (flat surfaces, blue primary, Plus Jakarta Sans, 48dp targets).
- Move all strings to resources; TalkBack pass; 200% font scale; landscape, tablet, foldable; contrast check.

### Phase 6: Release
- CI that builds and runs tests on every change; internal then closed beta; fix list from beta; version bump; changelog; store notes.
- Respect `PRIVACY.md`: no analytics or crash reporting is added without your explicit decision.

## 4. Targets (goals, not measurements)
- 21 empty layers: under 20 MiB extra heap.
- 21 painted layers at 1080p: stays inside heap budget on a mid-range device without OOM; drawing latency does not rise with layer count.
- 60 fps playback on the stress project on a mid-range device, or a clear in-app note.
- Zero data loss across the migration and crash-recovery tests.

## 5. Decisions needed from the owner
1. Per-frame layers or project-wide layers (Phase 1.6)?
2. Which devices and Android versions must work?
3. Crash reporting: yes or no?
4. Export folder rename: migrate old exports or keep `WishaClip`?
5. Brush direction: confirm original-pack approach (section 6).

## 6. Brush licensing note
James Julier's brushes are his own commercial work. We will not copy, rebuild one-to-one, or bundle them. We can make original brushes with a similar feel (grainy graphite, tapered inks), and improve import so people can load packs they legitimately own. If you want his brushes bundled, ask him for permission in writing.

## 7. Risks
- Layer model migration can corrupt projects: back up before migrating, test on copies.
- Tiling and composite caches add complexity; wrong invalidation shows stale pixels.
- All timings above are unmeasured until Phase 0.
