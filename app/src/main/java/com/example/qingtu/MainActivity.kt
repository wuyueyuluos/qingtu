package com.example.qingtu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = DeepGreen,
            onPrimary = Color.White,
            background = PageBg,
            surface = Color.White,
            onSurface = Ink
        )
    ) {
        Scaffold(
            containerColor = PageBg,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                        label = { Text("首页") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        label = { Text("记录") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("设置") }
                    )
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> HomePage(Modifier.padding(padding))
                1 -> EmptyPage("处理记录", "完成的图片会保存在这里。")
                else -> EmptyPage("设置", "默认保存位置、图片信息与外观设置将在这里提供。")
            }
        }
    }
}

@Composable
private fun HomePage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("轻图", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("简单、清爽的本地图片处理工具", fontSize = 14.sp, color = Muted)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = SoftGreen
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("让图片轻一点", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text("所有处理都在设备本地完成", fontSize = 13.sp, color = DeepGreen)
                }
                Box(
                    modifier = Modifier.size(58.dp).background(Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Compress, null, tint = DeepGreen, modifier = Modifier.size(30.dp))
                }
            }
        }
        Text("图片工具", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard("图片压缩", "减小文件体积", Icons.Rounded.Compress, Modifier.weight(1f))
                ToolCard("格式转换", "JPG、PNG、WebP", Icons.Rounded.SwapHoriz, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard("调整尺寸", "修改宽度和高度", Icons.Rounded.Tune, Modifier.weight(1f))
                ToolCard("批量处理", "一次处理多张图片", Icons.Rounded.Image, Modifier.weight(1f))
            }
        }
        Text("最近处理", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("还没有处理记录", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ToolCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(116.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = DeepGreen, modifier = Modifier.size(25.dp))
            Column {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun EmptyPage(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().background(PageBg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text(description, color = Muted, fontSize = 14.sp)
    }
}
