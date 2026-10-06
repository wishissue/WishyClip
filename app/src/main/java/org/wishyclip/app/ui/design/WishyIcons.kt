package org.wishyclip.app.ui.design

import androidx.annotation.DrawableRes
import org.wishyclip.app.R

enum class IconPack { CUTE, CLEAN }

object WishyIcons {
    var currentPack: IconPack = IconPack.CUTE

    @get:DrawableRes val Pen: Int get() = R.drawable.ic_pen
    @get:DrawableRes val Pencil: Int get() = R.drawable.ic_pencil
    @get:DrawableRes val Marker: Int get() = R.drawable.ic_marker
    @get:DrawableRes val Eraser: Int get() = R.drawable.ic_eraser
    @get:DrawableRes val Airbrush: Int get() = R.drawable.ic_airbrush
    @get:DrawableRes val Calligraphy: Int get() = R.drawable.ic_calligraphy
    @get:DrawableRes val Highlighter: Int get() = R.drawable.ic_highlighter
    @get:DrawableRes val Fill: Int get() = R.drawable.ic_fill
    @get:DrawableRes val Lasso: Int get() = R.drawable.ic_lasso
    @get:DrawableRes val Shapes: Int get() = R.drawable.ic_shapes
    @get:DrawableRes val Text: Int get() = R.drawable.ic_text
    @get:DrawableRes val Eyedropper: Int get() = R.drawable.ic_eyedropper
    @get:DrawableRes val Palette: Int get() = R.drawable.ic_palette
    @get:DrawableRes val Undo: Int get() = R.drawable.ic_undo
    @get:DrawableRes val Redo: Int get() = R.drawable.ic_redo
    @get:DrawableRes val Play: Int get() = R.drawable.ic_play
    @get:DrawableRes val Pause: Int get() = R.drawable.ic_pause
    @get:DrawableRes val SkipBack: Int get() = R.drawable.ic_skip_back
    @get:DrawableRes val SkipForward: Int get() = R.drawable.ic_skip_forward
    @get:DrawableRes val Layers: Int get() = R.drawable.ic_layers
    @get:DrawableRes val Onion: Int get() = R.drawable.ic_onion
    @get:DrawableRes val Audio: Int get() = R.drawable.ic_audio
    @get:DrawableRes val Export: Int get() = R.drawable.ic_export
    @get:DrawableRes val Add: Int get() = R.drawable.ic_add
    @get:DrawableRes val Duplicate: Int get() = R.drawable.ic_copy
    @get:DrawableRes val Delete: Int get() = R.drawable.ic_delete
    @get:DrawableRes val Settings: Int get() = R.drawable.ic_settings
    @get:DrawableRes val Home: Int get() = R.drawable.ic_home
    @get:DrawableRes val Back: Int get() = R.drawable.ic_back
    @get:DrawableRes val More: Int get() = R.drawable.ic_more
    @get:DrawableRes val Lock: Int get() = R.drawable.ic_lock
    @get:DrawableRes val VisibilityOn: Int get() = R.drawable.ic_visibility_on
    @get:DrawableRes val VisibilityOff: Int get() = R.drawable.ic_visibility_off
    @get:DrawableRes val Help: Int get() = R.drawable.ic_help
    @get:DrawableRes val Charcoal: Int get() = R.drawable.ic_charcoal
    @get:DrawableRes val Ink: Int get() = R.drawable.ic_ink
    @get:DrawableRes val Watercolor: Int get() = R.drawable.ic_watercolor
    @get:DrawableRes val Chalk: Int get() = R.drawable.ic_chalk
    @get:DrawableRes val Pixel: Int get() = R.drawable.ic_pixel
    @get:DrawableRes val BrushCustom: Int get() = R.drawable.ic_brush_custom
    @get:DrawableRes val Mirror: Int get() = R.drawable.ic_mirror
    @get:DrawableRes val Ruler: Int get() = R.drawable.ic_ruler
    @get:DrawableRes val RotateLeft: Int get() = R.drawable.ic_rotate_left
    @get:DrawableRes val RotateRight: Int get() = R.drawable.ic_rotate_right
    @get:DrawableRes val Import: Int get() = R.drawable.ic_import
    @get:DrawableRes val Merge: Int get() = R.drawable.ic_merge
    @get:DrawableRes val Blend: Int get() = R.drawable.ic_blend
    @get:DrawableRes val ShapeLine: Int get() = R.drawable.ic_shape_line
    @get:DrawableRes val ShapeRect: Int get() = R.drawable.ic_shape_rect
    @get:DrawableRes val ShapeEllipse: Int get() = R.drawable.ic_shape_ellipse
    @get:DrawableRes val Check: Int get() = R.drawable.ic_check
    @get:DrawableRes val ArrowUp: Int get() = R.drawable.ic_arrow_up
    @get:DrawableRes val ArrowDown: Int get() = R.drawable.ic_arrow_down
    @get:DrawableRes val RestoreUi: Int get() = R.drawable.ic_restore
    @get:DrawableRes val Logo: Int get() = R.drawable.ic_logo
}
