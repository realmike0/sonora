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
    var verified by mutableStateOf(true)
        private set
    var syncing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    private var token: String? = null

    val signedIn: Boolean get() = token != null

    fun authHeader(): String? = token?.let { "Bearer $it" }

    fun init(c: Context) {
        token = prefs(c).getString("sid_token", null)
        email = prefs(c).getString("sid_email", null)
        verified = prefs(c).getBoolean("sid_verified", true)
        if (token == null) email = null
        pull(c)
    }

    /**
     * Se c'è il Framework, è lui a decidere quale account è attivo (puoi cambiarlo da lì).
     * La prima volta Sonora gli passa l'account che ha già; dopo, se tolgono l'account dal Framework, esce anche Sonora.
     */
    fun pull(c: Context) {
        if (!Framework.installed(c)) return
        val st = Framework.state(c) ?: return
        val p = prefs(c)
        val migrated = p.getBoolean("fw_migrated", false)
        if (st.email != null && !st.token.isNullOrEmpty()) {
            p.edit().putBoolean("fw_migrated", true).apply()
            if (st.email != email || st.token != token || st.verified != verified) {
                token = st.token
                email = st.email
                verified = st.verified
                p.edit().putString("sid_token", st.token).putString("sid_email", st.email).putBoolean("sid_verified", st.verified).apply()
            }
        } else if (token != null && !migrated) {
            p.edit().putBoolean("fw_migrated", true).apply()
            Framework.adopt(c, email.orEmpty(), token!!, verified)
        } else if (token != null && migrated) {
            clearLocal(c, tellFramework = false)
        }
    }

    private suspend fun call(c: Context, method: String, path: String, body: JSONObject?, auth: Boolean): JSONObject =
        withContext(Dispatchers.IO) {
            val base = Tracking.serverUrl(c)
            if (base.isEmpty()) throw IOException("Server non configurato")
            val b = Request.Builder().url(base + path)
                .method(method, body?.toString()?.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "Sonora")
                .header("x-device-id", Framework.deviceId(c))
                .header("x-device-name", Framework.deviceName())
            if (auth) b.header("Authorization", "Bearer ${token ?: ""}")
            Http.client.newCall(b.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (auth && r.code == 401) {
                    // Sessione scaduta o revocata: si esce e si chiede di rientrare.
                    clearLocal(c)
                    throw IOException("Sessione scaduta: accedi di nuovo")
                }
                if (auth && r.code == 404 && json.optString("error").startsWith("Nessuna verifica")) {
                    // Account vecchio: il codice parte solo al prossimo accesso.
                    clearLocal(c)
                    throw IOException("Esci e rientra con email e password per ricevere il codice")
                }
                if (!r.isSuccessful) throw IOException(json.optString("error").ifEmpty { "Errore del server (${r.code})" })
                json
            }
        }

    private fun accept(c: Context, mail: String, r: JSONObject) {
        val t = r.optString("token").ifEmpty { throw IOException("Risposta non valida") }
        token = t
        email = mail
        verified = r.optBoolean("verified", false)
        prefs(c).edit().putString("sid_token", t).putString("sid_email", mail).putBoolean("sid_verified", verified).remove("sid_removed").apply()
        Framework.adopt(c, mail, t, verified)
    }

    private fun setVerified(c: Context, v: Boolean) {
        verified = v
        prefs(c).edit().putBoolean("sid_verified", v).apply()
        email?.let { Framework.setVerified(c, it, v) }
    }

    /** Controlla sul server se l'email è già verificata (da chiamare quando si apre il Profilo). */
    suspend fun refreshStatus(c: Context) {
        if (!signedIn) return
        runCatching { setVerified(c, call(c, "GET", "/v1/id/me", null, auth = true).optBoolean("verified", false)) }
    }

    suspend fun verifyEmail(c: Context, code: String) {
        call(c, "POST", "/v1/id/verify", JSONObject().put("code", code.trim()), auth = true)
        setVerified(c, true)
    }

    suspend fun resendCode(c: Context) {
        call(c, "POST", "/v1/id/resend", JSONObject(), auth = true)
    }

    suspend fun register(c: Context, mail: String, password: String) {
        val m = mail.trim()
        val r = call(c, "POST", "/v1/id/register", JSONObject().put("email", m).put("password", password), auth = false)
        accept(c, m, r)
    }

    suspend fun login(c: Context, mail: String, password: String) {
        val m = mail.trim()
        val r = call(c, "POST", "/v1/id/login", JSONObject().put("email", m).put("password", password), auth = false)
        accept(c, m, r)
    }

    private fun clearLocal(c: Context, tellFramework: Boolean = true) {
        if (tellFramework) email?.let { Framework.remove(c, it) }
        token = null
        email = null
        verified = true
        prefs(c).edit().remove("sid_token").remove("sid_email").remove("sid_verified").remove("sid_removed").apply()
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

    // ---- Dediche ----
    data class Dedication(
        val id: String, val from: String, val to: String, val msg: String,
        val track: Track, val mine: Boolean
    )

    suspend fun dedications(c: Context): List<Dedication> {
        val r = call(c, "GET", "/v1/id/ded", null, auth = true)
        val a = r.optJSONArray("items") ?: JSONArray()
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Dedication(
                o.optString("id"), o.optString("from"), o.optString("to"), o.optString("msg"),
                Track(o.optString("track"), o.optString("title"), o.optString("artist"), o.optString("thumb").ifEmpty { null }, 0L),
                o.optBoolean("mine")
            )
        }
    }

    suspend fun dedicate(c: Context, t: Track, to: String, msg: String, from: String) {
        call(
            c, "POST", "/v1/id/ded",
            JSONObject().put("track", t.id).put("title", t.title).put("artist", t.artist)
                .put("thumb", t.thumb ?: "").put("to", to).put("msg", msg).put("from", from),
            auth = true
        )
    }

    suspend fun reportDedication(c: Context, id: String) { call(c, "POST", "/v1/id/ded/$id/report", JSONObject(), auth = true) }
    suspend fun deleteDedication(c: Context, id: String) { call(c, "DELETE", "/v1/id/ded/$id", null, auth = true) }

    // ───────── Commenti Sonora e album in attesa ─────────

    data class SonoraComment(val id: String, val name: String, val text: String, val ts: Long, val mine: Boolean, val role: String = "user", val since: Long = 0L)
    data class Upcoming(val id: String, val artist: String, val album: String, val cover: String?, val release: Long)

    suspend fun comments(c: Context, trackId: String): List<SonoraComment> {
        val r = call(c, "GET", "/v1/comments?track=$trackId", null, auth = signedIn)
        role = r.optString("me", "user").ifEmpty { "user" }
        val a = r.optJSONArray("items") ?: JSONArray()
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            SonoraComment(o.optString("id"), o.optString("name"), o.optString("text"), o.optLong("ts"), o.optBoolean("mine"),
                o.optString("role", "user"), o.optLong("since"))
        }
    }

    suspend fun postComment(c: Context, trackId: String, name: String, text: String) {
        call(c, "POST", "/v1/id/comments", JSONObject().put("track", trackId).put("name", name).put("text", text), auth = true)
    }

    suspend fun reportComment(c: Context, id: String) { call(c, "POST", "/v1/id/comments/$id/report", JSONObject(), auth = true) }
    suspend fun deleteComment(c: Context, id: String) { call(c, "DELETE", "/v1/id/comments/$id", null, auth = true) }

    suspend fun upcoming(c: Context): List<Upcoming> {
        val r = call(c, "GET", "/v1/upcoming", null, auth = false)
        val a = r.optJSONArray("items") ?: JSONArray()
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Upcoming(o.optString("id"), o.optString("artist"), o.optString("album"), o.optString("cover").ifEmpty { null }, o.optLong("release"))
        }
    }

    // ───────── Ruolo, posta in arrivo, ticket, moderazione ─────────

    /** "admin", "mod" oppure "user". */
    var role by mutableStateOf("user")
    var modSince by mutableStateOf(0L)
    var unread by mutableStateOf(0)
    val isMod: Boolean get() = role == "mod" || role == "admin"

    data class Notice(val id: String, val kind: String, val text: String, val ts: Long)
    data class InboxItem(val id: String, val kind: String, val text: String, val ts: Long, val read: Boolean)
    data class TicketRow(val id: String, val subject: String, val status: String, val updated: Long)
    data class TicketMsg(val side: String, val text: String, val ts: Long)
    data class TicketFull(val id: String, val subject: String, val status: String, val messages: List<TicketMsg>)

    /** Avvisi nuovi (reclami, sospensioni, ban, risposte ai ticket): arrivano una volta sola. Aggiorna anche ruolo e posta non letta. */
    suspend fun notices(c: Context): List<Notice> {
        val r = call(c, "GET", "/v1/id/notices", null, auth = true)
        role = r.optString("role", "user").ifEmpty { "user" }
        modSince = r.optLong("since")
        unread = r.optInt("unread")
        val a = r.optJSONArray("items") ?: JSONArray()
        return (0 until a.length()).map { val o = a.getJSONObject(it); Notice(o.optString("id"), o.optString("kind"), o.optString("text"), o.optLong("ts")) }
    }

    suspend fun inbox(c: Context): List<InboxItem> {
        val a = call(c, "GET", "/v1/id/inbox", null, auth = true).optJSONArray("items") ?: JSONArray()
        return (0 until a.length()).map { val o = a.getJSONObject(it); InboxItem(o.optString("id"), o.optString("kind"), o.optString("text"), o.optLong("ts"), o.optBoolean("read")) }
    }

    suspend fun inboxRead(c: Context) { call(c, "POST", "/v1/id/inbox/read", JSONObject(), auth = true); unread = 0 }

    suspend fun tickets(c: Context): List<TicketRow> {
        val a = call(c, "GET", "/v1/id/tickets", null, auth = true).optJSONArray("items") ?: JSONArray()
        return (0 until a.length()).map { val o = a.getJSONObject(it); TicketRow(o.optString("id"), o.optString("subject"), o.optString("status"), o.optLong("updated")) }
    }

    suspend fun ticket(c: Context, id: String): TicketFull {
        val r = call(c, "GET", "/v1/id/tickets/$id", null, auth = true)
        val a = r.optJSONArray("messages") ?: JSONArray()
        return TicketFull(r.optString("id"), r.optString("subject"), r.optString("status"),
            (0 until a.length()).map { val o = a.getJSONObject(it); TicketMsg(o.optString("side"), o.optString("text"), o.optLong("ts")) })
    }

    suspend fun newTicket(c: Context, subject: String, text: String): String =
        call(c, "POST", "/v1/id/tickets", JSONObject().put("subject", subject).put("text", text), auth = true).optString("id")

    suspend fun ticketMsg(c: Context, id: String, text: String) {
        call(c, "POST", "/v1/id/tickets/$id/msg", JSONObject().put("text", text), auth = true)
    }

    /** Strumenti dei moderatori: remove / suspend / warn / report su un commento. */
    suspend fun modAct(c: Context, comment: String, action: String, days: Int = 0, text: String = "") {
        call(c, "POST", "/v1/id/mod/act", JSONObject().put("comment", comment).put("action", action).put("days", days).put("text", text), auth = true)
    }
}
