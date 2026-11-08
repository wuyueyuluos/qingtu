package com.example.qingtu

import android.Manifest
import android.app.Activity
import android.widget.Toast
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.io.ByteArrayOutputStream

private data class GalleryFolder(val path: String, val images: List<Uri>, val latestModified: Long = 0L)

private fun loadGalleryFolders(context: Context, sortMode: String, ascending: Boolean): List<GalleryFolder> {
    val map = linkedMapOf<String, MutableList<Pair<Uri, Long>>>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.DATE_MODIFIED
    )
    val direction = if (ascending) "ASC" else "DESC"
    val sortColumn = if (sortMode == "date") MediaStore.Images.Media.DATE_MODIFIED else MediaStore.Images.Media.DISPLAY_NAME
    val sort = "${MediaStore.Images.Media.RELATIVE_PATH} ASC, $sortColumn $direction, ${MediaStore.Images.Media.DISPLAY_NAME} ASC"
    context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sort)?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val pathCol = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
        val modifiedCol = c.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
        while (c.moveToNext()) {
            val path = if (pathCol >= 0) c.getString(pathCol) ?: "其他/" else "其他/"
            val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol).toString())
            val modified = if (modifiedCol >= 0) c.getLong(modifiedCol) else 0L
            map.getOrPut(path) { mutableListOf() }.add(uri to modified)
        }
    }

    val folders = map.map { (path, entries) ->
        GalleryFolder(path, entries.map { it.first }, entries.maxOfOrNull { it.second } ?: 0L)
    }
    return if (sortMode == "date") {
        folders.sortedWith(
            compareBy<GalleryFolder> { it.latestModified * if (ascending) 1L else -1L }
                .thenBy { it.path }
        )
    } else folders
}

