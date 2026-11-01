package com.sonora.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Traduce i testi con il servizio gratuito di Google Traduttore (nessuna chiave). */
object LyricsTranslator {
    private val http = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    /** Restituisce una traduzione per ogni riga (stesso ordine), oppure null se non riesce. */
    suspend fun translate(lines: List<String>, target: String): List<String>? = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyList()
        val out = ArrayList<String>(lines.size)
        // A blocchi, per non superare la lunghezza massima della richiesta.
        var i = 0
        while (i < lines.size) {
            var j = i
            var len = 0
            while (j < lines.size && len + lines[j].length < 1500) {
                len += lines[j].length + 1
                j++
            }
            if (j == i) j = i + 1
            val chunk = lines.subList(i, j)
            val part = runCatching { requestChunk(chunk, target) }.getOrNull() ?: return@withContext null
            out.addAll(part)
            i = j
        }
        out
    }

    private fun requestChunk(chunk: List<String>, target: String): List<String>? {
        val text = chunk.joinToString("\n")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t&q=" +
            URLEncoder.encode(text, "UTF-8")
        val body = http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build())
            .execute().use { r -> if (!r.isSuccessful) return null else r.body?.string() ?: return null }
        val segs = JSONArray(body).getJSONArray(0)
        val sb = StringBuilder()
        for (k in 0 until segs.length()) sb.append(segs.getJSONArray(k).optString(0))
        val lines = sb.toString().split("\n")
        if (lines.size == chunk.size) return lines.map { it.trim() }
        // Se le righe non coincidono, traduce una per una.
        return chunk.map { l ->
            if (l.isBlank()) "" else {
                val u = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t&q=" +
                    URLEncoder.encode(l, "UTF-8")
                val b = http.newCall(Request.Builder().url(u).header("User-Agent", "Mozilla/5.0").build())
                    .execute().use { r -> if (!r.isSuccessful) return null else r.body?.string() ?: return null }
                val s = JSONArray(b).getJSONArray(0)
                val one = StringBuilder()
                for (k in 0 until s.length()) one.append(s.getJSONArray(k).optString(0))
                one.toString().trim()
            }
        }
    }
}
