package com.sonora.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.comments.CommentsInfo
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Comment(val author: String, val text: String, val likes: Int, val date: String, val avatar: String?, val pinned: Boolean)

/**
 * Commenti dei video di YouTube (quelli dei brani). Si leggono sempre; per scriverne serve aver
 * collegato l'account Google. Scrivere usa l'API web non ufficiale di YouTube: può smettere di funzionare.
 */
object Comments {
    private const val ORIGIN = "https://www.youtube.com"

    suspend fun load(videoId: String): List<Comment> = withContext(Dispatchers.IO) {
        val info = CommentsInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        info.relatedItems.map { c ->
            Comment(
                author = c.uploaderName.orEmpty().removePrefix("@"),
                text = c.commentText?.content.orEmpty(),
                likes = c.likeCount,
                date = c.textualUploadDate.orEmpty(),
                avatar = c.uploaderAvatars.lastOrNull()?.url,
                pinned = c.isPinned
            )
        }.filter { it.text.isNotBlank() }
    }

    private fun context(): JSONObject {
        val version = "2." + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".00.00"
        return JSONObject().put(
            "client",
            JSONObject().put("clientName", "WEB").put("clientVersion", version).put("hl", "it").put("gl", "IT")
        )
    }

    private fun post(cookie: String, endpoint: String, body: JSONObject): JSONObject {
        val sapisid = GoogleSync.cookieValue(cookie, "SAPISID")
            ?: GoogleSync.cookieValue(cookie, "__Secure-3PAPISID")
            ?: throw IOException("Sessione Google non valida")
        val ts = System.currentTimeMillis() / 1000
        val auth = "SAPISIDHASH ${ts}_${GoogleSync.sha1("$ts $sapisid $ORIGIN")}"
        val full = if (cookie.contains("SOCS=")) cookie else "$cookie; SOCS=CAI"
        val req = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("X-Goog-AuthUser", "0")
            .header("Authorization", auth)
            .header("Cookie", full)
            .build()
        Http.client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401 || r.code == 403) throw IOException("Accesso scaduto: ricollega l'account Google dalle impostazioni")
            if (!r.isSuccessful) throw IOException("YouTube ha rifiutato il commento (${r.code})")
            return JSONObject(text)
        }
    }

    /** Pubblica un commento sul video. */
    suspend fun post(cookie: String, videoId: String, text: String) = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isEmpty()) throw IOException("Scrivi qualcosa")
        // 1) pagina del video → token della sezione commenti
        val first = post(cookie, "next", JSONObject().put("context", context()).put("videoId", videoId))
        val sections = mutableListOf<JSONObject>()
        GoogleSync.collect(first, "continuationItemRenderer", sections)
        val token = sections.firstOrNull { it.toString().contains("comments-section") }
            ?.let { GoogleSync.firstString(it, "token") }
            ?: sections.firstNotNullOfOrNull { GoogleSync.firstString(it, "token") }
            ?: throw IOException("Commenti non disponibili per questo video")
        // 2) sezione commenti → parametri per crearne uno
        val comments = post(cookie, "next", JSONObject().put("context", context()).put("continuation", token))
        val params = GoogleSync.firstString(comments, "createCommentParams")
            ?: throw IOException("Questo video non permette di commentare")
        // 3) pubblica
        post(cookie, "comment/create_comment", JSONObject().put("context", context()).put("commentText", clean).put("createCommentParams", params))
        Unit
    }
}
