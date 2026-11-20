package app.sauti.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.sauti.R
import app.sauti.ui.orbs.OrbState
import app.sauti.ui.orbs.ThinkingOrb
import app.sauti.ui.theme.Sauti
import app.sauti.ui.theme.Type
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Which orb animation tells the user what Sauti is doing. */
fun Activity.orb(): OrbState = when (this) {
    Activity.NO_MODEL -> OrbState.BREATHING
    Activity.LOADING_MODEL -> OrbState.SHAPING     // circle → triangle → square: the model takes shape
    Activity.IMPORTING -> OrbState.WEAVING         // strands plaiting: the file being brought in
    Activity.IDLE -> OrbState.BREATHING            // calm presence, ready
    Activity.LISTENING -> OrbState.LISTENING       // waveform rolling through the rings
    Activity.UNDERSTANDING -> OrbState.WORKING     // particles at work: speech → meaning
    Activity.THINKING -> OrbState.SOLVING          // bands scramble then click into place
    Activity.ANSWERING -> OrbState.COMPOSING       // undulating sash: words flowing out
    Activity.SPEAKING -> OrbState.COMPOSING        // the same sash, while the reply is spoken aloud
    Activity.ONLINE -> OrbState.CONNECTING         // constellation wiring up: the community server
}

@Composable
fun Activity.label(): String = stringResource(
    when (this) {
        Activity.NO_MODEL -> R.string.act_no_model
        Activity.LOADING_MODEL -> R.string.act_loading
        Activity.IMPORTING -> R.string.act_importing
        Activity.IDLE -> R.string.act_idle
        Activity.LISTENING -> R.string.act_listening
        Activity.UNDERSTANDING -> R.string.act_understanding
        Activity.THINKING -> R.string.act_thinking
        Activity.ANSWERING -> R.string.act_answering
        Activity.SPEAKING -> R.string.act_speaking
        Activity.ONLINE -> R.string.act_online
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(ui: UiState, vm: ChatViewModel, voiceMode: Boolean, onMic: () -> Unit, onSettings: () -> Unit, onHistory: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(ui.messages.size, ui.messages.lastOrNull()?.text?.length) {
        if (ui.messages.isNotEmpty()) list.animateScrollToItem(ui.messages.lastIndex)
    }
    val active = ui.activity !in setOf(Activity.IDLE, Activity.NO_MODEL)
    // the listening orb speeds up with your voice level
    val orbSpeed = when (ui.activity) {
        Activity.LISTENING -> 0.7 + 2.0 * ui.level
        Activity.SPEAKING -> 1.4
        else -> 1.0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThinkingOrb(ui.activity.orb(), size = 22.dp, preset = 20,
                            paused = ui.activity == Activity.NO_MODEL, label = ui.activity.label(),
                            dark = true, tint = ui.activity.tint())
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.app_name), style = Type.headlineMd.copy(fontWeight = FontWeight.Light))
                            Text(statusText(ui).uppercase(), style = Type.labelCaps, color = Sauti.secondary.copy(alpha = 0.85f), maxLines = 1)
                        }
                    }
                },
                actions = {
                    // Voice ⇄ Text: how you talk to Sauti, and whether it talks back
                    // On narrow (cheap) phones the toggle shows icons only, so the title never gets squeezed.
                    val compact = LocalConfiguration.current.screenWidthDp < 440
                    SingleChoiceSegmentedButtonRow(Modifier.padding(end = 4.dp)) {
                        SegmentedButton(
                            selected = voiceMode, onClick = { vm.setVoiceMode(true) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = { if (!compact) Icon(Icons.Default.RecordVoiceOver, null, Modifier.size(18.dp)) },
                        ) {
                            if (compact) Icon(Icons.Default.RecordVoiceOver, stringResource(R.string.voice_mode), Modifier.size(18.dp))
                            else Text(stringResource(R.string.voice_mode))
                        }
                        SegmentedButton(
                            selected = !voiceMode, onClick = { vm.setVoiceMode(false) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = { if (!compact) Icon(Icons.Default.Keyboard, null, Modifier.size(18.dp)) },
                        ) {
                            if (compact) Icon(Icons.Default.Keyboard, stringResource(R.string.text_mode), Modifier.size(18.dp))
                            else Text(stringResource(R.string.text_mode))
                        }
                    }
                    IconButton(onClick = onHistory) { Icon(Icons.Default.History, stringResource(R.string.history)) }
                    // "New conversation" only when there is a conversation to leave
                    if (ui.messages.isNotEmpty()) IconButton(onClick = { vm.newChat() }) {
                        Icon(Icons.Default.Add, stringResource(R.string.new_chat))
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sauti.surface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            if (ui.messages.isEmpty()) {
                // Empty chat: one big orb as Sauti's "face"
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LivingCore(ui.activity, ui.level, size = 236.dp)
                    FrequencyBars(ui.activity, ui.level)
                    Spacer(Modifier.height(10.dp))
                    Text(ui.activity.label().uppercase(), style = Type.labelCaps, color = Sauti.outline)
                    Spacer(Modifier.height(16.dp))
                    if (ui.activity == Activity.IDLE) Text(
                        stringResource(R.string.greeting), textAlign = TextAlign.Center,
                        style = Type.bodyLg, color = Sauti.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = list,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(ui.messages, key = { it.id }) { Bubble(it, ui.activity) }
                }
            }

            // Presence bar: what Sauti is doing right now, while it listens, thinks or talks
            AnimatedVisibility(visible = active && ui.messages.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LivingCore(ui.activity, ui.level, size = 64.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(ui.activity.label().uppercase(), style = Type.labelCaps, color = ui.activity.tint())
                        FrequencyBars(ui.activity, ui.level, Modifier.height(20.dp))
                    }
                }
            }

            if (voiceMode) VoiceBar(ui, vm, onMic) else Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(28.dp)).background(Sauti.surfaceHigh.copy(alpha = 0.75f))
                    .border(1.dp, Sauti.hairline, RoundedCornerShape(28.dp)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.input_hint), color = Sauti.outline) },
                    enabled = ui.engine == EngineState.READY && !ui.recording,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.size(6.dp))
                FilledIconButton(onClick = { vm.send(input.trim()); input = "" }, enabled = !ui.busy && input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
                }
            }
        }
    }

    ui.consent?.let { c ->
        AlertDialog(
            onDismissRequest = { vm.consent(false) },
            icon = { ThinkingOrb(OrbState.CONNECTING, size = 40.dp, preset = 64, dark = true, tint = Sauti.primaryContainer) },
            title = { Text(stringResource(R.string.consent_title)) },
            text = { Text(stringResource(R.string.consent_body) + "\n\n(" + c.reason + ")") },
            confirmButton = { TextButton(onClick = { vm.consent(true) }) { Text(stringResource(R.string.consent_yes)) } },
            dismissButton = { TextButton(onClick = { vm.consent(false) }) { Text(stringResource(R.string.consent_no)) } },
        )
    }
}

