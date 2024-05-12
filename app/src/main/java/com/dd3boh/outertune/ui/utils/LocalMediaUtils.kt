package com.dd3boh.outertune.ui.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.compose.ui.text.toLowerCase
import com.dd3boh.outertune.constants.ScannerMatchCriteria
import com.dd3boh.outertune.constants.ScannerImpl
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.AlbumEntity
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongArtistMap
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.utils.MetadataScanner
import com.dd3boh.outertune.utils.cache
import com.dd3boh.outertune.utils.retrieveImage
import com.dd3boh.outertune.utils.scanners.FFProbeScanner
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.YouTube.search
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
import java.time.LocalDateTime
import java.util.Locale

const val TAG = "LocalMediaUtils"

/**
 * TODO: Implement a proper, much faster scanner
 * Currently ffmpeg-kit will fail if you hit it with too many calls too quickly.
 * You can try more than 8 jobs, but good luck.
 * For easier debugging, uncomment SCANNER_CRASH_AT_FIRST_ERROR to stop at first error
 */
const val SCANNER_CRASH_AT_FIRST_ERROR = false // crash at ffprobe errors only
const val SYNC_SCANNER = false // true will not use multithreading for scanner
const val MAX_CONCURRENT_JOBS = 16
const val SCANNER_DEBUG = false

@OptIn(ExperimentalCoroutinesApi::class)
val scannerSession = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_JOBS)

// stuff to make this work
const val sdcardRoot = "/storage/emulated/0/"
val testScanPaths = arrayListOf("Music")
var directoryUID = 0
var cachedDirectoryTree: DirectoryTree? = null


// useful metadata
val projection = arrayOf(
    MediaStore.Audio.Media._ID,
    MediaStore.Audio.Media.DISPLAY_NAME,
    MediaStore.Audio.Media.TITLE,
    MediaStore.Audio.Media.DURATION,
    MediaStore.Audio.Media.ARTIST,
    MediaStore.Audio.Media.ARTIST_ID,
    MediaStore.Audio.Media.ALBUM,
    MediaStore.Audio.Media.ALBUM_ID,
    MediaStore.Audio.Media.RELATIVE_PATH,
)


/**
 * A tree representation of local audio files
 *
 * @param path root directory start
 */
class DirectoryTree(path: String) {
    companion object {
        const val TAG = "DirectoryTree"
    }

    /**
     * Directory name
     */
    var currentDir = path // file name

    /**
     * Full parent directory path
     */
    var parent: String = ""

    // folder contents
    var subdirs = ArrayList<DirectoryTree>()
    var files = ArrayList<Song>()

    val uid = directoryUID

    init {
        // increment uid
        directoryUID++
    }

    /**
     * Instantiate a directory tree directly
     */
    constructor(path: String, files: ArrayList<Song>) : this(path) {
        this.files = files
    }

    fun insert(path: String, song: Song) {
//        println("curr path =" + path)

        // add a file
        if (path.indexOf('/') == -1) {
            files.add(song)
            Timber.tag(TAG).d("Adding song with path: $path")
            return
        }

        // there is still subdirs to process
        var tmpPath = path
        if (path[path.length - 1] == '/') {
            tmpPath = path.substring(0, path.length - 1)
        }

        // the first directory before the .
        val subdirPath = tmpPath.substringBefore('/')

        // create subdirs if they do not exist, then insert
        var existingSubdir: DirectoryTree? = null
        subdirs.forEach { subdir ->
            if (subdir.currentDir == subdirPath) {
                existingSubdir = subdir
                return@forEach
            }
        }

        if (existingSubdir == null) {
            val tree = DirectoryTree(subdirPath)
            tree.parent = "$parent/$currentDir"
            tree.insert(tmpPath.substringAfter('/'), song)
            subdirs.add(tree)

        } else {
            existingSubdir!!.insert(tmpPath.substringAfter('/'), song)
        }
    }


    /**
     * Get the name of the file from full path, without any extensions
     */
    private fun getFileName(path: String?): String? {
        if (path == null) {
            return null
        }
        return path.substringAfterLast('/').substringBefore('.')
    }

    /**
     * Retrieves song object at path
     *
     * @return song at path, or null if it does not exist
     */
    fun getSong(path: String): Song? {
        Timber.tag(TAG).d("Searching for song, at path: $path")

        // search for song in current dir
        if (path.indexOf('/') == -1) {
            val foundSong: Song = files.first { getFileName(it.song.localPath) == getFileName(path) }
            Timber.tag(TAG).d("Searching for song, found?: ${foundSong.id} Name: ${foundSong.song.title}")
            return foundSong
        }

        // there is still subdirs to process
        var tmpPath = path
        if (path[path.length - 1] == '/') {
            tmpPath = path.substring(0, path.length - 1)
        }

        // the first directory before the .
        val subdirPath = tmpPath.substringBefore('/')

        // scan for matching subdirectory
        var existingSubdir: DirectoryTree? = null
        subdirs.forEach { subdir ->
            if (subdir.currentDir == subdirPath) {
                existingSubdir = subdir
                return@forEach
            }
        }

        // explore the subdirectory if it exists in
        if (existingSubdir == null) {
            return null
        } else {
            return existingSubdir!!.getSong(tmpPath.substringAfter('/'))
        }
    }


