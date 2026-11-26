package edu.cs312.netshift.measurement

import org.json.JSONArray
import org.json.JSONObject

/** Versioned JSON text for the Room TEXT columns; uses Android's built-in org.json (no extra plugin). */
object MeasurementJson {
    fun stringList(values: Collection<String>): String = JSONArray(values.toList()).toString()

    fun activityVerdicts(verdicts: VerdictSet): String {
        val activities = JSONArray()
        verdicts.activities.forEach { verdict ->
            activities.put(
                JSONObject()
                    .put("activity", verdict.activity.key)
                    .put("status", verdict.level.name)
                    .put("limitingMetric", verdict.limitingMetric?.name ?: JSONObject.NULL)
                    .put("reason", verdict.reason)
                    .put("summary", verdict.summary),
            )
        }
        return JSONObject()
            .put("version", verdicts.rulesVersion)
            .put("activities", activities)
            .toString()
    }

    /** Reads the versioned typed verdict payload for the UI; malformed or unknown versions are unavailable. */
    fun parseActivityVerdicts(json: String): VerdictSet? {
        return try {
            val root = JSONObject(json)
            val version = root.getInt("version")
            if (version != VerdictRules.VERSION) return null
            val values = root.getJSONArray("activities")
            val verdicts = (0 until values.length()).map { index ->
                val item = values.getJSONObject(index)
                val activity = ActivityType.values().firstOrNull { it.key == item.getString("activity") }
                    ?: return null
                val level = VerdictLevel.values().firstOrNull { it.name == item.getString("status") }
                    ?: return null
                val metricName = item.optString("limitingMetric", "")
                val metric = if (metricName.isBlank() || metricName == "null") null
                else LimitingMetric.values().firstOrNull { it.name == metricName } ?: return null
                ActivityVerdict(
                    activity = activity,
                    level = level,
                    limitingMetric = metric,
                    reason = item.getString("reason"),
                    summary = item.getString("summary"),
                )
            }
            if (verdicts.map { it.activity }.toSet().size != ActivityType.values().size) return null
            VerdictSet(version, verdicts)
        } catch (_: Exception) {
            null
        }
    }

    fun samples(records: List<ProbeRecord>): String {
        val array = JSONArray()
        records.forEach { r ->
            array.put(
                JSONObject()
                    .put("index", r.sample.index)
                    .put("startMs", r.startOffsetMs)
                    .put("rttMs", r.sample.rttMs ?: JSONObject.NULL)
                    .put("ok", r.sample.rttMs != null)
                    .put("failure", r.sample.failureReason ?: JSONObject.NULL)
                    .put("httpStatus", r.httpStatus ?: JSONObject.NULL),
            )
        }
        return JSONObject().put("version", 1).put("samples", array).toString()
    }
}
