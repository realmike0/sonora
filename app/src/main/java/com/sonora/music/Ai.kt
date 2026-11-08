package com.sonora.music

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Funzioni AI di Sonora: il server le chiede a Cloudflare Workers AI (nessuna chiave sul telefono). */
object Ai {
    data class Rating(
        val score: Double, val verdict: String,
        val meaning: Double = 0.0, val melody: Double = 0.0, val flow: Double = 0.0,
        val tracks: List<Double> = emptyList()
    )

    private fun parse(o: JSONObject): Rating {
        val arr = o.optJSONArray("tracks")
        return Rating(
            o.optDouble("score", 0.0), o.optString("verdict"),
            o.optDouble("meaning", 0.0), o.optDouble("melody", 0.0), o.optDouble("flow", 0.0),
            arr?.let { a -> (0 until a.length()).map { a.optDouble(it, 0.0) } } ?: emptyList()
        )
    }

    private fun prefs(c: Context) = c.getSharedPreferences("sonora", Context.MODE_PRIVATE)

    private suspend fun call(c: Context, path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val base = Tracking.serverUrl(c)
        if (base.isEmpty()) throw IOException("Server non configurato")
        body.put("lang", com.sonora.music.ui.Lang.effective())
        val req = Request.Builder().url(base + path)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", "Sonora")
            .apply { SonoraId.authHeader()?.let { header("Authorization", it) } }.build()
        Http.client.newBuilder().callTimeout(40, java.util.concurrent.TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (!r.isSuccessful) throw IOException(json.optString("error").ifEmpty { "Errore AI (${r.code})" })
            json
        }
    }

    /** Voto e giudizio di un album o di un artista. Il risultato si ricorda sul telefono. */
    suspend fun rate(c: Context, kind: String, name: String, artist: String, tracks: List<String>): Rating {
        val lang = com.sonora.music.ui.Lang.effective()
        val key = "ai_rate2_${kind}_${lang}_${name.lowercase()}_${artist.lowercase()}".take(120)
        prefs(c).getString(key, null)?.let { s ->
            runCatching { JSONObject(s) }.getOrNull()?.let { return parse(it) }
        }
        val r = call(
            c, "/v1/ai/rate",
            JSONObject().put("kind", kind).put("name", name).put("artist", artist).put("tracks", JSONArray(tracks.take(25)))
        )
        val rating = parse(r)
        if (rating.score > 0) prefs(c).edit().putString(key, r.toString()).apply()
        return rating
    }

    /** Scelta del giorno: una volta al giorno, in base agli ultimi ascolti. */
    suspend fun dailyPicks(c: Context, history: List<Track>): List<Track> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val p = prefs(c)
        if (p.getString("ai_picks_day", null) == today) {
            runCatching {
                val arr = JSONArray(p.getString("ai_picks", "[]"))
                val list = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Track(o.getString("id"), o.optString("title"), o.optString("artist"), o.optString("thumb").ifEmpty { null }, o.optLong("dur"))
                }
                if (list.isNotEmpty()) return list
            }
        }
        val r = call(
            c, "/v1/ai/picks",
            JSONObject().put("history", JSONArray(history.take(40).map { "${it.artist} - ${it.title}" }))
        )
        val arr = r.optJSONArray("picks") ?: JSONArray()
        val known = history.map { it.id }.toSet()
        // Ogni proposta si cerca su YouTube Music e si prende il primo risultato.
        val found = coroutineScope {
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                async { runCatching { MusicRepository.search("${o.optString("artist")} ${o.optString("title")}").firstOrNull() }.getOrNull() }
            }.awaitAll()
        }.filterNotNull().filter { it.id !in known }.distinctBy { it.id }
        if (found.isNotEmpty()) {
            val saved = JSONArray(found.map { t ->
                JSONObject().put("id", t.id).put("title", t.title).put("artist", t.artist).put("thumb", t.thumb ?: "").put("dur", t.durationSec)
            })
            p.edit().putString("ai_picks_day", today).putString("ai_picks", saved.toString()).apply()
        }
        return found
    }

    /** Ordine consigliato per un flusso fluido (indici nella lista data). */
    suspend fun mixOrder(c: Context, tracks: List<Track>): List<Int> {
        val r = call(c, "/v1/ai/mix", JSONObject().put("tracks", JSONArray(tracks.map { "${it.artist} - ${it.title}" })))
        val arr = r.optJSONArray("order") ?: throw IOException("Risposta non valida")
        return (0 until arr.length()).map { arr.getInt(it) }
    }
}
