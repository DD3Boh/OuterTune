package com.dd3boh.outertune.lyrics

import com.dd3boh.outertune.ui.component.animateScrollDuration
import kotlin.math.pow

object LyricsUtils {
    private val timeMarksRegex = "\\[(\\d{2}:\\d{2})([.:]\\d+)?]".toRegex()


    /**
     * Give lyrics in LRC format, parse and return a list of LyricEntry.
     *
     * The following implementation is imported from Gramophone (https://github.com/AkaneTan/Gramophone)
     * and has been adapted for OverTune (mostly variable renaming).
     * Note: OverTube does not support lyric translations.
     *
     *
     * Formats we have to consider in this method are:
     *  - Simple LRC files (ref Wikipedia) ex: [00:11.22] hello i am lyric
     *  - "compressed LRC" with >1 tag for repeating line ex: [00:11.22][00:15.33] hello i am lyric
     *  - Invalid LRC with all-zero tags [00:00.00] hello i am lyric
     *  - Lyrics that aren't synced and have no tags at all
     *  - Translations, type 1 (ex: pasting first japanese and then english lrc file into one file)
     *  - Translations, type 2 (ex: translated line directly under previous non-translated line)
     *  - The timestamps can variate in the following ways: [00:11] [00:11:22] [00:11.22] [00:11.222] [00:11:222]
     *
     * Multiline format:
     * - This technically isn't part of any listed guidelines, however is allows for
     *      reading of otherwise discarded lyrics
     * - All the lines between sync point A and B are read as lyric text of A
     *
     * In the future, we also want to support:
     *  - Extended LRC (ref Wikipedia) ex: [00:11.22] <00:11.22> hello <00:12.85> i am <00:13.23> lyric
     *  - Wakaloke gender extension (ref Wikipedia)
     *  - [offset:] tag in header (ref Wikipedia)
     * We completely ignore all ID3 tags from the header as MediaStore is our source of truth.
     */
    fun parseLyrics(lyrics: String, trim: Boolean, multilineEnable: Boolean): List<LyricsEntry> {
        val list = mutableListOf<LyricsEntry>()
        var foundNonNull = false
        var lyricsText: StringBuilder? = StringBuilder()
        //val measureTime = measureTimeMillis {
        // Add all lines found on LRC (probably will be unordered because of "compression" or translation type)
        lyrics.lines().forEach { line ->
            timeMarksRegex.findAll(line).let { sequence ->
                if (sequence.count() == 0) {
                    return@let
                }
                var lyricLine: String
                sequence.forEach { match ->
                    val firstSync = match.groupValues.subList(1, match.groupValues.size)
                        .joinToString("")

                    val ts = parseTime(firstSync)
                    if (!foundNonNull && ts > 0) {
                        foundNonNull = true
                        lyricsText = null
                    }

                    if (multilineEnable) {
                        val startIndex = lyrics.indexOf(line) + firstSync.length + 1
                        var endIndex = lyrics.length // default to end
                        var nextSync = ""

                        // track next sync point if found
                        if (timeMarksRegex.find(lyrics, startIndex)?.value != null) {
                            nextSync = timeMarksRegex.find(lyrics, startIndex)?.value!!
                            endIndex = lyrics.indexOf(nextSync) - 1 // delete \n at end
                        }

                        // read as single line *IF* this is a single line lyric
                        if (nextSync == "[$firstSync]") {
                            lyricLine = line.substring(sequence.last().range.last + 1)
                                .let { if (trim) it.trim() else it }
                        } else {
                            lyricLine = lyrics.substring(startIndex + 1, endIndex)
                                .let { if (trim) it.trim() else it }
                        }
                    } else {
                        lyricLine = line.substring(sequence.last().range.last + 1)
                            .let { if (trim) it.trim() else it }
                    }

                    lyricsText?.append(lyricLine + "\n")
                    list.add(LyricsEntry(ts, lyricLine))
                }
            }
        }
        // Sort and mark as translations all found duplicated timestamps (usually one)
        list.sortBy { it.time }
        var previousTs = -1L
        list.forEach {
            it.isTranslation = (it.time == previousTs)
            previousTs = it.time
        }

        if (list.isEmpty() && lyrics.isNotEmpty()) {
            list.add(LyricsEntry(1, lyrics, false))
        } else if (!foundNonNull) {
            list.clear()
            list.add(LyricsEntry(1, lyricsText!!.toString(), false))
        }

        return list
    }

    /**
     * Parse a timestamp in string format (ex: [mm:ss.ms]) into a Long value
     *
     * The following implementation is imported from Gramophone (https://github.com/AkaneTan/Gramophone)
     */
    private fun parseTime(timeString: String): Long {
        val timeRegex = "(\\d{2}):(\\d{2})[.:](\\d+)".toRegex()
        val matchResult = timeRegex.find(timeString)

        val minutes = matchResult?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val seconds = matchResult?.groupValues?.get(2)?.toLongOrNull() ?: 0
        val millisecondsString = matchResult?.groupValues?.get(3)
        // if one specifies micro/pico/nano/whatever seconds for some insane reason,
        // scrap the extra information
        val milliseconds = (millisecondsString?.substring(0, millisecondsString.length.coerceAtMost(3)
        )?.toLongOrNull() ?: 0) * 10f.pow(3 - (millisecondsString?.length ?: 0)).toLong()

        return minutes * 60000 + seconds * 1000 + milliseconds
    }

    fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int {
        for (index in lines.indices) {
            if (lines[index].time >= position + animateScrollDuration) {
                return index - 1
            }
        }
        return lines.lastIndex
    }
}