    /**
     * Retrieve a list of all the songs
     */
    fun toList(): List<Song> {
        val songs = ArrayList<Song>()

        fun traverseTree(tree: DirectoryTree, result: ArrayList<Song>) {
            result.addAll(tree.files)
            tree.subdirs.forEach { traverseTree(it, result) }
        }

        traverseTree(this, songs)
        return songs
    }

    /**
     * Retrieves a modified version of this DirectoryTree.
     * All folders are recognized to be top level folders
     */
    fun toFlattenedTree(): DirectoryTree {
        val result = DirectoryTree(sdcardRoot)
        getSubdirsRecursive(this, result.subdirs)
        return result
    }

    /**
     * Crawl the directory tree, add the subdirectories with songs to the list
     * @param it
     * @param result
     */
    private fun getSubdirsRecursive(it: DirectoryTree, result: ArrayList<DirectoryTree>) {
        if (it.files.size > 0) {
            result.add(DirectoryTree(it.currentDir, it.files))
        }

        if (it.subdirs.size > 0) {
            it.subdirs.forEach { getSubdirsRecursive(it, result) }
        }
    }
}


/**
 * ==========================
 * Actual local scanner utils
 * ==========================
 */


var advancedScannerImpl: MetadataScanner? = null

/**
 * TODO: Docs here
 */
fun getScanner(scannerImpl: ScannerImpl): MetadataScanner? {
    // kotlin won't let me return MetadataScanner even if it cant possibly be null broooo
    return when (scannerImpl) {
        ScannerImpl.FFPROBE -> if (advancedScannerImpl is FFProbeScanner) advancedScannerImpl else FFProbeScanner()
        ScannerImpl.MEDIASTORE -> throw Exception("Forcing MediaStore fallback")
    }
}

fun unloadScanner() {
    advancedScannerImpl = null
}

/**
 * Dev uses
 */
fun refreshLocal(context: Context, database: MusicDatabase) =
    refreshLocal(context, database, testScanPaths)


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
    scanPaths: ArrayList<String>
): MutableStateFlow<DirectoryTree> {
    val newDirectoryStructure = DirectoryTree(sdcardRoot)

    // get songs from db
    var existingSongs: List<Song>
    runBlocking(Dispatchers.IO) {
        existingSongs = database.allLocalSongs().first()
    }

    // Query for audio files
    val contentResolver: ContentResolver = context.contentResolver
    val cursor = contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?",
        scanPaths.map { "$sdcardRoot$it%" }.toTypedArray(), // whitelist paths
        null
    )
    Timber.tag(TAG).d("------------ SCAN: Starting Quick Directory Rebuild ------------")
    cursor?.use { cursor ->
        // Columns indices
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

        while (cursor.moveToNext()) {
            val name = cursor.getString(nameColumn) // file name
            val path = cursor.getString(pathColumn)

            if (SCANNER_DEBUG)
                Timber.tag(TAG).d("Quick scanner: PATH: $path")

            // Build directory tree with existing files
            val possibleMatch = existingSongs.firstOrNull() { it.song.localPath == "$sdcardRoot$path$name" }

            if (possibleMatch != null) {
                newDirectoryStructure.insert("$path$name", possibleMatch)
            }

        }
    }

    cachedDirectoryTree = newDirectoryStructure
    return MutableStateFlow(newDirectoryStructure)
}

/**
 * For passing along song metadata
 */
data class SongTempData(
    val id: String, val path: String, val title: String, val duration: Int,
    val artist: String?, val artistID: String?, val album: String?, val albumID: String?,
)

/**
 * Compiles a song with all it's necessary metadata. Unlike MediaStore,
 * this also supports multiple artists, multiple genres (TBD), and a few extra details (TBD).
 */
