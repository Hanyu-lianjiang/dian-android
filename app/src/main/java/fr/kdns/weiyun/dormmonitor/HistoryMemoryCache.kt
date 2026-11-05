package fr.kdns.weiyun.dormmonitor

/** Session-only chart data. It is never written to device storage. */
class HistoryMemoryCache(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val ttlMs: Long = 60_000L,
    private val capacity: Int = 64
) {
    private data class Entry(val points: List<TrendPoint>, val fetchedAt: Long)
    private val entries = LinkedHashMap<Pair<String, TrendRange>, Entry>(16, .75f, true)
    fun get(dormId: String, range: TrendRange): List<TrendPoint>? = entries[dormId to range]?.points
    fun isFresh(dormId: String, range: TrendRange): Boolean = entries[dormId to range]?.let { clock() - it.fetchedAt in 0L until ttlMs } ?: false
    fun put(dormId: String, range: TrendRange, points: List<TrendPoint>) {
        entries[dormId to range] = Entry(points, clock())
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
    fun remove(dormId: String) { entries.keys.removeAll { it.first == dormId } }
    fun clear() = entries.clear()
}