/** Voice mode input: one big mic (no typing needed), plus "stop talking" while Sauti speaks. */
@Composable
private fun VoiceBar(ui: UiState, vm: ChatViewModel, onMic: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (ui.activity == Activity.SPEAKING) {
            FilledTonalButton(onClick = { vm.stopSpeaking() }) {
                Icon(Icons.AutoMirrored.Filled.VolumeOff, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.stop_speaking))
            }
            Spacer(Modifier.height(8.dp))
        }
        val micOn = ui.engine == EngineState.READY && (!ui.busy || ui.recording)
        Box(contentAlignment = Alignment.Center) {
            // radial aura behind the mic, cyan while recording
            Box(Modifier.size(112.dp).background(Brush.radialGradient(listOf(
                (if (ui.recording) Sauti.secondary else Sauti.spectralViolet).copy(alpha = if (micOn) 0.35f else 0.1f),
                Color.Transparent)), CircleShape))
            IconButton(
                onClick = { if (ui.recording) vm.stopRecording() else onMic() },
                enabled = micOn,
                modifier = Modifier.size(76.dp).clip(CircleShape).background(
                    if (micOn) Brush.linearGradient(
                        if (ui.recording) listOf(Sauti.secondaryContainer, Sauti.secondary)
                        else listOf(Sauti.spectralViolet, Sauti.secondaryContainer))
                    else Brush.linearGradient(listOf(Sauti.surfaceHigh, Sauti.surfaceHigh)),
                ).border(1.dp, Sauti.hairlineStrong, CircleShape),
            ) {
                Icon(
                    if (ui.recording) Icons.Default.Stop else Icons.Default.Mic,
                    stringResource(if (ui.recording) R.string.stop else R.string.record),
                    Modifier.size(34.dp), tint = if (micOn) Color.White else Sauti.outline,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (ui.recording) R.string.tap_to_stop else R.string.tap_to_speak).uppercase(),
            style = Type.labelCaps, color = Sauti.outline,
        )
    }
}

