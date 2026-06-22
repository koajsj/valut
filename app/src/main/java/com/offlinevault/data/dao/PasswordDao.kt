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

    @Query("SELECT * FROM passwords WHERE vaultId = :vaultId ORDER BY updatedAt DESC")
    fun getPasswordsByVaultId(vaultId: String): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords WHERE vaultId = :vaultId")
    suspend fun getPasswordsByVaultIdOnce(vaultId: String): List<PasswordEntity>

    @Query("SELECT * FROM passwords")
    suspend fun getAllOnce(): List<PasswordEntity>

    @Query("DELETE FROM passwords WHERE vaultId = :vaultId")
    suspend fun deleteByVault(vaultId: String)
}
