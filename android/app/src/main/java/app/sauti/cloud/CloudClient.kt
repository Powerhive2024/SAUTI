package app.sauti.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Online tier: our own self-hosted Sauti model (bigger fine-tuned Gemma) behind an
 * OpenAI-compatible endpoint (e.g. vLLM / llama.cpp server). Never a frontier-model API.
 * Only redacted *text* is sent; requests carry "store": false and no user identifiers.
 */
class CloudClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class Turn(val role: String, val text: String)

    suspend fun complete(serverUrl: String, history: List<Turn>, systemPrompt: String): String =
        withContext(Dispatchers.IO) {
            val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.takeLast(8).forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.text)) }
            val body = JSONObject()
                .put("model", "sauti")
                .put("messages", msgs)
                .put("max_tokens", 512)
                .put("temperature", 0.6)
                .put("store", false)
                .toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url(serverUrl.trimEnd('/') + "/v1/chat/completions")
                .post(body)
                .build()
            http.newCall(req).execute().use { r ->
                check(r.isSuccessful) { "Server error ${r.code}" }
                JSONObject(r.body!!.string())
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
            }
        }
}
