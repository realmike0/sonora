@file:OptIn(androidx.media3.common.util.UnstableApi::class)

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
const val CACHE_ID = "__cache"
const val DOWNLOADS_ID = "__downloads"

data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track>,
    val fromGoogle: Boolean = false
)

fun Track.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("artist", artist)
    if (thumb != null) put("thumb", thumb)
    put("dur", durationSec)
}

fun JSONObject.toTrack(): Track {
    val t = optString("thumb", "")
    return Track(
        id = getString("id"),
        title = optString("title", ""),
        artist = optString("artist", ""),
        thumb = if (t.isEmpty()) null else t,
        durationSec = optLong("dur", 0L)
    )
}

fun JSONArray.toTracks(): List<Track> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let { runCatching { it.toTrack() }.getOrNull() } }

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

    var hasSearched by mutableStateOf(prefs.getBoolean("searched", false))
        private set
    var accountName by mutableStateOf(prefs.getString("acc_name", null))
        private set
    var accountPhoto by mutableStateOf(prefs.getString("acc_photo", null))
        private set

    val liked = mutableStateListOf<Track>()

    /** Brani ascoltati (più recenti per primi): servono anche come anagrafica di cache e download. */
    val played = mutableStateListOf<Track>()

    /** Brani avviati dai risultati di ricerca. */
    val searchPlays = mutableStateListOf<Track>()
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

    fun updateGoogleCookie(cookie: String?) {
        googleCookie = cookie
        val e = prefs.edit()
        if (cookie == null) e.remove("cookie") else e.putString("cookie", cookie)
        e.apply()
    }

    fun updateLyricsSource(source: String) {
        lyricsSource = source
        prefs.edit().putString("lyrics_source", source).apply()
    }

    fun markSearched() {
        if (hasSearched) return
        hasSearched = true
        prefs.edit().putBoolean("searched", true).apply()
    }

    fun updateAccount(name: String?, photo: String?) {
        accountName = name
        accountPhoto = photo
        val e = prefs.edit()
        if (name == null) e.remove("acc_name") else e.putString("acc_name", name)
        if (photo == null) e.remove("acc_photo") else e.putString("acc_photo", photo)
        e.apply()
    }

    // ───────── Cronologia ─────────

    fun recordPlayed(track: Track) {
        played.removeAll { it.id == track.id }
        played.add(0, track)
        // Limita la cronologia, ma non perde mai i dati dei brani in cache o scaricati.
        var i = played.lastIndex
        while (played.size > 300 && i >= 0) {
            val id = played[i].id
            if (id !in AudioStore.cachedIds && id !in AudioStore.downloadedIds) played.removeAt(i)
            i--
        }
        save()
    }

    /** Memorizza i dati di un brano (per cache/download) senza metterlo in cima alla cronologia. */
    fun remember(track: Track) {
        if (played.none { it.id == track.id }) {
            played.add(track)
            save()
        }
    }

    fun recordSearchPlay(track: Track) {
        searchPlays.removeAll { it.id == track.id }
        searchPlays.add(0, track)
        while (searchPlays.size > 50) searchPlays.removeAt(searchPlays.lastIndex)
        save()
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

    private fun load() {
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            root.optJSONArray("liked")?.toTracks()?.let { liked.addAll(it) }
            root.optJSONArray("played")?.toTracks()?.let { played.addAll(it) }
            root.optJSONArray("searchPlays")?.toTracks()?.let { searchPlays.addAll(it) }
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
        val playedSnap = played.toList()
        val searchSnap = searchPlays.toList()
        io.execute {
            try {
                val root = JSONObject()
                root.put("liked", JSONArray(likedSnap.map { it.toJson() }))
                root.put("played", JSONArray(playedSnap.map { it.toJson() }))
                root.put("searchPlays", JSONArray(searchSnap.map { it.toJson() }))
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
