package com.offlinevault.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.offlinevault.data.dao.PasswordDao
import com.offlinevault.data.dao.VaultDao
import com.offlinevault.data.model.PasswordEntity
import com.offlinevault.data.model.VaultEntity

@Database(
    entities = [VaultEntity::class, PasswordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao
    abstract fun passwordDao(): PasswordDao

    companion object {
        /** v2 encrypts metadata in place after unlock; the SQL shape itself is unchanged. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    // NEVER rename this file or the user's existing vault becomes orphaned.
                    "offline_vault.db"
                )
                    // Data-safety policy: every schema bump MUST ship a Migration here. Do NOT add
                    // fallbackToDestructiveMigration() — a missing migration should fail loudly,
                    // never silently wipe the encrypted vault.
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
