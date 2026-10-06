# Wishy Clip: Design System Guide

This document explains how to customize, extend, and re-skin Wishy Clip without touching core drawing or architecture logic.

## Design System Overview

All visual styling in Wishy Clip is governed by `WishyTokens` ([`WishyTokens.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/design/WishyTokens.kt)) and passed through `WishyTheme` ([`WishyTheme.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/design/WishyTheme.kt)).

Every UI component reads visual parameters (colors, corner radii, icon sizes, spacing scale, animation durations) strictly through `WishyTheme.tokens`.

---

## 1. How to Change a Color or Add a Theme

To add a built-in theme pack in Kotlin, create a file in `ui/design/themes/`:

```kotlin
package org.wishyclip.app.ui.design.themes

import androidx.compose.ui.graphics.Color
import org.wishyclip.app.ui.design.WishyTokens

val NeonTokens = WishyTokens(
    name = "Neon",
    primary = Color(0xFF00E676),
    onPrimary = Color(0xFF000000),
    surface = Color(0xFF121212),
    canvasBackdrop = Color(0xFF050505),
    toolRail = Color(0xFF1E1E1E),
    timeline = Color(0xFF1E1E1E),
    accent = Color(0xFFFF007F)
)
```

---

## 2. Community JSON Theme Schema

Users can import custom themes at runtime from a `.json` file stored in app storage or loaded from `filesDir/themes/`.

### JSON Schema Specification
```json
{
  "name": "Sunset Coral",
  "primary": "#FF6B6B",
  "primaryContainer": "#FFE3E3",
  "secondary": "#4ECDC4",
  "surface": "#FFF9F5",
  "surfaceVariant": "#F7EBE1",
  "canvasBackdrop": "#EFE0D5",
  "toolRail": "#FAF0E6",
  "timeline": "#FAF0E6",
  "accent": "#FFE66D",
  "danger": "#FF4757"
}
```

---

## 3. How to Swap the Icon Pack

Wishy Clip supports semantic icon mapping via [`WishyIcons.kt`](file:///home/denji/Documents/new/WishyClip/app/src/main/java/org/wishyclip/app/ui/design/WishyIcons.kt).
Two packs are bundled:
- **Cute**: Expressive, colorful vector drawables.
- **Clean**: Minimalist, monochrome, tintable icons.

To add or swap an icon pack, place matching XML vector drawables in `res/drawable/` and configure `WishyIcons.setPack(IconPack.CLEAN)` or `IconPack.CUTE`.

---

## 4. Left-Handed Mode & Mirroring Layout

Left-handed mode flips the position of the tool rail and side panels.
Simply set `isLeftHanded = true` in settings. The UI layout automatically aligns the tool rail to the right and panels to the left without duplicating screen composables.

---

## 5. Design Gallery

In debug builds, open **Settings > Design Gallery** to preview all components, themes, and icon packs side-by-side in real time!


---

## 6. The "Cloud" look (cute, big, Apple-style)

The default theme is **Cloud** (light) with **Midnight** as its dark twin. The look is built from a few
shared pieces, so re-skinning stays a tokens-only job:

| Piece | File | What it does |
|---|---|---|
| `GlassSurface` | `ui/components/GlassSurface.kt` | Floating rounded panel: translucent tint, hairline border, soft shadow. Used by the top bar, tool dock, timeline and pop-over panels. |
| `bouncyClickable` | `ui/components/BouncyClickable.kt` | Spring "squish" on press plus a light haptic tick. Used by every button. |
| `PrimaryRoundButton` | `ui/components/PrimaryRoundButton.kt` | The one big filled circle in a bar (Play / Pause). |
| `InterFamily` / `WishyTypography` | `ui/design/WishyTypography.kt` | Inter text styles, plus the iOS-style large title (`headlineMedium`). |

New tokens: `floatMargin`, `glassAlpha`, `glassBorder`, `primaryButtonSize`, `frameCellWidth`, `frameCellHeight`.
Defaults are now bigger and rounder: tool buttons 56dp, touch targets 52dp, radii 12 / 20 / 32dp.

Notes:
- The "glass" is a translucent tint, not a live background blur. Blurring behind the canvas would cost drawing performance.
- Apple's San Francisco font and SF Symbols can't be redistributed in an Android app, so Inter and the existing open icon packs are used.
