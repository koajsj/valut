package com.offlinevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.data.model.PasswordEntity
import com.offlinevault.data.repository.PasswordRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the recycle-bin screen: lists trashed credentials and restores / erases them. */
class TrashViewModel(
    private val passwordRepository: PasswordRepository
) : ViewModel() {

    val items: StateFlow<List<PasswordEntity>> =
        passwordRepository.trashed()
            .flowOn(Dispatchers.Default)
            .catch { error ->
                if (error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(id: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(runAction { passwordRepository.restore(id) }) }
    }

    fun deleteForever(id: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(runAction { passwordRepository.permanentlyDelete(id) }) }
    }

    fun emptyTrash(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(runAction { passwordRepository.emptyTrash() }) }
    }

    private suspend fun runAction(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
