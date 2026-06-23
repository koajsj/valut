package com.offlinevault.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlinevault.data.backup.ImportConflictStrategy
import com.offlinevault.data.backup.ImportPreview

@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    onDismiss: () -> Unit,
    onConfirm: (ImportConflictStrategy) -> Unit
) {
    var strategy by remember(preview) { mutableStateOf(ImportConflictStrategy.SKIP) }
    val validItems = (preview.totalItems - preview.invalidItems).coerceAtLeast(0)
    val summary = when (strategy) {
        ImportConflictStrategy.SKIP ->
            "将新增 ${preview.newItems} 项，跳过 ${preview.duplicateItems} 项重复数据"
        ImportConflictStrategy.OVERWRITE ->
            "将新增 ${preview.newItems} 项，覆盖 ${preview.duplicateItems} 项已有数据"
        ImportConflictStrategy.KEEP_BOTH ->
            "将导入 $validItems 项，并保留重复项"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入预览") },
        text = {
            Column {
                Text(preview.formatLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("目标：${preview.targetLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    "共 ${preview.totalItems} 项，新项目 ${preview.newItems} 项，重复 ${preview.duplicateItems} 项，无效 ${preview.invalidItems} 项。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                if (preview.sampleTitles.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "示例：${preview.sampleTitles.joinToString("、")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (preview.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    preview.warnings.forEach { warning ->
                        Text(warning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(12.dp))
                StrategyOption(
                    title = "跳过重复",
                    subtitle = "保留现有项目，仅导入新项目",
                    selected = strategy == ImportConflictStrategy.SKIP,
                    onSelect = { strategy = ImportConflictStrategy.SKIP }
                )
                StrategyOption(
                    title = "覆盖已有",
                    subtitle = "用导入内容更新同网址或同标题且同用户名的现有项目",
                    selected = strategy == ImportConflictStrategy.OVERWRITE,
                    onSelect = { strategy = ImportConflictStrategy.OVERWRITE }
                )
                StrategyOption(
                    title = "保留两份",
                    subtitle = "重复项目也会再导入一份",
                    selected = strategy == ImportConflictStrategy.KEEP_BOTH,
                    onSelect = { strategy = ImportConflictStrategy.KEEP_BOTH }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(strategy) }, enabled = validItems > 0) {
                Text("开始导入")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun StrategyOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
