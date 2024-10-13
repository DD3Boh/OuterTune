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
import com.dd3boh.outertune.constants.AlbumSortType
import com.dd3boh.outertune.db.entities.Album
import com.dd3boh.outertune.db.entities.AlbumEntity
import com.dd3boh.outertune.db.entities.AlbumWithSongs
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.extensions.reversed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface AlbumsDao {

    // region Gets
    @Query("SELECT * FROM album WHERE id = :id")
    fun album(id: String): Flow<Album?>

    @Query("SELECT * FROM album WHERE id = :albumId")
    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?>

    @Query("SELECT song.* FROM song JOIN song_album_map ON song.id = song_album_map.songId WHERE song_album_map.albumId = :albumId")
    fun albumSongs(albumId: String): Flow<List<Song>>

    @Query("""
        SELECT album.*
        FROM album
                 JOIN(SELECT albumId
                      FROM song
                               JOIN (SELECT songId, SUM(playTime) AS songTotalPlayTime
                                     FROM event
                                     WHERE timestamp > :fromTimeStamp
                                     GROUP BY songId) AS e
                                    ON song.id = e.songId
                      WHERE albumId IS NOT NULL
                      GROUP BY albumId
                      ORDER BY SUM(songTotalPlayTime) DESC
                      LIMIT :limit)
                     ON album.id = albumId
    """)
    fun mostPlayedAlbums(fromTimeStamp: Long, limit: Int = 6): Flow<List<Album>>

    @RawQuery(observedEntities = [AlbumEntity::class])
    fun _getAlbum(query: SupportSQLiteQuery): Flow<List<Album>>

    // region Albums Sort
    private fun queryAlbums(orderBy: String): SimpleSQLiteQuery {
        return SimpleSQLiteQuery("""
            SELECT * FROM album 
            WHERE EXISTS(
                SELECT * 
                FROM song 
                WHERE song.albumId = album.id 
                    AND song.inLibrary IS NOT NULL
           ) ORDER BY $orderBy
        """)
    }

    fun albumsByCreateDateAsc(): Flow<List<Album>> = _getAlbum(queryAlbums("rowId ASC"))
    fun albumsByNameAsc(): Flow<List<Album>> = _getAlbum(queryAlbums("title COLLATE NOCASE ASC"))
    fun albumsByYearAsc(): Flow<List<Album>> = _getAlbum(queryAlbums("year ASC"))
    fun albumsBySongCountAsc(): Flow<List<Album>> = _getAlbum(queryAlbums("songCount ASC"))
    fun albumsByLengthAsc(): Flow<List<Album>> = _getAlbum(queryAlbums("duration ASC"))

    @Query("""
        SELECT * FROM album
        WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL)
        ORDER BY (
            SELECT LOWER(GROUP_CONCAT(name, ''))
            FROM artist
            WHERE id IN (SELECT artistId FROM album_artist_map WHERE albumId = album.id)
            ORDER BY name
        ) COLLATE NOCASE
    """)
    fun albumByArtistAsc(): Flow<List<Album>>

    @Query("""
        SELECT album.*
        FROM album
            JOIN song ON song.albumId = album.id
        WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL)
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """)
    fun albumsByPlayTimeAsc(): Flow<List<Album>>

    fun albums(sortType: AlbumSortType, descending: Boolean) =
        when (sortType) {
            AlbumSortType.CREATE_DATE -> albumsByCreateDateAsc()
            AlbumSortType.NAME -> albumsByNameAsc()
            AlbumSortType.ARTIST -> albumByArtistAsc()
            AlbumSortType.YEAR -> albumsByYearAsc()
            AlbumSortType.SONG_COUNT -> albumsBySongCountAsc()
            AlbumSortType.LENGTH -> albumsByLengthAsc()
            AlbumSortType.PLAY_TIME -> albumsByPlayTimeAsc()
        }.map { it.reversed(descending) }
    // endregion

    // region Liked Albums Sort
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY rowId")
    fun albumsLikedByCreateDateAsc(): Flow<List<Album>>

    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY title COLLATE NOCASE ASC")
    fun albumsLikedByNameAsc(): Flow<List<Album>>

    @Query("""
        SELECT * FROM album
        WHERE bookmarkedAt IS NOT NULL
        ORDER BY (
            SELECT LOWER(GROUP_CONCAT(name, ''))
            FROM artist
            WHERE id IN (SELECT artistId FROM album_artist_map WHERE albumId = album.id)
            ORDER BY name
        ) COLLATE NOCASE
    """)
    fun albumLikeByArtistAsc(): Flow<List<Album>>

    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY year")
    fun albumsLikedByYearAsc(): Flow<List<Album>>

    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY songCount")
    fun albumsLikedBySongCountAsc(): Flow<List<Album>>

    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY duration")
    fun albumsLikedByLengthAsc(): Flow<List<Album>>

    @Query("""
        SELECT album.*
        FROM album
                 JOIN song
                      ON song.albumId = album.id
        WHERE bookmarkedAt IS NOT NULL
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """)
    fun albumsLikedByPlayTimeAsc(): Flow<List<Album>>

    fun albumsLiked(sortType: AlbumSortType, descending: Boolean) =
        when (sortType) {
            AlbumSortType.CREATE_DATE -> albumsLikedByCreateDateAsc()
            AlbumSortType.NAME -> albumsLikedByNameAsc()
            AlbumSortType.ARTIST -> albumLikeByArtistAsc()
            AlbumSortType.YEAR -> albumsLikedByYearAsc()
            AlbumSortType.SONG_COUNT -> albumsLikedBySongCountAsc()
            AlbumSortType.LENGTH -> albumsLikedByLengthAsc()
            AlbumSortType.PLAY_TIME -> albumsLikedByPlayTimeAsc()
        }.map { it.reversed(descending) }
    // endregion

    // region Downloaded Albums Sort
    private fun queryAlbumsWithDonwloads(orderBy: String): SimpleSQLiteQuery {
        return SimpleSQLiteQuery("""
            SELECT * FROM album 
            WHERE EXISTS(
                SELECT * 
                FROM song 
                WHERE song.albumId = album.id 
                    AND song.dateDownload IS NOT NULL
           ) ORDER BY $orderBy
        """)
    }

    fun albumsWithDonwloadsByCreateDateAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsWithDonwloads("rowId ASC"))
    fun albumsWithDonwloadsByNameAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsWithDonwloads("title COLLATE NOCASE ASC"))
    fun albumsWithDonwloadsByYearAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsWithDonwloads("year ASC"))
    fun albumsWithDonwloadsBySongCountAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsWithDonwloads("songCount ASC"))
    fun albumsWithDonwloadsByLengthAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsWithDonwloads("duration ASC"))

    @Query("""
        SELECT * FROM album
        WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.dateDownload IS NOT NULL)
        ORDER BY (
            SELECT LOWER(GROUP_CONCAT(name, ''))
            FROM artist
            WHERE id IN (SELECT artistId FROM album_artist_map WHERE albumId = album.id)
            ORDER BY name
        ) COLLATE NOCASE
    """)
    fun albumWithDonwloadsByArtistAsc(): Flow<List<Album>>

    @Query("""
        SELECT album.*
        FROM album
            JOIN song ON song.albumId = album.id
        WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.dateDownload IS NOT NULL)
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """)
    fun albumsWithDonwloadsByPlayTimeAsc(): Flow<List<Album>>

    fun albumsWithDonwloads(sortType: AlbumSortType, descending: Boolean) =
        when (sortType) {
            AlbumSortType.CREATE_DATE -> albumsWithDonwloadsByCreateDateAsc()
            AlbumSortType.NAME -> albumsWithDonwloadsByNameAsc()
            AlbumSortType.ARTIST -> albumWithDonwloadsByArtistAsc()
            AlbumSortType.YEAR -> albumsWithDonwloadsByYearAsc()
            AlbumSortType.SONG_COUNT -> albumsWithDonwloadsBySongCountAsc()
            AlbumSortType.LENGTH -> albumsWithDonwloadsByLengthAsc()
            AlbumSortType.PLAY_TIME -> albumsWithDonwloadsByPlayTimeAsc()
        }.map { it.reversed(descending) }
    // endregion
    // endregion

    // region Inserts
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(album: AlbumEntity): Long
    // endregion

    // region Updates
    @Update
    fun update(album: AlbumEntity)
    // endregion

    // region Deletes
    @Delete
    fun delete(album: AlbumEntity)

    @Transaction
    @Query("DELETE FROM album WHERE isLocal = 1")
    fun nukeLocalAlbums()
    // endregion
}