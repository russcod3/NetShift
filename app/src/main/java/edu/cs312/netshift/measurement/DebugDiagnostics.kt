package edu.cs312.netshift.measurement

import java.io.IOException
import okhttp3.Response

/**
 * Debug-only switch for capturing the response headers and a short body slice of a rejected download.
 * It stays false unless debug code sets it (the src/debug harness does); release builds never enable it,
 * so nothing is captured or shown there. Cookies are redacted; the body slice is at most 200 printable chars.
 */
object DebugDiagnostics {
    @Volatile var enabled = false

    private const val BODY_SLICE_BYTES = 256L
    private const val MAX_CHARS = 200

    fun failure(phase: String, request: String, error: Throwable, httpStatus: Int? = null): String {
        val actual = (error as? EndpointException)?.cause ?: error
        val message = actual.message.orEmpty()
            .map { if (it in ' '..'~') it else ' ' }
            .joinToString("")
            .take(MAX_CHARS)
        return "phase=$phase request=$request exception=${actual.javaClass.simpleName} " +
            "message=\"$message\" httpStatus=${httpStatus ?: "none"}"
    }

    fun describe(response: Response, counter: PayloadCounter? = null): String {
        val headers = response.headers.joinToString("; ") { (name, value) ->
            if (name.equals("set-cookie", ignoreCase = true)) "$name: (redacted)" else "$name: $value"
        }
        val slice = try {
            val bytes = response.peekBody(BODY_SLICE_BYTES).bytes()
            counter?.add(bytes.size.toLong())
            String(bytes, Charsets.UTF_8).map { if (it in ' '..'~') it else '.' }.joinToString("").take(MAX_CHARS)
        } catch (e: IOException) {
            "(unreadable: ${e.javaClass.simpleName})"
        }
        return "HTTP ${response.code} ${response.message} | $headers | body=\"$slice\""
    }
}