fun advancedScan(
    basicData: SongTempData,
    database: MusicDatabase,
    scannerImpl: ScannerImpl,
    onlineLookup: Boolean = false
): Song {
    val artists = ArrayList<ArtistEntity>()
//    var generes
//    var year: String? = null

    try {
        // decide which scanner to use
        val scanner = getScanner(scannerImpl)
        val artistString = scanner?.getMediaStoreSupplement(basicData.path)
        // parse data
        artistString?.artists?.split(';')?.forEach { element ->
            val artistVal = element.trim()

            // check if this artist exists in DB already
            val databaseArtistMatch =
                runBlocking(Dispatchers.IO) {
                    database.searchArtists(artistVal).first().filter { artist ->
                        return@filter artist.artist.name == artistVal
                    }
                }

            if (SCANNER_DEBUG)
                Timber.tag(TAG).d("ARTIST FOUND IN DB??? Results size: ${databaseArtistMatch.size}")

            // resolve artist from YTM if not found in DB
            try {
                if (onlineLookup && databaseArtistMatch.isEmpty()) {
                    youtubeArtistLookup(artistVal)?.let { artists.add(it) }
                } else if (databaseArtistMatch.isEmpty()) {
                    artists.add(
                        databaseArtistMatch.first().artist
                    )
                } else {
                    throw Exception("No artist resolved")
                }
            } catch (e: Exception) {
                artists.add(ArtistEntity("LA${ArtistEntity.generateArtistId()}", artistVal, isLocal = true))
            }
        }
    } catch (e: Exception) {
        if (SCANNER_CRASH_AT_FIRST_ERROR && scannerImpl != ScannerImpl.MEDIASTORE) {
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
            basicData.id,
            basicData.title,
            (basicData.duration / 1000), // we use seconds for duration
            albumId = basicData.albumID,
            albumName = basicData.album,
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
        }
    )
}

/**
 * Dev uses
 */
