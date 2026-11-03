package com.example.qingtu

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.util.Size
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream


private fun queryBatchGallery(context: android.content.Context): List<Uri> {
    val result = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sort)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext()) {
            result.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn).toString()))
        }
    }
    return result
}

@Composable
internal fun BatchProcessingPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var mode by remember { mutableStateOf("压缩") }
    var format by remember { mutableStateOf("JPG") }
    var quality by remember { mutableFloatStateOf(80f) }
    var scale by remember { mutableFloatStateOf(80f) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var galleryUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedGalleryUris by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var showGallery by remember { mutableStateOf(false) }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                galleryUris = withContext(Dispatchers.IO) { queryBatchGallery(context) }
                selectedGalleryUris = emptySet()
                showGallery = true
            }
        } else result = "需要允许轻图访问图片，才能显示本地图片列表。"
    }



    if (showGallery) {
        AlertDialog(
            onDismissRequest = { showGallery = false },
            title = { Text("选择图片（已选 ${selectedGalleryUris.size} 张）") },
            text = {
                if (galleryUris.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Text("没有找到本地图片", color = Muted)
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().height(420.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(galleryUris) { uri ->
                            var thumb by remember(uri) { mutableStateOf<Bitmap?>(null) }
                            LaunchedEffect(uri) {
                                thumb = withContext(Dispatchers.IO) {
                                    try { context.contentResolver.loadThumbnail(uri, Size(240, 240), null) } catch (_: Exception) { null }
                                }
                            }
                            val selected = selectedGalleryUris.contains(uri)
                            Box(Modifier.aspectRatio(1f).background(Color(0xFFE8ECE8), RoundedCornerShape(8.dp)).clickable {
                                selectedGalleryUris = if (selected) selectedGalleryUris - uri else selectedGalleryUris + uri
                            }) {
                                if (thumb != null) androidx.compose.foundation.Image(thumb!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                if (selected) {
                                    Box(Modifier.align(Alignment.TopEnd).padding(5.dp).background(DeepGreen, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    images = selectedGalleryUris.toList()
                    result = "已选择 ${images.size} 张图片。"
                    showGallery = false
                }, enabled = selectedGalleryUris.isNotEmpty()) { Text("确定（${selectedGalleryUris.size}）") }
            },
            dismissButton = { TextButton(onClick = { showGallery = false }) { Text("取消") } }
        )
    }

    Column(Modifier.fillMaxSize().background(PageBg).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Text("批量处理", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                scope.launch {
                    galleryUris = withContext(Dispatchers.IO) { queryBatchGallery(context) }
                    selectedGalleryUris = emptySet()
                    showGallery = true
                }
            } else permissionLauncher.launch(permission)
        }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Icon(Icons.Rounded.AddPhotoAlternate, null)
            Spacer(Modifier.width(8.dp))
            Text(if (images.isEmpty()) "从本机相册选择图片" else "重新选择图片（当前 ${images.size} 张）")
        }
        if (images.isNotEmpty()) {
            Text("已选中 ${images.size} 张图片", color = DeepGreen, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text("所有图片都会单独生成新文件，原图不会被覆盖。", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("处理方式", color = Ink, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf("压缩", "格式转换", "调整尺寸").forEach {
                FilterChip(selected = mode == it, onClick = { mode = it; result = "" }, label = { Text(it) }, enabled = !busy)
            }
        }
        when (mode) {
            "压缩" -> {
                Text("压缩质量：${quality.toInt()}%", color = Ink, modifier = Modifier.padding(top = 12.dp))
                Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..100f, enabled = !busy)
                Text("质量越低，通常文件越小。批量压缩将输出 JPG 文件。", color = Muted, fontSize = 12.sp)
            }
            "格式转换" -> {
                Text("目标格式", color = Ink, modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("JPG", "PNG", "WebP").forEach {
                        FilterChip(selected = format == it, onClick = { format = it }, label = { Text(it) }, enabled = !busy)
                    }
                }
                if (format != "PNG") {
                    Text("转换质量：${quality.toInt()}%", color = Ink)
                    Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..100f, enabled = !busy)
                } else Text("PNG 为无损格式。", color = Muted, fontSize = 12.sp)
            }
            else -> {
                Text("缩放比例：${scale.toInt()}%", color = Ink, modifier = Modifier.padding(top = 12.dp))
                Slider(value = scale, onValueChange = { scale = it }, valueRange = 10f..200f, enabled = !busy)
                Text("所有图片按相同比例缩放，保持各自原图比例。", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (result.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(result, color = DeepGreen, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val selected = images
            if (selected.isNotEmpty() && !busy) {
                busy = true
                result = "正在处理图片，请稍候…"
                scope.launch {
                    val summary = withContext(Dispatchers.IO) {
                        var success = 0
                        val errors = mutableListOf<String>()
                        selected.forEachIndexed { index, uri ->
                            try {
                                val input = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                                    ?: error("无法读取图片")
                                val inputBytes = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                                val currentMode = mode
                                val currentFormat = format
                                val currentQuality = quality.toInt()
                                val currentScale = scale
                                val outputBitmap: Bitmap
                                val outputFormat: Bitmap.CompressFormat
                                val ext: String
                                val mime: String
                                when (currentMode) {
                                    "压缩" -> {
                                        val sourceName = getImageDisplayName(context, uri)
                                        val encoded = imageFormatFromMime(context.contentResolver.getType(uri) ?: "", sourceName)
                                        outputBitmap = if (encoded.format == Bitmap.CompressFormat.JPEG && input.hasAlpha()) Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888).also { bmp ->
                                            val canvas = Canvas(bmp); canvas.drawColor(AndroidColor.WHITE); canvas.drawBitmap(input, 0f, 0f, null)
                                        } else input
                                        outputFormat = encoded.format; ext = encoded.extension; mime = encoded.mime
                                    }
                                    "格式转换" -> {
                                        outputBitmap = if (currentFormat == "JPG" && input.hasAlpha()) Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888).also { bmp ->
                                            val canvas = Canvas(bmp); canvas.drawColor(AndroidColor.WHITE); canvas.drawBitmap(input, 0f, 0f, null)
                                        } else input
                                        outputFormat = when (currentFormat) { "PNG" -> Bitmap.CompressFormat.PNG; "WebP" -> Bitmap.CompressFormat.WEBP; else -> Bitmap.CompressFormat.JPEG }
                                        ext = when (currentFormat) { "PNG" -> "png"; "WebP" -> "webp"; else -> "jpg" }
                                        mime = when (currentFormat) { "PNG" -> "image/png"; "WebP" -> "image/webp"; else -> "image/jpeg" }
                                    }
                                    else -> {
                                        val w = (input.width * currentScale / 100f).toInt().coerceAtLeast(1)
                                        val h = (input.height * currentScale / 100f).toInt().coerceAtLeast(1)
                                        outputBitmap = Bitmap.createScaledBitmap(input, w, h, true)
                                        val encoded = imageFormatFromMime(context.contentResolver.getType(uri) ?: "", getImageDisplayName(context, uri))
                                        outputFormat = encoded.format
                                        ext = encoded.extension
                                        mime = encoded.mime
                                    }
                                }
                                val buffer = ByteArrayOutputStream()
                                val q = if (outputFormat == Bitmap.CompressFormat.PNG) 100 else currentQuality
                                if (!outputBitmap.compress(outputFormat, q, buffer)) error("编码失败")
                                if (outputBitmap !== input) outputBitmap.recycle()
                                input.recycle()
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, outputImageName(context, uri, ext))
                                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/轻图")
                                }
                                val resolver = context.contentResolver
                                val out = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建文件")
                                val outputBytes = buffer.toByteArray()
                                try { resolver.openOutputStream(out)?.use { it.write(outputBytes) } ?: error("写入失败") }
                                catch (e: Exception) { resolver.delete(out, null, null); throw e }
                                val action = when (currentMode) { "压缩" -> "批量压缩"; "格式转换" -> "批量格式转换"; else -> "批量调整尺寸" }
                                val detail = when (currentMode) { "格式转换" -> "转为 $currentFormat"; "调整尺寸" -> "${currentScale.toInt()}%"; else -> "质量 ${currentQuality}%" }
                                ProcessingHistory.add(context, action, uri, out, inputBytes, outputBytes.size.toLong(), detail)
                                success++
                            } catch (e: Exception) { errors.add("${index + 1}号图片：${e.message ?: "处理失败"}") }
                        }
                        "处理完成：成功 $success / ${selected.size} 张。失败 ${errors.size} 张。" + if (errors.isEmpty()) "" else "\n" + errors.take(5).joinToString("\n")
                    }
                    result = summary
                    busy = false
                }
            }
        }, enabled = images.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("开始批量处理并保存")
        }
        Spacer(Modifier.height(12.dp))
        Text("保存位置：Pictures/轻图/", color = Muted, fontSize = 12.sp)
    }
}
