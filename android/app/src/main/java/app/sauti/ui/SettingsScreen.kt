package app.sauti.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sauti.R
import app.sauti.settings.NetworkMode
import app.sauti.settings.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    ui: UiState,
    recommended: String,
    onMode: (NetworkMode) -> Unit,
    onServer: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    var server by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = {
                IconButton(onClick = { onServer(server); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.model_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.recommended_model, recommended), style = MaterialTheme.typography.bodySmall)
            Text(settings.modelPath?.substringAfterLast('/') ?: "—")
            settings.loraPath?.let { Text("+ " + it.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onImport) { Text(stringResource(R.string.import_model)) }
            ui.importProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            Text(stringResource(R.string.model_hint), style = MaterialTheme.typography.bodySmall)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.titleMedium)
            listOf(
                NetworkMode.OFFLINE_ONLY to R.string.mode_offline,
                NetworkMode.ASK to R.string.mode_ask,
                NetworkMode.AUTO_WIFI to R.string.mode_auto,
            ).forEach { (m, label) ->
                Row(
                    Modifier.fillMaxWidth().selectable(settings.mode == m) { onMode(m) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.mode == m, onClick = { onMode(m) })
                    Text(stringResource(label))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = server, onValueChange = { server = it },
                label = { Text(stringResource(R.string.server_url)) },
                enabled = settings.mode != NetworkMode.OFFLINE_ONLY,
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (ui.laterQueue.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("⏳ " + ui.laterQueue.size, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
