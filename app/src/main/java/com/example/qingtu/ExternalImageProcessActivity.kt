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

class ExternalImageProcessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            }
            else -> intent.data ?: intent.clipData?.getItemAt(0)?.uri
        }
        if (uri == null) {
            finish()
            return
        }
        try {
            if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) { }
        setContent {
            QingTuTheme(darkTheme = false) {
                ExternalImageProcessDialog(uri = uri, onClose = { finish() })
            }
        }
    }
}

@Composable
private fun ExternalImageProcessDialog(uri: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var sourceBytes by remember(uri) { mutableLongStateOf(0L) }
    var sourceMime by remember(uri) { mutableStateOf("image/jpeg") }
    var imageName by remember(uri) { mutableStateOf("正在读取图片…") }
    var mode by remember(uri) { mutableStateOf("压缩") }
    var quality by remember(uri) { mutableFloatStateOf(80f) }
    var targetFormat by remember(uri) { mutableStateOf("JPG") }
    var scale by remember(uri) { mutableFloatStateOf(80f) }
    var busy by remember(uri) { mutableStateOf(false) }
    var message by remember(uri) { mutableStateOf("正在载入图片…") }

    LaunchedEffect(uri) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                val image = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                Triple(image, size, context.contentResolver.getType(uri) ?: "image/jpeg")
            } catch (_: Exception) { Triple(null, 0L, "image/jpeg") }
        }
        bitmap = loaded.first
        sourceBytes = loaded.second
        sourceMime = loaded.third
        imageName = getImageDisplayName(context, uri)
        message = if (loaded.first != null) "原图不会被修改，处理结果将另存为新文件。" else "图片读取失败，可能没有访问权限或格式不受支持。"
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
                    Text(imageName, color = Muted, fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = onClose, enabled = !busy) { Text("关闭") }
            }

            val preview = bitmap
            if (preview != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(preview.asImageBitmap(), "待处理图片",
                        Modifier.size(width = 92.dp, height = 92.dp),
                        contentScale = ContentScale.Crop)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${preview.width} × ${preview.height} px", color = Ink, fontSize = 14.sp)
                        Text(formatBytes(sourceBytes), color = Muted, fontSize = 13.sp)
                        Text(sourceMime, color = Muted, fontSize = 11.sp, maxLines = 1)
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
                        enabled = !busy && preview != null
                    )
                }
            }

            when (mode) {
                "压缩" -> {
                    Text("压缩质量：${quality.toInt()}%", color = Ink, fontSize = 14.sp)
                    Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..100f,
                        enabled = preview != null && !busy)
                    Text("保留原图片格式。", color = Muted, fontSize = 11.sp)
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
                            enabled = preview != null && !busy)
                    } else Text("PNG 为无损格式。", color = Muted, fontSize = 11.sp)
                }
                else -> {
                    Text("缩放比例：${scale.toInt()}%", color = Ink, fontSize = 14.sp)
                    Slider(value = scale, onValueChange = { scale = it }, valueRange = 10f..200f,
                        enabled = preview != null && !busy)
                    Text("输出尺寸：${((preview?.width ?: 0) * scale / 100f).toInt()} × ${((preview?.height ?: 0) * scale / 100f).toInt()} px",
                        color = Muted, fontSize = 11.sp)
                }
            }

            Text(message, color = Muted, fontSize = 12.sp)
            Button(
                onClick = {
                    val source = bitmap ?: return@Button
                    if (busy) return@Button
                    busy = true
                    message = "正在处理并保存…"
                    val selectedMode = mode
                    val selectedQuality = quality.toInt()
                    val selectedFormat = targetFormat
                    val selectedScale = scale
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                val sourceFormat = runCatching {
                                    imageFormatFromMime(sourceMime, imageName)
                                }.getOrDefault(EncodedImageFormat(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg"))
                                val encoded = when (selectedMode) {
                                    "格式转换" -> when (selectedFormat) {
                                        "PNG" -> EncodedImageFormat(Bitmap.CompressFormat.PNG, "png", "image/png")
                                        "WebP" -> EncodedImageFormat(Bitmap.CompressFormat.WEBP, "webp", "image/webp")
                                        else -> EncodedImageFormat(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
                                    }
                                    else -> sourceFormat
                                }
                                var outputBitmap: Bitmap = if (selectedMode == "调整尺寸") {
                                    Bitmap.createScaledBitmap(source,
                                        (source.width * selectedScale / 100f).toInt().coerceAtLeast(1),
                                        (source.height * selectedScale / 100f).toInt().coerceAtLeast(1), true)
                                } else source
                                if (encoded.format == Bitmap.CompressFormat.JPEG && outputBitmap.hasAlpha()) {
                                    val flattened = Bitmap.createBitmap(outputBitmap.width, outputBitmap.height, Bitmap.Config.ARGB_8888)
                                    Canvas(flattened).apply { drawColor(AndroidColor.WHITE); drawBitmap(outputBitmap, 0f, 0f, null) }
                                    if (outputBitmap !== source) outputBitmap.recycle()
                                    outputBitmap = flattened
                                }
                                val buffer = ByteArrayOutputStream()
                                val q = if (encoded.format == Bitmap.CompressFormat.PNG) 100 else selectedQuality
                                if (!outputBitmap.compress(encoded.format, q, buffer)) error("图片编码失败")
                                if (outputBitmap !== source) outputBitmap.recycle()
                                val bytes = buffer.toByteArray()
                                val outputName = outputImageName(context, uri, encoded.extension)
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, outputName)
                                    put(MediaStore.Images.Media.MIME_TYPE, encoded.mime)
                                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/轻图")
                                }
                                val resolver = context.contentResolver
                                val output = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                    ?: error("无法创建输出文件")
                                try {
                                    resolver.openOutputStream(output)?.use { it.write(bytes) } ?: error("无法写入输出文件")
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
                                ProcessingHistory.add(context, action, uri, output, sourceBytes, bytes.size.toLong(), detail)
                                "处理完成，已保存到 Pictures/轻图/"
                            } catch (e: Exception) {
                                "处理失败：${e.message ?: "未知错误"}"
                            }
                        }
                        message = result
                        busy = false
                    }
                },
                enabled = bitmap != null && !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("处理并保存")
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
