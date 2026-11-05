package fr.kdns.weiyun.dormmonitor

import org.junit.Assert.*
import org.junit.Test

class HistoryMemoryCacheTest {
    private val points = listOf(TrendPoint(1_800_000_000_000L, 12.0))

    @Test fun expiredDataRemainsAvailableWhileItIsRevalidated() {
        var now = 1000L
        val cache = HistoryMemoryCache(clock = { now })
        cache.put("a", TrendRange.DAY, points)
        assertTrue(cache.isFresh("a", TrendRange.DAY))
        now += 60_000
        assertFalse(cache.isFresh("a", TrendRange.DAY))
        assertSame(points, cache.get("a", TrendRange.DAY))
    }

    @Test fun roomsAndRangesDoNotReuseEachOthersHistory() {
        val cache = HistoryMemoryCache()
        cache.put("a", TrendRange.DAY, points)
        assertNull(cache.get("b", TrendRange.DAY))
        assertNull(cache.get("a", TrendRange.MONTH))
        cache.remove("a")
        assertNull(cache.get("a", TrendRange.DAY))
    }

    @Test fun clearOnAccountChangeDiscardsCachedData() {
        val cache = HistoryMemoryCache()
        cache.put("a", TrendRange.DAY, points)
        cache.clear()
        assertNull(cache.get("a", TrendRange.DAY))
        assertFalse(cache.isFresh("a", TrendRange.DAY))
    }

    @Test fun boundedCacheKeepsRecentlyUsedHistory() {
        val cache = HistoryMemoryCache(capacity = 2)
        cache.put("a", TrendRange.DAY, points)
        cache.put("b", TrendRange.DAY, points)
        cache.get("a", TrendRange.DAY)
        cache.put("c", TrendRange.DAY, points)
        assertNotNull(cache.get("a", TrendRange.DAY))
        assertNull(cache.get("b", TrendRange.DAY))
    }
}
