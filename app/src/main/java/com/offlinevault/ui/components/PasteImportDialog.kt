package com.offlinevault.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PasteImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    maxChars: Int = 10 * 1024 * 1024
) {
    var content by remember { mutableStateOf("") }
    val trimmed = content.trim()
    val tooLarge = content.length > maxChars

    AlertDialog(
        onDismissRequest = {
            content = ""
            onDismiss()
        },
        title = { Text("粘贴导入") },
        text = {
            Column {
                Text(
                    "当系统文件选择器不可用时，可复制 JSON 备份或浏览器 CSV 全文后粘贴到这里导入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("JSON 或 CSV 内容") },
                    singleLine = false,
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                if (tooLarge) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "内容超过 10 MB，请改用较小备份文件。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val importContent = trimmed
                    content = ""
                    onImport(importContent)
                },
                enabled = trimmed.isNotEmpty() && !tooLarge
            ) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    content = ""
                    onDismiss()
                }
            ) {
                Text("取消")
            }
        }
    )
}
