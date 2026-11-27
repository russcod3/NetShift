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
class RepositoryRecoveryTest {
    private lateinit var database: NetShiftDatabase
    private lateinit var tests: TestRepository
    private lateinit var handoffs: HandoffRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NetShiftDatabase::class.java,
        ).build()
        tests = TestRepository(database.testResultDao())
        handoffs = HandoffRepository(database.handoffEventDao())
    }

    @After fun tearDown() = database.close()

    @Test fun recoveryAbortsOnlyRunningRowsFromEarlierProcesses() = runBlocking {
        val old = TestResultEntity(startedAtEpochMs = 1_000, requestedTransport = "WIFI")
        val current = TestResultEntity(startedAtEpochMs = 9_000, requestedTransport = "WIFI")
        val done = TestResultEntity(startedAtEpochMs = 500, requestedTransport = "WIFI", status = "COMPLETE")
        listOf(old, current, done).forEach { tests.insert(it) }

        assertEquals(1, tests.recoverInterrupted(startedBefore = 5_000, now = 6_000))

        assertEquals("ABORTED", tests.getById(old.id)!!.status)
        assertEquals("PROCESS_ENDED", tests.getById(old.id)!!.failureReason)
        assertEquals(6_000L, tests.getById(old.id)!!.finishedAtEpochMs)
        assertEquals("RUNNING", tests.getById(current.id)!!.status)
        assertEquals("COMPLETE", tests.getById(done.id)!!.status)
    }

    @Test fun renameUpdatesNameAndKeyForAllTestsOfThatLocation() = runBlocking {
        val a = TestResultEntity(startedAtEpochMs = 1, requestedTransport = "WIFI", locationName = "Lib  rary")
        val b = TestResultEntity(startedAtEpochMs = 2, requestedTransport = "CELLULAR", locationName = "lib rary")
        val other = TestResultEntity(startedAtEpochMs = 3, requestedTransport = "WIFI", locationName = "Home")
        listOf(a, b, other).forEach { tests.insert(it) }
        val oldKey = tests.getById(a.id)!!.locationKey
        assertEquals("lib rary", oldKey)

        assertEquals(2, tests.renameLocation(oldKey, "  Main Library "))

        assertEquals("Main Library", tests.getById(a.id)!!.locationName)
        assertEquals("main library", tests.getById(b.id)!!.locationKey)
        assertEquals("home", tests.getById(other.id)!!.locationKey)
    }

    @Test fun pendingHandoffsFromEndedSessionsAreClosedWithoutInventingRecovery() = runBlocking {
        val stale = HandoffEventEntity(
            sessionId = "old", kind = "DISCONNECT", detectedAtEpochMs = 1_000, detectedElapsedMs = 10, fromTransport = "WIFI",
        )
        handoffs.insert(stale)

        assertEquals(1, handoffs.recoverInterrupted(startedBefore = 5_000, now = 6_000))

        val row = database.handoffEventDao().getById(stale.id)!!
        assertEquals("SESSION_ENDED", row.status)
        assertEquals(6_000L, row.completedAtEpochMs)
        assertNull(row.toNetworkHandle)
        assertNull(row.observedInterruptionMs)
    }
}
