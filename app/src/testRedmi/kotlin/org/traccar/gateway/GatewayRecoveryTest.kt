package org.traccar.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRecoveryTest {
    @Test fun `a healthy probe resets consecutive failure count`() {
        val recovery = GatewayRecovery()
        assertFalse(recovery.failedProbe())
        recovery.healthy()
        assertFalse(recovery.failedProbe())
        assertTrue(recovery.failedProbe())
    }

    @Test fun `backoff grows to five minutes and resets after recovery`() {
        val recovery = GatewayRecovery()
        assertEquals(listOf(5L, 10L, 20L, 40L, 80L, 160L, 300L, 300L), List(8) { recovery.nextRetry() / 1_000 })
        recovery.healthy()
        assertEquals(5_000L, recovery.nextRetry())
    }
}
