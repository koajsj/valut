package com.offlinevault.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offlinevault.data.model.PasswordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PasswordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PasswordEntity>)

    @Update
    suspend fun update(item: PasswordEntity)

    @Delete
    suspend fun delete(item: PasswordEntity)

    @Query("SELECT * FROM passwords WHERE id = :id")
    suspend fun getById(id: String): PasswordEntity?

    @Query("SELECT * FROM passwords WHERE id = :id")
    fun observeById(id: String): Flow<PasswordEntity?>

    // Active (non-trashed) rows only — deletedAt = 0.
    @Query("SELECT * FROM passwords WHERE vaultId = :vaultId AND deletedAt = 0 ORDER BY updatedAt DESC")
    fun getPasswordsByVaultId(vaultId: String): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords WHERE vaultId = :vaultId AND deletedAt = 0")
    suspend fun getPasswordsByVaultIdOnce(vaultId: String): List<PasswordEntity>

    @Query("SELECT * FROM passwords WHERE deletedAt = 0")
    suspend fun getAllOnce(): List<PasswordEntity>

    // ---- Recycle bin (soft delete) ----

    @Query("SELECT * FROM passwords WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<PasswordEntity>>

    @Query("UPDATE passwords SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE passwords SET deletedAt = 0 WHERE id = :id")
    suspend fun restore(id: String)

    /** Permanently removes trashed rows older than [cutoff]; active rows are never touched. */
    @Query("DELETE FROM passwords WHERE deletedAt > 0 AND deletedAt < :cutoff")
    suspend fun purgeTrashedOlderThan(cutoff: Long)

    @Query("DELETE FROM passwords WHERE deletedAt > 0")
    suspend fun emptyTrash()

}
