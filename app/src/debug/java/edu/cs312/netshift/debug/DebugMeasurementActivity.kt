package edu.cs312.netshift.debug

// DEBUG-ONLY (src/debug). Temporary harness for the real-device checks before the Test screen (step c) exists.
// Nothing in main/ references this file: delete app/src/debug/ and the build is back to normal.

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.cs312.netshift.AppContainer
import edu.cs312.netshift.NetShiftApplication
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.measurement.EndpointUrls
import edu.cs312.netshift.measurement.releaseResources
import edu.cs312.netshift.model.MeasurementConfig
import edu.cs312.netshift.model.MeasurementState
import edu.cs312.netshift.model.NetworkSnapshot
import edu.cs312.netshift.model.StartResult
import edu.cs312.netshift.model.TestTransport
import edu.cs312.netshift.platform.LeaseResult
import edu.cs312.netshift.ui.theme.NetShiftTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class DebugMeasurementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NetShiftApplication).container
        setContent {
            NetShiftTheme {
                val vm: DebugViewModel = viewModel(
                    factory = viewModelFactory { initializer { DebugViewModel(container) } },
                )
                DebugScreen(vm)
            }
        }
    }
}

class DebugViewModel(private val container: AppContainer) : ViewModel() {
    private val engine = container.measurementEngine
    val engineState: StateFlow<MeasurementState> = engine.state
    val defaultNetwork: StateFlow<NetworkSnapshot> = container.connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetworkSnapshot())

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()
    private val wallTimesMs = CopyOnWriteArrayList<Long>()

    private fun add(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        _log.update { (listOf("$stamp $line") + it).take(200) }
    }

    fun clearLog() { _log.value = emptyList() }

    /** Oldest-first text for pasting into the chat. */
    fun report(): String = _log.value.reversed().joinToString("\n")

    fun note(line: String) = add(line)

    // Check 1: endpoint contract on the selected network (about 1 KB of traffic).
    fun contractCheck(transport: TestTransport) {
        viewModelScope.launch(Dispatchers.IO) {
            val t0 = SystemClock.elapsedRealtime()
            when (val r = container.networkLeaseProvider.acquire(transport, MeasurementConfig.ACQUISITION_TIMEOUT_MS)) {
                is LeaseResult.Failed ->
                    add("CONTRACT ${transport.displayName}: no network (${r.reason}) after ${SystemClock.elapsedRealtime() - t0} ms")
                is LeaseResult.Acquired -> {
                    val client = r.lease.newHttpClient()
                    try {
                        listOf(1L, 1024L).forEach { bytes ->
                            add("CONTRACT ${transport.displayName}: " + checkOnce(client, bytes))
                        }
                    } catch (e: Exception) {
                        add("CONTRACT ${transport.displayName}: FAILED ${e.javaClass.simpleName}: ${e.message}")
                    } finally {
                        client.releaseResources()
                        r.lease.release()
                    }
                }
            }
        }
    }

    private fun checkOnce(client: OkHttpClient, bytes: Long): String {
        // Same headers as the real probes/download (see HttpSupport.requestFor).
        val request = Request.Builder()
            .url(EndpointUrls.download(MeasurementConfig.ENDPOINT, bytes))
            .header("Cache-Control", "no-cache, no-store")
            .header("Accept-Encoding", "identity")
            .get()
            .build()
        val t0 = SystemClock.elapsedRealtime()
        client.newBuilder().callTimeout(5, TimeUnit.SECONDS).build().newCall(request).execute().use { resp ->
            val stream = resp.body?.byteStream()
            val buffer = ByteArray(4096)
            var total = 0L
            while (stream != null) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total > bytes + 1024) break
            }
            val ms = SystemClock.elapsedRealtime() - t0
            val encoding = resp.header("Content-Encoding")
            val ok = resp.code == 200 && !resp.isRedirect && total == bytes &&
                (encoding == null || encoding.equals("identity", ignoreCase = true))
            return "${if (ok) "PASS" else "FAIL"} bytes=$bytes status=${resp.code} read=$total " +
                "enc=${encoding ?: "none"} len=${resp.header("Content-Length") ?: "n/a"} " +
                "cache=${resp.header("cf-cache-status") ?: resp.header("Cache-Control") ?: "n/a"} " +
                "proto=${resp.protocol} tls=${resp.handshake?.tlsVersion?.javaName ?: "n/a"} ${ms}ms (nonce sent)"
        }
    }

    // Checks 3 and 4: how long requestNetwork takes and what it returns. No HTTP traffic.
    fun acquireOnly(transport: TestTransport) {
        viewModelScope.launch {
            val t0 = SystemClock.elapsedRealtime()
            val net = defaultNetwork.value
            when (val r = container.networkLeaseProvider.acquire(transport, MeasurementConfig.ACQUISITION_TIMEOUT_MS)) {
                is LeaseResult.Failed ->
                    add("ACQUIRE ${transport.displayName}: FAILED ${r.reason} after ${SystemClock.elapsedRealtime() - t0} ms")
                is LeaseResult.Acquired -> {
                    val l = r.lease
                    add(
                        "ACQUIRE ${transport.displayName}: OK ${SystemClock.elapsedRealtime() - t0} ms " +
                            "handle=${l.handle} ips=${l.ipAddresses} metered=${l.isMetered} validated=${l.isValidated} " +
                            "| default=${net.transport} handle=${net.networkHandle}",
                    )
                    l.release()
                }
            }
        }
    }

    // Checks 2, 3, 5: a real test. Mobile data: about 18 MB plus overhead.
    fun runFullTest(transport: TestTransport, location: String) {
        viewModelScope.launch {
            val net = defaultNetwork.value
            val t0 = SystemClock.elapsedRealtime()
            when (val s = engine.start(transport, location)) {
                is StartResult.Rejected -> add("TEST ${transport.displayName}: rejected (${s.reason})")
                is StartResult.Started -> {
                    add("TEST ${transport.displayName}: started, default=${net.transport} handle=${net.networkHandle}")
                    val terminal = withTimeoutOrNull(45_000) {
                        engine.state.first { it.testId == s.testId && it.isTerminal }
                    }
                    val wall = SystemClock.elapsedRealtime() - t0
                    if (terminal == null) {
                        add("TEST ${transport.displayName}: NO TERMINAL STATE within 45 s (bug), wall=$wall ms")
                        return@launch
                    }
                    wallTimesMs += wall
                    val row = waitForSavedRow(s.testId)
                    add(summary(transport, terminal, row, wall))
                }
            }
        }
    }

    fun cancelTest() = engine.cancel()

    // Check 5: tap-to-terminal times across the runs in this session.
    fun timingSummary() {
        val sorted = wallTimesMs.sorted()
        if (sorted.isEmpty()) { add("TIMING: no completed runs yet"); return }
        val p95 = sorted[ceil(0.95 * sorted.size).toInt().coerceAtLeast(1) - 1]
        add("TIMING: n=${sorted.size} max=${sorted.last()} ms p95=$p95 ms all=$sorted (target <= 30000)")
    }

    private suspend fun waitForSavedRow(id: String): TestResultEntity? {
        repeat(30) {
            val row = container.testRepository.getById(id)
            if (row != null && row.status != "RUNNING") return row
            delay(100)
        }
        return container.testRepository.getById(id)
    }

    private fun summary(t: TestTransport, state: MeasurementState, row: TestResultEntity?, wallMs: Long): String {
        if (row == null) return "TEST ${t.displayName}: ${state.phase} but row missing, wall=$wallMs ms, saveError=${state.saveError}"
        return "TEST ${t.displayName}: ${row.status} reason=${row.failureReason} wall=$wallMs ms stored=${row.durationMs} ms " +
            "measured=${row.measuredTransport} handle=${row.networkHandle} ips=${row.startIpAddressesJson} " +
            "metered=${row.isMetered} | probes=${row.probeCountAttempted}/ok ${row.probeCountSucceeded} " +
            "loss=${f(row.probeLossPercent)}% lat=${f(row.latencyMedianMs)} ms jitter=${f(row.jitterMs)} ms pairs=${row.jitterPairCount} | " +
            "dl=${row.downloadBytes} B in ${f(row.downloadBodyDurationMs)} ms = ${f(row.downloadMbps, 2)} Mbps stop=${row.downloadStopReason} | " +
            "payload=${row.measurementPayloadBytes} B flags=${row.qualityFlagsJson} saveError=${state.saveError}"
    }

    private fun f(v: Double?, digits: Int = 1): String =
        v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "n/a"
}

