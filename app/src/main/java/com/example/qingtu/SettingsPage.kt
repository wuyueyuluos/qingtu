package com.example.qingtu

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

private val SettingsBg = Color(0xFFF6F7F3)
private val SettingsGreen = Color(0xFF496B5B)
private val SettingsInk = Color(0xFF26332C)
private val SettingsMuted = Color(0xFF89938B)

private const val SETTINGS_PREFS = "qingtu_settings"
private const val KEY_APPEARANCE = "appearance"

@Composable
internal fun SettingsPage() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE) }
    var appearance by remember { mutableStateOf(prefs.getString(KEY_APPEARANCE, "跟随系统") ?: "跟随系统") }

    Column(
        Modifier.fillMaxSize().background(SettingsBg).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("设置", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SettingsInk)
        Text("根据你的使用习惯调整轻图", fontSize = 13.sp, color = SettingsMuted)

        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Palette, null, tint = SettingsGreen)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("外观", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SettingsInk)
                    Text("选择你喜欢的显示方式", fontSize = 12.sp, color = SettingsMuted)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("跟随系统", "日间模式", "夜间模式").forEach { option ->
                    FilterChip(
                        selected = appearance == option,
                        onClick = {
                            appearance = option
                            prefs.edit().putString(KEY_APPEARANCE, option).apply()
                        },
                        label = { Text(option, fontSize = 12.sp) }
                    )
                }
            }
            Text("外观偏好已保存；全局深浅色适配将在后续步骤完成。", fontSize = 11.sp, color = SettingsMuted)
        }

        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null, tint = SettingsGreen)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("保存位置", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SettingsInk)
                    Text("处理后的图片会保存到这里", fontSize = 12.sp, color = SettingsMuted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFFF1F4F0), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Pictures/轻图/", modifier = Modifier.padding(14.dp), color = SettingsGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text("目前统一使用此目录，暂不支持自定义。", fontSize = 11.sp, color = SettingsMuted)
        }

        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = SettingsGreen)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("图片信息", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SettingsInk)
                    Text("EXIF 信息保留选项将在下一步加入", fontSize = 12.sp, color = SettingsMuted)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}