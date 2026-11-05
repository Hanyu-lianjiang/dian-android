package fr.kdns.weiyun.dormmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, tonal: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val compact = LocalAppearance.current.compact
    top.yukonga.miuix.kmp.basic.Card(modifier = modifier.fillMaxWidth(), cornerRadius = if (compact) 24.dp else 32.dp, insideMargin = PaddingValues(0.dp), onClick = onClick,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(color = if (tonal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 18.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp), content = content)
    }
}

@Composable internal fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, icon: ImageVector? = null) {
    top.yukonga.miuix.kmp.basic.Button(onClick = onClick, modifier = modifier.heightIn(min = 52.dp), enabled = enabled, cornerRadius = 50.dp,
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(color = MaterialTheme.colorScheme.primaryContainer)) {
        val color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant else if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp), tint = color); Spacer(Modifier.width(10.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable internal fun AppTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, singleLine: Boolean = true, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None) {
    top.yukonga.miuix.kmp.basic.TextField(value = value, onValueChange = onValueChange, label = label, modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh, labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        enabled = enabled, singleLine = singleLine, keyboardOptions = keyboardOptions, visualTransformation = visualTransformation)
}

@Composable internal fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium) }

@Composable internal fun IconTile(icon: ImageVector, size: Int = 48) {
    Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape((size * .32f).dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * .51f).dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable internal fun BrandMark(modifier: Modifier = Modifier) {
    Icon(painterResource(R.drawable.ic_brand), null, modifier, tint = MaterialTheme.colorScheme.primary)
}

@Composable internal fun SettingsLink(icon: ImageVector, title: String, summary: String? = null, rightText: String? = null, onClick: () -> Unit) {
    top.yukonga.miuix.kmp.extra.SuperArrow(title = title, summary = summary, rightText = rightText,
        leftAction = { IconTile(icon, 40); Spacer(Modifier.width(14.dp)) }, onClick = onClick,
        insideMargin = PaddingValues(horizontal = 22.dp, vertical = 20.dp))
}

@Composable internal fun SettingsDivider() { HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)) }

@Composable internal fun SettingsSwitch(icon: ImageVector, title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, 40); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.layout.Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), contentAlignment = Alignment.Center) {
            top.yukonga.miuix.kmp.basic.Switch(checked = checked, onCheckedChange = onChange,
                modifier = Modifier.semantics { contentDescription = title })
        }
    }
}
