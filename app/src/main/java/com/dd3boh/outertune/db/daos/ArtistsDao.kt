package com.dd3boh.outertune.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.dd3boh.outertune.constants.ArtistSongSortType
import com.dd3boh.outertune.constants.ArtistSortType
import com.dd3boh.outertune.db.entities.Artist
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.extensions.reversed
import com.dd3boh.outertune.ui.utils.resize
import com.zionhuang.innertube.pages.ArtistPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

@Dao
interface ArtistsDao {

    // region Gets
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE id = :id")
    fun artist(id: String): Flow<Artist?>

    @Query("SELECT * FROM artist WHERE name = :name")
    fun artistByName(name: String): ArtistEntity?

    @Transaction
    @Query("SELECT * FROM artist WHERE isLocal != 1")
    fun allRemoteArtists(): Flow<List<ArtistEntity>>

    @Transaction
    @Query("SELECT * FROM artist WHERE isLocal = 1")
    fun allLocalArtists(): Flow<List<ArtistEntity>>

    @Transaction
    @Query("SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL LIMIT :previewSize")
    fun artistSongsPreview(artistId: String, previewSize: Int = 3): Flow<List<Song>>

    @Transaction
    @Query("""
        SELECT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN song ON song_artist_map.songId = song.id
                WHERE artistId = artist.id
                  AND song.inLibrary IS NOT NULL) AS songCount
        FROM artist
                 JOIN(SELECT artistId, SUM(songTotalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN (SELECT songId, SUM(playTime) AS songTotalPlayTime
                                     FROM event
                                     WHERE timestamp > :fromTimeStamp
                                     GROUP BY songId) AS e
                                    ON song_artist_map.songId = e.songId
                      GROUP BY artistId
                      ORDER BY totalPlayTime DESC
                      LIMIT :limit)
                     ON artist.id = artistId
    """)
    fun mostPlayedArtists(fromTimeStamp: Long, limit: Int = 6): Flow<List<Artist>>

    @Transaction
    @RawQuery(observedEntities = [ArtistEntity::class])
    fun _getArtists(query: SupportSQLiteQuery): Flow<List<Artist>>

    // region Artists Sort
    private fun queryArtists(orderBy: String): SimpleSQLiteQuery {
        return SimpleSQLiteQuery("""
            SELECT artist.*, COUNT(song.id) AS songCount
            FROM artist
                INNER JOIN song_artist_map ON artist.id = song_artist_map.artistId
                INNER JOIN song ON song_artist_map.songId = song.id
            WHERE song.inLibrary IS NOT NULL
            GROUP BY artist.id
            HAVING songCount > 0
            ORDER BY $orderBy
        """)
    }

    fun artistsByCreateDateAsc(): Flow<List<Artist>> = _getArtists(queryArtists("artist.rowId ASC"))
    fun artistsByNameAsc(): Flow<List<Artist>> = _getArtists(queryArtists("artist.name COLLATE NOCASE ASC"))
    fun artistsBySongCountAsc(): Flow<List<Artist>> = _getArtists(queryArtists("songCount ASC"))
    fun artistsByPlayTimeAsc(): Flow<List<Artist>> = _getArtists(queryArtists("SUM(totalPlayTime) ASC"))

    fun artists(sortType: ArtistSortType, descending: Boolean) =
        when (sortType) {
            ArtistSortType.CREATE_DATE -> artistsByCreateDateAsc()
            ArtistSortType.NAME -> artistsByNameAsc()
            ArtistSortType.SONG_COUNT -> artistsBySongCountAsc()
            ArtistSortType.PLAY_TIME -> artistsByPlayTimeAsc()
        }.map { artists ->
            artists
                .filter { it.artist.isYouTubeArtist || it.artist.isLocalArtist } // temp: add ui to filter by local or remote or something idk
                .reversed(descending)
        }
    // endregion

    // region Bookmarked Artists Sort
    private fun queryArtistsBookmarked(orderBy: String): SimpleSQLiteQuery {
        return SimpleSQLiteQuery("""
            SELECT artist.*, COUNT(song.id) AS songCount
            FROM artist
                INNER JOIN song_artist_map ON artist.id = song_artist_map.artistId
                INNER JOIN song ON song_artist_map.songId = song.id
            WHERE artist.bookmarkedAt IS NOT NULL
            GROUP BY artist.id
            HAVING songCount > 0
            ORDER BY $orderBy
        """)
    }

