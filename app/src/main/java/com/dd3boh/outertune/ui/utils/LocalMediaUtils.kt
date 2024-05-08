package com.dd3boh.outertune.ui.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import com.dd3boh.outertune.constants.ScannerSensitivity
import com.dd3boh.outertune.constants.ScannerType
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.AlbumEntity
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.utils.scanners.FFProbeScanner
import com.dd3boh.outertune.utils.cache
import com.dd3boh.outertune.utils.retrieveImage
import com.dd3boh.outertune.utils.scanners.FFProbeKitScanner
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.YouTube.search
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.time.LocalDateTime

const val TAG = "LocalMediaUtils"

/**
 * TODO: Implement a proper, much faster scanner
 * Currently ffmpeg-kit will fail if you hit it with too many calls too quickly.
 * You can try more than 8 jobs, but good luck.
 * For easier debugging, uncomment SCANNER_CRASH_AT_FIRST_ERROR to stop at first error
 */
const val SCANNER_CRASH_AT_FIRST_ERROR = false // crash at ffprobe errors only
const val SYNC_SCANNER = false // true will not use multithreading for scanner
const val MAX_CONCURRENT_JOBS = 8
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
        existingSongs = database.allLocalSongsData().first()
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
    Timber.tag(TAG).d("------------ Starting Quick Directory Rebuild ------------")
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
 * Dev uses
 */
fun scanLocal(context: Context, database: MusicDatabase, scannerType: ScannerType) =
    scanLocal(context, database, testScanPaths, scannerType)

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
    scannerType: ScannerType
): MutableStateFlow<DirectoryTree> {

    if (scannerType != ScannerType.MEDIASTORE) {
        // load advanced scanner libs
        System.loadLibrary("avcodec")
        System.loadLibrary("avdevice")
        System.loadLibrary("ffprobejni")
        System.loadLibrary("avfilter")
        System.loadLibrary("avformat")
        System.loadLibrary("avutil")
        System.loadLibrary("swresample")
        System.loadLibrary("swscale")
    }

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
    Timber.tag(TAG).d("------------ Starting Full Scanner ------------")

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

                /**
                 * Compiles a song with all it's necessary metadata. Unlike MediaStore,
                 * this also supports multiple artists, multiple genres (TBD), and a few extra details (TBD).
                 */
                fun advancedScan(): Song {
                    var artists = ArrayList<ArtistEntity>()
//                    var generes
//                    var year: String? = null

                    try {
                        // decide which scanner to use
                        val scanner = when (scannerType) {
                            ScannerType.FFPROBEKIT_ASYNC -> FFProbeKitScanner()
                            ScannerType.FFPROBE -> FFProbeScanner()
                            ScannerType.MEDIASTORE -> throw Exception("Forcing MediaStore fallback")
                        }

                        val artistString = scanner.getMediaStoreSupplement("$sdcardRoot$path$name")
                        // parse data
                        artistString.artists?.split(';')?.forEach { element ->
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
                            if (databaseArtistMatch.isEmpty()) {
                                youtubeArtistLookup(artistVal)?.let { artists.add(it) }
                            } else {
                                artists.add(
                                    databaseArtistMatch.first().artist
                                )
                            }
                        }
                    } catch (e: Exception) {
                        if (SCANNER_CRASH_AT_FIRST_ERROR && scannerType != ScannerType.MEDIASTORE) {
                            throw Exception("HALTING AT FIRST SCANNER ERROR " + e.message) // debug
                        }
                        // fallback on media store
                        Timber.tag(TAG).d(
                            "ERROR READING ARTIST METADATA: ${e.message}" +
                                    " Falling back on MediaStore for $path$name"
                        )
                        e.printStackTrace()

                        if (artist.isNotBlank()) {
                            artists.add(ArtistEntity(artistID, artist, isLocal = true))
                        }
                    }

                    return Song(
                        SongEntity(
                            id.toString(),
                            title,
                            (duration / 1000), // we use seconds for duration
                            albumId = albumID,
                            albumName = album,
                            isLocal = true,
                            inLibrary = LocalDateTime.now(),
                            localPath = "$sdcardRoot$path$name"
                        ),
                        artists,
                        // album not working
                        AlbumEntity(albumID, title = album, duration = 0, songCount = 1)
                    )
                }

                if (!SYNC_SCANNER) {
                    // use async scanner
                    scannerJobs.add(
                        async(scannerSession) { advancedScan() }
                    )
                } else {
                    // force synchronous scanning of songs
                    val toInsert = advancedScan()
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
        try {
            search(query, YouTube.SearchFilter.FILTER_ARTIST).onSuccess { result ->

                val foundArtist = result.items.first()
                ytmResult = ArtistEntity(
                    // I pray the first search result will always be the correct one
                    foundArtist.id,
                    foundArtist.title,
                    foundArtist.thumbnail
                )

                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("Found remote artist:  ${result.items.first().title}")
            }.onFailure {
                throw Exception("Failed to search on YouTube Music")
            }
        } catch (e: Exception) {
            // create temporary artists
            Timber.tag(TAG).w("Failed to retrieve remote artist, generating one")
            ytmResult = ArtistEntity(ArtistEntity.generateArtistId(), query, isLocal = true)
        }
    }

    return ytmResult
}

/**
 * Update the Database with local files
 *
 * @param database
 * @param directoryStructure
 * @param matchStrength How lax should the scanner be
 * @param strictFileNames Whether to consider file names
 * @param refreshExisting Setting this this to true will updated existing songs
 * with new information, else existing song's data will not be touched, regardless
 * whether it was actually changed on disk
 *
 * inserts a song if not found
 * updates a song information depending
 */
fun syncDB(
    database: MusicDatabase,
    directoryStructure: List<Song>,
    matchStrength: ScannerSensitivity,
    strictFileNames: Boolean,
    refreshExisting: Boolean? = false
) {
    Timber.tag(TAG).d("------------ Starting Local Library Sync ------------")
    Timber.tag(TAG).d("Entries to process: ${directoryStructure.size}")

    directoryStructure.forEach { song ->
        val querySong = database.searchSongs(song.song.title)

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


            /**
             * TODO: update specific fields instead of whole object
             */
            if (songMatch.isNotEmpty() && refreshExisting == true) { // known song, update the song info in the database
                Timber.tag(TAG).d("Found in database, updating song: ${song.song.title}")
                database.update(song.song)
            } else if (songMatch.isEmpty() ){ // new song
                Timber.tag(TAG).d("NOT found in database, adding song: ${song.song.title}")
                database.insert(song.toMediaMetadata())
            }
            // do not delete songs from database automatically
        }
    }

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
fun compareSong(a: Song, b: Song, matchStrength: ScannerSensitivity, strictFileNames: Boolean): Boolean {
    // if match file names
    if (strictFileNames &&
        (a.song.localPath?.substringAfterLast('/') !=
                b.song.localPath?.substringAfterLast('/'))
    ) {
        return false
    }

    // compare songs based on scanner strength
    return when (matchStrength) {
        ScannerSensitivity.LEVEL_1 -> a.song.title == b.song.title
        ScannerSensitivity.LEVEL_2 -> a.song.title == b.song.title &&
                compareArtist(a.artists, b.artists)

        ScannerSensitivity.LEVEL_3 -> a.song.title == b.song.title &&
                compareArtist(a.artists, b.artists) && true // album compare go here
    }
}

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