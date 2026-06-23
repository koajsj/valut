package com.offlinevault.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A previous password value for a credential, kept so a mistaken password change can be undone.
 * The value is stored as AES-256-GCM ciphertext (the same envelope as the live credential). Rows
 * cascade-delete with their parent credential, so emptying the recycle bin also clears its history.
 */
@Entity(
    tableName = "password_history",
    foreignKeys = [
        ForeignKey(
            entity = PasswordEntity::class,
            parentColumns = ["id"],
            childColumns = ["passwordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("passwordId")]
)
data class PasswordHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val passwordId: String,
    val encryptedPassword: String,
    val changedAt: Long = System.currentTimeMillis()
)
