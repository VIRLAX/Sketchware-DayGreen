package org.sketchware.daygreen.ai

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicInteger

class AiSettingsStore(context: Context) {

    companion object {
        private const val PREFS = "ai_settings"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_MODEL = "model"
        private const val KEY_API_KEY = "api_key_"
        private const val KEY_API_KEYS = "api_keys_"
        private const val KEY_VERIFIED = "verified_"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_PERMISSION = "permission"
        private const val KEY_TOOL = "tool_"
        private const val KEY_MODEL_ID = "model_id"
        private const val KEY_AGENT_TOOLS_ENABLED = "agent_tools_enabled"
        private const val KEY_TOKEN_SAVER_ENABLED = "token_saver_enabled"
        private const val KEY_AUTO_FAILOVER_ENABLED = "auto_failover_enabled"
        private const val KEY_ROUND_ROBIN_ENABLED = "round_robin_enabled"
        private const val KEY_VERIFIED_MODELS = "verified_models_"

        // Thread-safe in-memory counters for round-robin rotation per provider
        private val ROUND_ROBIN_COUNTERS = Array(10) { AtomicInteger(0) }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var providerIndex: Int
        get() = prefs.getInt(KEY_PROVIDER, 0)
        set(index) = prefs.edit().putInt(KEY_PROVIDER, index).apply()

    var modelIndex: Int
        get() = prefs.getInt(KEY_MODEL, 0)
        set(index) = prefs.edit().putInt(KEY_MODEL, index).apply()

    fun getApiKey(providerIndex: Int): String {
        val keys = getApiKeys(providerIndex)
        return if (keys.isNotEmpty()) keys[0] else prefs.getString(KEY_API_KEY + providerIndex, "") ?: ""
    }

    fun setApiKey(providerIndex: Int, key: String) {
        prefs.edit().putString(KEY_API_KEY + providerIndex, key).apply()
        if (key.isNotEmpty()) {
            setApiKeys(providerIndex, listOf(key))
        } else {
            setApiKeys(providerIndex, emptyList())
        }
    }

    fun getApiKeys(providerIndex: Int): List<String> {
        val raw = prefs.getString(KEY_API_KEYS + providerIndex, null)
        if (!raw.isNullOrEmpty()) {
            return try {
                val array = JSONArray(raw)
                val list = ArrayList<String>()
                for (i in 0 until array.length()) {
                    val k = array.getString(i).trim()
                    if (k.isNotEmpty()) list.add(k)
                }
                if (list.isNotEmpty()) list else fallbackSingleKey(providerIndex)
            } catch (_: Exception) {
                fallbackSingleKey(providerIndex)
            }
        }
        return fallbackSingleKey(providerIndex)
    }

    private fun fallbackSingleKey(providerIndex: Int): List<String> {
        val single = prefs.getString(KEY_API_KEY + providerIndex, "") ?: ""
        return if (single.isNotBlank()) listOf(single.trim()) else emptyList()
    }

    fun setApiKeys(providerIndex: Int, keys: List<String>) {
        val distinct = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val array = JSONArray(distinct)
        val single = if (distinct.isNotEmpty()) distinct[0] else ""
        prefs.edit()
            .putString(KEY_API_KEYS + providerIndex, array.toString())
            .putString(KEY_API_KEY + providerIndex, single)
            .apply()
    }

    fun getNextApiKey(providerIndex: Int): String {
        val keys = getApiKeys(providerIndex)
        if (keys.isEmpty()) return ""
        if (keys.size == 1 || !isRoundRobinEnabled) return keys[0]

        val counter = if (providerIndex in ROUND_ROBIN_COUNTERS.indices) {
            ROUND_ROBIN_COUNTERS[providerIndex]
        } else {
            ROUND_ROBIN_COUNTERS[0]
        }
        val idx = Math.abs(counter.getAndIncrement()) % keys.size
        return keys[idx]
    }

    fun getVerifiedModels(providerIndex: Int): List<String> {
        val raw = prefs.getString(KEY_VERIFIED_MODELS + providerIndex, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = ArrayList<String>()
            for (i in 0 until array.length()) {
                val m = array.getString(i).trim()
                if (m.isNotEmpty()) list.add(m)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setVerifiedModels(providerIndex: Int, models: List<String>) {
        val distinct = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val array = JSONArray(distinct)
        prefs.edit().putString(KEY_VERIFIED_MODELS + providerIndex, array.toString()).apply()
    }

    var isAgentToolsEnabled: Boolean
        get() = prefs.getBoolean(KEY_AGENT_TOOLS_ENABLED, true)
        set(enabled) = prefs.edit().putBoolean(KEY_AGENT_TOOLS_ENABLED, enabled).apply()

    var isTokenSaverEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOKEN_SAVER_ENABLED, true)
        set(enabled) = prefs.edit().putBoolean(KEY_TOKEN_SAVER_ENABLED, enabled).apply()

    var isAutoFailoverEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FAILOVER_ENABLED, true)
        set(enabled) = prefs.edit().putBoolean(KEY_AUTO_FAILOVER_ENABLED, enabled).apply()

    var isRoundRobinEnabled: Boolean
        get() = prefs.getBoolean(KEY_ROUND_ROBIN_ENABLED, true)
        set(enabled) = prefs.edit().putBoolean(KEY_ROUND_ROBIN_ENABLED, enabled).apply()

    fun isVerified(providerIndex: Int): Boolean {
        return prefs.getBoolean(KEY_VERIFIED + providerIndex, false)
    }

    fun setVerified(providerIndex: Int, verified: Boolean) {
        prefs.edit().putBoolean(KEY_VERIFIED + providerIndex, verified).apply()
    }

    fun getSystemPrompt(defaultPrompt: String): String {
        return prefs.getString(KEY_SYSTEM_PROMPT, defaultPrompt) ?: defaultPrompt
    }

    fun setSystemPrompt(prompt: String) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply()
    }

    var permission: Int
        get() = prefs.getInt(KEY_PERMISSION, 0)
        set(index) = prefs.edit().putInt(KEY_PERMISSION, index).apply()

    fun isToolEnabled(tool: String): Boolean {
        if (!isAgentToolsEnabled) return false
        return prefs.getBoolean(KEY_TOOL + tool, true)
    }

    fun getModelId(fallback: String): String {
        return prefs.getString(KEY_MODEL_ID, fallback) ?: fallback
    }

    fun setModelId(id: String) {
        prefs.edit().putString(KEY_MODEL_ID, id).apply()
    }

    fun setToolEnabled(tool: String, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TOOL + tool, enabled).apply()
    }
}
