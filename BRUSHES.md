# Wishy Clip: Brush Engine & Importers Specification

Wishy Clip supports an extensible, community-friendly brush architecture where any brush is defined as data fed into the unified dab engine.

## 1. Built-in Brushes (~12)
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
