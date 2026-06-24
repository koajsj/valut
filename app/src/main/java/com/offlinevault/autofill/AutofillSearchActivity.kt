package com.offlinevault.autofill

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.offlinevault.OfflineVaultApp
import com.offlinevault.R
import com.offlinevault.data.repository.DecryptedPassword
import com.offlinevault.security.SessionManager
import com.offlinevault.ui.components.SectionCard
import com.offlinevault.ui.theme.OfflineVaultTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutofillSearchActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_USERNAME_ID = "autofill_username_id"
        private const val EXTRA_PASSWORD_ID = "autofill_password_id"
        private const val EXTRA_WEB_DOMAIN = "autofill_web_domain"
        private const val EXTRA_PACKAGE_NAME = "autofill_package_name"

        fun buildIntent(
            context: Context,
            usernameId: android.view.autofill.AutofillId?,
            passwordId: android.view.autofill.AutofillId?,
            webDomain: String?,
            packageName: String?
        ): Intent = Intent(context, AutofillSearchActivity::class.java).apply {
            putExtra(EXTRA_WEB_DOMAIN, webDomain)
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_USERNAME_ID, usernameId)
            putExtra(EXTRA_PASSWORD_ID, passwordId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val usernameId = intent.autofillIdExtra(EXTRA_USERNAME_ID)
        val passwordId = intent.autofillIdExtra(EXTRA_PASSWORD_ID)
        val webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val targetLabel = OriginMatcher.targetLabel(webDomain, packageName)

        if (!SessionManager.isUnlocked || (usernameId == null && passwordId == null)) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val app = application as OfflineVaultApp
        val loadingState = mutableStateOf(true)
        val errorState = mutableStateOf<String?>(null)
        val itemsState = mutableStateOf<List<DecryptedPassword>>(emptyList())

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val all = app.container.passwordRepository.allDecryptedSkippingCorrupt()
                    val ranked = all.sortedByDescending { candidate ->
                        when {
                            !webDomain.isNullOrBlank() && OriginMatcher.matches(candidate.url, webDomain) -> 2
                            !packageName.isNullOrBlank() && OriginMatcher.matchesApp(candidate.url, packageName) -> 2
                            else -> 0
                        }
                    }
                    ranked
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            loadingState.value = false
            if (result == null) {
                errorState.value = "无法读取密码库"
            } else {
                itemsState.value = result
            }
        }

        setContent {
            OfflineVaultTheme {
                AutofillSearchScreen(
                    targetLabel = targetLabel,
                    loading = loadingState.value,
                    error = errorState.value,
                    allItems = itemsState.value,
                    onBack = { finish() },
                    onPick = { item ->
                        if (!SessionManager.isUnlocked) {
                            setResult(RESULT_CANCELED)
                            finish()
                            return@AutofillSearchScreen
                        }
                        val dataset = try {
                            buildResultDataset(item, usernameId, passwordId, targetLabel)
                        } catch (_: Exception) {
                            setResult(RESULT_CANCELED)
                            finish()
                            return@AutofillSearchScreen
                        }
                        val resultIntent = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                )
            }
        }
    }

    private fun buildResultDataset(
        item: DecryptedPassword,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        targetLabel: String
    ): Dataset {
        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, item.title.ifEmpty { "离线密码库" })
            setTextViewText(
                R.id.autofill_subtitle,
                listOf(item.username.ifEmpty { item.url }, "当前：$targetLabel")
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            )
        }
        return Dataset.Builder(presentation).apply {
            usernameId?.let { setValue(it, AutofillValue.forText(item.username)) }
            passwordId?.let { setValue(it, AutofillValue.forText(item.password)) }
        }.build()
    }

    @Suppress("DEPRECATION")
    private fun Intent.autofillIdExtra(key: String): AutofillId? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(key, AutofillId::class.java)
            } else {
                getParcelableExtra(key)
            }
        }.getOrNull()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AutofillSearchScreen(
    targetLabel: String,
    loading: Boolean,
    error: String?,
    allItems: List<DecryptedPassword>,
    onBack: () -> Unit,
    onPick: (DecryptedPassword) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, allItems) {
        val keyword = query.trim()
        if (keyword.isBlank()) allItems
        else allItems.filter { item ->
            item.title.contains(keyword, ignoreCase = true) ||
                item.username.contains(keyword, ignoreCase = true) ||
                item.url.contains(keyword, ignoreCase = true) ||
                item.note.contains(keyword, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) },
        topBar = {
            TopAppBar(
                title = { Text("自动填充手动搜索", fontWeight = FontWeight.Bold) },
                actions = { TextButton(onClick = onBack) { Text("关闭") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "当前目标：$targetLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索标题、用户名或网址") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.height(12.dp))
            when {
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }

                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有匹配的密码项", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        SectionCard {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(item) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(item.title.ifEmpty { "未命名项目" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(item.username.ifEmpty { item.url }, item.url.takeIf { item.url.isNotBlank() && item.url != item.username }.orEmpty())
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
