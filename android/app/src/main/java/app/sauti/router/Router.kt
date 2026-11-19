package app.sauti.router

import app.sauti.device.DeviceProfile
import app.sauti.settings.NetworkMode
import app.sauti.settings.Settings

enum class Route { EDGE, ASK_CONSENT, ONLINE }

data class Decision(val route: Route, val reason: String)

/**
 * Decides where a request runs. Edge is the default; the online tier is only considered when the
 * request genuinely needs it, the user's mode allows it, a server is configured and the network is up.
 */
class Router(private val device: DeviceProfile) {

    // Signals that a question needs fresh or external knowledge the offline model cannot have.
    private val freshness = Regex(
        """\b(leo|sasa hivi|wiki hii|habari (za|mpya)|bei ya|kiwango cha|hali ya hewa|matokeo|""" +
            """today|right now|this week|latest|news|price of|exchange rate|weather|results)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun decide(text: String, settings: Settings): Decision {
        if (settings.mode == NetworkMode.OFFLINE_ONLY) return Decision(Route.EDGE, "offline-only mode")
        if (settings.serverUrl.isBlank()) return Decision(Route.EDGE, "no community server configured")

        val needsOnline = when {
            freshness.containsMatchIn(text) -> "needs up-to-date information"
            text.length > 4000 -> "long document"
            else -> null
        } ?: return Decision(Route.EDGE, "on-device model can handle it")

        val net = device.connectivity()
        if (!net.online) return Decision(Route.EDGE, "no connection")
        return when (settings.mode) {
            NetworkMode.AUTO_WIFI ->
                if (net.unmetered) Decision(Route.ONLINE, needsOnline)
                else Decision(Route.ASK_CONSENT, "$needsOnline (mobile data)")
            else -> Decision(Route.ASK_CONSENT, needsOnline)
        }
    }

    /** After an on-device answer, offer escalation if the model itself signals it doesn't know. */
    fun edgeAnswerLooksUnsure(answer: String): Boolean =
        Regex("""\b(sijui|sina uhakika|siwezi kujua|I don't know|I'm not sure)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(answer)
}
