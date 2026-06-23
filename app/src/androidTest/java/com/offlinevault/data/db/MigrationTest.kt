package com.offlinevault.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the Room schema and migrations against the exported JSON schemas in /schemas.
 *
 * These run only AFTER a build has exported the schema files (exportSchema = true). The latest-schema
 * test below catches the most dangerous mistake: changing an @Entity without writing a migration —
 * Room will detect the mismatch and fail here instead of wiping a user's vault at runtime.
 *
 * Adding a per-migration test going forward: once both version N and N+1 schemas are committed, add
 *   helper.createDatabase(TEST_DB, N).close()
 *   helper.runMigrationsAndValidate(TEST_DB, N + 1, true, <MIGRATION_N_Np1>)
 * (the MIGRATION_* objects need to be made internal/visible to the test for that).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun latestSchemaMatchesEntities() {
        // Fails if any @Entity drifted from the exported schema (i.e. a missing migration).
        helper.createDatabase(testDb, 4).close()
    }
}
