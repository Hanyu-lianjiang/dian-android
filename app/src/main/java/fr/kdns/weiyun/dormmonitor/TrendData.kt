package fr.kdns.weiyun.dormmonitor

enum class TrendRange(val label: String, val minutes: Int, val points: Int, val caption: String) {
    FIVE_MINUTES("5分钟", 60, 60, "近1小时 · 5分钟采样"),
    DAY("1天", 1440, 120, "近24小时"),
    WEEK("7天", 10080, 240, "近7天"),
    MONTH("30天", 43200, 360, "近30天")
}

data class TrendPoint(val timestamp: Long, val remaining: Double, val samples: Int = 1)
fun normalizedTrendPoints(points: List<TrendPoint>): List<TrendPoint> =
    points.filter { it.timestamp > 0 && it.remaining.isFinite() }.sortedBy { it.timestamp }.distinctBy { it.timestamp }
