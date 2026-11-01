# Wishy Clip: Brush Engine & Importers Specification

Wishy Clip supports an extensible, community-friendly brush architecture where any brush is defined as data fed into the unified dab engine.

## 1. Built-in Brushes (11 + Eraser)
1. **Pen**: Smooth ink vector stroke, round cap.
2. **Pencil**: Textured graphite shading with discrete path effect.
3. **Marker**: Broad square-cap marker with semi-transparent blending.
4. **Eraser**: Pixel clearing tool.
5. **Airbrush**: Soft radial blurred spray.
6. **Calligraphy**: Flat chisel-angled nib stroke.
7. **Highlighter**: Semi-transparent broad stroke.
8. **Charcoal**: Textured rough organic shader.
9. **Ink Brush**: Variable pressure fluid ink.
10. **Watercolor**: Soft wet edge blending wash.
11. **Chalk**: Dry pastel textured dab.
12. **Pixel Pen**: Hard aliased retro pixel brush.

---

## 2. Supported Brush Importers
Wishy Clip supports batch importing community brushes with safety limits (max 20 MB per file, max 512 brushes per bundle, tip max 2048px):
1. **.wbrush (.zip)**: `brush.json` parameters + `tip.png` grayscale/RGBA tip.
2. **Krita Bundles (.bundle) & Presets (.kpp)**: Parses tip image, spacing, size, opacity, rotation, and scatter parameters from XML preset metadata.
3. **Photoshop Brushes (.abr)**: Parses tip bitmap textures and spacing.
4. **GIMP Brushes (.gbr / .gih)**: Parses raster tip bitmaps.
5. **Procreate Brushes (.brush)**: Best-effort parse of `Shape.png`, `Grain.png`, and property list parameters.

### Dab engine and limits
Imported brushes are drawn by stamping the tip (an 8-bit alpha mask) along the stroke every `spacing` x diameter pixels, optionally rotated (`angle`, or following the stroke direction), scattered and size-jittered. Notes on what is and is not imported:
- GIMP greyscale tips are read as black = ink; Photoshop sampled tips as 255 = ink; Procreate `Shape.png` as white = ink; PNG tips use their alpha channel if they have one, otherwise black = ink.
- Procreate: only the shape is used. Grain textures, dynamics and the plist parameters are ignored.
- Krita: only an embedded tip, name, spacing and angle are read. Dynamics, textures and blending modes are ignored.
- Photoshop v6+ sample records have a variable header, so the image header is found by scanning for a plausible one.

### Pressure response
Dab brushes change size with pressure (20% to 100% of the set size). Brushes whose own flow is below 0.9 (pencils, charcoal, pastel, spray) also build up darker with pressure (35% to 100% of their flow). Solid inks and paints (flow 0.9 or more) change size only.

## 3. Starter pack (16 original brushes)
Graphite HB, Graphite 6B, Sketch pencil, Colour pencil, Technical pen, Ink liner, Brush pen, Dry ink, Charcoal stick, Pastel, Gouache, Oil flat, Bristle, Splatter, Stipple, Spray grain. Generated in code (`brush/StarterBrushes.kt`), installed once per pack version, editable and deletable. No third-party brush files or textures are used.
