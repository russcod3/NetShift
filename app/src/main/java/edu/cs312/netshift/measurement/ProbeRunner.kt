package edu.cs312.netshift.measurement

import edu.cs312.netshift.model.MeasurementConfig
import edu.cs312.netshift.model.ProbeSample
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Response

data class ProbeRecord(val sample: ProbeSample, val startOffsetMs: Long, val httpStatus: Int?)

/**
 * HTTP-level latency probes (not ICMP): one untimed warm-up GET, then 20 timed 1-byte GETs, one per
 * 600 ms slot, never overlapping. The warm-up is the discarded first request (it absorbs DNS/TCP/TLS
 * setup) and is excluded from every metric. Later probes reuse the pooled connection; a reconnect after
 * a failure is part of that probe's time. A timed-out slot is a failed sample, never a zero RTT.
 */
class ProbeRunner(private val clock: MonotonicClock) {

    /** Excluded from metrics. Throws EndpointException (contract) or IOException (transport/timeout). */
    suspend fun warmUp(client: OkHttpClient, endpoint: String, timeoutMs: Long, counter: PayloadCounter) {
        client.withLimits(timeoutMs).newCall(requestFor(EndpointUrls.probe(endpoint)))
            .executeAndRead { response ->
                validateResponse(response)
                readExactlyOneByte(response, counter)
            }
    }

    /**
     * Runs up to 20 slots, reporting each finished probe through [onRecord]. Returns early (fewer than 20
     * records) if [deadlineNs] is reached; unlaunched slots are simply absent, never failed samples.
     * Throws EndpointException on a contract violation (stop the test; do not call it packet loss).
     */
    suspend fun runProbes(
        client: OkHttpClient,
        endpoint: String,
        slotMs: Long,
        probeTimeoutMs: Long,
        deadlineNs: Long,
        counter: PayloadCounter,
        onRecord: (ProbeRecord) -> Unit,
    ) {
        val phaseStartNs = clock.nowNs()
        for (index in 0 until MeasurementConfig.PROBE_COUNT) {
            val slotStartNs = phaseStartNs + index * slotMs * 1_000_000L
            val waitMs = (slotStartNs - clock.nowNs()) / 1_000_000L
            if (waitMs > 0) delay(waitMs)
            val remainingMs = (deadlineNs - clock.nowNs()) / 1_000_000L
            if (remainingMs <= 0) return
            onRecord(probeOnce(client, endpoint, index, phaseStartNs, minOf(probeTimeoutMs, remainingMs), counter))
        }
    }

    private suspend fun probeOnce(
        client: OkHttpClient,
        endpoint: String,
        index: Int,
        phaseStartNs: Long,
        timeoutMs: Long,
        counter: PayloadCounter,
    ): ProbeRecord {
        var startNs = 0L
        var endNs = 0L
        var status: Int? = null
        return try {
            client.withLimits(timeoutMs).newCall(requestFor(EndpointUrls.probe(endpoint)))
                .executeAndRead(onStart = { startNs = clock.nowNs() }) { response ->
                    status = response.code
                    validateResponse(response)
                    readExactlyOneByte(response, counter)
                    endNs = clock.nowNs()
                }
            ProbeRecord(
                sample = ProbeSample(index, (endNs - startNs) / 1_000_000.0),
                startOffsetMs = (startNs - phaseStartNs) / 1_000_000L,
                httpStatus = status,
            )
        } catch (e: EndpointException) {
            throw e
        } catch (e: SSLException) {
            throw EndpointException("TLS_ERROR")
        } catch (e: IOException) {
            // A cancelled call also surfaces as IOException; do not record it as a failed probe.
            currentCoroutineContext().ensureActive()
            ProbeRecord(
                sample = ProbeSample(index, null, failureCategory(e)),
                startOffsetMs = if (startNs == 0L) 0L else (startNs - phaseStartNs) / 1_000_000L,
                httpStatus = status,
            )
        }
    }

    private fun failureCategory(e: IOException): String = when (e) {
        is InterruptedIOException -> "TIMEOUT" // includes SocketTimeoutException and OkHttp call timeouts
        is UnknownHostException -> "DNS"
        is ConnectException, is NoRouteToHostException -> "CONNECT"
        else -> "IO"
    }
}

internal fun readExactlyOneByte(response: Response, counter: PayloadCounter) {
    val body = response.body ?: throw EndpointException("NO_BODY")
    val stream = body.byteStream()
    val buffer = ByteArray(16)
    var total = 0
    while (true) {
        val n = stream.read(buffer)
        if (n < 0) break
        total += n
        counter.add(n.toLong())
        if (total > 1) throw EndpointException("WRONG_BODY_SIZE")
    }
    if (total != 1) throw EndpointException("WRONG_BODY_SIZE")
}
