package org.traccar.gateway

internal class GatewayRecovery {
    private var failures = 0
    private var retryDelay = 5_000L

    fun healthy() {
        failures = 0
        retryDelay = 5_000L
    }

    fun failedProbe(): Boolean = ++failures >= 2

    fun nextRetry(): Long {
        failures = 0
        val delay = retryDelay
        retryDelay = (retryDelay * 2).coerceAtMost(300_000L)
        return delay
    }
}
