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

/** Sonora ID: account con email e password per sincronizzare i brani preferiti tra dispositivi. */
object SonoraId {
    private fun prefs(c: Context) = c.getSharedPreferences("sonora", Context.MODE_PRIVATE)

    var email by mutableStateOf<String?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    private var token: String? = null

    val signedIn: Boolean get() = token != null

    fun init(c: Context) {
        token = prefs(c).getString("sid_token", null)
        email = prefs(c).getString("sid_email", null)
        if (token == null) email = null
    }

    private suspend fun call(c: Context, method: String, path: String, body: JSONObject?, auth: Boolean): JSONObject =
        withContext(Dispatchers.IO) {
            val base = Tracking.serverUrl(c)
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

    private fun accept(c: Context, mail: String, t: String) {
        token = t
        email = mail
        prefs(c).edit().putString("sid_token", t).putString("sid_email", mail).remove("sid_removed").apply()
    }

    suspend fun register(c: Context, mail: String, password: String) {
        val m = mail.trim()
        val r = call(c, "POST", "/v1/id/register", JSONObject().put("email", m).put("password", password), auth = false)
        accept(c, m, r.optString("token").ifEmpty { throw IOException("Risposta non valida") })
    }

    suspend fun login(c: Context, mail: String, password: String) {
        val m = mail.trim()
        val r = call(c, "POST", "/v1/id/login", JSONObject().put("email", m).put("password", password), auth = false)
        accept(c, m, r.optString("token").ifEmpty { throw IOException("Risposta non valida") })
    }

    private fun clearLocal(c: Context) {
        token = null
        email = null
        prefs(c).edit().remove("sid_token").remove("sid_email").remove("sid_removed").apply()
    }

    suspend fun logout(c: Context) {
        runCatching { call(c, "POST", "/v1/id/logout", JSONObject(), auth = true) }
        clearLocal(c)
    }

    /** Elimina l'account e i preferiti dal server (sul telefono resta tutto). */
    suspend fun delete(c: Context) {
        call(c, "DELETE", "/v1/id", null, auth = true)
        clearLocal(c)
    }

    /** Ricorda che un brano è stato tolto dai preferiti, così alla prossima sincronizzazione sparisce anche dal server. */
    fun onUnlike(c: Context, id: String) {
        if (!signedIn) return
        val p = prefs(c)
        val set = (p.getStringSet("sid_removed", emptySet()) ?: emptySet()).toMutableSet()
        set.add(id)
        p.edit().putStringSet("sid_removed", set).apply()
    }

    /** Invia i preferiti del telefono e unisce quelli del server. */
    suspend fun sync(c: Context, store: Store) {
        if (!signedIn || syncing) return
        syncing = true
        message = null
        try {
            val p = prefs(c)
            val removed = (p.getStringSet("sid_removed", emptySet()) ?: emptySet()).toList()
            val body = JSONObject()
                .put("likes", JSONArray(store.liked.toList().take(1000).map { t ->
                    JSONObject().put("id", t.id).put("title", t.title).put("artist", t.artist)
                        .put("thumb", t.thumb ?: "").put("dur", t.durationSec)
                }))
                .put("removed", JSONArray(removed))
            val r = call(c, "POST", "/v1/id/sync", body, auth = true)
            val arr = r.optJSONArray("likes") ?: JSONArray()
            val remote = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Track(o.getString("id"), o.optString("title"), o.optString("artist"), o.optString("thumb").ifEmpty { null }, o.optLong("dur"))
            }
            store.setLikedFromSync(remote)
            p.edit().remove("sid_removed").apply()
            message = "Sincronizzato"
        } catch (e: IOException) {
            message = e.message ?: "Sincronizzazione non riuscita"
        } finally {
            syncing = false
        }
    }
}
