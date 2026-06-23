package com.offlinevault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinevault.ui.components.SectionCard
import com.offlinevault.ui.theme.RiskHigh
import com.offlinevault.ui.theme.RiskLow
import com.offlinevault.ui.theme.RiskMedium
import com.offlinevault.viewmodel.PasswordHealthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordHealthScreen(
    viewModel: PasswordHealthViewModel,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("密码安全体检", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            state.error != null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) { Text(state.error!!, color = MaterialTheme.colorScheme.error) }

            else -> {
                val report = state.report
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    SummaryCard(report)

                    if (report.weak.isEmpty() && report.reused.isEmpty() && report.stale.isEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        SectionCard {
                            Text(
                                if (report.total == 0) "还没有密码可供体检。"
                                else "未发现明显问题，你的密码状况良好。",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    IssueSection(
                        title = "弱密码",
                        description = "强度过低，容易被破解，建议尽快更换。",
                        color = RiskHigh,
                        entries = report.weak,
                        onOpenItem = onOpenItem
                    )
                    IssueSection(
                        title = "重复使用的密码",
                        description = "多个账号使用了相同的密码，一处泄露会牵连其它账号。",
                        color = RiskMedium,
                        entries = report.reused,
                        onOpenItem = onOpenItem
                    )
                    IssueSection(
                        title = "长期未更新",
                        description = "超过 180 天未修改，建议定期更换重要账号的密码。",
                        color = RiskMedium,
                        entries = report.stale,
                        onOpenItem = onOpenItem
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(report: PasswordHealthViewModel.HealthReport) {
    SectionCard {
        Column {
            Text("共 ${report.total} 条密码", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("弱密码", report.weak.size, RiskHigh)
                Stat("重复", report.reused.size, RiskMedium)
                Stat("久未更新", report.stale.size, RiskLow)
            }
        }
    }
}

@Composable
private fun Stat(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IssueSection(
    title: String,
    description: String,
    color: Color,
    entries: List<PasswordHealthViewModel.HealthEntry>,
    onOpenItem: (String) -> Unit
) {
    if (entries.isEmpty()) return
    Spacer(Modifier.height(20.dp))
    Text(
        "$title · ${entries.size}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    SectionCard {
        Column {
            entries.forEachIndexed { index, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenItem(entry.id) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                        if (entry.username.isNotBlank()) {
                            Text(
                                entry.username,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (index != entries.lastIndex) {
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}
