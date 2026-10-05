package org.wishyclip.app.canvas

import android.graphics.Bitmap
import java.io.File
import java.nio.ByteBuffer
import net.jpountz.lz4.LZ4Factory

const val TILE_SIZE = 256

class TileStore(private val rootDir: File) {
    private val lz4 = LZ4Factory.fastestInstance()
    private val compressor = lz4.fastCompressor()
    private val decompressor = lz4.decompressor()
    private val pool = BitmapPool(32)

    private val memoryCache = HashMap<String, Bitmap>()

    fun tileKey(frameId: Long, layerId: Long, tx: Int, ty: Int): String =
        "${frameId}_${layerId}_${tx}_${ty}"

    fun getTile(frameId: Long, layerId: Long, tx: Int, ty: Int): Bitmap {
        val key = tileKey(frameId, layerId, tx, ty)
        memoryCache[key]?.let { if (!it.isRecycled) return it }

        val tileFile = File(rootDir, "tiles/$key.lz4")
        val bmp = pool.get(TILE_SIZE, TILE_SIZE)
        if (tileFile.exists()) {
            try {
                val compressed = tileFile.readBytes()
                val uncompressed = ByteArray(TILE_SIZE * TILE_SIZE * 4)
                decompressor.decompress(compressed, 0, uncompressed, 0, uncompressed.size)
                val buffer = ByteBuffer.wrap(uncompressed)
                bmp.copyPixelsFromBuffer(buffer)
            } catch (e: Exception) {
                bmp.eraseColor(0)
            }
        } else {
            bmp.eraseColor(0)
        }
        memoryCache[key] = bmp
        return bmp
    }

    fun saveTile(frameId: Long, layerId: Long, tx: Int, ty: Int, bmp: Bitmap) {
        val key = tileKey(frameId, layerId, tx, ty)
        memoryCache[key] = bmp

        val tileDir = File(rootDir, "tiles").apply { mkdirs() }
        val tileFile = File(tileDir, "$key.lz4")

        val buffer = ByteBuffer.allocate(TILE_SIZE * TILE_SIZE * 4)
        bmp.copyPixelsToBuffer(buffer)
        val raw = buffer.array()
        try {
            val maxLen = compressor.maxCompressedLength(raw.size)
            val compressed = ByteArray(maxLen)
            val len = compressor.compress(raw, 0, raw.size, compressed, 0, maxLen)
            tileFile.writeBytes(compressed.copyOf(len))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun evictFrame(frameId: Long) {
        val prefix = "${frameId}_"
        val keysToRemove = memoryCache.keys.filter { it.startsWith(prefix) }
        for (k in keysToRemove) {
            memoryCache.remove(k)?.let { pool.recycle(it) }
        }
    }

    fun clear() {
        for (bmp in memoryCache.values) {
            pool.recycle(bmp)
        }
        memoryCache.clear()
        pool.clear()
    }
}
