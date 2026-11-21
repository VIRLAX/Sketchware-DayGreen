package org.sketchware.daygreen.ai

import android.content.Context
import android.util.Log
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiChatClient(
    private val context: Context,
    private val scId: String,
    private val javaName: String,
    private val xmlName: String
) {

    private val store = AiSettingsStore(context)
    private val messages = JSONArray()

    @Volatile
    private var isCancelled = false
    @Volatile
    private var currentCall: Call? = null

    fun cancel() {
        isCancelled = true
        try {
            currentCall?.cancel()
        } catch (_: Exception) {}
    }

    class Proposal(
        val steps: List<AiBlockWriter.Step>?,
        val javaName: String?,
        val eventKey: String?,
        val sourceCode: String?,
        val xmlName: String?,
        val layoutXml: String?,
        val widgetType: String?,
        val widgetId: String?,
        val widgetParent: String?,
        val widgetText: String?,
        val widgetWidth: String?,
        val widgetHeight: String?,
        val widgetOrientation: String?,
        val filePath: String?,
        val fileContent: String?,
        val isFileWrite: Boolean,
        val activityName: String?,
        val activityTitle: String?,
        val summary: String
    ) {
        val isLayout: Boolean get() = layoutXml != null
        val isSource: Boolean get() = sourceCode != null
        val isWidget: Boolean get() = widgetType != null
        val isFile: Boolean get() = filePath != null
        val isActivity: Boolean get() = activityName != null

        companion object {
            fun blocks(javaName: String, eventKey: String, steps: List<AiBlockWriter.Step>, summary: String): Proposal {
                return Proposal(steps, javaName, eventKey, null, null, null, null, null, null, null, null, null, null, null, null, false, null, null, summary)
            }

            fun source(javaName: String, eventKey: String, code: String): Proposal {
                val summary = "Write source code to event $eventKey in $javaName"
                return Proposal(null, javaName, eventKey, code, null, null, null, null, null, null, null, null, null, null, null, false, null, null, summary)
            }

            fun layout(xmlName: String, xmlContent: String): Proposal {
                val summary = "Apply layout XML to $xmlName"
                return Proposal(null, null, null, null, xmlName, xmlContent, null, null, null, null, null, null, null, null, null, false, null, null, summary)
            }

            fun widget(
                xmlName: String, widgetType: String, id: String, parentId: String,
                text: String, width: String, height: String, orientation: String
            ): Proposal {
                val summary = "Add widget $widgetType (id: $id) to layout $xmlName"
                return Proposal(null, null, null, null, xmlName, null, widgetType, id, parentId, text, width, height, orientation, null, null, false, null, null, summary)
            }

            fun file(path: String, content: String, isWrite: Boolean): Proposal {
                val action = if (isWrite) "Modify file" else "Create file"
                return Proposal(null, null, null, null, null, null, null, null, null, null, null, null, null, path, content, isWrite, null, null, "$action $path")
            }

            fun activity(name: String, title: String): Proposal {
                return Proposal(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false, name, title, "Create Activity $name ($title)")
            }
        }
    }

    interface Callback {
        fun onResult(reply: String, proposals: List<Proposal>)
        fun onError(error: String)
        fun onStatus(status: String)
        fun onThinking(thinking: String)
    }

    companion object {
        private const val TAG = "AiChatClient"
        private const val MAX_SOURCE_CHARS = 8000
        private const val MAX_ROUNDS = 14
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        private val HTTP = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()

        private const val DEFAULT_PROMPT =
            "You are an expert Android & Sketchware AI developer assistant inside Sketchware DayGreen.\n" +
            "Your task is to assist the user in designing layouts, writing logic code, adding blocks, generating files, fixing bugs, and compiling projects.\n" +
            "GUIDELINES:\n" +
            "1. CODE PRESENTATION: Always separate conversational explanations from source code. Place all code inside clear markdown code blocks with proper language identifiers (e.g. ```java, ```kotlin, ```xml). Ensure the code is complete, correct, and self-contained so the user can review and copy it cleanly.\n" +
            "2. DIRECT ACTION: If the user commands you to apply, write, fix, or create something, directly invoke the corresponding tool.\n" +
            "3. ACCURACY & CONCISENESS: Be direct, accurate, and avoid redundant filler sentences."

        private const val RULES =
            "\n\nAgent Rules & Capabilities:\n" +
            "1. FILES & PROJECT: create_file, write_file, read_file, search_in_project, create_activity.\n" +
            "2. LOGIC BLOCKS: list_event_keys, list_events, read_event_blocks, list_blocks, append_blocks, clear_event_blocks, propose_source_code.\n" +
            "3. COMPONENTS & MANIFEST: add_component, add_variable, add_permission.\n" +
            "4. LAYOUT: read_layout, add_widget, apply_layout_xml.\n" +
            "5. DIAGNOSTICS & FIXING: get_compile_error, analyze_code_bug, fix_compile_error."
    }

    init {
        try {
            messages.put(message("system", store.getSystemPrompt(DEFAULT_PROMPT) + RULES))
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to initialize system message", e)
        }
    }

    fun ask(userText: String, callback: Callback) {
        isCancelled = false
        Thread { run(userText, callback) }.start()
    }

    private fun run(userText: String, callback: Callback) {
        try {
            val providerIndex = store.providerIndex
            val provider = AiProviders.ALL[providerIndex]
            if (!provider.openAiCompatible) {
                callback.onError("Provider ${provider.name} is not supported for agent chat.")
                return
            }

            val keys = store.getApiKeys(providerIndex)
            if (keys.isEmpty()) {
                callback.onError("API key not configured. Please set it in agent settings.")
                return
            }

            var currentKey = store.getNextApiKey(providerIndex)
            var model = store.getModelId("")
            if (model.isEmpty()) {
                val cached = store.getVerifiedModels(providerIndex)
                model = if (cached.isNotEmpty()) cached[0] else provider.models[0]
            }

            // Automatic Token Saver
            if (store.isTokenSaverEnabled && messages.length() > 6) {
                val sys = messages.optJSONObject(0)
                val pruned = JSONArray()
                if (sys != null && sys.optString("role") == "system") {
                    pruned.put(sys)
                }
                val start = Math.max(1, messages.length() - 4)
                for (k in start until messages.length()) {
                    pruned.put(messages.getJSONObject(k))
                }
                while (messages.length() > 0) messages.remove(0)
                for (k in 0 until pruned.length()) messages.put(pruned.getJSONObject(k))
            }

            messages.put(message("user", userText))

            val proposals = mutableListOf<Proposal>()
            for (round in 0 until MAX_ROUNDS) {
                if (isCancelled) {
                    callback.onError("Request canceled.")
                    return
                }

                callback.onStatus(if (round == 0) "Thinking..." else "Continuing...")

                var reply: JSONObject? = null
                var keyAttempts = 0
                val maxAttempts = if (store.isAutoFailoverEnabled) Math.max(1, keys.size) else 1

                while (keyAttempts < maxAttempts) {
                    try {
                        reply = post(provider, currentKey, model)
                        break
                    } catch (e: Exception) {
                        val msg = e.message ?: ""
                        if ((msg.contains("429") || msg.contains("quota", ignoreCase = true)) && store.isAutoFailoverEnabled && keys.size > 1) {
                            keyAttempts++
                            currentKey = store.getNextApiKey(providerIndex)
                            callback.onStatus("Rate limit (429) detected. Switching key...")
                            continue
                        } else {
                            throw e
                        }
                    }
                }

                if (reply == null) {
                    callback.onError("Failed to obtain response from provider.")
                    return
                }

                if (isCancelled) {
                    callback.onError("Request canceled.")
                    return
                }

                val assistant = reply.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")

                val reasoning = assistant.optString("reasoning_content", "")
                assistant.remove("reasoning_content")
                if (reasoning.isNotEmpty()) {
                    callback.onThinking(reasoning)
                }
                messages.put(assistant)

                val calls = assistant.optJSONArray("tool_calls")
                if (calls == null || calls.length() == 0) {
                    val content = if (assistant.isNull("content")) "" else assistant.optString("content", "")
                    callback.onResult(content, proposals)
                    return
                }

                for (i in 0 until calls.length()) {
                    if (isCancelled) {
                        callback.onError("Request canceled.")
                        return
                    }

                    val call = calls.getJSONObject(i)
                    val fn = call.getJSONObject("function")
                    val name = fn.getString("name")
                    callback.onStatus("Executing $name...")

                    val result = try {
                        execute(name, JSONObject(fn.getString("arguments")), proposals)
                    } catch (e: Exception) {
                        Log.e(TAG, "Tool '$name' failed", e)
                        "Tool $name failed: ${e.message}. Check arguments and try again."
                    }
                    messages.put(toolMessage(call.getString("id"), result))
                }
            }
            callback.onResult("(Step limit reached. Please try asking a more specific question.)", proposals)
        } catch (e: Exception) {
            if (isCancelled) {
                callback.onError("Request canceled.")
                return
            }
            Log.e(TAG, "Chat request failed", e)
            callback.onError(e.message ?: e.toString())
        }
    }

    private fun execute(name: String, args: JSONObject, proposals: MutableList<Proposal>): String {
        if (!store.isToolEnabled(name)) {
            return disabled(name)
        }

        return when (name) {
            // File & Project Operations
            "create_file" -> {
                val path = args.getString("path")
                val content = args.getString("content")
                if (store.permission == 3) {
                    AiProjectHelper.createFile(scId, path, content).message
                } else {
                    proposals.add(Proposal.file(path, content, false))
                    "Pending user approval to create file $path."
                }
            }
            "write_file" -> {
                val path = args.getString("path")
                val content = args.getString("content")
                if (store.permission == 3) {
                    AiProjectHelper.writeFile(scId, path, content).message
                } else {
                    proposals.add(Proposal.file(path, content, true))
                    "Pending user approval to write/modify file $path."
                }
            }
            "read_file" -> {
                val path = args.getString("path")
                AiProjectHelper.readFile(scId, path)
            }
            "search_in_project" -> {
                val query = args.getString("query")
                AiProjectHelper.searchInProject(scId, query)
            }
            "create_activity" -> {
                val actName = args.getString("name")
                val title = args.optString("title", actName)
                if (store.permission == 3) {
                    AiProjectHelper.createActivity(scId, actName, title).message
                } else {
                    proposals.add(Proposal.activity(actName, title))
                    "Pending user approval to create Activity $actName."
                }
            }
            "add_permission" -> {
                val perm = args.getString("permission")
                AiProjectHelper.addPermission(scId, perm).message
            }
            "add_component" -> {
                val type = args.getString("type")
                val id = args.getString("id")
                AiProjectHelper.addComponent(scId, javaName, type, id).message
            }
            "add_variable" -> {
                val type = args.getString("type")
                val varName = args.getString("name")
                AiProjectHelper.addVariable(scId, javaName, type, varName).message
            }
            "fix_compile_error" -> {
                val err = AiDiagnosticHelper.getCompileError(scId)
                val diag = AiDiagnosticHelper.diagnoseProject(scId, javaName, xmlName)
                "$err\n\n$diag"
            }

            // Blocks Operations
            "list_blocks" -> {
                val out = JSONArray()
                for (entry in AiBlockCatalog.search(args.optString("query", ""), 40)) {
                    val o = JSONObject().apply {
                        put("op", entry.opCode)
                        put("spec", entry.spec)
                        put("type", entry.type)
                        put("params", JSONArray(entry.paramTypes))
                    }
                    out.put(o)
                }
                if (out.length() == 0) "No blocks match the query." else out.toString()
            }
            "list_event_keys" -> {
                if (scId.isEmpty()) {
                    "Project ID is missing."
                } else {
                    val keys = AiBlockWriter.listEventKeys(scId, javaName)
                    if (keys.isEmpty()) "No events with blocks in $javaName." else JSONArray(keys).toString()
                }
            }
            "list_events" -> {
                val out = JSONArray()
                for (e in AiEventCatalog.activityEvents()) {
                    out.put(JSONObject().put("name", e.name).put("key", e.key))
                }
                out.toString()
            }
            "read_event_blocks" -> {
                if (scId.isEmpty()) {
                    "Project ID is missing."
                } else {
                    val eventKey = args.optString("event_key", "")
                    if (eventKey.isEmpty()) "event_key is required." else AiBlockWriter.readEventBlocks(scId, javaName, eventKey)
                }
            }
            "append_blocks" -> {
                val eventKey = args.getString("event_key")
                val stepsJson = args.getJSONArray("steps")
                val steps = mutableListOf<AiBlockWriter.Step>()
                val summary = StringBuilder()

                for (i in 0 until stepsJson.length()) {
                    val s = stepsJson.getJSONObject(i)
                    val params = mutableListOf<String>()
                    val p = s.optJSONArray("params")
                    if (p != null) {
                        for (j in 0 until p.length()) {
                            params.add(p.optString(j, ""))
                        }
                    }
                    val op = s.getString("op")
                    steps.add(AiBlockWriter.Step(op, params))
                    summary.append(i + 1).append(". ").append(op).append(params).append('\n')
                }

                if (store.permission == 3) {
                    AiBlockWriter.appendBlocks(scId, javaName, eventKey, steps).message
                } else {
                    proposals.add(Proposal.blocks(javaName, eventKey, steps, summary.toString().trim()))
                    "Pending user approval."
                }
            }
            "clear_event_blocks" -> {
                val eventKey = args.getString("event_key")
                AiBlockWriter.clearEventBlocks(scId, javaName, eventKey).message
            }
            "create_more_block" -> {
                val mbName = args.getString("name")
                val spec = args.getString("spec")
                AiBlockWriter.createMoreBlock(scId, javaName, mbName, spec).message
            }
            "propose_source_code" -> {
                val eventKey = args.optString("event_key", "")
                val code = args.optString("code", "")
                if (code.trim().isEmpty()) return "Code is empty."
                if (code.length > MAX_SOURCE_CHARS) return "Code too long (max $MAX_SOURCE_CHARS characters)."
                if (!AiBlockWriter.isValidEventKey(scId, javaName, eventKey)) return "Event '$eventKey' is invalid."

                if (store.permission == 3) {
                    AiBlockWriter.writeSourceDirectly(scId, javaName, eventKey, code).message
                } else {
                    proposals.add(Proposal.source(javaName, eventKey, code))
                    "Pending user approval to insert source code."
                }
            }

            // Layout Operations
            "read_layout" -> {
                val targetXml = args.optString("xml_name", xmlName)
                AiLayoutHelper.readLayout(scId, targetXml)
            }
            "add_widget" -> {
                val targetXml = args.optString("xml_name", xmlName)
                val widgetType = args.getString("widget_type")
                val id = args.optString("id", "")
                val parent = args.optString("parent", "root")
                val text = args.optString("text", "")
                val width = args.optString("width", "wrap_content")
                val height = args.optString("height", "wrap_content")
                val orientation = args.optString("orientation", "vertical")

                proposals.add(Proposal.widget(targetXml, widgetType, id, parent, text, width, height, orientation))
                "Pending user approval to add widget $widgetType ($id)."
            }
            "apply_layout_xml" -> {
                val targetXml = args.optString("xml_name", xmlName)
                val content = args.getString("xml_content")
                if (store.permission == 3) {
                    AiLayoutHelper.applyLayoutXml(scId, targetXml, content).message
                } else {
                    proposals.add(Proposal.layout(targetXml, content))
                    "Pending user approval to apply layout XML to $targetXml."
                }
            }

            // Diagnostics
            "get_compile_error" -> {
                AiDiagnosticHelper.getCompileError(scId)
            }
            "analyze_code_bug" -> {
                AiDiagnosticHelper.diagnoseProject(scId, javaName, xmlName)
            }

            else -> "Unknown tool: $name"
        }
    }

    private fun disabled(tool: String): String {
        return "Tool $tool is disabled in agent settings."
    }

    private fun post(provider: AiProviders.Provider, apiKey: String, model: String): JSONObject {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            val schemas = toolSchemas()
            if (schemas.length() > 0) {
                put("tools", schemas)
                put("tool_choice", "auto")
            }
        }

        val reqBuilder = Request.Builder()
            .url(provider.baseUrl ?: "")
            .post(body.toString().toRequestBody(JSON_TYPE))

        if (provider.name.contains("Gemini", ignoreCase = true)) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
            reqBuilder.header("x-goog-api-key", apiKey)
        } else if (provider.name.contains("Anthropic", ignoreCase = true)) {
            reqBuilder.header("x-api-key", apiKey)
            reqBuilder.header("anthropic-version", "2023-06-01")
        } else {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        val call = HTTP.newCall(reqBuilder.build())
        currentCall = call
        try {
            call.execute().use { response ->
                val text = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: $text")
                }
                return JSONObject(text)
            }
        } finally {
            currentCall = null
        }
    }

    private fun toolSchemas(): JSONArray {
        val tools = JSONArray()
        if (!store.isAgentToolsEnabled) return tools

        // 1. Files & Project
        val cfProps = JSONObject().put("path", JSONObject().put("type", "string").put("description", "File path, e.g. NetworkUtil.java, models/User.kt"))
            .put("content", JSONObject().put("type", "string").put("description", "Complete code content"))
        tools.put(tool("create_file", "Create a new source file (.java, .kt, .xml, .cpp).", cfProps, JSONArray().put("path").put("content")))
        tools.put(tool("write_file", "Write or modify an existing source file.", cfProps, JSONArray().put("path").put("content")))

        val rfProps = JSONObject().put("path", JSONObject().put("type", "string").put("description", "Relative file path"))
        tools.put(tool("read_file", "Read a file from project.", rfProps, JSONArray().put("path")))

        val spProps = JSONObject().put("query", JSONObject().put("type", "string").put("description", "Text or keyword to search across project"))
        tools.put(tool("search_in_project", "Search across project files.", spProps, JSONArray().put("query")))

        val caProps = JSONObject().put("name", JSONObject().put("type", "string").put("description", "Activity name, e.g. DetailActivity"))
            .put("title", JSONObject().put("type", "string").put("description", "Title for ActionBar"))
        tools.put(tool("create_activity", "Create a new Activity with layout XML.", caProps, JSONArray().put("name")))

        val permProps = JSONObject().put("permission", JSONObject().put("type", "string").put("description", "Permission name e.g. INTERNET, POST_NOTIFICATIONS"))
        tools.put(tool("add_permission", "Add uses-permission to AndroidManifest.xml.", permProps, JSONArray().put("permission")))

        val compProps = JSONObject().put("type", JSONObject().put("type", "string").put("description", "Component type e.g. Intent, Timer, FirebaseDB"))
            .put("id", JSONObject().put("type", "string").put("description", "Component variable ID"))
        tools.put(tool("add_component", "Add a component to Activity.", compProps, JSONArray().put("type").put("id")))

        val varProps = JSONObject().put("type", JSONObject().put("type", "string").put("description", "Variable type e.g. boolean, int, String, Map"))
            .put("name", JSONObject().put("type", "string").put("description", "Variable name"))
        tools.put(tool("add_variable", "Add a variable to Activity.", varProps, JSONArray().put("type").put("name")))

        // 2. Blocks
        val lbProps = JSONObject().put("query", JSONObject().put("type", "string").put("description", "Opcode keyword or block text"))
        tools.put(tool("list_blocks", "Search available blocks.", lbProps, JSONArray()))
        tools.put(tool("list_event_keys", "List events that contain blocks.", JSONObject(), JSONArray()))
        tools.put(tool("list_events", "List standard Activity lifecycle events.", JSONObject(), JSONArray()))

        val rebProps = JSONObject().put("event_key", JSONObject().put("type", "string").put("description", "Event key"))
        tools.put(tool("read_event_blocks", "Read blocks within an event.", rebProps, JSONArray().put("event_key")))

        val stepItem = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject().put("op", JSONObject().put("type", "string")).put("params", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))))
            .put("required", JSONArray().put("op"))
        val abProps = JSONObject().put("event_key", JSONObject().put("type", "string")).put("steps", JSONObject().put("type", "array").put("items", stepItem))
        tools.put(tool("append_blocks", "Add blocks to an event.", abProps, JSONArray().put("event_key").put("steps")))

        val cebProps = JSONObject().put("event_key", JSONObject().put("type", "string").put("description", "Event key"))
        tools.put(tool("clear_event_blocks", "Clear all blocks in an event.", cebProps, JSONArray().put("event_key")))

        val scProps = JSONObject().put("event_key", JSONObject().put("type", "string")).put("code", JSONObject().put("type", "string"))
        tools.put(tool("propose_source_code", "Propose direct source code (addSourceDirectly).", scProps, JSONArray().put("event_key").put("code")))

        // 3. Layout
        val rlProps = JSONObject().put("xml_name", JSONObject().put("type", "string"))
        tools.put(tool("read_layout", "Read widget hierarchy and layout XML.", rlProps, JSONArray()))

        val awProps = JSONObject()
            .put("xml_name", JSONObject().put("type", "string"))
            .put("widget_type", JSONObject().put("type", "string"))
            .put("id", JSONObject().put("type", "string"))
            .put("parent", JSONObject().put("type", "string"))
            .put("text", JSONObject().put("type", "string"))
            .put("width", JSONObject().put("type", "string"))
            .put("height", JSONObject().put("type", "string"))
            .put("orientation", JSONObject().put("type", "string"))
        tools.put(tool("add_widget", "Add a new widget to layout.", awProps, JSONArray().put("widget_type")))

        val alProps = JSONObject().put("xml_name", JSONObject().put("type", "string")).put("xml_content", JSONObject().put("type", "string"))
        tools.put(tool("apply_layout_xml", "Replace or create layout XML.", alProps, JSONArray().put("xml_content")))

        // 4. Diagnostics & Fixing
        tools.put(tool("get_compile_error", "Get the latest compile error log.", JSONObject(), JSONArray()))
        tools.put(tool("analyze_code_bug", "Analyze potential bugs in project.", JSONObject(), JSONArray()))
        tools.put(tool("fix_compile_error", "Diagnose and provide fix recommendations for latest compile error.", JSONObject(), JSONArray()))

        return tools
    }

    private fun tool(name: String, description: String, properties: JSONObject, required: JSONArray): JSONObject {
        val params = JSONObject().put("type", "object").put("properties", properties).put("required", required)
        val fn = JSONObject().put("name", name).put("description", description).put("parameters", params)
        return JSONObject().put("type", "function").put("function", fn)
    }

    private fun message(role: String, content: String): JSONObject {
        return JSONObject().put("role", role).put("content", content)
    }

    private fun toolMessage(id: String, content: String): JSONObject {
        return JSONObject().put("role", "tool").put("tool_call_id", id).put("content", content)
    }
}
