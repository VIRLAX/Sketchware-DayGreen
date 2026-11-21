package org.sketchware.daygreen.ai

import org.json.JSONObject
import java.util.Locale
import java.util.TreeSet
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

object AiModelFetcher {

    interface Callback {
        fun onResult(models: List<String>)
        fun onError(error: String)
    }

    private val HTTP = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

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
            if (provider.modelsUrl == null) {
                callback.onError("Provider ${provider.name} belum mendukung pengambilan daftar model dinamis.")
                return@Thread
            }
            if (keys.isEmpty()) {
                callback.onError("API key belum diisi.")
                return@Thread
            }

            var lastError = "Gagal mengambil model."
            val ids = TreeSet<String>()

            // Try keys until one succeeds with HTTP 200
            for (key in keys) {
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
                        val code = response.code
                        val body = response.body?.string() ?: ""
                        if (code == 200) {
                            val data = JSONObject(body).optJSONArray("data")
                            if (data != null) {
                                for (i in 0 until data.length()) {
                                    var id = data.getJSONObject(i).optString("id", "")
                                    if (id.startsWith("models/")) {
                                        id = id.substring("models/".length)
                                    }
                                    if (id.isNotEmpty() && isChatModel(id)) {
                                        ids.add(id)
                                    }
                                }
                            }
                            if (ids.isNotEmpty()) {
                                val list = ArrayList(ids)
                                store?.setVerifiedModels(store.providerIndex, list)
                                callback.onResult(list)
                                return@Thread
                            }
                        } else if (code == 429) {
                            lastError = "Rate limit (HTTP 429). Mencoba key berikutnya..."
                            // continue loop to next key
                        } else {
                            lastError = "HTTP $code: $body"
                        }
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: e.toString()
                }
            }

            callback.onError(lastError)
        }.start()
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
