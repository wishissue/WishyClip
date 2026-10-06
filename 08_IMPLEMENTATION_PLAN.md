# 08 — IMPLEMENTATION PLAN & ARCHITECTURE

## 1. Architecture Overview
The implementation follows MVVM with clean separation between UI components (`ui/components/`), reactive ViewModels (`ui/EditorViewModel.kt`), drawing canvas hardware engine (`canvas/`), and persistence repositories (`data/`).

---

## 2. Phased Implementation Roadmap

### Phase 1: Design Tokens & Asset Standardization
- Map all vector icons under `assets/icons/` into `WishyIcons.kt`.
- Apply `FlipDarkTokens` dark neutral foundation (`#161618` surface, `#FF5252` coral accent).
- Verify minimum touch target size ($48 \times 48\text{dp}$) across all reusable buttons (`ToolButton.kt`, `ActionIconButton.kt`).

### Phase 2: App Shell & Workspace Layout Modes
- Update `EditorScreen.kt` to support **Classic Mode** (canvas-first, compact timeline) vs **Studio Mode** (expanded multi-layer timeline & audio lanes).
- Ensure smooth layout adaptation for landscape, portrait, and left-handed mode toggles.

### Phase 3: Contextual Tools & Quick Adjustments
- Implement floating numerical value bubble overlay (`"18 px"`, `"42%"`) for quick brush size/opacity dragging.
- Refine Eyedropper magnifier lens overlay on canvas.

### Phase 4: Timeline Frame Exposure Duration Engine
- Extend `FrameEntity` schema / in-memory model to include `exposureDuration: Int` (default $1$).
- Update `TimelineStrip.kt` cell rendering to display duration badges (`x2`, `x5`).
- Update `EditorViewModel` playback coroutine loop to respect frame exposure duration counts.

### Phase 5: Layer Management & Controls
- Enhance `LayerPanel.kt` and `LayerRow.kt` with quick lock toggle, visibility toggle, opacity slider, blend mode picker, and merge down action.

### Phase 6: Keyboard Shortcuts & Accessibility
- Wire complete keyboard event listener in `EditorScreen.kt` (B, E, F, L, T, R, M, O, Space, Arrow keys, Ctrl+Z, Ctrl+Y/Shift+Z).
- Create `ShortcutPanel` help dialog displaying keyboard controls.

### Phase 7: Crash Recovery & Journal Check
- Add startup recovery check in `WishyApp` / `HomeScreen` for orphaned temporary project state.
- Display `ProjectRecoveryDialog` when crash state detected.

---

## 3. Targeted File Modification List

1. [`WishyTokens.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/design/WishyTokens.kt) — Token values update (coral accent `#FF5252`, dark surface `#161618`).
2. [`WishyIcons.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/design/WishyIcons.kt) — Vector drawable mapping cleanup.
3. [`EditorScreen.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/screens/EditorScreen.kt) — Workspace layout modes, keyboard event handling, quick gesture overlays.
4. [`EditorViewModel.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/EditorViewModel.kt) — Frame exposure duration handling, undo stack updates, crash recovery checks.
5. [`TimelineStrip.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/components/TimelineStrip.kt) — Exposure duration badges, Studio mode expansion.
6. [`TimelineFrameCell.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/components/TimelineFrameCell.kt) — Frame cell hold width & exposure indicator.
7. [`LayerPanel.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/components/LayerPanel.kt) — Panel UI density & lock/blend actions.
8. [`BrushMenu.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/components/BrushMenu.kt) — Quick size/opacity/stabilizer sliders & preview.
9. [`WishyDialog.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/components/WishyDialog.kt) — Recovery & Shortcut dialogs.
10. [`strings.xml`](file:///home/denji/Documents/new/WishyClip/app/src/main/res/values/strings.xml) — Localization strings for tooltips and accessibility.
