package com.sdrapp.ui

/** Waterfall/spectrum colour palettes (name -> colour ramp). */
internal fun paletteColor(name: String, v: Float): Int {
    val x = v.coerceIn(0f, 1f)
    return when (name) {
        "green" -> ramp(
            x,
            intArrayOf(0xFF000000.toInt(), 0xFF003300.toInt(), 0xFF00CC44.toInt(), 0xFFFFFFFF.toInt())
        )
        "blue" -> ramp(
            x,
            intArrayOf(0xFF000000.toInt(), 0xFF001060.toInt(), 0xFF00B0FF.toInt(), 0xFFFFFFFF.toInt())
        )
        "mono" -> ramp(
            x,
            intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        )
        "fire" -> ramp(
            x,
            intArrayOf(
                0xFF000000.toInt(), 0xFFAA0000.toInt(), 0xFFFF8800.toInt(),
                0xFFFFFF00.toInt(), 0xFFFFFFFF.toInt()
            )
        )
        else -> ramp(
            x,
            intArrayOf(
                0xFF000010.toInt(), 0xFF001030.toInt(), 0xFF0044AA.toInt(),
                0xFF00AA44.toInt(), 0xFFFFAA00.toInt(), 0xFFFF4400.toInt()
            )
        )
    }
}

internal val PALETTE_NAMES = arrayOf("classic", "green", "blue", "mono", "fire")

private fun ramp(x: Float, stops: IntArray): Int {
    if (stops.size == 1) return stops[0]
    val scaled = x * (stops.size - 1)
    val i = scaled.toInt().coerceIn(0, stops.size - 2)
    val f = scaled - i
    return lerpColor(stops[i], stops[i + 1], f)
}

private fun lerpColor(c0: Int, c1: Int, f: Float): Int {
    fun ch(c: Int, s: Int) = (c shr s) and 0xff
    val a = ch(c0, 24) + ((ch(c1, 24) - ch(c0, 24)) * f).toInt()
    val r = ch(c0, 16) + ((ch(c1, 16) - ch(c0, 16)) * f).toInt()
    val g = ch(c0, 8) + ((ch(c1, 8) - ch(c0, 8)) * f).toInt()
    val b = ch(c0, 0) + ((ch(c1, 0) - ch(c0, 0)) * f).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
