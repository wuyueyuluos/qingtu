package com.example.qingtu

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ProcessingRecord(
    val id: Long, val time: Long, val action: String,
    val sourceName: String, val outputName: String,
    val sourceBytes: Long, val outputBytes: Long, val details: String
)

internal object ProcessingHistory {
    private const val PREFS = "processing_history"
    private const val KEY = "records"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<ProcessingRecord> {
        val array = JSONArray(prefs(context).getString(KEY, "[]"))
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            ProcessingRecord(o.optLong("id"), o.optLong("time"), o.optString("action"),
                o.optString("source"), o.optString("output"), o.optLong("before"),
                o.optLong("after"), o.optString("details"))
        }.sortedByDescending { it.time }
    }

    fun add(context: Context, action: String, sourceUri: Uri?, outputUri: Uri,
            sourceBytes: Long, outputBytes: Long, details: String = "") {
        val sourceName = displayName(context, sourceUri) ?: "图片"
        val outputName = displayName(context, outputUri) ?: "处理后的图片"
        val records = all(context).toMutableList()
        records.add(0, ProcessingRecord(System.currentTimeMillis(), System.currentTimeMillis(),
            action, sourceName, outputName, sourceBytes, outputBytes, details))
        val array = JSONArray()
        records.take(300).forEach { r ->
            array.put(JSONObject().put("id", r.id).put("time", r.time).put("action", r.action)
                .put("source", r.sourceName).put("output", r.outputName)
                .put("before", r.sourceBytes).put("after", r.outputBytes).put("details", r.details))
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    fun clear(context: Context) { prefs(context).edit().remove(KEY).apply() }

    private fun displayName(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    fun date(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        return if (kb < 1024) String.format(Locale.getDefault(), "%.1f KB", kb)
        else String.format(Locale.getDefault(), "%.2f MB", kb / 1024.0)
    }
}