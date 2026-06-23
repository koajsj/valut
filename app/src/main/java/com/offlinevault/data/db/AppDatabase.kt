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
import com.offlinevault.data.model.PasswordHistoryEntity
import com.offlinevault.data.model.VaultEntity

@Database(
    entities = [VaultEntity::class, PasswordEntity::class, PasswordHistoryEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao
    abstract fun passwordDao(): PasswordDao

    companion object {
        /** v2 encrypts metadata in place after unlock; the SQL shape itself is unchanged. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        /** v3 adds the recycle-bin soft-delete column. Purely additive — existing rows default to 0. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE passwords ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4 adds the password-history table (additive). Must match Room's generated schema exactly. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `password_history` (" +
                        "`id` TEXT NOT NULL, " +
                        "`passwordId` TEXT NOT NULL, " +
                        "`encryptedPassword` TEXT NOT NULL, " +
                        "`changedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`passwordId`) REFERENCES `passwords`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_password_history_passwordId` " +
                        "ON `password_history` (`passwordId`)"
                )
            }
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
