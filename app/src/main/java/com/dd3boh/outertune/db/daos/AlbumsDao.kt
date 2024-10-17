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
    @Query("""
        SELECT album.*, count(song.dateDownload) downloadCount
        FROM album
            JOIN song ON song.albumId = album.id
        WHERE album.id = :id
        GROUP BY album.id
    """)
    fun album(id: String): Flow<Album?>

    @Query("""
        SELECT album.*, count(song.dateDownload) downloadCount
        FROM album
            JOIN song ON song.albumId = album.id
        WHERE album.title LIKE '%' || :query || '%' AND song.inLibrary IS NOT NULL
        GROUP BY album.id
        LIMIT :previewSize
    """)
    fun searchAlbums(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Album>>

    @Query("""
        SELECT album.*, count(song.dateDownload) downloadCount
        FROM album
            JOIN song ON song.albumId = album.id
        WHERE album.id = :albumId
    """)
    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?>

    @Query("SELECT song.* FROM song JOIN song_album_map ON song.id = song_album_map.songId WHERE song_album_map.albumId = :albumId")
    fun albumSongs(albumId: String): Flow<List<Song>>

    @Query("""
        SELECT album.*, count(song.dateDownload) downloadCount
        FROM album
            JOIN song ON album.id = song.albumId
            JOIN event ON song.id = event.songId
        WHERE event.timestamp > :fromTimeStamp
        GROUP BY album.id
        ORDER BY SUM(event.playTime) DESC
        LIMIT :limit;
    """)
    fun mostPlayedAlbums(fromTimeStamp: Long, limit: Int = 6): Flow<List<Album>>

    @RawQuery(observedEntities = [AlbumEntity::class])
    fun _getAlbum(query: SupportSQLiteQuery): Flow<List<Album>>

    // region Albums Sort
    private fun queryAlbums(orderBy: String): SimpleSQLiteQuery {
        return SimpleSQLiteQuery("""
            SELECT album.*, count(song.dateDownload) downloadCount
            FROM album
                JOIN song ON song.albumId = album.id
            WHERE song.inLibrary IS NOT NULL 
            GROUP BY album.id
            ORDER BY $orderBy
        """)
    }

    fun albumsByCreateDateAsc(): Flow<List<Album>> = _getAlbum(queryAlbums("album.rowId ASC"))
    fun albumsByNameAsc(): Flow<List<Album>> = _getAlbum(queryAlbums("album.title COLLATE NOCASE ASC"))
    fun albumsByYearAsc(): Flow<List<Album>> = _getAlbum(queryAlbums("album.year ASC"))
    fun albumsBySongCountAsc(): Flow<List<Album>> = _getAlbum(queryAlbums("album.songCount ASC"))
    fun albumsByLengthAsc(): Flow<List<Album>> = _getAlbum(queryAlbums("album.duration ASC"))
    fun albumsByPlayTimeAsc(): Flow<List<Album>> = _getAlbum(queryAlbums("SUM(song.totalPlayTime) ASC"))

    @Query("""
        SELECT album.*, count(song.dateDownload) downloadCount
        FROM album
            JOIN song ON song.albumId = album.id
        WHERE song.inLibrary IS NOT NULL 
        GROUP BY album.id
        ORDER BY (
            SELECT LOWER(GROUP_CONCAT(name, ''))
            FROM artist
            WHERE id IN (SELECT artistId FROM album_artist_map WHERE albumId = album.id)
            ORDER BY name
        ) COLLATE NOCASE
    """)
    fun albumByArtistAsc(): Flow<List<Album>>

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
    private fun queryAlbumsLiked(orderBy: String): SimpleSQLiteQuery {
        return SimpleSQLiteQuery("""
            SELECT album.*, count(song.dateDownload) downloadCount
            FROM album
                INNER JOIN song ON song.albumId = album.id
            WHERE bookmarkedAt IS NOT NULL
            GROUP BY album.id
            ORDER BY $orderBy
        """)
    }

    fun albumsLikedByCreateDateAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsLiked("album.rowId ASC"))
    fun albumsLikedByNameAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsLiked("album.title COLLATE NOCASE ASC"))
    fun albumsLikedByYearAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsLiked("album.year ASC"))
    fun albumsLikedBySongCountAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsLiked("album.songCount ASC"))
    fun albumsLikedByLengthAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsLiked("album.duration ASC"))

    @Query("""
        SELECT album.*, count(song.dateDownload) downloadCount 
        FROM album
            INNER JOIN song ON song.albumId = album.id
        WHERE bookmarkedAt IS NOT NULL
        GROUP BY album.id
        ORDER BY (
            SELECT LOWER(GROUP_CONCAT(name, ''))
            FROM artist
            WHERE id IN (SELECT artistId FROM album_artist_map WHERE albumId = album.id)
            ORDER BY name
        ) COLLATE NOCASE
    """)
    fun albumLikeByArtistAsc(): Flow<List<Album>>

    @Query("""
        SELECT album.*, count(song.dateDownload) downloadCount 
        FROM album
            INNER JOIN song ON song.albumId = album.id
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
            SELECT album.*, count(song.dateDownload) downloadCount
            FROM album
                INNER JOIN song ON song.albumId = album.id AND song.dateDownload IS NOT NULL
            GROUP BY album.id
            ORDER BY $orderBy
        """)
    }

    fun albumsWithDonwloadsByCreateDateAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsWithDonwloads("album.rowId ASC"))
    fun albumsWithDonwloadsByNameAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsWithDonwloads("album.title COLLATE NOCASE ASC"))
    fun albumsWithDonwloadsByYearAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsWithDonwloads("album.year ASC"))
    fun albumsWithDonwloadsBySongCountAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsWithDonwloads("album.songCount ASC"))
    fun albumsWithDonwloadsByLengthAsc(): Flow<List<Album>> = _getAlbum(queryAlbumsWithDonwloads("album.duration ASC"))

    @Query("""
        SELECT *, count(song.dateDownload) downloadCount
        FROM album
            INNER JOIN song ON song.albumId = album.id
        WHERE song.dateDownload IS NOT NULL
        GROUP BY album.id
        ORDER BY (
            SELECT LOWER(GROUP_CONCAT(name, ''))
            FROM artist
            WHERE id IN (SELECT artistId FROM album_artist_map WHERE albumId = album.id)
            ORDER BY name
        ) COLLATE NOCASE
    """)
    fun albumWithDonwloadsByArtistAsc(): Flow<List<Album>>

    @Query("""
        SELECT album.*, count(song.dateDownload) downloadCount
        FROM album
            INNER JOIN song ON song.albumId = album.id
        WHERE song.dateDownload IS NOT NULL
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