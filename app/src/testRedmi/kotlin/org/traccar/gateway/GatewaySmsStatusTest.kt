package org.traccar.gateway

import org.junit.Assert.assertEquals
import org.junit.Test

class GatewaySmsStatusTest {
    private val success = SmsSegmentResult(1, -1)
    private val failure = SmsSegmentResult(2, 1)
    private val pending = SmsSegmentResult(2, null)

    @Test fun `all callbacks must succeed for sent status`() {
        assertEquals("sent", smsStatus(listOf(success, SmsSegmentResult(2, -1)), false, false))
        assertEquals("partial", smsStatus(listOf(success, failure), false, false))
        assertEquals("failed", smsStatus(listOf(SmsSegmentResult(1, 2), failure), false, false))
    }

    @Test fun `timeout preserves unknown status until a late callback arrives`() {
        assertEquals("pending", smsStatus(listOf(success, pending), false, false))
        assertEquals("unknown", smsStatus(listOf(success, pending), true, false))
        assertEquals("partial", smsStatus(listOf(failure, success), true, false))
    }

    @Test fun `a synchronous rejection is reported without waiting for callbacks`() {
        assertEquals("failed", smsStatus(listOf(pending), false, true))
    }
}
