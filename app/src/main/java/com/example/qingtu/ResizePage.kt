package com.example.qingtu

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream


@Composable
internal fun ResizePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var originalBytes by remember { mutableLongStateOf(0L) }
    var sourceMime by remember { mutableStateOf("image/jpeg") }
    var widthText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var keepRatio by remember { mutableStateOf(true) }
    var percent by remember { mutableFloatStateOf(100f) }
    var mode by remember { mutableStateOf("像素") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("选择图片，设置新的尺寸。") }
    var savedSize by remember { mutableStateOf("") }

    val source = bitmap
    val originalWidth = source?.width ?: 0
    val originalHeight = source?.height ?: 0
    val targetWidth = if (mode == "比例") (originalWidth * percent / 100f).toInt().coerceAtLeast(1) else widthText.toIntOrNull() ?: 0
    val targetHeight = if (mode == "比例") (originalHeight * percent / 100f).toInt().coerceAtLeast(1) else heightText.toIntOrNull() ?: 0

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            sourceUri = uri
            sourceMime = context.contentResolver.getType(uri) ?: "image/jpeg"
            bitmap = null
            originalBytes = 0L
            savedSize = ""
            message = "正在读取图片…"
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    val image = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                    Pair(image, size)
                }
                bitmap = loaded.first
                originalBytes = loaded.second
                widthText = loaded.first?.width?.toString() ?: ""
                heightText = loaded.first?.height?.toString() ?: ""
                percent = 100f
                message = if (loaded.first != null) "已载入原图，可按像素或比例调整。" else "图片读取失败，请重新选择。"
            }
        }
    }

    Column(Modifier.fillMaxSize().background(PageBg).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Text("调整尺寸", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Image, null)
            Spacer(Modifier.width(8.dp))
            Text(if (bitmap == null) "选择图片" else "重新选择图片")
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth().height(210.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
            Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                if (source != null) Image(source.asImageBitmap(), contentDescription = "图片预览", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text("图片预览区域", color = Muted)
            }
        }
        if (originalWidth > 0) {
            Text("原图：${originalWidth} × ${originalHeight} px", color = DeepGreen, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("调整方式", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf("像素", "比例").forEach { item ->
                FilterChip(selected = mode == item, onClick = { mode = item; savedSize = "" }, label = { Text(if (item == "像素") "指定像素" else "按比例缩放") }, enabled = !busy)
            }
        }
        if (mode == "像素") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = widthText, onValueChange = { value ->
                    widthText = value.filter { it.isDigit() }.take(5)
                    if (keepRatio && originalWidth > 0) {
                        val w = widthText.toIntOrNull()
                        if (w != null) heightText = (w.toDouble() * originalHeight / originalWidth).toInt().coerceAtLeast(1).toString()
                    }
                    savedSize = ""
                }, label = { Text("宽度 (px)") }, modifier = Modifier.weight(1f), singleLine = true, enabled = source != null && !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = heightText, onValueChange = { value ->
                    heightText = value.filter { it.isDigit() }.take(5)
                    if (keepRatio && originalHeight > 0) {
                        val h = heightText.toIntOrNull()
                        if (h != null) widthText = (h.toDouble() * originalWidth / originalHeight).toInt().coerceAtLeast(1).toString()
                    }
                    savedSize = ""
                }, label = { Text("高度 (px)") }, modifier = Modifier.weight(1f), singleLine = true, enabled = source != null && !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = keepRatio, onCheckedChange = { keepRatio = it }, enabled = source != null && !busy)
                Text("保持原图比例", color = Ink, fontSize = 14.sp)
            }
        } else {
            Text("缩放比例：${percent.toInt()}%", color = Ink, fontWeight = FontWeight.Medium)
            Slider(value = percent, onValueChange = { percent = it; savedSize = "" }, valueRange = 10f..200f, enabled = source != null && !busy)
        }
        if (source != null) {
            Text("输出尺寸：${targetWidth} × ${targetHeight} px", color = DeepGreen, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("原图不会被修改，处理后的图片将另存为新文件。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text(message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        if (savedSize.isNotEmpty()) Text(savedSize, color = DeepGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        Button(onClick = {
            val input = bitmap
            val w = targetWidth
            val h = targetHeight
            if (input != null && !busy && w in 1..12000 && h in 1..12000) {
                busy = true
                message = "正在调整并保存…"
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val resized = Bitmap.createScaledBitmap(input, w, h, true)
                            val mime = if (resized.hasAlpha()) "image/png" else sourceMime
                            val format = when {
                                mime == "image/png" -> Bitmap.CompressFormat.PNG
                                mime == "image/webp" -> Bitmap.CompressFormat.WEBP
                                else -> Bitmap.CompressFormat.JPEG
                            }
                            val extension = when (format) { Bitmap.CompressFormat.PNG -> "png"; Bitmap.CompressFormat.WEBP -> "webp"; else -> "jpg" }
                            val outputMime = when (format) { Bitmap.CompressFormat.PNG -> "image/png"; Bitmap.CompressFormat.WEBP -> "image/webp"; else -> "image/jpeg" }
                            val buffer = ByteArrayOutputStream()
                            if (!resized.compress(format, 95, buffer)) error("图片处理失败")
                            if (resized !== input) resized.recycle()
                            val bytes = buffer.toByteArray()
                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, "轻图_尺寸_${System.currentTimeMillis()}.$extension")
                                put(MediaStore.Images.Media.MIME_TYPE, outputMime)
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/轻图")
                            }
                            val resolver = context.contentResolver
                            val output = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建保存文件")
                            try {
                                resolver.openOutputStream(output)?.use { it.write(bytes) } ?: error("无法写入文件")
                                ProcessingHistory.add(context, "调整尺寸", sourceUri, output, originalBytes, bytes.size.toLong(), "${w} × ${h} px")
                                Pair("已保存到 Pictures/轻图/，文件大小：${resizeBytes(bytes.size.toLong())}", true)
                            } catch (e: Exception) {
                                resolver.delete(output, null, null)
                                throw e
                            }
                        } catch (e: Exception) { Pair("保存失败：${e.message ?: "未知错误"}", false) }
                    }
                    message = result.first
                    savedSize = if (result.second) "输出尺寸：${w} × ${h} px" else ""
                    busy = false
                }
            } else if (input != null) {
                message = "尺寸需在 1–12000 像素之间，请检查输入。"
            }
        }, enabled = bitmap != null && !busy && targetWidth in 1..12000 && targetHeight in 1..12000, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("调整尺寸并保存")
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun resizeBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    return String.format("%.2f MB", kb / 1024.0)
}
