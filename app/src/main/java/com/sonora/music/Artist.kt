package com.sonora.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class AlbumItem(val title: String, val subtitle: String, val thumb: String?, val url: String)

data class ArtistPage(
    val name: String,
    val image: String?,
    val songs: List<Track>,
    val albums: List<AlbumItem>
)

/** Pagina dell'artista: brani popolari e album, cercati su YouTube Music. */
object ArtistRepository {

    private val cache = HashMap<String, ArtistPage>()

    /** "A, B & C" -> ["A", "B", "C"] */
    fun splitArtists(raw: String): List<String> =
        raw.split(",", "&", " feat. ", " ft. ", " x ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    suspend fun load(name: String): ArtistPage {
        synchronized(cache) { cache[name] }?.let { return it }
        val page = withContext(Dispatchers.IO) {
            coroutineScope {
                val songs = async { runCatching { MusicRepository.search(name) }.getOrDefault(emptyList()) }
                val albums = async { runCatching { searchAlbums(name) }.getOrDefault(emptyList()) }
                val image = async { runCatching { artistImage(name) }.getOrNull() }
                val allSongs = songs.await()
                val mine = allSongs.filter { it.artist.contains(name, ignoreCase = true) }
                ArtistPage(
                    name = name,
                    image = image.await() ?: (mine.firstOrNull() ?: allSongs.firstOrNull())?.thumb,
                    songs = mine.ifEmpty { allSongs },
                    albums = albums.await()
                )
            }
        }
        if (page.songs.isEmpty() && page.albums.isEmpty()) throw java.io.IOException("Nessun risultato per $name")
        synchronized(cache) { cache[name] = page }
        return page
    }

    private fun artistImage(name: String): String? {
        val ex = ServiceList.YouTube.getSearchExtractor(
            name, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS), ""
        )
        ex.fetchPage()
        val item = ex.initialPage.items.filterIsInstance<ChannelInfoItem>().firstOrNull() ?: return null
        return item.thumbnails.maxByOrNull { it.height }?.url ?: item.thumbnails.lastOrNull()?.url
    }

    private fun searchAlbums(name: String): List<AlbumItem> {
        val ex = ServiceList.YouTube.getSearchExtractor(
            name, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS), ""
        )
        ex.fetchPage()
        val all = ex.initialPage.items.filterIsInstance<PlaylistInfoItem>().map {
            AlbumItem(
                title = it.name,
                subtitle = (it.uploaderName ?: "").removeSuffix(" - Topic"),
                thumb = it.thumbnails.maxByOrNull { t -> t.height }?.url ?: it.thumbnails.lastOrNull()?.url,
                url = it.url
            )
        }
        val mine = all.filter { it.subtitle.contains(name, ignoreCase = true) }
        return mine.ifEmpty { all }.take(20)
    }

    /** Brani di un album. */
    suspend fun albumTracks(album: AlbumItem): List<Track> = withContext(Dispatchers.IO) {
        val ex = ServiceList.YouTube.getPlaylistExtractor(album.url)
        ex.fetchPage()
        with(MusicRepository) {
            ex.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toTrack() }
                .map { if (it.thumb == null) it.copy(thumb = album.thumb) else it }
        }
    }
}
