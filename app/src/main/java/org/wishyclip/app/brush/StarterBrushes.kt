package org.wishyclip.app.brush

import java.util.Random

/*
 * The WishyClip starter brush pack: 16 ORIGINAL brushes generated in code (no third-party brush
 * files, textures or names are used). Pure Kotlin, deterministic (fixed seeds) and unit-tested.
 *
 * Every brush is a [ParsedBrush]: a generated alpha tip plus dab-engine settings, so they are
 * installed into the normal brush library and behave like any imported brush (they can be
 * tuned or deleted by the user). Pressure response comes from the shared dab engine.
 */
object StarterBrushes {

    /** Bump when the pack changes; the installer adds the pack once per version. */
    const val VERSION = 1

    private const val SIDE = 128

    fun all(): List<ParsedBrush> = listOf(
        // ---- Pencils
        pencil("Graphite HB", hardness = 0.45f, grain = 0.55f, spacing = 0.07f, flow = 0.50f, seed = 101),
        pencil("Graphite 6B", hardness = 0.20f, grain = 0.40f, spacing = 0.06f, flow = 0.62f, seed = 102),
        pencil("Sketch pencil", hardness = 0.30f, grain = 0.70f, spacing = 0.09f, flow = 0.42f, seed = 103, jitter = 0.18f),
        pencil("Colour pencil", hardness = 0.72f, grain = 0.50f, spacing = 0.05f, flow = 0.75f, seed = 104, fine = true),
        // ---- Inks
        ink("Technical pen", hardness = 0.97f, spacing = 0.05f),
        ink("Ink liner", hardness = 0.88f, spacing = 0.04f),
        brushPen("Brush pen"),
        dryInk("Dry ink"),
        // ---- Dry media
        pencil("Charcoal stick", hardness = 0.40f, grain = 1.00f, spacing = 0.07f, flow = 0.70f, seed = 105, jitter = 0.10f, scatter = 0.04f, cell = 3, contrast = 2.4f),
        pencil("Pastel", hardness = 0.50f, grain = 0.90f, spacing = 0.10f, flow = 0.65f, seed = 106, scatter = 0.03f, cell = 8, contrast = 1.8f),
        // ---- Paint
        paint("Gouache", hardness = 0.80f, spacing = 0.05f, flow = 0.92f),
        flat("Oil flat"),
        bristle("Bristle"),
        // ---- Texture / effects
        splatter("Splatter"),
        stipple("Stipple"),
        spray("Spray grain")
    )

    // ------------------------------------------------------------------ builders

    private fun pencil(
        name: String, hardness: Float, grain: Float, spacing: Float, flow: Float, seed: Long,
        jitter: Float = 0.08f, scatter: Float = 0.02f,
        fine: Boolean = false, cell: Int = 5, contrast: Float = 1f
    ) = ParsedBrush(
        name = name,
        tip = tip { x, y, d -> falloff(d, hardness) * grainAt(x, y, grain, seed, fine, cell, contrast) },
        spacing = spacing, scatter = scatter, sizeJitter = jitter, flow = flow
    )

    private fun ink(name: String, hardness: Float, spacing: Float) = ParsedBrush(
        name = name,
        tip = tip { _, _, d -> falloff(d, hardness) },
        spacing = spacing, flow = 1f
    )

    /** A thin ellipse that turns with the stroke, like a soft brush pen. */
    private fun brushPen(name: String) = ParsedBrush(
        name = name,
        tip = tipXY { nx, ny -> falloff(Math.hypot(nx.toDouble(), (ny / 0.45f).toDouble()).toFloat(), 0.6f) },
        spacing = 0.05f, rotateWithStroke = true, flow = 1f
    )

    private fun dryInk(name: String) = ParsedBrush(
        name = name,
        tip = tip { x, y, d -> falloff(d, 0.85f) * grainAt(x, y, 0.8f, 201, fine = true, contrast = 1.5f) },
        spacing = 0.05f, scatter = 0.01f, flow = 0.95f
    )

    private fun paint(name: String, hardness: Float, spacing: Float, flow: Float) = ParsedBrush(
        name = name,
        tip = tip { x, y, d -> falloff(d, hardness) * grainAt(x, y, 0.12f, 301) },
        spacing = spacing, flow = flow
    )

    /** Flat chisel at a fixed angle with a faint bristle grain. */
    private fun flat(name: String) = ParsedBrush(
        name = name,
        tip = tipXY { nx, ny ->
            val edgeX = smooth((1f - Math.abs(nx)) / 0.08f)
            val edgeY = smooth((0.42f - Math.abs(ny)) / 0.08f)
            edgeX * edgeY * (0.85f + 0.15f * grainAt(((nx + 1f) * 64f).toInt(), ((ny + 1f) * 64f).toInt(), 0.5f, 401))
        },
        spacing = 0.06f, angle = 35f, flow = 0.95f
    )

