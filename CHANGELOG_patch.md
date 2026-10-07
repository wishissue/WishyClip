# Patch notes

## Fonts / text
- Imported fonts are listed by their real family name (read from the font's `name` table), shown in their own typeface, and can be removed. Multi-file import; duplicates are detected; invalid files are rejected with a message.
- "Inter" now actually renders in Inter (it used the system font before). Added Inter Bold, Sans Serif, Cursive, Casual, Condensed.
- Text tool: Edit-text button for a selected text, colour/opacity controls, text field previews the chosen font.

## Themes
- Settings: import theme (.json), New theme (Theme Studio with live preview), edit/delete, copy-as-JSON. Custom themes persist (previously only the name was saved, so they reverted on restart).
- Theme parsing validates input, detects dark/light, and derives readable text colours.

## Brushes
- PNG/JPG/WebP images can be imported as brush tips (alpha, or dark-on-light / light-on-dark auto-detected). Oversized images are downsampled instead of rejected.
- Brush list shows a real stamp preview rendered with the canvas' own stamping rules.
- Per-brush settings: spacing, flow, scatter, size jitter, angle, follow-stroke. Saved to disk.
- Selecting a custom brush starts at a visible size (was 8 px, which hid the tip); size range up to 200 px.
- Clear message when a brush's tip file is missing instead of silently drawing a plain line.

## Import
- Import hub: images as frames, video as frames, image as NEW layer, audio, brushes, fonts.
- Fixed: image-as-layer overwrote the active layer; image/video frames were imported in reverse order; large photos could OOM; EXIF rotation ignored; video import produced duplicate key frames.

## Export
- Streams one frame at a time (no more all-frames-in-memory OOM), honours blend modes and frame holds, safe file names, "Current frame" exports the current frame, failures show a notification instead of leaving a stuck one.
- MP4 encoder rewritten: flushes the last frames, monotonic timestamps. Audio is still not muxed (label corrected).
- Asks for notification permission on Android 13+.

## Misc
- Dialogs scroll (buttons were pushed off-screen on small/landscape screens).
- Onboarding shows on first launch. Debug gallery hidden in release builds.
