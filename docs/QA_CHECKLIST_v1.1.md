# v1.1 device QA checklist

Nothing in this release has been run on a device yet. Work through this on a real phone (and a low-end one if you can) before publishing. Use a COPY of a real project for the layer items.

## 0. Build
- [ ] `./gradlew assembleDebug` succeeds (or the CI "Build" job is green on branch `wishissue-redesign`).
- [ ] `./gradlew test` passes (new: LayerDataTest, StarterBrushesTest, PlaybackClockTest, WishyThemesTest).

## 1. Layers (review complaint)
- [ ] New project 1920x1080, add 21 layers, draw nothing: app stays smooth, memory (Android Studio profiler) barely moves.
- [ ] Paint on 3 layers, flip through 20 frames: no lag spikes, no crash.
- [ ] Add 64 layers: the 65th shows "Layer limit reached".
- [ ] Add a layer on frame 5: it appears on frames 1 and 20 too.
- [ ] Hide, lock, rename, change opacity on frame 2: same on frame 9 (after about half a second).
- [ ] Reorder, delete, merge down on frame 3: same stack on other frames; drawings on other frames merge/delete as expected.
- [ ] Delete and merge ask for confirmation.
- [ ] Hide a layer on frame 2 and immediately leave the editor / start an export: reopen, other frames show it hidden too.
- [ ] Export a 100-frame project with 21 layers: noticeably faster than before; exported look matches the canvas.
- [ ] Duplicate a painted layer, switch frame and back, close and reopen the app: the copy still has its pixels.
- [ ] Open an OLD project (made before this update): it still opens; layer edits behave sensibly (frames with a different layer count are skipped).
- [ ] Undo after drawing on a layer that was empty a second ago.

## 2. Playback
- [ ] 24 fps, 60 fps, 12 fps: time a 30 second playback with a stopwatch; it should match within about 1%.
- [ ] Project with 100+ frames at 60 fps: note dropped frames; compare with the old build if you have one.
- [ ] Stop and start playback repeatedly; background the app during playback.

## 3. Brushes
- [ ] First launch after update: 16 new brushes appear (Graphite HB ... Spray grain).
- [ ] Each brush paints without crash at small and large sizes.
- [ ] Pencils get darker with pressure (stylus); Technical pen stays solid.
- [ ] Delete a starter brush, restart: it does not come back.
- [ ] Import a Procreate / ABR brush you own: still works (shape only).

## 4. Settings and input
- [ ] Palm rejection on: fingers only pan/zoom, stylus draws. Off: fingers draw.
- [ ] Haptic feedback off: no vibration on taps.
- [ ] Hardware keyboard: B E F L T R M O, Space, arrows, Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y.
- [ ] Theme: first launch follows system light/dark; "Follow system light/dark" returns to that; picking Cloud etc. sticks.
- [ ] Icon pack switch updates icons immediately.

## 5. Look and accessibility
- [ ] Light and dark: no white flash on launch in dark mode; text readable everywhere.
- [ ] Status bar and gesture bar do not overlap Settings, New project, Gallery, Onboarding.
- [ ] TalkBack: tool buttons (incl. eraser/lasso/fill/text/shapes), sliders, layer rows are reachable and named.
- [ ] Font size 200%: no clipped buttons in dialogs.
- [ ] Rotate the phone in the editor, onboarding, and a dialog.
- [ ] Delete project asks first.
- [ ] Back with a dialog / color picker / brush menu / layers panel open closes it; Back with nothing open leaves the editor and saves.
- [ ] New project: custom size 0, 99999, letters: clamped to 64..4096; tapping Create twice makes one project.

## 6. Stability
- [ ] Low-memory device or emulator with a small heap: drawing on a big canvas shows the "Not enough memory" message instead of crashing.
- [ ] Kill the app during a stroke and after drawing: reopen, last saved work is there.
- [ ] Export GIF and MP4 of a project with many layers.
- [ ] Export a 24 fps GIF of a 10 second animation: it lasts 10 seconds (not 9.6).
