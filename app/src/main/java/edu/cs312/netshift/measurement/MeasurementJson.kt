package edu.cs312.netshift.measurement

import org.json.JSONArray
import org.json.JSONObject

/** Versioned JSON text for the Room TEXT columns; uses Android's built-in org.json (no extra plugin). */
object MeasurementJson {
    fun stringList(values: Collection<String>): String = JSONArray(values.toList()).toString()

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
