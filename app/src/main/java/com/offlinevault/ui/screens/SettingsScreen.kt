package com.offlinevault.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinevault.data.backup.ImportResult
import com.offlinevault.security.PasswordStrengthChecker
import com.offlinevault.ui.components.PasswordVisualField
import com.offlinevault.ui.components.SectionCard
import com.offlinevault.ui.components.VaultTextField
import com.offlinevault.utils.FileIo
import com.offlinevault.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    biometricAvailable: Boolean,
    onBack: () -> Unit,
    onEnableBiometric: (onDone: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val autoLock by viewModel.autoLockMinutes.collectAsStateWithLifecycle()
    val screenshotBlocked by viewModel.screenshotBlocked.collectAsStateWithLifecycle()
    val clipboardSeconds by viewModel.clipboardClearSeconds.collectAsStateWithLifecycle()
    val recoveryQuestion by viewModel.recoveryQuestion.collectAsStateWithLifecycle()
    val credentialType by viewModel.credentialType.collectAsStateWithLifecycle()
    val vaults by viewModel.vaults.collectAsStateWithLifecycle()

    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }
    fun importToast(r: ImportResult) =
        toast("已导入 ${r.imported} 项，跳过 ${r.skippedDuplicates} 项，失败 ${r.failed} 项")

    // ---- Dialog state ----
    var showAutoLock by remember { mutableStateOf(false) }
    var showClipboard by remember { mutableStateOf(false) }
    var showChangeMaster by remember { mutableStateOf(false) }
    var showChangeRecovery by remember { mutableStateOf(false) }
    var showCsvWarning by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // Pending values across file picker round-trips.
    var pendingJsonBackup by remember { mutableStateOf<String?>(null) }
    var pendingCsvVaultId by remember { mutableStateOf<String?>(null) }
    var importPasswordPrompt by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var pendingImportCsv by remember { mutableStateOf<String?>(null) }
    var csvTargetPick by remember { mutableStateOf(false) }
    var askExportPassword by remember { mutableStateOf(false) }

    // ---- File launchers ----
    val saveJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingJsonBackup
        if (uri != null && json != null) {
            scope.launch {
                val ok = FileIo.writeText(context, uri, json)
                toast(if (ok) "加密备份已保存" else "无法写入文件")
            }
        }
        pendingJsonBackup = null
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val vaultId = pendingCsvVaultId
        if (uri != null && vaultId != null) {
            viewModel.buildCsv(vaultId) { csv ->
                scope.launch {
                    val ok = FileIo.writeText(context, uri, csv)
                    toast(if (ok) "CSV 已导出（包含明文密码）" else "无法写入文件")
                }
            }
        }
        pendingCsvVaultId = null
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = FileIo.readText(context, uri)
            if (text == null) {
                toast("无法读取文件，或文件超过 10 MB")
                return@launch
            }
            val name = uri.toString().lowercase()
            val looksCsv = name.endsWith(".csv") || (text.lineSequence().firstOrNull()?.contains(",") == true && !text.trimStart().startsWith("{"))
            if (looksCsv) {
                if (vaults.isEmpty()) {
                    viewModel.importCsv(text, "") { importToast(it) }
                } else {
                    pendingImportCsv = text
                    csvTargetPick = true
                }
            } else {
                pendingImportJson = text
                importPasswordPrompt = true
            }
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionHeader("安全")
            SectionCard {
                Column {
                    if (biometricAvailable) {
                        ToggleSetting(
                            title = "指纹解锁",
                            subtitle = "使用生物识别解锁",
                            checked = biometricEnabled,
                            onChange = { enabled ->
                                if (enabled) onEnableBiometric { toast("已启用指纹解锁") }
                                else { viewModel.disableBiometric(); toast("已关闭指纹解锁") }
                            }
                        )
                        SettingDivider()
                    }
                    ToggleSetting(
                        title = "禁止截屏和录屏",
                        subtitle = if (screenshotBlocked) "已开启，当前界面无法被捕获"
                        else "已关闭，允许截屏",
                        checked = screenshotBlocked,
                        onChange = {
                            viewModel.setScreenshotBlocked(it)
                            toast(
                                if (it) "已禁止截屏和录屏"
                                else "警告：当前允许截屏"
                            )
                        }
                    )
                    SettingDivider()
                    ClickSetting("自动锁定", autoLockLabel(autoLock)) { showAutoLock = true }
                    SettingDivider()
                    ClickSetting("自动清除剪贴板", "${clipboardSeconds}秒") { showClipboard = true }
                    SettingDivider()
                    ClickSetting("修改${credentialType.noun}", credentialType.displayName) { showChangeMaster = true }
                    SettingDivider()
                    ClickSetting("安全问题", recoveryQuestion.ifBlank { "未设置" }) { showChangeRecovery = true }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("备份与数据")
            SectionCard {
                Column {
                    ClickSetting("导出加密备份（JSON）", "推荐") { askExportPassword = true }
                    SettingDivider()
                    ClickSetting("导出表格（CSV）", "明文格式，请谨慎使用") { showCsvWarning = true }
                    SettingDivider()
                    ClickSetting("导入（加密备份 / 浏览器 CSV）", "") {
                        com.offlinevault.security.LockGuard.suppressNextBackground = true
                        openFileLauncher.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("关于")
            SectionCard {
                Column {
                    ClickSetting("关于离线密码库", "版本 1.0 · 完全离线") { showAbout = true }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    // ---- Auto-lock dialog ----
    if (showAutoLock) {
        ChoiceDialog(
            title = "自动锁定",
            options = listOf(0 to "立即锁定", 1 to "1 分钟后", 5 to "5 分钟后"),
            selected = autoLock,
            onSelect = { viewModel.setAutoLockMinutes(it); showAutoLock = false },
            onDismiss = { showAutoLock = false }
        )
    }
    if (showClipboard) {
        ChoiceDialog(
            title = "自动清除剪贴板",
            options = listOf(10 to "10 秒", 20 to "20 秒", 30 to "30 秒", 0 to "从不"),
            selected = clipboardSeconds,
            onSelect = { viewModel.setClipboardSeconds(it); showClipboard = false },
            onDismiss = { showClipboard = false }
        )
    }

    // ---- Change master password ----
    if (showChangeMaster) {
        ChangeMasterDialog(
            credentialType = credentialType,
            onDismiss = { showChangeMaster = false },
            onConfirm = { old, new ->
                viewModel.changeMasterPassword(old, new) { ok, msg ->
                    if (ok) { showChangeMaster = false; toast("${credentialType.noun}已修改") }
                    else toast(msg ?: "操作失败")
                }
            }
        )
    }

    // ---- Change recovery ----
    if (showChangeRecovery) {
        ChangeRecoveryDialog(
            currentQuestion = recoveryQuestion,
            onDismiss = { showChangeRecovery = false },
            onConfirm = { q, a ->
                viewModel.changeRecovery(q, a) { showChangeRecovery = false; toast("安全问题已更新") }
            }
        )
    }

    // ---- Export JSON: ask password ----
    if (askExportPassword) {
        BackupPasswordDialog(
            title = "备份密码",
            message = "该密码用于加密备份文件，恢复数据时需要输入。",
            onDismiss = { askExportPassword = false },
            onConfirm = { pw ->
                askExportPassword = false
                viewModel.buildEncryptedJsonBackup(pw) { json ->
                    pendingJsonBackup = json
                    com.offlinevault.security.LockGuard.suppressNextBackground = true
                    saveJsonLauncher.launch("offline-vault-backup.json")
                }
            }
        )
    }

    // ---- CSV warning then vault pick ----
    if (showCsvWarning) {
        AlertDialog(
            onDismissRequest = { showCsvWarning = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("导出明文 CSV？") },
            text = { Text("CSV 文件将包含明文密码，任何能打开文件的人都可以查看。请仅导出到可信且安全的位置。") },
            confirmButton = {
                TextButton(onClick = {
                    showCsvWarning = false
                    if (vaults.size == 1) {
                        pendingCsvVaultId = vaults.first().id
                        com.offlinevault.security.LockGuard.suppressNextBackground = true
                        saveCsvLauncher.launch("offline-vault-export.csv")
                    } else csvTargetPick = true
                }) { Text("我已了解，继续导出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showCsvWarning = false }) { Text("取消") } }
        )
    }

    // ---- CSV vault picker (export or import target) ----
    if (csvTargetPick) {
        val importing = pendingImportCsv != null
        VaultPickDialog(
            title = if (importing) "导入到哪个密码库？" else "导出哪个密码库？",
            vaults = vaults.map { it.id to it.name },
            onPick = { id ->
                csvTargetPick = false
                if (importing) {
                    val csv = pendingImportCsv
                    pendingImportCsv = null
                    if (csv != null) viewModel.importCsv(csv, id) { importToast(it) }
                } else {
                    pendingCsvVaultId = id
                    com.offlinevault.security.LockGuard.suppressNextBackground = true
                    saveCsvLauncher.launch("offline-vault-export.csv")
                }
            },
            onDismiss = { csvTargetPick = false; pendingImportCsv = null }
        )
    }

    // ---- Import JSON: ask password ----
    if (importPasswordPrompt) {
        BackupPasswordDialog(
            title = "恢复备份",
            message = "请输入创建此备份时使用的密码。",
            onDismiss = { importPasswordPrompt = false; pendingImportJson = null },
            onConfirm = { pw ->
                importPasswordPrompt = false
                val json = pendingImportJson
                pendingImportJson = null
                if (json != null) viewModel.importJson(json, pw) { importToast(it) }
            }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("离线密码库") },
            text = {
                Text(
                    "完全离线的密码管理器，不申请联网权限，不使用云端或服务器。" +
                        "密码库使用 AES-256-GCM 加密，数字密码或普通密码通过 PBKDF2-HMAC-SHA256 派生密钥。" +
                        "版本 1.0。"
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("关闭") } }
        )
    }
}

private fun autoLockLabel(minutes: Int): String = when (minutes) {
    0 -> "立即锁定"
    1 -> "1 分钟后"
    else -> "$minutes 分钟后"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun ToggleSetting(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ClickSetting(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingDivider() {
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == value, onClick = { onSelect(value) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun VaultPickDialog(
    title: String,
    vaults: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                vaults.forEach { (id, name) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 12.dp)
                    ) { Text(name, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ChangeMasterDialog(
    credentialType: com.offlinevault.security.CredentialType,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val strength = PasswordStrengthChecker.evaluate(new)
    val keyboardType = if (credentialType.isNumeric)
        androidx.compose.ui.text.input.KeyboardType.NumberPassword
    else androidx.compose.ui.text.input.KeyboardType.Password

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改${credentialType.noun}") },
        text = {
            Column {
                PasswordVisualField(value = old, onValueChange = { old = credentialType.sanitize(it) }, label = "当前${credentialType.noun}", revealed = false, keyboardType = keyboardType)
                Spacer(Modifier.height(10.dp))
                PasswordVisualField(value = new, onValueChange = { new = credentialType.sanitize(it) }, label = "新${credentialType.displayName}", revealed = false, keyboardType = keyboardType)
                if (credentialType == com.offlinevault.security.CredentialType.PASSWORD) {
                    Spacer(Modifier.height(8.dp))
                    com.offlinevault.ui.components.StrengthMeter(score = strength.score, level = strength.level)
                }
                Spacer(Modifier.height(10.dp))
                PasswordVisualField(value = confirm, onValueChange = { confirm = credentialType.sanitize(it) }, label = "确认${credentialType.noun}", revealed = false, keyboardType = keyboardType)
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    !credentialType.isValid(new) ->
                        if (credentialType.isNumeric) "请输入${credentialType.minLength}位数字密码"
                        else "密码至少需要${credentialType.minLength}个字符"
                    new != confirm -> "两次输入的${credentialType.noun}不一致"
                    else -> null
                }
                if (error == null) onConfirm(old, new)
            }) { Text("修改") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ChangeRecoveryDialog(
    currentQuestion: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var question by remember { mutableStateOf(currentQuestion) }
    var answer by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安全问题") },
        text = {
            Column {
                VaultTextField(value = question, onValueChange = { question = it }, label = "安全问题")
                Spacer(Modifier.height(10.dp))
                VaultTextField(value = answer, onValueChange = { answer = it }, label = "新答案")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (question.isNotBlank() && answer.isNotBlank()) onConfirm(question, answer) },
                enabled = question.isNotBlank() && answer.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    message: String,
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
                PasswordVisualField(value = password, onValueChange = { password = it }, label = "备份密码", revealed = false)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (password.isNotEmpty()) onConfirm(password) }, enabled = password.isNotEmpty()) {
                Text("继续")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
