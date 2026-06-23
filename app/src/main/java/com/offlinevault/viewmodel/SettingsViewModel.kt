package com.offlinevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.data.backup.BackupManager
import com.offlinevault.data.backup.ImportResult
import com.offlinevault.data.model.VaultEntity
import com.offlinevault.data.preferences.SecurityPreferences
import com.offlinevault.data.repository.VaultRepository
import com.offlinevault.security.KeyManager
import com.offlinevault.security.KeystoreManager
import com.offlinevault.security.UnlockResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import javax.crypto.Cipher

class SettingsViewModel(
    private val keyManager: KeyManager,
    private val prefs: SecurityPreferences,
    private val backupManager: BackupManager,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private var changeMasterJob: Job? = null
    private var changeRecoveryJob: Job? = null
    private var changeMnemonicJob: Job? = null
    private var disableMnemonicJob: Job? = null

    data class ExportPayload(
        val content: String? = null,
        val errorMessage: String? = null
    )

    val biometricEnabled: StateFlow<Boolean> =
        prefs.biometricEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val autoLockMinutes: StateFlow<Int> =
        prefs.autoLockMinutesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    val screenshotBlocked: StateFlow<Boolean> =
        prefs.screenshotBlockedFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val lockOnScreenOff: StateFlow<Boolean> =
        prefs.lockOnScreenOffFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val clipboardClearSeconds: StateFlow<Int> =
        prefs.clipboardClearSecondsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 10)

    val recoveryQuestion: StateFlow<String> =
        prefs.recoveryQuestionFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val mnemonicEnabled: StateFlow<Boolean> =
        prefs.mnemonicEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val credentialType: StateFlow<com.offlinevault.security.CredentialType> =
        prefs.credentialTypeFlow
            .map { com.offlinevault.security.CredentialType.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, com.offlinevault.security.CredentialType.PIN6)

    val vaults: StateFlow<List<VaultEntity>> =
        vaultRepository.allVaults()
            .catch { error ->
                if (error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Settings toggles --------------------------------------------------

    fun setAutoLockMinutes(value: Int, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(runAction { prefs.setAutoLockMinutes(value) })
    }

    fun setScreenshotBlocked(value: Boolean, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(runAction { prefs.setScreenshotBlocked(value) })
    }

    fun setLockOnScreenOff(value: Boolean, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(runAction { prefs.setLockOnScreenOff(value) })
    }

    fun setClipboardSeconds(value: Int, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(runAction { prefs.setClipboardClearSeconds(value) })
    }

    // ---- Master password / recovery ---------------------------------------

    fun changeMasterPassword(old: String, new: String, onResult: (Boolean, String?) -> Unit) {
        if (changeMasterJob?.isActive == true) return
        changeMasterJob = viewModelScope.launch {
            try {
                when (val result = withContext(Dispatchers.Default) { keyManager.changeMasterPassword(old, new) }) {
                    is UnlockResult.Success -> onResult(true, null)
                    is UnlockResult.WrongCredential -> onResult(false, "当前密码不正确")
                    is UnlockResult.Delayed -> onResult(
                        false,
                        "失败次数过多，请在 ${result.secondsRemaining} 秒后重试"
                    )
                    is UnlockResult.Error -> onResult(false, result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onResult(false, "无法修改密码")
            }
        }
    }

    fun changeRecovery(question: String, answer: String, onResult: (Boolean, String?) -> Unit) {
        if (changeRecoveryJob?.isActive == true) return
        changeRecoveryJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { keyManager.changeRecovery(question.trim(), answer) }
                onResult(true, null)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onResult(false, "无法更新安全问题")
            }
        }
    }

    fun updateMnemonicRecovery(masterPassword: String, mnemonicPhrase: String, onResult: (Boolean, String?) -> Unit) {
        if (changeMnemonicJob?.isActive == true) return
        changeMnemonicJob = viewModelScope.launch {
            try {
                when (val result = withContext(Dispatchers.Default) {
                    keyManager.updateMnemonicRecovery(masterPassword, mnemonicPhrase)
                }) {
                    is UnlockResult.Success -> onResult(true, null)
                    is UnlockResult.WrongCredential -> onResult(false, "当前密码不正确")
                    is UnlockResult.Delayed -> onResult(false, "失败次数过多，请在 ${result.secondsRemaining} 秒后重试")
                    is UnlockResult.Error -> onResult(false, result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onResult(false, "无法更新助记词")
            }
        }
    }

    fun disableMnemonicRecovery(masterPassword: String, onResult: (Boolean, String?) -> Unit) {
        if (disableMnemonicJob?.isActive == true) return
        disableMnemonicJob = viewModelScope.launch {
            try {
                when (val result = withContext(Dispatchers.Default) {
                    keyManager.disableMnemonicRecovery(masterPassword)
                }) {
                    is UnlockResult.Success -> onResult(true, null)
                    is UnlockResult.WrongCredential -> onResult(false, "当前密码不正确")
                    is UnlockResult.Delayed -> onResult(false, "失败次数过多，请在 ${result.secondsRemaining} 秒后重试")
                    is UnlockResult.Error -> onResult(false, result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onResult(false, "无法关闭助记词恢复")
            }
        }
    }

    // ---- Biometric ---------------------------------------------------------

    /** Builds an ENCRYPT cipher to enable biometric unlock. Null if Keystore key creation failed. */
    fun prepareEnableBiometricCipher(onReady: (Cipher?) -> Unit) {
        val cipher = try {
            KeystoreManager.deleteKey() // start fresh so enrollment changes don't haunt us
            KeystoreManager.encryptCipher()
        } catch (e: Exception) {
            null
        }
        onReady(cipher)
    }

    fun finishEnableBiometric(cipher: Cipher, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = try {
                keyManager.storeBiometricWrappedDek(cipher)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            onDone(success)
        }
    }

    fun disableBiometric(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(runAction { keyManager.disableBiometric() })
        }
    }

    // ---- Import / export ---------------------------------------------------

    fun buildEncryptedJsonBackup(password: String, onResult: (ExportPayload) -> Unit) {
        viewModelScope.launch {
            val result = try {
                ExportPayload(
                    content = withContext(Dispatchers.Default) {
                        backupManager.buildEncryptedJsonBackup(password)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ExportPayload(errorMessage = "无法创建加密备份")
            }
            onResult(result)
        }
    }

    fun buildCsv(vaultId: String, slim: Boolean, onResult: (ExportPayload) -> Unit) {
        viewModelScope.launch {
            val result = try {
                ExportPayload(
                    content = withContext(Dispatchers.Default) { backupManager.buildCsvForVault(vaultId, slim) }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ExportPayload(errorMessage = "无法导出 CSV")
            }
            onResult(result)
        }
    }

    fun importJson(content: String, password: String, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.Default) { backupManager.importJsonBackup(content, password) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ImportResult(0, 0, 0, listOf("导入失败，请检查文件后重试"))
            }
            onResult(result)
        }
    }

    fun importCsv(content: String, vaultId: String, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.Default) { backupManager.importChromeCsv(content, vaultId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ImportResult(0, 0, 0, listOf("导入失败，请检查文件后重试"))
            }
            onResult(result)
        }
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
