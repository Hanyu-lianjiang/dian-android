package fr.kdns.weiyun.dormmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.extra.SuperDialog

@Composable
internal fun UpdateDialog(show: MutableState<Boolean>, update: UpdateState, onDownload: () -> Unit, onInstall: () -> Unit) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .7f
    val largeText = LocalDensity.current.fontScale > 1.25f
    val status = update.message.takeUnless { it == "发现新版本 ${update.release?.version}" }.orEmpty()
    SuperDialog(show = show, onDismissRequest = { show.value = false }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stackButtons = maxWidth < 300.dp || largeText
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Outlined.SystemUpdate, 48)
                    Text("发现新版本", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    Text(update.release?.version.orEmpty(), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                if (update.downloading) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(progress = { update.downloadProgress / 100f }, Modifier.fillMaxWidth())
                        Text("正在下载 ${update.downloadProgress}%", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                } else {
                    if (status.isNotEmpty()) Text(status, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    val install = update.downloadedFile != null
                    val action = if (install) onInstall else onDownload
                    val label = if (install) "安装更新" else "下载更新"
                    val enabled = install || update.release?.apk != null
                    if (stackButtons) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppButton(label, action, Modifier.fillMaxWidth(), enabled = enabled, primary = true)
                            AppButton("稍后", { show.value = false }, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            AppButton("稍后", { show.value = false }, Modifier.weight(1f))
                            AppButton(label, action, Modifier.weight(1f), enabled = enabled, primary = true)
                        }
                    }
                }
            }
        }
    }
}
