package com.sonora.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: String = "") {
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

    const val AUTO = "Auto"
    const val SOURCE_LRCLIB = "LRCLIB"

    /** Fonti verificate. "Auto" le prova tutte insieme e sceglie il testo migliore. */
    val sources = listOf(AUTO, SOURCE_LRCLIB, "BetterLyrics", "Unison", "LyricsPlus", "KuGou")

    // Ogni richiesta dei testi ha al massimo 8 secondi, così una fonte lenta non blocca le altre.
    private val client by lazy { Http.client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build() }
    private const val UA = "Sonora/1.0 (https://github.com/realmike0/sonora)"

    private fun get(url: okhttp3.HttpUrl): String? {
        val request = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(request).execute().use { r ->
            return if (r.isSuccessful) r.body?.string() else null
        }
    }

    private fun url(base: String, vararg params: Pair<String, String>) =
        base.toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()

    suspend fun fetch(source: String, title: String, artist: String, durationSec: Long): Lyrics? =
        withContext(Dispatchers.IO) {
            val t = cleanTitle(title)
            val a = firstArtist(artist)
            val names = if (source == AUTO || source !in sources) sources.drop(1) else listOf(source)
            var found = pickBest(runAll(names, t, a, durationSec), durationSec)
            if (found == null && names.size == 1) {
                found = pickBest(runAll(sources.drop(1) - names.toSet(), t, a, durationSec), durationSec)
            }
            found
        }

    private suspend fun runAll(names: List<String>, t: String, a: String, d: Long): List<Lyrics> = coroutineScope {
        names.map { n ->
            async(Dispatchers.IO) {
                try {
                    fetchOne(n, t, a, d)?.let { it.copy(source = n) }
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun fetchOne(name: String, t: String, a: String, d: Long): Lyrics? = when (name) {
        SOURCE_LRCLIB -> lrclib(t, a, d)
        "BetterLyrics" -> betterLyrics(t, a, d)
        "Unison" -> unison(t, a, d)
        "LyricsPlus" -> lyricsPlus(t, a, d)
        "KuGou" -> kugou(t, a, d)
        else -> null
    }

    private val priority = mapOf(SOURCE_LRCLIB to 6, "BetterLyrics" to 5, "Unison" to 4, "LyricsPlus" to 3, "KuGou" to 1)

    /** Sincronizzato batte non sincronizzato; la durata dell'ultima riga deve combaciare col brano; a parità vince la fonte più affidabile. */
    private fun pickBest(list: List<Lyrics>, durationSec: Long): Lyrics? = list.maxByOrNull { l ->
        var score = (priority[l.source] ?: 0).toDouble()
        if (l.synced) {
            score += 100
            val last = l.lines.last().timeMs
            if (durationSec > 0) {
                val ratio = last / (durationSec * 1000.0)
                score += when {
                    ratio > 1.03 -> -60.0
                    ratio >= 0.6 -> 25.0
                    ratio >= 0.4 -> 8.0
                    else -> 0.0
                }
            }
            score += minOf(l.lines.size, 60) / 6.0
        } else {
            score += minOf(l.lines.size, 60) / 12.0
        }
        score
    }

    private fun JSONObject.str(key: String): String? {
        if (isNull(key)) return null
        return optString(key, "").takeIf { it.isNotBlank() }
    }

    private fun plainToLyrics(plain: String): Lyrics? {
        val lines = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(0L, it) }
        return if (lines.isEmpty()) null else Lyrics(lines, false)
    }

    private fun lrclib(title: String, artist: String, durationSec: Long): Lyrics? {
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
        return plainToLyrics(results.firstNotNullOfOrNull { it.str("plainLyrics") } ?: return null)
    }

    private fun search(params: Map<String, String>): List<JSONObject> {
        val body = get(url("https://lrclib.net/api/search", *params.toList().toTypedArray())) ?: return emptyList()
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    // ---------- BetterLyrics (TTML) ----------
    private fun betterLyrics(title: String, artist: String, durationSec: Long): Lyrics? {
        val body = get(url("https://api.betterlyrics.org/getLyrics", "s" to title, "a" to artist, "d" to durationSec.toString())) ?: return null
        val ttml = JSONObject(body).str("ttml") ?: return null
        return parseTtml(ttml)
    }

    // ---------- Unison ----------
    private fun unison(title: String, artist: String, durationSec: Long): Lyrics? {
        val body = get(url("https://unison.boidu.dev/lyrics", "song" to title, "artist" to artist, "duration" to durationSec.toString())) ?: return null
        val root = JSONObject(body)
        if (!root.optBoolean("success")) return null
        val d = root.optJSONObject("data") ?: return null
        val text = d.str("lyrics") ?: return null
        return when (d.optString("format")) {
            "ttml" -> parseTtml(text)
            "lrc" -> parseLrc(text)?.let { Lyrics(it, true) }
            else -> plainToLyrics(text)
        }
    }

    // ---------- LyricsPlus (non verificata: se la risposta cambia, la fonte viene semplicemente saltata) ----------
    private fun lyricsPlus(title: String, artist: String, durationSec: Long): Lyrics? {
        val body = get(url("https://lyricsplus.prjktla.workers.dev/v2/lyrics/get", "title" to title, "artist" to artist, "duration" to durationSec.toString())) ?: return null
        val arr = JSONObject(body).optJSONArray("lyrics") ?: return null
        val out = (0 until arr.length()).mapNotNull {
            val o = arr.optJSONObject(it) ?: return@mapNotNull null
            val text = o.optString("text").trim()
            if (text.isEmpty() || !o.has("time")) null else LyricLine(o.optLong("time"), text)
        }.sortedBy { it.timeMs }
        return if (out.isEmpty()) null else Lyrics(out, true)
    }

    // ---------- KuGou ----------
    private fun kugou(title: String, artist: String, durationSec: Long): Lyrics? {
        val s = get(url("https://mobileservice.kugou.com/api/v3/search/song", "version" to "9108", "plat" to "0", "pagesize" to "8", "showtype" to "0", "keyword" to "$artist $title")) ?: return null
        val info = JSONObject(s).optJSONObject("data")?.optJSONArray("info") ?: return null
        val tl = title.lowercase()
        val song = (0 until info.length()).mapNotNull { info.optJSONObject(it) }
            .filter { it.optString("songname").lowercase().let { n -> n.contains(tl) || tl.contains(n) } }
            .filter { durationSec <= 0 || abs(it.optLong("duration") - durationSec) <= 6 }
            .firstOrNull() ?: return null
        val c = get(url("https://krcs.kugou.com/search", "ver" to "1", "man" to "yes", "client" to "mobi", "hash" to song.optString("hash"), "duration" to (song.optLong("duration") * 1000).toString())) ?: return null
        val cand = JSONObject(c).optJSONArray("candidates")?.optJSONObject(0) ?: return null
        val d = get(url("https://lyrics.kugou.com/download", "ver" to "1", "client" to "pc", "id" to cand.optString("id"), "accesskey" to cand.optString("accesskey"), "fmt" to "lrc", "charset" to "utf8")) ?: return null
        val content = JSONObject(d).str("content") ?: return null
        val lrc = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT), Charsets.UTF_8).removePrefix("\uFEFF")
        return parseLrc(lrc)?.let { Lyrics(it, true) }
    }

    // ---------- TTML ----------
    private val ttmlLine = Regex("""<p\b[^>]*?\bbegin="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val tag = Regex("<[^>]+>")

    private fun ttmlTime(t: String): Long {
        val parts = t.removeSuffix("s").split(":")
        var sec = 0.0
        for (p in parts) sec = sec * 60 + (p.toDoubleOrNull() ?: return 0L)
        return (sec * 1000).toLong()
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private fun parseTtml(ttml: String): Lyrics? {
        val out = ttmlLine.findAll(ttml).mapNotNull { m ->
            val text = unescape(m.groupValues[2].replace(tag, "")).replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) null else LyricLine(ttmlTime(m.groupValues[1]), text)
        }.toList().sortedBy { it.timeMs }
        return if (out.isEmpty()) null else Lyrics(out, true)
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
