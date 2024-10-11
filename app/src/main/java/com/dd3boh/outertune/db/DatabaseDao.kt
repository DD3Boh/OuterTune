package com.dd3boh.outertune.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.dd3boh.outertune.constants.AlbumSortType
import com.dd3boh.outertune.constants.ArtistSongSortType
import com.dd3boh.outertune.constants.ArtistSortType
import com.dd3boh.outertune.constants.PlaylistSortType
import com.dd3boh.outertune.constants.SongSortType
import com.dd3boh.outertune.db.entities.Album
import com.dd3boh.outertune.db.entities.AlbumArtistMap
import com.dd3boh.outertune.db.entities.AlbumEntity
import com.dd3boh.outertune.db.entities.AlbumWithSongs
import com.dd3boh.outertune.db.entities.Artist
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.Event
import com.dd3boh.outertune.db.entities.EventWithSong
import com.dd3boh.outertune.db.entities.FormatEntity
import com.dd3boh.outertune.db.entities.GenreEntity
import com.dd3boh.outertune.db.entities.LyricsEntity
import com.dd3boh.outertune.db.entities.PlayCountEntity
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.db.entities.PlaylistSongMap
import com.dd3boh.outertune.db.entities.QueueEntity
import com.dd3boh.outertune.db.entities.QueueSongMap
import com.dd3boh.outertune.db.entities.RelatedSongMap
import com.dd3boh.outertune.db.entities.SearchHistory
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongAlbumMap
import com.dd3boh.outertune.db.entities.SongArtistMap
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.db.entities.SongGenreMap
import com.dd3boh.outertune.extensions.reversed
import com.dd3boh.outertune.extensions.toSQLiteQuery
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.MultiQueueObject
import com.dd3boh.outertune.models.QueueBoard
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.ui.utils.resize
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.pages.AlbumPage
import com.zionhuang.innertube.pages.ArtistPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneOffset

