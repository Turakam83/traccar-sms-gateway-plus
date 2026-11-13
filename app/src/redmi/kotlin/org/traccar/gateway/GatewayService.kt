@file:Suppress("DEPRECATION")

package org.traccar.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.preference.PreferenceManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.Json
import org.fossify.messages.R
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object GatewaySettings {
    const val PORT = 8082
    private const val KEY = "gateway_api_key"
    private const val ENABLED = "gateway_enabled"

    private fun preferences(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    fun enabled(context: Context) = preferences(context).getBoolean(ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) = preferences(context).edit().putBoolean(ENABLED, enabled).commit()
    fun key(context: Context) = preferences(context).getString(KEY, null)
    fun setKey(context: Context, key: String) = preferences(context).edit().putString(KEY, key).commit()
}

class GatewayService : Service() {
    private val recovery = GatewayRecovery()
    private lateinit var journal: GatewayJournal
    private lateinit var sender: GatewaySms
    private var server: GatewayServer? = null
    private var healthId = ""
    private var hasStarted = false

    @Volatile private var active = true
    @Volatile private var tick: ScheduledFuture<*>? = null

    override fun onCreate() {
        super.onCreate()
        journal = GatewayJournal.get(this)
        sender = GatewaySms(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!GatewaySettings.enabled(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!hasStarted) {
            try {
                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (error: RuntimeException) {
                executor.execute { journal.record("Gateway foreground start failed: ${error.javaClass.simpleName}") }
                stopSelf(startId)
                return START_NOT_STICKY
            }
            hasStarted = true
            state = State.STARTING
            val reason = intent?.getStringExtra(START_REASON) ?: "Android sticky restart"
            executor.execute { journal.record("Gateway start requested: $reason") }
            schedule(0)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        active = false
        tick?.cancel(false)
        state = State.STOPPED
        val reason = if (GatewaySettings.enabled(this)) "service destroyed" else "disabled in the app"
        executor.execute {
            closeServer()
            journal.record("Gateway stopped: $reason")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun schedule(delay: Long) {
        if (canRun()) tick = executor.schedule(::watch, delay, TimeUnit.MILLISECONDS)
    }

    private fun canRun() = active && GatewaySettings.enabled(this)

    private fun watch() {
        if (!canRun()) return
        val delay = try {
            maintainServer()
        } catch (error: Exception) {
            journal.record("Gateway server error: ${error.javaClass.simpleName}")
            closeServer()
            retry()
        }
        runCatching {
            journal.pruneExpired()
            journal.pendingHistory().forEach { GatewaySmsHistory.sync(applicationContext, it) }
        }.onFailure { journal.record("Gateway history reconciliation unavailable: ${it.javaClass.simpleName}") }
        schedule(delay)
    }

    private fun maintainServer(): Long {
        val existing = server
        if (existing == null) {
            startServer()
        } else if (healthy(existing)) {
            recovery.healthy()
        } else if (recovery.failedProbe()) {
            journal.record("Gateway HTTP probe failed twice; restarting server")
            closeServer()
            return retry()
        }
        return PROBE_INTERVAL_MS
    }

    private fun retry(): Long {
        if (canRun()) state = State.RETRYING
        val delay = recovery.nextRetry()
        journal.record("Gateway HTTP retry in ${delay / 1_000} seconds")
        return delay
    }

    private fun startServer() {
        val key = checkNotNull(GatewaySettings.key(this)?.takeIf(String::isNotBlank))
        healthId = UUID.randomUUID().toString()
        val newServer = GatewayServer(GatewaySettings.PORT, key, GatewayServer.Handler { phone, message, slot ->
            if (canRun()) sender.send(phone, message, slot) else GatewayServer.Submission(error = "Gateway is stopping")
        }, statusLookup = { id ->
            journal.request(id)?.status?.let { Json.encodeToString(it) }
        }, healthId = healthId)
        server = newServer
        newServer.start()
        check(healthy(newServer))
        if (canRun()) {
            state = State.RUNNING
            journal.record("Gateway HTTP server is ready on port ${GatewaySettings.PORT}")
        } else {
            closeServer()
        }
    }

    private fun closeServer() {
        val running = server ?: return
        server = null
        runCatching { running.stop() }
            .onFailure { journal.record("Gateway server shutdown failed: ${it.javaClass.simpleName}") }
    }

    private fun healthy(running: GatewayServer): Boolean {
        if (!running.isStarted) return false
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", GatewaySettings.PORT), 2_000)
                socket.soTimeout = 2_000
                socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray())
                val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                val status = reader.readLine() ?: return@use false
                status.startsWith("HTTP/1.1 200 ") && generateSequence { reader.readLine() }
                    .take(32).takeWhile(String::isNotEmpty)
                    .any { it.substringBefore(':').equals(GatewayServer.HEALTH_HEADER, ignoreCase = true) &&
                        it.substringAfter(':').trim() == healthId }
            }
        }.getOrDefault(false)
    }

    private fun createNotification(): Notification {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL, getString(R.string.gateway_channel), NotificationManager.IMPORTANCE_LOW,
        ))
        val open = PendingIntent.getActivity(this, 0, Intent(this, GatewayActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_messenger)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setContentTitle(getString(R.string.redmi_service_title))
            .setContentText(getString(R.string.gateway_service_description))
            .setContentIntent(open)
            .build()
    }

    internal enum class State { STOPPED, STARTING, RUNNING, RETRYING }

    companion object {
        private const val PROBE_INTERVAL_MS = 60_000L
        private const val NOTIFICATION_ID = 8722227
        private const val CHANNEL = "notifications_gateway"
        private const val START_REASON = "gateway_start_reason"
        private val executor = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }

        @Volatile internal var state = State.STOPPED
            private set

        internal fun requestStart(context: Context, reason: String) {
            if (!GatewaySettings.enabled(context)) return
            val intent = Intent(context, GatewayService::class.java).putExtra(START_REASON, reason)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: RuntimeException) {
                val journal = GatewayJournal.get(context)
                executor.execute { journal.record("Gateway start blocked ($reason): ${error.javaClass.simpleName}") }
            }
        }
    }
}

class GatewayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> GatewayService.requestStart(context, "boot completed")
            Intent.ACTION_MY_PACKAGE_REPLACED -> GatewayService.requestStart(context, "app updated")
        }
    }
}
