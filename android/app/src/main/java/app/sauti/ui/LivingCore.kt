package app.sauti.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sauti.ui.orbs.ThinkingOrb
import app.sauti.ui.theme.Sauti
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The colour of Sauti's presence for each activity (DESIGN.md: cyan = voice, mist = deliberation, violet = dialogue). */
fun Activity.tint(): Color = when (this) {
    Activity.LISTENING -> Sauti.secondary
    Activity.UNDERSTANDING, Activity.THINKING -> Sauti.tertiary
    Activity.ANSWERING, Activity.SPEAKING -> Color(0xFF9FE9FF)
    Activity.ONLINE -> Sauti.primaryContainer
    else -> Sauti.primary
}

/**
 * "The Living Core": the thinking-orb (its animation still says *what* Sauti is doing) inside a
 * breathing bioluminescent aura, with sound ripples while listening or speaking.
 */
@Composable
fun LivingCore(activity: Activity, level: Float, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val tint by animateColorAsState(activity.tint(), tween(700), label = "tint")
    val inf = rememberInfiniteTransition(label = "core")
    // idle breath ≈ 0.15 Hz (scale 0.96 → 1.04); talking breathes faster
    val breathMs = when (activity) { Activity.SPEAKING, Activity.ANSWERING -> 2200; Activity.THINKING -> 3200; else -> 6600 }
    val breath by inf.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(breathMs / 2), RepeatMode.Reverse), label = "breath")
    val ripple by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "ripple")
    val rippling = activity == Activity.LISTENING || activity == Activity.SPEAKING
    val speed = when (activity) {
        Activity.LISTENING -> 0.7 + 2.0 * level
        Activity.SPEAKING -> 1.4
        else -> 1.0
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            // rear atmospheric glow: violet core bleeding into the activity colour
            val glow = r * (0.78f + 0.12f * level) * breath
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.32f), Sauti.spectralViolet.copy(alpha = 0.14f), Color.Transparent),
                center, glow), glow)
            if (rippling) for (k in 0..1) {
                val p = (ripple + k * 0.5f) % 1f
                drawCircle(tint.copy(alpha = 0.35f * (1 - p)), r * (0.42f + 0.5f * p), style = Stroke(1.dp.toPx()))
            }
            // hairline ring the orb sits in
            drawCircle(Color.White.copy(alpha = 0.08f), r * 0.44f, style = Stroke(1.dp.toPx()))
        }
        Box(Modifier.size(size * 0.62f).scale(breath).background(Sauti.surfaceLowest.copy(alpha = 0.55f), CircleShape))
        ThinkingOrb(activity.orb(), Modifier.scale(breath), size = size * 0.5f, preset = 64, speed = speed,
            paused = activity == Activity.NO_MODEL, label = activity.label(), dark = true, tint = tint)
    }
}

/** Seven-bar "frequency" readout under the core: your real mic level while listening. */
@Composable
fun FrequencyBars(activity: Activity, level: Float, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(0L) }
    val moving = activity in setOf(Activity.LISTENING, Activity.SPEAKING, Activity.THINKING, Activity.UNDERSTANDING, Activity.ANSWERING)
    LaunchedEffect(moving) { while (moving) withFrameMillis { now = it } }
    val colors = listOf(Sauti.outline.copy(alpha = .4f), Sauti.primary.copy(alpha = .7f), Sauti.secondary,
        Color(0xFF00F2FE), Sauti.primary, Sauti.tertiary, Sauti.outline.copy(alpha = .4f))
    Row(modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEachIndexed { i, c ->
            val spread = sin(i / 6.0 * PI).toFloat()
            val t = now / 1000.0
            val h = when (activity) {
                Activity.LISTENING -> 4f + spread * 24f * level.coerceIn(0f, 1f) + 2f * abs(sin(t * 9 + i)).toFloat()
                Activity.SPEAKING, Activity.ANSWERING -> 6f + spread * 18f * (0.55f + 0.45f * sin(t * 6.5 + i * 1.3).toFloat())
                Activity.THINKING, Activity.UNDERSTANDING -> 10f + 7f * sin(t * 4.2 + i * 0.8).toFloat()
                else -> 4f + spread * 3f
            }
            Box(Modifier.width(4.dp).height(h.coerceIn(4f, 28f).dp).background(c, CircleShape))
        }
    }
}
