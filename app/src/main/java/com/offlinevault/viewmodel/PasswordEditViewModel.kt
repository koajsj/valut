package com.offlinevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.data.repository.PasswordRepository
import com.offlinevault.data.repository.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditFormState(
    val id: String? = null,
    val vaultId: String = "",
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val tags: String = "",
    val note: String = "",
    val isEditing: Boolean = false
)

class PasswordEditViewModel(
    private val passwordRepository: PasswordRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val _form = MutableStateFlow(EditFormState())
    val form: StateFlow<EditFormState> = _form.asStateFlow()

    private var loadedKey: String? = null

    /** Loads either a new (vaultId set) or existing (passwordId set) form exactly once per target. */
    fun initialize(vaultId: String, passwordId: String?) {
        val key = "$vaultId|$passwordId"
        if (loadedKey == key) return
        loadedKey = key

        if (passwordId == null) {
            _form.value = EditFormState(vaultId = vaultId, isEditing = false)
        } else {
            viewModelScope.launch {
                val item = passwordRepository.getDecrypted(passwordId)
                if (item != null) {
                    _form.value = EditFormState(
                        id = item.id,
                        vaultId = item.vaultId,
                        title = item.title,
                        username = item.username,
                        password = item.password,
                        url = item.url,
                        tags = item.tags.joinToString(", "),
                        note = item.note,
                        isEditing = true
                    )
                }
            }
        }
    }

    fun update(transform: (EditFormState) -> EditFormState) {
        _form.value = transform(_form.value)
    }

    fun setPassword(value: String) = update { it.copy(password = value) }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.title.isBlank() && f.username.isBlank()) {
            onDone()
            return
        }
        viewModelScope.launch {
            val tags = f.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            passwordRepository.save(
                id = f.id,
                vaultId = f.vaultId,
                title = f.title.ifBlank { f.username.ifBlank { "无标题" } },
                username = f.username,
                password = f.password,
                url = f.url,
                tags = tags,
                note = f.note
            )
            vaultRepository.touch(f.vaultId)
            onDone()
        }
    }
}
