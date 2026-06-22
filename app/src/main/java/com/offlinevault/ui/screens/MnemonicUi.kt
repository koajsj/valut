package com.offlinevault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.offlinevault.ui.components.PasswordVisualField
import com.offlinevault.ui.components.PrimaryButton
import com.offlinevault.ui.components.SectionCard
import com.offlinevault.ui.components.SecureWindowEffect
import com.offlinevault.ui.components.VaultTextField

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MnemonicConfirmationContent(
    words: List<String>,
    modifier: Modifier = Modifier,
    actionLabel: String,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    SecureWindowEffect(enabled = true)
    val verifyIndexes = remember(words) { words.indices.shuffled().take(3).sorted() }
    val inputs = remember(words) { mutableStateListOf("", "", "") }
    var error by remember(words) { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        SectionCard {
            Column {
                Text("请离线抄写以下 12 个助记词", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "仅展示这一次。它只能在忘记主密码且忘记密保答案时，用于恢复数据加密密钥并重设主密码。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    words.forEachIndexed { index, word ->
                        MnemonicChip(index = index + 1, word = word)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard {
            Column {
                Text("随机校验", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "请输入下列位置对应的单词，校验通过后才会完成设置。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                verifyIndexes.forEachIndexed { localIndex, wordIndex ->
                    VaultTextField(
                        value = inputs[localIndex],
                        onValueChange = { inputs[localIndex] = it.trim().lowercase() },
                        label = "第 ${wordIndex + 1} 个单词"
                    )
                    if (localIndex != verifyIndexes.lastIndex) Spacer(Modifier.height(10.dp))
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = actionLabel,
            enabled = inputs.all { it.isNotBlank() },
            onClick = {
                val ok = verifyIndexes.withIndex().all { (i, wordIndex) ->
                    inputs[i].trim().lowercase() == words[wordIndex]
                }
                if (!ok) {
                    error = "助记词校验失败，请检查离线备份后重试"
                    return@PrimaryButton
                }
                onConfirm()
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回上一步") }
    }
}

@Composable
fun MasterPasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                PasswordVisualField(
                    value = password,
                    onValueChange = { password = it },
                    label = "当前主密码",
                    revealed = false,
                    keyboardType = KeyboardType.Password
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun MnemonicChip(index: Int, word: String) {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(index.toString(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(0.dp))
        Text("  $word", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
