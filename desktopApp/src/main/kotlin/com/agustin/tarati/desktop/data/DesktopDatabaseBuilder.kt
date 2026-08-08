package com.agustin.tarati.desktop.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.agustin.tarati.core.data.database.DATABASE_NAME
import com.agustin.tarati.core.data.database.MIGRATION_1_2
import com.agustin.tarati.core.data.database.MIGRATION_2_3
import com.agustin.tarati.core.data.database.MIGRATION_3_4
import com.agustin.tarati.core.data.database.TaratiDatabase
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Creates the Room database instance for Desktop/JVM.
 *
 * **Database Location**:
 * - Windows: `C:\Users\{username}\.tarati\tarati.db`
 * - macOS: `/Users/{username}/.tarati/tarati.db`
 * - Linux: `/home/{username}/.tarati/tarati.db`
 *
 * **How Room works on Desktop**:
 * 1. Uses SQLite via JDBC (Java Database Connectivity)
 * 2. BundledSQLiteDriver provides the SQLite native library
 * 3. Database file persists between app launches
 * 4. Supports all Room features (DAOs, migrations, transactions)
 *
 * **Differences from Android**:
 * - Android: Uses `databaseBuilder(context, Class, name)`
 * - Desktop: Uses `databaseBuilder<Database>(name = path)`
 * - Android: SQLite is part of the OS
 * - Desktop: SQLite bundled with the app
 *
 * @return TaratiDatabase instance ready to use
 */
fun createDesktopDatabase(): TaratiDatabase {
    val dbFile = getDatabaseFile()

    return Room.databaseBuilder<TaratiDatabase>(
        name = dbFile.absolutePath
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()
}

/**
 * Gets the database file, creating the parent directory if needed.
 *
 * **Directory structure**:
 * ```
 * ~/.tarati/
 *   ├── tarati.db           <- Main database file
 *   ├── tarati.db-shm       <- Shared memory (auto-created by SQLite)
 *   └── tarati.db-wal       <- Write-ahead log (auto-created by SQLite)
 * ```
 *
 * @return File pointing to the database location
 */
private fun getDatabaseFile(): File {
    val userHome = System.getProperty("user.home")
        ?: throw IllegalStateException("Cannot determine user home directory")

    val appDir = File(userHome, ".tarati")

    // Create directory if it doesn't exist
    if (!appDir.exists()) {
        appDir.mkdirs()
    }

    return File(appDir, DATABASE_NAME)
}

/**
 * Gets the database path as a string.
 * Useful for debugging or displaying to the user.
 *
 * @return Absolute path to the database file
 */
fun getDatabasePath(): String = getDatabaseFile().absolutePath