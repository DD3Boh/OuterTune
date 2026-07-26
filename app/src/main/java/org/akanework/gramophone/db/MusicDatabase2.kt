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
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.akanework.gramophone.db.MusicDatabase2.Companion.MUSIC_DATABASE_VERSION
import org.akanework.gramophone.db.entities.ChromaprintEntity
import org.akanework.gramophone.db.entities.PlayEventEntity
import org.akanework.gramophone.db.entities.PlayEventLegacyEntity
import org.akanework.gramophone.db.entities.SongEntity
import org.akanework.gramophone.db.entities.SongTagEntity


class MusicDatabase2(
    private val delegate: InternalDatabase2,
) : DatabaseDao by delegate.dao {
    val openHelper: SupportSQLiteOpenHelper
        get() = delegate.openHelper

    fun query(block: MusicDatabase2.() -> Unit) = with(delegate) {
        queryExecutor.execute {
            block(this@MusicDatabase2)
        }
    }

    fun transaction(block: MusicDatabase2.() -> Unit) = with(delegate) {
        transactionExecutor.execute {
            runInTransaction {
                block(this@MusicDatabase2)
            }
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
    ],
    version = MUSIC_DATABASE_VERSION,
    exportSchema = true,
    autoMigrations = [
    ]
)

abstract class InternalDatabase2 : RoomDatabase() {
    abstract val dao: DatabaseDao

    companion object {
        const val DB_NAME = "gramophone_song.db"

        fun newInstance(context: Context): MusicDatabase2 =
            MusicDatabase2(
                delegate = Room.databaseBuilder(context, InternalDatabase2::class.java, DB_NAME)
                    .build()
            )

        // keep this separate in the rare case we come across concepts of a plan to support migrations from other forks
        fun newTestInstance(context: Context, dbName: String): MusicDatabase2 =
            MusicDatabase2(
                delegate = Room.databaseBuilder(context, InternalDatabase2::class.java, dbName)
                    .build()
            )

        fun newUnitTestInstance(context: Context): MusicDatabase2 =
            MusicDatabase2(
                delegate = Room.databaseBuilder(context, InternalDatabase2::class.java, "unit_test")
                    .allowMainThreadQueries()
                    .build()
            )

        fun genMediaItem(
            chromaprint: String?,
            title: String?,
            artist: String?,
            album: String?,

            uri: Uri?,
            id: String? = null,
        ): MediaItem {
            if (chromaprint == null && title == null) throw IllegalArgumentException("chromaprint or title must be defined")
            val metadata = MediaMetadata.Builder()
                .setTitle(title.takeIf { !it.isNullOrBlank() })
                .setArtist(artist.takeIf { !it.isNullOrBlank() })
                .setAlbumTitle(album.takeIf { !it.isNullOrBlank() })

            if (chromaprint != null) {
                metadata.setExtras(Bundle().apply { putString("chromaprint", chromaprint) })
            }

            val mediaItem =  MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(metadata.build())

            id?.let {
                mediaItem.setMediaId(it)
            }

            return mediaItem.build()
        }
    }
}
