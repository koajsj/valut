package com.offlinevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.data.repository.DecryptedPassword
import com.offlinevault.data.repository.PasswordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordDetailViewModel(
    private val passwordRepository: PasswordRepository
) : ViewModel() {

    private val id = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Observes the underlying row so the screen always reflects the latest edit, and collapses to
     * null if the item is deleted. Decryption happens on each emission using the active session key.
     */
    val item: StateFlow<DecryptedPassword?> =
        id.flatMapLatest { pid ->
            if (pid == null) flowOf(null)
            else passwordRepository.observe(pid).map { entity ->
                entity?.let {
                    try {
                        passwordRepository.decrypt(it)
                    } catch (e: Exception) {
                        _error.value = "这条凭据已损坏，无法解密。"
                        null
                    }
                }
            }.catch {
                _error.value = "这条凭据已损坏，无法解密。"
                emit(null)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun load(passwordId: String) {
        _error.value = null
        id.value = passwordId
    }

    fun delete(onDone: () -> Unit) {
        val pid = id.value ?: return onDone()
        viewModelScope.launch {
            passwordRepository.deleteById(pid)
            onDone()
        }
    }
}
