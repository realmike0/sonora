package com.sonora.music

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException

private const val SCHEME = "ytm"

/** URI segnaposto: l'URL reale viene risolto al momento della riproduzione. */
fun trackUri(videoId: String): Uri =
    Uri.Builder().scheme(SCHEME).authority("track").appendPath(videoId).build()

/**
 * Servizio in primo piano che tiene viva la riproduzione quando l'app è in background,
 * con notifica multimediale e controlli nella schermata di blocco.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var retriedId: String? = null

    override fun onCreate() {
        super.onCreate()

        val http = OkHttpDataSource.Factory(Http.client).setUserAgent(USER_AGENT)
        val resolving = ResolvingDataSource.Factory(http, ResolvingDataSource.Resolver { spec: DataSpec ->
            if (spec.uri.scheme == SCHEME) {
                val id = spec.uri.lastPathSegment ?: throw IOException("ID brano mancante")
                val url = try {
                    MusicRepository.resolveAudioUrl(id)
                } catch (e: IOException) {
                    throw e
                } catch (e: Exception) {
                    throw IOException("Impossibile ottenere lo stream: ${e.message}", e)
                }
                spec.withUri(Uri.parse(url))
            } else {
                spec
            }
        })

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retriedId = null
            }

            override fun onPlayerError(error: PlaybackException) {
                val id = player.currentMediaItem?.mediaId ?: return
                if (retriedId != id) {
                    // URL scaduto o rifiutato: lo rigeneriamo e riproviamo una volta.
                    retriedId = id
                    MusicRepository.invalidate(id)
                    player.prepare()
                } else if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                }
            }
        })

        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // I controller inviano solo mediaId + metadati: qui aggiungiamo l'URI.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>
                ): ListenableFuture<List<MediaItem>> {
                    val items = mediaItems.map {
                        it.buildUpon().setUri(trackUri(it.mediaId)).build()
                    }
                    return Futures.immediateFuture(items)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Se l'utente chiude l'app mentre non sta riproducendo, fermiamo il servizio.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
