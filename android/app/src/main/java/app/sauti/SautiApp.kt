package app.sauti

import android.app.Application
import app.sauti.cloud.CloudClient
import app.sauti.device.DeviceProfile
import app.sauti.edge.EdgeEngine
import app.sauti.edge.ModelStore
import app.sauti.router.Router
import app.sauti.settings.SettingsRepository
import app.sauti.voice.Speaker

/** Tiny manual DI container: one instance of each service for the whole app. */
class SautiApp : Application() {
    lateinit var settings: SettingsRepository; private set
    lateinit var device: DeviceProfile; private set
    lateinit var models: ModelStore; private set
    lateinit var edge: EdgeEngine; private set
    lateinit var cloud: CloudClient; private set
    lateinit var router: Router; private set
    lateinit var speaker: Speaker; private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        device = DeviceProfile(this)
        models = ModelStore(this)
        edge = EdgeEngine(this)
        cloud = CloudClient()
        router = Router(device)
        speaker = Speaker(this)
    }
}
