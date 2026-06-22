package com.offlinevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.data.backup.BackupManager
import com.offlinevault.data.backup.ImportResult
import com.offlinevault.data.model.PasswordEntity
import com.offlinevault.data.repository.PasswordRepository
import com.offlinevault.data.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordListViewModel(
    private val passwordRepository: PasswordRepository,
    private val vaultRepository: VaultRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    private val vaultId = MutableStateFlow("")
    private val query = MutableStateFlow("")
    private val tagFilter = MutableStateFlow<String?>(null)

    val searchQuery: StateFlow<String> = query.asStateFlow()
    val activeTag: StateFlow<String?> = tagFilter.asStateFlow()

    private val _vaultName = MutableStateFlow("")
    val vaultName: StateFlow<String> = _vaultName.asStateFlow()

    val items: StateFlow<List<PasswordEntity>> =
        combine(vaultId, query, tagFilter) { id, q, tag -> Triple(id, q, tag) }
            .flatMapLatest { (id, q, tag) ->
                when {
                    id.isEmpty() -> kotlinx.coroutines.flow.flowOf(emptyList())
                    tag != null -> passwordRepository.byTag(id, tag)
                    q.isNotBlank() -> passwordRepository.search(id, q.trim())
                    else -> passwordRepository.passwordsByVault(id)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All distinct tags present in the current vault, for the filter row. */
    val tags: StateFlow<List<String>> =
        vaultId.flatMapLatest { id ->
            if (id.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
            else passwordRepository.passwordsByVault(id)
        }.map { list ->
            list.flatMap { it.tags.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load(id: String) {
        vaultId.value = id
        viewModelScope.launch {
            _vaultName.value = vaultRepository.getById(id)?.name ?: "密码库"
        }
    }

    /** Opens the single default vault, creating it on first run. Used as the app's home. */
    fun loadDefault() {
        viewModelScope.launch {
            val vault = vaultRepository.ensureDefault()
            vaultId.value = vault.id
            _vaultName.value = vault.name
        }
    }

    /** Current vault id, or empty if not loaded yet. */
    fun currentVaultId(): String = vaultId.value

    fun importJson(content: String, password: String, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            onResult(withContext(Dispatchers.Default) { backupManager.importJsonBackup(content, password) })
        }
    }

    fun importCsv(content: String, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val target = vaultId.value.ifEmpty { vaultRepository.ensureDefault().id }
            onResult(withContext(Dispatchers.Default) { backupManager.importChromeCsv(content, target) })
        }
    }

    fun setQuery(value: String) { query.value = value }

    fun toggleTag(tag: String) {
        tagFilter.value = if (tagFilter.value == tag) null else tag
    }

    fun clearFilters() {
        query.value = ""
        tagFilter.value = null
    }
}