    fun artistsBookmarkedByCreateDateAsc(): Flow<List<Artist>> = _getArtists(queryArtistsBookmarked("artist.bookmarkedAt ASC"))
    fun artistsBookmarkedByNameAsc(): Flow<List<Artist>> = _getArtists(queryArtistsBookmarked("artist.name COLLATE NOCASE ASC"))
    fun artistsBookmarkedBySongCountAsc(): Flow<List<Artist>> = _getArtists(queryArtistsBookmarked("songCount ASC"))
    fun artistsBookmarkedByPlayTimeAsc(): Flow<List<Artist>> = _getArtists(queryArtistsBookmarked("SUM(totalPlayTime) ASC"))

    fun artistsBookmarked(sortType: ArtistSortType, descending: Boolean) =
        when (sortType) {
            ArtistSortType.CREATE_DATE -> artistsBookmarkedByCreateDateAsc()
            ArtistSortType.NAME -> artistsBookmarkedByNameAsc()
            ArtistSortType.SONG_COUNT -> artistsBookmarkedBySongCountAsc()
            ArtistSortType.PLAY_TIME -> artistsBookmarkedByPlayTimeAsc()
        }.map { artists ->
            artists
                .filter { it.artist.isYouTubeArtist }
                .reversed(descending)
        }
    // endregion

    // region Downloaded Artists Sort
    private fun queryArtistsWithDonwloads(orderBy: String): SimpleSQLiteQuery {
        return SimpleSQLiteQuery("""
            SELECT artist.*, COUNT(song.id) AS songCount
            FROM artist
                INNER JOIN song_artist_map ON artist.id = song_artist_map.artistId
                INNER JOIN song ON song_artist_map.songId = song.id
            WHERE song.dateDownload IS NOT NULL
            GROUP BY artist.id
            HAVING songCount > 0
            ORDER BY $orderBy
        """)
    }

    fun artistsWithDonwloadsByCreateDateAsc(): Flow<List<Artist>> = _getArtists(queryArtistsWithDonwloads("artist.bookmarkedAt ASC"))
    fun artistsWithDonwloadsByNameAsc(): Flow<List<Artist>> = _getArtists(queryArtistsWithDonwloads("artist.name COLLATE NOCASE ASC"))
    fun artistsWithDonwloadsBySongCountAsc(): Flow<List<Artist>> = _getArtists(queryArtistsWithDonwloads("songCount ASC"))
    fun artistsWithDonwloadsByPlayTimeAsc(): Flow<List<Artist>> = _getArtists(queryArtistsWithDonwloads("SUM(totalPlayTime) ASC"))

    fun artistsWithDonwloads(sortType: ArtistSortType, descending: Boolean) =
        when (sortType) {
            ArtistSortType.CREATE_DATE -> artistsWithDonwloadsByCreateDateAsc()
            ArtistSortType.NAME -> artistsWithDonwloadsByNameAsc()
            ArtistSortType.SONG_COUNT -> artistsWithDonwloadsBySongCountAsc()
            ArtistSortType.PLAY_TIME -> artistsWithDonwloadsByPlayTimeAsc()
        }.map { artists ->
            artists
                .filter { it.artist.isYouTubeArtist }
                .reversed(descending)
        }
    // endregion

    // region Artist Songs Sort
    @Transaction
    @Query("SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY inLibrary")
    fun artistSongsByCreateDateAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query("SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY title COLLATE NOCASE ASC")
    fun artistSongsByNameAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query("SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY totalPlayTime")
    fun artistSongsByPlayTimeAsc(artistId: String): Flow<List<Song>>

    fun artistSongs(artistId: String, sortType: ArtistSongSortType, descending: Boolean) =
        when (sortType) {
            ArtistSongSortType.CREATE_DATE -> artistSongsByCreateDateAsc(artistId)
            ArtistSongSortType.NAME -> artistSongsByNameAsc(artistId)
            ArtistSongSortType.PLAY_TIME -> artistSongsByPlayTimeAsc(artistId)
        }.map { it.reversed(descending) }
    // endregion
    // endregion

    // region Inserts
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(artist: ArtistEntity)
    // endregion

    // region Updates
    @Update
    fun update(artist: ArtistEntity)

    @Transaction
    fun update(artist: ArtistEntity, artistPage: ArtistPage) {
        update(
            artist.copy(
                name = artistPage.artist.title,
                thumbnailUrl = artistPage.artist.thumbnail.resize(544, 544),
                lastUpdateTime = LocalDateTime.now()
            )
        )
    }
    // endregion

    // region Deletes
    @Delete
    fun delete(artist: ArtistEntity)

    @Transaction
    @Query("DELETE FROM artist WHERE isLocal = 1")
    fun nukeLocalArtists()
    // endregion
}