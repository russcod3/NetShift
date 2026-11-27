package edu.cs312.netshift.data

import edu.cs312.netshift.data.db.entity.TestResultEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareModelTest {
    private val minute = 60_000L

    private fun test(
        id: String,
        transport: String,
        location: String = "library",
        startedAt: Long,
        status: String = "COMPLETE",
        succeeded: Int = 20,
        pairs: Int = 19,
        bodyMs: Double? = 2_000.0,
        stop: String? = "EOF",
        mbps: Double? = 25.0,
    ) = TestResultEntity(
        id = id,
        startedAtEpochMs = startedAt,
        locationName = location,
        locationKey = location.lowercase(),
        status = status,
        requestedTransport = transport,
        measuredTransport = transport,
        probeCountAttempted = 20,
        probeCountSucceeded = succeeded,
        probeCountFailed = 20 - succeeded,
        jitterPairCount = pairs,
        latencyMedianMs = 20.0,
        jitterMs = 3.0,
        probeLossPercent = 100.0 * (20 - succeeded) / 20,
        downloadMbps = mbps,
        downloadBodyDurationMs = bodyMs,
        downloadStopReason = stop,
    )

    @Test fun pairsSameLocationWithinPreferredGap() {
        val result = CompareModel.latestPairs(
            listOf(test("w", "WIFI", startedAt = 0), test("c", "CELLULAR", startedAt = 5 * minute)),
        )
        assertEquals(1, result.size)
        assertTrue(result[0].withinPreferredGap)
        assertEquals(5 * minute, result[0].timeGapMs)
        assertEquals("w", result[0].wifi.id)
        assertEquals("c", result[0].cellular.id)
    }

    @Test fun differentLocationsAreNeverPaired() {
        val result = CompareModel.latestPairs(
            listOf(test("w", "WIFI", "library", 0), test("c", "CELLULAR", "classroom", minute)),
        )
        assertTrue(result.isEmpty())
    }

    @Test fun missingPartnerGivesNoComparison() {
        assertTrue(CompareModel.latestPairs(listOf(test("w", "WIFI", startedAt = 0))).isEmpty())
    }

    @Test fun partialShortAndSparseRunsAreExcluded() {
        assertFalse(CompareModel.isEligible(test("a", "WIFI", startedAt = 0, status = "PARTIAL")))
        assertFalse(CompareModel.isEligible(test("b", "WIFI", startedAt = 0, bodyMs = 999.0)))
        assertFalse(CompareModel.isEligible(test("c", "WIFI", startedAt = 0, succeeded = 15)))
        assertFalse(CompareModel.isEligible(test("d", "WIFI", startedAt = 0, pairs = 9)))
        assertFalse(CompareModel.isEligible(test("e", "WIFI", startedAt = 0, stop = "IO_ERROR")))
        assertFalse(CompareModel.isEligible(test("f", "WIFI", startedAt = 0, mbps = null)))
        assertTrue(CompareModel.isEligible(test("g", "WIFI", startedAt = 0, stop = "TIME_LIMIT")))
    }

    @Test fun boundaryValuesAreEligible() {
        assertTrue(CompareModel.isEligible(test("a", "WIFI", startedAt = 0, succeeded = 16, pairs = 10, bodyMs = 1_000.0)))
    }

    @Test fun prefersNewestPairWithinGapOverOlderCloserPair() {
        val result = CompareModel.latestPairs(
            listOf(
                test("w1", "WIFI", startedAt = 0), test("c1", "CELLULAR", startedAt = minute),
                test("w2", "WIFI", startedAt = 100 * minute), test("c2", "CELLULAR", startedAt = 110 * minute),
            ),
        )
        assertEquals("w2", result[0].wifi.id)
        assertEquals("c2", result[0].cellular.id)
    }

    @Test fun fallsBackToNewestOfEachWhenNothingIsClose() {
        val result = CompareModel.latestPairs(
            listOf(test("w", "WIFI", startedAt = 0), test("c", "CELLULAR", startedAt = 60 * minute)),
        )
        assertEquals(1, result.size)
        assertFalse(result[0].withinPreferredGap)
        assertEquals(60 * minute, result[0].timeGapMs)
    }

    @Test fun nullMetricStaysNullInPair() {
        val w = test("w", "WIFI", startedAt = 0)
        val c = test("c", "CELLULAR", startedAt = 0)
        val pair = LocationComparison("library", "library", w, c, 0, true)
        assertEquals(25.0, pair.downloadMbps.wifi!!, 0.0001)
        assertEquals(20.0, pair.latencyMs.cellular!!, 0.0001)
    }
}
