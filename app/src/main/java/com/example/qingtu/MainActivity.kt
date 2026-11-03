package com.example.qingtu

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

private val PageBg = Color(0xFFF6F7F3)
private val DeepGreen = Color(0xFF496B5B)
private val SoftGreen = Color(0xFFE7EFE8)
private val Ink = Color(0xFF26332C)
private val Muted = Color(0xFF89938B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QingTuApp() }
    }
}

@Composable
fun QingTuApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCompressor by remember { mutableStateOf(false) }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = DeepGreen, onPrimary = Color.White,
            background = PageBg, surface = Color.White, onSurface = Ink
        )
    ) {
        if (showCompressor) {
            CompressionPage(onBack = { showCompressor = false })
        } else {
            Scaffold(
                containerColor = PageBg,
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Rounded.Image, null) }, label = { Text("首页") })
                        NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Rounded.History, null) }, label = { Text("记录") })
                        NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("设置") })
                    }
                }
            ) { padding ->
                when (selectedTab) {
                    0 -> HomePage(Modifier.padding(padding), onCompress = { showCompressor = true })
                    1 -> EmptyPage("处理记录", "完成的图片会保存在这里。")
                    else -> EmptyPage("设置", "默认保存位置、图片信息与外观设置将在这里提供。")
                }
            }
        }
    }
}

@Composable
private fun HomePage(modifier: Modifier = Modifier, onCompress: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("轻图", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("简单、清爽的本地图片处理工具", fontSize = 14.sp, color = Muted)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = SoftGreen) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("让图片轻一点", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text("所有处理都在设备本地完成", fontSize = 13.sp, color = DeepGreen)
                }
                Box(Modifier.size(58.dp).background(Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Compress, null, tint = DeepGreen, modifier = Modifier.size(30.dp))
                }
            }
        }
        Text("图片工具", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard("图片压缩", "减小文件体积", Icons.Rounded.Compress, Modifier.weight(1f), onCompress)
                ToolCard("格式转换", "JPG、PNG、WebP", Icons.Rounded.SwapHoriz, Modifier.weight(1f)) {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard("调整尺寸", "修改宽度和高度", Icons.Rounded.Tune, Modifier.weight(1f)) {}
                ToolCard("批量处理", "一次处理多张图片", Icons.Rounded.Image, Modifier.weight(1f)) {}
            }
        }
        Text("最近处理", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text("还没有处理记录", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ToolCard(
    title: String, subtitle: String, icon: ImageVector,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = modifier.height(116.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = DeepGreen, modifier = Modifier.size(25.dp))
            Column {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CompressionPage(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalBytes by remember { mutableLongStateOf(0L) }
    var compressedBytes by remember { mutableLongStateOf(0L) }
    var quality by remember { mutableFloatStateOf(75f) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("选择一张图片，开始压缩。") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            bitmap = null
            compressedBytes = 0L
            message = "正在读取图片…"
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    val image = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                    val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                        it.length
                    } ?: 0L
                    Pair(image, size)
                }
                bitmap = loaded.first
                originalBytes = loaded.second
                message = if (loaded.first != null) "图片已载入，可以调整压缩质量。" else "图片读取失败，请换一张图片试试。"
            }
        }
    }

    Column(Modifier.fillMaxSize().background(PageBg).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Text("图片压缩", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Image, null)
            Spacer(Modifier.width(8.dp))
            Text(if (bitmap == null) "选择图片" else "重新选择图片")
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)) {
            Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                val preview = bitmap
                if (preview != null) {
                    Image(preview.asImageBitmap(), contentDescription = "待压缩图片",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Text("图片预览区域", color = Muted)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("压缩质量：${quality.toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..100f,
            enabled = bitmap != null)
        Text("数值越低，文件通常越小，画质损失也会更明显。", fontSize = 12.sp, color = Muted)
        if (originalBytes > 0) {
            Text("原始大小：${formatBytes(originalBytes)}", color = DeepGreen, modifier = Modifier.padding(top = 8.dp))
        }
        if (compressedBytes > 0) {
            val savedPercent = if (originalBytes > 0) {
                ((originalBytes - compressedBytes).toDouble() / originalBytes * 100).toInt()
            } else 0
            Text("压缩后：${formatBytes(compressedBytes)}", color = DeepGreen,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Text(
                if (savedPercent >= 0) "体积减少 $savedPercent%" else "文件增大了 ${-savedPercent}%",
                color = Muted, fontSize = 12.sp
            )
        }
        Text(message, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val source = bitmap
                if (source != null && !busy) {
                    busy = true
                    message = "正在压缩并保存…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                val buffer = ByteArrayOutputStream()
                                if (!source.compress(Bitmap.CompressFormat.JPEG, quality.toInt(), buffer)) {
                                    error("图片压缩失败")
                                }
                                val imageBytes = buffer.toByteArray()
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, "轻图_${System.currentTimeMillis()}.jpg")
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/轻图")
                                }
                                val resolver = context.contentResolver
                                val output = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                    ?: error("无法创建保存文件")
                                try {
                                    resolver.openOutputStream(output)?.use { stream ->
                                        stream.write(imageBytes)
                                    } ?: error("无法写入文件")
                                    Pair("已保存到 Pictures/轻图/", imageBytes.size.toLong())
                                } catch (e: Exception) {
                                    resolver.delete(output, null, null)
                                    throw e
                                }
                            } catch (e: Exception) {
                                Pair("保存失败：${e.message ?: "未知错误"}", 0L)
                            }
                        }
                        message = result.first
                        compressedBytes = result.second
                        busy = false
                    }
                }
            },
            enabled = bitmap != null && !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("开始压缩并保存")
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${DecimalFormat("0.0").format(kb)} KB"
    return "${DecimalFormat("0.00").format(kb / 1024.0)} MB"
}

@Composable
private fun EmptyPage(title: String, description: String) {
    Column(Modifier.fillMaxSize().background(PageBg).padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text(description, color = Muted, fontSize = 14.sp)
    }
}
