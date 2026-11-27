package edu.cs312.netshift

import android.content.Context
import androidx.room.Room
import edu.cs312.netshift.data.HandoffRepository
import edu.cs312.netshift.data.TestRepository
import edu.cs312.netshift.data.db.NetShiftDatabase
import edu.cs312.netshift.platform.ConnectivityObserver

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    val database: NetShiftDatabase = Room.databaseBuilder(
        applicationContext,
        NetShiftDatabase::class.java,
        "netshift.db",
    ).build()
    val testRepository = TestRepository(database.testResultDao())
    val handoffRepository = HandoffRepository(database.handoffEventDao())
    val connectivityObserver = ConnectivityObserver(applicationContext)
}
