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
    @get:DrawableRes val Airbrush: Int get() = R.drawable.ic_pen
    @get:DrawableRes val Calligraphy: Int get() = R.drawable.ic_pencil
    @get:DrawableRes val Highlighter: Int get() = R.drawable.ic_marker
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
    @get:DrawableRes val Logo: Int get() = R.drawable.ic_logo
}
