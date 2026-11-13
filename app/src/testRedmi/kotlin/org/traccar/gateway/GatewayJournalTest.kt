package org.traccar.gateway

import android.app.Activity
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE, application = Application::class)
class GatewayJournalTest {
    private val journal = GatewayJournal.get(RuntimeEnvironment.getApplication())

    @Test fun `callbacks are indexed by part and the first result for a part wins`() {
        val id = UUID.randomUUID().toString()
        journal.create(id, 2)
        journal.attachHistory(id, "content://sms/1")

        assertTrue(journal.recordSegment(id, 2, Activity.RESULT_OK, null, false))
        assertFalse(journal.recordSegment(id, 2, 1, 100, true))
        assertFalse(journal.recordSegment(id, 3, Activity.RESULT_OK, null, false))
        assertEquals("pending", journal.request(id)?.status?.status)

        assertTrue(journal.recordSegment(id, 1, 2, 31, true))
        val status = checkNotNull(journal.request(id)).status
        assertEquals("partial", status.status)
        assertEquals(listOf(SmsSegmentResult(1, 2, 31, true), SmsSegmentResult(2, Activity.RESULT_OK)), status.segments)
        assertTrue(id in journal.pendingHistory())
        journal.historyDone(id)
        assertFalse(id in journal.pendingHistory())
    }

    @Test fun `expired requests are unavailable and cannot accept callbacks`() {
        val id = UUID.randomUUID().toString()
        journal.create(id, 1)
        journal.writableDatabase.execSQL("UPDATE sms SET created = 0 WHERE id = ?", arrayOf(id))
        assertNull(journal.request(id))
        assertFalse(journal.recordSegment(id, 1, Activity.RESULT_OK, null, false))

        journal.create(UUID.randomUUID().toString(), 1)
        journal.readableDatabase.rawQuery("SELECT 1 FROM segment WHERE sms_id = ?", arrayOf(id)).use {
            assertFalse(it.moveToFirst())
        }
    }
}
