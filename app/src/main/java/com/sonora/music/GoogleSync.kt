package com.sonora.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Importa le playlist dell'account Google usando l'API web di YouTube Music
 * con i cookie della sessione ottenuta dal login nella WebView.
 * Non è un'API ufficiale: se YouTube cambia formato può smettere di funzionare.
 */
object GoogleSync {

    private const val ORIGIN = "https://music.youtube.com"
    private const val BROWSE = "$ORIGIN/youtubei/v1/browse"

    fun hasSession(cookie: String): Boolean =
        cookieValue(cookie, "SAPISID") != null || cookieValue(cookie, "__Secure-3PAPISID") != null

    suspend fun importAll(cookie: String, onProgress: (String) -> Unit): List<Playlist> =
        withContext(Dispatchers.IO) {
            onProgress("Cerco le tue playlist…")
            val remote = listPlaylists(cookie)
            val result = mutableListOf<Playlist>()
            var lastError: IOException? = null

            remote.forEachIndexed { i, r ->
                onProgress("(${i + 1}/${remote.size}) ${r.title}")
                try {
                    val tracks = parseTracks(fetchAll(cookie, r.browseId))
                    if (tracks.isNotEmpty()) {
                        result += Playlist("yt_" + r.browseId.removePrefix("VL"), r.title, tracks, true)
                    }
                } catch (e: IOException) {
                    lastError = e
                }
            }

            if (result.isEmpty() && lastError != null) {
                throw IOException(lastError?.message ?: "Non sono riuscito a leggere le playlist")
            }
            result
        }

    data class Account(val name: String?, val photo: String?)

    /** Nome e foto profilo dell'account collegato (menu account di YouTube Music). */
    suspend fun fetchAccount(cookie: String): Account? = withContext(Dispatchers.IO) {
        try {
            val json = post(cookie, null, null, "$ORIGIN/youtubei/v1/account/account_menu")
            val headers = mutableListOf<JSONObject>()
            collect(json, "activeAccountHeaderRenderer", headers)
            val h = headers.firstOrNull() ?: return@withContext null
            val thumbs = h.at("accountPhoto", "thumbnails") as? JSONArray
            val photo = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")?.takeIf { it.isNotEmpty() }
            val name = h.at("accountName", "runs", 0, "text") as? String
            Account(name, photo)
        } catch (e: Exception) {
            null
        }
    }

    // ───────── Richieste ─────────

    private fun cookieValue(cookie: String, name: String): String? =
        cookie.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun authHeader(cookie: String): String {
        val sapisid = cookieValue(cookie, "SAPISID")
            ?: cookieValue(cookie, "__Secure-3PAPISID")
            ?: throw IOException("Sessione Google non valida")
        val ts = System.currentTimeMillis() / 1000
        return "SAPISIDHASH ${ts}_${sha1("$ts $sapisid $ORIGIN")}"
    }

