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
package org.akanework.gramophone.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "song")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val year: Int?,
    val mergeable: Boolean = true
) {
    companion object {
        var hax = 1L

        /**
         * Used for unit tests only. DO NOT use in production.
         */
        fun generateSongIdForUnitTests(): Long {
            return hax++
        }
    }
}
