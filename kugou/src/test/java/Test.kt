import com.zionhuang.kugou.KuGou
import com.zionhuang.kugou.KuGou.generateKeyword
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class Test {
    @Test
    fun test() = runBlocking {
        val candidates = KuGou.getLyricsCandidate(
            generateKeyword("千年以後 (After A Thousand Years)", "陳零九"),
            285
        )
        assertTrue(candidates != null)
        val downloadedLyrics = KuGou.getLyrics("楊丞琳", "點水", 259)
        println(downloadedLyrics)
        assertTrue(downloadedLyrics.isSuccess)
    }

    @Test
    fun searchAlanWalkerSong() = runBlocking {
        val songName = "Faded"
        val artistName = "Alan Walker"

        val keyword = generateKeyword(songName, artistName)
        val song = KuGou.searchSongs(keyword)

        assertTrue(song.data.info.isNotEmpty())

        val candidates = KuGou.getLyricsCandidate(
            keyword,
            song.data.info.first().duration
        )

        assertTrue(candidates != null)

        val downloadedLyrics = KuGou.getLyrics(songName, artistName, song.data.info.first().duration)
        println(downloadedLyrics)
        assertTrue(downloadedLyrics.isSuccess)
    }
}
