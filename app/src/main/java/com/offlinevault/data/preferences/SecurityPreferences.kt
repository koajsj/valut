package com.offlinevault.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.offlinevault.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "offline_vault_secure_prefs")

/** App level (non-secret-bearing key material + user settings) persistence backed by DataStore. */
class SecurityPreferences(private val context: Context) {

    private object Keys {
        val MASTER_SALT = stringPreferencesKey("master_salt")
        val RECOVERY_SALT = stringPreferencesKey("recovery_salt")
        val MASTER_WRAPPED_DEK = stringPreferencesKey("master_wrapped_dek")
        val RECOVERY_WRAPPED_DEK = stringPreferencesKey("recovery_wrapped_dek")
        val RECOVERY_QUESTION = stringPreferencesKey("recovery_question")
        val MNEMONIC_SALT = stringPreferencesKey("mnemonic_salt")
        val MNEMONIC_WRAPPED_DEK = stringPreferencesKey("mnemonic_wrapped_dek")
        val MNEMONIC_VERIFIER_HASH = stringPreferencesKey("mnemonic_verifier_hash")

        // PBKDF2 iteration counts the master / recovery keys were derived with. Persisted so the
        // default can be raised over time without locking existing vaults out. Absent => legacy.
        val MASTER_ITERATIONS = intPreferencesKey("master_iterations")
        val RECOVERY_ITERATIONS = intPreferencesKey("recovery_iterations")

        val CREDENTIAL_TYPE = stringPreferencesKey("credential_type")

        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val BIOMETRIC_WRAPPED_DEK = stringPreferencesKey("biometric_wrapped_dek")

        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val SCREENSHOT_BLOCKED = booleanPreferencesKey("screenshot_blocked")
        val CLIPBOARD_CLEAR_SECONDS = intPreferencesKey("clipboard_clear_seconds")

        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LAST_FAILED_AT = longPreferencesKey("last_failed_at")
    }

    // ---- Security material -------------------------------------------------

    val initializedFlow: Flow<Boolean> =
        context.dataStore.data
            .map { it[Keys.MASTER_WRAPPED_DEK] != null }
            // Fail closed: a preferences read error must not expose setup over an existing vault.
            .catch { emit(true) }

    suspend fun saveVaultMaterial(
        masterSalt: String,
        recoverySalt: String,
        masterWrappedDek: String,
        recoveryWrappedDek: String,
        recoveryQuestion: String,
        masterIterations: Int,
        recoveryIterations: Int,
        mnemonicSalt: String,
        mnemonicWrappedDek: String,
        mnemonicVerifierHash: String
    ) {
        context.dataStore.edit {
            it[Keys.MASTER_SALT] = masterSalt
            it[Keys.RECOVERY_SALT] = recoverySalt
            it[Keys.MASTER_WRAPPED_DEK] = masterWrappedDek
            it[Keys.RECOVERY_WRAPPED_DEK] = recoveryWrappedDek
            it[Keys.RECOVERY_QUESTION] = recoveryQuestion
            it[Keys.MASTER_ITERATIONS] = masterIterations
            it[Keys.RECOVERY_ITERATIONS] = recoveryIterations
            it[Keys.MNEMONIC_SALT] = mnemonicSalt
            it[Keys.MNEMONIC_WRAPPED_DEK] = mnemonicWrappedDek
            it[Keys.MNEMONIC_VERIFIER_HASH] = mnemonicVerifierHash
        }
    }

    suspend fun updateMasterMaterial(masterSalt: String, masterWrappedDek: String, masterIterations: Int) {
        context.dataStore.edit {
            it[Keys.MASTER_SALT] = masterSalt
            it[Keys.MASTER_WRAPPED_DEK] = masterWrappedDek
            it[Keys.MASTER_ITERATIONS] = masterIterations
        }
    }

    suspend fun updateRecoveryMaterial(
        recoverySalt: String,
        recoveryWrappedDek: String,
        recoveryQuestion: String,
        recoveryIterations: Int
    ) {
        context.dataStore.edit {
            it[Keys.RECOVERY_SALT] = recoverySalt
            it[Keys.RECOVERY_WRAPPED_DEK] = recoveryWrappedDek
            it[Keys.RECOVERY_QUESTION] = recoveryQuestion
            it[Keys.RECOVERY_ITERATIONS] = recoveryIterations
        }
    }

    suspend fun masterSalt(): String? = context.dataStore.data.first()[Keys.MASTER_SALT]
    suspend fun recoverySalt(): String? = context.dataStore.data.first()[Keys.RECOVERY_SALT]
    suspend fun masterWrappedDek(): String? = context.dataStore.data.first()[Keys.MASTER_WRAPPED_DEK]
    suspend fun recoveryWrappedDek(): String? = context.dataStore.data.first()[Keys.RECOVERY_WRAPPED_DEK]
    suspend fun mnemonicSalt(): String? = context.dataStore.data.first()[Keys.MNEMONIC_SALT]
    suspend fun mnemonicWrappedDek(): String? = context.dataStore.data.first()[Keys.MNEMONIC_WRAPPED_DEK]
    suspend fun mnemonicVerifierHash(): String? = context.dataStore.data.first()[Keys.MNEMONIC_VERIFIER_HASH]

