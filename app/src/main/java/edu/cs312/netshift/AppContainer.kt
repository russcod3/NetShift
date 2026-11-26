package edu.cs312.netshift

import android.content.Context
import android.util.Log
import androidx.room.Room
import edu.cs312.netshift.data.HandoffRepository
import edu.cs312.netshift.data.TestRepository
import edu.cs312.netshift.data.db.NetShiftDatabase
import edu.cs312.netshift.measurement.MeasurementEngine
import edu.cs312.netshift.measurement.SystemMonotonicClock
import edu.cs312.netshift.platform.AndroidNetworkLeaseProvider
import edu.cs312.netshift.platform.ConnectivityObserver
import edu.cs312.netshift.platform.NetworkLeaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val processStartMs = System.currentTimeMillis()

    /** Application-lifetime scope for work that must outlive any screen (recovery, the engine's tests). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: NetShiftDatabase = Room.databaseBuilder(
        applicationContext,
        NetShiftDatabase::class.java,
        "netshift.db",
    ).build()
    val testRepository = TestRepository(database.testResultDao())
    val handoffRepository = HandoffRepository(database.handoffEventDao())
    val connectivityObserver = ConnectivityObserver(applicationContext)
    val networkLeaseProvider: NetworkLeaseProvider = AndroidNetworkLeaseProvider(applicationContext)

    /** One engine for the whole app: a test survives rotation and screen navigation. */
    val measurementEngine = MeasurementEngine(
        testRepository = testRepository,
        leaseProvider = networkLeaseProvider,
        clock = SystemMonotonicClock(),
        scope = appScope,
    )

    init {
        // Rows left RUNNING/PENDING by an earlier process can never complete; mark them, never resume traffic.
        appScope.launch {
            try {
                testRepository.recoverInterrupted(startedBefore = processStartMs)
                handoffRepository.recoverInterrupted(startedBefore = processStartMs)
            } catch (e: Exception) {
                Log.w("NetShift", "Startup recovery failed", e)
            }
        }
    }
}
