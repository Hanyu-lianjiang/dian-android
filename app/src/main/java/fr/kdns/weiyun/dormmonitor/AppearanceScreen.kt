package fr.kdns.weiyun.dormmonitor

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable internal fun AppearanceScreen() {
    val p = LocalAppearance.current
    val save = LocalSaveAppearance.current
    var custom by remember(p.custom) { mutableStateOf(p.custom) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeading("随心配色", "外观")
        AppCard(tonal = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(Modifier.size(48.dp)); Spacer(Modifier.width(16.dp))
                Column { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall); Text("主题预览", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.WbSunny, null, tint = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary).forEach { color -> Box(Modifier.size(32.dp).background(color, CircleShape)) }
            }
        }
        SectionTitle("明暗模式")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("system", "自动", Icons.Outlined.BrightnessAuto), Triple("light", "浅色", Icons.Outlined.LightMode), Triple("dark", "深色", Icons.Outlined.DarkMode)).forEach { (id, label, icon) ->
                ThemeChoice(label, icon, p.mode == id, Modifier.weight(1f)) { save(p.copy(mode = id)) }
            }
        }
        SectionTitle("主题色")
        AppCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                paletteOptions.take(3).forEach { c -> ColorChoice(c, p.palette == c.id) { save(p.copy(palette = c.id)) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                paletteOptions.drop(3).forEach { c -> ColorChoice(c, p.palette == c.id) { save(p.copy(palette = c.id)) } }
            }
            Text(if (Build.VERSION.SDK_INT >= 31) "壁纸配色随系统主题变化。" else "当前系统使用雾蓝配色。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextField(custom, { custom = it.removePrefix("#").take(6) }, "自选颜色 · HEX")
            AppButton("应用自选颜色", { save(p.copy(palette = "custom", custom = custom.uppercase())) }, Modifier.fillMaxWidth(),
                enabled = custom.matches(Regex("[a-fA-F0-9]{6}")), primary = p.palette == "custom", icon = Icons.Outlined.Colorize)
        }
        SectionTitle("导航与卡片")
        top.yukonga.miuix.kmp.basic.Card(cornerRadius = 32.dp) {
            SettingsSwitch(Icons.Outlined.Contrast, "纯黑背景", "深色模式下使用纯黑底色", p.black) { save(p.copy(black = it)) }; SettingsDivider()
            SettingsSwitch(Icons.Outlined.ViewAgenda, "悬浮底栏", "关闭后使用贴边底栏", p.floating) { save(p.copy(floating = it)) }; SettingsDivider()
            SettingsSwitch(Icons.Outlined.BlurOn, "液态玻璃", "通透底栏与柔和高光", p.glass) { save(p.copy(glass = it)) }; SettingsDivider()
            SettingsSwitch(Icons.Outlined.AddCircleOutline, "添加入口", "在底栏显示添加宿舍", p.importTab) { save(p.copy(importTab = it)) }; SettingsDivider()
            SettingsSwitch(Icons.Outlined.DensitySmall, "紧凑卡片", "减少卡片留白", p.compact) { save(p.copy(compact = it)) }; SettingsDivider()
            SettingsSwitch(Icons.Outlined.ShowChart, "趋势缩略图", "在宿舍卡片显示近期趋势", p.miniTrend) { save(p.copy(miniTrend = it)) }
        }
        AppButton("恢复默认外观", { save(Appearance()) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.RestartAlt)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun ColorChoice(option: PaletteOption, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.widthIn(min = 72.dp).clickable(onClick = onClick).semantics { role = Role.RadioButton; this.selected = selected; contentDescription = option.name + "配色" }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(48.dp).border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).padding(5.dp).background(option.color.copy(alpha = .7f), CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(23.dp))
            else if (option.id == "wallpaper") Icon(Icons.Outlined.Wallpaper, null, tint = Color.White, modifier = Modifier.size(23.dp))
        }
        Text(option.name, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun ThemeChoice(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.semantics { role = Role.RadioButton; this.selected = selected }, shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable internal fun PageHeading(title: String, eyebrow: String? = null) {
    Column(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (eyebrow != null) Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Normal)
    }
}
