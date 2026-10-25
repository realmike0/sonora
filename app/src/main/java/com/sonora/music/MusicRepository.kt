package com.sonora.music

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val thumb: String?,
    val durationSec: Long
)

fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(thumb?.let(Uri::parse))
                .build()
        )
        .build()

object MusicRepository {

    private const val STREAM_TTL_MS = 3 * 60 * 60 * 1000L // gli URL di YouTube scadono dopo ~6h

    private val streamCache = ConcurrentHashMap<String, Pair<String, Long>>()

    /** Cerca brani su YouTube Music. */
    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val extractor = ServiceList.YouTube.getSearchExtractor(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toTrack() }
            .distinctBy { it.id }
    }

    /**
     * Restituisce l'URL diretto del miglior stream audio. Chiamata bloccante:
     * va usata solo da thread di caricamento (es. il DataSource di ExoPlayer).
     */
    fun resolveAudioUrl(videoId: String): String {
        val now = System.currentTimeMillis()
        streamCache[videoId]?.let { (url, time) ->
            if (now - time < STREAM_TTL_MS) return url
        }

        val info = StreamInfo.getInfo(
            ServiceList.YouTube,
            "https://www.youtube.com/watch?v=$videoId"
        )
        val candidates = info.audioStreams.filter { it.isUrl }
        val best = candidates
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.averageBitrate }
            ?: candidates.firstOrNull()
            ?: throw IOException("Nessuno stream audio disponibile")

        streamCache[videoId] = best.content to now
        return best.content
    }

    fun invalidate(videoId: String) {
        streamCache.remove(videoId)
    }

    private fun StreamInfoItem.toTrack(): Track? {
        val id = Uri.parse(url).getQueryParameter("v") ?: return null
        return Track(
            id = id,
            title = name,
            artist = (uploaderName ?: "").removeSuffix(" - Topic"),
            thumb = thumbnails.lastOrNull()?.url,
            durationSec = duration
        )
    }
}