    private fun clientContext(): JSONObject {
        val version = "1." + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".01.00"
        return JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", version)
                .put("hl", "it")
                .put("gl", "IT")
        )
    }

    internal fun post(cookie: String?, browseId: String?, token: String?, endpoint: String = BROWSE): JSONObject {
        val body = JSONObject().put("context", clientContext())
        if (browseId != null) body.put("browseId", browseId)

        val url = StringBuilder("$endpoint?alt=json")
        if (token != null) {
            val t = URLEncoder.encode(token, "UTF-8")
            url.append("&ctoken=").append(t).append("&continuation=").append(t)
        }

        val builder = Request.Builder()
            .url(url.toString())
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
        if (cookie != null) {
            val fullCookie = if (cookie.contains("SOCS=")) cookie else "$cookie; SOCS=CAI"
            builder
                .header("X-Goog-AuthUser", "0")
                .header("Authorization", authHeader(cookie))
                .header("Cookie", fullCookie)
        } else {
            builder.header("Cookie", "SOCS=CAI")
        }
        val request = builder.build()

        Http.client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401 || r.code == 403) {
                throw IOException("Accesso scaduto: ricollega l'account Google dalle impostazioni")
            }
            if (!r.isSuccessful) throw IOException("Errore di YouTube Music (${r.code})")
            return JSONObject(text)
        }
    }

    /** Scarica la pagina e le sue continuazioni (liste lunghe). */
    private fun fetchAll(cookie: String, browseId: String, maxPages: Int = 40): List<JSONObject> {
        val pages = mutableListOf<JSONObject>()
        var page = post(cookie, browseId, null)
        pages += page
        val seen = HashSet<String>()
        while (pages.size < maxPages) {
            val token = continuationToken(page) ?: break
            if (!seen.add(token)) break
            page = try {
                post(cookie, null, token)
            } catch (e: IOException) {
                break
            }
            pages += page
        }
        return pages
    }

    // ───────── Parsing ─────────

    private data class Remote(val browseId: String, val title: String)

    private fun listPlaylists(cookie: String): List<Remote> {
        val out = LinkedHashMap<String, Remote>()
        for (page in fetchAll(cookie, "FEmusic_liked_playlists")) {
            val items = mutableListOf<JSONObject>()
            collect(page, "musicTwoRowItemRenderer", items)
            for (item in items) {
                val id = item.at("navigationEndpoint", "browseEndpoint", "browseId") as? String ?: continue
                if (!id.startsWith("VL") || id == "VLSE") continue
                val title = item.at("title", "runs", 0, "text") as? String ?: continue
                out.putIfAbsent(id, Remote(id, title))
            }
        }
        out.putIfAbsent("VLLM", Remote("VLLM", "Brani che ti piacciono"))
        return out.values.toList()
    }

    private fun parseTracks(pages: List<JSONObject>): List<Track> {
        val seen = HashSet<String>()
        val out = mutableListOf<Track>()
        for (page in pages) {
            val items = mutableListOf<JSONObject>()
            collect(page, "musicResponsiveListItemRenderer", items)
            for (item in items) {
                val id = firstString(item, "videoId") ?: continue
                if (!seen.add(id)) continue

                val title = item.at(
                    "flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs", 0, "text"
                ) as? String ?: continue

                val runs = item.at(
                    "flexColumns", 1, "musicResponsiveListItemFlexColumnRenderer", "text", "runs"
                ) as? JSONArray
                val artist = runs?.let { r ->
                    (0 until r.length())
                        .mapNotNull { r.optJSONObject(it)?.optString("text") }
                        .firstOrNull { it.isNotBlank() && it.trim() != "•" }
                }.orEmpty()

                val thumbs = item.at(
                    "thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"
                ) as? JSONArray
                val thumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")?.takeIf { it.isNotEmpty() }

                val durationText = item.at(
                    "fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer", "text", "runs", 0, "text"
                ) as? String

                out += Track(id, title, artist, thumb, parseDuration(durationText))
            }
        }
        return out
    }

    internal fun parseDuration(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val parts = text.trim().split(":").mapNotNull { it.toLongOrNull() }
        return parts.fold(0L) { acc, p -> acc * 60 + p }
    }

    private fun continuationToken(root: JSONObject): String? {
        val next = mutableListOf<JSONObject>()
        collect(root, "nextContinuationData", next)
        next.firstOrNull()?.optString("continuation")?.takeIf { it.isNotEmpty() }?.let { return it }

        val cmd = mutableListOf<JSONObject>()
        collect(root, "continuationCommand", cmd)
        return cmd.firstOrNull()?.optString("token")?.takeIf { it.isNotEmpty() }
    }

    /** Raccoglie, in tutto l'albero JSON, gli oggetti memorizzati sotto la chiave data. */
    internal fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (k == key && v is JSONObject) out.add(v) else collect(v, key, out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
        }
    }

    internal fun firstString(node: Any?, key: String): String? {
        when (node) {
            is JSONObject -> {
                val direct = node.opt(key)
                if (direct is String && direct.isNotEmpty()) return direct
                val keys = node.keys()
                while (keys.hasNext()) {
                    firstString(node.opt(keys.next()), key)?.let { return it }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                firstString(node.opt(i), key)?.let { return it }
            }
        }
        return null
    }

    internal fun Any?.at(vararg path: Any): Any? {
        var cur: Any? = this
        for (p in path) {
            cur = when (p) {
                is String -> (cur as? JSONObject)?.opt(p)
                is Int -> (cur as? JSONArray)?.opt(p)
                else -> null
            }
            if (cur == null) return null
        }
        return cur
    }
}
