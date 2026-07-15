package com.agustin.tarati.core.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.agustin.tarati.core.data.database.dao.GameDao
import com.agustin.tarati.core.data.database.entities.GameEntity

@Database(
    entities = [GameEntity::class],
    version = 3,
    exportSchema = true,
)
@ConstructedBy(TaratiDatabaseConstructor::class)
abstract class TaratiDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
}

// Constructor multiplataforma requerido por Room en targets sin reflexión (iOS).
// El compilador de Room genera las implementaciones `actual` para cada plataforma.
@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object TaratiDatabaseConstructor : RoomDatabaseConstructor<TaratiDatabase> {
    override fun initialize(): TaratiDatabase
}

val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE games ADD COLUMN observations TEXT NOT NULL DEFAULT ''")
        }
    }

val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            // Las partidas existentes empezaron desde la posición estándar.
            // boardPosition contenía la posición final — se preserva tal cual.
            // initialBoardPosition se rellena con la posición inicial estándar
            // para no invalidar partidas ya guardadas.
            // Notación producida por GameState.toPositionNotation() para la
            // posición inicial estándar (4 blancas en D1/D2/C1/C2, 4 negras en D3/D4/C7/C8).
            // Las partidas existentes comenzaron desde esta posición, por lo que el
            // DEFAULT es válido para reconstruir cualquier movimiento de su historial.
            val standardInitial = "C1w/C2w/C7b/C8b/D1w/D2w/D3b/D4b w"
            connection.execSQL(
                "ALTER TABLE games ADD COLUMN initialBoardPosition TEXT NOT NULL DEFAULT '$standardInitial'"
            )
        }
    }

/**
 * Nombre del archivo de base de datos.
 *
 * Se usa en:
 * - Android: DatabaseBuilder
 * - iOS: DatabaseBuilder
 * - Desktop: DatabaseBuilder con path completo
 */
const val DATABASE_NAME: String = "tarati.db"