@Composable
private fun statusText(ui: UiState): String = when (ui.engine) {
    EngineState.NO_MODEL -> stringResource(R.string.status_no_model)
    EngineState.LOADING -> stringResource(R.string.status_loading)
    EngineState.READY -> stringResource(R.string.status_offline) + " · " + ui.engineDetail
    EngineState.ERROR -> "⚠ " + ui.engineDetail.take(60)
}

@Composable
private fun Bubble(m: ChatMessage, activity: Activity) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
            if (!m.fromUser) Text(stringResource(R.string.app_name).uppercase(), style = Type.labelCaps, color = Sauti.outline,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Surface(
                shape = if (m.fromUser) RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp) else RoundedCornerShape(18.dp),
                color = if (m.fromUser) Sauti.surfaceHigh.copy(alpha = 0.6f) else Sauti.surfaceLow.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (m.fromUser) Sauti.hairlineStrong else Sauti.hairline),
                modifier = Modifier.widthIn(max = if (m.fromUser) 300.dp else 360.dp),
            ) {
                if (!m.fromUser && m.text.isEmpty()) {
                    // waiting for the first words: a small inline orb instead of "…"
                    val orb = if (m.where == Where.ONLINE) OrbState.CONNECTING
                    else if (activity == Activity.ANSWERING) OrbState.COMPOSING else OrbState.SOLVING
                    ThinkingOrb(orb, Modifier.padding(14.dp), size = 22.dp, preset = 20, dark = true,
                        tint = if (m.where == Where.ONLINE) Sauti.primaryContainer else activity.tint())
                } else {
                    Text(
                        if (m.fromUser) AnnotatedString(m.text) else markdownLite(m.text),
                        Modifier.padding(horizontal = 16.dp, vertical = if (m.fromUser) 10.dp else 14.dp),
                        style = if (m.fromUser) Type.bodyMd else Type.bodyLg,
                        color = if (m.fromUser) Color.White else Sauti.onSurface.copy(alpha = 0.92f),
                    )
                }
            }
            m.where?.let { w ->
                Row(Modifier.padding(start = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (w == Where.DEVICE) Icons.Default.PhoneAndroid else Icons.Default.Cloud, null,
                            Modifier.size(12.dp), tint = if (w == Where.DEVICE) Sauti.secondary else Sauti.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (w == Where.DEVICE) R.string.answered_on_device else R.string.answered_online) +
                                (m.lang?.let { " · " + stringResource(when (it) {
                                    app.sauti.lang.Lang.ENGLISH -> R.string.lang_en
                                    app.sauti.lang.Lang.SWAHILI -> R.string.lang_sw
                                    app.sauti.lang.Lang.SHENG -> R.string.lang_sheng
                                }) } ?: "") +
                                if (m.note == "offline-fallback") " · " + stringResource(R.string.queued_offline) else "",
                            style = Type.labelSm, color = Sauti.outline,
                        )
                }
            }
        }
    }
}

/** Minimal markdown for model output: **bold**, and "* " / "- " bullets → "• ". */
fun markdownLite(src: String): AnnotatedString = buildAnnotatedString {
    val text = src.lines().joinToString("\n") { line ->
        val t = line.trimStart()
        if (t.startsWith("* ") || t.startsWith("- ")) "• " + t.drop(2) else line
    }
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("**", i)
        if (start < 0) { append(text.substring(i)); break }
        val end = text.indexOf("**", start + 2)
        if (end < 0) { append(text.substring(i)); break }
        append(text.substring(i, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(start + 2, end)) }
        i = end + 2
    }
}