@Composable
internal fun GalleryWorkspacePage(
    appearance: String,
    onAppearanceChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<GalleryFolder>>(emptyList()) }
    val preferences = remember { context.getSharedPreferences("qingtu_settings", Context.MODE_PRIVATE) }
    var sortMode by remember { mutableStateOf(preferences.getString("gallery_sort_mode", "name") ?: "name") }
    var sortAscending by remember { mutableStateOf(preferences.getBoolean("gallery_sort_ascending", true)) }
    var showSortMenu by remember { mutableStateOf(false) }
    var currentPath by remember {
        mutableStateOf(context.getSharedPreferences("qingtu_settings", Context.MODE_PRIVATE)
            .getString("batch_gallery_folder", "全部文件夹") ?: "全部文件夹")
    }
    var browsingPath by remember { mutableStateOf("") }
    var pathMode by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var previewImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var previewIndex by remember { mutableIntStateOf(0) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf("") }
    var quality by remember { mutableFloatStateOf(80f) }
    var targetFormat by remember { mutableStateOf("JPG") }
    var resizePercent by remember { mutableFloatStateOf(80f) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var showStatus by remember { mutableStateOf(false) }
    var lastBackPressedAt by remember { mutableLongStateOf(0L) }

    BackHandler {
        when {
            pathMode -> {
                val path = browsingPath.trim('/')
                if (path.isBlank()) {
                    pathMode = false
                } else {
                    val parent = path.substringBeforeLast('/', "")
                    browsingPath = if (parent.isBlank()) "" else "$parent/"
                }
            }
            selectionMode && selected.isNotEmpty() -> {
                selected = emptySet()
            }
            selectionMode -> {
                selectionMode = false
                selected = emptySet()
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressedAt <= 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressedAt = now
                    Toast.makeText(context, "再按一次返回键退出轻图", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    fun refreshGallery() {
        scope.launch {
            folders = withContext(Dispatchers.IO) { loadGalleryFolders(context, sortMode, sortAscending) }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refreshGallery()
        else { status = "需要允许轻图访问本地图片。"; showStatus = true }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) refreshGallery()
        else permissionLauncher.launch(permission)
    }

    val normalizedPath = currentPath.trimStart('/').let { if (it.isBlank()) "" else if (it.endsWith("/")) it else "$it/" }
    val visibleFolders = if (currentPath == "全部文件夹") folders else folders.filter { f ->
        val p = f.path.trimStart('/')
        p == normalizedPath.trimEnd('/') || p.startsWith(normalizedPath)
    }
    val visibleCount = visibleFolders.sumOf { it.images.size }

    fun updateSort(mode: String = sortMode, ascending: Boolean = sortAscending) {
        sortMode = mode
        sortAscending = ascending
        preferences.edit().putString("gallery_sort_mode", mode).putBoolean("gallery_sort_ascending", ascending).apply()
        refreshGallery()
    }

    Box(Modifier.fillMaxSize().background(PageBg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("轻图", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(if (currentPath == "全部文件夹") "全部图片 · $visibleCount 张" else currentPath + " · $visibleCount 张",
                        color = Muted, fontSize = 12.sp, maxLines = 1)
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Rounded.Tune, "排序方式", tint = DeepGreen, modifier = Modifier.size(25.dp))
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (sortMode == "name") "✓ 按名称排序" else "按名称排序") },
                            onClick = { showSortMenu = false; updateSort(mode = "name") }
                        )
                        DropdownMenuItem(
                            text = { Text(if (sortMode == "date") "✓ 按日期排序" else "按日期排序") },
                            onClick = { showSortMenu = false; updateSort(mode = "date") }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text(if (sortAscending) "✓ 升序" else "升序") },
                            onClick = { showSortMenu = false; updateSort(ascending = true) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (!sortAscending) "✓ 降序" else "降序") },
                            onClick = { showSortMenu = false; updateSort(ascending = false) }
                        )
                    }
                }
                IconButton(onClick = { showHistory = true }) {
                    Icon(Icons.Rounded.History, "处理记录", tint = DeepGreen, modifier = Modifier.size(25.dp))
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Rounded.Settings, "设置", tint = DeepGreen, modifier = Modifier.size(25.dp))
                }
            }
            if (selected.isNotEmpty() && !selectionMode) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("压缩", "转换格式", "调整尺寸").forEach { label ->
                        FilledTonalButton(onClick = { operation = label }, modifier = Modifier.weight(1f)) { Text(label, fontSize = 12.sp) }
                    }
                }
            }
            if (folders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在读取图片，或当前没有可访问的图片。", color = Muted)
                }
            } else if (visibleFolders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("当前路径下没有图片", color = Muted) }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleFolders.forEach { folder ->
                        item(key = "header:${folder.path}") {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(folder.path.trimEnd('/').substringAfterLast('/').ifBlank { "根目录" },
                                    color = DeepGreen, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                Text("${folder.images.size} 张", color = Muted, fontSize = 11.sp)
                            }
                        }
                        items(folder.images.chunked(4), key = { row -> row.joinToString("|") }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                row.forEach { uri ->
                                    var thumb by remember(uri) { mutableStateOf<Bitmap?>(null) }
                                    LaunchedEffect(uri) {
                                        thumb = withContext(Dispatchers.IO) {
                                            try { context.contentResolver.loadThumbnail(uri, Size(320, 320), null) } catch (_: Exception) { null }
                                        }
                                    }
                                    val isSelected = selected.contains(uri)
                                    Box(
                                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(9.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                if (selectionMode) selected = if (isSelected) selected - uri else selected + uri
                                                else {
                                                    previewImages = visibleFolders.flatMap { it.images }
                                                    previewIndex = previewImages.indexOf(uri).coerceAtLeast(0)
                                                    previewUri = uri
                                                    previewBitmap = null
                                                }
                                            }
                                    ) {
                                        if (thumb != null) Image(thumb!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        if (selectionMode && isSelected) {
                                            Box(Modifier.fillMaxSize().background(DeepGreen.copy(alpha = 0.32f)))
                                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(25.dp))
                                        }
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                            }
                        }
                    }
                }
            }
        }

        if (!pathMode && !selectionMode) {
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FloatingActionButton(onClick = {
                    browsingPath = if (currentPath == "全部文件夹") "" else currentPath
                    pathMode = true
                }, containerColor = SoftGreen, contentColor = DeepGreen) {
                    Icon(Icons.Rounded.FolderOpen, "选择路径")
                }
                FloatingActionButton(onClick = { selected = emptySet(); selectionMode = true },
                    containerColor = DeepGreen, contentColor = Color.White) {
                    Icon(Icons.Rounded.Check, "选择图片")
                }
            }
        }
        if (selectionMode) {
            FloatingActionButton(
                onClick = { selectionMode = false },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 22.dp),
                containerColor = DeepGreen, contentColor = Color.White
            ) { Icon(Icons.Rounded.Check, "确定") }
            TextButton(onClick = { selectionMode = false; selected = emptySet() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 92.dp, bottom = 30.dp)) {
                Text("取消", color = DeepGreen)
            }
        }
        if (pathMode) {
            val current = browsingPath.trimStart('/').let { if (it.isBlank()) "" else if (it.endsWith("/")) it else "$it/" }
            val children = folders.mapNotNull { f ->
                val p = f.path.trimStart('/')
                if (!p.startsWith(current)) null else p.removePrefix(current).trimEnd('/').substringBefore('/').takeIf { it.isNotBlank() }
            }.distinct().sorted()
            Box(Modifier.fillMaxSize().background(PageBg)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("选择图片路径", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pathMode = false }) { Text("取消") }
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (current.isEmpty()) "/" else "/$current", color = DeepGreen, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (current.isNotEmpty()) TextButton(onClick = {
                            browsingPath = current.trimEnd('/').substringBeforeLast('/', "").let { if (it.isBlank()) "" else "$it/" }
                        }) { Text("上一级") }
                    }
                    if (children.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("当前目录没有包含图片的子文件夹。", color = Muted)
                    } else LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                        items(children) { child ->
                            Row(Modifier.fillMaxWidth().clickable { browsingPath = current + child + "/" }.padding(vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.FolderOpen, null, tint = DeepGreen)
                                Spacer(Modifier.width(14.dp))
                                Text(child, color = Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Text("›", color = Muted, fontSize = 24.sp)
                            }
                            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
                FloatingActionButton(onClick = {
                    currentPath = current.ifBlank { "全部文件夹" }
                    context.getSharedPreferences("qingtu_settings", Context.MODE_PRIVATE).edit().putString("batch_gallery_folder", currentPath).apply()
                    pathMode = false
                }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 22.dp),
                    containerColor = DeepGreen, contentColor = Color.White) { Icon(Icons.Rounded.Check, "确定") }
            }
        }
    }

    previewUri?.let { uri ->
        Dialog(
            onDismissRequest = { previewUri = null; previewBitmap = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            var loading by remember(uri) { mutableStateOf(true) }
            var scale by remember(uri) { mutableFloatStateOf(1f) }
            var offsetX by remember(uri) { mutableFloatStateOf(0f) }
            var offsetY by remember(uri) { mutableFloatStateOf(0f) }
            LaunchedEffect(uri) {
                loading = true
                previewBitmap = withContext(Dispatchers.IO) {
                    try {
                        val resolver = context.contentResolver
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
                            val metrics = context.resources.displayMetrics
                            val targetWidth = metrics.widthPixels * 2
                            val targetHeight = metrics.heightPixels * 2
                            var sample = 1
                            while (bounds.outWidth / (sample * 2) >= targetWidth &&
                                bounds.outHeight / (sample * 2) >= targetHeight) sample *= 2
                            val options = BitmapFactory.Options().apply { inSampleSize = sample }
                            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                        }
                    } catch (_: Exception) { null }
                }
                loading = false
            }
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(uri) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var swipeDistance = 0f
                            var edgeSwipeDistance = 0f
                            var event = awaitPointerEvent()
                            while (event.changes.any { it.pressed }) {
                                val pressed = event.changes.filter { it.pressed }
                                val bitmap = previewBitmap
                                if (pressed.size >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val oldScale = scale
                                    val newScale = (oldScale * zoomChange).coerceIn(1f, 6.5f)
                                    val scaleFactor = newScale / oldScale

                                    // graphicsLayer scales around the viewport center by default.
                                    // Convert the finger centroid to coordinates relative to that pivot
                                    // before compensating the translation, so the touched image point stays fixed.
                                    val pivotX = size.width / 2f
                                    val pivotY = size.height / 2f
                                    offsetX = (centroid.x - pivotX) -
                                        (centroid.x - pivotX - offsetX) * scaleFactor + panChange.x
                                    offsetY = (centroid.y - pivotY) -
                                        (centroid.y - pivotY - offsetY) * scaleFactor + panChange.y
                                    scale = newScale

                                    if (bitmap != null && size.width > 0 && size.height > 0) {
                                        val fit = minOf(
                                            size.width.toFloat() / bitmap.width,
                                            size.height.toFloat() / bitmap.height
                                        )
                                        val maxX = ((bitmap.width * fit * newScale - size.width) / 2f).coerceAtLeast(0f)
                                        val maxY = ((bitmap.height * fit * newScale - size.height) / 2f).coerceAtLeast(0f)
                                        offsetX = offsetX.coerceIn(-maxX, maxX)
                                        offsetY = offsetY.coerceIn(-maxY, maxY)
                                    }
                                    if (newScale <= 1.01f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                    swipeDistance = 0f
                                    edgeSwipeDistance = 0f
                                } else if (pressed.size == 1) {
                                    val delta = pressed.first().positionChange()
                                    if (scale > 1.01f && bitmap != null && size.width > 0 && size.height > 0) {
                                        val fit = minOf(
                                            size.width.toFloat() / bitmap.width,
                                            size.height.toFloat() / bitmap.height
                                        )
                                        val maxX = ((bitmap.width * fit * scale - size.width) / 2f).coerceAtLeast(0f)
                                        val maxY = ((bitmap.height * fit * scale - size.height) / 2f).coerceAtLeast(0f)
                                        val oldX = offsetX
                                        val oldY = offsetY
                                        offsetX = (offsetX + delta.x).coerceIn(-maxX, maxX)
                                        offsetY = (offsetY + delta.y).coerceIn(-maxY, maxY)

                                        // Only the movement that cannot pan the image is treated as an edge swipe.
                                        edgeSwipeDistance += delta.x - (offsetX - oldX)
                                    } else {
                                        swipeDistance += delta.x
                                    }
                                }
                                event.changes.forEach { it.consume() }
                                event = awaitPointerEvent()
                            }

                            val switchDistance = if (scale > 1.01f) edgeSwipeDistance else swipeDistance
                            if (abs(switchDistance) > 85f) {
                                val next = if (switchDistance < 0f) previewIndex + 1 else previewIndex - 1
                                if (next in previewImages.indices) {
                                    previewIndex = next
                                    previewUri = previewImages[next]
                                    previewBitmap = null
                                }
                            }
                        }
                    },

                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        previewBitmap!!.asImageBitmap(),
                        contentDescription = "图片预览",
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(if (loading) "正在加载…" else "图片无法打开", color = Color.White)
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("外观", color = Ink, fontWeight = FontWeight.SemiBold)
                    listOf("跟随系统", "日间模式", "夜间模式").forEach { option ->
                        FilterChip(selected = appearance == option, onClick = { onAppearanceChange(option) }, label = { Text(option) })
                    }
                    Text("处理后的图片保存到 Pictures/轻图/，原图不会被覆盖。", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("完成") } }
        )
    }
    if (showHistory) {
        Dialog(onDismissRequest = { showHistory = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = PageBg) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("处理记录", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showHistory = false }) { Text("关闭") }
                    }
                    ProcessingHistoryPage()
                }
            }
        }
    }

    if (operation.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { if (!busy) operation = "" },
            title = { Text(when(operation) { "压缩" -> "图片压缩"; "转换格式" -> "格式转换"; else -> "调整尺寸" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已选择 ${selected.size} 张图片", color = Muted, fontSize = 12.sp)
                    when(operation) {
                        "压缩" -> {
                            Text("压缩质量：${quality.toInt()}%", color = Ink)
                            Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..100f, enabled = !busy)
                            Text("保持每张图片原有格式。", color = Muted, fontSize = 12.sp)
                        }
                        "转换格式" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("JPG", "PNG", "WebP").forEach { f ->
                                    FilterChip(selected = targetFormat == f, onClick = { targetFormat = f }, label = { Text(f) }, enabled = !busy)
                                }
                            }
                            if (targetFormat != "PNG") {
                                Text("转换质量：${quality.toInt()}%", color = Ink)
                                Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..100f, enabled = !busy)
                            } else Text("PNG 为无损格式。", color = Muted, fontSize = 12.sp)
                        }
                        else -> {
                            Text("缩放比例：${resizePercent.toInt()}%", color = Ink)
                            Slider(value = resizePercent, onValueChange = { resizePercent = it }, valueRange = 10f..200f, enabled = !busy)
                        }
                    }
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp)); Text("正在处理，请稍候…", color = Muted)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && selected.isNotEmpty(), onClick = {
                    val work = selected.toList()
                    val op = operation
                    val q = quality.toInt()
                    val fmt = targetFormat
                    val scale = resizePercent
                    busy = true
                    scope.launch {
                        val summary = withContext(Dispatchers.IO) {
                            var ok = 0
                            val errors = mutableListOf<String>()
                            work.forEachIndexed { index, uri ->
                                try {
                                    val resolver = context.contentResolver
                                    val input = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("无法读取图片")
                                    val before = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                                    val mimeIn = resolver.getType(uri) ?: ""
                                    val sourceFormat = imageFormatFromMime(mimeIn, getImageDisplayName(context, uri))
                                    var outputBitmap: Bitmap = input
                                    val encoded: EncodedImageFormat
                                    when(op) {
                                        "压缩" -> { encoded = sourceFormat }
                                        "转换格式" -> {
                                            encoded = when(fmt) {
                                                "PNG" -> EncodedImageFormat(Bitmap.CompressFormat.PNG, "png", "image/png")
                                                "WebP" -> EncodedImageFormat(Bitmap.CompressFormat.WEBP, "webp", "image/webp")
                                                else -> EncodedImageFormat(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
                                            }
                                        }
                                        else -> {
                                            val w = (input.width * scale / 100f).toInt().coerceAtLeast(1)
                                            val h = (input.height * scale / 100f).toInt().coerceAtLeast(1)
                                            outputBitmap = Bitmap.createScaledBitmap(input, w, h, true)
                                            encoded = sourceFormat
                                        }
                                    }
                                    if (encoded.format == Bitmap.CompressFormat.JPEG && outputBitmap.hasAlpha()) {
                                        val flattened = Bitmap.createBitmap(outputBitmap.width, outputBitmap.height, Bitmap.Config.ARGB_8888)
                                        Canvas(flattened).apply { drawColor(AndroidColor.WHITE); drawBitmap(outputBitmap, 0f, 0f, null) }
                                        if (outputBitmap !== input) outputBitmap.recycle()
                                        outputBitmap = flattened
                                    }
                                    val bytes = ByteArrayOutputStream()
                                    val qNow = if (encoded.format == Bitmap.CompressFormat.PNG) 100 else q
                                    if (!outputBitmap.compress(encoded.format, qNow, bytes)) error("编码失败")
                                    if (outputBitmap !== input) outputBitmap.recycle()
                                    input.recycle()
                                    val values = ContentValues().apply {
                                        put(MediaStore.Images.Media.DISPLAY_NAME, outputImageName(context, uri, encoded.extension))
                                        put(MediaStore.Images.Media.MIME_TYPE, encoded.mime)
                                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/轻图")
                                    }
                                    val out = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建输出文件")
                                    try { resolver.openOutputStream(out)?.use { it.write(bytes.toByteArray()) } ?: error("无法写入输出文件") }
                                    catch (e: Exception) { resolver.delete(out, null, null); throw e }
                                    val action = when(op) { "压缩" -> "图片压缩"; "转换格式" -> "格式转换"; else -> "调整尺寸" }
                                    val detail = when(op) { "压缩" -> "质量 ${q}%"; "转换格式" -> "转为 ${fmt}"; else -> "${scale.toInt()}%" }
                                    ProcessingHistory.add(context, action, uri, out, before, bytes.size().toLong(), detail)
                                    ok++
                                } catch (e: Exception) { errors.add("${index + 1}：${e.message ?: "处理失败"}") }
                            }
                            "完成：成功 $ok / ${work.size} 张，失败 ${errors.size} 张。" + if (errors.isEmpty()) "" else "\n" + errors.take(5).joinToString("\n")
                        }
                        busy = false
                        operation = ""
                        status = summary
                        showStatus = true
                        selected = emptySet()
                    }
                }) { Text(if (busy) "处理中" else "开始处理") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { operation = "" }) { Text("取消") } }
        )
    }
    if (showStatus) AlertDialog(
        onDismissRequest = { showStatus = false },
        title = { Text("处理结果") },
        text = { Text(status) },
        confirmButton = { TextButton(onClick = { showStatus = false }) { Text("知道了") } }
    )
}
