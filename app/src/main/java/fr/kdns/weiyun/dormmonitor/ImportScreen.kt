package fr.kdns.weiyun.dormmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.extra.SuperArrow

@Composable internal fun ImportPage(vm: MonitorModel) {
    val labels = listOf("校区", "楼栋", "宿舍")
    val icons = listOf(Icons.Outlined.School, Icons.Outlined.Apartment, Icons.Outlined.MeetingRoom)
    var query by remember(vm.importStep) { mutableStateOf("") }
    val scroll = rememberLazyListState()
    LaunchedEffect(vm.importStep) { scroll.scrollToItem(0) }
    val confirmation = vm.canImport && vm.importStep == 3
    val choices = remember(vm.importOptions, query) {
        vm.importOptions.filter { it.optString("name").contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll,
            verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item { PageHeading("找到你的那一间", "添加宿舍") }
            if (!vm.canImport) {
                item { AppCard { IconTile(Icons.Outlined.Lock); Text("当前账号暂不能添加宿舍，请联系管理员") } }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        labels.forEachIndexed { index, label ->
                            Surface(modifier = Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                color = if (index <= vm.importStep) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(if (index < vm.importStep) Icons.Outlined.CheckCircleOutline else icons[index], null, tint = MaterialTheme.colorScheme.primary)
                                    Text(label, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                if (vm.importChoices.isNotEmpty()) item {
                    top.yukonga.miuix.kmp.basic.Card(cornerRadius = 24.dp) {
                        vm.importChoices.forEachIndexed { index, choice ->
                            SuperArrow(title = labels[index], rightText = choice.optString("name"), enabled = !vm.busy,
                                onClick = { vm.chooseStep(index) })
                        }
                    }
                }
                if (confirmation) {
                    item {
                        AppCard(tonal = true) {
                            IconTile(Icons.Outlined.Cottage, 64)
                            SectionTitle(vm.importChoices.last().optString("name"))
                            Text(vm.importChoices.take(2).joinToString(" · ") { it.optString("name") }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("选择${labels[vm.importStep]}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (vm.importLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                    if (vm.importError.isNotEmpty()) item {
                        Text(vm.importError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        AppButton("重试", vm::retryImportOptions, enabled = !vm.importLoading, icon = Icons.Outlined.Refresh)
                    }
                    if (vm.importOptions.size > 8) item { AppTextField(query, { query = it }, "搜索${labels[vm.importStep]}") }
                    if (!vm.busy && !vm.importLoading && vm.importError.isEmpty() && vm.importOptions.isEmpty()) item {
                        AppCard {
                            Text(if (vm.error.isEmpty()) "暂无可选${labels[vm.importStep]}" else "暂时无法读取选项")
                            AppButton("重试", vm::retryImportOptions, icon = Icons.Outlined.Refresh)
                        }
                    }
                    items(choices, key = { it.optString("id") }) { option ->
                        top.yukonga.miuix.kmp.basic.Card(cornerRadius = 24.dp) {
                            SuperArrow(title = option.optString("name"),
                                leftAction = { Icon(icons[vm.importStep], null, Modifier.padding(end = 16.dp), tint = MaterialTheme.colorScheme.primary) },
                                enabled = !vm.busy && !vm.importLoading, onClick = { vm.chooseOption(option) })
                        }
                    }
                    if (choices.isEmpty() && vm.importOptions.isNotEmpty()) item { Text("没有匹配的选项") }
                }
            }
        }
        // Reserve the action's height independently of the scrollable summary.
        if (confirmation) Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            AppButton("确认添加", vm::importSelected, Modifier.fillMaxWidth(), enabled = !vm.busy, primary = true, icon = Icons.Outlined.Add)
        }
    }
}
