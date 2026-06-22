package com.offlinevault.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offlinevault.data.model.VaultEntity
import com.offlinevault.data.model.VaultWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vault: VaultEntity)

    @Update
    suspend fun update(vault: VaultEntity)

    @Delete
    suspend fun delete(vault: VaultEntity)

    @Query("SELECT * FROM vaults WHERE id = :id")
    suspend fun getById(id: String): VaultEntity?

    @Query("SELECT * FROM vaults ORDER BY updatedAt DESC")
    fun getAllVaults(): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vaults ORDER BY updatedAt DESC")
    suspend fun getAllVaultsOnce(): List<VaultEntity>

    @Query(
        """
        SELECT v.id AS id, v.name AS name, v.icon AS icon,
               v.createdAt AS createdAt, v.updatedAt AS updatedAt,
               (SELECT COUNT(*) FROM passwords p WHERE p.vaultId = v.id) AS itemCount
        FROM vaults v
        ORDER BY v.updatedAt DESC
        """
    )
    fun getVaultsWithCount(): Flow<List<VaultWithCount>>

    @Query("SELECT COUNT(*) FROM vaults")
    suspend fun count(): Int

    @Query("SELECT * FROM vaults ORDER BY updatedAt DESC LIMIT 1")
    suspend fun firstVault(): VaultEntity?
}
