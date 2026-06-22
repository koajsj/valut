package com.offlinevault.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A stored credential. Secret fields use AES-256-GCM. Metadata fields are also stored as
 * version-prefixed ciphertext and are decrypted by the repository while the vault is unlocked.
 */
@Entity(
    tableName = "passwords",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vaultId")]
)
data class PasswordEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vaultId: String,
    val title: String,
    val username: String,
    val encryptedPassword: String,
    val url: String = "",
    val tags: String = "",            // comma separated
    val encryptedNote: String = "",   // encrypted, may be empty
    val strengthScore: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
