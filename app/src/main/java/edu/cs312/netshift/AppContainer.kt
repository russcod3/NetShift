package edu.cs312.netshift

import android.content.Context
import android.util.Log
import androidx.room.Room
import edu.cs312.netshift.data.HandoffRepository
import edu.cs312.netshift.data.TestRepository
import edu.cs312.netshift.data.db.NetShiftDatabase
import edu.cs312.netshift.platform.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val processStartMs = System.currentTimeMillis()

    /** Application-lifetime scope for work that must outlive any screen (recovery now; engine later). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: NetShiftDatabase = Room.databaseBuilder(
        applicationContext,
        NetShiftDatabase::class.java,
        "netshift.db",
    ).build()
    val testRepository = TestRepository(database.testResultDao())
    val handoffRepository = HandoffRepository(database.handoffEventDao())
    val connectivityObserver = ConnectivityObserver(applicationContext)

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
