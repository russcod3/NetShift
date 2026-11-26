package edu.cs312.netshift.measurement

import edu.cs312.netshift.data.TestRepository
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.model.FailureReason
import edu.cs312.netshift.model.MeasurementConfig
import edu.cs312.netshift.model.MeasurementPhase
import edu.cs312.netshift.model.MeasurementState
import edu.cs312.netshift.model.ProbeSample
import edu.cs312.netshift.model.QualityFlag
import edu.cs312.netshift.model.StartResult
import edu.cs312.netshift.model.StopReason
import edu.cs312.netshift.model.TestStatus
import edu.cs312.netshift.model.TestTransport
import edu.cs312.netshift.platform.LeaseResult
import edu.cs312.netshift.platform.NetworkLease
import edu.cs312.netshift.platform.NetworkLeaseProvider
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient

/**
 * Runs one measurement at a time on an explicitly selected network.
 *
 * - The RUNNING row is written before any traffic; every started test ends in exactly one terminal row.
 * - A single global monotonic deadline overrides every stage. A watchdog publishes a terminal state at the
 *   deadline without waiting for blocked DNS or HTTP work; late work is ignored through the run's
 *   `finished` flag (first caller of finish() wins).
 * - Unlaunched probe slots are never failed probes; loss is only reported for a complete 20-probe phase.
 */
