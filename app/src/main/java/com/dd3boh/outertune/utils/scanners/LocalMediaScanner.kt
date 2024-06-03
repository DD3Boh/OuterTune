package com.dd3boh.outertune.utils.scanners

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.dd3boh.outertune.constants.ScannerImpl
import com.dd3boh.outertune.constants.ScannerMatchCriteria
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.AlbumEntity
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.FormatEntity
import com.dd3boh.outertune.db.entities.GenreEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongArtistMap
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.models.DirectoryTree
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.ui.utils.ARTIST_SEPARATORS
import com.dd3boh.outertune.ui.utils.SCANNER_CRASH_AT_FIRST_ERROR
import com.dd3boh.outertune.ui.utils.SCANNER_DEBUG
import com.dd3boh.outertune.ui.utils.SYNC_SCANNER
import com.dd3boh.outertune.models.SongTempData
import com.dd3boh.outertune.ui.utils.cacheDirectoryTree
import com.dd3boh.outertune.ui.utils.projection
import com.dd3boh.outertune.ui.utils.scannerSession
import com.dd3boh.outertune.ui.utils.storageRoot
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.utils.parseTime
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.lang.Integer.parseInt
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class LocalMediaScanner {

    /**
     * Compiles a song with all it's necessary metadata. Unlike MediaStore,
     * this also supports multiple artists, multiple genres (TBD), and a few extra details (TBD).
     */
    private fun advancedScan(
        basicData: SongTempData,
        database: MusicDatabase,
        scannerImpl: ScannerImpl,
    ): Song {
        val artists = ArrayList<ArtistEntity>()
        val genres = ArrayList<GenreEntity>()
        var year: Int? = null
        var date: LocalDateTime? = null

        // MediaStore mode
        var rawArtists = basicData.artist
        var rawGenres = basicData.genre
        var rawDate = basicData.date


        try {
            // decide which scanner to use
            val scanner = getAdvancedScanner(scannerImpl)
            var ffmpegData: ExtraMetadataWrapper? = null
            if (scannerImpl == ScannerImpl.MEDIASTORE_FFPROBE) {
                ffmpegData = scanner?.getMediaStoreSupplement(basicData.path)
                rawArtists = ffmpegData?.artists
                rawGenres = ffmpegData?.genres
                rawDate = ffmpegData?.date
            } else if (scannerImpl == ScannerImpl.FFPROBE) {
                ffmpegData = scanner?.getAllMetadata(basicData.path, basicData.formatEntity)
                rawArtists = ffmpegData?.artists
                rawGenres = ffmpegData?.genres
                rawDate = ffmpegData?.date
            }

            // parse artist
            rawArtists?.split(ARTIST_SEPARATORS)?.forEach { element ->
                val artistVal = element.trim()
                artists.add(ArtistEntity(ArtistEntity.generateArtistId(), artistVal, isLocal = true))
            }

            // parse genre
            rawGenres?.split(";")?.forEach { element ->
                val genreVal = element.trim()
                genres.add(GenreEntity(GenreEntity.generateGenreId(), genreVal, isLocal = true))
            }

            // parse year
            if (scannerImpl == ScannerImpl.MEDIASTORE) {
                year = basicData.date?.let { parseInt(it) }
            } else {
                if (rawDate != null) {
                    try {
                        date = LocalDate.parse(rawDate.substringAfter(';').trim()).atStartOfDay()
                    } catch (e: Exception) { }

                    year = date?.year ?: parseInt(rawDate.trim())
                }
            }

            // file format info
            if (scannerImpl == ScannerImpl.FFPROBE && ffmpegData?.format != null) {
                database.query {
                    upsert(
                        ffmpegData.format!!
                    )
                }
            } else { // MEDIASTORE_FFPROBE and MEDIASTORE
                database.query {
                    upsert(
                        basicData.formatEntity
                    )
                }
            }
        } catch (e: Exception) {
            if (SCANNER_CRASH_AT_FIRST_ERROR) {
                throw Exception("HALTING AT FIRST SCANNER ERROR " + e.message) // debug
            }
            // fallback on media store
            if (SCANNER_DEBUG) {
                Timber.tag(TAG).d(
                    "ERROR READING ARTIST METADATA: ${e.message}" +
                            " Falling back on MediaStore for ${basicData.path}"
                )
                e.printStackTrace()
            }

            if (basicData.artist?.isNotBlank() == true) {
                if (SCANNER_DEBUG) {
                    Timber.tag(TAG).d(
                        "Adding local artist with name: ${basicData.artist}"
                    )
                    artists.add(ArtistEntity(ArtistEntity.generateArtistId(), basicData.artist, isLocal = true))
                }
            }
        }

        return Song(
            SongEntity(
                id = basicData.id,
                title = basicData.title,
                duration = (basicData.duration / 1000), // we use seconds for duration
                albumId = basicData.albumID,
                albumName = basicData.album,
                year = year,
                date = date,
                dateModified = date,
                isLocal = true,
                inLibrary = LocalDateTime.now(),
                localPath = basicData.path
            ),
            artists,
            // album not working
            basicData.albumID?.let {
                basicData.album?.let { it1 ->
                    AlbumEntity(
                        it,
                        title = it1,
                        duration = 0,
                        songCount = 1
                    )
                }
            },
            genre = genres
        )
    }


    /**
     * Scan MediaStore for songs given a list of paths to scan for.
     * This will replace all data in the database for a given song.
     *
     * @param context Context
     * @param scanPaths List of whitelist paths to scan under. This assumes
     * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
     * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun scanLocal(
        context: Context,
        database: MusicDatabase,
        scannerImpl: ScannerImpl,
        scanPaths: List<String>,
        excludedScanPaths: List<String>,
    ): MutableStateFlow<DirectoryTree> {
        var newDirectoryStructure = DirectoryTree(storageRoot)
        val contentResolver: ContentResolver = context.contentResolver
        val (selection, selectionArgs) = parseScannerFilter(scanPaths) // path whitelist

        // Query for audio files
        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs.toTypedArray(),
            null
        )
        Timber.tag(TAG).d("------------ SCAN: Starting Full Scanner ------------")

        val scannerJobs = ArrayList<Deferred<Song>>()
        runBlocking {
            // MediaStore is our "basic" scanner & file discovery
            cursor?.use { cursor ->
                // Columns indices
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIDColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                val storageVolumeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME)

                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val bitrateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)

                while (cursor.moveToNext()) {
                    val id = SongEntity.generateSongId()
                    val name = cursor.getString(nameColumn) // file name
                    var title = cursor.getString(titleColumn) // song title
                    val duration = cursor.getInt(durationColumn)
                    val artist = cursor.getString(artistColumn)
                    val artistID = cursor.getString(artistIdColumn)
                    val albumID = cursor.getString(albumIDColumn)
                    val album = cursor.getString(albumColumn)
                    val year = cursor.getString(yearColumn)
                    val dateModified = cursor.getString(dateModifiedColumn)
                    val path = cursor.getString(pathColumn)
                    val storageVol = cursor.getString(storageVolumeColumn)

                    // extra stream info
                    val bitrate = cursor.getInt(bitrateColumn)
                    val mime = cursor.getString(mimeColumn)

                    if (title.isBlank()) { // songs with no title tag
                        title = name.substringBeforeLast('.')
                    }

                    if (SCANNER_DEBUG)
                        Timber.tag(TAG)
                            .d("ID: $id, Name: $name, ARTIST: $artist, PATH: $storageVol --> $path")

                    val exclPaths = excludedScanPaths.map { getRealPathFromUri(it) }
                    val realPath = getRealPath(storageVol, path, name)

                    // excluded paths
                    val realPathDir = realPath.substringBeforeLast('/') // get dir
                    if (exclPaths.any { realPathDir.startsWith(it) }) {
                        if (SCANNER_DEBUG)
                            Timber.tag(TAG).d("$realPathDir is excluded")
                        continue
                    }

                    // append song to list
                    // media store doesn't support multi artists...
                    // do not link album (and whatever song id) with youtube yet, figure that out later

                    if (!SYNC_SCANNER) {
                        // use async scanner
                        scannerJobs.add(
                            async(scannerSession) {
                                if (scannerRequestCancel) {
                                    if (SCANNER_DEBUG)
                                        Timber.tag(TAG).d("WARNING: Canceling advanced scanner job.")
                                    throw ScannerAbortException("")
                                }
                                advancedScan(
                                    SongTempData(
                                        id, realPath,
                                        title, duration, artist, artistID, album, albumID,
                                        FormatEntity(
                                            id = id,
                                            itag = -1,
                                            mimeType = mime,
                                            codecs = mime.substringAfter('/'),
                                            bitrate = bitrate,
                                            sampleRate = -1,
                                            contentLength = duration.toLong(),
                                            loudnessDb = null,
                                            playbackUrl = null
                                        ), "", year, dateModified,
                                    ),
                                    database, scannerImpl,
                                )
                            }
                        )
                    } else {
                        if (scannerRequestCancel) {
                            if (SCANNER_DEBUG)
                                Timber.tag(TAG).d("WARNING: Requested to cancel Full Scanner. Aborting.")
                            scannerRequestCancel = false
                            throw ScannerAbortException("Scanner canceled during Full Scanner (synchronous)")
                        }

                        // force synchronous scanning of songs
                        val toInsert = advancedScan(
                            SongTempData(
                                id, realPath,
                                title, duration, artist, artistID, album, albumID,
                                FormatEntity(
                                    id = id,
                                    itag = -1,
                                    mimeType = mime,
                                    codecs = mime.substringAfter('/'),
                                    bitrate = bitrate,
                                    sampleRate = -1,
                                    contentLength = duration.toLong(),
                                    loudnessDb = null,
                                    playbackUrl = null
                                ), "", year, dateModified,
                            ), database, scannerImpl
                        )
                        toInsert.song.localPath?.let { s ->
                            newDirectoryStructure.insert(
                                s.substringAfter(storageRoot), toInsert
                            )
                        }
                    }
                }
            }

            if (!SYNC_SCANNER) {
                // use async scanner
                scannerJobs.awaitAll()
                if (scannerRequestCancel) {
                    if (SCANNER_DEBUG)
                        Timber.tag(TAG).d("WARNING: Requested to cancel Full Scanner. Aborting.")
                    scannerRequestCancel = false
                    throw ScannerAbortException("Scanner canceled during Full Scanner (asynchronous)")
                }
            }
        }

        // build the tree
        scannerJobs.forEach {
            val song = it.getCompleted()

            song.song.localPath?.let { s ->
                newDirectoryStructure.insert(
                    s.substringAfter(storageRoot), song
                )
            }
        }

        Timber.tag(TAG).d("------------ SCAN: Finished Full Scanner ------------")
        cacheDirectoryTree(newDirectoryStructure.androidStorageWorkaround().trimRoot())
        return MutableStateFlow(newDirectoryStructure)
    }

    /**
     * Update the Database with local files
     *
     * @param database
     * @param newSongs
     * @param matchStrength How lax should the scanner be
     * @param strictFileNames Whether to consider file names
     * @param refreshExisting Setting this this to true will updated existing songs
     * with new information, else existing song's data will not be touched, regardless
     * whether it was actually changed on disk
     *
     * Inserts a song if not found
     * Updates a song information depending on if refreshExisting value
     */
    fun syncDB(
        database: MusicDatabase,
        newSongs: List<Song>,
        matchStrength: ScannerMatchCriteria,
        strictFileNames: Boolean,
        refreshExisting: Boolean? = false
    ) {
        Timber.tag(TAG).d("------------ SYNC: Starting Local Library Sync ------------")
        Timber.tag(TAG).d("Entries to process: ${newSongs.size}")

        newSongs.forEach { song ->
            if (scannerRequestCancel) {
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("WARNING: Requested to cancel Local Library Sync. Aborting.")
                scannerRequestCancel = false
                throw ScannerAbortException("Scanner canceled during Local Library Sync")
            }

            val querySong = database.searchSongsInclNotInLibrary(song.song.title)

            runBlocking(Dispatchers.IO) {

                // check if this song is known to the library
                val songMatch = querySong.first().filter {
                    return@filter compareSong(it, song, matchStrength, strictFileNames)
                }

                if (SCANNER_DEBUG) {
                    Timber.tag(TAG)
                        .d("Found songs that match: ${songMatch.size}, Total results from database: ${querySong.first().size}")
                    if (songMatch.isNotEmpty()) {
                        Timber.tag(TAG)
                            .d("FIRST Found songs ${songMatch.first().song.title}")
                    }
                }


                if (songMatch.isNotEmpty() && refreshExisting == true) { // known song, update the song info in the database
                    Timber.tag(TAG)
                        .d("Found in database, updating song: ${song.song.title}")
                    val songToUpdate = songMatch.first()
                    database.update(songToUpdate.song)

                    // destroy existing artist links
                    database.unlinkSongArtists(songToUpdate.id)

                    // update artists
                    var artistPos = 0
                    song.artists.forEach {
                        val dbArtist = database.searchArtists(it.name).firstOrNull()?.firstOrNull()

                        if (dbArtist == null) {
                            // artist does not exist in db, add it then link it
                            database.insert(it)
                            database.insert(SongArtistMap(songToUpdate.id, it.id, artistPos))
                        } else {
                            // artist does  exist in db, link to it
                            database.insert(SongArtistMap(songToUpdate.id, dbArtist.artist.id, artistPos))
                        }

                        artistPos++
                    }
                } else if (songMatch.isEmpty()) { // new song
                    if (SCANNER_DEBUG)
                        Timber.tag(TAG)
                            .d("NOT found in database, adding song: ${song.song.title}")

                    database.insert(song.toMediaMetadata())
                }
                // do not delete songs from database automatically, we just disable them
                disableSongs(database)
            }
        }
        Timber.tag(TAG).d("------------ SYNC: Finished Local Library Sync ------------")
    }

    /**
     * A faster scanner implementation that adds new songs to the database,
     * and does not touch older songs entires (apart from removing
     * inacessable songs from libaray).
     *
     * No remote artist lookup is done
     *
     * WARNING: cachedDirectoryTree is not refreshed and may lead to inconsistencies.
     * It is highly recommend to rebuild the tree after scanner operation
     *
     * @param newSongs List of songs. This is expecting a barebones DirectoryTree
     * (only paths are necessary), thus you may use the output of refreshLocal().toList()
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun quickSync(
        database: MusicDatabase,
        newSongs: List<Song>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean,
        scannerImpl: ScannerImpl,
    ) {
        Timber.tag(TAG).d("------------ SYNC: Starting Quick (additive delta) Library Sync ------------")
        Timber.tag(TAG).d("Entries to process: ${newSongs.size}")

        val mData = MediaMetadataRetriever()

        runBlocking(Dispatchers.IO) {
            // get list of all songs in db, then get songs unknown to the database
            val allSongs = database.allLocalSongs().first()
            val delta = newSongs.filterNot {
                allSongs.any { dbSong -> compareSong(it, dbSong, matchCriteria, strictFileNames) }
            }

            val artistsWithMetadata = ArrayList<Song>()
            val scannerJobs = ArrayList<Deferred<Song>>()
            runBlocking {
                // Get song basic metadata
                delta.forEach { s ->
                    if (scannerRequestCancel) {
                        if (SCANNER_DEBUG)
                            Timber.tag(TAG).d("WARNING: Requested to cancel. Aborting.")
                        scannerRequestCancel = false
                        throw ScannerAbortException("Scanner canceled during Quick (additive delta) Library Sync")
                    }
                    mData.setDataSource(s.song.localPath)

                    val id = SongEntity.generateSongId()
                    var title =
                        mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).let { it ?: "" } // song title
                    val duration =
                        Integer.parseInt(mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!)
                    val artist = mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val artistID = if (artist == null) ArtistEntity.generateArtistId() else null
                    val album = mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    val albumID = if (album == null) AlbumEntity.generateAlbumId() else null
                    val year = mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    val dateModified = mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    val genre = mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    // path should never be null since its coming from directory tree scanner
                    // but Kotlin is too dumb to care. Just ruthlessly suppress the error...
                    val path = "" + s.song.localPath

                    // extra stream info
                    val bitrate = mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let {
                        Integer.parseInt(
                            it
                        )
                    }
                    val mime = "" + mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    var sampleRate: Int? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        sampleRate = mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.let {
                            Integer.parseInt(
                                it
                            )
                        }
                    }

                    if (title.isBlank()) { // songs with no title tag
                        title = path.substringAfterLast('/').substringBeforeLast('.')
                    }


                    if (SCANNER_DEBUG)
                        Timber.tag(TAG)
                            .d("ID: $id, Title: $title, ARTIST: $artist, PATH: $path")

                    // append song to list
                    // media store doesn't support multi artists...
                    // do not link album (and whatever song id) with youtube yet, figure that out later

                    if (!SYNC_SCANNER) {
                        // use async scanner
                        scannerJobs.add(
                            async(scannerSession) {
                                advancedScan(
                                    SongTempData(
                                        id, path, title, duration, artist, artistID, album, albumID,
                                        FormatEntity(
                                            id = id,
                                            itag = -1,
                                            mimeType = mime,
                                            codecs = mime.substringAfter('/'),
                                            bitrate = bitrate ?: -1,
                                            sampleRate = sampleRate,
                                            contentLength = duration.toLong(),
                                            loudnessDb = null,
                                            playbackUrl = null
                                        ), "", year, dateModified,
                                    ), database, scannerImpl // no online artist lookup
                                )
                            }
                        )
                    } else {
                        // force synchronous scanning of songs
                        val toInsert = advancedScan(
                            SongTempData(
                                id, path, title, duration, artist, artistID, album, albumID,
                                FormatEntity(
                                    id = id,
                                    itag = -1,
                                    mimeType = mime,
                                    codecs = mime.substringAfter('/'),
                                    bitrate = bitrate ?: -1,
                                    sampleRate = sampleRate,
                                    contentLength = duration.toLong(),
                                    loudnessDb = null,
                                    playbackUrl = null
                                ), genre, year, dateModified,
                            ), database, scannerImpl
                        )
                        artistsWithMetadata.add(toInsert)
                    }
                }
            }

            if (!SYNC_SCANNER) {
                // use async scanner
                scannerJobs.awaitAll()
            }

            // add to finished list
            scannerJobs.forEach {
                artistsWithMetadata.add(it.getCompleted())
            }

            if (delta.isNotEmpty()) {
                syncDB(database, artistsWithMetadata, matchCriteria, strictFileNames)
            }

            disableSongs(database)
        }
        Timber.tag(TAG).d("------------ SYNC: Finished Quick (additive delta) Library Sync ------------")
    }

    /**
     * Converts all local artists to remote artists if possible
     */
    fun localToRemoteArtist(database: MusicDatabase) {
        runBlocking(Dispatchers.IO) {
            val allLocal = database.allLocalArtists().first()
            val scannerJobs = ArrayList<Deferred<Unit?>>()

            allLocal.forEach { element ->
                val artistVal = element.name.trim()

                // check if this artist exists in DB already
                val databaseArtistMatch =
                    runBlocking(Dispatchers.IO) {
                        database.searchArtists(artistVal).first().filter { artist ->
                            // only look for remote artists here
                            return@filter artist.artist.name == artistVal && !artist.artist.isLocalArtist
                        }
                    }

                if (SCANNER_DEBUG)
                    Timber.tag(TAG)
                        .d("ARTIST FOUND IN DB??? Results size: ${databaseArtistMatch.size}")

                scannerJobs.add(
                    async(scannerSession) {
                        // cancel here since this is where the real heavy action is
                        if (scannerRequestCancel) {
                            if (SCANNER_DEBUG)
                                Timber.tag(TAG).d("WARNING: Requested to cancel youtubeArtistLookup job. Aborting.")
                            throw ScannerAbortException("Scanner canceled during youtubeArtistLookup job")
                        }

                        // resolve artist from YTM if not found in DB
                        if (databaseArtistMatch.isEmpty()) {
                            try {
                                youtubeArtistLookup(artistVal)?.let {
                                    // add new artist, switch all old references, then delete old one
                                    database.insert(it)
                                    swapArtists(element, it, database)
                                }
                            } catch (e: Exception) {
                                // don't touch anything if ytm fails --> keep old artist
                            }
                        } else {
                            // swap with database artist
                            swapArtists(element, databaseArtistMatch.first().artist, database)
                        }
                    }
                )
            }

            scannerJobs.awaitAll()

            if (scannerRequestCancel) {
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("WARNING: Requested to cancel during localToRemoteArtist. Aborting.")
                throw ScannerAbortException("Scanner canceled during localToRemoteArtist")
            }
        }
    }


    /**
     * Remove inaccessible songs from the library
     */
    private fun disableSongs(database: MusicDatabase) {
        runBlocking(Dispatchers.IO) {
            // get list of all songs in db
            val allSongs = database.allLocalSongs().first()

            for (song in allSongs) {
                if (song.song.localPath == null) {
                    database.inLibrary(song.id, null)
                    continue
                }

                val f = File(song.song.localPath)
                // we can't play non-existent file or if it becomes a directory
                if (!f.exists() || f.isDirectory()) {
                    if (SCANNER_DEBUG)
                        Timber.tag(TAG).d("Disabling song ${song.song.localPath}")
                    database.inLibrary(song.song.id, null)
                }
            }
        }
    }


    /**
     * Destroys all local library data (local songs and artists, does not include YTM downloads)
     * from the database
     */
    fun nukeLocalDB(database: MusicDatabase) {
        Timber.tag(TAG)
            .w("NUKING LOCAL FILE LIBRARY FROM DATABASE! Nuke status: ${database.nukeLocalData()}")
    }

    /**
     * Destroys all local library data (local songs and artists, does not include YTM downloads)
     * from the database, then rebuilds it.
     *
     * @param database
     * @param newSongs
     * @param matchCriteria How lax should the scanner be
     * @param strictFileNames Whether to consider file names
     */
    fun destructiveRescanDB(
        database: MusicDatabase,
        newSongs: List<Song>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean
    ) {
        nukeLocalDB(database)
        syncDB(database, newSongs, matchCriteria, strictFileNames)
    }


    companion object {
        // do not put any thing that should adhere to the scanner lock in here
        const val TAG = "LocalMediaScanner"

        private var localScanner: LocalMediaScanner? = null
        private var advancedScannerImpl: MetadataScanner? = null

        /**
         * TODO: Create a lock for background jobs like youtubeartists and etc
         */
        var scannerActive = MutableStateFlow(false)
        var scannerFinished = MutableStateFlow(false)
        var scannerRequestCancel = false

        /**
         * ==========================
         * Scanner management
         * ==========================
         */

        /**
         * Trust me bro, it should never be null
         */
        fun getScanner(): LocalMediaScanner {
            if (localScanner == null) {
                localScanner = LocalMediaScanner()
            }

            return localScanner!!
        }

        fun destroyScanner() {
            localScanner = null
            unloadAdvancedScanner()
        }

        /**
         * TODO: Docs here
         */
        fun getAdvancedScanner(scannerImpl: ScannerImpl): MetadataScanner? {
            // kotlin won't let me return MetadataScanner even if it cant possibly be null broooo
            return when (scannerImpl) {
                ScannerImpl.FFPROBE, ScannerImpl.MEDIASTORE_FFPROBE ->
                    if (advancedScannerImpl is FFProbeScanner) advancedScannerImpl else FFProbeScanner()

                ScannerImpl.MEDIASTORE -> null
            }
        }

        fun unloadAdvancedScanner() {
            advancedScannerImpl = null
        }


        /**
         * ==========================
         * Scanner helpers
         * ==========================
         */


        data class ScannerFilter(val selection: String, val selectionArgs: MutableList<String>)

        /**
         * Parse a list of paths scan into a format understood by the MediaStore scanner
         */
        private fun parseScannerFilter(scanPaths: List<String>): ScannerFilter {
            val selection = StringBuilder("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            val selectionArgs = mutableListOf<String>()

            scanPaths.forEachIndexed { index, path ->

                if (path.isNotBlank()) {
                    val convertedPath = getRealPathFromUri(path)

                    if (index == 0) {
                        selection.append(" AND (")
                    } else {
                        selection.append(" OR ")
                    }
                    selection.append("${MediaStore.Audio.Media.DATA} LIKE ?")
                    selectionArgs.add("$convertedPath%")
                }

            }
            selection.append(")")

            return ScannerFilter(selection.toString(), selectionArgs)
        }

        /**
         * I honestly do not remember what that is for but its necessary
         */
        private fun getRealPath(storageVol: String, path:String, name: String): String {
            return if (storageVol.contains("external_primary")) {
                "${storageRoot}emulated/0/$path$name"
            } else {
                // WHY IS THIS THE LOWERCASE VARIANT WHEN ITS UPPERCASE ON DISK???
                "$storageRoot${storageVol.uppercase(Locale.getDefault())}/$path$name"
            }
        }

        /**
         * Get real path from UI
         * @param path in format "/tree/<media>:<rest of path>"
         */
        private fun getRealPathFromUri(path: String): String {
            val primaryStorageRoot = Environment.getExternalStorageDirectory().absolutePath
            // Google plz don't change ur api kthx
            val storageMedia = path.substringAfter("/tree/").substringBefore(':')
            return if (storageMedia == "primary") {
                path.replaceFirst("/tree/primary:", "$primaryStorageRoot/")
            } else {
                "/storage/$storageMedia/${path.substringAfter(':')}"
            }
        }

        /**
         * Quickly rebuild a skeleton directory tree of local files based on the database
         *
         * Notes:
         * If files move around, that's on you to re run the scanner.
         * If the metadata changes, that's also on you to re run the scanner.
         *
         * @param context Context
         * @param scanPaths List of whitelist paths to scan under. This assumes
         * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
         * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
         */
        fun refreshLocal(
            context: Context,
            database: MusicDatabase,
            scanPaths: List<String>
        ): MutableStateFlow<DirectoryTree> {
            var newDirectoryStructure = DirectoryTree(storageRoot)

            // get songs from db
            var existingSongs: List<Song>
            runBlocking(Dispatchers.IO) {
                existingSongs = database.allLocalSongs().first()
            }

            // Query for audio files
            val contentResolver: ContentResolver = context.contentResolver
            val (selection, selectionArgs) = parseScannerFilter(scanPaths) // path whitelist
            val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs.toTypedArray(),
                null
            )
            Timber.tag(TAG).d("------------ SCAN: Starting Quick Directory Rebuild ------------")
            cursor?.use { localCursor ->
                // Columns indices
                val nameColumn = localCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathColumn = localCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                val storageVolumeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME)

                while (localCursor.moveToNext()) {
                    val name = localCursor.getString(nameColumn) // file name
                    val path = localCursor.getString(pathColumn)
                    val storageVol = cursor.getString(storageVolumeColumn)

                    if (SCANNER_DEBUG)
                        Timber.tag(TAG).d("Quick scanner: PATH: $path")

                    // Build directory tree with existing files
                    val possibleMatch = existingSongs.firstOrNull() { it.song.localPath == getRealPath(storageVol, path, name) }

                    if (possibleMatch != null) {
                        newDirectoryStructure.insert(getRealPath(storageVol, path, name)
                            .substringAfter(storageRoot), possibleMatch
                        )
                    }

                }
            }

            Timber.tag(TAG).d("------------ SCAN: Finished Quick Directory Rebuild ------------")
            cacheDirectoryTree(newDirectoryStructure.androidStorageWorkaround().trimRoot())
            return MutableStateFlow(newDirectoryStructure)
        }

        /**
         * Check if artists are the same
         *
         *  Both null == same artists
         *  Either null == different artists
         */
        fun compareArtist(a: List<ArtistEntity>, b: List<ArtistEntity>): Boolean {
            if (a.isEmpty() && b.isEmpty()) {
                return true
            } else if (a.isEmpty() || b.isEmpty()) {
                return false
            }

            // compare entries
            if (a.size != b.size) {
                return false
            }
            val matchingArtists = a.filter { artist ->
                b.any { it.name.lowercase(Locale.getDefault()) == artist.name.lowercase(Locale.getDefault()) }
            }

            return matchingArtists.size == a.size
        }

        /**
         * Check the similarity of a song
         *
         * @param a
         * @param b
         * @param matchStrength How lax should the scanner be
         * @param strictFileNames Whether to consider file names
         */
        fun compareSong(a: Song, b: Song, matchStrength: ScannerMatchCriteria, strictFileNames: Boolean): Boolean {
            // if match file names
            if (strictFileNames &&
                (a.song.localPath?.substringAfterLast('/') !=
                        b.song.localPath?.substringAfterLast('/'))
            ) {
                return false
            }

            /**
             * Compare file paths
             *
             * I draw the "user error" line here
             */
            fun closeEnough(): Boolean {
                return a.song.localPath == b.song.localPath
            }

            // compare songs based on scanner strength
            return when (matchStrength) {
                ScannerMatchCriteria.LEVEL_1 -> a.song.title == b.song.title
                ScannerMatchCriteria.LEVEL_2 -> closeEnough() || (a.song.title == b.song.title &&
                        compareArtist(a.artists, b.artists))

                ScannerMatchCriteria.LEVEL_3 -> closeEnough() || (a.song.title == b.song.title &&
                        compareArtist(a.artists, b.artists) /* && album compare goes here */)
            }
        }

        /**
         * Search for an artist on YouTube Music.
         *
         * If no artist is found, create one locally
         */
        fun youtubeArtistLookup(query: String): ArtistEntity? {
            var ytmResult: ArtistEntity? = null

            // hit up YouTube for artist
            runBlocking(Dispatchers.IO) {
                YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).onSuccess { result ->

                    val foundArtist = result.items.firstOrNull {
                        it.title.lowercase(Locale.getDefault()) == query.lowercase(Locale.getDefault())
                    } ?: throw Exception("Failed to search: Artist not found on YouTube Music")
                    ytmResult = ArtistEntity(
                        foundArtist.id,
                        foundArtist.title,
                        foundArtist.thumbnail
                    )

                    if (SCANNER_DEBUG)
                        Timber.tag(TAG)
                            .d("Found remote artist:  ${result.items.first().title}")
                }.onFailure {
                    throw Exception("Failed to search on YouTube Music")
                }

            }

            return ytmResult
        }

        /**
         * Swap all participation(s) with old artist to use new artist
         *
         * p.s. This is here instead of DatabaseDao because it won't compile there because
         * "oooga boooga error in generated code"
         */
        suspend fun swapArtists(old: ArtistEntity, new: ArtistEntity, database: MusicDatabase) {
            if (database.artist(old.id).first() == null) {
                throw Exception("Attempting to swap with non-existent old artist in database with id: ${old.id}")
            }
            if (database.artist(new.id).first() == null) {
                throw Exception("Attempting to swap with non-existent new artist in database with id: ${new.id}")
            }

            // update participation(s)
            database.updateSongArtistMap(old.id, new.id)
            database.updateAlbumArtistMap(old.id, new.id)

            // nuke old artist
            database.delete(old)
        }
    }
}

class ScannerAbortException(message: String) : Throwable(message)