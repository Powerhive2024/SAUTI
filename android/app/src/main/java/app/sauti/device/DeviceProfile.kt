package app.sauti.device

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class ModelTier(val label: String, val fileHint: String) {
    E2B("Gemma 3n E2B (3–5 GB RAM)", "sauti-e2b"),
    E4B("Gemma 3n E4B (6 GB+ RAM)", "sauti-e4b"),
}

data class Connectivity(val online: Boolean, val unmetered: Boolean)

/** Facts about this phone used to pick the model tier and decide when going online is sensible. */
class DeviceProfile(private val context: Context) {

    val totalRamGb: Double by lazy {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        mi.totalMem / 1e9
    }

    /** E4B int4 needs roughly 3+ GB free while running; keep headroom for Android itself. */
    fun recommendedTier(): ModelTier = if (totalRamGb >= 7.0) ModelTier.E4B else ModelTier.E2B

    fun isLowRamDevice(): Boolean =
        context.getSystemService(ActivityManager::class.java).isLowRamDevice

    fun connectivity(): Connectivity {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return Connectivity(false, false)
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return Connectivity(online, unmetered)
    }
}
