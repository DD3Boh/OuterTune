/*
 *     Copyright (C) 2026 The Gramophone contributors
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.akanework.gramophone.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.akanework.gramophone.db.GramophoneDatabase.Companion.MUSIC_DATABASE_VERSION
import org.akanework.gramophone.db.entities.ChromaprintEntity
import org.akanework.gramophone.db.entities.PlayEventEntity
import org.akanework.gramophone.db.entities.PlayEventLegacyEntity
import org.akanework.gramophone.db.entities.QueueEntity
import org.akanework.gramophone.db.entities.QueueSongMap
import org.akanework.gramophone.db.entities.SongEntity
import org.akanework.gramophone.db.entities.SongTagEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset


class GramophoneDatabase(
    private val delegate: AppDatabase,
) : DatabaseDao by delegate.dao {
    val openHelper: SupportSQLiteOpenHelper
        get() = delegate.openHelper

    fun query(block: GramophoneDatabase.() -> Unit) = with(delegate) {
        queryExecutor.execute {
            block(this@GramophoneDatabase)
        }
    }

    fun transaction(block: GramophoneDatabase.() -> Unit) = with(delegate) {
        runInTransaction { // Outertune override
            block(this@GramophoneDatabase)
        }
    }

    fun close() = delegate.close()

    companion object {
        const val MUSIC_DATABASE_VERSION = 1
    }
}

@Database(
    entities = [
        SongEntity::class,
        SongTagEntity::class,
        PlayEventEntity::class,
        PlayEventLegacyEntity::class,
        ChromaprintEntity::class,
        QueueEntity::class,
        QueueSongMap::class,
    ],
    version = MUSIC_DATABASE_VERSION,
    exportSchema = true,
    autoMigrations = [
    ]
)

@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract val dao: DatabaseDao

    companion object {
        const val DB_NAME = "gramophone_data.db"
        const val TEST_DB_NAME = "probe_song.db"

        fun newInstance(context: Context): GramophoneDatabase =
            GramophoneDatabase(
                delegate = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                    .build()
            )

        // keep this separate in the rare case we come across concepts of a plan to support migrations from other forks
        fun newTestInstance(context: Context, dbName: String): GramophoneDatabase =
            GramophoneDatabase(
                delegate = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                    .build()
            )

        fun newUnitTestInstance(context: Context): GramophoneDatabase =
            GramophoneDatabase(
                delegate = Room.databaseBuilder(context, AppDatabase::class.java, "unit_test")
                    .allowMainThreadQueries()
                    .build()
            )
    }
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? =
        if (value != null) LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneOffset.UTC)
        else null

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? =
        date?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
}
