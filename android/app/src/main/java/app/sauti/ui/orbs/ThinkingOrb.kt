package app.sauti.ui.orbs

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One clock for every orb on screen, so several orbs stay in phase (as upstream). */
private val CLOCK_ORIGIN_NS = System.nanoTime()

/**
 * Animated dotted thought-orb (port of thinking-orbs). [preset] picks the tuned design (64 = avatar
 * scale, 20 = inline scale); [size] is the on-screen size — the tuned frame is drawn at its native
 * coordinates and scaled, so a 64 preset shown at 160.dp keeps its exact look, just bigger.
 * Monochrome ink follows light/dark; system "remove animations" gets a static frame.
 */
@Composable
fun ThinkingOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    preset: Int = if (size >= 40.dp) 64 else 20,
    dark: Boolean = isSystemInDarkTheme(),
    speed: Double = 1.0,
    paused: Boolean = false,
    label: String? = null,
    /** Colour the dots instead of monochrome ink (the brightness of each dot becomes its opacity). */
    tint: Color? = null,
) {
    val resolved = remember(state, preset) { resolveOrb(state, preset) }
    val context = LocalContext.current
    val reduced = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var t by remember { mutableDoubleStateOf(0.6) }

    LaunchedEffect(resolved, speed, paused, reduced) {
        if (reduced) { t = 0.6; return@LaunchedEffect }
        if (paused) return@LaunchedEffect
        while (true) {
            withFrameNanos { now -> t = (now - CLOCK_ORIGIN_NS) / 1e9 * resolved.speed * speed }
        }
    }

    Canvas(modifier.size(size).semantics { contentDescription = label ?: state.name.lowercase() }) {
        val frame = resolved.frame(t)
        val k = this.size.minDimension / resolved.size
        scale(k, pivot = Offset.Zero) {
            for (l in frame.lines) {
                drawLine(ink(l.white, l.a, dark, tint), Offset(l.x1.toFloat(), l.y1.toFloat()),
                    Offset(l.x2.toFloat(), l.y2.toFloat()), strokeWidth = l.w.toFloat())
            }
            for (d in frame.dots) {
                drawCircle(ink(d.white, d.a, dark, tint), radius = d.r.toFloat(), center = Offset(d.x.toFloat(), d.y.toFloat()))
            }
        }
    }
}

/** Upstream paint contract: grey = round(w·255), mirrored (1 − w) on dark substrates. */
private fun ink(white: Double, alpha: Double, dark: Boolean, tint: Color? = null): Color {
    val w = min(1.0, max(0.0, white))
    if (tint != null) {
        val lum = if (dark) 1 - w else w
        return tint.copy(alpha = (alpha.coerceIn(0.0, 1.0) * lum).toFloat() * tint.alpha)
    }
    val g = ((if (dark) 1 - w else w) * 255).roundToInt()
    return Color(g, g, g, (alpha.coerceIn(0.0, 1.0) * 255).roundToInt())
}
