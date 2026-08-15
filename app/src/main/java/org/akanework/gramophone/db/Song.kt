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

import androidx.compose.ui.util.fastSumBy
import androidx.room.Embedded
import androidx.room.Relation
import org.akanework.gramophone.db.entities.ChromaprintEntity
import org.akanework.gramophone.db.entities.PlayEventEntity
import org.akanework.gramophone.db.entities.PlayEventLegacyEntity
import org.akanework.gramophone.db.entities.SongEntity

data class Song(
    @Embedded val song: SongEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "songId"
    )
    val chromaprints: List<ChromaprintEntity>
)

data class SongWithPlaycount(
    @Embedded val song: SongEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "songId"
    )
    val chromaprints: List<ChromaprintEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "songId"
    )
    val playEvents: List<PlayEventEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "songId"
    )
    val playEventsLegacy: List<PlayEventLegacyEntity>,
) {
    val totalPlaycount
        get() = playEvents.size + playEventsLegacy.fastSumBy { it.count }
    val playcount
        get() = playEvents.size
    val playcountLegacy
        get() = playEventsLegacy.fastSumBy { it.count }
}

data class PlayEventWithSong(
    @Embedded val event: PlayEventEntity,
    @Relation(
        parentColumn = "songId",
        entityColumn = "id"
    )
    val song: SongEntity
)
