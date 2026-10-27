package com.sonora.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Random

data class Shelf(val title: String, val tracks: List<Track>)

/** Contenuti della Home: 9 "scelte del giorno" + scaffali stile YouTube Music. */
data class HomeData(val day: String, val picks: List<Track>, val shelves: List<Shelf>)

object HomeRepository {

    private val FALLBACK_QUERIES = listOf(
        "Hit del momento" to "top hits italia",
        "Nuove uscite" to "nuove uscite musica",
        "Pop italiano" to "pop italiano",
        "Hip hop e rap" to "rap italiano",
        "Per rilassarsi" to "chill relax musica"
    )

    private val TYPE_WORDS = setOf(
        "brano", "song", "video", "singolo", "single", "ep", "album", "•", "·"
    )

    fun today(): String = LocalDate.now().toString()

    // ───────── Cache su disco (una Home al giorno) ─────────

    fun loadCached(dir: File): HomeData? = try {
        val f = File(dir, "home.json")
        if (!f.exists()) null else {
            val o = JSONObject(f.readText())
            HomeData(
                day = o.getString("day"),
                picks = o.getJSONArray("picks").toTracks(),
                shelves = o.getJSONArray("shelves").let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val s = arr.optJSONObject(i) ?: return@mapNotNull null
                        Shelf(s.optString("title"), s.getJSONArray("tracks").toTracks())
                    }
                }
            )
        }
    } catch (e: Exception) {
        null
    }

    fun saveCached(dir: File, data: HomeData) {
        try {
            val o = JSONObject()
            o.put("day", data.day)
            o.put("picks", JSONArray(data.picks.map { it.toJson() }))
            o.put("shelves", JSONArray(data.shelves.map { s ->
                JSONObject().put("title", s.title).put("tracks", JSONArray(s.tracks.map { it.toJson() }))
            }))
            File(dir, "home.json").writeText(o.toString())
        } catch (e: Exception) {
            // best-effort
        }
    }

    // ───────── Caricamento ─────────

    /**
     * Prima prova la Home di YouTube Music (personalizzata se l'account Google è collegato),
     * altrimenti ripiega su alcune ricerche.
     * [personal] = Mi piace e brani ascoltati: entrano nelle scelte del giorno.
     */
    suspend fun load(cookie: String?, personal: List<Track>): HomeData = withContext(Dispatchers.IO) {
        var shelves = try {
            fetchYtmHome(cookie)
        } catch (e: Exception) {
            emptyList()
        }
        if (shelves.sumOf { it.tracks.size } < 9) {
            shelves = fetchFallback()
        }
        if (shelves.isEmpty()) throw java.io.IOException("Nessun contenuto disponibile")

        val seed = LocalDate.now().toEpochDay()
        val rnd = Random(seed)
        val fromPersonal = personal.distinctBy { it.id }.shuffled(rnd).take(3)
        val pool = shelves.flatMap { it.tracks }.distinctBy { it.id }
            .filter { t -> fromPersonal.none { it.id == t.id } }
            .shuffled(rnd)
        val picks = (fromPersonal + pool).take(9)

        HomeData(today(), picks, shelves)
    }

    private suspend fun fetchFallback(): List<Shelf> = coroutineScope {
        FALLBACK_QUERIES.map { (title, q) ->
            async {
                try {
                    Shelf(title, MusicRepository.search(q).take(15))
                } catch (e: Exception) {
                    Shelf(title, emptyList())
                }
            }
        }.awaitAll().filter { it.tracks.isNotEmpty() }
    }

    // ───────── Home di YouTube Music ─────────

    private fun fetchYtmHome(cookie: String?): List<Shelf> {
        val page = GoogleSync.post(cookie, "FEmusic_home", null)
        val shelfNodes = mutableListOf<JSONObject>()
        GoogleSync.collect(page, "musicCarouselShelfRenderer", shelfNodes)

        val out = mutableListOf<Shelf>()
        for (node in shelfNodes) {
            val title = node.path("header", "musicCarouselShelfBasicHeaderRenderer", "title", "runs", 0, "text") as? String
                ?: continue
            val contents = node.optJSONArray("contents") ?: continue
            val tracks = mutableListOf<Track>()
            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i) ?: continue
                val t = item.optJSONObject("musicResponsiveListItemRenderer")?.let(::responsiveToTrack)
                    ?: item.optJSONObject("musicTwoRowItemRenderer")?.let(::twoRowToTrack)
                if (t != null) tracks += t
            }
            val unique = tracks.distinctBy { it.id }
            if (unique.size >= 3) out += Shelf(title, unique)
        }
        return out
    }

    private fun responsiveToTrack(item: JSONObject): Track? {
        val id = GoogleSync.firstString(item, "videoId") ?: return null
        val title = item.path(
            "flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs", 0, "text"
        ) as? String ?: return null
        val runs = item.path(
            "flexColumns", 1, "musicResponsiveListItemFlexColumnRenderer", "text", "runs"
        ) as? JSONArray
        val artist = firstArtist(runs)
        val thumbs = item.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails") as? JSONArray
        val dur = item.path(
            "fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer", "text", "runs", 0, "text"
        ) as? String
        return Track(id, title, artist, lastThumb(thumbs), GoogleSync.parseDuration(dur))
    }

    private fun twoRowToTrack(item: JSONObject): Track? {
        val id = item.path("navigationEndpoint", "watchEndpoint", "videoId") as? String ?: return null
        val title = item.path("title", "runs", 0, "text") as? String ?: return null
        val runs = item.path("subtitle", "runs") as? JSONArray
        val thumbs = item.path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails") as? JSONArray
        return Track(id, title, firstArtist(runs), lastThumb(thumbs), 0L)
    }

    private fun firstArtist(runs: JSONArray?): String {
        if (runs == null) return ""
        for (i in 0 until runs.length()) {
            val t = runs.optJSONObject(i)?.optString("text")?.trim().orEmpty()
            if (t.isNotEmpty() && t.lowercase() !in TYPE_WORDS) return t
        }
        return ""
    }

    private fun lastThumb(thumbs: JSONArray?): String? =
        thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")?.takeIf { it.isNotEmpty() }

    private fun JSONObject.path(vararg keys: Any): Any? {
        var cur: Any? = this
        for (k in keys) {
            cur = when (k) {
                is String -> (cur as? JSONObject)?.opt(k)
                is Int -> (cur as? JSONArray)?.opt(k)
                else -> null
            }
            if (cur == null) return null
        }
        return cur
    }
}
