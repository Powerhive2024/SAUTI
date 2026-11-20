package app.sauti.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sauti.R
import app.sauti.history.ConversationSummary
import app.sauti.ui.orbs.OrbState
import app.sauti.ui.orbs.ThinkingOrb
import app.sauti.ui.theme.Sauti
import app.sauti.ui.theme.Type

/** Past conversations, kept only on this phone: reopen one, start a new one, or delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<ConversationSummary>,
    currentId: String?,
    saving: Boolean,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), style = Type.headlineMd) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sauti.surface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            FilledTonalButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.new_chat))
            }
            Spacer(Modifier.height(12.dp))
            PrivacyNote(if (saving) R.string.history_privacy else R.string.history_save_off)
            Spacer(Modifier.height(12.dp))

            if (history.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ThinkingOrb(OrbState.BREATHING, size = 72.dp, preset = 64, dark = true, tint = Sauti.spectralViolet)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.history_empty), style = Type.bodyMd, color = Sauti.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(history, key = { it.id }) { c ->
                        HistoryRow(c, current = c.id == currentId, onOpen = { onOpen(c.id) }, onDelete = { confirmDelete = c })
                    }
                }
                TextButton(onClick = { confirmAll = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp), tint = Sauti.error)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.delete_all), color = Sauti.error)
                }
            }
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_chat_title)) },
            text = { Text("“" + c.title.ifBlank { stringResource(R.string.history_untitled) } + "”\n\n" + stringResource(R.string.delete_chat_body)) },
            confirmButton = {
                TextButton(onClick = { onDelete(c.id); confirmDelete = null }) { Text(stringResource(R.string.delete), color = Sauti.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmAll) DeleteAllDialog(history.size, onConfirm = { onDeleteAll(); confirmAll = false }, onDismiss = { confirmAll = false })
}

@Composable
fun DeleteAllDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_all_title)) },
        text = { Text(stringResource(R.string.delete_all_body, count)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = Sauti.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "Kept only on this phone…" — so people always see what is stored and where. */
@Composable
fun PrivacyNote(text: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(14.dp).padding(top = 2.dp), tint = Sauti.secondary)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(text), style = Type.bodySm, color = Sauti.onSurfaceVariant)
    }
}

@Composable
private fun HistoryRow(c: ConversationSummary, current: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (current) Sauti.surfaceHigh.copy(alpha = 0.7f) else Sauti.surfaceLow.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, if (current) Sauti.spectralViolet.copy(alpha = 0.6f) else Sauti.hairline),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    c.title.ifBlank { stringResource(R.string.history_untitled) },
                    style = Type.bodyMd, color = Sauti.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current) {
                        Text(stringResource(R.string.history_current).uppercase(), style = Type.labelCaps, color = Sauti.secondary)
                        Text(" · ", style = Type.labelSm, color = Sauti.outline)
                    }
                    Text(
                        DateUtils.getRelativeTimeSpanString(c.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() +
                            " · " + stringResource(R.string.history_messages, c.messageCount),
                        style = Type.labelSm, color = Sauti.outline, maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete), tint = Sauti.outline)
            }
        }
    }
}
