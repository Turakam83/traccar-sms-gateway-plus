package org.traccar.gateway

import kotlinx.serialization.Serializable

internal const val SMS_RESULT_TIMEOUT_MS = 120_000L
internal const val SMS_RESULT_RETENTION_MS = 86_400_000L

@Serializable
internal data class SmsSegmentResult(
    val index: Int,
    val resultCode: Int?,
    val errorCode: Int? = null,
    val noDefault: Boolean = false,
)

@Serializable
internal data class GatewaySmsStatus(
    val id: String,
    val status: String,
    val totalSegments: Int,
    val segments: List<SmsSegmentResult>,
)

internal fun smsStatus(
    segments: List<SmsSegmentResult>,
    expired: Boolean,
    submissionFailed: Boolean,
): String {
    val successes = segments.count { it.resultCode == android.app.Activity.RESULT_OK }
    if (successes == segments.size) return "sent"
    if (submissionFailed) return if (successes > 0) "partial" else "failed"
    if (segments.any { it.resultCode == null }) return if (expired) "unknown" else "pending"
    return if (successes > 0) "partial" else "failed"
}
