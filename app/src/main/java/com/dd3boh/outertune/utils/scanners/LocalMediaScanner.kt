package com.dd3boh.outertune.utils.scanners

import android.media.MediaPlayer
import android.os.Environment
import com.dd3boh.outertune.constants.ScannerMatchCriteria
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongArtistMap
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.db.entities.SongGenreMap
import com.dd3boh.outertune.models.DirectoryTree
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.ui.utils.SCANNER_DEBUG
import com.dd3boh.outertune.ui.utils.STORAGE_ROOT
import com.dd3boh.outertune.ui.utils.SYNC_SCANNER
import com.dd3boh.outertune.ui.utils.cacheDirectoryTree
import com.dd3boh.outertune.ui.utils.scannerSession
import com.dd3boh.outertune.utils.closestMatch
import com.zionhuang.innertube.YouTube
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
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime
import java.util.Locale


class LocalMediaScanner {

    /**
     * Compiles a song with all it's necessary metadata. Unlike MediaStore,
     * this also supports multiple artists, multiple genres (TBD), and a few extra details (TBD).
     */
    private fun advancedScan(
        path: String,
        database: MusicDatabase,
    ): Song {
        try {
            // test if system can play
            val testPlayer = MediaPlayer()
            testPlayer.setDataSource(path)
            testPlayer.prepare()
            testPlayer.release()

            // decide which scanner to use
            val scanner = getAdvancedScanner()
                ?: throw NullPointerException("Advanced Extractor is null") // debug
            val ffmpegData = scanner.getAllMetadata(path)

            // file format info
            database.query {
                upsert(
                    ffmpegData.format
                )
            }
            return ffmpegData.song
        } catch (e: Exception) {
            when (e) {
                is IOException, is IllegalArgumentException, is IllegalStateException ->
                    throw InvalidAudioFileException("Not in a playable format: ${e.message} for: $path")
                else -> {
                    if (SCANNER_DEBUG) {
                        Timber.tag(TAG).d(
                            "ERROR READING METADATA: ${e.message} for: $path"
                        )
                        e.printStackTrace()
                    }

                    // we still want the song to be playable even if metadata extractor fails
                    return Song(
                        SongEntity(
                            SongEntity.generateSongId(),
                            path.substringAfterLast('/'),
                            thumbnailUrl = path,
                            isLocal = true,
                            inLibrary = LocalDateTime.now(),
                            localPath = path
                        ),
                        artists = ArrayList())
                }
            }
        }

    }


    /**
     * Scan MediaStore for songs given a list of paths to scan for.
     * This will replace all data in the database for a given song.
     *
     * @param scanPaths List of whitelist paths to scan under. This assumes
     * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
     * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun scanLocal(
        database: MusicDatabase,
        scanPaths: List<String>,
        excludedScanPaths: List<String>,
        pathsOnly: Boolean = false,
    ): MutableStateFlow<DirectoryTree> {
        val newDirectoryStructure = DirectoryTree(STORAGE_ROOT)
        Timber.tag(TAG).d("------------ SCAN: Starting Full Scanner ------------")

        val scannerJobs = ArrayList<Deferred<Song?>>()
        runBlocking {
            getScanPaths(scanPaths, excludedScanPaths).forEach { path ->
                // we can expect lrc is not a song
                if (path.substringAfterLast('.') == "lrc") {
                    return@forEach
                }
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("PATH: $path")

                /**
                 * TODO: do not link album (and whatever song id) with youtube yet, figure that out later
                 */

                // just get the paths
                if (pathsOnly) {
                    newDirectoryStructure.insert(
                        path.substringAfter(STORAGE_ROOT),
                        Song(SongEntity("", "", localPath = path), artists = ArrayList())
                    )
                    return@forEach
                }

