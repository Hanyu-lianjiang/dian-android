package fr.kdns.weiyun.dormmonitor

import org.junit.Assert.*
import org.junit.Test

class TrendDataTest {
    @Test fun fiveMinuteViewContainsMultipleFiveMinuteSamples() {
        assertEquals(60, TrendRange.FIVE_MINUTES.minutes)
        val now = 1_800_000_000_000L
        val points = (0..11).map { TrendPoint(now - it * 300_000L, 100.0 - it) }
        assertEquals(12, normalizedTrendPoints(points).size)
    }
    @Test fun singlePointAndZeroAreNotDiscarded() {
        assertEquals(listOf(TrendPoint(1000L, 0.0)), normalizedTrendPoints(listOf(TrendPoint(1000L, 0.0))))
    }
    @Test fun invalidPointsAreRemovedAndTimestampsSorted() {
        val points = listOf(TrendPoint(3000L, 5.0), TrendPoint(1000L, 6.0), TrendPoint(0L, 2.0), TrendPoint(2000L, Double.NaN))
        assertEquals(listOf(1000L, 3000L), normalizedTrendPoints(points).map { it.timestamp })
    }
}