@Dao
interface DatabaseDao {
    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY rowId")
    fun songsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY inLibrary")
    fun songsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY date")
    fun songsByReleaseDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY dateModified")
    fun songsByDateModifiedAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY title COLLATE NOCASE ASC")
    fun songsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY totalPlayTime")
    fun songsByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT song.*, (SELECT SUM(playCount.count) 
            FROM playCount 
            WHERE playCount.song = song.id) AS pc 
        FROM song 
        WHERE inLibrary IS NOT NULL 
        ORDER BY pc ASC
    """)
    fun songsByPlayCountAsc(): Flow<List<Song>>

    fun songs(sortType: SongSortType, descending: Boolean) =
        when (sortType) {
            SongSortType.CREATE_DATE -> songsByCreateDateAsc()
            SongSortType.MODIFIED_DATE -> songsByDateModifiedAsc()
            SongSortType.RELEASE_DATE -> {
                val songs = songsByReleaseDateAsc()
                runBlocking {
                    flowOf(songs.first().sortedBy {
                        it.song.getDateLong()
                    })
                }
            }
            SongSortType.NAME -> songsByNameAsc()
            SongSortType.ARTIST -> songsByRowIdAsc().map { songs ->
                songs.sortedBy { song ->
                    song.artists.joinToString(separator = "") { it.name }.lowercase()
                }
            }

            SongSortType.PLAY_TIME -> songsByPlayTimeAsc()
        }.map { it.reversed(descending) }

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY rowId")
    fun likedSongsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY likedDate")
    fun likedSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY date")
    fun likedSongsByReleaseDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY dateModified")
    fun likedSongsByDateModifiedAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY title COLLATE NOCASE ASC")
    fun likedSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY totalPlayTime")
    fun likedSongsByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT song.*, (SELECT SUM(playCount.count) 
            FROM playCount 
            WHERE playCount.song = song.id) AS pc 
        FROM song 
        WHERE liked IS NOT NULL 
        ORDER BY pc ASC
    """)
    fun likedSongsByPlayCountAsc(): Flow<List<Song>>

    fun likedSongs(sortType: SongSortType, descending: Boolean) =
        when (sortType) {
            SongSortType.CREATE_DATE -> likedSongsByCreateDateAsc()
            SongSortType.MODIFIED_DATE -> likedSongsByDateModifiedAsc()
            SongSortType.RELEASE_DATE -> {
                val songs = likedSongsByReleaseDateAsc()
                runBlocking {
                    flowOf(songs.first().sortedBy {
                        it.song.getDateLong()
                    })
                }
            }
            SongSortType.NAME -> likedSongsByNameAsc()
            SongSortType.ARTIST -> likedSongsByRowIdAsc().map { songs ->
                songs.sortedBy { song ->
                    song.artists.joinToString(separator = "") { it.name }.lowercase()
                }
            }

            SongSortType.PLAY_TIME -> likedSongsByPlayTimeAsc()
        }.map { it.reversed(descending) }

    @Query("SELECT COUNT(1) FROM song WHERE liked")
    fun likedSongsCount(): Flow<Int>

    @Transaction
    @Query("SELECT song.* FROM song JOIN song_album_map ON song.id = song_album_map.songId WHERE song_album_map.albumId = :albumId")
    fun albumSongs(albumId: String): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position")
    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>>

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

    @Transaction
    @Query("SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL LIMIT :previewSize")
    fun artistSongsPreview(artistId: String, previewSize: Int = 3): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT *, COUNT(1) AS referredCount
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN song ON song.id = map.relatedSongId
        WHERE songId IN (SELECT songId
                         FROM (SELECT songId
                               FROM event
                               ORDER BY ROWID DESC
                               LIMIT 5)
                         UNION
                         SELECT songId
                         FROM (SELECT songId
                               FROM event
                               WHERE timestamp > :now - 86400000 * 7
                               GROUP BY songId
                               ORDER BY SUM(playTime) DESC
                               LIMIT 5)
                         UNION
                         SELECT id
                         FROM (SELECT id
                               FROM song
                               ORDER BY totalPlayTime DESC
                               LIMIT 10))
        ORDER BY referredCount DESC
        LIMIT 100
    """
    )
    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT *
        FROM song
        WHERE id IN (SELECT songId
                     FROM event
                     WHERE timestamp > :fromTimeStamp
                     GROUP BY songId
                     ORDER BY SUM(playTime) DESC
                     LIMIT :limit)
    """
    )
    fun mostPlayedSongs(fromTimeStamp: Long, limit: Int = 6): Flow<List<Song>>

    @Transaction
    @Query(
        """
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
    """
    )
    fun mostPlayedArtists(fromTimeStamp: Long, limit: Int = 6): Flow<List<Artist>>

    @Transaction
    @Query(
        """
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
    """
    )
    fun mostPlayedAlbums(fromTimeStamp: Long, limit: Int = 6): Flow<List<Album>>

    @Query("SELECT sum(count) from playCount WHERE song = :songId")
    fun getLifetimePlayCount(songId: String?): Flow<Int>
    @Query("SELECT sum(count) from playCount WHERE song = :songId AND year = :year")
    fun getPlayCountByYear(songId: String?, year: Int): Flow<Int>
    @Query("SELECT count from playCount WHERE song = :songId AND year = :year AND month = :month")
    fun getPlayCountByMonth(songId: String?, year: Int, month: Int): Flow<Int>


    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId")
    fun song(songId: String?): Flow<Song?>

    @Transaction
    @Query("SELECT * FROM song ORDER BY rowId")
    fun allSongsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song ORDER BY inLibrary")
    fun allSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song ORDER BY date")
    fun allSongsByReleaseDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song ORDER BY dateModified")
    fun allSongsByDateModifiedAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song ORDER BY title COLLATE NOCASE ASC")
    fun allSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song ORDER BY totalPlayTime")
    fun allSongsByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song")
    fun allSongs(): Flow<List<Song>>

    fun allSongs(
        downloads: Set<String>,
        sortType: SongSortType,
        descending: Boolean
    ) = when (sortType) {
            SongSortType.CREATE_DATE -> allSongsByCreateDateAsc()
            SongSortType.MODIFIED_DATE -> allSongsByDateModifiedAsc()
            SongSortType.RELEASE_DATE -> allSongsByReleaseDateAsc()
            SongSortType.NAME -> allSongsByNameAsc()
            SongSortType.ARTIST -> allSongsByRowIdAsc().map { songs ->
                songs.sortedBy { song ->
                    song.artists.joinToString(separator = "") { it.name }.lowercase()
                }
            }
            SongSortType.PLAY_TIME -> allSongsByPlayTimeAsc()
        }.map { songs ->
            songs.filter { song ->
                // show local songs as under downloaded for now
                song.song.isLocal || downloads.any { it == song.song.id }
            }
        }.map { it.reversed(descending) }

    @Transaction
    @Query("SELECT * FROM song WHERE isLocal = 1 and inLibrary IS NOT NULL")
    fun allLocalSongs(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM artist WHERE isLocal != 1")
    fun allRemoteArtists(): Flow<List<ArtistEntity>>

    @Transaction
    @Query("SELECT * FROM artist WHERE isLocal = 1")
    fun allLocalArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM format WHERE id = :id")
    fun format(id: String?): Flow<FormatEntity?>

    @Query("SELECT * FROM lyrics WHERE id = :id")
    fun lyrics(id: String?): Flow<LyricsEntity?>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE songCount > 0 ORDER BY rowId")
    fun artistsByCreateDateAsc(): Flow<List<Artist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE songCount > 0 ORDER BY name COLLATE NOCASE ASC")
    fun artistsByNameAsc(): Flow<List<Artist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE songCount > 0 ORDER BY songCount")
    fun artistsBySongCountAsc(): Flow<List<Artist>>

    @Transaction
    @Query(
        """
        SELECT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN song ON song_artist_map.songId = song.id
                WHERE artistId = artist.id
                  AND song.inLibrary IS NOT NULL) AS songCount
        FROM artist
                 JOIN(SELECT artistId, SUM(totalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN song
                                    ON song_artist_map.songId = song.id
                      GROUP BY artistId
                      ORDER BY totalPlayTime)
                     ON artist.id = artistId
        WHERE songCount > 0
    """
    )
    fun artistsByPlayTimeAsc(): Flow<List<Artist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt")
    fun artistsBookmarkedByCreateDateAsc(): Flow<List<Artist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY name COLLATE NOCASE ASC")
    fun artistsBookmarkedByNameAsc(): Flow<List<Artist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY songCount")
    fun artistsBookmarkedBySongCountAsc(): Flow<List<Artist>>

    @Transaction
    @Query(
        """
        SELECT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN song ON song_artist_map.songId = song.id
                WHERE artistId = artist.id
                  AND song.inLibrary IS NOT NULL) AS songCount
        FROM artist
                 JOIN(SELECT artistId, SUM(totalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN song
                                    ON song_artist_map.songId = song.id
                      GROUP BY artistId
                      ORDER BY totalPlayTime)
                     ON artist.id = artistId
        WHERE bookmarkedAt IS NOT NULL
    """
    )
    fun artistsBookmarkedByPlayTimeAsc(): Flow<List<Artist>>

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

    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE id = :id")
    fun artist(id: String): Flow<Artist?>

    @Transaction
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY rowId")
    fun albumsByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY title COLLATE NOCASE ASC")
    fun albumsByNameAsc(): Flow<List<Album>>

    @Transaction
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY year")
    fun albumsByYearAsc(): Flow<List<Album>>

    @Transaction
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY songCount")
    fun albumsBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY duration")
    fun albumsByLengthAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        """
        SELECT album.*
        FROM album
                 JOIN song
                      ON song.albumId = album.id
        WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL)
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """
    )
    fun albumsByPlayTimeAsc(): Flow<List<Album>>

    @Transaction
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY rowId")
    fun albumsLikedByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY title COLLATE NOCASE ASC")
    fun albumsLikedByNameAsc(): Flow<List<Album>>

    @Transaction
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY year")
    fun albumsLikedByYearAsc(): Flow<List<Album>>

    @Transaction
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY songCount")
    fun albumsLikedBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY duration")
    fun albumsLikedByLengthAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        """
        SELECT album.*
        FROM album
                 JOIN song
                      ON song.albumId = album.id
        WHERE bookmarkedAt IS NOT NULL
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """
    )
    fun albumsLikedByPlayTimeAsc(): Flow<List<Album>>

    fun albums(sortType: AlbumSortType, descending: Boolean) =
        when (sortType) {
            AlbumSortType.CREATE_DATE -> albumsByCreateDateAsc()
            AlbumSortType.NAME -> albumsByNameAsc()
            AlbumSortType.ARTIST -> albumsByCreateDateAsc().map { albums ->
                albums.sortedBy { album ->
                    album.artists.joinToString(separator = "") { it.name }.lowercase()
                }
            }

            AlbumSortType.YEAR -> albumsByYearAsc()
            AlbumSortType.SONG_COUNT -> albumsBySongCountAsc()
            AlbumSortType.LENGTH -> albumsByLengthAsc()
            AlbumSortType.PLAY_TIME -> albumsByPlayTimeAsc()
        }.map { it.reversed(descending) }

    fun albumsLiked(sortType: AlbumSortType, descending: Boolean) =
        when (sortType) {
            AlbumSortType.CREATE_DATE -> albumsLikedByCreateDateAsc()
            AlbumSortType.NAME -> albumsLikedByNameAsc()
            AlbumSortType.ARTIST -> albumsLikedByCreateDateAsc().map { albums ->
                albums.sortedBy { album ->
                    album.artists.joinToString(separator = "") { it.name }.lowercase()
                }
            }

            AlbumSortType.YEAR -> albumsLikedByYearAsc()
            AlbumSortType.SONG_COUNT -> albumsLikedBySongCountAsc()
            AlbumSortType.LENGTH -> albumsLikedByLengthAsc()
            AlbumSortType.PLAY_TIME -> albumsLikedByPlayTimeAsc()
        }.map { it.reversed(descending) }

    @Transaction
    @Query("SELECT * FROM album WHERE id = :id")
    fun album(id: String): Flow<Album?>

    @Transaction
    @Query("SELECT * FROM album WHERE id = :albumId")
    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL OR isLocal = 1 ORDER BY rowId")
    fun playlistsByCreateDateAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL OR isLocal = 1 ORDER BY name COLLATE NOCASE ASC")
    fun playlistsByNameAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL OR isLocal = 1 ORDER BY songCount")
    fun playlistsBySongCountAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE isEditable AND bookmarkedAt IS NOT NULL ORDER BY rowId")
    fun editablePlaylistsByCreateDateAsc(): Flow<List<Playlist>>

    fun playlists(sortType: PlaylistSortType, descending: Boolean) =
        when (sortType) {
            PlaylistSortType.CREATE_DATE -> playlistsByCreateDateAsc()
            PlaylistSortType.NAME -> playlistsByNameAsc()
            PlaylistSortType.SONG_COUNT -> playlistsBySongCountAsc()
        }.map { it.reversed(descending) }

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE id = :playlistId")
    fun playlist(playlistId: String): Flow<Playlist?>

    @Query("SELECT songId from playlist_song_map WHERE playlistId = :playlistId AND songId IN (:songIds)")
    fun playlistDuplicates(
        playlistId: String,
        songIds: List<String>,
    ): List<String>

    @Transaction
    fun addSongToPlaylist(playlist: Playlist, songIds: List<String>) {
        var position = playlist.songCount
        songIds.forEach { id ->
            insert(
                PlaylistSongMap(
                    songId = id,
                    playlistId = playlist.id,
                    position = position++
                )
            )
        }
    }

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE browseId = :browseId")
    fun playlistByBrowseId(browseId: String): Flow<Playlist?>

    @Transaction
    @Query("UPDATE playlist SET isLocal = 1 WHERE id = :playlistId")
    fun playlistDesync(playlistId: String)

    @Transaction
    @Query("SELECT * FROM song WHERE title LIKE '%' || :query || '%' AND inLibrary IS NOT NULL LIMIT :previewSize")
    fun searchSongs(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE title LIKE '%' || :query || '%' AND isLocal = 1 LIMIT :previewSize")
    fun searchSongsAllLocal(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Song>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE name LIKE '%' || :query || '%' AND songCount > 0 LIMIT :previewSize")
    fun searchArtists(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Artist>>

    @Transaction
    @Query("SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE song_artist_map.artistId IN (SELECT id FROM artist WHERE name LIKE '%' || :query || '%') LIMIT :previewSize")
    fun searchArtistSongs(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM artist WHERE name LIKE '%' || :query || '%' LIMIT :previewSize")
    fun fuzzySearchArtists(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<ArtistEntity>>

    @Transaction
    @Query("SELECT * FROM album WHERE title LIKE '%' || :query || '%' AND EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) LIMIT :previewSize")
    fun searchAlbums(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Album>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE name LIKE '%' || :query || '%' LIMIT :previewSize")
    fun searchPlaylists(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId DESC")
    fun events(): Flow<List<EventWithSong>>

    @Query("DELETE FROM event")
    fun clearListenHistory()

    @Query("SELECT * FROM search_history WHERE `query` LIKE :query || '%' ORDER BY id DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistory>>

    @Query("DELETE FROM search_history")
    fun clearSearchHistory()

    @Query("UPDATE song SET totalPlayTime = totalPlayTime + :playTime WHERE id = :songId")
    fun incrementTotalPlayTime(songId: String, playTime: Long)

    @Query("UPDATE playCount SET count = count + 1 WHERE song = :songId AND year = :year AND month = :month")
    fun incrementPlayCount(songId: String, year: Int, month: Int)

    /**
     * Increment by one the play count with today's year and month.
     */
    fun incrementPlayCount(songId: String) {
        val time = LocalDateTime.now().atOffset(ZoneOffset.UTC)
        var oldCount: Int
        runBlocking {
            oldCount = getPlayCountByMonth(songId, time.year, time.monthValue).first()
        }

        // add new
        if (oldCount <= 0) {
            insert(PlayCountEntity(songId, time.year, time.monthValue, 0))
        }
        incrementPlayCount(songId, time.year, time.monthValue)
    }

    @Transaction
    fun toggleInLibrary(songId: String, inLibrary: LocalDateTime?) {
        inLibrary(songId, inLibrary)
        if (inLibrary == null) {
            removeLike(songId)
        }
    }

    @Query("UPDATE song SET inLibrary = :inLibrary WHERE id = :songId")
    fun inLibrary(songId: String, inLibrary: LocalDateTime?)

    @Query("UPDATE song SET liked = 0, likedDate = null WHERE id = :songId")
    fun removeLike(songId: String)

    @Query("UPDATE song SET inLibrary = null WHERE localPath = null")
    fun disableInvalidLocalSongs()
    @Query("UPDATE song SET inLibrary = null, localPath = null WHERE id = :songId")
    fun disableLocalSong(songId: String)

    @Query("""
        SELECT * FROM song
        WHERE localPath IN (
            SELECT localPath
            FROM song
            GROUP BY localPath
            HAVING COUNT(*) > 1
        )
        ORDER BY localPath
    """)
    fun duplicatedLocalSongs(): Flow<List<SongEntity>>

    fun updateLocalSongPath(songId: String, inLibrary: LocalDateTime?, localPath: String?) {
        if (localPath != null) {
            updateLSP(songId, inLibrary, localPath)
        }
    }

    /**
     * DON'T USE THIS DIRECTLY, USE updateLocalSongPath(...) instead!
     */
    @Query("UPDATE song SET inLibrary = :inLibrary, localPath = :localPath WHERE id = :songId")
    fun updateLSP(songId: String, inLibrary: LocalDateTime?, localPath: String)

    @Query("SELECT COUNT(1) FROM related_song_map WHERE songId = :songId LIMIT 1")
    fun hasRelatedSongs(songId: String): Boolean

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

    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    fun clearPlaylist(playlistId: String)

    @Query("SELECT * FROM artist WHERE name = :name")
    fun artistByName(name: String): ArtistEntity?

    @Query("SELECT * FROM genre WHERE title = :name")
    fun genreByName(name: String): GenreEntity?

    @Transaction
    @Query("SELECT * FROM genre WHERE title LIKE '%' || :query || '%' LIMIT :previewSize")
    fun genreByAproxName(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<GenreEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(genre: GenreEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: SongArtistMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: SongAlbumMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: AlbumArtistMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: SongGenreMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: PlaylistSongMap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(searchHistory: SearchHistory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(event: Event)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: RelatedSongMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playCountEntity: PlayCountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(queue: QueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(queueSong: QueueSongMap)

    @Transaction
    fun insert(mediaMetadata: MediaMetadata, block: (SongEntity) -> SongEntity = { it }) {
        if (insert(mediaMetadata.toSongEntity().let(block)) == -1L) return
        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId()
            insert(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    isLocal = artist.isLocal
                )
            )
            insert(
                SongArtistMap(
                    songId = mediaMetadata.id,
                    artistId = artistId,
                    position = index
                )
            )
        }
        mediaMetadata.genre?.forEachIndexed { index, genre ->
            val genreId = genreByName(genre.title)?.id ?: GenreEntity.generateGenreId()
            insert(
                GenreEntity(
                    id = genreId,
                    title = genre.title,
                    isLocal = genre.isLocal
                )
            )
            insert(
                SongGenreMap(
                    songId = mediaMetadata.id,
                    genreId = genreId,
                    index = index
                )
            )
        }
    }

    @Transaction
    fun insert(albumPage: AlbumPage) {
        if (insert(AlbumEntity(
                id = albumPage.album.browseId,
                playlistId = albumPage.album.playlistId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { it.duration ?: 0 }
            )) == -1L
        ) return
        albumPage.songs.map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index
                )
            }
            .forEach(::upsert)
        albumPage.album.artists
            ?.map { artist ->
                ArtistEntity(
                    id = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId(),
                    name = artist.name
                )
            }
            ?.onEach(::insert)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumPage.album.browseId,
                    artistId = artist.id,
                    order = index
                )
            }
            ?.forEach(::insert)
    }

    @Transaction
    fun insert(albumItem: AlbumItem) {
        if (insert(AlbumEntity(
                id = albumItem.browseId,
                playlistId = albumItem.playlistId,
                title = albumItem.title,
                year = albumItem.year,
                thumbnailUrl = albumItem.thumbnail,
                songCount = 0,
                duration = 0
            )) == -1L
        ) return
        albumItem.artists
            ?.map { artist ->
                ArtistEntity(
                    id = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId(),
                    name = artist.name
                )
            }
            ?.onEach(::insert)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumItem.browseId,
                    artistId = artist.id,
                    order = index
                )
            }
            ?.forEach(::insert)
    }

    @Update
    fun update(song: SongEntity)

    @Update
    fun update(artist: ArtistEntity)

    @Update
    fun update(album: AlbumEntity)

    @Update
    fun update(playlist: PlaylistEntity)

    @Update
    fun update(map: PlaylistSongMap)

    @Update
    fun update(queue: QueueEntity)

    fun update(artist: ArtistEntity, artistPage: ArtistPage) {
        update(
            artist.copy(
                name = artistPage.artist.title,
                thumbnailUrl = artistPage.artist.thumbnail.resize(544, 544),
                lastUpdateTime = LocalDateTime.now()
            )
        )
    }

    @Transaction
    fun update(album: AlbumEntity, albumPage: AlbumPage) {
        update(
            album.copy(
                id = albumPage.album.browseId,
                playlistId = albumPage.album.playlistId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { it.duration ?: 0 }
            )
        )
        albumPage.songs.map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index
                )
            }
            .forEach(::upsert)
        albumPage.album.artists
            ?.map { artist ->
                ArtistEntity(
                    id = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId(),
                    name = artist.name
                )
            }
            ?.onEach(::insert)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumPage.album.browseId,
                    artistId = artist.id,
                    order = index
                )
            }
            ?.forEach(::insert)
    }

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
    @Query("UPDATE song_artist_map SET artistId = :newId WHERE artistId = :oldId")
    fun updateSongArtistMap(oldId: String, newId: String)

    @Transaction
    @Query("UPDATE album_artist_map SET artistId = :newId WHERE artistId = :oldId")
    fun updateAlbumArtistMap(oldId: String, newId: String)

    @Upsert
    fun upsert(map: SongAlbumMap)

    @Upsert
    fun upsert(lyrics: LyricsEntity)

    @Upsert
    fun upsert(format: FormatEntity)

    @Delete
    fun delete(song: SongEntity)

    @Delete
    fun delete(artist: ArtistEntity)

    @Delete
    fun delete(album: AlbumEntity)

    @Delete
    fun delete(playlist: PlaylistEntity)

    @Delete
    fun delete(playlistSongMap: PlaylistSongMap)

    @Delete
    fun delete(lyrics: LyricsEntity)

    @Delete
    fun delete(searchHistory: SearchHistory)

    @Delete
    fun delete(event: Event)

    @Delete
    fun delete(mq: QueueEntity)

    @Transaction
    @Query("DELETE FROM song_artist_map WHERE songId = :songID")
    fun unlinkSongArtists(songID: String)

    @Transaction
    @Query("DELETE FROM song WHERE isLocal = 1")
    fun nukeLocalSongs()

    @Transaction
    @Query("DELETE FROM artist WHERE isLocal = 1")
    fun nukeLocalArtists()

    @Transaction
    @Query("DELETE FROM album WHERE isLocal = 1")
    fun nukeLocalAlbums()

    @Transaction
    @Query("DELETE FROM genre WHERE isLocal = 1")
    fun nukeLocalGenre()

    @Transaction
    fun nukeLocalData() {
        nukeLocalSongs()
        nukeLocalArtists()
        nukeLocalAlbums()
        nukeLocalGenre()
    }

    @Query("SELECT * FROM playlist_song_map WHERE songId = :songId")
    fun playlistSongMaps(songId: String): List<PlaylistSongMap>

    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId AND position >= :from ORDER BY position")
    fun playlistSongMaps(playlistId: String, from: Int): List<PlaylistSongMap>

    @RawQuery
    fun raw(supportSQLiteQuery: SupportSQLiteQuery): Int

    fun checkpoint() {
        raw("PRAGMA wal_checkpoint(FULL)".toSQLiteQuery())
    }

    /**
     * Queueboard
     */

    @Transaction
    fun saveQueue(mq: MultiQueueObject) {
        if (mq.queue.isEmpty() || mq.unShuffled.isEmpty()) {
            return
        }

        insert(
            QueueEntity(
                id = mq.id,
                title = mq.title,
                shuffled = mq.shuffled,
                queuePos = mq.queuePos,
                index = mq.index
            )
        )

        deleteAllQueueSongs(mq.id)
        // insert songs
        mq.unShuffled.forEach {
            insert(it)
            insert(
                QueueSongMap(
                    queueId = mq.id,
                    songId = it.id,
                    shuffled = false
                )
            )
        }

        mq.queue.forEach {
            insert(
                QueueSongMap(
                    queueId = mq.id,
                    songId = it.id,
                    shuffled = true
                )
            )
        }
    }

    @Transaction
    fun updateQueue(mq: MultiQueueObject) {
        update(
            QueueEntity(
                id = mq.id,
                title = mq.title,
                shuffled = mq.shuffled,
                queuePos = mq.queuePos,
                index = mq.index,
                playlistId = mq.playlistId
            )
        )
    }

    @Transaction
    fun updateAllQueues(mqs: List<MultiQueueObject>) {
        CoroutineScope(Dispatchers.IO).launch {
            QueueBoard.mutex.withLock { // possible ConcurrentModificationException
                mqs.forEach { updateQueue(it) }
            }
        }
    }

    @Query("SELECT * from queue ORDER BY `index`")
    fun getAllQueues(): Flow<List<QueueEntity>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * from queue_song_map JOIN song ON queue_song_map.songId = song.id WHERE queueId = :queueId AND shuffled = 1")
    fun getQueueSongs(queueId: Long): Flow<List<Song>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * from queue_song_map JOIN song ON queue_song_map.songId = song.id WHERE queueId = :queueId AND shuffled = 0")
    fun getQueueSongsUnshuffled(queueId: Long): Flow<List<Song>>

    fun readQueue(): List<MultiQueueObject> {
        val resultQueues = ArrayList<MultiQueueObject>()
        val queues = runBlocking { getAllQueues().first() }

        queues.forEach { queue ->
            val shuffledSongs = runBlocking { getQueueSongs(queue.id).first() }
            val unshuffledSongs = runBlocking { getQueueSongsUnshuffled(queue.id).first() }

            resultQueues.add(
                MultiQueueObject(
                    id = queue.id,
                    title = queue.title,
                    queue = shuffledSongs.map { it.toMediaMetadata() }.toMutableList(),
                    unShuffled = unshuffledSongs.map { it.toMediaMetadata() }.toMutableList(),
                    shuffled = queue.shuffled,
                    queuePos = queue.queuePos,
                    index = queue.index
                )
            )
        }

        return resultQueues
    }

    @Query("DELETE FROM queue")
    fun deleteAllQueues()

    @Query("DELETE FROM queue_song_map WHERE queueId = :id")
    fun deleteAllQueueSongs(id: Long)

    @Query("DELETE FROM queue WHERE id = :id")
    fun deleteQueue(id: Long)

    /**
     * WARNING: This removes all queue data and re-adds the queue. Did you mean to use updateQueue()?
     */
    @Transaction
    fun rewriteQueue(mq: MultiQueueObject) {
        delete(
            QueueEntity(
                id = mq.id,
                title = mq.title,
                shuffled = mq.shuffled,
                queuePos = mq.queuePos,
                index = mq.index,
                playlistId = mq.playlistId
            )
        )

        saveQueue(mq)
    }

    /**
     * WARNING: This removes ALL queues and their data, and re-adds them. Did you mean to use rewriteQueue()?
     */
    @Transaction
    fun rewriteAllQueues(queues: List<MultiQueueObject>) {
        deleteAllQueues()

        queues.forEach { mq ->
            saveQueue(mq)
        }
    }
}
