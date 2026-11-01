package com.sonora.music

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

data class RankedTrack(val id: String, val title: String, val artist: String, val thumb: String?, val plays: Int)
data class RankedArtist(val name: String, val minutes: Long, val plays: Int)

data class WrappedStats(
    val year: Int,
    val minutes: Long,
    val plays: Int,
    val distinctTracks: Int,
    val topTracks: List<RankedTrack>,
    val topArtists: List<RankedArtist>,
    val topMonth: Int,   // 1..12, 0 = nessuno
    val topHour: Int     // 0..23, -1 = nessuno
) {
    companion object {
        /** Calcolo locale, dal registro degli ascolti sul telefono. */
        fun fromLog(year: Int, events: List<PlayEvent>): WrappedStats {
            val zone = ZoneId.systemDefault()
            val ev = events.filter { Instant.ofEpochMilli(it.ts).atZone(zone).year == year }
            val tracks = ev.groupBy { it.id }.map { (id, l) ->
                RankedTrack(id, l.last().title, l.last().artist, l.last().thumb, l.size)
            }.sortedByDescending { it.plays }
            val artists = ev.groupBy { ArtistRepository.splitArtists(it.artist).firstOrNull() ?: it.artist }
                .filterKeys { it.isNotBlank() }
                .map { (n, l) -> RankedArtist(n, l.sumOf { it.ms } / 60000, l.size) }
                .sortedByDescending { it.minutes }
            val month = ev.groupBy { Instant.ofEpochMilli(it.ts).atZone(zone).monthValue }
                .maxByOrNull { it.value.size }?.key ?: 0
            val hour = ev.groupBy { Instant.ofEpochMilli(it.ts).atZone(zone).hour }
                .maxByOrNull { it.value.size }?.key ?: -1
            return WrappedStats(year, ev.sumOf { it.ms } / 60000, ev.size, tracks.size, tracks.take(5), artists.take(5), month, hour)
        }

        fun fromJson(j: JSONObject): WrappedStats {
            val t = j.optJSONArray("topTracks")
            val a = j.optJSONArray("topArtists")
            return WrappedStats(
                j.optInt("year"), j.optLong("minutes"), j.optInt("plays"), j.optInt("distinctTracks"),
                (0 until (t?.length() ?: 0)).map {
                    val o = t!!.getJSONObject(it)
                    RankedTrack(o.optString("id"), o.optString("title"), o.optString("artist"),
                        o.optString("thumb").takeIf { s -> s.isNotEmpty() }, o.optInt("plays"))
                },
                (0 until (a?.length() ?: 0)).map {
                    val o = a!!.getJSONObject(it)
                    RankedArtist(o.optString("name"), o.optLong("minutes"), o.optInt("plays"))
                },
                j.optInt("topMonth"), j.optInt("topHour", -1)
            )
        }
    }
}
