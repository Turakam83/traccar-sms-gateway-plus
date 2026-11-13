package org.traccar.gateway

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ActivityGatewayBinding
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.DateFormat
import java.util.Date
import java.util.UUID

class GatewayActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityGatewayBinding::inflate)
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundSettings = GatewayBackgroundSettings(this)
    private lateinit var status: TextView
    private lateinit var importKey: MaterialButton

    private val updateStatus = object : Runnable {
        override fun run() {
            renderStatus()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.gatewayNestedScrollview))
        setupMaterialScrollListener(scrollingView = binding.gatewayNestedScrollview, topAppBar = binding.gatewayAppbar)
        binding.gatewayCloudLabel.isGone = true
        binding.gatewayCloudKeyHolder.isGone = true
        binding.root.findViewById<View>(R.id.gateway_cloud_divider).isGone = true
        binding.gatewayLocalEnable.setText(R.string.redmi_enable)

        status = TextView(this).apply { setPadding(padding(), padding(), padding(), padding()) }
        binding.gatewayHolder.addView(status, binding.gatewayHolder.indexOfChild(binding.gatewayLocalEnableHolder) + 1)
        importKey = addButton(R.string.redmi_import_key, ::showImportKey)
        addButton(R.string.redmi_diagnostics, ::showJournal)
        addButton(R.string.gateway_background_setup, backgroundSettings::start)
        backgroundSettings.restore(
            savedInstanceState?.getString("background_settings_step"),
            savedInstanceState?.getBoolean("background_settings_waiting") == true,
        )

        if (GatewaySettings.key(this) == null) GatewaySettings.setKey(this, UUID.randomUUID().toString())
        binding.gatewayLocalEnableHolder.setOnClickListener { toggleService() }
        binding.gatewayLocalKeyHolder.setOnClickListener { copy(binding.gatewayLocalKey.text) }
        binding.gatewayLocalEndpointsHolder.setOnClickListener { copy(binding.gatewayLocalEndpoints.text) }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.gatewayAppbar, NavigationIcon.Arrow)
        binding.gatewayLocalKey.text = GatewaySettings.key(this)
        binding.gatewayLocalEndpoints.text = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>().filterNot { it.isLoopbackAddress }
                .joinToString("\n") { "http://${it.hostAddress}:${GatewaySettings.PORT}" }
        }.getOrDefault("")
        updateStatus.run()
        if (GatewayService.state == GatewayService.State.STOPPED) GatewayService.requestStart(this, "gateway opened")
    }

    override fun onPause() {
        handler.removeCallbacks(updateStatus)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("background_settings_step", backgroundSettings.savedStep())
        outState.putBoolean("background_settings_waiting", backgroundSettings.isWaitingForSettings())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        backgroundSettings.dismiss()
        super.onDestroy()
    }

    private fun toggleService() {
        val enabled = !GatewaySettings.enabled(this)
        if ((enabled && GatewaySettings.key(this).isNullOrEmpty()) || !GatewaySettings.setEnabled(this, enabled)) {
            Toast.makeText(this, R.string.redmi_settings_error, Toast.LENGTH_LONG).show()
            return
        }
        if (enabled) {
            GatewayService.requestStart(this, "enabled in the app")
        } else {
            stopService(Intent(this, GatewayService::class.java))
        }
        renderStatus()
    }

    private fun renderStatus() {
        val enabled = GatewaySettings.enabled(this)
        binding.gatewayLocalEnable.isChecked = enabled
        importKey.isEnabled = !enabled
        val label = when (GatewayService.state) {
            GatewayService.State.STOPPED -> R.string.redmi_stopped
            GatewayService.State.STARTING -> R.string.redmi_starting
            GatewayService.State.RUNNING -> R.string.redmi_running
            GatewayService.State.RETRYING -> R.string.redmi_retrying
        }
        status.setText(label)
    }

    private fun showImportKey() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            setHint(R.string.gateway_key)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.redmi_import_key)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = input.text.toString().trim()
                if (key.length !in 1..256 || key.any { it !in '!'..'~' }) {
                    input.error = getString(R.string.redmi_invalid_key)
                } else if (GatewaySettings.enabled(this) || !GatewaySettings.setKey(this, key)) {
                    input.error = getString(R.string.redmi_settings_error)
                } else {
                    binding.gatewayLocalKey.text = key
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showJournal() {
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) { runCatching { GatewayJournal.get(applicationContext).events() } }
            val date = DateFormat.getDateTimeInstance()
            val text = events.fold({ rows ->
                rows.joinToString("\n\n") { (time, entry) -> "${date.format(Date(time))}\n$entry" }
                    .ifEmpty { getString(R.string.redmi_journal_empty) }
            }, { getString(R.string.redmi_journal_error) })
            val view = TextView(this@GatewayActivity).apply {
                this.text = text
                setTextIsSelectable(true)
                setPadding(padding(), padding(), padding(), padding())
            }
            val scroll = ScrollView(this@GatewayActivity).apply { addView(view) }
            AlertDialog.Builder(this@GatewayActivity)
                .setTitle(R.string.redmi_diagnostics)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(android.R.string.copy) { _, _ -> copy(text) }
                .show()
        }
    }

    private fun addButton(label: Int, action: () -> Unit): MaterialButton = MaterialButton(this).apply {
        setText(label)
        setOnClickListener { action() }
        binding.gatewayHolder.addView(this)
    }

    private fun copy(text: CharSequence) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(null, text))
        Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
    }

    private fun padding() = (16 * resources.displayMetrics.density).toInt()
}
