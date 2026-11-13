package org.traccar.gateway

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File

internal class GatewayJournal private constructor(context: Context) : SQLiteOpenHelper(
    context, File(context.noBackupFilesDir, "gateway.db").path, null, 1,
) {
    data class Request(val status: GatewaySmsStatus, val historyUri: String?, val submissionFailed: Boolean)

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE sms (id TEXT PRIMARY KEY, created INTEGER NOT NULL, uri TEXT,
                rejected INTEGER NOT NULL DEFAULT 0, history_done INTEGER NOT NULL DEFAULT 0)
        """.trimIndent())
        db.execSQL("CREATE INDEX sms_created ON sms(created)")
        db.execSQL("""
            CREATE TABLE segment (sms_id TEXT NOT NULL REFERENCES sms(id) ON DELETE CASCADE,
                part INTEGER NOT NULL, code INTEGER, error INTEGER, no_default INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (sms_id, part))
        """.trimIndent())
        db.execSQL("CREATE TABLE event (id INTEGER PRIMARY KEY, time INTEGER NOT NULL, detail TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun create(id: String, count: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("sms", "created <= ?", arrayOf(expirationCutoff()))
            db.insertOrThrow("sms", null, ContentValues().apply {
                put("id", id)
                put("created", System.currentTimeMillis())
            })
            for (part in 1..count) {
                db.insertOrThrow("segment", null, ContentValues().apply {
                    put("sms_id", id)
                    put("part", part)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun attachHistory(id: String, uri: String) {
        check(updateRequest(id, ContentValues().apply { put("uri", uri) }) == 1)
    }

    fun rejectSubmission(id: String) {
        updateRequest(id, ContentValues().apply { put("rejected", 1) })
    }

    fun historyDone(id: String) {
        updateRequest(id, ContentValues().apply { put("history_done", 1) })
    }

    fun pruneExpired() {
        writableDatabase.delete("sms", "created <= ?", arrayOf(expirationCutoff()))
    }

    private fun updateRequest(id: String, values: ContentValues): Int =
        writableDatabase.update("sms", values, "id = ?", arrayOf(id))

    fun recordSegment(id: String, part: Int, code: Int, error: Int?, noDefault: Boolean): Boolean =
        writableDatabase.update("segment", ContentValues().apply {
            put("code", code)
            put("error", error)
            put("no_default", noDefault)
        }, "sms_id = ? AND part = ? AND code IS NULL AND EXISTS (SELECT 1 FROM sms WHERE id = ? AND created > ?)",
            arrayOf(id, part.toString(), id, expirationCutoff())) == 1

    fun request(id: String): Request? = readableDatabase.rawQuery("""
        SELECT sms.created, sms.uri, sms.rejected, segment.part, segment.code, segment.error, segment.no_default
        FROM sms JOIN segment ON sms.id = segment.sms_id
        WHERE sms.id = ? AND sms.created > ? ORDER BY segment.part
    """.trimIndent(), arrayOf(id, expirationCutoff())).use { cursor ->
        if (!cursor.moveToFirst()) return null
        val expired = System.currentTimeMillis() - cursor.getLong(0) >= SMS_RESULT_TIMEOUT_MS
        val uri = cursor.getString(1)
        val rejected = cursor.getInt(2) == 1
        val segments = buildList {
            do {
                add(SmsSegmentResult(cursor.getInt(3), cursor.intOrNull(4), cursor.intOrNull(5), cursor.getInt(6) == 1))
            } while (cursor.moveToNext())
        }
        Request(GatewaySmsStatus(id, smsStatus(segments, expired, rejected), segments.size, segments), uri, rejected)
    }

    fun pendingHistory(): List<String> = readableDatabase.rawQuery("""
        SELECT id FROM sms WHERE uri IS NOT NULL AND history_done = 0 AND created > ? AND (
            rejected = 1 OR EXISTS (SELECT 1 FROM segment WHERE sms_id = sms.id AND code != ?) OR
            NOT EXISTS (SELECT 1 FROM segment WHERE sms_id = sms.id AND code IS NULL)
        )
    """.trimIndent(), arrayOf(expirationCutoff(), android.app.Activity.RESULT_OK.toString())).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    fun record(detail: String) {
        Log.i("TraccarRedmi", detail)
        runCatching {
            val db = writableDatabase
            db.insertOrThrow("event", null, ContentValues().apply {
                put("time", System.currentTimeMillis())
                put("detail", detail)
            })
            db.execSQL("DELETE FROM event WHERE id <= (SELECT MAX(id) - 500 FROM event)")
        }.onFailure { Log.w("TraccarRedmi", "Diagnostic storage unavailable") }
    }

    fun events(): List<Pair<Long, String>> = readableDatabase.rawQuery(
        "SELECT time, detail FROM event ORDER BY id DESC LIMIT 500", null,
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getString(1)) }
    }

    private fun Cursor.intOrNull(column: Int): Int? = if (isNull(column)) null else getInt(column)

    private fun expirationCutoff() = (System.currentTimeMillis() - SMS_RESULT_RETENTION_MS).toString()

    companion object {
        @Volatile private var instance: GatewayJournal? = null

        fun get(context: Context): GatewayJournal = instance ?: synchronized(this) {
            instance ?: GatewayJournal(context.applicationContext).also { instance = it }
        }
    }
}
