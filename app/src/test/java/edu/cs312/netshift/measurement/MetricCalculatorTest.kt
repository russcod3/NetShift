package edu.cs312.netshift.measurement

import edu.cs312.netshift.model.ProbeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricCalculatorTest {
    private fun success(index: Int, rtt: Double) = ProbeSample(index, rtt)
    private fun failure(index: Int) = ProbeSample(index, null, "TIMEOUT")

    @Test fun chronologicalJitterDoesNotBridgeFailedSlots() {
        val result = MetricCalculator.calculate(
            listOf(success(0, 10.0), success(1, 20.0), failure(2), success(3, 50.0), success(4, 30.0)),
            plannedCount = 5,
        )
        assertEquals(25.0, result.latencyMedianMs!!, 0.0001)
        assertEquals(15.0, result.jitterMs!!, 0.0001)
        assertEquals(2, result.jitterPairCount)
        assertEquals(20.0, result.probeLossPercent!!, 0.0001)
    }

    @Test fun oddMedianAndJitterUseDifferentOrdering() {
        val result = MetricCalculator.calculate(
            listOf(success(2, 20.0), success(0, 30.0), success(1, 10.0)), 3,
        )
        assertEquals(20.0, result.latencyMedianMs!!, 0.0001)
        assertEquals(15.0, result.jitterMs!!, 0.0001)
    }

    @Test fun constantRttHasZeroJitter() {
        val result = MetricCalculator.calculate(List(20) { success(it, 12.0) })
        assertEquals(0.0, result.jitterMs!!, 0.0001)
        assertEquals(0.0, result.probeLossPercent!!, 0.0001)
    }

    @Test fun threeFailedProbesOfTwentyIsFifteenPercent() {
        val result = MetricCalculator.calculate(List(20) { if (it < 3) failure(it) else success(it, 10.0) })
        assertEquals(20, result.attempted)
        assertEquals(17, result.succeeded)
        assertEquals(3, result.failed)
        assertEquals(15.0, result.probeLossPercent!!, 0.0001)
    }

    @Test fun allFailuresProduceNoLatencyOrJitter() {
        val result = MetricCalculator.calculate(List(20) { failure(it) })
        assertNull(result.latencyMedianMs)
        assertNull(result.jitterMs)
        assertEquals(100.0, result.probeLossPercent!!, 0.0001)
    }

    @Test fun incompletePhaseDoesNotInventLossForUnlaunchedSlots() {
        val result = MetricCalculator.calculate(List(7) { success(it, 10.0) })
        assertEquals(7, result.attempted)
        assertEquals(0, result.failed)
        assertNull(result.probeLossPercent)
    }

    @Test fun nonAdjacentSuccessesDoNotProduceJitter() {
        val result = MetricCalculator.calculate(listOf(success(0, 10.0), success(2, 30.0)))
        assertNull(result.jitterMs)
        assertEquals(0, result.jitterPairCount)
    }

    @Test fun emptyPhaseHasNoInventedMetrics() {
        val result = MetricCalculator.calculate(emptyList())
        assertNull(result.latencyMedianMs)
        assertNull(result.jitterMs)
        assertNull(result.probeLossPercent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateSlotIsRejected() {
        MetricCalculator.calculate(listOf(success(0, 10.0), success(0, 20.0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun slotOutsidePlannedPhaseIsRejected() {
        MetricCalculator.calculate(listOf(success(20, 10.0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteRttIsRejected() {
        success(0, Double.NaN)
    }

    @Test fun throughputUsesDecimalMegabitsAndActualBytes() {
        assertEquals(4.0, MetricCalculator.downloadMbps(1_000_000, 2_000.0)!!, 0.0001)
        assertNull(MetricCalculator.downloadMbps(0, 2_000.0))
        assertNull(MetricCalculator.downloadMbps(100, 0.0))
        assertNull(MetricCalculator.downloadMbps(100, Double.POSITIVE_INFINITY))
    }
}
