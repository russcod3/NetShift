package edu.cs312.netshift.platform

import edu.cs312.netshift.model.TestTransport
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

/**
 * A held, in-memory claim on one selected Network for the duration of one test. Never persisted or
 * restored from a database handle. The engine always calls [release].
 */
interface NetworkLease {
    val handle: Long?
    val transport: TestTransport
    /** Assigned addresses captured at acquisition (canonical sorted list). */
    val ipAddresses: List<String>
    val isMetered: Boolean?
    val isValidated: Boolean

    /**
     * Null while the selected network is intact. Set once to NETWORK_CHANGED if it is lost, replaced,
     * changes transport, or changes its assigned addresses. A default-route change alone does not set it.
     */
    val interruption: StateFlow<String?>

    /** Client whose DNS and sockets are bound to this network. Release with BoundHttpClientFactory's helper. */
    fun newHttpClient(): OkHttpClient

    fun release()
}

sealed interface LeaseResult {
    data class Acquired(val lease: NetworkLease) : LeaseResult
    /** [reason] is one of the FailureReason constants. */
    data class Failed(val reason: String) : LeaseResult
}

interface NetworkLeaseProvider {
    suspend fun acquire(transport: TestTransport, timeoutMs: Long): LeaseResult
}
