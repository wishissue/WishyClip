# 05 — FEATURE ACCESSIBILITY MATRIX

## 1. Feature Access & Discoverability Matrix

| Feature | Primary Access | Secondary Access | Keyboard Shortcut | Touch Target | Content Description | Discoverable? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Brush Select** | Tool Rail → Brush Button | Double tap Brush | `B` | $48 \times 48\text{dp}$ | "Brush tool" | YES |
| **Brush Menu / Pickers** | Brush Button (tap active brush) | Tool Options popup | `Shift + B` | $48 \times 48\text{dp}$ | "Open brush menu" | YES |
| **Brush Size** | Tool Options popup / Drag overlay | Brush slider | `[` / `]` | $48 \times 48\text{dp}$ | "Brush size slider" | YES |
| **Brush Opacity** | Tool Options popup | Opacity slider | `Shift + [` / `Shift + ]` | $48 \times 48\text{dp}$ | "Brush opacity slider" | YES |
| **Eraser** | Tool Rail → Eraser | Tool Options | `E` | $48 \times 48\text{dp}$ | "Eraser tool" | YES |
| **Fill / Bucket** | Tool Rail → Fill | Tool Options | `F` | $48 \times 48\text{dp}$ | "Fill tool" | YES |
| **Lasso Selection** | Tool Rail → Lasso | Tool Options | `L` | $48 \times 48\text{dp}$ | "Lasso selection tool" | YES |
| **Shapes (Line/Rect/Ellipse)** | Tool Rail → Shapes | Tool Options | `U` | $48 \times 48\text{dp}$ | "Shapes tool" | YES |
| **Text Tool** | Tool Rail → Text | Tool Options | `T` | $48 \times 48\text{dp}$ | "Text tool" | YES |
| **Ruler** | Tool Rail → Ruler Icon | Overflow Menu | `R` | $48 \times 48\text{dp}$ | "Toggle ruler" | YES |
| **Mirror Symmetry** | Tool Rail → Mirror Icon | Overflow Menu | `M` | $48 \times 48\text{dp}$ | "Toggle symmetry mirror" | YES |
| **Eyedropper** | Tool Rail → Eyedropper | Long-press Color Swatch | `I` | $48 \times 48\text{dp}$ | "Eyedropper tool" | YES |
| **Color Picker** | Tool Rail → Color Swatch | Double-tap Swatch | `C` | $48 \times 48\text{dp}$ | "Open color picker" | YES |
| **Undo** | Top Bar → Undo Button | 2-finger tap canvas | `Ctrl + Z` / `Cmd + Z` | $48 \times 48\text{dp}$ | "Undo action" | YES |
| **Redo** | Top Bar → Redo Button | 3-finger tap canvas | `Ctrl + Shift + Z` | $48 \times 48\text{dp}$ | "Redo action" | YES |
| **Play / Pause** | Top Bar → Play Button | Timeline Play Button | `Space` | $48 \times 48\text{dp}$ | "Play or pause animation" | YES |
| **Add Frame** | Timeline Strip → `+` Cell | Frame Overflow Menu | `N` or `Ctrl + N` | $64 \times 48\text{dp}$ | "Add new blank frame" | YES |
| **Duplicate Frame** | Timeline Bar → Copy Icon | Frame Dropdown | `Ctrl + D` | $48 \times 48\text{dp}$ | "Duplicate current frame" | YES |
| **Delete Frame** | Timeline Bar → Trash Icon | Frame Dropdown | `Delete` / `Backspace` | $48 \times 48\text{dp}$ | "Delete current frame" | YES |
| **Previous / Next Frame** | Timeline Cell tap | Swipe Timeline | `Left Arrow` / `Right Arrow` | $48 \times 48\text{dp}$ | "Navigate to frame" | YES |
| **Layer Panel** | Top Bar → Layers Button | Swipe right from edge | `Ctrl + L` | $48 \times 48\text{dp}$ | "Open layer panel" | YES |
| **Add Layer** | Layer Panel → `+ Add Layer` | Layer Menu | `Ctrl + Shift + N` | $48 \times 48\text{dp}$ | "Add new layer" | YES |
| **Layer Visibility** | Layer Row → Eye Icon | Double-tap Layer Row | `V` | $48 \times 48\text{dp}$ | "Toggle layer visibility" | YES |
| **Layer Lock** | Layer Row → Lock Icon | Layer Options | `K` | $48 \times 48\text{dp}$ | "Toggle layer lock" | YES |
| **Onion Skin Toggle** | Timeline Bar → Onion Icon | Overflow Menu | `O` | $48 \times 48\text{dp}$ | "Toggle onion skin overlay" | YES |
| **Audio Track Manager** | Timeline Bar → Audio Icon | Overflow Menu | `A` | $48 \times 48\text{dp}$ | "Manage audio tracks" | YES |
| **Export Project** | Overflow Menu → Export | Home Screen Card Menu | `Ctrl + E` | $48 \times 48\text{dp}$ | "Export animation" | YES |
| **Canvas Reset View** | Canvas double-tap | Overflow → Reset View | `0` or `Ctrl + 0` | $48 \times 48\text{dp}$ | "Reset zoom and rotation" | YES |
| **Shortcuts / Help Overlay** | Top Bar → Overflow → Help | Settings → Shortcuts | `?` or `F1` | $48 \times 48\text{dp}$ | "Keyboard shortcuts guide" | YES |

---

## 2. Accessibility Guidelines Compliance
1. **Touch Targets**: All interactive icons maintain a minimum $48 \times 48\text{dp}$ touch target area (via `ActionIconButton.kt` and `ToolButton.kt`).
2. **Contrast Ratio**: High contrast text (`#EDEDF0` on `#161618` dark surface $\ge 12:1$ contrast ratio).
3. **Screen Reader Support**: Every icon button uses localized string resources for `contentDescription` (`strings.xml`).
4. **State Communication**: Selected tools and active states use both color accent (`#FF5252`) and structural shape changes (pill background container) to ensure accessibility for colorblind users.
