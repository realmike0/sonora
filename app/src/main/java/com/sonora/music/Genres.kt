package com.sonora.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory

/** Generi musicali con le relative playlist (cercate su YouTube Music). */
object GenreRepository {
    /** nome mostrato → colore di sfondo della tessera */
    val genres = listOf(
        "Pop" to 0xFFE91E63, "Rock" to 0xFFD84315, "Hip-hop" to 0xFF6A1B9A, "Rap" to 0xFF4527A0,
        "Dance" to 0xFF00897B, "Elettronica" to 0xFF1565C0, "R&B" to 0xFFAD1457, "Latino" to 0xFFEF6C00,
        "Reggaeton" to 0xFFF9A825, "Indie" to 0xFF2E7D32, "Metal" to 0xFF37474F, "Jazz" to 0xFF5D4037,
        "Classica" to 0xFF6D4C41, "Country" to 0xFF9E9D24, "Lo-fi" to 0xFF455A64, "K-pop" to 0xFFC2185B
    )

    private val cache = HashMap<String, List<AlbumItem>>()

    suspend fun playlists(genre: String): List<AlbumItem> {
        synchronized(cache) { cache[genre] }?.let { return it }
        val list = withContext(Dispatchers.IO) {
            val ex = ServiceList.YouTube.getSearchExtractor(
                "$genre hits", listOf(YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS), ""
            )
            ex.fetchPage()
            ex.initialPage.items.filterIsInstance<PlaylistInfoItem>().map {
                AlbumItem(
                    title = it.name,
                    subtitle = (it.uploaderName ?: "").removeSuffix(" - Topic"),
                    thumb = it.thumbnails.maxByOrNull { t -> t.height }?.url ?: it.thumbnails.lastOrNull()?.url,
                    url = it.url
                )
            }.take(24)
        }
        if (list.isNotEmpty()) synchronized(cache) { cache[genre] = list }
        return list
    }
}
