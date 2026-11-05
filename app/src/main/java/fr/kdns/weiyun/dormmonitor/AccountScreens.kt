package fr.kdns.weiyun.dormmonitor

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

@Composable internal fun AccountSettings(vm: MonitorModel, appearance: () -> Unit, bottomInset: Dp = 0.dp) {
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = bottomInset), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeading("按你的习惯", "设置")
        AppCard(tonal = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.PersonOutline, 56); Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(vm.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                    Text(if (vm.role == "admin") "管理员" else "已登录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        top.yukonga.miuix.kmp.basic.Card(cornerRadius = 32.dp) {
            SettingsLink(Icons.Outlined.Palette, "主题与外观", "配色、明暗与显示偏好", onClick = appearance); SettingsDivider()
            SettingsLink(Icons.Outlined.BatteryAlert, "低电量提醒", "个人提醒阈值") { vm.openThreshold() }; SettingsDivider()
            SettingsLink(Icons.Outlined.Widgets, "桌面小组件", "宿舍与刷新设置") { vm.page = "widget" }
        }
        if (vm.role == "admin") top.yukonga.miuix.kmp.basic.Card(cornerRadius = 32.dp) {
            SettingsLink(Icons.Outlined.Key, "学校查询授权") { vm.page = "authorization" }; SettingsDivider()
            SettingsLink(Icons.Outlined.AdminPanelSettings, "管理后台") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://bill.weiyun.kdns.fr/admin"))) }
        }
        top.yukonga.miuix.kmp.basic.Card(cornerRadius = 32.dp) {
            SettingsLink(Icons.Outlined.Info, "关于", rightText = BuildConfig.VERSION_NAME) { vm.page = "about" }
        }
        AppButton("退出登录", vm::logout, Modifier.fillMaxWidth(), enabled = !vm.busy, icon = Icons.Outlined.Logout)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable internal fun ThresholdPage(vm: MonitorModel) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeading("留一点余量")
        AppCard {
            IconTile(Icons.Outlined.BatteryAlert, 64)
            Text("低于设定电量时标记提醒。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (vm.thresholdLoading) Text("正在读取提醒设置…", style = MaterialTheme.typography.bodySmall)
            if (vm.thresholdError.isNotEmpty()) {
                Text(vm.thresholdError, color = MaterialTheme.colorScheme.error)
                AppButton("重试", { vm.openThreshold(force = true) }, enabled = !vm.thresholdLoading)
            }
            AppTextField(vm.threshold, { vm.threshold = it }, "阈值（度）", enabled = !vm.thresholdLoading && !vm.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            AppButton("保存", vm::saveThreshold, Modifier.fillMaxWidth(), enabled = !vm.busy && !vm.thresholdLoading && vm.thresholdError.isEmpty() && vm.threshold.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..100000.0 } == true, primary = true, icon = Icons.Outlined.Check)
        }
    }
}

@Composable internal fun AuthorizationPage(json: String, update: (String) -> Unit, busy: Boolean, save: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeading("学校查询授权")
        AppCard {
            IconTile(Icons.Outlined.Key, 64)
            AppTextField(json, update, "授权 JSON", singleLine = false, modifier = Modifier.heightIn(min = 140.dp, max = 240.dp))
            AppButton("更新授权", save, Modifier.fillMaxWidth(), enabled = !busy && json.isNotBlank(), primary = true, icon = Icons.Outlined.Sync)
        }
    }
}

@Composable internal fun AuthPage(vm: MonitorModel, register: Boolean) {
    var username by rememberSaveable(register) { mutableStateOf("") }
    var password by remember(register) { mutableStateOf("") }
    var confirmation by remember(register) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember(register) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (!register) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("电量，心中有数", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
                Text("登录后查看你的宿舍", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else PageHeading("创建你的账号")
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (register) Icons.Outlined.PersonAddAlt else Icons.Outlined.Login, null, tint = MaterialTheme.colorScheme.primary); Text(if (register) "注册" else "登录", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleLarge) }
            AppTextField(username, { username = it; error = ""; vm.clearError() }, "用户名", enabled = !vm.busy)
            AppTextField(password, { password = it; error = ""; vm.clearError() }, "密码", enabled = !vm.busy,
                visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (register) AppTextField(confirmation, { confirmation = it; error = "" }, "确认密码", enabled = !vm.busy,
                visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("显示密码", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "隐藏密码" else "显示密码", tint = MaterialTheme.colorScheme.primary) }
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            AppButton(if (register) "注册" else "登录", {
                error = when { username.isBlank() || password.isEmpty() -> "请输入用户名和密码"; register && !username.trim().matches(Regex("^[A-Za-z0-9_]{3,40}$")) -> "用户名须为 3–40 位字母、数字或下划线"; register && password.length !in 10..200 -> "密码须为 10–200 位"; register && password != confirmation -> "两次输入的密码不一致"; else -> "" }
                if (error.isEmpty()) vm.auth(username.trim(), password, register)
            }, Modifier.fillMaxWidth(), enabled = !vm.busy, primary = true, icon = Icons.Outlined.ArrowForward)
            if (!register) AppButton("创建账号", { vm.clearError(); vm.page = "register" }, Modifier.fillMaxWidth(), enabled = !vm.busy, icon = Icons.Outlined.PersonAddAlt)
        }
    }
}
