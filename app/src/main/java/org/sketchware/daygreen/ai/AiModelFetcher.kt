package org.sketchware.daygreen.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object AiModelFetcher {

    interface Callback {
        fun onResult(models: List<String>)
        fun onError(error: String)
    }

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private val HTTP = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val POOL = Executors.newFixedThreadPool(4)

    @JvmStatic
    fun fetch(provider: AiProviders.Provider, apiKey: String?, callback: Callback) {
        val keys = if (apiKey.isNullOrEmpty()) emptyList() else listOf(apiKey)
        fetchWithKeys(provider, keys, null, callback)
    }

    @JvmStatic
    fun fetchWithStore(provider: AiProviders.Provider, store: AiSettingsStore, providerIndex: Int, callback: Callback) {
        val keys = store.getApiKeys(providerIndex)
        fetchWithKeys(provider, keys, store, callback)
    }

    private fun fetchWithKeys(
        provider: AiProviders.Provider,
        keys: List<String>,
        store: AiSettingsStore?,
        callback: Callback
    ) {
        Thread {
            if (keys.isEmpty()) {
                callback.onError("API key belum diisi.")
                return@Thread
            }

            val key = keys[0]
            val candidateModels = getCandidateModels(provider, key)

            if (candidateModels.isEmpty()) {
                callback.onError("Tidak ada model yang ditemukan untuk provider ${provider.name}.")
                return@Thread
            }

            // Realtime 200 OK Ping Filter:
            // Test each candidate model. Discard anything returning 429, 404, 400, or errors!
            val verifiedQueue = ConcurrentLinkedQueue<String>()
            val latch = CountDownLatch(candidateModels.size)

            for (model in candidateModels) {
                POOL.execute {
                    try {
                        val is200 = testModelHealth(provider, key, model)
                        if (is200) {
                            verifiedQueue.add(model)
                        }
                    } catch (_: Exception) {
                    } finally {
                        latch.countDown()
                    }
                }
            }

            try {
                latch.await(15, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
            }

            val activeList = ArrayList(verifiedQueue).distinct()

            if (activeList.isNotEmpty()) {
                if (store != null) {
                    store.setVerifiedModels(store.providerIndex, activeList)
                    val current = store.getModelId("")
                    if (current.isEmpty() || !activeList.contains(current)) {
                        store.setModelId(activeList[0])
                    }
                }
                callback.onResult(activeList)
            } else {
                callback.onError("Semua model terdeteksi rate limit (HTTP 429) atau kuota habis.")
            }
        }.start()
    }

    private fun getCandidateModels(provider: AiProviders.Provider, key: String): List<String> {
        val candidates = mutableListOf<String>()

        // 1. Try querying /models endpoint if available
        if (provider.modelsUrl != null) {
            val reqBuilder = Request.Builder().url(provider.modelsUrl)
            if (provider.name.contains("Gemini", ignoreCase = true)) {
                reqBuilder.header("Authorization", "Bearer $key")
                reqBuilder.header("x-goog-api-key", key)
            } else if (provider.name.contains("Anthropic", ignoreCase = true)) {
                reqBuilder.header("x-api-key", key)
                reqBuilder.header("anthropic-version", "2023-06-01")
            } else {
                reqBuilder.header("Authorization", "Bearer $key")
            }

            try {
                HTTP.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.code == 200) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val data = json.optJSONArray("data")
                        if (data != null) {
                            for (i in 0 until data.length()) {
                                var id = data.getJSONObject(i).optString("id", "")
                                if (id.startsWith("models/")) {
                                    id = id.substring("models/".length)
                                }
                                if (id.isNotEmpty() && isChatModel(id)) {
                                    candidates.add(id)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Add provider default models if not already present
        for (m in provider.models) {
            if (!candidates.contains(m)) {
                candidates.add(m)
            }
        }

        // Limit candidate testing to top 8 most relevant to keep latency fast
        return candidates.distinct().take(8)
    }

    private fun testModelHealth(provider: AiProviders.Provider, key: String, model: String): Boolean {
        if (provider.baseUrl == null && !provider.name.contains("Anthropic", ignoreCase = true)) {
            return true
        }

        return if (provider.name.contains("Anthropic", ignoreCase = true)) {
            val body = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "1")))
            }
            val req = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()
            HTTP.newCall(req).execute().use { it.code == 200 }
        } else {
            val body = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "1")))
            }
            val reqBuilder = Request.Builder()
                .url(provider.baseUrl ?: "")
                .post(body.toString().toRequestBody(JSON_TYPE))

            if (provider.name.contains("Gemini", ignoreCase = true)) {
                reqBuilder.header("Authorization", "Bearer $key")
                reqBuilder.header("x-goog-api-key", key)
            } else {
                reqBuilder.header("Authorization", "Bearer $key")
            }
            HTTP.newCall(reqBuilder.build()).execute().use { it.code == 200 }
        }
    }

    private fun isChatModel(id: String): Boolean {
        val lower = id.lowercase(Locale.ROOT)
        return !(lower.contains("embed")
            || lower.contains("tts")
            || lower.contains("whisper")
            || lower.contains("transcribe")
            || lower.contains("dall-e")
            || lower.contains("image")
            || lower.contains("moderation")
            || lower.contains("audio")
            || lower.contains("realtime")
            || lower.contains("babbage")
            || lower.contains("davinci"))
    }
}
