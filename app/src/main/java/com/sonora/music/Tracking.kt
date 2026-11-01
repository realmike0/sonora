package com.sonora.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Stato del tracciamento: "unset" (da chiedere), "declined" (rifiutato) o "accepted" (attivo e verificato). */
object Tracking {
    private fun prefs(c: Context) = c.getSharedPreferences("sonora", Context.MODE_PRIVATE)

    var state by mutableStateOf("unset")
        private set
    private var token: String? = null
    private var uploading = false

    fun init(c: Context) {
        val p = prefs(c)
        token = p.getString("track_token", null)
        state = p.getString("track_state", "unset") ?: "unset"
        if (state == "accepted" && token == null) state = "unset"
    }

    fun serverUrl(c: Context): String =
        (prefs(c).getString("server_url", null)?.takeIf { it.isNotBlank() } ?: Config.SERVER_URL).trim().trimEnd('/')

    fun setServerUrl(c: Context, url: String) {
        prefs(c).edit().putString("server_url", url.trim()).apply()
    }

    fun hasServer(c: Context) = serverUrl(c).isNotEmpty()

    /** L'utente non vuole essere tracciato: nulla lascia il telefono. */
    fun decline(c: Context) {
        prefs(c).edit().putString("track_state", "declined").remove("track_token").apply()
        token = null
        state = "declined"
    }

    private suspend fun call(c: Context, method: String, path: String, body: JSONObject?, auth: Boolean): JSONObject =
        withContext(Dispatchers.IO) {
            val base = serverUrl(c)
            if (base.isEmpty()) throw IOException("Server non configurato")
            val b = Request.Builder().url(base + path)
                .method(method, body?.toString()?.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "Sonora")
            if (auth) b.header("Authorization", "Bearer ${token ?: ""}")
            Http.client.newCall(b.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (!r.isSuccessful) throw IOException(json.optString("error").ifEmpty { "Errore del server (${r.code})" })
                json
            }
        }

    /** Invia un codice a 6 cifre all'email. */
    suspend fun requestCode(c: Context, email: String) {
        call(c, "POST", "/v1/code", JSONObject().put("email", email.trim()), auth = false)
    }

    /** Controlla il codice: se è giusto il tracciamento parte da questo momento (la cronologia passata resta sul telefono). */
    suspend fun verify(c: Context, email: String, code: String) {
        val r = call(c, "POST", "/v1/verify", JSONObject().put("email", email.trim()).put("code", code.trim()), auth = false)
        val t = r.optString("token")
        if (t.isEmpty()) throw IOException("Codice non valido")
        accept(c, t)
    }

    private fun accept(c: Context, t: String) {
        token = t
        prefs(c).edit().putString("track_token", t).putString("track_state", "accepted")
            .putLong("track_cursor", System.currentTimeMillis()).apply()
        state = "accepted"
    }

    /** Verifica con Telegram: crea una richiesta e restituisce l'id da mettere nel link del bot. */
    suspend fun telegramStart(c: Context): String =
        call(c, "POST", "/v1/tg/start", JSONObject(), auth = false).optString("sid").ifEmpty { throw IOException("Risposta non valida") }

    /** Aspetta (fino a 10 minuti) che l'utente condivida il numero nel bot. */
    suspend fun telegramWait(c: Context, sid: String) {
        repeat(300) {
            val r = call(c, "POST", "/v1/tg/poll", JSONObject().put("sid", sid), auth = false)
            val t = r.optString("token")
            if (t.isNotEmpty()) {
                accept(c, t)
                return
            }
            kotlinx.coroutines.delay(2000)
        }
        throw IOException("Tempo scaduto, riprova")
    }

    /** Spegne il tracciamento (i dati già sul server restano finché non li elimini). */
    fun disable(c: Context) {
        prefs(c).edit().putString("track_state", "declined").remove("track_token").apply()
        token = null
        state = "declined"
    }

    /** Cancella dal server tutto ciò che riguarda l'utente. */
    suspend fun deleteServerData(c: Context) {
        call(c, "DELETE", "/v1/me", null, auth = true)
        disable(c)
    }

    fun onNewEvent(c: Context) {
        if (state != "accepted" || uploading) return
        uploading = true
        Thread {
            try {
                kotlinx.coroutines.runBlocking { upload(c) }
            } catch (e: Exception) {
                // riprova al prossimo ascolto
            } finally {
                uploading = false
            }
        }.start()
    }

    private suspend fun upload(c: Context) {
        val p = prefs(c)
        val cursor = p.getLong("track_cursor", 0L)
        val pending = PlayLog.all().filter { it.ts > cursor }.take(200)
        if (pending.isEmpty()) return
        call(c, "POST", "/v1/events", JSONObject().put("events", JSONArray(pending.map { it.toJson() })), auth = true)
        p.edit().putLong("track_cursor", pending.maxOf { it.ts }).apply()
    }

    suspend fun serverWrapped(c: Context, year: Int): WrappedStats {
        val j = call(c, "GET", "/v1/wrapped?year=$year", null, auth = true)
        return WrappedStats.fromJson(j)
    }
}
