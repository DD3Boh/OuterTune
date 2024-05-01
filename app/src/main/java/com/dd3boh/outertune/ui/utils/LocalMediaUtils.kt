package com.dd3boh.outertune.ui.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.util.Log
import com.dd3boh.outertune.constants.ScannerSensitivity
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.AlbumEntity
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.utils.getExtraMetadata
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.YouTube.search
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

const val TAG = "LocalMediaUtils"

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
    var currentDir = path // file name, id

    // folder contents
    var subdirs = ArrayList<DirectoryTree>()
    var files = ArrayList<Song>()

    val uid = directoryUID

    init {
        // increment uid
        directoryUID++
    }

    fun insert(path: String, song: Song) {
//        println("curr path =" + path)

        // add a file
        if (path.indexOf('/') == -1) {
            files.add(song)
            println("add A MUSIC FILE AAAAAAHHH " + path)
            return
        }

        // there is still subdirs to process
        var tmppath = path
        if (path[path.length - 1] == '/') {
            tmppath = path.substring(0, path.length - 1)
        }

        // the first directory before the .
        val subdirPath = tmppath.substringBefore('/')

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
            tree.insert(tmppath.substringAfter('/'), song)
            subdirs.add(tree)

        } else {
            existingSubdir!!.insert(tmppath.substringAfter('/'), song)
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
        Log.d(TAG, "Searching for song, at path --> $path")

        // search for song in current dir
        if (path.indexOf('/') == -1) {
            val foundSong: Song = files.first { getFileName(it.song.localPath) == getFileName(path) }
            Log.d(TAG, "Searching for song, found? --> " + foundSong?.id + " name: " + foundSong?.song?.title)
            return foundSong
        }

        // there is still subdirs to process
        var tmppath = path
        if (path[path.length - 1] == '/') {
            tmppath = path.substring(0, path.length - 1)
        }

        // the first directory before the .
        val subdirPath = tmppath.substringBefore('/')

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
            return existingSubdir!!.getSong(tmppath.substringAfter('/'))
        }
    }


    /**
     * Retrieve a list of all the songs
     */
    fun toList(): List<Song> {
        val songs = ArrayList<Song>()

        fun traverseHELPME(tree: DirectoryTree, result: ArrayList<Song>) {
            result.addAll(tree.files)
            tree.subdirs.forEach { traverseHELPME(it, result) }
        }

        traverseHELPME(this, songs)

        return songs
    }
}

/**
 * A wrapper containing extra raw metadata that MediaStore fails to read properly
 */
data class ExtraMetadataWrapper(val artists: String, val genres: String, val date: String)


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


// so here is the thing right, if your files move around, that's on you to re run the full scanner.
// so here is the other other thing right, if the metadata changes, that's also on you to re run the full scanner.
/**
 * Quickly rebuild a skeleton directory tree of local files based on the database
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
    Log.d(TAG, "------------------------------------------")
    cursor?.use { cursor ->
        // Columns indices
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

        while (cursor.moveToNext()) {
            val name = cursor.getString(nameColumn) // file name
            val path = cursor.getString(pathColumn)

            Log.d(TAG, "Quick scanner: PATH: $path")

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
fun scanLocal(context: Context, database: MusicDatabase) =
    scanLocal(context, database, testScanPaths)

/**
 * Scan MediaStore for songs given a list of paths to scan for.
 * This will replace all data in the database for a given song.
 *
 * @param context Context
 * @param scanPaths List of whitelist paths to scan under. This assumes
 * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
 * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
 */
