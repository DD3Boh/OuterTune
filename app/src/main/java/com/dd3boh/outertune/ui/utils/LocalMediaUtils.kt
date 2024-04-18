package com.dd3boh.outertune.ui.utils

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.models.toMediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


/**
 * A tree representation of local audio files
 *
 * @param path root directory start
 */
class DirectoryTree(path: String) {
    private var currentDir = path // file name, id

    // folder contents
    private var subdirs = ArrayList<DirectoryTree>()
    private var files = ArrayList<Song>()

    fun insert(path: String, song: Song) {
//        println("curr path =" + path)

        // add a file
        if (path.indexOf("/") == -1) {
            files.add(song)
//            println("add file to " + path)
            return
        }

        // there is still subdirs to process
        var tmppath = path
        if (path[path.length - 1] == '/') {
            tmppath = path.substring(0, path.length - 1)
        }
        val subdirPath = tmppath.split("/")[tmppath.split("/").size - 1]

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
            tree.insert(subdirPath, song)
            subdirs.add(tree)

        } else {
            existingSubdir!!.insert(subdirPath, song)
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


val testScanPaths = arrayListOf("Music")

/**
 * Dev uses
 */
fun scanLocal(context: Context) = scanLocal(context, testScanPaths)

/**
 * Scan MediaStore for songs given a list of paths to scan for.
 *
 * @param context Context
 * @param scanPaths List of whitelist paths to scan under. This assumes
 * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
 * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
 */
fun scanLocal(context: Context, scanPaths: ArrayList<String>): DirectoryTree {
    val directoryStructure = DirectoryTree("/storage/emulated/0/")
    val contentResolver: ContentResolver = context.contentResolver

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

    // Query for audio files
    val cursor = contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?",
        scanPaths.map { "/storage/emulated/0/$it%" }.toTypedArray(), // whitelist paths
        null
    )
    Log.d("WTF", "------------------------------------------")
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
            // do not link artist and album (and whatever id) with youtube yet, figure that out later
            val artists = ArrayList<ArtistEntity>()
            artists.add(ArtistEntity(artistID, artist, isLocal = true)) // placeholder

            directoryStructure.insert(
                "$path$name",
                Song(
                    SongEntity(
                        id.toString(),
                        title,
                        (duration / 1000), // we use seconds for duration
                        albumId = albumID,
                        albumName = album,
                        isLocal = true,
                        localPath = "/storage/emulated/0/$path$name"
                    ),
                    artists,
//                worry about album later
                )
            )
        }
    }

    return directoryStructure
}


/**
 * Specify how strict the metadata scanner should be
 */
enum class ScannerSensitivity {
    LEVEL_1, // Title only
    LEVEL_2, // Title and artists
    LEVEL_3, // Title, artists, albums

}


fun syncDB(database: MusicDatabase, directoryStructure: List<Song>) {
    syncDB(database, directoryStructure, ScannerSensitivity.LEVEL_2, true)
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

            /**
             * Check if artists are the same
             */
            fun compareArtist(a: List<ArtistEntity>): Boolean {
                val matchingArtists = a.filter { artist ->
                    song.artists.any { it.name == artist.name }
                }

                return matchingArtists.size == a.size
            }

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

                    // rest of metadata
                    when (matchStrength) {
                        ScannerSensitivity.LEVEL_1 -> it.song.title.compareTo(song.song.title) == 1
                        ScannerSensitivity.LEVEL_2 -> it.song.title.compareTo(song.song.title) == 1 && compareArtist(
                            it.artists
                        )

                        ScannerSensitivity.LEVEL_3 -> it.song.title.compareTo(song.song.title) == 1 && compareArtist(
                            it.artists
                        ) && true
                    }
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