    /** A round brush made of streaky bristles (random stripe strength per column). */
    private fun bristle(name: String): ParsedBrush {
        val rnd = Random(501)
        val stripe = FloatArray(SIDE) { 0.35f + 0.65f * rnd.nextFloat() }
        return ParsedBrush(
            name = name,
            tip = tip { x, _, d -> falloff(d, 0.7f) * stripe[x.coerceIn(0, SIDE - 1)] },
            spacing = 0.05f, rotateWithStroke = true, flow = 0.9f
        )
    }

    /** A cluster of random blobs: scatters into drops when stroked. */
    private fun splatter(name: String): ParsedBrush {
        val rnd = Random(601)
        val blobs = List(14) {
            val a = rnd.nextDouble() * Math.PI * 2
            val r = Math.sqrt(rnd.nextDouble()) * 0.8
            Triple((Math.cos(a) * r).toFloat(), (Math.sin(a) * r).toFloat(), 0.05f + 0.16f * rnd.nextFloat())
        }
        return ParsedBrush(
            name = name,
            tip = tipXY { nx, ny ->
                var v = 0f
                for ((bx, by, br) in blobs) {
                    val dd = Math.hypot((nx - bx).toDouble(), (ny - by).toDouble()).toFloat()
                    v = Math.max(v, falloff(dd / br, 0.7f))
                }
                v
            },
            spacing = 0.55f, scatter = 0.6f, sizeJitter = 0.55f, flow = 1f
        )
    }

    private fun stipple(name: String) = ParsedBrush(
        name = name,
        tip = tip { _, _, d -> falloff(d, 0.9f) },
        spacing = 0.9f, scatter = 0.5f, sizeJitter = 0.5f, flow = 1f
    )

    private fun spray(name: String) = ParsedBrush(
        name = name,
        tip = tip { x, y, d -> falloff(d, 0.0f) * (if (hash(x, y, 701) < 0.30f) 1f else 0f) },
        spacing = 0.10f, scatter = 0.25f, sizeJitter = 0.2f, flow = 0.8f
    )

    // ------------------------------------------------------------------ tip helpers

    /** [f] gets pixel x, y and the normalised distance from the centre (0 at centre, 1 at the rim). */
    private fun tip(f: (x: Int, y: Int, d: Float) -> Float): TipMask {
        val out = ByteArray(SIDE * SIDE)
        val c = (SIDE - 1) / 2f
        val r = SIDE / 2f
        for (y in 0 until SIDE) {
            for (x in 0 until SIDE) {
                val d = Math.hypot((x - c).toDouble(), (y - c).toDouble()).toFloat() / r
                out[y * SIDE + x] = toByte(f(x, y, d))
            }
        }
        return TipMask(SIDE, SIDE, out)
    }

    /** [f] gets coordinates normalised to -1..1 on both axes. */
    private fun tipXY(f: (nx: Float, ny: Float) -> Float): TipMask {
        val out = ByteArray(SIDE * SIDE)
        val c = (SIDE - 1) / 2f
        val r = SIDE / 2f
        for (y in 0 until SIDE) {
            for (x in 0 until SIDE) {
                out[y * SIDE + x] = toByte(f((x - c) / r, (y - c) / r))
            }
        }
        return TipMask(SIDE, SIDE, out)
    }

    /** 1 inside [hardness] of the radius, fading to 0 at the rim, 0 outside. */
    private fun falloff(d: Float, hardness: Float): Float = when {
        d >= 1f -> 0f
        d <= hardness -> 1f
        else -> 1f - (d - hardness) / (1f - hardness).coerceAtLeast(0.001f)
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun toByte(v: Float): Byte = Math.round(v.coerceIn(0f, 1f) * 255f).toByte()

    /** Deterministic 0..1 noise for a pixel. */
    private fun hash(x: Int, y: Int, seed: Long): Float {
        var h = x * 374761393L + y * 668265263L + seed * 2246822519L
        h = (h xor (h ushr 13)) * 1274126177L
        h = h xor (h ushr 16)
        return ((h and 0xFFFFFF).toInt()) / 16777216f
    }

    /**
     * Paper-like grain: blocky value noise (4 px cells) mixed with fine noise. Returns a multiplier
     * that is 1 where the graphite catches and lower where it skips; [amount] 0 = no grain.
     */
    private fun grainAt(
        x: Int, y: Int, amount: Float, seed: Long,
        fine: Boolean = false, cell: Int = 5, contrast: Float = 1f
    ): Float {
        val detail = hash(x, y, seed + 7)
        var n = if (fine) detail else 0.6f * valueNoise(x, y, cell, seed) + 0.4f * detail
        n = ((n - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
        return 1f - amount * n
    }

    /** Smoothly interpolated random grid ("value noise"), so grain has no square cell edges. */
    private fun valueNoise(x: Int, y: Int, cell: Int, seed: Long): Float {
        val fx = x.toFloat() / cell
        val fy = y.toFloat() / cell
        val x0 = Math.floor(fx.toDouble()).toInt()
        val y0 = Math.floor(fy.toDouble()).toInt()
        val tx = smooth(fx - x0)
        val ty = smooth(fy - y0)
        val a = hash(x0, y0, seed)
        val b = hash(x0 + 1, y0, seed)
        val c = hash(x0, y0 + 1, seed)
        val d = hash(x0 + 1, y0 + 1, seed)
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty
    }
}
