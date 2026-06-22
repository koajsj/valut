package com.offlinevault.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinevault.data.backup.ImportResult
import com.offlinevault.data.model.PasswordEntity
import com.offlinevault.security.LockGuard
import com.offlinevault.security.PasswordStrengthChecker
import com.offlinevault.ui.components.CardShape
import com.offlinevault.ui.components.PasswordVisualField
import com.offlinevault.ui.components.RiskChip
import com.offlinevault.utils.FileIo
import com.offlinevault.viewmodel.PasswordListViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordListScreen(
    viewModel: PasswordListViewModel,
    onOpenItem: (String) -> Unit,
    onAddItem: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val activeTag by viewModel.activeTag.collectAsStateWithLifecycle()
    val vaultName by viewModel.vaultName.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var importJsonPasswordPrompt by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }
    fun importToast(r: ImportResult) =
        toast(r.errors.firstOrNull()
            ?: "已导入 ${r.imported} 项，跳过 ${r.skippedDuplicates} 项，失败 ${r.failed} 项")

    androidx.compose.runtime.LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    fun handlePickedUri(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val text = FileIo.readText(context, uri)
            if (text == null) {
                toast("无法读取文件，或文件超过 10 MB")
                return@launch
            }
            val name = uri.toString().lowercase()
            val looksCsv = name.endsWith(".csv") ||
                (text.lineSequence().firstOrNull()?.contains(",") == true && !text.trimStart().startsWith("{"))
            if (looksCsv) {
                viewModel.importCsv(text) { importToast(it) }
            } else {
                pendingImportJson = text
                importJsonPasswordPrompt = true
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> handlePickedUri(uri) }

    // Fallback for devices whose system has no ACTION_OPEN_DOCUMENT handler.
    val getContentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> handlePickedUri(uri) }

    fun launchImport() {
        LockGuard.suppressNextBackground = true
        try {
            openDocumentLauncher.launch(
                arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "*/*")
            )
        } catch (_: RuntimeException) {
            try {
                getContentLauncher.launch("*/*")
            } catch (_: RuntimeException) {
                LockGuard.suppressNextBackground = false
                toast("未找到可用的文件管理器，无法选择文件")
            }
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(vaultName.ifEmpty { "我的密码" }, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { launchImport() }) {
                        Icon(Icons.Filled.Download, contentDescription = "导入密码")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddItem,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加密码")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("搜索标题、用户名或网址…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tags) { tag ->
                        FilterChip(
                            selected = activeTag == tag,
                            onClick = { viewModel.toggleTag(tag) },
                            label = { Text(tag) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank() && activeTag == null) "暂无密码，点击 + 添加，或用右上角导入。"
                        else "没有匹配结果",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        PasswordCard(item = item, onClick = { onOpenItem(item.id) })
                    }
                }
            }
        }
    }

    if (importJsonPasswordPrompt) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importJsonPasswordPrompt = false; pendingImportJson = null },
            title = { Text("恢复备份") },
            text = {
                Column {
                    Text(
                        "请输入创建此备份时使用的密码。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    PasswordVisualField(value = password, onValueChange = { password = it }, label = "备份密码", revealed = false)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        importJsonPasswordPrompt = false
                        val json = pendingImportJson
                        pendingImportJson = null
                        if (json != null) viewModel.importJson(json, password) { importToast(it) }
                    },
                    enabled = password.isNotEmpty()
                ) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { importJsonPasswordPrompt = false; pendingImportJson = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PasswordCard(item: PasswordEntity, onClick: () -> Unit) {
    val level = PasswordStrengthChecker.levelFor(item.strengthScore)
    val tagList = item.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "cardPress")

    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(10.dp, CardShape, clip = false, spotColor = Color.Black, ambientColor = Color.Black)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CardShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                item.title.take(1).uppercase().ifEmpty { "?" },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title.ifEmpty { "无标题" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (item.username.isNotEmpty()) {
                Text(item.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                "••••••••••",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            if (tagList.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tagList.take(3).forEach { tag ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        RiskChip(level = level)
    }
}
