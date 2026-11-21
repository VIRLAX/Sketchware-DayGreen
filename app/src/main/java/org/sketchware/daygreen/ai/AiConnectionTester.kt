package org.sketchware.daygreen.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object AiConnectionTester {

    interface Callback {
        fun onResult(success: Boolean, message: String)
    }

    interface MultiKeyCallback {
        fun onResult(validKeys: List<String>, invalidKeys: List<String>, rateLimitedKeys: List<String>, message: String)
    }

    private val CLIENT = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @JvmStatic
    fun test(providerIndex: Int, apiKey: String, callback: Callback) {
        Thread {
            val request = buildRequest(providerIndex, apiKey)
            if (request == null) {
                callback.onResult(false, "Provider tidak dikenal")
                return@Thread
            }
            try {
                CLIENT.newCall(request).execute().use { response ->
                    val code = response.code
                    if (code == 200) {
                        callback.onResult(true, "Terhubung (HTTP 200 OK)")
                    } else if (code == 401 || code == 403) {
                        callback.onResult(false, "API key tidak valid (HTTP $code)")
                    } else if (code == 429) {
                        callback.onResult(false, "Kuota habis / Rate limit (HTTP 429)")
                    } else {
                        callback.onResult(false, "Gagal (HTTP $code)")
                    }
                }
            } catch (e: IOException) {
                callback.onResult(false, "Kesalahan jaringan: ${e.message}")
            }
        }.start()
    }

    @JvmStatic
    fun testMultiKeys(providerIndex: Int, rawInput: String, callback: MultiKeyCallback) {
        Thread {
            val candidateKeys = rawInput.split(Regex("[\\n,;]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            if (candidateKeys.isEmpty()) {
                callback.onResult(emptyList(), emptyList(), emptyList(), "Masukkan API key terlebih dahulu.")
                return@Thread
            }

            val validKeys = mutableListOf<String>()
            val invalidKeys = mutableListOf<String>()
            val rateLimitedKeys = mutableListOf<String>()

            for (key in candidateKeys) {
                val req = buildRequest(providerIndex, key)
                if (req == null) {
                    invalidKeys.add(key)
                    continue
                }
                try {
                    CLIENT.newCall(req).execute().use { res ->
                        when (res.code) {
                            200 -> validKeys.add(key)
                            429 -> rateLimitedKeys.add(key)
                            else -> invalidKeys.add(key)
                        }
                    }
                } catch (_: Exception) {
                    invalidKeys.add(key)
                }
            }

            val msg = if (validKeys.isNotEmpty()) {
                val rText = if (rateLimitedKeys.isNotEmpty()) " · ${rateLimitedKeys.size} limit (429)" else ""
                val iText = if (invalidKeys.isNotEmpty()) " · ${invalidKeys.size} invalid" else ""
                "${validKeys.size} Key Aktif (HTTP 200 OK)$rText$iText"
            } else if (rateLimitedKeys.isNotEmpty()) {
                "Semua key (${rateLimitedKeys.size}) terkena rate limit (HTTP 429)"
            } else {
                "Semua API key tidak valid / gagal (HTTP != 200)"
            }

            callback.onResult(validKeys, invalidKeys, rateLimitedKeys, msg)
        }.start()
    }

    fun buildRequest(providerIndex: Int, apiKey: String): Request? {
        return when (providerIndex) {
            0 -> Request.Builder()
                .url("https://api.openai.com/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .build()
            1 -> Request.Builder()
                .url("https://api.anthropic.com/v1/models")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .build()
            2 -> Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/openai/models")
                .header("Authorization", "Bearer $apiKey")
                .header("x-goog-api-key", apiKey)
                .build()
            3 -> Request.Builder()
                .url("https://api.deepseek.com/models")
                .header("Authorization", "Bearer $apiKey")
                .build()
            4 -> Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .build()
            5 -> Request.Builder()
                .url("https://api.moonshot.ai/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .build()
            else -> null
        }
    }
}
