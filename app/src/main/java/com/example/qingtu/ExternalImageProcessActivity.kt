package com.example.qingtu

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private data class ExternalImageSource(
    val uri: Uri,
    val bitmap: Bitmap,
    val bytes: Long,
    val mime: String,
    val name: String
)

class ExternalImageProcessActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = linkedSetOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> {
                val streams = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                streams?.let { uris.addAll(it) }
            }
            Intent.ACTION_SEND -> {
                val stream = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                stream?.let { uris.add(it) }
            }
        }
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let { uris.add(it) }
            }
        }
        intent.data?.let { uris.add(it) }

        if (uris.isEmpty()) {
            finish()
            return
        }

        uris.forEach { uri ->
            try {
                if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (_: Exception) { }
        }

        setContent {
            QingTuTheme(darkTheme = false) {
                ExternalImageProcessDialog(uris = uris.toList(), onClose = { finish() })
            }
        }
    }
}

@Composable
private fun ExternalImageProcessDialog(uris: List<Uri>, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sources by remember(uris) { mutableStateOf<List<ExternalImageSource>>(emptyList()) }
    var mode by remember(uris) { mutableStateOf("压缩") }
    var quality by remember(uris) { mutableFloatStateOf(80f) }
    var targetFormat by remember(uris) { mutableStateOf("JPG") }
    var scale by remember(uris) { mutableFloatStateOf(80f) }
    var busy by remember(uris) { mutableStateOf(false) }
    var message by remember(uris) { mutableStateOf("正在载入图片…") }
    var progress by remember(uris) { mutableIntStateOf(0) }

    LaunchedEffect(uris) {
        val loaded = withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                try {
                    val image = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: return@mapNotNull null
                    val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                        it.length
                    } ?: 0L
                    ExternalImageSource(
                        uri = uri,
                        bitmap = image,
                        bytes = size,
                        mime = context.contentResolver.getType(uri) ?: "image/jpeg",
                        name = getImageDisplayName(context, uri)
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
        sources = loaded
        message = when {
            loaded.isEmpty() -> "图片读取失败，可能没有访问权限或格式不受支持。"
            loaded.size == uris.size -> "原图不会被修改，处理结果将另存为新文件。"
            else -> "成功读取 ${loaded.size}/${uris.size} 张图片，其余图片无法读取。"
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("轻图 · 图片处理", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (uris.size > 1) "已选择 ${uris.size} 张图片" else sources.firstOrNull()?.name ?: "正在读取图片…",
                        color = Muted, fontSize = 12.sp, maxLines = 1
                    )
                }
                TextButton(onClick = onClose, enabled = !busy) { Text("关闭") }
            }

            val first = sources.firstOrNull()
            if (first != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        first.bitmap.asImageBitmap(), "待处理图片",
                        Modifier.size(width = 92.dp, height = 92.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (sources.size > 1) "共 ${sources.size} 张图片" else first.name,
                            color = Ink, fontSize = 14.sp, maxLines = 1
                        )
                        Text("${first.bitmap.width} × ${first.bitmap.height} px", color = Muted, fontSize = 13.sp)
                        Text(
                            if (sources.size > 1) "将对全部已载入图片应用相同设置" else formatBytes(first.bytes),
                            color = Muted, fontSize = 11.sp
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("压缩", "格式转换", "调整尺寸").forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = { mode = item },
                        label = { Text(item, fontSize = 12.sp) },
                        enabled = !busy && first != null
                    )
                }
            }

            when (mode) {
                "压缩" -> {
                    Text("压缩质量：${quality.toInt()}%", color = Ink, fontSize = 14.sp)
                    Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..100f,
                        enabled = first != null && !busy)
                    Text("保留各图片原有格式。", color = Muted, fontSize = 11.sp)
                }
                "格式转换" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("JPG", "PNG", "WebP").forEach { item ->
                            FilterChip(selected = targetFormat == item, onClick = { targetFormat = item },
                                label = { Text(item) }, enabled = !busy)
                        }
                    }
                    if (targetFormat != "PNG") {
                        Text("转换质量：${quality.toInt()}%", color = Ink, fontSize = 14.sp)
                        Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..100f,
                            enabled = first != null && !busy)
                    } else Text("PNG 为无损格式。", color = Muted, fontSize = 11.sp)
                }
                else -> {
                    Text("缩放比例：${scale.toInt()}%", color = Ink, fontSize = 14.sp)
                    Slider(value = scale, onValueChange = { scale = it }, valueRange = 10f..200f,
                        enabled = first != null && !busy)
                    Text(
                        "示例输出尺寸：${((first?.bitmap?.width ?: 0) * scale / 100f).toInt()} × ${((first?.bitmap?.height ?: 0) * scale / 100f).toInt()} px",
                        color = Muted, fontSize = 11.sp
                    )
                }
            }

            if (busy) {
                LinearProgressIndicator(
                    progress = if (sources.isEmpty()) 0f else progress.toFloat() / sources.size,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(message, color = Muted, fontSize = 12.sp)
            Button(
                onClick = {
                    if (sources.isEmpty() || busy) return@Button
                    busy = true
                    progress = 0
                    message = "正在处理…"
                    val selectedMode = mode
                    val selectedQuality = quality.toInt()
                    val selectedFormat = targetFormat
                    val selectedScale = scale
                    val batch = sources
                    scope.launch {
                        var succeeded = 0
                        val failures = mutableListOf<String>()
                        batch.forEachIndexed { index, source ->
                            message = "正在处理第 ${index + 1}/${batch.size} 张…"
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    val sourceFormat = runCatching {
                                        imageFormatFromMime(source.mime, source.name)
                                    }.getOrDefault(
                                        EncodedImageFormat(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
                                    )
                                    val encoded = when (selectedMode) {
                                        "格式转换" -> when (selectedFormat) {
                                            "PNG" -> EncodedImageFormat(Bitmap.CompressFormat.PNG, "png", "image/png")
                                            "WebP" -> EncodedImageFormat(Bitmap.CompressFormat.WEBP, "webp", "image/webp")
                                            else -> EncodedImageFormat(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
                                        }
                                        else -> sourceFormat
                                    }
                                    var outputBitmap: Bitmap = if (selectedMode == "调整尺寸") {
                                        Bitmap.createScaledBitmap(
                                            source.bitmap,
                                            (source.bitmap.width * selectedScale / 100f).toInt().coerceAtLeast(1),
                                            (source.bitmap.height * selectedScale / 100f).toInt().coerceAtLeast(1),
                                            true
                                        )
                                    } else source.bitmap

                                    if (encoded.format == Bitmap.CompressFormat.JPEG && outputBitmap.hasAlpha()) {
                                        val flattened = Bitmap.createBitmap(
                                            outputBitmap.width, outputBitmap.height, Bitmap.Config.ARGB_8888
                                        )
                                        Canvas(flattened).apply {
                                            drawColor(AndroidColor.WHITE)
                                            drawBitmap(outputBitmap, 0f, 0f, null)
                                        }
                                        if (outputBitmap !== source.bitmap) outputBitmap.recycle()
                                        outputBitmap = flattened
                                    }

                                    val buffer = ByteArrayOutputStream()
                                    val q = if (encoded.format == Bitmap.CompressFormat.PNG) 100 else selectedQuality
                                    if (!outputBitmap.compress(encoded.format, q, buffer)) error("图片编码失败")
                                    if (outputBitmap !== source.bitmap) outputBitmap.recycle()
                                    val bytes = buffer.toByteArray()
                                    val outputName = outputImageName(context, source.uri, encoded.extension)
                                    val values = ContentValues().apply {
                                        put(MediaStore.Images.Media.DISPLAY_NAME, outputName)
                                        put(MediaStore.Images.Media.MIME_TYPE, encoded.mime)
                                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/轻图")
                                    }
                                    val resolver = context.contentResolver
                                    val output = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                        ?: error("无法创建输出文件")
                                    try {
                                        resolver.openOutputStream(output)?.use { it.write(bytes) }
                                            ?: error("无法写入输出文件")
                                    } catch (e: Exception) {
                                        resolver.delete(output, null, null)
                                        throw e
                                    }
                                    val action = when (selectedMode) {
                                        "压缩" -> "图片压缩"
                                        "格式转换" -> "格式转换"
                                        else -> "调整尺寸"
                                    }
                                    val detail = when (selectedMode) {
                                        "压缩" -> "质量 ${selectedQuality}%"
                                        "格式转换" -> "转为 ${selectedFormat}"
                                        else -> "${selectedScale.toInt()}%"
                                    }
                                    ProcessingHistory.add(
                                        context, action, source.uri, output,
                                        source.bytes, bytes.size.toLong(), detail
                                    )
                                    null
                                } catch (e: Exception) {
                                    e.message ?: "未知错误"
                                }
                            }
                            if (result == null) succeeded++ else failures.add("${source.name}：$result")
                            progress = index + 1
                        }
                        message = if (failures.isEmpty()) {
                            "处理完成：成功 ${succeeded} 张，已保存到 Pictures/轻图/"
                        } else {
                            "处理完成：成功 ${succeeded} 张，失败 ${failures.size} 张。\n" +
                                failures.take(3).joinToString("\n") +
                                if (failures.size > 3) "\n…" else ""
                        }
                        busy = false
                    }
                },
                enabled = sources.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (uris.size > 1) "批量处理并保存" else "处理并保存")
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
    val kb = bytes / 1024.0
    return if (kb < 1024) String.format("%.1f KB", kb)
    else String.format("%.2f MB", kb / 1024.0)
}
