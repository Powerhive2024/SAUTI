package app.sauti.media

import kotlin.math.roundToInt

/** Pure sizing/sampling rules for photos and video (no Android types, so they are unit-tested). */
object MediaMath {
    /** Gemma 3n's image encoder works on 768x768; bigger photos only cost memory and time. */
    const val MAX_SIDE = 768
    /** A video is shown to the model as this many still frames, spread evenly through the clip. */
    const val VIDEO_FRAMES = 4
    /** Most images one message may carry (a video's frames count too). */
    const val MAX_IMAGES = 5

    /** Width and height scaled so the longest side is at most [max]; small images are left alone. */
    fun fit(w: Int, h: Int, max: Int = MAX_SIDE): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 1 to 1
        if (w <= max && h <= max) return w to h
        val s = max.toDouble() / maxOf(w, h)
        return maxOf(1, (w * s).roundToInt()) to maxOf(1, (h * s).roundToInt())
    }

    /** Power-of-two decode step that keeps the image at least [max] on its longest side. */
    fun sampleSize(w: Int, h: Int, max: Int = MAX_SIDE): Int {
        var n = 1
        while (maxOf(w, h) / (n * 2) >= max) n *= 2
        return n
    }

    /** Frame times (microseconds) at the middle of [n] equal slices of a clip of [durationMs]. */
    fun frameTimesUs(durationMs: Long, n: Int = VIDEO_FRAMES): List<Long> {
        if (n <= 0) return emptyList()
        if (durationMs <= 0) return listOf(0L)
        return (0 until n).map { i -> durationMs * (2 * i + 1) / (2 * n) * 1000 }
    }
}
