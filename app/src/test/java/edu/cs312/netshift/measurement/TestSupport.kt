package edu.cs312.netshift.measurement

import edu.cs312.netshift.data.db.dao.TestResultDao
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.model.TestTransport
import edu.cs312.netshift.platform.LeaseResult
import edu.cs312.netshift.platform.NetworkLease
import edu.cs312.netshift.platform.NetworkLeaseProvider
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

object SystemNanoClock : MonotonicClock {
    override fun nowNs(): Long = System.nanoTime()
    override fun wallMs(): Long = System.currentTimeMillis()
}

fun plainClient(): OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .build()

class FakeLease(
    override val transport: TestTransport = TestTransport.WIFI,
    private val clientFactory: () -> OkHttpClient = ::plainClient,
) : NetworkLease {
    override val handle: Long? = 42L
    override val ipAddresses: List<String> = listOf("192.168.1.10")
    override val isMetered: Boolean? = false
    override val isValidated: Boolean = true
    private val _interruption = MutableStateFlow<String?>(null)
    override val interruption: StateFlow<String?> = _interruption
    @Volatile var released = false

    fun interrupt(reason: String = "NETWORK_CHANGED") { _interruption.value = reason }
    override fun newHttpClient(): OkHttpClient = clientFactory()
    override fun release() { released = true }
}

class FakeLeaseProvider(private val result: LeaseResult) : NetworkLeaseProvider {
    override suspend fun acquire(transport: TestTransport, timeoutMs: Long): LeaseResult = result
}

class FakeTestResultDao : TestResultDao {
    val rows = ConcurrentHashMap<String, TestResultEntity>()
    override suspend fun insert(result: TestResultEntity) { rows[result.id] = result }
    override suspend fun update(result: TestResultEntity) { rows[result.id] = result }
    override fun observeRecent(limit: Int): Flow<List<TestResultEntity>> =
        flowOf(rows.values.sortedByDescending { it.startedAtEpochMs })
    override fun observeById(id: String): Flow<TestResultEntity?> = flowOf(rows[id])
    override suspend fun getById(id: String): TestResultEntity? = rows[id]
    override fun observeCompleteForCompare(profileId: String, limit: Int): Flow<List<TestResultEntity>> = flowOf(emptyList())
    override suspend fun renameLocation(oldKey: String, newName: String, newKey: String): Int = 0
    override suspend fun abortRunning(now: Long, startedBefore: Long): Int = 0
    override suspend fun clear() { rows.clear() }
}

/** Serves `bytes=N` objects: 1-byte probes exactly, downloads capped at [downloadBodyCap] bytes. */
fun fakeEndpoint(downloadBodyCap: Int = 200_000, status: Int = 200) = object : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val requested = request.requestUrl?.queryParameter("bytes")?.toIntOrNull() ?: 0
        val size = if (requested <= 1) requested else minOf(requested, downloadBodyCap)
        return MockResponse().setResponseCode(status).setBody(Buffer().write(ByteArray(size)))
    }
}
