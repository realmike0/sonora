package com.sonora.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Chiamate alle rotte /v1/admin del server. La chiave resta solo su questo telefono. */
object AdminApi {
    const val BASE = "https://sonora-wrapped.sonorawrapped.workers.dev"

    suspend fun call(key: String, method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val con = URL(BASE + path).openConnection() as HttpURLConnection
            try {
                con.requestMethod = method
                con.connectTimeout = 15000
                con.readTimeout = 20000
                con.setRequestProperty("x-admin-key", key)
                con.setRequestProperty("User-Agent", "Sonora-Admin")
                if (body != null) {
                    con.doOutput = true
                    con.setRequestProperty("content-type", "application/json")
                    con.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = con.responseCode
                val text = (if (code < 400) con.inputStream else con.errorStream)?.bufferedReader()?.readText().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (code == 404) throw IOException("Chiave non valida")
                if (code >= 400) throw IOException(json.optString("error").ifEmpty { "Errore ($code)" })
                json
            } finally {
                con.disconnect()
            }
        }
}

data class UserRow(val id: String, val created: Long, val verified: Boolean, val comments: Int, val deds: Int, val name: String, val banned: Boolean, val until: Long)
data class CommentRow(val id: String, val user: String, val name: String, val text: String, val ts: Long, val reports: Int)
data class DedRow(val id: String, val user: String, val from: String, val to: String, val msg: String, val title: String, val ts: Long, val hidden: Boolean)

private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

suspend fun AdminApi.users(key: String, q: String): List<UserRow> =
    call(key, "GET", "/v1/admin/users?limit=100&q=$q").optJSONArray("items").objs().map {
        UserRow(it.optString("id"), it.optLong("created"), it.optBoolean("verified"), it.optInt("comments"), it.optInt("deds"),
            it.optString("name"), it.optBoolean("banned"), it.optLong("until"))
    }

suspend fun AdminApi.comments(key: String): List<CommentRow> =
    call(key, "GET", "/v1/admin/comments?limit=100").optJSONArray("items").objs().map {
        CommentRow(it.optString("id"), it.optString("user"), it.optString("name"), it.optString("text"), it.optLong("ts"), it.optInt("reports"))
    }

suspend fun AdminApi.deds(key: String): List<DedRow> =
    call(key, "GET", "/v1/admin/ded?limit=100").optJSONArray("items").objs().map {
        DedRow(it.optString("id"), it.optString("user"), it.optString("from"), it.optString("to"), it.optString("msg"),
            it.optString("title"), it.optLong("ts"), it.optInt("hidden") == 1)
    }
