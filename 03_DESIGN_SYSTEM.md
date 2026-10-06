# 03 — DESIGN SYSTEM SPECIFICATION

## 1. Visual Identity & Design Strategy
WishyClip uses a focused, dark-first studio aesthetic designed for 2D animators. The primary canvas backdrop and surrounding toolbars provide high contrast against artwork without causing eye strain.

- **Primary Brand Theme**: `FlipDarkTokens` / `DarkTokens`
- **Design Philosophy**: Minimal distraction, maximum canvas area, instant tool availability, high touch target accessibility ($48\text{dp}$ minimum).

---

## 2. Color Tokens

| Token Name | Hex Code | Visual Role |
| :--- | :--- | :--- |
| `primary` / `accent` | `#FF5252` | Warm Coral Accent — selected tools, playhead, active frame highlight, call-to-action buttons |
| `onPrimary` | `#FFFFFF` | High contrast text/icon on primary accent |
| `surface` | `#161618` | Main app background, top bar, dialogs, sheet surfaces |
| `onSurface` | `#EDEDF0` | Primary text and icons |
| `surfaceVariant` | `#242428` | Button backgrounds, input fields, unselected tab chips |
| `onSurfaceVariant` | `#A0A0AB` | Secondary labels, disabled states, borders |
| `canvasBackdrop` | `#2B2B30` | Canvas surround area outside drawing paper |
| `paper` | `#FFFFFF` | Default animation paper background |
| `toolRail` | `#1C1C20` | Floating tool rail background surface |
| `timeline` | `#1C1C20` | Timeline track and controls container |
| `onionPreviousTint` | `#FF4040` | Red tint overlay for previous onion skin frames |
| `onionNextTint` | `#40C040` | Green tint overlay for next onion skin frames |
| `danger` | `#FF3B30` | Delete actions, destructive warnings |

---

## 3. Dimensional & Layout Tokens

| Token Name | Value | Purpose |
| :--- | :--- | :--- |
| `smallRadius` | $8\text{dp}$ | Small chips, text fields, frame thumbnails |
| `mediumRadius` | $16\text{dp}$ | Tool options popups, layer rows, dialog corners |
| `largeRadius` | $28\text{dp}$ | Floating action bars, bottom sheets |
| `spaceXs` | $4\text{dp}$ | Micro-padding between tool icons |
| `spaceSmall` | $8\text{dp}$ | Compact row spacing |
| `spaceMedium` | $12\text{dp}$ | Standard panel padding |
| `spaceLarge` | $16\text{dp}$ | Screen margins, section headers |
| `spaceXl` | $24\text{dp}$ | Hero spacing, modal offsets |
| `toolIconSize` | $24\text{dp}$ | Standard tool vector icon size |
| `actionIconSize` | $24\text{dp}$ | Top bar / action button icon size |
| `toolButtonSize` | $44\text{dp}$ | Tool button container size |
| `minTouchTarget` | $48\text{dp}$ | Android accessibility touch target standard |
| `topBarHeight` | $52\text{dp}$ | Editor top navigation bar height |
| `toolRailWidth` | $56\text{dp}$ | Vertical tool rail width |
| `timelineHeight` | $120\text{dp}$ (Classic) / $220\text{dp}$ (Studio) | Timeline strip height |

---

## 4. Typography Scale

| Style Name | Font / Size / Weight | Usage |
| :--- | :--- | :--- |
| **Title Large** | System Sans, $22\text{sp}$, Bold | Screen titles, project names |
| **Title Medium** | System Sans, $16\text{sp}$, SemiBold | Top bar header, dialog headers |
| **Body Large** | System Sans, $16\text{sp}$, Regular | Dialog message text, onboarding copy |
| **Body Medium** | System Sans, $14\text{sp}$, Regular | Settings labels, layer titles |
| **Label Large** | System Sans, $14\text{sp}$, Medium | Button text, frame counters |
| **Label Medium** | System Sans, $12\text{sp}$, Medium | Sliders, numerical bubbles, tool descriptions |
| **Label Small** | System Sans, $10\text{sp}$, Regular | Timecode markers, FPS badges |

---

## 5. Icon System Rules
1. **ViewBox**: $24 \times 24$ vector bounds.
2. **Stroke Width**: $2\text{dp}$ uniform stroke.
3. **Caps & Joins**: Round stroke caps (`android:strokeLineCap="round"`) and round line joins (`android:strokeLineJoin="round"`).
4. **Consistency**: Zero filled solid icons mixed into line-art toolbars. Tint applied via `WishyTheme.tokens.onSurface` or `primary` when active.
