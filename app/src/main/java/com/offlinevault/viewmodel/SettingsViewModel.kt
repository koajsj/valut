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
import javax.crypto.Cipher

class SettingsViewModel(
    private val keyManager: KeyManager,
    private val prefs: SecurityPreferences,
    private val backupManager: BackupManager,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    val biometricEnabled: StateFlow<Boolean> =
        prefs.biometricEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val autoLockMinutes: StateFlow<Int> =
        prefs.autoLockMinutesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    val screenshotBlocked: StateFlow<Boolean> =
        prefs.screenshotBlockedFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val clipboardClearSeconds: StateFlow<Int> =
        prefs.clipboardClearSecondsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 10)

    val recoveryQuestion: StateFlow<String> =
        prefs.recoveryQuestionFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

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

    fun setAutoLockMinutes(value: Int) = viewModelScope.launch { prefs.setAutoLockMinutes(value) }
    fun setScreenshotBlocked(value: Boolean) = viewModelScope.launch { prefs.setScreenshotBlocked(value) }
    fun setClipboardSeconds(value: Int) = viewModelScope.launch { prefs.setClipboardClearSeconds(value) }

    // ---- Master password / recovery ---------------------------------------

    fun changeMasterPassword(old: String, new: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.Default) { keyManager.changeMasterPassword(old, new) }) {
                is UnlockResult.Success -> onResult(true, null)
                is UnlockResult.WrongCredential -> onResult(false, "当前密码不正确")
                is UnlockResult.Delayed -> onResult(
                    false,
                    "失败次数过多，请在 ${result.secondsRemaining} 秒后重试"
                )
                else -> onResult(false, "无法修改密码")
            }
        }
    }

    fun changeRecovery(question: String, answer: String, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { keyManager.changeRecovery(question.trim(), answer) }
            onDone()
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

    fun finishEnableBiometric(cipher: Cipher, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                keyManager.storeBiometricWrappedDek(cipher)
            } finally {
                onDone()
            }
        }
    }

    fun disableBiometric() {
        viewModelScope.launch { keyManager.disableBiometric() }
    }

    // ---- Import / export ---------------------------------------------------

    fun buildEncryptedJsonBackup(password: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.Default) { backupManager.buildEncryptedJsonBackup(password) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@launch
            }
            onResult(result)
        }
    }

    fun buildCsv(vaultId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.Default) { backupManager.buildCsvForVault(vaultId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@launch
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
}
