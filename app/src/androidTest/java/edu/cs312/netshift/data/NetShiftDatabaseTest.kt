package edu.cs312.netshift.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.cs312.netshift.data.db.NetShiftDatabase
import edu.cs312.netshift.data.db.entity.HandoffEventEntity
import edu.cs312.netshift.data.db.entity.TestResultEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetShiftDatabaseTest {
    private lateinit var database: NetShiftDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NetShiftDatabase::class.java,
        ).build()
    }

    @After fun tearDown() = database.close()

    @Test fun testResultPreservesUnknownMetricsAndUpdatesInPlace() = runBlocking {
        val dao = database.testResultDao()
        val original = TestResultEntity(startedAtEpochMs = 1_000, requestedTransport = "WIFI")
        dao.insert(original)
        assertNull(dao.getById(original.id)!!.latencyMedianMs)
        val completed = original.copy(status = "COMPLETE", latencyMedianMs = 25.0, finishedAtEpochMs = 2_000)
        dao.update(completed)
        assertEquals(completed, dao.getById(original.id))
    }

    @Test fun pendingHandoffKeepsOldIpWhenNewIpIsUnavailable() = runBlocking {
        val dao = database.handoffEventDao()
        val pending = HandoffEventEntity(
            sessionId = "session",
            kind = "DISCONNECT",
            detectedAtEpochMs = 1_000,
            detectedElapsedMs = 500,
            fromTransport = "WIFI",
            fromPrimaryIp = "192.168.1.2",
        )
        dao.insert(pending)
        val restored = dao.getById(pending.id)!!
        assertEquals("192.168.1.2", restored.fromPrimaryIp)
        assertNull(restored.toPrimaryIp)
        assertNull(restored.observedInterruptionMs)
    }
}