class MeasurementEngine(
    private val testRepository: TestRepository,
    private val leaseProvider: NetworkLeaseProvider,
    private val clock: MonotonicClock = SystemMonotonicClock(),
    private val profile: MeasurementProfile = MeasurementProfile(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val probeRunner: ProbeRunner = ProbeRunner(clock),
    private val downloadRunner: DownloadRunner = DownloadRunner(clock),
) {
    private val _state = MutableStateFlow(MeasurementState())
    val state: StateFlow<MeasurementState> = _state.asStateFlow()

    private val _lastDiagnostics = MutableStateFlow<String?>(null)

    /** Debug-only request failure details for the current run; null otherwise. */
    val lastDiagnostics: StateFlow<String?> = _lastDiagnostics.asStateFlow()

    private val gate = Mutex()
    @Volatile private var current: Run? = null
    private val unsaved = ConcurrentLinkedQueue<TestResultEntity>()

    /**
     * Call only after any mobile-data/metered confirmation. Returns Rejected (and creates no row and no
     * traffic) if a test is already running or the initial row cannot be saved.
     */
    suspend fun start(transport: TestTransport, locationName: String): StartResult {
        if (!gate.tryLock()) return StartResult.Rejected(FailureReason.ALREADY_RUNNING)
        val startNs = clock.nowNs()
        val initial = TestResultEntity(
            startedAtEpochMs = clock.wallMs(),
            locationName = locationName,
            requestedTransport = transport.key,
            endpointBaseUrl = profile.endpoint,
        )
        try {
            testRepository.insert(initial)
        } catch (e: CancellationException) {
            gate.unlock()
            throw e
        } catch (e: Exception) {
            gate.unlock()
            return StartResult.Rejected(FailureReason.SAVE_FAILED)
        }
        val run = Run(
            id = initial.id,
            transport = transport,
            initial = initial,
            startNs = startNs,
            deadlineNs = startNs + profile.totalTimeoutMs * 1_000_000L,
            counter = PayloadCounter(profile.maxPayloadBytes),
        )
        current = run
        _lastDiagnostics.value = null
        _state.value = MeasurementState(phase = MeasurementPhase.ACQUIRING, testId = run.id)
        run.track(scope.launch {
            delay(remainingMs(run))
            finish(run, interruptedStatus(run), FailureReason.DEADLINE)
        })
        run.track(scope.launch { execute(run) })
        return StartResult.Started(run.id)
    }

    /** User cancellation or app backgrounding. Keeps partial evidence; safe to call at any time. */
    fun cancel() {
        val run = current ?: return
        finish(run, TestStatus.CANCELLED, FailureReason.USER_CANCEL)
    }

    /** Re-attempts rows that could not be written (e.g. a disk error). Returns true if nothing is left unsaved. */
    suspend fun retryUnsaved(): Boolean {
        while (true) {
            val row = unsaved.poll() ?: break
            try {
                testRepository.update(row)
            } catch (e: CancellationException) {
                unsaved.add(row)
                throw e
            } catch (e: Exception) {
                unsaved.add(row)
                return false
            }
        }
        _state.update { if (it.saveError != null) it.copy(saveError = null) else it }
        return true
    }

    // ---- execution ----

    private suspend fun execute(run: Run) {
        try {
            runMeasurement(run)
        } catch (e: CancellationException) {
            throw e // whoever cancelled already claimed finish()
        } catch (e: Exception) {
            finish(run, interruptedStatus(run), FailureReason.INTERNAL_ERROR)
        }
    }

    private suspend fun runMeasurement(run: Run) {
        // 1. Acquire the selected network.
        val acquisition = leaseProvider.acquire(
            run.transport,
            minOf(profile.acquisitionTimeoutMs, remainingMs(run)),
        )
        val lease = when (acquisition) {
            is LeaseResult.Failed -> return finish(run, TestStatus.FAILED, acquisition.reason)
            is LeaseResult.Acquired -> acquisition.lease
        }
        if (!run.attachLease(lease)) return
        run.mutate {
            it.copy(
                measuredTransport = lease.transport.key,
                networkHandle = lease.handle,
                startIpAddressesJson = MeasurementJson.stringList(lease.ipAddresses),
                isMetered = lease.isMetered,
                wasValidatedAtStart = lease.isValidated,
            )
        }
        run.track(scope.launch {
            val reason = lease.interruption.filterNotNull().first()
            if (run.phase == MeasurementPhase.DOWNLOADING) {
                run.mutate { it.copy(downloadStopReason = StopReason.NETWORK_LOST) }
            }
            finish(run, interruptedStatus(run), reason)
        })
        val client = lease.newHttpClient()
        if (!run.attachClient(client)) return

        // 2. Warm-up: the untimed, excluded first request.
        setPhase(run, MeasurementPhase.WARMING_UP)
        try {
            probeRunner.warmUp(client, profile.endpoint, minOf(profile.warmupTimeoutMs, remainingMs(run)), run.counter)
        } catch (e: EndpointException) {
            recordDebugFailure("WARMING_UP", e.requestLabel ?: "warm-up", e, e.httpStatus)
            run.addFlag(QualityFlag.ENDPOINT_ERROR)
            run.addFlag("ENDPOINT_DETAIL:${e.category}")
            return finish(run, TestStatus.FAILED, reasonOr(lease, FailureReason.ENDPOINT_ERROR))
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            recordDebugFailure("WARMING_UP", "warm-up", e)
            return finish(run, TestStatus.FAILED, reasonOr(lease, FailureReason.ENDPOINT_UNREACHABLE))
        }

        // 3. Twenty timed probes.
        setPhase(run, MeasurementPhase.PROBING)
        try {
            probeRunner.runProbes(
                client = client,
                endpoint = profile.endpoint,
                slotMs = profile.probeSlotMs,
                probeTimeoutMs = profile.probeTimeoutMs,
                deadlineNs = run.deadlineNs - profile.finalizeAllowanceMs * 1_000_000L,
                counter = run.counter,
            ) { record ->
                record.diagnostic?.let { _lastDiagnostics.value = it }
                run.addRecord(record)
                update(run) { it.copy(probesAttempted = run.attempted()) }
            }
        } catch (e: EndpointException) {
            recordDebugFailure("PROBING", e.requestLabel ?: "probe", e, e.httpStatus)
            run.addFlag(QualityFlag.ENDPOINT_ERROR)
            run.addFlag("ENDPOINT_DETAIL:${e.category}")
            return finish(run, TestStatus.FAILED, reasonOr(lease, FailureReason.ENDPOINT_ERROR))
        }
        val samples = run.samples()
        if (samples.size < MeasurementConfig.PROBE_COUNT) {
            return finish(run, interruptedStatus(run), FailureReason.DEADLINE)
        }
        val metrics = MetricCalculator.calculate(samples)
        run.mutate {
            it.copy(
                latencyMedianMs = metrics.latencyMedianMs,
                jitterMs = metrics.jitterMs,
                jitterPairCount = metrics.jitterPairCount,
                probeLossPercent = metrics.probeLossPercent,
            )
        }
        if (metrics.succeeded == 0) {
            // All 20 timed out: a no-reachability result, not a zero-latency success. No download is attempted.
            return finish(run, TestStatus.FAILED, reasonOr(lease, FailureReason.ENDPOINT_UNREACHABLE))
        }
        if (metrics.succeeded < MeasurementConfig.MIN_SUCCESSFUL_PROBES ||
            metrics.jitterPairCount < MeasurementConfig.MIN_JITTER_PAIRS
        ) {
            run.addFlag(QualityFlag.SPARSE_PROBES)
        }

        // 4. Sequential capped download requests; each object stays below the observed accepted size.
        setPhase(run, MeasurementPhase.DOWNLOADING)
        val download = downloadRunner.run(
            client = client,
            endpoint = profile.endpoint,
            bytesRequested = profile.downloadBytes,
            maxRequestBytes = profile.downloadRequestBytes,
            planTimeoutMs = profile.downloadTimeoutMs,
            deadlineNs = run.deadlineNs - profile.finalizeAllowanceMs * 1_000_000L,
            counter = run.counter,
        ) { bytes ->
            run.mutate { it.copy(downloadBytes = bytes) }
            update(run) { it.copy(downloadBytes = bytes) }
        }
        download.diagnostics?.let { _lastDiagnostics.value = it }
        val mbps = MetricCalculator.downloadMbps(download.bytes, download.bodyDurationMs ?: 0.0)
        run.mutate {
            it.copy(
                downloadBytes = download.bytes,
                downloadBodyDurationMs = download.bodyDurationMs,
                downloadWallDurationMs = download.wallDurationMs,
                downloadMbps = mbps,
                downloadStopReason = download.stopReason,
            )
        }
        if ((download.bodyDurationMs ?: 0.0) < MeasurementConfig.MIN_DOWNLOAD_BODY_MS) {
            run.addFlag(QualityFlag.SHORT_DOWNLOAD)
        }
        val validStop = download.stopReason == StopReason.EOF ||
            download.stopReason == StopReason.BYTE_LIMIT ||
            download.stopReason == StopReason.TIME_LIMIT
        if (validStop && mbps != null) {
            finish(run, TestStatus.COMPLETE, null)
        } else {
            if (download.stopReason == StopReason.HTTP_ERROR) {
                run.addFlag(QualityFlag.ENDPOINT_ERROR)
                run.addFlag("ENDPOINT_DETAIL:${download.detail}")
            }
            finish(
                run,
                TestStatus.PARTIAL,
                reasonOr(
                    lease,
                    if (download.stopReason == StopReason.HTTP_ERROR) FailureReason.ENDPOINT_ERROR
                    else FailureReason.ENDPOINT_UNREACHABLE,
                ),
            )
        }
    }

    // ---- finishing ----

    /** First caller wins; every later call (late response, watchdog after completion, cancel) is ignored. */
    private fun finish(run: Run, status: String, failureReason: String?) {
        if (!run.finished.compareAndSet(false, true)) return
        scope.launch(NonCancellable) { complete(run, status, failureReason) }
    }

    private suspend fun complete(run: Run, status: String, failureReason: String?) {
        run.cancelJobs()
        run.releaseResources()
        val entity = run.finalEntity(
            status = status,
            failureReason = failureReason,
            finishedAtEpochMs = clock.wallMs(),
            durationMs = (clock.nowNs() - run.startNs) / 1_000_000L,
        )
        val phase = terminalPhase(status)
        // At the deadline the user-visible terminal state must not wait for the database.
        val publishFirst = failureReason == FailureReason.DEADLINE
        var saved: Boolean? = null
        if (!publishFirst) {
            update(run, force = true) { it.copy(phase = MeasurementPhase.SAVING) }
            saved = trySave(entity)
        }
        _state.value = MeasurementState(
            phase = phase,
            testId = run.id,
            probesAttempted = entity.probeCountAttempted,
            downloadBytes = entity.downloadBytes,
            failureReason = failureReason,
            saveError = if (saved == false) FailureReason.SAVE_FAILED else null,
        )
        gate.unlock()
        if (publishFirst && !trySave(entity)) {
            _state.update { if (it.testId == run.id) it.copy(saveError = FailureReason.SAVE_FAILED) else it }
        }
    }

    private suspend fun trySave(entity: TestResultEntity): Boolean = try {
        withTimeout(MeasurementConfig.SAVE_TIMEOUT_MS) { testRepository.update(entity) }
        true
    } catch (e: Exception) {
        unsaved.add(entity)
        false
    }

    // ---- helpers ----

    private fun remainingMs(run: Run): Long = ((run.deadlineNs - clock.nowNs()) / 1_000_000L).coerceAtLeast(0)

    private fun recordDebugFailure(phase: String, request: String, error: Throwable, httpStatus: Int? = null) {
        if (DebugDiagnostics.enabled) _lastDiagnostics.value = DebugDiagnostics.failure(phase, request, error, httpStatus)
    }

    private fun interruptedStatus(run: Run): String =
        if (run.hasProgress()) TestStatus.PARTIAL else TestStatus.FAILED

    /** If the selected network was lost while something else failed, report the network reason. */
    private fun reasonOr(lease: NetworkLease, default: String): String = lease.interruption.value ?: default

    private fun terminalPhase(status: String) = when (status) {
        TestStatus.COMPLETE -> MeasurementPhase.COMPLETE
        TestStatus.PARTIAL -> MeasurementPhase.PARTIAL
        TestStatus.CANCELLED -> MeasurementPhase.CANCELLED
        else -> MeasurementPhase.FAILED
    }

    private fun setPhase(run: Run, phase: MeasurementPhase) {
        run.phase = phase
        update(run) { it.copy(phase = phase) }
    }

    /** Updates the public state only for this run and only while it is not terminal. */
    private fun update(run: Run, force: Boolean = false, change: (MeasurementState) -> MeasurementState) {
        _state.update { if (it.testId == run.id && (force || !it.isTerminal)) change(it) else it }
    }
}

/** Mutable bookkeeping for one test; all writes stop once [finished] is set. */
private class Run(
    val id: String,
    val transport: TestTransport,
    initial: TestResultEntity,
    val startNs: Long,
    val deadlineNs: Long,
    val counter: PayloadCounter,
) {
    val finished = AtomicBoolean(false)
    @Volatile var phase = MeasurementPhase.ACQUIRING

    private val lock = Any()
    private var entity = initial
    private val records = ArrayList<ProbeRecord>()
    private val flags = LinkedHashSet<String>()
    private var lease: NetworkLease? = null
    private var client: OkHttpClient? = null
    private val jobs = CopyOnWriteArrayList<Job>()

    fun track(job: Job) {
        jobs.add(job)
        if (finished.get()) job.cancel()
    }

    fun cancelJobs() = jobs.forEach { it.cancel() }

    fun mutate(change: (TestResultEntity) -> TestResultEntity) {
        synchronized(lock) {
            if (finished.get()) return@synchronized
            entity = change(entity)
        }
    }

    fun addRecord(record: ProbeRecord) {
        synchronized(lock) {
            if (finished.get()) return@synchronized
            records.add(record)
            val attempted = records.size
            val succeeded = records.count { it.sample.rttMs != null }
            entity = entity.copy(
                probeCountAttempted = attempted,
                probeCountSucceeded = succeeded,
                probeCountFailed = attempted - succeeded,
            )
        }
    }

    fun addFlag(flag: String) {
        synchronized(lock) {
            if (finished.get()) return@synchronized
            flags.add(flag)
        }
    }

    fun attempted(): Int = synchronized(lock) { records.size }
    fun samples(): List<ProbeSample> = synchronized(lock) { records.map { it.sample } }
    fun hasProgress(): Boolean = synchronized(lock) { records.isNotEmpty() || entity.downloadBytes > 0 }

    /** Returns false (and releases it) if the run already finished, so nothing leaks. */
    fun attachLease(l: NetworkLease): Boolean {
        synchronized(lock) {
            if (!finished.get()) {
                lease = l
                return true
            }
        }
        l.release()
        return false
    }

    fun attachClient(c: OkHttpClient): Boolean {
        synchronized(lock) {
            if (!finished.get()) {
                client = c
                return true
            }
        }
        c.releaseResources()
        return false
    }

    fun releaseResources() {
        val (l, c) = synchronized(lock) { lease to client }
        c?.releaseResources()
        l?.release()
    }

    fun finalEntity(status: String, failureReason: String?, finishedAtEpochMs: Long, durationMs: Long): TestResultEntity =
        synchronized(lock) {
            val finalFlags = flags.toSet()
            val completed = status == TestStatus.COMPLETE && failureReason == null
            val verdicts = VerdictEvaluator.evaluate(
                VerdictInput(
                    completed = completed,
                    probeCountPlanned = entity.probeCountPlanned,
                    probeCountAttempted = records.size,
                    probeCountSucceeded = records.count { it.sample.rttMs != null },
                    probeCountFailed = records.count { it.sample.rttMs == null },
                    jitterPairCount = entity.jitterPairCount,
                    latencyMedianMs = entity.latencyMedianMs,
                    jitterMs = entity.jitterMs,
                    downloadBodyDurationMs = entity.downloadBodyDurationMs,
                    downloadMbps = entity.downloadMbps,
                    qualityFlags = finalFlags,
                ),
            )
            entity.copy(
                status = status,
                failureReason = failureReason,
                finishedAtEpochMs = finishedAtEpochMs,
                durationMs = durationMs,
                endIpAddressesJson = lease?.ipAddresses?.let { MeasurementJson.stringList(it) } ?: entity.endIpAddressesJson,
                probeSamplesJson = MeasurementJson.samples(records),
                qualityFlagsJson = MeasurementJson.stringList(finalFlags),
                measurementPayloadBytes = counter.value,
                verdictRulesVersion = verdicts.rulesVersion,
                activityVerdictsJson = MeasurementJson.activityVerdicts(verdicts),
            )
        }
}
