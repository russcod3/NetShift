package edu.cs312.netshift.measurement

import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** The endpoint broke the contract (status, redirect, encoding, body size, TLS). Never counted as packet loss. */
class EndpointException(
    val category: String,
    val httpStatus: Int? = null,
    val requestLabel: String? = null,
    cause: Throwable? = null,
) : RuntimeException(category, cause) {
    fun forRequest(label: String) = EndpointException(category, httpStatus, label, cause)
}

/** Counts every response-body byte read (probes, warm-up, download), including partial reads. */
class PayloadCounter(val ceiling: Long) {
    private val total = AtomicLong()
    val value: Long get() = total.get()
    fun add(bytes: Long): Long = total.addAndGet(bytes)
}

object EndpointUrls {
    fun probe(endpoint: String): HttpUrl = build(endpoint, 1)
    fun download(endpoint: String, bytes: Long): HttpUrl = build(endpoint, bytes)

    private fun build(endpoint: String, bytes: Long): HttpUrl = endpoint.toHttpUrl().newBuilder()
        .addQueryParameter("bytes", bytes.toString())
        .addQueryParameter("netshiftNonce", UUID.randomUUID().toString())
        .build()
}

internal fun requestFor(url: HttpUrl): Request = Request.Builder()
    .url(url)
    .header("Cache-Control", "no-cache, no-store")
    .header("Accept-Encoding", "identity")
    .get()
    .build()

/** Same pool/dispatcher/socket factory as the receiver, with per-call limits. */
internal fun OkHttpClient.withLimits(callMs: Long, connectReadMs: Long = callMs): OkHttpClient = newBuilder()
    .callTimeout(callMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)
    .connectTimeout(connectReadMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)
    .readTimeout(connectReadMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)
    .writeTimeout(connectReadMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)
    .build()

/** Status 200, no redirect, and no content encoding (identity only). */
internal fun validateResponse(response: Response) {
    if (response.code != 200) {
        throw EndpointException(
            if (response.isRedirect) "REDIRECT" else "HTTP_${response.code}",
            httpStatus = response.code,
        )
    }
    val encoding = response.header("Content-Encoding")
    if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
        throw EndpointException("COMPRESSED")
    }
}

/**
 * Runs the blocking OkHttp call on Dispatchers.IO and cancels the Call as soon as the coroutine is
 * cancelled, which closes a blocked read. [onStart] runs on the IO thread just before the request is
 * sent, so timing excludes dispatcher hand-off. DNS lookups cannot be interrupted by Call.cancel();
 * the engine therefore never waits for this function when it publishes a terminal state.
 */
internal suspend fun <T> Call.executeAndRead(onStart: () -> Unit = {}, block: (Response) -> T): T = coroutineScope {
    val call = this@executeAndRead
    val done = AtomicBoolean(false)
    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } catch (e: CancellationException) {
            if (!done.get()) call.cancel()
            throw e
        }
    }
    try {
        withContext(Dispatchers.IO) {
            onStart()
            call.execute().use(block)
        }
    } finally {
        done.set(true)
        watcher.cancel()
    }
}
