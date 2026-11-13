package org.traccar.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.eclipse.jetty.http.HttpHeaders
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class GatewayServer(
    port: Int,
    private val key: String?,
    private val handler: Handler,
    private val statusLookup: ((String) -> String?)? = null,
    private val healthId: String? = null,
) : Server(port) {

    fun interface Handler {
        fun onSendMessage(phone: String, message: String, slot: Int?): Submission
    }

    data class Submission(val id: String? = null, val error: String? = null)

    init {
        setHandler(object : AbstractHandler() {
            override fun handle(
                target: String,
                baseRequest: Request,
                request: HttpServletRequest,
                response: HttpServletResponse
            ) {
                response.contentType = "text/html; charset=utf-8"

                when {
                    request.method == "POST" -> handlePost(request, response)
                    statusLookup != null && request.method == "GET" && target.startsWith(STATUS_PATH) -> {
                        handleStatus(target.removePrefix(STATUS_PATH), request, response)
                    }
                    else -> {
                        if (healthId != null && target == "/" && request.remoteAddr == "127.0.0.1") {
                            response.setHeader(HEALTH_HEADER, healthId)
                        }
                        handleGet(response)
                    }
                }

                baseRequest.isHandled = true
            }
        })
    }

    private fun handlePost(
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        if (!isAuthorized(request)) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }

        val sms = try {
            if (request.characterEncoding == null) request.characterEncoding = "UTF-8"
            val body = Json.parseToJsonElement(request.reader.readText()) as? JsonObject
                ?: throw IllegalArgumentException()
            val slot = body["slot"]?.takeUnless { it == JsonNull }?.let {
                (it as? JsonPrimitive)?.intOrNull ?: throw IllegalArgumentException()
            }
            SmsRequest(body.text("to"), body.text("message"), slot)
        } catch (_: IllegalArgumentException) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            response.writer.print("Invalid SMS request JSON")
            return
        }

        val result = if (sms.phone != null && sms.message != null) {
            handler.onSendMessage(sms.phone, sms.message, sms.slot)
        } else {
            Submission(error = "Missing phone or message")
        }

        result.id?.let { response.setHeader("X-SMS-Request-Id", it) }
        if (result.error == null) {
            response.status = HttpServletResponse.SC_OK
        } else {
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            response.writer.print(result.error)
        }
    }

    private fun handleStatus(id: String, request: HttpServletRequest, response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        if (!isAuthorized(request)) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }
        val status = statusLookup?.invoke(id)
        if (status == null) {
            response.status = HttpServletResponse.SC_NOT_FOUND
            return
        }
        response.contentType = "application/json; charset=utf-8"
        response.writer.print(status)
    }

    private fun isAuthorized(request: HttpServletRequest): Boolean =
        !key.isNullOrBlank() && request.getHeader(HttpHeaders.AUTHORIZATION) == key

    private fun JsonObject.text(field: String): String? = (get(field) as? JsonPrimitive)
        ?.takeIf { it != JsonNull && (it.isString || it.content.toDoubleOrNull() != null) }
        ?.content

    private data class SmsRequest(val phone: String?, val message: String?, val slot: Int?)

    companion object {
        private const val STATUS_PATH = "/sms/status/"
        const val HEALTH_HEADER = "X-Gateway-Instance"
    }

    private fun handleGet(
        response: HttpServletResponse
    ) {
        response.writer.print(
            """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body>
                <p>Send SMS using following API:</p>
                <pre>
                POST /
                {
                    "to": "+10000000000",
                    "message": "Your message"
                }
                </pre>
            </body>
            </html>
            """.trimIndent()
        )
    }

}
