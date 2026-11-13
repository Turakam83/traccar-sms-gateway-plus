package org.traccar.gateway

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI

class GatewayServerTest {
    private val sent = mutableListOf<Triple<String, String, Int?>>()
    private val server = GatewayServer(0, "test-key", GatewayServer.Handler { phone, message, slot ->
        sent.add(Triple(phone, message, slot))
        GatewayServer.Submission(id = "test-id")
    }, statusLookup = { id -> if (id == "test-id") """{"status":"sent"}""" else null }, healthId = "test-instance")

    @Before fun start() = server.start()
    @After fun stop() = server.stop()

    @Test fun `existing POST submits intact Unicode and returns 200 with an empty body`() {
        val message = "Test 🌨é ".repeat(40)
        val response = request("/", "test-key", """{"to":"+1234567890","message":"$message","slot":1}""")
        assertEquals(200, response.code)
        assertEquals("", response.body)
        assertEquals("test-id", response.requestId)
        assertEquals(listOf(Triple("+1234567890", message, 1)), sent)
    }

    @Test fun `slot stays optional and numeric phone remains accepted`() {
        val response = request("/", "test-key", """{"to":1234,"message":"Hi"}""")
        assertEquals(200, response.code)
        assertEquals(listOf(Triple("1234", "Hi", null)), sent)
    }

    @Test fun `status lookup requires authorization and has no side effect`() {
        assertEquals(401, request("/sms/status/test-id").code)
        assertEquals(401, request("/sms/status/test-id", "wrong").code)
        assertEquals(404, request("/sms/status/unknown", "test-key").code)
        val response = request("/sms/status/test-id", "test-key")
        assertEquals(200, response.code)
        assertEquals("no-store", response.cacheControl)
        assertEquals("""{"status":"sent"}""", response.body)
        assertEquals(emptyList<Triple<String, String, Int?>>(), sent)
    }

    @Test fun `invalid requests do not call the SMS handler`() {
        assertEquals(401, request("/", body = """{"to":"123","message":"hi"}""").code)
        assertEquals(400, request("/", "test-key", "{").code)
        val missing = request("/", "test-key", "{}")
        assertEquals(500, missing.code)
        assertNull(missing.requestId)
        assertEquals(emptyList<Triple<String, String, Int?>>(), sent)
    }

    @Test fun `loopback health probe identifies the instance without sending SMS`() {
        val response = request("/")
        assertEquals(200, response.code)
        assertEquals("test-instance", response.healthId)
        assertEquals(emptyList<Triple<String, String, Int?>>(), sent)
    }

    private fun request(path: String, key: String? = null, body: String? = null): Response {
        val port = server.connectors.single().localPort
        val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            key?.let { connection.setRequestProperty("Authorization", it) }
            body?.let {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { stream -> stream.write(it.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code < 400) connection.inputStream else connection.errorStream
            return Response(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty(),
                connection.getHeaderField("X-SMS-Request-Id"), connection.getHeaderField("Cache-Control"),
                connection.getHeaderField(GatewayServer.HEALTH_HEADER))
        } finally {
            connection.disconnect()
        }
    }

    private data class Response(
        val code: Int, val body: String, val requestId: String?, val cacheControl: String?, val healthId: String?,
    )
}
