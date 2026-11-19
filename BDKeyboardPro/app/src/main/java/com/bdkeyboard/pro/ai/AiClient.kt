package com.bdkeyboard.pro.ai

import com.bdkeyboard.pro.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class AiAction(val id: String, val label: String) {
    REWRITE("rewrite", "Rewrite"),
    FIX("fix", "Fix grammar"),
    FORMAL("formal", "Formal"),
    CONCISE("concise", "Concise"),
    EXPAND("expand", "Expand"),
    SUMMARIZE("summarize", "Summarize"),
    BN_TO_EN("bn_to_en", "বাংলা→EN"),
    EN_TO_BN("en_to_bn", "EN→বাংলা"),
    BANGLISH("banglish", "Banglish→বাংলা")
}

sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Error(val kind: Kind, val message: String) : AiResult()
    enum class Kind { OFFLINE, RATE_LIMIT, UNAVAILABLE, AUTH, UNKNOWN }
}

/**
 * Talks ONLY to our own backend proxy. Provider API keys live on the server.
 * Called only after the user selects text and taps an action.
 */
class AiClient(
    private val baseUrl: String = BuildConfig.AI_BASE_URL,
    private val tokenProvider: () -> String? = { null }
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun run(action: AiAction, text: String): AiResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("action", action.id).put("text", text).toString()
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/transform")
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply { tokenProvider()?.let { header("Authorization", "Bearer $it") } }
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> AiResult.Success(JSONObject(raw).optString("result"))
                    resp.code == 401 || resp.code == 403 -> AiResult.Error(AiResult.Kind.AUTH, "Sign-in required")
                    resp.code == 429 -> AiResult.Error(AiResult.Kind.RATE_LIMIT, "Too many requests. Try later.")
                    resp.code >= 500 -> AiResult.Error(AiResult.Kind.UNAVAILABLE, "Service unavailable")
                    else -> AiResult.Error(AiResult.Kind.UNKNOWN, "Error ${resp.code}")
                }
            }
        } catch (e: IOException) {
            AiResult.Error(AiResult.Kind.OFFLINE, "You appear to be offline")
        } catch (e: Exception) {
            AiResult.Error(AiResult.Kind.UNKNOWN, e.message ?: "Unknown error")
        }
    }
}
