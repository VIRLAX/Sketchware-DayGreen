package org.sketchware.daygreen.ai.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.besome.sketch.lib.base.BaseAppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import org.sketchware.daygreen.ai.AiConnectionTester
import org.sketchware.daygreen.ai.AiModelFetcher
import org.sketchware.daygreen.ai.AiModelPicker
import org.sketchware.daygreen.ai.AiProviders
import org.sketchware.daygreen.ai.AiSettingsStore
import pro.sketchware.databinding.ActivityAiSettingsBinding
import pro.sketchware.databinding.DialogAiInputBinding

class AiSettingsActivity : BaseAppCompatActivity() {

    companion object {
        private const val DEFAULT_SYSTEM_PROMPT =
            "Anda adalah pengembang aplikasi Android & Sketchware AI di Sketchware DayGreen."

        private const val COLOR_OK = 0xFF4CAF50.toInt()
        private const val COLOR_ERROR = 0xFFE53935.toInt()
        private const val COLOR_NEUTRAL = 0xFF9E9E9E.toInt()
    }

    private lateinit var binding: ActivityAiSettingsBinding
    private lateinit var store: AiSettingsStore
    private val mainHandler = Handler(Looper.getMainLooper())
    private var providerIndex = 0
    private var modelIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeNoContrast()
        super.onCreate(savedInstanceState)
        binding = ActivityAiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mainView = binding.main
        val left = mainView.paddingLeft
        val top = mainView.paddingTop
        val right = mainView.paddingRight
        val bottom = mainView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(mainView) { _, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            mainView.setPadding(left + systemInsets.left, top, right + systemInsets.right, bottom)
            insets
        }

