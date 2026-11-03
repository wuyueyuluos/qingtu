package com.example.qingtu

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat

private val ConvertBg = Color(0xFFF6F7F3)
private val ConvertGreen = Color(0xFF496B5B)
private val ConvertInk = Color(0xFF26332C)
private val ConvertMuted = Color(0xFF89938B)

@Composable
internal fun FormatConversionPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var originalBytes by remember { mutableLongStateOf(0L) }
    var convertedBytes by remember { mutableLongStateOf(0L) }
    var format by remember { mutableStateOf("JPG") }
    var quality by remember { mutableFloatStateOf(90f) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("选择图片，再选择想要转换的格式。") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            sourceUri = uri
            bitmap = null
            convertedBytes = 0L
            message = "正在读取图片…"
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    val image = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                    Pair(image, size)
                }
                bitmap = loaded.first
                originalBytes = loaded.second
                message = if (loaded.first != null) "图片已载入，选择格式后即可转换。" else "图片读取失败，请重新选择。"
            }
        }
    }
    Column(Modifier.fillMaxSize().background(ConvertBg).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Text("格式转换", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ConvertInk)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Image, null)
            Spacer(Modifier.width(8.dp))
            Text(if (bitmap == null) "选择图片" else "重新选择图片")
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                val preview = bitmap
                if (preview != null) Image(preview.asImageBitmap(), contentDescription = "图片预览", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text("图片预览区域", color = ConvertMuted)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("目标格式", color = ConvertInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("JPG", "PNG", "WebP").forEach { item ->
                FilterChip(selected = format == item, onClick = { format = item; convertedBytes = 0L }, label = { Text(item) }, enabled = !busy)
            }
        }
        if (format == "PNG") {
            Text("PNG 为无损格式，不需要调整质量。", color = ConvertMuted, fontSize = 12.sp)
        } else {
            Text("转换质量：" + quality.toInt() + "%", color = ConvertInk, fontWeight = FontWeight.Medium)
            Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..100f, enabled = bitmap != null && !busy)
        }
        if (originalBytes > 0L) Text("原始大小：" + convertBytes(originalBytes), color = ConvertGreen, fontSize = 13.sp)
        if (convertedBytes > 0L) {
            val diff = ((originalBytes - convertedBytes).toDouble() / originalBytes * 100).toInt()
            Text("转换后：" + convertBytes(convertedBytes), color = ConvertGreen, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(if (diff >= 0) "体积减少 " + diff + "%" else "文件增大了 " + (-diff) + "%", color = ConvertMuted, fontSize = 12.sp)
        }
        Text(message, color = ConvertMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val source = bitmap
            if (source != null && !busy) {
                busy = true
                message = "正在转换并保存…"
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val selectedFormat = format
                            val outputBitmap = if (selectedFormat == "JPG" && source.hasAlpha()) {
                                Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { flattened ->
                                    val canvas = Canvas(flattened)
                                    canvas.drawColor(AndroidColor.WHITE)
                                    canvas.drawBitmap(source, 0f, 0f, null)
                                }
                            } else source
                            val compressFormat = when (selectedFormat) {
                                "PNG" -> Bitmap.CompressFormat.PNG
                                "WebP" -> Bitmap.CompressFormat.WEBP
                                else -> Bitmap.CompressFormat.JPEG
                            }
                            val buffer = ByteArrayOutputStream()
                            val ok = outputBitmap.compress(compressFormat, if (selectedFormat == "PNG") 100 else quality.toInt(), buffer)
                            if (outputBitmap !== source) outputBitmap.recycle()
                            if (!ok) error("图片转换失败")
                            val bytes = buffer.toByteArray()
                            val ext = when (selectedFormat) { "PNG" -> "png"; "WebP" -> "webp"; else -> "jpg" }
                            val mime = when (selectedFormat) { "PNG" -> "image/png"; "WebP" -> "image/webp"; else -> "image/jpeg" }
                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, "轻图_" + System.currentTimeMillis() + "." + ext)
                                put(MediaStore.Images.Media.MIME_TYPE, mime)
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/轻图")
                            }
                            val resolver = context.contentResolver
                            val output = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建保存文件")
                            try {
                                resolver.openOutputStream(output)?.use { it.write(bytes) } ?: error("无法写入文件")
                                ProcessingHistory.add(context, "格式转换", sourceUri, output, originalBytes, bytes.size.toLong(), "转为 $selectedFormat")
                                Pair("已保存到 Pictures/轻图/", bytes.size.toLong())
                            } catch (e: Exception) {
                                resolver.delete(output, null, null)
                                throw e
                            }
                        } catch (e: Exception) { Pair("转换失败：" + (e.message ?: "未知错误"), 0L) }
                    }
                    message = result.first
                    convertedBytes = result.second
                    busy = false
                }
            }
        }, enabled = bitmap != null && !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("开始转换并保存")
        }
    }
}

private fun convertBytes(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    val kb = bytes / 1024.0
    if (kb < 1024) return DecimalFormat("0.0").format(kb) + " KB"
    return DecimalFormat("0.00").format(kb / 1024.0) + " MB"
}