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
import com.dd3boh.outertune.constants.PlaylistSortType
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.extensions.reversed
import com.zionhuang.innertube.models.PlaylistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface PlaylistsDao {

    // region Gets
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE id = :playlistId")
    fun playlist(playlistId: String): Flow<Playlist?>

    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position")
    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>>

    @Query("SELECT songId from playlist_song_map WHERE playlistId = :playlistId AND songId IN (:songIds)")
    fun playlistDuplicates(playlistId: String, songIds: List<String>,): List<String>

    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE browseId = :browseId")
    fun playlistByBrowseId(browseId: String): Flow<Playlist?>

    @RawQuery(observedEntities = [PlaylistEntity::class])
    fun _getPlaylists(query: SupportSQLiteQuery): Flow<List<Playlist>>

    // region Playlist Sort
    private fun queryPlaylists(orderBy: String): SimpleSQLiteQuery {
        return SimpleSQLiteQuery("""
            SELECT p.*, COUNT(psm.playlistId) AS songCount
            FROM playlist p
                LEFT JOIN playlist_song_map psm ON p.id = psm.playlistId
            WHERE p.bookmarkedAt IS NOT NULL OR p.isLocal = 1
            GROUP BY p.id
            ORDER BY $orderBy
        """)
    }

    fun playlistsByCreateDateAsc(): Flow<List<Playlist>> = _getPlaylists(queryPlaylists("p.rowId ASC"))
    fun playlistsByNameAsc(): Flow<List<Playlist>> = _getPlaylists(queryPlaylists("p.name COLLATE NOCASE ASC"))
    fun playlistsBySongCountAsc(): Flow<List<Playlist>> = _getPlaylists(queryPlaylists("songCount ASC"))
    fun editablePlaylistsByCreateDateAsc(): Flow<List<Playlist>> = _getPlaylists(queryPlaylists("p.rowId ASC"))

    fun playlists(sortType: PlaylistSortType, descending: Boolean) =
        when (sortType) {
            PlaylistSortType.CREATE_DATE -> playlistsByCreateDateAsc()
            PlaylistSortType.NAME -> playlistsByNameAsc()
            PlaylistSortType.SONG_COUNT -> playlistsBySongCountAsc()
        }.map { it.reversed(descending) }
    // endregion

    // region Playlist Sort with Downloads Sort
    private fun queryPlaylistsWithDownloads(orderBy: String): SimpleSQLiteQuery {
        return SimpleSQLiteQuery("""
            SELECT p.*, COUNT(psm.playlistId) AS songCount
            FROM playlist p
                LEFT JOIN playlist_song_map psm ON p.id = psm.playlistId
                INNER JOIN song s ON psm.songId = s.id and s.dateDownload IS NOT NULL
            WHERE p.bookmarkedAt IS NOT NULL OR p.isLocal = 1
            GROUP BY p.id
            ORDER BY $orderBy
        """)
    }

    fun playlistsWithDownloadsByCreateDateAsc(): Flow<List<Playlist>> = _getPlaylists(queryPlaylistsWithDownloads("p.rowId ASC"))
    fun playlistsWithDownloadsByNameAsc(): Flow<List<Playlist>> = _getPlaylists(queryPlaylistsWithDownloads("p.name COLLATE NOCASE ASC"))
    fun playlistsWithDownloadsBySongCountAsc(): Flow<List<Playlist>> = _getPlaylists(queryPlaylistsWithDownloads("songCount ASC"))

    fun playlistsWithDownloads(sortType: PlaylistSortType, descending: Boolean) =
        when (sortType) {
            PlaylistSortType.CREATE_DATE -> playlistsWithDownloadsByCreateDateAsc()
            PlaylistSortType.NAME -> playlistsWithDownloadsByNameAsc()
            PlaylistSortType.SONG_COUNT -> playlistsWithDownloadsBySongCountAsc()
        }.map { it.reversed(descending) }
    // endregion
    // endregion

    // region Inserts
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playlist: PlaylistEntity)
    // endregion

    // region Updates
    @Update
    fun update(playlist: PlaylistEntity)

    @Update
    fun update(playlistEntity: PlaylistEntity, playlistItem: PlaylistItem) {
        update(playlistEntity.copy(
            name = playlistItem.title,
            browseId = playlistItem.id,
            isEditable = playlistItem.isEditable,
            thumbnailUrl = playlistItem.thumbnail,
            remoteSongCount = playlistItem.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
            playEndpointParams = playlistItem.playEndpoint?.params,
            shuffleEndpointParams = playlistItem.shuffleEndpoint?.params,
            radioEndpointParams = playlistItem.radioEndpoint?.params
        ))
    }

    @Transaction
    @Query("UPDATE playlist SET isLocal = 1 WHERE id = :playlistId")
    fun playlistDesync(playlistId: String)

    @Transaction
    @Query(
        """
        UPDATE playlist_song_map SET position = 
            CASE 
                WHEN position < :fromPosition THEN position + 1
                WHEN position > :fromPosition THEN position - 1
                ELSE :toPosition
            END 
        WHERE playlistId = :playlistId AND position BETWEEN MIN(:fromPosition, :toPosition) AND MAX(:fromPosition, :toPosition)
    """
    )
    fun move(playlistId: String, fromPosition: Int, toPosition: Int)
    // endregion

    // region Deletes
    @Delete
    fun delete(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    fun clearPlaylist(playlistId: String)
    // endregion
}