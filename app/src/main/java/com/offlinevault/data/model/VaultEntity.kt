package com.offlinevault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "vaults")
data class VaultEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "lock",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Vault row joined with its password count, used by the vault list screen. */
data class VaultWithCount(
    val id: String,
    val name: String,
    val icon: String,
    val createdAt: Long,
    val updatedAt: Long,
    val itemCount: Int
)
