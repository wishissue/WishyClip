# ASSET MANIFEST

| Asset Name | Category | Source URL | License | License File / URL | Modification Status | App Location / Usage | Attribution Req? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **WishyClip Logo** | App Branding | User Provided | Custom | `design/logo/logo_source.png` | Custom doodle cat branding asset | App launcher, splash, and in-app branding (`ic_logo.png`) | No |
| **Phosphor Icons** | Vector Icons | https://phosphoricons.com | MIT | `assets/icons/LICENSES/PHOSPHOR_LICENSE.txt` | Bold weight vector drawables styled to WishyClip tokens | Primary action, drawing tool, & toolbar icons (`res/drawable/ic_*.xml`) | No (MIT) |
| **Tabler Icons** | Vector Icons | https://tabler.io/icons | MIT | `assets/icons/LICENSES/TABLER_LICENSE.txt` | Vector colors/strokes styled to WishyClip tokens (24x24dp, 2dp stroke) | Primary action & toolbar icons (`res/drawable/ic_*.xml`) | No (MIT) |
| **Lucide Icons** | Vector Icons | https://lucide.dev/ | ISC / MIT | `assets/icons/LICENSES/LUCIDE_LICENSE.txt` | Stroke width & caps standardized to 24x24dp | Secondary action icons (ruler, mirror, audio waveform) | No (ISC) |
| **Material Symbols** | Vector Icons | https://fonts.google.com/icons | Apache-2.0 | `https://www.apache.org/licenses/LICENSE-2.0` | Adapted stroke & size | Fallback icons for system settings/more menu | No (Apache) |
| **Lottie Animations** | Lottie JSON | https://lottiefiles.com | CC BY 4.0 / Lottie Simple | `assets/animations/LICENSES/LOTTIE_LICENSE.txt` | Custom color tinting to active theme tokens | Export success, empty states, loading indicators | Yes (Record in ATTRIBUTIONS.md) |
| **System Typography** | Font | System / Android SDK | Apache-2.0 / OFL | `assets/fonts/LICENSES/FONT_LICENSES.txt` | Standard Android System Sans (Roboto / Noto) | UI labels, numerical displays, sliders, timecodes | No |

## Icon Inventory Mapping

### Navigation
- `back`: `ic_back.xml` (Tabler arrow-left)
- `home`: `ic_home.xml` (Tabler home)
- `close`: `ic_delete.xml` / `ic_back.xml` (Tabler x / arrow-left)
- `more`: `ic_more.xml` (Tabler dots-vertical)
- `settings`: `ic_settings.xml` (Tabler settings)
- `help`: `ic_help.xml` (Tabler help-circle)

### Drawing Tools
- `brush`: `ic_pen.xml`, `ic_pencil.xml`, `ic_marker.xml`, `ic_airbrush.xml`, `ic_calligraphy.xml`, `ic_highlighter.xml`, `ic_charcoal.xml`, `ic_ink.xml`, `ic_watercolor.xml`, `ic_chalk.xml`, `ic_pixel.xml`
- `eraser`: `ic_eraser.xml` (Tabler eraser)
- `fill`: `ic_fill.xml` (Tabler color-bucket)
- `eyedropper`: `ic_eyedropper.xml` (Tabler color-picker)
- `lasso`: `ic_lasso.xml` (Tabler lasso)
- `shapes`: `ic_shapes.xml` (Tabler shapes: line, rect, ellipse)
- `text`: `ic_text.xml` (Tabler text-size)
- `ruler`: `ic_ruler.xml` (Lucide ruler)
- `mirror`: `ic_mirror.xml` (Tabler flip-horizontal)

### Animation & Timeline
- `play`: `ic_play.xml` (Tabler player-play)
- `pause`: `ic_pause.xml` (Tabler player-pause)
- `onion`: `ic_onion.xml` (Custom dual-circle layer overlay)
- `add_frame`: `ic_add.xml` (Tabler plus)
- `duplicate_frame`: `ic_copy.xml` (Tabler copy)
- `delete_frame`: `ic_delete.xml` (Tabler trash)

### Layers & Audio
- `layers`: `ic_layers.xml` (Tabler layers-intersect)
- `visibility_on`: `ic_visibility_on.xml` (Tabler eye)
- `visibility_off`: `ic_visibility_off.xml` (Tabler eye-off)
- `lock`: `ic_lock.xml` (Tabler lock)
- `blend`: `ic_blend.xml` (Tabler blend)
- `merge`: `ic_merge.xml` (Tabler git-merge)
- `audio`: `ic_audio.xml` (Tabler volume)
