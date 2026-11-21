package org.sketchware.daygreen.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object AiModelPicker {

    @JvmStatic
    fun show(context: Context, store: AiSettingsStore, providerIndex: Int, onPicked: Runnable?) {
        val provider = AiProviders.ALL[providerIndex]
        val cached = store.getVerifiedModels(providerIndex)

        if (cached.isNotEmpty()) {
            showList(context, store, cached, provider.models, onPicked, null, providerIndex)
            return
        }

        val main = Handler(Looper.getMainLooper())
        val bar = ProgressBar(context)
        val pad = dp(context, 24)
        bar.setPadding(pad, pad, pad, pad)

        val loading = MaterialAlertDialogBuilder(context)
            .setTitle("Memuat model terverifikasi...")
            .setView(bar)
            .setCancelable(false)
            .show()

        AiModelFetcher.fetchWithStore(provider, store, providerIndex, object : AiModelFetcher.Callback {
            override fun onResult(models: List<String>) {
                main.post {
                    loading.dismiss()
                    showList(context, store, models, provider.models, onPicked, null, providerIndex)
                }
            }

            override fun onError(error: String) {
                main.post {
                    loading.dismiss()
                    showList(context, store, emptyList(), provider.models, onPicked, error, providerIndex)
                }
            }
        })
    }

    private fun showList(
        context: Context,
        store: AiSettingsStore,
        fetched: List<String>,
        defaults: Array<out String>,
        onPicked: Runnable?,
        warning: String?,
        providerIndex: Int
    ) {
        val models = if (fetched.isNotEmpty()) fetched else defaults.toList()
        if (warning != null) {
            Toast.makeText(context, "Daftar model: $warning", Toast.LENGTH_SHORT).show()
        }

        var current = store.getModelId("")
        if (current.isEmpty()) {
            current = defaults[0]
        }
        val items = models.toTypedArray()
        val checked = Math.max(0, models.indexOf(current))
        val selected = intArrayOf(checked)

        MaterialAlertDialogBuilder(context)
            .setTitle("Pilih Model (200 OK)")
            .setSingleChoiceItems(items, checked) { _, which -> selected[0] = which }
            .setPositiveButton("Pilih") { _, _ ->
                if (selected[0] in items.indices) {
                    store.setModelId(items[selected[0]])
                    onPicked?.run()
                }
            }
            .setNeutralButton("Segarkan") { _, _ ->
                // Clear cache and re-fetch
                store.setVerifiedModels(providerIndex, emptyList())
                show(context, store, providerIndex, onPicked)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
