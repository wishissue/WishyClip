# Redesign notes

Status of the Orion-inspired redesign. **Nothing here has been compiled or run on a device**; the
authoring environment had no Android SDK/Gradle. Changed Kotlin files were checked only for balanced
brackets and for unresolved icon / string / token names. Build and test locally first
(`./gradlew :app:testDebugUnitTest :app:assembleDebug`).

## What Orion actually is (verified in source)
- Orion's colour system is generated, not hand-written (`tools/` + Palettes). Its relationship is a
  saturated primary with a contrasting accent and quiet tinted neutrals. WishyClip's palette
  (`themes/Indigo.kt`) re-derives that relationship with `material-color-utilities`; no Orion file was copied.
- Orion icons are generated Material Symbols Rounded vectors. WishyClip does the same with
  `tools/gen-symbols.mjs` (outlined + filled pair per icon, filled used for selected states).
- Orion is GPL-3.0, WishyClip Apache-2.0. Only design relationships were used, so no GPL code is in the tree.

## Done
| Area | Change |
|---|---|
| Icons | ~120 Material Symbols Rounded drawables (`ic_*.xml`, `ic_*_filled.xml`), `SelectedIcons.kt`, `WishyIcons.selected()`. Icon visuals 22dp inside the existing 44-48dp touch targets. Tabler/Lucide/Phosphor no longer used. |
| Theme | New default family: Indigo, Indigo Dark, Indigo Dusk, Indigo OLED (`tools/gen-themes.mjs`). Old themes remain selectable. Test added for paper/stage contrast. |
| Timeline | `TimelineMode` MINIMIZED / COMPACT / EXPANDED. Drag the handle (or tap) to change size. Landscape COMPACT is a single row (transport, counter, frames, actions). EXPANDED height is capped (28% portrait / 30% landscape) and scrolls. Default size is a setting. |
| Chrome | Top bar is flat 48dp (was a 56dp floating capsule with margins). Frame cells 56x46dp. Studio toggle moved from the top bar into the timeline. |
| Settings | Rebuilt as 4 categories (Appearance, Workspace, Pen and touch, About). Phone: opened category expands, others collapse to compact rows. Width >= 720dp: list + detail. Live previews: workspace sketch (theme, handedness, timeline size), theme swatches, a pen/finger pad showing real pointer type and pressure. |
| Removed settings | **Icon pack** (the two packs resolved to the same drawables, so it did nothing) and its DataStore key accessor. |
| Branding | Home wordmark and the Bagel Fat One font removed. Splash (Android 12+) is background colour only (`values-v31/themes.xml`, `splash_blank.xml`). |
| Layers | Blend mode is now picked directly from a chip row for the selected layer (was a menu item that cycled). Locked layers show a lock icon on the row. Proper grip icon on the drag handle, icons on Add layer / Import image, list cap lowered so the footer always fits. Thumbnails, visibility, opacity, reorder, rename, duplicate, merge and delete were already working and are unchanged. |
| Tool bar | `ContextToolPanel` already showed only what the active tool needs (brush / eraser / fill / text / shapes / selection). Changes: eraser and shape size now expand inline into a slider in the bar (the old `onSizeChange` parameter was never used); Lasso and Eyedropper no longer show an irrelevant colour swatch and instead say what to do. Brush menu, options popover and colour picker are unchanged. |
| Motion | `WishyMotion` (spring specs, snap when system animations are off). Used by settings and the timeline. The canvas is not animated. |
| Image import | Picked images land as a floating picture. While placing, the bar shows Place, Fit to canvas, Fill, Rotate 90°, Reset shape and Cancel (all real: they drive the same transform used for commit). Aspect is kept by Fit/Fill; Cancel adds no layer changes. |
| Attribution | `ATTRIBUTIONS.md`, `README.md` updated. |

## Not done (be aware)
These parts of the brief are **not** implemented in this pass; I did not want to ship them unverified:
- A full layer-panel rewrite: only the refinements in the table above were made.
- True crop for imported images: the floating-selection model has no crop, so no crop control was added (nothing fake). Drag/scale/rotate on canvas works as before.
- New colour picker and long-press / gesture shortcuts: `BrushMenu`, `ColorPickerSheet` and the options popover are untouched apart from icons and colours.
- Canvas "reset view" and fit already exist (`CanvasViewState.reset`); not changed.
- Export/Storage/Advanced settings categories were not added; only categories with working settings exist.
- Adaptive `WindowSizeClass` is not used; settings uses a 720dp width check.
- Phase 6 audit of every tool for half-built features was not completed.

## Things to check on a device
1. Timeline handle: swipe up/down and tap; EXPANDED scrolling inside the cap; landscape single row.
2. Filled/outlined icon swap on the tool rail and action buttons; icons are white-filled and rely on `tint`.
3. Settings: rotate while a category is open (state is saved); the Follow-system switch turning off keeps the shown theme.
4. Splash on Android 12+ (blank, light/dark); pre-12 shows the window background.
5. `./gradlew test` (theme tests were edited).
