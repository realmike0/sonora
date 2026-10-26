package com.sonora.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

const val LIKED_ID = "__liked"

data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track>,
    val fromGoogle: Boolean = false
)

/**
 * Account locale: profilo, Mi piace e playlist vivono sul telefono
 * (file JSON privato dell'app + SharedPreferences).
 */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("sonora", Context.MODE_PRIVATE)
    private val file = File(context.filesDir, "library.json")
    private val io = Executors.newSingleThreadExecutor()

    var userName by mutableStateOf(prefs.getString("name", "") ?: "")
        private set
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false))
        private set
    var googleCookie by mutableStateOf(prefs.getString("cookie", null))
        private set
    var lyricsSource by mutableStateOf(prefs.getString("lyrics_source", "LRCLIB") ?: "LRCLIB")
        private set

    val liked = mutableStateListOf<Track>()
    val playlists = mutableStateListOf<Playlist>()

    init {
        load()
    }

    // ───────── Profilo e impostazioni ─────────

    fun completeOnboarding(name: String) {
        userName = name.trim()
        onboarded = true
        prefs.edit().putString("name", userName).putBoolean("onboarded", true).apply()
    }

    fun setGoogleCookie(cookie: String?) {
        googleCookie = cookie
        val e = prefs.edit()
        if (cookie == null) e.remove("cookie") else e.putString("cookie", cookie)
        e.apply()
    }

    fun setLyricsSource(source: String) {
        lyricsSource = source
        prefs.edit().putString("lyrics_source", source).apply()
    }

    // ───────── Mi piace ─────────

    fun isLiked(id: String): Boolean = liked.any { it.id == id }

    fun toggleLike(track: Track) {
        val i = liked.indexOfFirst { it.id == track.id }
        if (i >= 0) liked.removeAt(i) else liked.add(0, track)
        save()
    }

    // ───────── Playlist ─────────

    fun createPlaylist(name: String): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().ifEmpty { "Nuova playlist" }, emptyList())
        playlists.add(p)
        save()
        return p
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        val i = playlists.indexOfFirst { it.id == playlistId }
        if (i < 0) return
        val p = playlists[i]
        if (p.tracks.any { it.id == track.id }) return
        playlists[i] = p.copy(tracks = p.tracks + track)
        save()
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        val i = playlists.indexOfFirst { it.id == playlistId }
        if (i < 0) return
        val p = playlists[i]
        playlists[i] = p.copy(tracks = p.tracks.filter { it.id != trackId })
        save()
    }

    fun deletePlaylist(playlistId: String) {
        playlists.removeAll { it.id == playlistId }
        save()
    }

    /** Sostituisce solo le playlist importate da Google, lasciando intatte quelle create dall'utente. */
    fun replaceGooglePlaylists(imported: List<Playlist>) {
        playlists.removeAll { it.fromGoogle }
        playlists.addAll(imported)
        save()
    }

    // ───────── Persistenza ─────────

    private fun Track.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        if (thumb != null) put("thumb", thumb)
        put("dur", durationSec)
    }

    private fun JSONObject.toTrack(): Track {
        val t = optString("thumb", "")
        return Track(
            id = getString("id"),
            title = optString("title", ""),
            artist = optString("artist", ""),
            thumb = if (t.isEmpty()) null else t,
            durationSec = optLong("dur", 0L)
        )
    }

    private fun JSONArray.toTracks(): List<Track> =
        (0 until length()).mapNotNull { i -> optJSONObject(i)?.let { runCatching { it.toTrack() }.getOrNull() } }

    private fun load() {
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            root.optJSONArray("liked")?.toTracks()?.let { liked.addAll(it) }
            val arr = root.optJSONArray("playlists") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                playlists.add(
                    Playlist(
                        id = o.getString("id"),
                        name = o.optString("name", "Playlist"),
                        tracks = o.optJSONArray("tracks")?.toTracks() ?: emptyList(),
                        fromGoogle = o.optBoolean("google", false)
                    )
                )
            }
        } catch (e: Exception) {
            // File danneggiato: si riparte da una libreria vuota.
        }
    }

    private fun save() {
        val likedSnap = liked.toList()
        val playlistsSnap = playlists.toList()
        io.execute {
            try {
                val root = JSONObject()
                root.put("liked", JSONArray(likedSnap.map { it.toJson() }))
                root.put(
                    "playlists",
                    JSONArray(playlistsSnap.map { p ->
                        JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("google", p.fromGoogle)
                            put("tracks", JSONArray(p.tracks.map { it.toJson() }))
                        }
                    })
                )
                val tmp = File(file.path + ".tmp")
                tmp.writeText(root.toString())
                tmp.renameTo(file)
            } catch (e: Exception) {
                // Salvataggio best-effort.
            }
        }
    }
}
