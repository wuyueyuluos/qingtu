package com.example.qingtu

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize

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
@androidx.compose.runtime.Composable
internal fun ProcessingHistoryPage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var records by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ProcessingHistory.all(context)) }
    var confirmClear by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFF6F7F3)).padding(horizontal = 20.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.material3.Text("处理记录", fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF26332C), modifier = androidx.compose.ui.Modifier.weight(1f))
            if (records.isNotEmpty()) androidx.compose.material3.TextButton(onClick = { confirmClear = true }) { androidx.compose.material3.Text("清空") }
        }
        if (records.isEmpty()) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.material3.Text("还没有处理记录\n处理完成的图片会显示在这里。", color = androidx.compose.ui.graphics.Color(0xFF89938B), textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(records.size) { index ->
                    val r = records[index]
                    androidx.compose.material3.Card(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(14.dp)) {
                            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.Text(r.action, color = androidx.compose.ui.graphics.Color(0xFF496B5B), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 15.sp, modifier = androidx.compose.ui.Modifier.weight(1f))
                                androidx.compose.material3.Text(ProcessingHistory.date(r.time), color = androidx.compose.ui.graphics.Color(0xFF89938B), fontSize = 11.sp)
                            }
                            androidx.compose.material3.Text(r.sourceName, color = androidx.compose.ui.graphics.Color(0xFF26332C), fontSize = 13.sp, modifier = androidx.compose.ui.Modifier.padding(top = 8.dp))
                            androidx.compose.material3.Text("输出：${r.outputName}", color = androidx.compose.ui.graphics.Color(0xFF647168), fontSize = 12.sp, modifier = androidx.compose.ui.Modifier.padding(top = 3.dp))
                            androidx.compose.material3.Text("${ProcessingHistory.size(r.sourceBytes)} → ${ProcessingHistory.size(r.outputBytes)}" + if (r.details.isNotBlank()) " · ${r.details}" else "", color = androidx.compose.ui.graphics.Color(0xFF89938B), fontSize = 11.sp, modifier = androidx.compose.ui.Modifier.padding(top = 7.dp))
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { androidx.compose.material3.Text("清空处理记录？") },
        text = { androidx.compose.material3.Text("只会清除轻图中的记录，不会删除已保存的图片。") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { ProcessingHistory.clear(context); records = emptyList(); confirmClear = false }) { androidx.compose.material3.Text("清空") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { androidx.compose.material3.Text("取消") } }
    )
}