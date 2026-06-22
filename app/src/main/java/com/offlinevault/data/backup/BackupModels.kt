package com.offlinevault.data.backup

/** Plain transfer objects used for JSON backup serialization (via Gson). */
data class BackupVault(
    val id: String,
    val name: String,
    val icon: String
)

data class BackupItem(
    val vaultId: String,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val tags: List<String>,
    val note: String
)

data class BackupData(
    val vaults: List<BackupVault>,
    val items: List<BackupItem>
)

/** On-disk wrapper for an ENCRYPTED JSON backup. [data] is Base64(IV || ciphertext). */
data class EncryptedBackup(
    val format: String = "offline-vault-backup",
    val version: Int = 1,
    val encrypted: Boolean = true,
    val salt: String = "",
    val data: String = "",
    /** PBKDF2 iterations used to derive the backup key. 0/absent => legacy backup. */
    val iterations: Int = 0
)

/** Result of importing a Chrome CSV / JSON file. */
data class ImportResult(
    val imported: Int,
    val skippedDuplicates: Int,
    val failed: Int,
    val errors: List<String>
)
