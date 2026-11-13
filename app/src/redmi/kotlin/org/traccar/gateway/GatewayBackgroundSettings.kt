package org.traccar.gateway

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import org.fossify.messages.R

internal class GatewayBackgroundSettings(private val activity: GatewayActivity) {
    private enum class Step { ANDROID, BATTERY, AUTOSTART, SUMMARY }

    private val appUri get() = Uri.fromParts("package", activity.packageName, null)
    private val isXiaomi = listOf("xiaomi", "redmi", "poco").any {
        it.equals(Build.MANUFACTURER, ignoreCase = true) || it.equals(Build.BRAND, ignoreCase = true)
    }
    private var step: Step? = null
    private var waitingForSettings = false
    private var dialog: AlertDialog? = null

    private val settings = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        waitingForSettings = false
        advance()
    }

    fun start() {
        if (step != null) return
        if (isExempt()) {
            afterAndroid()
        } else {
            step = Step.ANDROID
            if (!openSettings(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                )
            ) {
                afterAndroid()
            }
        }
    }

    fun restore(savedStep: String?, waiting: Boolean) {
        step = savedStep?.let(Step::valueOf)
        waitingForSettings = waiting
        if (!waitingForSettings) showStep()
    }

    fun savedStep(): String? = step?.name

    fun isWaitingForSettings(): Boolean = waitingForSettings

    fun dismiss() {
        dialog?.dismiss()
    }

    private fun afterAndroid() {
        step = if (isXiaomi) Step.BATTERY else Step.SUMMARY
        showStep()
    }

    private fun advance() {
        when (step) {
            Step.ANDROID -> afterAndroid()
            Step.BATTERY -> {
                step = Step.AUTOSTART
                showStep()
            }
            Step.AUTOSTART -> {
                step = Step.SUMMARY
                showStep()
            }
            Step.SUMMARY -> showStep()
            null -> Unit
        }
    }

    private fun showStep() {
        val builder = AlertDialog.Builder(activity).setTitle(R.string.gateway_background_setup)
        when (step) {
            Step.BATTERY -> builder
                .setMessage(R.string.gateway_background_miui_battery)
                .setPositiveButton(R.string.gateway_background_open_settings) { _, _ ->
                    val battery = Intent().setClassName(
                        "com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                    ).putExtra("package_name", activity.packageName)
                        .putExtra("package_label", activity.getString(R.string.redmi_launcher_name))
                    if (!openSettings(battery, appSettings())) advance()
                }
                .setNegativeButton(R.string.gateway_background_skip) { _, _ -> advance() }
            Step.AUTOSTART -> builder
                .setMessage(R.string.gateway_background_miui_autostart)
                .setPositiveButton(R.string.gateway_background_open_settings) { _, _ ->
                    val autostart = Intent().setClassName(
                        "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    )
                    if (!openSettings(autostart, appSettings())) advance()
                }
                .setNegativeButton(R.string.gateway_background_skip) { _, _ -> advance() }
            Step.SUMMARY -> {
                val status = activity.getString(
                    if (isExempt()) R.string.gateway_background_exempt else R.string.gateway_background_optimized,
                )
                val guidance = activity.getString(
                    if (isXiaomi) R.string.gateway_background_miui_summary else R.string.gateway_background_generic,
                )
                builder.setMessage("$status\n\n$guidance")
                    .setPositiveButton(android.R.string.ok) { _, _ -> step = null }
                if (!isXiaomi) {
                    builder.setNeutralButton(R.string.gateway_background_open_settings) { _, _ ->
                        if (!openSettings(appSettings(), Intent(Settings.ACTION_SETTINGS))) {
                            step = null
                        }
                    }
                }
            }
            Step.ANDROID, null -> return
        }
        dialog = builder.setOnCancelListener { step = null }.show()
    }

    private fun openSettings(vararg intents: Intent): Boolean {
        for (intent in intents) {
            try {
                settings.launch(intent)
                waitingForSettings = true
                return true
            } catch (_: ActivityNotFoundException) {
                // Some manufacturers omit or rename these screens.
            } catch (_: SecurityException) {
                // Some manufacturer settings are restricted to system apps.
            }
        }
        return false
    }

    private fun appSettings() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)

    private fun isExempt() = activity.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(activity.packageName)
}
