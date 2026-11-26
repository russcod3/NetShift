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
)

/**
 * One streamed GET of a fixed-size object: no disk, one reused 64 KiB buffer, byte-accurate counting.
 * Stops at EOF, the requested byte count, the planned time limit, or the payload ceiling. The announced
 * Content-Length never replaces bytes actually read.
 */
class DownloadRunner(private val clock: MonotonicClock) {

    suspend fun run(
        client: OkHttpClient,
        endpoint: String,
        bytesRequested: Long,
        planTimeoutMs: Long,
        deadlineNs: Long,
        counter: PayloadCounter,
        onProgress: (Long) -> Unit = {},
    ): DownloadResult {
        val callStartNs = clock.nowNs()
        val limitNs = minOf(callStartNs + planTimeoutMs * 1_000_000L, deadlineNs)
        val limitMs = ((limitNs - callStartNs) / 1_000_000L).coerceAtLeast(1)
        val call = client.withLimits(limitMs, minOf(2_000L, limitMs))
            .newCall(requestFor(EndpointUrls.download(endpoint, bytesRequested)))

        var bytes = 0L
        var bodyStartNs = 0L
        var lastReadNs = 0L
        var bodyStarted = false
        var stop: String? = null
        var detail: String? = null

        try {
            call.executeAndRead { response ->
                try {
                    validateResponse(response)
                } catch (e: EndpointException) {
                    stop = StopReason.HTTP_ERROR
                    detail = e.category
                    return@executeAndRead
                }
                val body = response.body
                if (body == null) {
                    stop = StopReason.HTTP_ERROR
                    detail = "NO_BODY"
                    return@executeAndRead
                }
                if (body.contentLength() > bytesRequested) {
                    stop = StopReason.HTTP_ERROR
                    detail = "OVERSIZED"
                    return@executeAndRead
                }
                val stream = body.byteStream()
                val buffer = ByteArray(BUFFER_BYTES)
                bodyStarted = true
                bodyStartNs = clock.nowNs()
                lastReadNs = bodyStartNs
                while (true) {
                    val room = bytesRequested - bytes
                    if (room <= 0) { stop = StopReason.BYTE_LIMIT; break }
                    val n = stream.read(buffer, 0, minOf(buffer.size.toLong(), room).toInt())
                    if (n < 0) { stop = StopReason.EOF; break }
                    val now = clock.nowNs()
                    bytes += n
                    lastReadNs = now
                    if (counter.add(n.toLong()) > counter.ceiling) { stop = StopReason.BYTE_LIMIT; break }
                    onProgress(bytes)
                    if (now >= limitNs) { stop = StopReason.TIME_LIMIT; break }
                }
            }
        } catch (e: IOException) {
            // A cancelled call also surfaces as IOException; let the cancellation win.
            currentCoroutineContext().ensureActive()
            val plannedTimeout = bodyStarted && e is InterruptedIOException &&
                clock.nowNs() >= limitNs - TIMEOUT_SLACK_NS
            stop = if (plannedTimeout) StopReason.TIME_LIMIT else StopReason.IO_ERROR
            detail = e.javaClass.simpleName
        }

        return DownloadResult(
            bytes = bytes,
            bodyDurationMs = if (bytes > 0) (lastReadNs - bodyStartNs) / 1_000_000.0 else null,
            wallDurationMs = (clock.nowNs() - callStartNs) / 1_000_000L,
            stopReason = stop ?: StopReason.IO_ERROR,
            detail = detail,
        )
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val TIMEOUT_SLACK_NS = 100_000_000L
    }
}
