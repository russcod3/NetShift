package edu.cs312.netshift.measurement

import edu.cs312.netshift.model.StopReason
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient

data class DownloadResult(
    val bytes: Long,
    /** First body read to last included read; null when no bytes arrived. Not link speed. */
    val bodyDurationMs: Double?,
    val wallDurationMs: Long,
    val stopReason: String,
    val detail: String? = null,
    /** Debug-only: response headers and a short body slice of a rejected download (see DebugDiagnostics). */
    val diagnostics: String? = null,
)

/**
 * Sequential streamed GETs, each bounded by maxRequestBytes: no disk, one reused 64 KiB buffer,
 * byte-accurate counting. Stops at EOF, the combined byte target, the planned time limit, or payload
 * ceiling. The announced Content-Length never replaces bytes actually read.
 */
class DownloadRunner(private val clock: MonotonicClock) {

    suspend fun run(
        client: OkHttpClient,
        endpoint: String,
        bytesRequested: Long,
        planTimeoutMs: Long,
        deadlineNs: Long,
        counter: PayloadCounter,
        maxRequestBytes: Long = bytesRequested,
        onProgress: (Long) -> Unit = {},
    ): DownloadResult {
        require(bytesRequested >= 0 && maxRequestBytes > 0)
        val callStartNs = clock.nowNs()
        val limitNs = minOf(callStartNs + planTimeoutMs * 1_000_000L, deadlineNs)
        var bytes = 0L
        var bodyDurationNs = 0L
        var stop: String? = null
        var detail: String? = null
        var diagnostics: String? = null
        var requestNumber = 0

        while (bytes < bytesRequested && clock.nowNs() < limitNs && stop == null) {
            requestNumber++
            val requestLabel = "download[$requestNumber]"
            val requestBytes = minOf(maxRequestBytes, bytesRequested - bytes)
            val requestStartNs = clock.nowNs()
            val limitMs = ((limitNs - requestStartNs) / 1_000_000L).coerceAtLeast(1)
            val call = client.withLimits(limitMs, minOf(2_000L, limitMs))
                .newCall(requestFor(EndpointUrls.download(endpoint, requestBytes)))
            var requestBytesRead = 0L
            var requestBodyDurationNs = 0L
            var bodyStarted = false
            var httpStatus: Int? = null
            try {
                call.executeAndRead { response ->
                httpStatus = response.code
                try {
                    validateResponse(response)
                } catch (e: EndpointException) {
                    stop = StopReason.HTTP_ERROR
                    detail = e.category
                    if (diagnostics == null && DebugDiagnostics.enabled) {
                        diagnostics = DebugDiagnostics.failure("DOWNLOADING", requestLabel, e, response.code) +
                            " | " + DebugDiagnostics.describe(response, counter)
                    }
                    return@executeAndRead
                }
                val body = response.body
                if (body == null) {
                    stop = StopReason.HTTP_ERROR
                    detail = "NO_BODY"
                    if (DebugDiagnostics.enabled) {
                        val e = EndpointException("NO_BODY", response.code, requestLabel)
                        diagnostics = DebugDiagnostics.failure("DOWNLOADING", requestLabel, e, response.code)
                    }
                    return@executeAndRead
                }
                if (body.contentLength() > requestBytes) {
                    stop = StopReason.HTTP_ERROR
                    detail = "OVERSIZED"
                    if (DebugDiagnostics.enabled) {
                        val e = EndpointException("OVERSIZED", response.code, requestLabel)
                        diagnostics = DebugDiagnostics.failure("DOWNLOADING", requestLabel, e, response.code)
                    }
                    return@executeAndRead
                }
                val stream = body.byteStream()
                val buffer = ByteArray(BUFFER_BYTES)
                bodyStarted = true
                var lastReadNs = clock.nowNs()
                while (true) {
                    val room = requestBytes - requestBytesRead
                    if (room <= 0) { stop = StopReason.BYTE_LIMIT; break }
                    val payloadRoom = counter.ceiling - counter.value
                    if (payloadRoom <= 0) { stop = StopReason.BYTE_LIMIT; break }
                    val n = stream.read(buffer, 0, minOf(buffer.size.toLong(), room, payloadRoom).toInt())
                    if (n < 0) { stop = StopReason.EOF; break }
                    val now = clock.nowNs()
                    requestBytesRead += n
                    bytes += n
                    requestBodyDurationNs += now - lastReadNs
                    lastReadNs = now
                    if (counter.add(n.toLong()) > counter.ceiling) { stop = StopReason.BYTE_LIMIT; break }
                    onProgress(bytes)
                    if (now >= limitNs) { stop = StopReason.TIME_LIMIT; break }
                }
                }
            } catch (e: IOException) {
                // A cancelled call also surfaces as IOException; let the cancellation win.
                currentCoroutineContext().ensureActive()
                // Closing a response early can itself report an IOException. Preserve an intentional
                // byte/time stop already reached while reading that response.
                if (stop == null) {
                    val plannedTimeout = bodyStarted && e is InterruptedIOException &&
                        clock.nowNs() >= limitNs - TIMEOUT_SLACK_NS
                    stop = if (plannedTimeout) StopReason.TIME_LIMIT else StopReason.IO_ERROR
                    detail = e.javaClass.simpleName
                    if (DebugDiagnostics.enabled) {
                        diagnostics = DebugDiagnostics.failure("DOWNLOADING", requestLabel, e, httpStatus)
                    }
                }
            }
            bodyDurationNs += requestBodyDurationNs
            if (stop == StopReason.BYTE_LIMIT && bytes < bytesRequested &&
                counter.value < counter.ceiling && clock.nowNs() < limitNs
            ) stop = null
        }
        if (stop == null) stop = if (bytes >= bytesRequested) StopReason.BYTE_LIMIT else StopReason.TIME_LIMIT

        return DownloadResult(
            bytes = bytes,
            bodyDurationMs = if (bytes > 0) bodyDurationNs / 1_000_000.0 else null,
            wallDurationMs = (clock.nowNs() - callStartNs) / 1_000_000L,
            stopReason = stop ?: StopReason.IO_ERROR,
            detail = detail,
            diagnostics = diagnostics,
        )
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val TIMEOUT_SLACK_NS = 100_000_000L
    }
}
