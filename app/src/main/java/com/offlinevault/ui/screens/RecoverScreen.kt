package com.offlinevault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.offlinevault.security.CredentialType
import com.offlinevault.security.PasswordStrengthChecker
import com.offlinevault.ui.components.IconBadge
import com.offlinevault.ui.components.PasswordVisualField
import com.offlinevault.ui.components.PrimaryButton
import com.offlinevault.ui.components.SectionCard
import com.offlinevault.ui.components.StrengthMeter
import com.offlinevault.ui.components.VaultTextField

@Composable
fun RecoverScreen(
    question: String,
    credentialType: CredentialType,
    onRecover: (answer: String, newPassword: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var answer by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val keyboardType = if (credentialType.isNumeric) KeyboardType.NumberPassword else KeyboardType.Password
    val strength = PasswordStrengthChecker.evaluate(newPassword)
    val canSubmit = answer.isNotBlank() && credentialType.isValid(newPassword) && confirm.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Spacer(Modifier.height(8.dp))
        IconBadge(icon = Icons.Filled.RestartAlt)
        Spacer(Modifier.height(16.dp))
        Text("恢复访问", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "回答安全问题后可设置新的${credentialType.noun}。回答正确即可解锁加密密钥，现有数据不会丢失。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        SectionCard {
            Column {
                Text("安全问题", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    question.ifBlank { "未设置安全问题" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(14.dp))
                VaultTextField(value = answer, onValueChange = { answer = it }, label = "你的答案")
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard {
            Column {
                Text("新${credentialType.noun}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                PasswordVisualField(
                    value = newPassword,
                    onValueChange = { newPassword = credentialType.sanitize(it) },
                    label = "新${credentialType.displayName}",
                    revealed = false,
                    keyboardType = keyboardType
                )
                if (credentialType == CredentialType.PASSWORD) {
                    Spacer(Modifier.height(10.dp))
                    StrengthMeter(score = strength.score, level = strength.level)
                }
                Spacer(Modifier.height(12.dp))
                PasswordVisualField(
                    value = confirm,
                    onValueChange = { confirm = credentialType.sanitize(it) },
                    label = "确认${credentialType.noun}",
                    revealed = false,
                    keyboardType = keyboardType
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = if (working) "正在验证…" else "恢复并解锁",
            enabled = canSubmit && !working,
            onClick = {
                error = when {
                    !credentialType.isValid(newPassword) ->
                        if (credentialType.isNumeric) "请输入${credentialType.minLength}位数字密码"
                        else "密码至少需要${credentialType.minLength}个字符"
                    newPassword != confirm -> "两次输入的${credentialType.noun}不一致"
                    else -> null
                }
                if (error == null) {
                    working = true
                    onRecover(answer, newPassword) { success, message ->
                        working = false
                        if (!success) error = message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("返回解锁") }
    }
}
