package com.sonora.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {
    /** Indice della riga da evidenziare alla posizione data (-1 se non ancora iniziato). */
    fun activeIndex(positionMs: Long): Int {
        if (!synced) return -1
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs + 150) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}

object LyricsRepository {

    const val SOURCE_LRCLIB = "LRCLIB"

    /** Fonti disponibili nelle impostazioni (per ora solo LRCLIB). */
    val sources = listOf(SOURCE_LRCLIB)

    suspend fun fetch(source: String, title: String, artist: String, durationSec: Long): Lyrics? =
        withContext(Dispatchers.IO) {
            try {
                when (source) {
                    SOURCE_LRCLIB -> lrclib(title, artist, durationSec)
                    else -> lrclib(title, artist, durationSec)
                }
            } catch (e: IOException) {
                null
            } catch (e: JSONException) {
                null
            }
        }

    private fun JSONObject.str(key: String): String? {
        if (isNull(key)) return null
        return optString(key, "").takeIf { it.isNotBlank() }
    }

    private fun lrclib(rawTitle: String, rawArtist: String, durationSec: Long): Lyrics? {
        val title = cleanTitle(rawTitle)
        val artist = firstArtist(rawArtist)

        var results = search(mapOf("track_name" to title, "artist_name" to artist))
        if (results.isEmpty()) results = search(mapOf("q" to "$artist $title"))
        if (results.isEmpty()) return null

        fun diff(o: JSONObject): Double =
            if (durationSec > 0 && !o.isNull("duration")) abs(o.optDouble("duration") - durationSec) else 0.0

        val best = results
            .filter { it.str("syncedLyrics") != null }
            .minByOrNull { diff(it) }

        if (best != null && diff(best) <= 8.0) {
            parseLrc(best.str("syncedLyrics") ?: "")?.let { return Lyrics(it, true) }
        }

        val plain = results.firstNotNullOfOrNull { it.str("plainLyrics") } ?: return null
        val lines = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(0L, it) }
        return if (lines.isEmpty()) null else Lyrics(lines, false)
    }

    private fun search(params: Map<String, String>): List<JSONObject> {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Sonora/1.0 (https://github.com/realmike0/sonora)")
            .build()
        Http.client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            val arr = JSONArray(r.body?.string().orEmpty())
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }
    }

    private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    private fun parseLrc(lrc: String): List<LyricLine>? {
        val out = mutableListOf<LyricLine>()
        for (line in lrc.lines()) {
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = line.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (m in tags) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val millis = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                out += LyricLine((minutes * 60 + seconds) * 1000 + millis, text)
            }
        }
        return out.sortedBy { it.timeMs }.takeIf { it.isNotEmpty() }
    }

    private val noise = Regex(
        """\s*[(\[][^)\]]*(official|video|audio|lyric|visuali[sz]er|remaster|hd|4k|mv)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE
    )

    private fun cleanTitle(title: String): String =
        title.replace(noise, "").trim().ifEmpty { title }

    private val artistSplit = Regex("""\s*(,|&|\bfeat\.?\s|\bft\.?\s)\s*""", RegexOption.IGNORE_CASE)

    private fun firstArtist(artist: String): String =
        artist.split(artistSplit).firstOrNull()?.trim().orEmpty().ifEmpty { artist }
}
