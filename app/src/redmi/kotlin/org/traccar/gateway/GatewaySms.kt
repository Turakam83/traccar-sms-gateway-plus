@file:Suppress("DEPRECATION")

package org.traccar.gateway

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony.Sms
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import org.fossify.messages.extensions.getThreadId
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.messagingUtils
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.refreshMessages
import java.util.UUID
import java.util.concurrent.Executors

internal class GatewaySms(private val context: Context) {
    private val journal = GatewayJournal.get(context)

    @SuppressLint("MissingPermission")
    fun send(phone: String, message: String, slot: Int?): GatewayServer.Submission {
        val destination = PhoneNumberUtils.stripSeparators(phone)
        if (destination.isNullOrEmpty() || message.isEmpty()) {
            return GatewayServer.Submission(error = "Missing phone or message")
        }
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            journal.record("SMS submission rejected: SEND_SMS permission missing")
            return GatewayServer.Submission(error = "SEND_SMS permission is missing")
        }

        val manager = try {
            if (slot == null) {
                SmsManager.getDefault()
            } else {
                val subscriptions = context.getSystemService(SubscriptionManager::class.java)
                val subscription = subscriptions.getActiveSubscriptionInfoForSimSlotIndex(slot)
                    ?: return GatewayServer.Submission(error = "No active SIM in requested slot").also {
                        journal.record("SMS submission rejected: requested SIM slot is inactive")
                    }
                SmsManager.getSmsManagerForSubscriptionId(subscription.subscriptionId)
            }
        } catch (error: Exception) {
            journal.record("SMS SIM selection failed: ${error.javaClass.simpleName}")
            val detail = when (error) {
                is SecurityException -> "Permission to read the requested SIM is missing"
                is IllegalArgumentException -> "Invalid SIM slot"
                else -> "Unable to select the SMS subscription"
            }
            return GatewayServer.Submission(error = detail)
        }

        val id = UUID.randomUUID().toString()
        val parts = try {
            manager.divideMessage(message).also { check(it.isNotEmpty()) }
        } catch (error: Exception) {
            journal.record("SMS $id segmentation failed: ${error.javaClass.simpleName}")
            return GatewayServer.Submission(error = "Android could not segment the SMS")
        }

        try {
            journal.create(id, parts.size)
        } catch (error: Exception) {
            journal.record("SMS $id tracking unavailable: ${error.javaClass.simpleName}")
            return GatewayServer.Submission(error = "Unable to create SMS tracking record")
        }

        try {
            if (Sms.getDefaultSmsPackage(context) == context.packageName) {
                createHistory(id, destination, message, manager.subscriptionId)
            }
        } catch (error: Exception) {
            reject(id, "history creation", error)
            return GatewayServer.Submission(id = id, error = "Unable to save SMS in the conversation")
        }

        val sentIntents = ArrayList<PendingIntent>(parts.size)
        try {
            parts.indices.forEach { sentIntents.add(sentIntent(id, it + 1)) }
            if (parts.size == 1) {
                manager.sendTextMessage(destination, null, parts.single(), sentIntents.single(), null)
            } else {
                manager.sendMultipartTextMessage(destination, null, parts, sentIntents, null)
            }
        } catch (error: Exception) {
            reject(id, "Android submission", error)
            return GatewayServer.Submission(id = id, error = "Android rejected SMS submission (${error.javaClass.simpleName})")
        }
        journal.record("SMS $id submitted to Android (${parts.size} segments, slot=${slot ?: "default"})")
        return GatewayServer.Submission(id = id)
    }

    private fun createHistory(id: String, phone: String, message: String, subscriptionId: Int) {
        val uri = context.messagingUtils.insertSmsMessage(
            subId = subscriptionId, dest = phone, text = message, timestamp = System.currentTimeMillis(),
            threadId = context.getThreadId(setOf(phone)),
        )
        try {
            journal.attachHistory(id, uri.toString())
        } catch (error: Exception) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw error
        }
        refreshConversations()
        refreshMessages()
    }

    private fun reject(id: String, step: String, error: Exception) {
        journal.record("SMS $id $step failed: ${error.javaClass.simpleName}")
        runCatching { journal.rejectSubmission(id) }
        GatewaySmsHistory.sync(context, id)
    }

    private fun sentIntent(id: String, part: Int): PendingIntent {
        val intent = Intent(context, GatewaySmsResultReceiver::class.java).apply {
            action = RESULT_ACTION
            data = Uri.parse("traccar-sms://result/$id/$part")
        }
        // Android fills in the modem error and the no-default-subscription flag.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_ONE_SHOT or mutable)
    }

    companion object {
        const val RESULT_ACTION = "org.traccar.gateway.redmi.SMS_SENT"
    }
}

internal object GatewaySmsHistory {
    @Synchronized
    fun sync(context: Context, id: String) {
        val journal = GatewayJournal.get(context)
        try {
            if (Sms.getDefaultSmsPackage(context) != context.packageName) return
            val request = journal.request(id) ?: return
            val uri = request.historyUri?.let(Uri::parse) ?: return
            val segments = request.status.segments
            val type = when {
                request.status.status == "sent" -> Sms.MESSAGE_TYPE_SENT
                request.submissionFailed || segments.any { it.resultCode != null && it.resultCode != Activity.RESULT_OK } ->
                    Sms.MESSAGE_TYPE_FAILED
                else -> return
            }
            val updated = context.contentResolver.update(uri, ContentValues().apply { put(Sms.TYPE, type) },
                "${Sms.TYPE} = ?", arrayOf(Sms.MESSAGE_TYPE_OUTBOX.toString()))
            if (updated > 0) {
                runCatching { context.messagesDB.updateType(checkNotNull(uri.lastPathSegment).toLong(), type) }
                    .onFailure { journal.record("SMS $id conversation cache update failed: ${it.javaClass.simpleName}") }
                refreshConversations()
                refreshMessages()
            }
            journal.historyDone(id)
        } catch (error: Exception) {
            journal.record("SMS $id history update failed: ${error.javaClass.simpleName}")
        }
    }
}

class GatewaySmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GatewaySms.RESULT_ACTION) return
        val path = intent.data?.pathSegments ?: return
        if (path.size != 2) return
        val (id, partValue) = path
        val part = partValue.toIntOrNull() ?: return
        val code = resultCode
        val error = intent.getIntExtra("errorCode", -1).takeUnless { it == -1 }
        val noDefault = intent.getBooleanExtra("noDefault", false)
        val pending = goAsync()
        val app = context.applicationContext
        results.execute {
            val journal = GatewayJournal.get(app)
            try {
                if (journal.recordSegment(id, part, code, error, noDefault)) {
                    journal.record("SMS $id segment $part result=$code modem=${error ?: "-"} noDefault=$noDefault")
                    GatewaySmsHistory.sync(app, id)
                }
            } catch (failure: Exception) {
                journal.record("SMS $id result storage failed: ${failure.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val results = Executors.newSingleThreadExecutor()
    }
}
