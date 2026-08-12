package com.gcatcode.petmephone.core.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `task-persistence` spec / design decision 11: `room-testing` and `MigrationTestHelper` are
 * wired even though no migration exists yet — this exercises version 2's schema loading and the
 * database opening, so the first real migration is a test, not an infrastructure project.
 *
 * Schema JSON loads via `Instrumentation`'s `Context.getAssets()` under the database class's
 * qualified name, regardless of which `MigrationTestHelper` constructor overload is used —
 * confirmed against Room 2.8.4's actual bytecode, which differs from an earlier assumption that
 * the driver-based `file` constructor argument would itself be read as the schema source (the
 * risk design.md's decision 11 flagged). `AndroidRoomConventionPlugin` wires the `test` source
 * set's `assets` to the same schema directory the KSP `room.schemaLocation` arg already owns, so
 * `Context.getAssets().open("<AppDatabase-qualified-name>/2.json")` resolves. The `file`
 * constructor argument is instead the *target* database file this helper opens — a directory
 * (such as the schema directory) fails there, so it must be a real file path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "migration-test.db"),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @Test
    fun `version 2 schema loads and the database opens`() {
        // `createDatabase(version)` — not the legacy `(name, version)` overload — is the shape a
        // driver-based helper actually exposes in Room 2.8.4, confirmed by compiling against it:
        // the legacy overload throws when a SQLiteDriver was supplied at construction (design.md's
        // flagged risk on task 2.15).
        helper.createDatabase(2).close()
    }
}