fun scanLocal(
    context: Context,
    database: MusicDatabase,
    scanPaths: ArrayList<String>
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
    Log.d("WTF", "------------------------------------------")

    val scannerJobs = ArrayList<Deferred<Song>>()
    runBlocking {
        // MediaStore is our "basic" scanner
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
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) // file name
                val title = cursor.getString(titleColumn) // song title
                val duration = cursor.getInt(durationColumn)
                val artist = cursor.getString(artistColumn)
                val artistID = cursor.getString(artistIdColumn)
                val albumID = cursor.getString(albumIDColumn)
                val album = cursor.getString(albumColumn)
                val path = cursor.getString(pathColumn)

                Log.d("WTF", "ID: $id, Name: $name, ARTITST: $artist\" , PATH: $path")

                // append song to list
                // media store doesn't support multi artists...
                // do not link album (and whatever song id) with youtube yet, figure that out later

                /**
                 * Compiles a song with all it's necessary metadata. Unlike MediaStore,
                 * this also supports, multiple genres (TBD), and a few extra details (TBD).
                 */
                fun advancedScan(): Song {
                    var artists = ArrayList<ArtistEntity>()
//                    var generes
//                    var year: String? = null

                    try {
                        val artistString = getExtraMetadata("$sdcardRoot$path$name")

                        // parse the data
                        artistString.artists.split(';').forEach { artistVal ->

                            // check if this artist exists in DB already
                            val databaseArtistMatch =
                                runBlocking(Dispatchers.IO) {
                                    database.searchArtists(artistVal).first().filter { artist ->
                                        return@filter artist.artist.name == artistVal
                                    }
                                }

                            Log.d("WTF", "ARTIST FOUND IN DB???" + databaseArtistMatch.size)

                            if (databaseArtistMatch.isEmpty()) {

                                // hit up YouTube for artist
                                runBlocking(Dispatchers.IO) {
                                    search(artistVal, YouTube.SearchFilter.FILTER_ARTIST).onSuccess { result ->

                                        val foundArtist = result.items.first()
                                        artists.add(
                                            ArtistEntity(
                                                // I pray the first search result will always be the correct one
                                                foundArtist.id,
                                                foundArtist.title,
                                                foundArtist.thumbnail
                                            )
                                        )
                                        Log.d("WTF", "WHO TF IS THIS" + result.items.first().title)

                                    }.onFailure {
                                        // create temporary artists
                                        Log.w("WTF", "FAILED TO ADD ARTIST, genrtaing one ")
                                        artists.add(ArtistEntity(ArtistEntity.generateArtistId(), artistVal))
                                    }
                                }
                            } else {
                                artists.add(
                                    databaseArtistMatch.first().artist
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // fallback on media store
                        Log.d(
                            "WTF",
                            "ERROR READING ARTIST METADATA, ${e.message} falling back on MediaStore for $path$name"
                        )
                        e.printStackTrace()

                        artists.add(ArtistEntity(artistID, artist, isLocal = true))
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

                scannerJobs.add(async(Dispatchers.IO) { advancedScan() })
            }
        }

        scannerJobs.awaitAll()
    }

    // build the tree
    scannerJobs.forEach {
        val song = it.getCompleted()

        song.song.localPath?.let { it ->
            newDirectoryStructure.insert(
                it.substringAfter(sdcardRoot), song
            )
        }
    }

    cachedDirectoryTree = newDirectoryStructure
    return MutableStateFlow(newDirectoryStructure)
}

/**
 * Update the Database with local files
 *
 * @param database
 * @param directoryStructure
 * @param matchStrength How lax should the scanner be
 * @param strictFileNames Whether to consider file names
 *
 * inserts a song if not found
 * updates a song information depending
 */
fun syncDB(
    database: MusicDatabase,
    directoryStructure: List<Song>,
    matchStrength: ScannerSensitivity,
    strictFileNames: Boolean
) {
    println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" + directoryStructure.size)

    database.transaction {
        directoryStructure.forEach { song ->
//            println("search for " + song.song.title.substringBeforeLast('.'))
            val querySong = database.searchSongs(song.song.title.substringBeforeLast('.'))



            CoroutineScope(Dispatchers.IO).launch {

                // check if this song is known to the library
                val songMatch = querySong.first().filter {
                    // match file names
                    if (strictFileNames &&
                        (it.song.localPath?.substringBeforeLast('/') !=
                            song.song.localPath?.substringBeforeLast('/'))
                    ) {
                        return@filter false
                    }
//                    println("song here:: " + it.song.title)

                    return@filter compareSong(it, song, matchStrength, strictFileNames)
                }


                /**
                 * TODO: update specific fields instead of whole object
                 */
                if (songMatch.isNotEmpty()) { // known song, update the song info in the database
                    println("WE HAVE THIS WANKER: " + song.song.title)
                    database.update(song.song)
                } else { // new song
                    println("WE inserrttt WANKER   " + song.song.title + song.artists.first().name + song.artists.first().id)
                    println(database.insert(song.toMediaMetadata()))
                }
                // do not delete songs from database automatically
            }
        }
    }

}

/**
 * Check if artists are the same
 *
 *  * Both null == same artists
 *  * Either null == different artists
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
    if (strictFileNames &&
        (a.song.localPath?.substringBeforeLast('/') !=
                b.song.localPath?.substringBeforeLast('/'))
    ) {
        return false
    }

    return when (matchStrength) {
        ScannerSensitivity.LEVEL_1 -> a.song.title == b.song.title
        ScannerSensitivity.LEVEL_2 -> a.song.title == b.song.title &&
                compareArtist(a.artists, b.artists)

        ScannerSensitivity.LEVEL_3 -> a.song.title == b.song.title &&
                compareArtist(a.artists, b.artists) && true // album compare go here
    }
}


object CachedBitmap {
    var path: String? = null
    var image: Bitmap? = null

    /**
     * Adds an image to the cache
     */
    fun cache(path: String, image: Bitmap?) {
        if (image == null) {
            return
        }

        this.path = path
        this.image = image
        bitmapCache.add(this)
    }

    /**
     * Retrieves an image from the cache
     */
    fun retrieveImage(path: String): Bitmap? {
        return bitmapCache.first { it.path == path }.image
    }
}

/**
 * TODO: Fix the root cause of the miniplayer constantly needing reloading
 * TODO: Clear the cache on library re-scan
 */
// memory leak? who cares? speed is king!
var bitmapCache = ArrayList<CachedBitmap>()

/**
 * Extract the album art from the audio file
 *
 * @param path Full path of audio file
 */
fun getLocalThumbnail(path: String?): Bitmap? {
    if (path == null) {
        return null
    }

    // get cached image
    try {
        return CachedBitmap.retrieveImage(path)
    } catch (_: NoSuchElementException) {
    }


    val mData = MediaMetadataRetriever()
    mData.setDataSource(path)

    val image: Bitmap? = try {
        val art = mData.embeddedPicture
        BitmapFactory.decodeByteArray(art, 0, art!!.size)
    } catch (e: Exception) {
        null
    }

    CachedBitmap.cache(path, image)
    return image
}


/**
 * Get cached directory tree
 */
fun getDirectorytree(): DirectoryTree? {
    if (cachedDirectoryTree == null) {
        return null
    }
    return cachedDirectoryTree
}