    /** Iteration counts; absent material predates persistence and uses the legacy count. */
    suspend fun masterIterations(): Int =
        context.dataStore.data.first()[Keys.MASTER_ITERATIONS] ?: CryptoManager.LEGACY_PBKDF2_ITERATIONS
    suspend fun recoveryIterations(): Int =
        context.dataStore.data.first()[Keys.RECOVERY_ITERATIONS] ?: CryptoManager.LEGACY_PBKDF2_ITERATIONS

    val credentialTypeFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.CREDENTIAL_TYPE] ?: "pin6" }.catch { emit("pin6") }

    suspend fun setCredentialType(value: String) {
        context.dataStore.edit { it[Keys.CREDENTIAL_TYPE] = value }
    }

    val recoveryQuestionFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.RECOVERY_QUESTION] ?: "" }.catch { emit("") }

    val mnemonicEnabledFlow: Flow<Boolean> =
        context.dataStore.data
            .map {
                !it[Keys.MNEMONIC_SALT].isNullOrBlank() &&
                    !it[Keys.MNEMONIC_WRAPPED_DEK].isNullOrBlank() &&
                    !it[Keys.MNEMONIC_VERIFIER_HASH].isNullOrBlank()
            }
            .catch { emit(false) }

    suspend fun updateMnemonicMaterial(
        mnemonicSalt: String,
        mnemonicWrappedDek: String,
        mnemonicVerifierHash: String
    ) {
        context.dataStore.edit {
            it[Keys.MNEMONIC_SALT] = mnemonicSalt
            it[Keys.MNEMONIC_WRAPPED_DEK] = mnemonicWrappedDek
            it[Keys.MNEMONIC_VERIFIER_HASH] = mnemonicVerifierHash
        }
    }

    suspend fun clearMnemonicMaterial() {
        context.dataStore.edit {
            it.remove(Keys.MNEMONIC_SALT)
            it.remove(Keys.MNEMONIC_WRAPPED_DEK)
            it.remove(Keys.MNEMONIC_VERIFIER_HASH)
        }
    }

    // ---- Biometric ---------------------------------------------------------

    val biometricEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }.catch { emit(false) }

    suspend fun setBiometric(enabled: Boolean, wrappedDek: String?) {
        context.dataStore.edit {
            it[Keys.BIOMETRIC_ENABLED] = enabled
            if (wrappedDek != null) it[Keys.BIOMETRIC_WRAPPED_DEK] = wrappedDek
            else it.remove(Keys.BIOMETRIC_WRAPPED_DEK)
        }
    }

    suspend fun biometricWrappedDek(): String? =
        context.dataStore.data.first()[Keys.BIOMETRIC_WRAPPED_DEK]

    // ---- User settings -----------------------------------------------------

    val autoLockMinutesFlow: Flow<Int> =
        context.dataStore.data.map { it[Keys.AUTO_LOCK_MINUTES] ?: 1 }.catch { emit(1) }

    suspend fun setAutoLockMinutes(value: Int) {
        context.dataStore.edit { it[Keys.AUTO_LOCK_MINUTES] = value }
    }

    suspend fun autoLockMinutesValue(): Int =
        context.dataStore.data.first()[Keys.AUTO_LOCK_MINUTES] ?: 1

    suspend fun screenshotBlockedValue(): Boolean =
        context.dataStore.data.first()[Keys.SCREENSHOT_BLOCKED] ?: true

    val screenshotBlockedFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SCREENSHOT_BLOCKED] ?: true }.catch { emit(true) }

    suspend fun setScreenshotBlocked(value: Boolean) {
        context.dataStore.edit { it[Keys.SCREENSHOT_BLOCKED] = value }
    }

    val clipboardClearSecondsFlow: Flow<Int> =
        context.dataStore.data.map { it[Keys.CLIPBOARD_CLEAR_SECONDS] ?: 10 }.catch { emit(10) }

    suspend fun setClipboardClearSeconds(value: Int) {
        context.dataStore.edit { it[Keys.CLIPBOARD_CLEAR_SECONDS] = value }
    }

    // ---- Failed unlock tracking (brute force delay) ------------------------

    suspend fun failedAttempts(): Int = context.dataStore.data.first()[Keys.FAILED_ATTEMPTS] ?: 0
    suspend fun lastFailedAt(): Long = context.dataStore.data.first()[Keys.LAST_FAILED_AT] ?: 0L

    suspend fun recordFailure() {
        context.dataStore.edit {
            it[Keys.FAILED_ATTEMPTS] = (it[Keys.FAILED_ATTEMPTS] ?: 0) + 1
            it[Keys.LAST_FAILED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun resetFailures() {
        context.dataStore.edit {
            it[Keys.FAILED_ATTEMPTS] = 0
            it[Keys.LAST_FAILED_AT] = 0L
        }
    }
}
