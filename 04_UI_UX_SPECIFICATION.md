# 04 — UI / UX SPECIFICATION

## 1. UX Philosophy & FlipaClip Reference Analysis
An animator's primary workspace is the **Canvas**. Every tool, panel, and modal must serve the creative flow without permanently obscuring artwork.

### Key Usability Principles:
1. **Immediate Tool Access**: Brush, Eraser, Lasso, Fill, Shapes, Text, Eyedropper, Mirror, Ruler, and Color are accessible in 1 tap from the primary tool rail.
2. **Transient Quick Adjustments**: Adjusting brush size or opacity shows a temporary floating bubble ("18 px", "85%") that dismisses automatically upon release.
3. **Workspace Adaptability**: Support two primary workspace modes:
   - **Classic Mode**: Maximized canvas area, compact floating tool rail, single-row frame timeline strip. Ideal for fast hand-drawn sketch animation.
   - **Studio Mode**: Expanded timeline view revealing multi-track layer rows, audio waveforms, frame exposure duration handles, and advanced onion skin controls. Ideal for complex multi-layer lip-sync and scene finishing.
4. **Left-Handed Mirroring**: Full layout reversal (tool rail on right, panels on left) with 1 toggle in Settings.

---

## 2. Editor Screen Structural Layout

```
+-----------------------------------------------------------------------+
| [<-] Project Name          [Undo] [Redo] [Play] [Layers] [Onion] [...]| (Top Bar 52dp)
+----+------------------------------------------------------------------+
|    |                                                                  |
| T  |                                                                  |
| O  |                                                                  |
| O  |                         CANVAS AREA                              |
| L  |              (Pinch Zoom / Pan / 2-Finger Rotate)                |
|    |                                                                  |
| R  |                                                                  |
| A  |                                                                  |
| I  |                                                                  |
| L  |                                                                  |
+----+------------------------------------------------------------------+
| [1/24] [12 fps]  (Onion) (Copy) (Paste) (Dup) (Del)  [+ Add Frame]    | (Timeline Strip)
| [ Frame 1 ] [ Frame 2 ] [ Frame 3 ] [ Frame 4 ] ...                   |
+-----------------------------------------------------------------------+
```

---

## 3. Tool Specifications & Interaction Models

### 3.1 Brush Tool & Brush Menu
- **Trigger**: Single tap on Brush icon opens/selects brush; second tap opens Brush Selection Sheet.
- **Built-in Brushes**: Pen, Pencil, Marker, Airbrush, Calligraphy, Highlighter, Charcoal, Ink, Watercolor, Chalk, Pixel Pen, plus Imported Tip Brushes (`.wbrush`, `.kpp`, `.abr`, `.gbr`, `.brush`).
- **Quick Controls**:
  - Size Slider ($1\text{px} - 200\text{px}$) with logarithmic response.
  - Opacity Slider ($0\% - 100\%$).
  - Stabilizer Slider ($0 - 10$ stroke smoothing).
  - Live stroke preview box updated dynamically.

### 3.2 Eraser Tool
- **Behavior**: Clears pixels on active layer using brush size, opacity, and stabilizer settings. Supports hard-edge and soft-edge erase profiles.

### 3.3 Lasso & Transform Tool
- **Selection**: Freehand closed loop path.
- **Bounding Box Overlay**: 8 control handles (corners & edges) + top rotation stalk handle.
- **Actions**: Drag to move, pinch/corner drag to scale, rotation handle to rotate, flip horizontal, flip vertical, copy, paste, delete selection.

### 3.4 Fill Tool (Paint Bucket)
- **Engine**: Scanline flood fill with tolerance ($0 - 255$).
- **Sample Mode**: Current Layer vs Visible Layers (All Layers).
- **Anti-Aliasing**: Smooth boundary expansion ($1\text{px} - 2\text{px}$).

### 3.5 Mirror Symmetry
- **Modes**: Off, Left/Right (Vertical Axis), Top/Bottom (Horizontal Axis), 4-Way Quad.
- **Interactions**: Real-time mirrored stroke rendering across center line for all drawing tools, eraser, and shapes.

### 3.6 Ruler Straight Edge
- **Behavior**: Movable line segment on canvas with two endpoint drag handles and center drag handle. Strokes drawn within snap threshold auto-align to ruler line.

### 3.7 Color Picker
- **Triggers**: Tap Color Swatch at end of tool rail.
- **Interface**:
  - Continuous HSV Color Wheel / Square.
  - Current vs Previous Color comparison swatch.
  - HEX value text entry (`#FF5252`).
  - 12 Recent Color Swatches + Custom Palette slots.
  - Eyedropper toggle button (samples canvas color then restores active tool).

---

## 4. Timeline & Layer Subsystems

### 4.1 Timeline Architecture
- **Frame Exposure / Hold Duration**:
  - Each frame entity holds `position` and `duration` (exposure count $N \ge 1$).
  - Drawing stays visible for $N$ timeline playback steps without duplicating underlying layer PNG data.
- **Frame Operations**:
  - Add Frame (inserts blank frame at current index).
  - Duplicate Frame (copies frame drawing and layer structure).
  - Delete Frame (recycles bitmap resources, repositions indices).
  - Copy / Paste Frame Buffer (stores frame data in `EditorViewModel`).
  - Timeline Drag Reorder / Exposure Stretch: Drag frame cell boundary to adjust hold duration.

### 4.2 Layer Subsystem
- **Layer Stack Order**: Index 0 = Bottom (Background), Index $M$ = Top.
- **Layer Panel**:
  - Add Layer / Delete Layer.
  - Visibility Toggle (Eye icon).
  - Lock Toggle (Lock icon prevents accidental drawing).
  - Opacity Slider ($0\% - 100\%$).
  - Blend Modes Dropdown: Normal, Multiply, Screen, Overlay, Darken, Lighten, Add (`LayerBlending`).
  - Merge Down (blends layer with layer directly below).

### 4.3 Onion Skin Overlay
- **Settings**:
  - Toggle On / Off.
  - Previous Frames ($1 - 3$ frames back, default red tint `#FF4040`).
  - Next Frames ($1 - 3$ frames forward, default green tint `#40C040`).
  - Overlay Opacity ($10\% - 80\%$).

---

## 5. Responsive Layout & Screen Configurations

| Screen Type / Orientation | Tool Rail Position | Timeline Layout | Layer Panel Position |
| :--- | :--- | :--- | :--- |
| **Phone Portrait** | Horizontal bar above timeline | Compact horizontal scroll strip | Bottom sheet popup |
| **Phone Landscape** | Vertical side rail (Left or Right) | Bottom horizontal strip | Right / Left docked panel |
| **Tablet Portrait / Landscape** | Vertical side rail | Extended Studio timeline with audio track | Side panel overlay |