private fun dataSaverStatus(context: Context): String {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "unavailable"
    return when (cm.restrictBackgroundStatus) {
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "Data Saver OFF"
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "Data Saver ON, NetShift allowed"
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "Data Saver ON, NetShift restricted"
        else -> "unknown"
    }
}

@Composable
private fun DebugScreen(vm: DebugViewModel) {
    val context = LocalContext.current
    val log by vm.log.collectAsStateWithLifecycle()
    val engine by vm.engineState.collectAsStateWithLifecycle()
    val network by vm.defaultNetwork.collectAsStateWithLifecycle()
    var mobileConfirmed by rememberSaveable { mutableStateOf(false) }
    var location by rememberSaveable { mutableStateOf("Debug") }
    var dataSaver by remember { mutableStateOf(dataSaverStatus(context)) }
    val busy = engine.isRunning

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(
                "DEBUG-ONLY SCREEN. Temporary harness for real-device checks; not part of the app UI. " +
                    "Remove by deleting app/src/debug/.",
                Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Text(
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}",
            fontSize = 12.sp,
        )
        Text(
            "Default network now: ${network.transport} handle=${network.networkHandle} ips=${network.ipAddresses} " +
                "validated=${network.isValidated} metered=${network.isMetered}",
            fontSize = 12.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$dataSaver", fontSize = 12.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { dataSaver = dataSaverStatus(context); vm.note("DATA SAVER: $dataSaver") }) {
                Text("Check 6: refresh")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = mobileConfirmed, onCheckedChange = { mobileConfirmed = it })
            Text("I accept up to about 20 MB of mobile data per mobile test (contract check is about 1 KB).", fontSize = 12.sp)
        }
        OutlinedTextField(
            value = location,
            onValueChange = { location = it.take(60) },
            label = { Text("Location name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TransportRow(
            label = "Wi-Fi",
            enabled = !busy,
            onContract = { vm.contractCheck(TestTransport.WIFI) },
            onAcquire = { vm.acquireOnly(TestTransport.WIFI) },
            onTest = { vm.runFullTest(TestTransport.WIFI, location) },
        )
        TransportRow(
            label = "Mobile data (needs the checkbox)",
            enabled = !busy && mobileConfirmed,
            onContract = { vm.contractCheck(TestTransport.CELLULAR) },
            onAcquire = { vm.acquireOnly(TestTransport.CELLULAR) },
            onTest = { vm.runFullTest(TestTransport.CELLULAR, location) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::cancelTest, enabled = busy, modifier = Modifier.weight(1f), contentPadding = TIGHT) { Text("Cancel test") }
            OutlinedButton(onClick = vm::timingSummary, modifier = Modifier.weight(1f), contentPadding = TIGHT) { Text("Check 5: timing") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("NetShift debug log", vm.report()))
                },
                modifier = Modifier.weight(1f),
                contentPadding = TIGHT,
            ) { Text("Copy log") }
            OutlinedButton(onClick = vm::clearLog, modifier = Modifier.weight(1f), contentPadding = TIGHT) { Text("Clear log") }
        }
        Text(
            "Engine: ${engine.phase} probes=${engine.probesAttempted}/${engine.probesPlanned} bytes=${engine.downloadBytes} " +
                "reason=${engine.failureReason} saveError=${engine.saveError}",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        log.forEach { Text(it, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
    }
}

private val TIGHT = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

@Composable
private fun TransportRow(label: String, enabled: Boolean, onContract: () -> Unit, onAcquire: () -> Unit, onTest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onContract, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = TIGHT) { Text("Contract", fontSize = 12.sp) }
            OutlinedButton(onClick = onAcquire, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = TIGHT) { Text("Acquire", fontSize = 12.sp) }
            Button(onClick = onTest, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = TIGHT) { Text("Full test", fontSize = 12.sp) }
        }
    }
}
