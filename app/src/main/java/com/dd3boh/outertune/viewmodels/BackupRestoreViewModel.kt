package com.dd3boh.outertune.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.dd3boh.outertune.MainActivity
import com.dd3boh.outertune.R
import com.dd3boh.outertune.db.InternalDatabase
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.extensions.div
import com.dd3boh.outertune.extensions.zipInputStream
import com.dd3boh.outertune.extensions.zipOutputStream
import com.dd3boh.outertune.playback.MusicService
import com.dd3boh.outertune.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.db.InternalDatabase2
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    // TODO: make these calls non-blocking
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val TAG = BackupRestoreViewModel::class.simpleName.toString()
    fun backup(uri: Uri) {
        runCatching {
            context.applicationContext.contentResolver.openOutputStream(uri)?.use {
                it.buffered().zipOutputStream().use { outputStream ->
                    outputStream.setLevel(Deflater.BEST_COMPRESSION)
                    (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered().use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                        inputStream.copyTo(outputStream)
                    }
                    runBlocking(Dispatchers.IO) {
                        database.checkpoint()
                    }
                    FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }.onSuccess {
            Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
        }.onFailure {
            reportException(it)
            Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun restore(uri: Uri) {
        runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use {
                it.zipInputStream().use { inputStream ->
                    var entry = inputStream.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            SETTINGS_FILENAME -> {
                                (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream()
                                    .use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                            }

                            InternalDatabase.DB_NAME -> {
                                Log.i(TAG, "Starting database restore")
                                runBlocking(Dispatchers.IO) {
                                    database.checkpoint()
                                }
                                database.close()

                                Log.i(TAG, "Testing new database for compatibility...")
                                val destFile = context.getDatabasePath(InternalDatabase.TEST_DB_NAME)
                                destFile.parentFile?.apply {
                                    if (!exists()) mkdirs()
                                }
                                FileOutputStream(destFile).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }

                                val status = try {
                                    val t = InternalDatabase.newTestInstance(context, InternalDatabase.TEST_DB_NAME)
                                    t.openHelper.writableDatabase.isDatabaseIntegrityOk
                                    t.close()
                                    true
                                } catch (e: Exception) {
                                    Log.e(TAG, "DB validation failed", e)
                                    false
                                }

                                if (status) {
                                    Log.i(TAG, "Found valid database, proceeding with restore")
                                    destFile.inputStream().use { inputStream ->
                                        FileOutputStream(database.openHelper.writableDatabase.path).use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                } else {
                                    Log.e(TAG, "Incompatible database, aborting restore")
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.err_restore_incompatible_database),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        entry = inputStream.nextEntry
                    }
                }
            }

            val stopIntent = Intent(context, MusicService::class.java)
            context.stopService(stopIntent)
            val startIntent = Intent(context, MainActivity::class.java)
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(startIntent)
            exitProcess(0)
        }.onFailure {
            reportException(it)
            Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
    }
}


@HiltViewModel
class GramophoneExportViewModel @Inject constructor(
    // TODO: make these calls non-blocking
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val TAG = BackupRestoreViewModel::class.simpleName.toString()


    val gramophoneDatabase = InternalDatabase2.newInstance(context)

    var gramophoneSongId = 0L

    fun backup(uri: Uri, localOnly: Boolean = true) {
        runCatching {
            val dbFile = context.getDatabasePath(InternalDatabase2.DB_NAME)
            if (dbFile.exists()) {
                dbFile.delete()
            }

//            Looper.prepare()
            val rawDb = database.dumpPlayHistory(localOnly).filter { !it.playCount.isEmpty() || !it.event.isEmpty() }

            // due to a very apparent skill issue, I tracked play counts and history simultaneously. Fsck.
            // Subtract event from playCount
            val history = rawDb.map { song ->
                val s = song.song
                val pc = song.playCount
                val e = song.event

                if (e.isEmpty() || pc.isEmpty()) {
                    song
                } else {
                    e.forEach { event ->
                        fun add1Month(year: Int, month: Int): Pair<Int, Int> {
                            return if (month == 12) {
                                Pair(year + 1, 1)
                            } else {
                                Pair(year, month + 1)
                            }
                        }
                        for (playCount in pc) {
                            val nextMonth = add1Month(playCount.year, playCount.month)
                            val start = LocalDateTime.of(playCount.year, playCount.month, 1, 0, 0)

                            val end = LocalDateTime.of(nextMonth.first, nextMonth.second, 1, 0, 0).minusSeconds(1)
                            // subtract from a pc if the even is in range of this month to the next

                            if (event.timestamp.atOffset(ZoneOffset.UTC).toLocalDateTime() in start..<end) {
                                // this is possible ffs
                                if (playCount.count > 0) {
                                    playCount.count -= 1
                                }
//                                assert(playCount.count >= 0)
                                return@forEach
                            }
                        }


                    }

                    song.copy(playCount = pc.filter { it.count > 0 })
                }
            }

            history.forEach { song ->
                val s = song.song
                val pc = song.playCount
                val e = song.event

                val mediaItem = InternalDatabase2.genMediaItem(
                    chromaprint = s.acoustid,
                    title = s.title,
                    artist = song.artists.joinToString(";"),
                    album = s.albumName,
                    uri = s.localPath?.toUri(),
                )

                e.forEach {
                    gramophoneDatabase.recordEvent(
                        mediaItem,
                        it.timestamp.toEpochSecond(ZoneOffset.UTC),
                        it.playTime

                    )
                }
                pc.forEach {
                    assert(it.count > 0)
                    gramophoneDatabase.recordEventLegacy(
                        mediaItem,
                        month = it.month,
                        year = it.year,
                        count = it.count
                    )
                }
            }

            context.applicationContext.contentResolver.openOutputStream(uri)?.use {
                it.buffered().zipOutputStream().use { outputStream ->
                    outputStream.setLevel(Deflater.BEST_COMPRESSION)
//                    runBlocking(Dispatchers.IO) {
//                        gramophoneDatabase.checkpoint()
//                    }
                    FileInputStream(gramophoneDatabase.openHelper.writableDatabase.path).use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(InternalDatabase2.DB_NAME))
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }.onSuccess {
            gramophoneDatabase.close()
            Toast.makeText(context, "Successfully created export", Toast.LENGTH_SHORT).show()
        }.onFailure {

            reportException(it)
            gramophoneDatabase.close()
            Toast.makeText(context, "Failed to create export. See adb logcat", Toast.LENGTH_SHORT).show()
        }
    }
}