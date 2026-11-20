package app.sauti

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sauti.ui.ChatScreen
import app.sauti.ui.ChatViewModel
import app.sauti.ui.HistoryScreen
import app.sauti.ui.SettingsScreen
import app.sauti.ui.theme.SautiTheme

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startRecording()
        else Toast.makeText(this, R.string.mic_permission, Toast.LENGTH_LONG).show()
    }

    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        debugInjectVoice(intent)
    }

    /**
     * Debug builds only: `adb shell am start -n app.sauti/.MainActivity --es debug_wav <file in app files dir>`
     * sends a recorded 16 kHz WAV through exactly the same path as the microphone (emulators
     * usually can't hear the PC's mic).
     */
    private fun debugInjectVoice(intent: android.content.Intent?) {
        if (!BuildConfig.DEBUG) return
        val name = intent?.getStringExtra("debug_wav") ?: return
        val f = java.io.File(filesDir, name)
        if (f.exists()) vm.sendVoice(f.readBytes())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        debugInjectVoice(intent)
        setContent {
            SautiTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                val settings by vm.settings.collectAsStateWithLifecycle()
                val history by vm.history.collectAsStateWithLifecycle()
                var showSettings by remember { mutableStateOf(false) }
                var showHistory by remember { mutableStateOf(false) }

                if (showHistory) {
                    BackHandler { showHistory = false }
                    HistoryScreen(
                        history = history,
                        currentId = ui.conversationId,
                        saving = settings.saveHistory,
                        onOpen = { vm.openConversation(it); showHistory = false },
                        onNew = { vm.newChat(); showHistory = false },
                        onDelete = { vm.deleteConversation(it) },
                        onDeleteAll = { vm.deleteAllHistory() },
                        onBack = { showHistory = false },
                    )
                } else if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        settings = settings,
                        ui = ui,
                        recommended = vm.recommendedTier.label,
                        onMode = { vm.setMode(it) },
                        onServer = { vm.setServer(it) },
                        onImport = { pickModel.launch(arrayOf("*/*")) },
                        history = history,
                        onSaveHistory = { vm.setSaveHistory(it) },
                        onDeleteAll = { vm.deleteAllHistory() },
                        onBack = { showSettings = false },
                    )
                } else {
                    ChatScreen(
                        ui = ui,
                        vm = vm,
                        voiceMode = settings.voiceMode,
                        onMic = {
                            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) vm.startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onSettings = { showSettings = true },
                        onHistory = { showHistory = true },
                    )
                }
            }
        }
    }
}
