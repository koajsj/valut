package com.offlinevault.data.backup

import com.google.gson.Gson
import com.offlinevault.data.model.VaultEntity
import com.offlinevault.data.repository.DecryptedPassword
import com.offlinevault.data.repository.PasswordRepository
import com.offlinevault.data.repository.VaultRepository
import com.offlinevault.security.CryptoManager
import kotlinx.coroutines.CancellationException

/**
 * Handles JSON (encrypted) and CSV import/export. Everything runs locally; nothing is uploaded.
 */
class BackupManager(
    private val vaultRepository: VaultRepository,
    private val passwordRepository: PasswordRepository
) {
    private companion object {
        const val MAX_IMPORT_VAULTS = 1_000
        const val MAX_IMPORT_ITEMS = 50_000
        const val MAX_CSV_ROWS = MAX_IMPORT_ITEMS + 1
    }

    private val gson = Gson()

    // ---- JSON export (encrypted) ------------------------------------------

    suspend fun buildEncryptedJsonBackup(backupPassword: String): String {
        val vaults = vaultRepository.allOnce().map { BackupVault(it.id, it.name, it.icon) }
        val items = passwordRepository.allDecrypted().map {
            BackupItem(it.vaultId, it.title, it.username, it.password, it.url, it.tags, it.note, it.favorite)
        }
        val plainJson = gson.toJson(BackupData(vaults, items))

        val salt = CryptoManager.newSalt()
        val iterations = CryptoManager.DEFAULT_PBKDF2_ITERATIONS
        val key = CryptoManager.deriveKey(backupPassword.toCharArray(), salt, iterations)
        val blob = CryptoManager.encrypt(key, plainJson.toByteArray(Charsets.UTF_8))

        val wrapper = EncryptedBackup(
            salt = CryptoManager.encode(salt),
            data = CryptoManager.encode(blob),
            iterations = iterations
        )
        return gson.toJson(wrapper)
    }

    // ---- JSON import -------------------------------------------------------

    suspend fun importJsonBackup(content: String, backupPassword: String): ImportResult {
        val data = try {
            val wrapper = gson.fromJson(content, EncryptedBackup::class.java)
            if (wrapper != null && wrapper.format == "offline-vault-backup" && wrapper.encrypted) {
                val salt = CryptoManager.decode(wrapper.salt)
                val iterations = CryptoManager.requireValidIterations(
                    if (wrapper.iterations > 0) wrapper.iterations
                    else CryptoManager.LEGACY_PBKDF2_ITERATIONS
                )
                val key = CryptoManager.deriveKey(backupPassword.toCharArray(), salt, iterations)
                val plain = String(CryptoManager.decrypt(key, CryptoManager.decode(wrapper.data)), Charsets.UTF_8)
                gson.fromJson(plain, BackupData::class.java)
            } else {
                // Fall back to a plain (unencrypted) BackupData JSON.
                gson.fromJson(content, BackupData::class.java)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportResult(0, 0, 0, listOf("无法读取备份：密码错误或文件已损坏"))
        } ?: return ImportResult(0, 0, 0, listOf("备份为空或格式无效"))

        val validationError = validateBackup(data)
        if (validationError != null) return ImportResult(0, 0, 0, listOf(validationError))

        var imported = 0
        var skipped = 0
        var failed = 0
        val errors = mutableListOf<String>()

        // Recreate vaults (preserve ids so item links survive).
        val existingIds = vaultRepository.allOnce().map { it.id }.toMutableSet()
        for (v in data.vaults) {
            try {
                if (v.id !in existingIds) {
                    vaultRepository.insertImported(VaultEntity(id = v.id, name = v.name, icon = v.icon))
                    existingIds.add(v.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add("密码库“${v.name}”导入失败：${e.message}")
            }
        }

        // Group items by vault, dedupe against existing rows in that vault.
        val byVault = data.items.groupBy { it.vaultId }
        for ((rawVaultId, items) in byVault) {
            // Re-route items whose vault is missing (orphan/malformed backup) to a real vault so the
            // foreign-key insert never fails.
            val vaultId = if (rawVaultId.isNotEmpty() && rawVaultId in existingIds) rawVaultId
            else existingIds.firstOrNull()
            if (vaultId == null) { failed += items.size; continue }

            val existing = passwordRepository.decryptedForVault(vaultId).map { it.dedupeKey() }.toMutableSet()
            val toInsert = mutableListOf<DecryptedPassword>()
            for (item in items) {
                try {
                    val key = item.dedupeKey()
                    if (key in existing) { skipped++; continue }
                    existing.add(key)
                    toInsert.add(item.toDecrypted())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                }
            }
            try {
                imported += passwordRepository.importDecrypted(vaultId, toInsert)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += toInsert.size
                errors.add("部分项目导入失败：${e.message}")
            }
        }
        return ImportResult(imported, skipped, failed, errors)
    }

    private fun validateBackup(data: BackupData): String? = try {
        when {
            data.vaults.size > MAX_IMPORT_VAULTS -> "备份中的密码库数量过多"
            data.items.size > MAX_IMPORT_ITEMS -> "备份中的项目数量过多"
            data.vaults.any { it.id.isBlank() || it.name.isBlank() } ->
                "备份中包含无效的密码库"
            data.items.any { it.vaultId.isBlank() } -> "备份中包含无效的项目"
            else -> null
        }
    } catch (e: Exception) {
        // Gson can assign null to Kotlin non-null properties in malformed external JSON.
        "备份结构无效"
    }

    // ---- CSV export -------------------------------------------------------

    /**
     * Builds CSV for a vault. [slim] = true emits only the browser-compatible
     * name,url,username,password columns (easy to import into Chrome/Edge); otherwise the full
     * format including note and tags is produced.
     */
    suspend fun buildCsvForVault(vaultId: String, slim: Boolean = false): String {
        val items = passwordRepository.decryptedForVault(vaultId)
        val sb = StringBuilder()
        val header = if (slim) listOf("name", "url", "username", "password")
        else listOf("name", "url", "username", "password", "note", "tags")
        sb.append(CsvUtils.buildRow(header)).append("\n")
        for (it in items) {
            val row = if (slim) listOf(it.title, it.url, it.username, it.password)
            else listOf(it.title, it.url, it.username, it.password, it.note, it.tags.joinToString("|"))
            sb.append(CsvUtils.buildRow(row)).append("\n")
        }
        return sb.toString()
    }

    // ---- CSV import (Chrome / generic) ------------------------------------

    suspend fun importChromeCsv(content: String, targetVaultId: String): ImportResult {
        val rows = CsvUtils.parse(content)
        if (rows.isEmpty()) return ImportResult(0, 0, 0, listOf("CSV 文件为空"))
        if (rows.size > MAX_CSV_ROWS) {
            return ImportResult(0, 0, 0, listOf("CSV 中的项目数量过多"))
        }

        // Ensure a valid destination vault exists; create one if the target is blank or was deleted.
        val vaultId = if (targetVaultId.isNotBlank() && vaultRepository.getById(targetVaultId) != null) {
            targetVaultId
        } else {
            vaultRepository.create("已导入", "lock").id
        }

        val header = rows.first().map { it.trim().lowercase() }
        val nameIdx = header.indexOfFirst { it == "name" || it == "title" }
        val urlIdx = header.indexOfFirst { it == "url" }
        val userIdx = header.indexOfFirst { it == "username" || it == "login" || it == "email" }
        val passIdx = header.indexOfFirst { it == "password" }
        val noteIdx = header.indexOfFirst { it == "note" || it == "notes" }
        val tagsIdx = header.indexOfFirst { it == "tags" }

        if (passIdx < 0) {
            return ImportResult(0, 0, 0, listOf("CSV 中缺少 “password” 列"))
        }

        var skipped = 0
        var failed = 0
        val errors = mutableListOf<String>()

        val existing = passwordRepository.decryptedForVault(vaultId).map { it.dedupeKey() }.toMutableSet()
        val toInsert = mutableListOf<DecryptedPassword>()

        for (r in rows.drop(1)) {
            try {
                fun at(idx: Int) = if (idx in r.indices) r[idx].trim() else ""
                val title = at(nameIdx).ifEmpty { at(urlIdx).ifEmpty { "已导入" } }
                val username = at(userIdx)
                val password = at(passIdx)
                val url = at(urlIdx)
                val note = at(noteIdx)
                val tags = at(tagsIdx).split("|", ",").map { it.trim() }.filter { it.isNotEmpty() }

                if (password.isEmpty() && username.isEmpty()) { failed++; continue }

                val item = DecryptedPassword(
                    id = "", vaultId = vaultId, title = title, username = username,
                    password = password, url = url, tags = tags, note = note,
                    strengthScore = 0, createdAt = 0, updatedAt = 0
                )
                val key = item.dedupeKey()
                if (key in existing) { skipped++; continue }
                existing.add(key)
                toInsert.add(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                errors.add("已跳过一行：${e.message}")
            }
        }

        val imported = passwordRepository.importDecrypted(vaultId, toInsert)
        return ImportResult(imported, skipped, failed, errors)
    }

    private fun BackupItem.toDecrypted() = DecryptedPassword(
        id = "", vaultId = vaultId, title = title, username = username, password = password,
        url = url, tags = tags, note = note, strengthScore = 0, createdAt = 0, updatedAt = 0,
        favorite = favorite
    )

    private fun BackupItem.dedupeKey() = "${title.lowercase()}|${username.lowercase()}|$password"
    private fun DecryptedPassword.dedupeKey() = "${title.lowercase()}|${username.lowercase()}|$password"
}
