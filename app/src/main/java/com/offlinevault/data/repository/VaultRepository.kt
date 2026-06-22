package com.offlinevault.data.repository

import com.offlinevault.data.dao.VaultDao
import com.offlinevault.data.model.VaultEntity
import com.offlinevault.security.EncryptedField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VaultRepository(private val vaultDao: VaultDao) {

    fun allVaults(): Flow<List<VaultEntity>> = vaultDao.getAllVaults().map { rows -> rows.map(::decrypt) }

    suspend fun getById(id: String): VaultEntity? = vaultDao.getById(id)?.let(::decrypt)

    suspend fun allOnce(): List<VaultEntity> = vaultDao.getAllVaultsOnce().map(::decrypt)

    suspend fun create(name: String, icon: String): VaultEntity {
        val vault = VaultEntity(name = EncryptedField.encrypt(name.trim().ifEmpty { "密码库" }), icon = icon)
        vaultDao.insert(vault)
        return decrypt(vault)
    }

    suspend fun touch(vaultId: String) {
        vaultDao.getById(vaultId)?.let {
            vaultDao.update(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    /** Ensures there is always at least one vault to put credentials into. */
    suspend fun ensureDefault(): VaultEntity {
        vaultDao.firstVault()?.let { return decrypt(it) }
        val vault = VaultEntity(name = EncryptedField.encrypt("个人"), icon = "person")
        vaultDao.insert(vault)
        return decrypt(vault)
    }

    suspend fun insertImported(vault: VaultEntity) {
        vaultDao.insert(vault.copy(name = EncryptedField.encrypt(vault.name)))
    }

    suspend fun migrateLegacyMetadata() {
        vaultDao.getAllVaultsOnce().forEach { row ->
            if (!EncryptedField.isEncrypted(row.name)) {
                vaultDao.update(row.copy(name = EncryptedField.encrypt(row.name)))
            }
        }
    }

    private fun decrypt(vault: VaultEntity): VaultEntity =
        vault.copy(name = EncryptedField.decrypt(vault.name))
}
