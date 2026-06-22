package com.offlinevault

import android.content.Context
import com.offlinevault.data.backup.BackupManager
import com.offlinevault.data.db.AppDatabase
import com.offlinevault.data.preferences.SecurityPreferences
import com.offlinevault.data.repository.PasswordRepository
import com.offlinevault.data.repository.VaultRepository
import com.offlinevault.security.KeyManager

/** Tiny manual dependency container. Keeps the app free of any DI framework. */
class AppContainer(context: Context) {

    private val database = AppDatabase.get(context)

    val securityPreferences = SecurityPreferences(context.applicationContext)
    val keyManager = KeyManager(securityPreferences)

    val vaultRepository = VaultRepository(database.vaultDao())
    val passwordRepository = PasswordRepository(database.passwordDao())
    val backupManager = BackupManager(vaultRepository, passwordRepository)
}