        val scroll = binding.contentScroll
        val sLeft = scroll.paddingLeft
        val sTop = scroll.paddingTop
        val sRight = scroll.paddingRight
        val sBottom = scroll.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            scroll.setPadding(sLeft, sTop, sRight, sBottom + navBarInsets.bottom)
            insets
        }

        store = AiSettingsStore(this)
        providerIndex = store.providerIndex
        modelIndex = store.modelIndex

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rowProvider.setOnClickListener { showProviderDialog() }
        binding.rowApiKey.setOnClickListener { showApiKeyDialog() }
        binding.rowModel.setOnClickListener { showModelDialog() }
        binding.rowSystemPrompt.setOnClickListener { showSystemPromptDialog() }
        binding.btnValidate.setOnClickListener { validateKeysRealtime() }
        binding.rowTest.setOnClickListener { validateKeysRealtime() }

        // Smart Feature Switches
        binding.swRoundRobin.isChecked = store.isRoundRobinEnabled
        binding.swRoundRobin.setOnCheckedChangeListener { _, isChecked ->
            store.isRoundRobinEnabled = isChecked
        }

        binding.swTokenSaver.isChecked = store.isTokenSaverEnabled
        binding.swTokenSaver.setOnCheckedChangeListener { _, isChecked ->
            store.isTokenSaverEnabled = isChecked
        }

        // Master Agent Tools Switch
        binding.swAgentTools.isChecked = store.isAgentToolsEnabled
        binding.swAgentTools.setOnCheckedChangeListener { _, isChecked ->
            store.isAgentToolsEnabled = isChecked
        }

        // Permissions
        binding.radioPermission.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.rbAsk.id -> store.permission = 0
                binding.rbAutoEdit.id -> store.permission = 1
                binding.rbPlan.id -> store.permission = 2
                binding.rbAutonomous.id -> store.permission = 3
            }
        }
        selectPermission(store.permission)

        refreshValues()
    }

    private fun selectPermission(index: Int) {
        val buttons = arrayOf(binding.rbAsk, binding.rbAutoEdit, binding.rbPlan, binding.rbAutonomous)
        val validIndex = index.coerceIn(0, buttons.size - 1)
        buttons[validIndex].isChecked = true
    }

    private fun refreshValues() {
        val provider = AiProviders.ALL[providerIndex]
        val keys = store.getApiKeys(providerIndex)

        binding.tvProviderValue.text = provider.name
        binding.tvApiKeyValue.text = when {
            keys.isEmpty() -> "Belum diisi"
            keys.size == 1 -> maskKey(keys[0])
            else -> "${keys.size} API Keys terkonfigurasi (Round-Robin)"
        }

        val verifiedModels = store.getVerifiedModels(providerIndex)
        val modelId = store.getModelId("")
        val displayModel = if (modelId.isNotEmpty()) {
            modelId
        } else if (verifiedModels.isNotEmpty()) {
            verifiedModels[0]
        } else {
            provider.models[0]
        }
        binding.tvModelValue.text = displayModel
        binding.tvSystemPromptValue.text = store.getSystemPrompt(DEFAULT_SYSTEM_PROMPT)

        if (keys.isEmpty()) {
            setTestStatus("Masukkan API key untuk mulai validasi", COLOR_NEUTRAL)
        } else if (store.isVerified(providerIndex)) {
            val countInfo = if (keys.size > 1) "${keys.size} Key Aktif · " else ""
            setTestStatus("● ${countInfo}Terhubung (HTTP 200 OK)", COLOR_OK)
        } else {
            setTestStatus("Belum diuji (Klik tombol untuk uji 200 OK)", COLOR_NEUTRAL)
        }
    }

    private fun setTestStatus(text: String, color: Int) {
        binding.tvTestStatus.text = text
        binding.tvTestStatus.setTextColor(color)
    }

    private fun maskKey(key: String): String {
        if (key.length <= 8) return "••••••••"
        return key.substring(0, 3) + "••••••••" + key.substring(key.length - 4)
    }

    private fun showProviderDialog() {
        val names = Array(AiProviders.ALL.size) { i -> AiProviders.ALL[i].name }
        val selected = intArrayOf(providerIndex)

        MaterialAlertDialogBuilder(this)
            .setTitle("Pilih Provider AI")
            .setSingleChoiceItems(names, providerIndex) { _, which -> selected[0] = which }
            .setPositiveButton("Pilih") { _, _ ->
                if (providerIndex != selected[0]) {
                    providerIndex = selected[0]
                    modelIndex = 0
                    store.providerIndex = providerIndex
                    store.modelIndex = modelIndex
                    store.setModelId("")
                }
                refreshValues()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showApiKeyDialog() {
        val keys = store.getApiKeys(providerIndex)
        val initialText = keys.joinToString("\n")

        showInputDialog(
            title = "API Key (Multi-Key)",
            hint = "Paste 1 atau beberapa API key (1 baris per key)",
            initialValue = initialText,
            password = false,
            multiline = true
        ) { input ->
            val parsed = input.split(Regex("[\\n,;]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            store.setApiKeys(providerIndex, parsed)
            store.setVerified(providerIndex, false)
            refreshValues()

            if (parsed.isNotEmpty()) {
                validateKeysRealtime()
            }
        }
    }

    private fun validateKeysRealtime() {
        val keys = store.getApiKeys(providerIndex)
        if (keys.isEmpty()) {
            setTestStatus("Masukkan API key terlebih dahulu", COLOR_NEUTRAL)
            showApiKeyDialog()
            return
        }

        binding.btnValidate.isEnabled = false
        setTestStatus("Memeriksa API key & menguji respon model (HTTP 200 OK)...", COLOR_NEUTRAL)

        val rawInput = keys.joinToString("\n")
        AiConnectionTester.testMultiKeys(providerIndex, rawInput, object : AiConnectionTester.MultiKeyCallback {
            override fun onResult(
                validKeys: List<String>,
                invalidKeys: List<String>,
                rateLimitedKeys: List<String>,
                message: String
            ) {
                mainHandler.post {
                    if (validKeys.isNotEmpty()) {
                        store.setApiKeys(providerIndex, validKeys)
                        store.setVerified(providerIndex, true)

                        val provider = AiProviders.ALL[providerIndex]
                        setTestStatus("Key valid. Menguji model aktif (200 OK)...", COLOR_NEUTRAL)

                        // Realtime 200 OK Ping test on all candidate models
                        AiModelFetcher.fetchWithStore(provider, store, providerIndex, object : AiModelFetcher.Callback {
                            override fun onResult(models: List<String>) {
                                mainHandler.post {
                                    binding.btnValidate.isEnabled = true
                                    val statusText = "● ${validKeys.size} Key Aktif · ${models.size} Model Siap (200 OK)"
                                    setTestStatus(statusText, COLOR_OK)

                                    binding.tvTestStatus.animate()
                                        .scaleX(1.05f).scaleY(1.05f)
                                        .setDuration(180)
                                        .setInterpolator(AccelerateDecelerateInterpolator())
                                        .withEndAction {
                                            binding.tvTestStatus.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                                        }
                                        .start()

                                    Toast.makeText(this@AiSettingsActivity, "Validasi Berhasil: ${models.size} model aktif (200 OK)", Toast.LENGTH_SHORT).show()
                                    refreshValues()
                                }
                            }

                            override fun onError(error: String) {
                                mainHandler.post {
                                    binding.btnValidate.isEnabled = true
                                    setTestStatus("▲ Key valid tapi model limit/429: $error", COLOR_ERROR)
                                    Toast.makeText(this@AiSettingsActivity, error, Toast.LENGTH_LONG).show()
                                    refreshValues()
                                }
                            }
                        })
                    } else {
                        binding.btnValidate.isEnabled = true
                        store.setVerified(providerIndex, false)
                        setTestStatus("✕ $message", COLOR_ERROR)
                        Toast.makeText(this@AiSettingsActivity, message, Toast.LENGTH_LONG).show()
                        refreshValues()
                    }
                }
            }
        })
    }

    private fun showModelDialog() {
        val keys = store.getApiKeys(providerIndex)
        if (keys.isEmpty()) {
            Toast.makeText(this, "Masukkan dan validasi API key terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        AiModelPicker.show(this, store, providerIndex) {
            refreshValues()
        }
    }

    private fun showSystemPromptDialog() {
        showInputDialog(
            title = "System Prompt",
            hint = "Instruksi sistem",
            initialValue = store.getSystemPrompt(DEFAULT_SYSTEM_PROMPT),
            password = false,
            multiline = true
        ) { prompt ->
            val p = prompt.trim()
            store.setSystemPrompt(if (p.isEmpty()) DEFAULT_SYSTEM_PROMPT else p)
            refreshValues()
        }
    }

    private fun showInputDialog(
        title: String,
        hint: String,
        initialValue: String,
        password: Boolean,
        multiline: Boolean,
        onSave: (String) -> Unit
    ) {
        val inputBinding = DialogAiInputBinding.inflate(layoutInflater)
        val layout = inputBinding.tilInput
        val edit = inputBinding.etInput

        layout.hint = hint
        edit.setText(initialValue)

        var inputType = InputType.TYPE_CLASS_TEXT
        if (password) {
            inputType = inputType or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        } else if (multiline) {
            inputType = inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            edit.minLines = 4
            edit.maxLines = 10
            edit.gravity = Gravity.TOP or Gravity.START
        }
        edit.inputType = inputType

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(inputBinding.root)
            .setPositiveButton("Simpan") { _, _ -> onSave(edit.text?.toString() ?: "") }
            .setNegativeButton("Batal", null)
            .show()
    }
}