                // extract metadata now
                if (!SYNC_SCANNER) {
                    // use async scanner
                    scannerJobs.add(
                        async(scannerSession) {
                            if (scannerRequestCancel) {
                                if (SCANNER_DEBUG)
                                    Timber.tag(TAG).d("WARNING: Canceling advanced scanner job.")
                                throw ScannerAbortException("")
                            }
                            try {
                                advancedScan(path, database)
                            } catch (e: InvalidAudioFileException) {
                                null
                            }
                        }
                    )
                } else {
                    if (scannerRequestCancel) {
                        if (SCANNER_DEBUG)
                            Timber.tag(TAG).d("WARNING: Requested to cancel Full Scanner. Aborting.")
                        scannerRequestCancel = false
                        throw ScannerAbortException("Scanner canceled during Full Scanner (synchronous)")
                    }

                    // force synchronous scanning of songs. Do not catch errors
                    val toInsert = advancedScan(path, database)
                    toInsert.song.localPath?.let { s ->
                        newDirectoryStructure.insert(
                            s.substringAfter(STORAGE_ROOT), toInsert
                        )
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

            song?.song?.localPath?.let { s ->
                newDirectoryStructure.insert(
                    s.substringAfter(STORAGE_ROOT), song
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
        refreshExisting: Boolean = false,
        noDisable: Boolean = false
    ) {
        Timber.tag(TAG).d("------------ SYNC: Starting Local Library Sync ------------")
        // deduplicate
        val finalSongs = ArrayList<Song>()
        newSongs.forEach { song ->
            if (finalSongs.none { s -> compareSong(song, s, matchStrength, strictFileNames) }) {
                finalSongs.add(song)
            }
        }
        Timber.tag(TAG).d("Entries to process: ${newSongs.size}. After dedup: ${finalSongs.size}")

        // sync
        finalSongs.forEach { song ->
            if (scannerRequestCancel) {
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("WARNING: Requested to cancel Local Library Sync. Aborting.")
                scannerRequestCancel = false
                throw ScannerAbortException("Scanner canceled during Local Library Sync")
            }

            val querySong = database.searchSongsAllLocal(song.song.title)


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


                if (songMatch.isNotEmpty()) { // known song, update the song info in the database
                    if (SCANNER_DEBUG)
                        Timber.tag(TAG)
                            .d("Found in database, updating song: ${song.song.title} rescan = $refreshExisting")

                    val oldSong = songMatch.first().song
                    val songToUpdate = song.song.copy(id = oldSong.id, localPath = song.song.localPath)

                    // don't run if we will update these values in rescan anyways
                    // always ensure inLibrary and local path values are valid
                    if (!refreshExisting && (oldSong.inLibrary == null || oldSong.localPath == null)) {
                        database.update(songToUpdate)
                    }

                    if (!refreshExisting) { // below is only for when rescan is enabled
                        // always update the path
                        database.updateLocalSongPath(songToUpdate.id, songToUpdate.inLibrary, songToUpdate.localPath)
                        return@runBlocking
                    }

                    database.transaction {
                        update(songToUpdate)

                        // destroy existing artist links
                        unlinkSongArtists(songToUpdate.id)
                    }

                    // update artists
                    var artistPos = 0
                    song.artists.forEach {
                        val dbQuery = database.searchArtists(it.name).firstOrNull()?.sortedBy { item -> item.artist.name.length }
                        val dbArtist = dbQuery?.let { item -> closestMatch(it.name, item) }

                        database.transaction {
                            if (dbArtist == null) {
                                // artist does not exist in db, add it then link it
                                insert(it)
                                insert(SongArtistMap(songToUpdate.id, it.id, artistPos))
                            } else {
                                // artist does  exist in db, link to it
                                insert(SongArtistMap(songToUpdate.id, dbArtist.artist.id, artistPos))
                            }
                        }

                        artistPos++
                    }

                    artistPos = 0 // reuse this var for genres
                    song.genre?.forEach {
                        val dbGenre = database.genreByAproxName(it.title).firstOrNull()?.firstOrNull()

                        database.transaction {
                            if (dbGenre == null) {
                                // genre does not exist in db, add it then link it
                                insert(it)
                                insert(SongGenreMap(songToUpdate.id, it.id, artistPos))
                            } else {
                                // genre does exist in db, link to it
                                insert(SongGenreMap(songToUpdate.id, dbGenre.id, artistPos))
                            }
                        }

                        artistPos++
                    }

                } else { // new song
                    if (SCANNER_DEBUG)
                        Timber.tag(TAG)
                            .d("NOT found in database, adding song: ${song.song.title}")

                    database.insert(song.toMediaMetadata())
                }
            }
        }

        // do not delete songs from database automatically, we just disable them
        if (!noDisable) {
            finalize(finalSongs, database)
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
    ) {
        Timber.tag(TAG).d("------------ SYNC: Starting Quick (additive delta) Library Sync ------------")
        Timber.tag(TAG).d("Entries to process: ${newSongs.size}")

        runBlocking(Dispatchers.IO) {
            // get list of all songs in db, then get songs unknown to the database
            val allSongs = database.allLocalSongs().first()
            val delta = newSongs.filterNot {
                allSongs.any { dbSong -> compareSong(it, dbSong, matchCriteria, true) } // ignore user strictFileNames prefs for initial matching
            }

            val finalSongs = ArrayList<Song>()
            val scannerJobs = ArrayList<Deferred<Song?>>()
            runBlocking {
                // Get song basic metadata
                delta.forEach { s ->
                    if (scannerRequestCancel) {
                        if (SCANNER_DEBUG)
                            Timber.tag(TAG).d("WARNING: Requested to cancel. Aborting.")
                        scannerRequestCancel = false
                        throw ScannerAbortException("Scanner canceled during Quick (additive delta) Library Sync")
                    }

                    // we can expect lrc is not a song
                    if (s.song.localPath?.substringAfterLast('.') == "lrc") {
                        return@forEach
                    }

                    val path = "" + s.song.localPath

                    if (SCANNER_DEBUG)
                        Timber.tag(TAG).d("PATH: $path")

                    /**
                     * TODO: do not link album (and whatever song id) with youtube yet, figure that out later
                     */

                    if (!SYNC_SCANNER) {
                        // use async scanner
                        scannerJobs.add(
                            async(scannerSession) {
                                if (scannerRequestCancel) {
                                    if (SCANNER_DEBUG)
                                        Timber.tag(TAG).d("WARNING: Canceling advanced scanner job.")
                                    throw ScannerAbortException("")
                                }
                                try {
                                    advancedScan(path, database)
                                } catch (e: InvalidAudioFileException) {
                                    null
                                }
                            }
                        )
                    } else {
                        // force synchronous scanning of songs. Do not catch errors
                        finalSongs.add(advancedScan(path, database))
                    }
                }
            }

            if (!SYNC_SCANNER) {
                // use async scanner
                scannerJobs.awaitAll()
            }

            // add to finished list
            scannerJobs.forEach {
                val song = it.getCompleted()
                song?.song?.let { finalSongs.add(song) }
            }

            if (finalSongs.isNotEmpty()) {
                syncDB(database, finalSongs, matchCriteria, strictFileNames, noDisable = true)
            } else {
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("Not syncing, no valid songs found!")
            }

            // we handle disabling songs here instead
            finalize(newSongs, database)
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
                        database.fuzzySearchArtists(artistVal).first().filter { artist ->
                            // only look for remote artists here
                            return@filter artist.name == artistVal && !artist.isLocalArtist
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
                            swapArtists(element, databaseArtistMatch.first(), database)
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
     * Remove inaccessible, and duplicate songs from the library
     */
    private fun finalize(newSongs: List<Song>, database: MusicDatabase) {
        if (SCANNER_DEBUG)
            Timber.tag(TAG).d("Start finalize (disable songs) job. Number of valid songs: ${newSongs.size}")
        runBlocking(Dispatchers.IO) {
            // get list of all local songs in db
            database.disableInvalidLocalSongs() // make sure path is existing
            val allSongs = database.allLocalSongs().first()

            // disable if not in directory anymore
            for (song in allSongs) {
                if (song.song.localPath == null) {
                    continue
                }

                // new songs is all songs that are known to be valid
                // delete all songs in the DB that do not match a path
                if (newSongs.none { it.song.localPath == song.song.localPath }) {
                    if (SCANNER_DEBUG)
                        Timber.tag(TAG).d("Disabling song ${song.song.localPath}")
                    database.transaction {
                        disableLocalSong(song.song.id)
                    }
                }
            }

            // remove duplicates
            val dupes = database.duplicatedLocalSongs().first().toMutableList()
            var index = 0

            if (SCANNER_DEBUG)
                Timber.tag(TAG).d("Start finalize (duplicate removal) job. Number of candidates: ${dupes.size}")

            while (index < dupes.size) {
                // collect all the duplicates
                val contenders = ArrayList<Pair<SongEntity, Int>>()
                val localPath = dupes[index].localPath
                while (index < dupes.size && dupes[index].localPath == localPath) {
                    contenders.add(Pair(dupes[index], database.getLifetimePlayCount(dupes[index].id).first()))
                    index++
                }
                // yeet the lower play count songs
                contenders.remove(contenders.maxByOrNull { it.second })
                contenders.forEach {
                    if (SCANNER_DEBUG)
                        Timber.tag(TAG).d("Deleting song ${it.first.id} (${it.first.title})")
                    database.delete(it.first)
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
        fun getAdvancedScanner(): MetadataScanner? {
            // kotlin won't let me return MetadataScanner even if it cant possibly be null broooo
            return if (advancedScannerImpl is FFMpegScanner) advancedScannerImpl else FFMpegScanner()
        }

        fun unloadAdvancedScanner() {
            advancedScannerImpl = null
        }


        /**
         * ==========================
         * Scanner helpers
         * ==========================
         */


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
         * Build a list of paths to scan in, taking in exclusions into account. Exclusions
         * will override inclusions. All subdirectories will also be affected.
         */
        fun getScanPaths(scanPaths: List<String>, excludedScanPaths: List<String>): ArrayList<String> {
            val allSongs = ArrayList<String>()

            val resultingPaths =
                scanPaths.filterNot { incl ->
                    excludedScanPaths.any { excl ->
                        if (excl.isBlank()) false else incl.startsWith(excl)
                    }
                }

            val exclusions = excludedScanPaths.map { getRealPathFromUri(it) }

            resultingPaths.forEach { path ->
                try {
                    val songsHere =
                        File(getRealPathFromUri(path)).walk().filter { it.isFile }.toList().map { it.absolutePath }
                    allSongs.addAll(songsHere.filterNot { include -> exclusions.any { include.startsWith(it) } })
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                    throw Exception("oh well idk man this should never happen")
                }
            }

            return allSongs
        }

        /**
         * Quickly rebuild a skeleton directory tree of local files based on the database
         *
         * Notes:
         * If files move around, that's on you to re run the scanner.
         * If the metadata changes, that's also on you to re run the scanner.
         *
         * @param scanPaths List of whitelist paths to scan under. This assumes
         * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
         * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
         */
        fun refreshLocal(
            database: MusicDatabase,
            scanPaths: List<String>,
            excludedScanPaths: List<String>,
        ): MutableStateFlow<DirectoryTree> {
            val newDirectoryStructure = DirectoryTree(STORAGE_ROOT)

            // get songs from db
            var existingSongs: List<Song>
            runBlocking(Dispatchers.IO) {
                existingSongs = database.allLocalSongs().first()
            }

            Timber.tag(TAG).d("------------ SCAN: Starting Quick Directory Rebuild ------------")
            getScanPaths(scanPaths, excludedScanPaths).forEach { path ->
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("Quick scanner: PATH: $path")

                // Build directory tree with existing files
                val possibleMatch =
                    existingSongs.firstOrNull { it.song.localPath == path }

                if (possibleMatch != null) {
                    newDirectoryStructure.insert(
                        path, possibleMatch
                    )
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
        fun compareSong(
            a: Song,
            b: Song,
            matchStrength: ScannerMatchCriteria = ScannerMatchCriteria.LEVEL_2,
            strictFileNames: Boolean = false
        ): Boolean {
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

            database.transaction {
                // update participation(s)
                database.updateSongArtistMap(old.id, new.id)
                database.updateAlbumArtistMap(old.id, new.id)

                // nuke old artist
                database.delete(old)
            }
        }
    }
}

class InvalidAudioFileException(message: String) : Throwable(message)
class ScannerAbortException(message: String) : Throwable(message)