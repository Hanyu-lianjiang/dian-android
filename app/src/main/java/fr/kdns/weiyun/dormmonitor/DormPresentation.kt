package fr.kdns.weiyun.dormmonitor

import org.json.JSONObject
import java.util.Locale

fun dormLocation(dorm: JSONObject): String {
    val location = dorm.optJSONObject("location") ?: return ""
    return listOf("campus", "building").map { location.optString(it).trim() }.filter { it.isNotEmpty() && it != "null" }.joinToString(" · ")
}
fun dormDisplayStatus(dorm: JSONObject): String {
    val status = dorm.optString("status")
    val value = dorm.optDouble("remaining", Double.NaN)
    return if (status == "ok" && value.isFinite() && value < dorm.optDouble("lowThreshold", 30.0)) "low" else status
}
fun decimal(value: Double): String = if (value.isFinite()) String.format(Locale.CHINA, "%.2f", value) else "--"
fun trendPoints(rows: List<JSONObject>): List<TrendPoint> = normalizedTrendPoints(rows.map { TrendPoint(it.optLong("ts"), it.optDouble("remaining", Double.NaN), it.optInt("samples", 1)) })
