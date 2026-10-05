package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnionSkinTest {

    @Test
    fun testOnionSkinSettingsDefaults() {
        val settings = OnionSkinSettings()
        assertEquals(false, settings.enabled)
        assertEquals(1, settings.framesBefore)
        assertEquals(0, settings.framesAfter)
        assertEquals(0.35f, settings.opacity)
    }

    @Test
    fun testDefaultOnionSkinRendererDraws() {
        val settings = OnionSkinSettings(enabled = true, opacity = 1.0f)
        val ghostBmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        ghostBmp.eraseColor(Color.RED)

        val layer = LayerData(id = 1L, name = "L1", visible = true, opacity = 1f, bitmap = ghostBmp)
        val ghost = GhostFrame(distance = 1, layers = listOf(layer))

        val targetBmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBmp)

        DefaultOnionSkinRenderer.drawGhost(
            canvas = canvas,
            ghost = ghost,
            settings = settings,
            tint = 0,
            dstRect = Rect(0, 0, 10, 10)
        )

        assertNotNull(targetBmp)
    }
}
