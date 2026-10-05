package org.wishyclip.app.canvas

import android.graphics.Bitmap
import java.util.LinkedList

class BitmapPool(private val maxCapacity: Int = 24) {
    private val pool = LinkedList<Bitmap>()

    @Synchronized
    fun get(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val bmp = iterator.next()
            if (bmp.width == width && bmp.height == height && bmp.config == config && !bmp.isRecycled) {
                iterator.remove()
                bmp.eraseColor(0)
                return bmp
            }
        }
        return Bitmap.createBitmap(width, height, config)
    }

    @Synchronized
    fun recycle(bmp: Bitmap) {
        if (bmp.isRecycled) return
        if (pool.size < maxCapacity) {
            pool.add(bmp)
        } else {
            bmp.recycle()
        }
    }

    @Synchronized
    fun clear() {
        for (bmp in pool) {
            if (!bmp.isRecycled) bmp.recycle()
        }
        pool.clear()
    }
}