fun scanLocal(context: Context, database: MusicDatabase, scannerImpl: ScannerImpl, onlineLookup: Boolean) =
    scanLocal(context, database, testScanPaths, scannerImpl, onlineLookup)

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
    scanPaths: ArrayList<String>,
    scannerImpl: ScannerImpl,
    onlineLookup: Boolean
): MutableStateFlow<DirectoryTree> {

    val newDirectoryStructure = DirectoryTree(sdcardRoot)
    val contentResolver: ContentResolver = context.contentResolver

    // Query for audio files
    val cursor = contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?",
        scanPaths.map { "$sdcardRoot$it%" }.toTypedArray(), // whitelist paths
        null
    )
    Timber.tag(TAG).d("------------ SCAN: Starting Full Scanner ------------")

    val scannerJobs = ArrayList<Deferred<Song>>()
    runBlocking {
        // MediaStore is our "basic" scanner & file discovery
        cursor?.use { cursor ->
            // Columns indices
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIDColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = SongEntity.generateSongId()
                val name = cursor.getString(nameColumn) // file name
                val title = cursor.getString(titleColumn) // song title
                val duration = cursor.getInt(durationColumn)
                val artist = cursor.getString(artistColumn)
                val artistID = cursor.getString(artistIdColumn)
                val albumID = cursor.getString(albumIDColumn)
                val album = cursor.getString(albumColumn)
                val path = cursor.getString(pathColumn)

                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("ID: $id, Name: $name, ARTIST: $artist, PATH: $path")

                // append song to list
                // media store doesn't support multi artists...
                // do not link album (and whatever song id) with youtube yet, figure that out later

                if (!SYNC_SCANNER) {
                    // use async scanner
                    scannerJobs.add(
                        async(scannerSession) {
                            advancedScan(
                                SongTempData(
                                    id.toString(), "$sdcardRoot$path$name", title, duration,
                                    artist, artistID, album, albumID,
                                ), database, scannerImpl, onlineLookup
                            )
                        }
                    )
                } else {
                    // force synchronous scanning of songs
                    val toInsert = advancedScan(
                        SongTempData(
                            id.toString(), "$sdcardRoot$path$name", title, duration,
                            artist, artistID, album, albumID,
                        ), database, scannerImpl
                    )
                    toInsert.song.localPath?.let { s ->
                        newDirectoryStructure.insert(
                            s.substringAfter(sdcardRoot), toInsert
                        )
                    }
                }
            }
        }

        if (!SYNC_SCANNER) {
            // use async scanner
            scannerJobs.awaitAll()
        }
    }

    // build the tree
    scannerJobs.forEach {
        val song = it.getCompleted()

        song.song.localPath?.let { s ->
            newDirectoryStructure.insert(
                s.substringAfter(sdcardRoot), song
            )
        }
    }

    cachedDirectoryTree = newDirectoryStructure
    return MutableStateFlow(newDirectoryStructure)
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
        search(query, YouTube.SearchFilter.FILTER_ARTIST).onSuccess { result ->

            val foundArtist = result.items.firstOrNull {
                it.title.lowercase(Locale.getDefault()) == query.lowercase(Locale.getDefault())
            } ?: throw Exception("Failed to search: Artist not found on YouTube Music")
            ytmResult = ArtistEntity(
                foundArtist.id,
                foundArtist.title,
                foundArtist.thumbnail
            )

            if (SCANNER_DEBUG)
                Timber.tag(TAG).d("Found remote artist:  ${result.items.first().title}")
        }.onFailure {
            throw Exception("Failed to search on YouTube Music")
        }

    }

    return ytmResult
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
                    Timber.tag(TAG).d("FIRST Found songs ${songMatch.first().song.title}")
                }
            }


            if (songMatch.isNotEmpty() && refreshExisting == true) { // known song, update the song info in the database
                Timber.tag(TAG).d("Found in database, updating song: ${song.song.title}")
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
                Timber.tag(TAG).d("NOT found in database, adding song: ${song.song.title}")
                database.insert(song.toMediaMetadata())
            }
            // do not delete songs from database automatically, we just disable them
            disableSongs(database)
        }
    }

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
                mData.setDataSource(s.song.localPath)

                val id = SongEntity.generateSongId()
                val title =
                    mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).let { it ?: "" } // song title
                val duration = mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).let { parseInt(it) }
                val artist = mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val artistID = if (artist == null) ArtistEntity.generateArtistId() else null
                val album = mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val albumID = if (album == null) AlbumEntity.generateAlbumId() else null
                // path should never be null since its coming from directory tree scanner
                // but Kotlin is too dumb to care. Just ruthlessly suppress the error...
                val path = "" + s.song.localPath


                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("ID: $id, Title: $title, ARTIST: $artist, PATH: $path")

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
                                ), database, scannerImpl // no online artist lookup
                            )
                        }
                    )
                } else {
                    // force synchronous scanning of songs
                    val toInsert = advancedScan(
                        SongTempData(
                            id, path, title, duration, artist, artistID, album, albumID,
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
                        // this is different from the regular syncDb operation: only look for remote artists
                        return@filter artist.artist.name == artistVal && !artist.artist.isLocalArtist
                    }
                }

            if (SCANNER_DEBUG)
                Timber.tag(TAG).d("ARTIST FOUND IN DB??? Results size: ${databaseArtistMatch.size}")

            scannerJobs.add(
                async(scannerSession) {
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
    }
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
    Timber.tag(TAG).w("NUKING LOCAL FILE LIBRARY FROM DATABASE! Nuke status: ${database.nukeLocalData()}")
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

/**
 * Check if artists are the same
 *
 *  Both null == same artists
 *  Either null == different artists
 */
fun compareArtist(a: List<ArtistEntity>?, b: List<ArtistEntity>?): Boolean {

    if (a == null && b == null) {
        return true
    } else if (a == null || b == null) {
        return false
    }

    // compare entries
    if (a.size != b.size) {
        return false
    }
    val matchingArtists = a.filter { artist ->
        b.any { it.name == artist.name }
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

    // compare songs based on scanner strength
    return when (matchStrength) {
        ScannerMatchCriteria.LEVEL_1 -> a.song.title == b.song.title
        ScannerMatchCriteria.LEVEL_2 -> a.song.title == b.song.title &&
                compareArtist(a.artists, b.artists)

        ScannerMatchCriteria.LEVEL_3 -> a.song.title == b.song.title &&
                compareArtist(a.artists, b.artists) && true // album compare go here
    }
}

/**
 * ==========================
 * Various misc helpers
 * ==========================
 */


/**
 * Extract the album art from the audio file. The image is not resized
 * (did you mean to use getLocalThumbnail(path: String?, resize: Boolean)?).
 *
 * @param path Full path of audio file
 */
fun getLocalThumbnail(path: String?): Bitmap? = getLocalThumbnail(path, false)

/**
 * Extract the album art from the audio file
 *
 * @param path Full path of audio file
 * @param resize Whether to resize the Bitmap to a thumbnail size (300x300)
 */
fun getLocalThumbnail(path: String?, resize: Boolean): Bitmap? {
    if (path == null) {
        return null
    }
    // try cache lookup
    val cachedImage = if (resize) {
        retrieveImage(path)?.resizedImage
    } else {
        retrieveImage(path)?.image
    }

    if (cachedImage == null) {
        Timber.tag(TAG).d("Cache miss on $path")
    } else {
        return cachedImage
    }

    val mData = MediaMetadataRetriever()
    mData.setDataSource(path)

    var image: Bitmap = try {
        val art = mData.embeddedPicture
        BitmapFactory.decodeByteArray(art, 0, art!!.size)
    } catch (e: Exception) {
        null
    } ?: return null

    if (resize) {
        image = Bitmap.createScaledBitmap(image, 300, 300, false)
    }

    cache(path, image, resize)
    return image
}


/**
 * Get cached directory tree
 */
fun getDirectoryTree(): DirectoryTree? {
    if (cachedDirectoryTree == null) {
        return null
    }
    return cachedDirectoryTree
}