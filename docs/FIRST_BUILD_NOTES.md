# First build notes (v1.1.0)

This work was written without being able to run Gradle or a device, so the first compile may show errors. Everything is on branch `wishissue-redesign`; `main` is untouched.

## Get it
- Push the branch (or `git am wishyclip-redesign.patch` on a clean checkout of the original commit `14994ae`).
- CI (`.github/workflows/ci.yml`) runs on this branch and can be started by hand from the Actions tab.
- Locally: `./gradlew assembleDebug` then `./gradlew test`.

## What WAS checked before hand-off
- All Kotlin parses (no syntax errors) and braces balance.
- The Android-only sources (`canvas/`, `brush/`, `model/`, `data/BitmapStore.kt`) type-check against `android.jar` 34.
- `StarterBrushes` and `PlaybackClock` were compiled and run on a plain JVM; brush tips were rendered and looked at.
- Every new resource name referenced in code exists in `strings.xml`, and the file is valid XML.

## What was NOT checked (look here first if something fails)
1. `ui/EditorViewModel.kt`: the layer mirroring block (`mirrorLayerOp`, `mirrorAdd/Delete/Insert/Move/Merge`, `mirrorLayerMeta`, `runPendingMetaMirrors`, `settleLayerMirrors`) uses coroutines, `Mutex`, and Room writes.
2. `ui/screens/EditorScreen.kt`: keyboard handler (`handleKey`), rename/confirm dialogs, `onKeyEvent` + `focusable` order.
3. Compose files touched by the string extraction (`stringResource(R.string.ui_...)`): a missing import or a call outside composable scope would show here. Search for `ui_` names if a string is missing.
4. `ui/components/WishySlider.kt` (semantics + inset thumb), `BouncyClickable.kt` (focus ring, `LocalHapticsEnabled`), `ToolButton.kt` (`semanticsOnClick` alias).
5. `data/SettingsStore.kt`: new `starterBrushVersion` flow and `getThemeTokensByName(..., systemDark)`.
6. `export/ExportService.kt` (`composeFrame` now skips blank layers via `BitmapStore.decodeOrNull`).
7. Room: no schema change was made, so no migration is needed.

## If a piece is too risky to keep
Each commit is self-contained: `git log --oneline` then `git revert <sha>`.
- Layers shared across frames: commit "Layers shared across all frames ...".
- Sparse layers + memory budget: "Phase 1: sparse layers ...".
- Starter brushes: "Phase 3 part 1 ...".
- String extraction: "Pressure-to-flow ...; extract 98 UI strings" and the later strings commit.

## Then
Work through `QA_CHECKLIST_v1.1.md` on a real device.

## Database rule
No destructive migration fallback exists any more. If you change an entity, bump `version` in `WishyDatabase.kt` AND add a `Migration`; otherwise the app will crash on start for existing installs (on purpose, instead of wiping their projects).
