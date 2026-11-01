package com.sonora.music

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class PlayEvent(val ts: Long, val id: String, val title: String, val artist: String, val thumb: String?, val ms: Long) {
    fun toJson(): JSONObject = JSONObject().put("ts", ts).put("id", id).put("title", title)
        .put("artist", artist).put("thumb", thumb ?: "").put("ms", ms)

    companion object {
        fun from(o: JSONObject) = PlayEvent(
            o.getLong("ts"), o.getString("id"), o.optString("title"), o.optString("artist"),
            o.optString("thumb").takeIf { it.isNotEmpty() }, o.optLong("ms")
        )
    }
}

/**
 * Registro degli ascolti, sempre salvato sul telefono (serve a Wrapped e ai widget).
 * Va sul server solo se l'utente ha accettato il tracciamento e verificato la propria email.
 */
object PlayLog {
    private lateinit var appContext: Context
    private lateinit var file: File
    private val io = Executors.newSingleThreadExecutor()
    private val events = ArrayList<PlayEvent>()

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        file = File(appContext.filesDir, "plays.jsonl")
        try {
            if (file.exists()) file.forEachLine { l ->
                runCatching { events.add(PlayEvent.from(JSONObject(l))) }
            }
        } catch (e: Exception) {
            // registro illeggibile: si riparte
        }
    }

    @Synchronized
    fun all(): List<PlayEvent> = events.toList()

    @Synchronized
    fun add(e: PlayEvent) {
        events.add(e)
        io.execute { runCatching { file.appendText(e.toJson().toString() + "\n") } }
        Widgets.updateAll(appContext)
        Tracking.onNewEvent(appContext)
    }

    @Synchronized
    fun clear() {
        events.clear()
        io.execute { runCatching { file.delete() } }
        Widgets.updateAll(appContext)
    }
}
