package com.sonora.music

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

const val TRACK_SCHEME = "ytm"

/** URI segnaposto: l'URL reale viene risolto al momento della riproduzione. */
fun trackUri(videoId: String): Uri =
    Uri.Builder().scheme(TRACK_SCHEME).authority("track").appendPath(videoId).build()

/**
 * Servizio in primo piano che tiene viva la riproduzione quando l'app è in background,
 * con notifica multimediale e controlli nella schermata di blocco.
 */
private const val FADE_OUT_MS = 4000L
private const val FADE_IN_MS = 2500L

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var retriedId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var fadeTick: Runnable? = null

    override fun onCreate() {
        super.onCreate()

        AudioStore.init(this)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(AudioStore.playbackFactory()))
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
                // Salva il brano intero in cache mentre lo ascolti.
                mediaItem?.let { AudioStore.fillCache(it.mediaId) }
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

        // Mixaggio fluido: il brano che finisce sfuma in uscita e il successivo sale in entrata.
        val prefs = getSharedPreferences("sonora", MODE_PRIVATE)
        val tick = object : Runnable {
            override fun run() {
                try {
                    if (prefs.getBoolean("mix_fade", true) && player.playWhenReady) {
                        val dur = player.duration
                        val pos = player.currentPosition
                        var v = 1f
                        if (dur != C.TIME_UNSET && dur > 30_000L) {
                            if (player.hasNextMediaItem()) {
                                val rem = dur - pos
                                if (rem < FADE_OUT_MS) v = minOf(v, smooth(rem.toFloat() / FADE_OUT_MS))
                            }
                            if (pos < FADE_IN_MS) v = minOf(v, smooth(pos.toFloat() / FADE_IN_MS))
                        }
                        player.volume = v
                    } else if (player.volume != 1f) {
                        player.volume = 1f
                    }
                } catch (e: Exception) {
                    // best-effort
                }
                handler.postDelayed(this, 120)
            }
        }
        fadeTick = tick
        handler.post(tick)

        // Tocco sulla copertina/notifica: apre l'app sulla schermata principale (mai sul player).
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
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

    private fun smooth(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    override fun onDestroy() {
        fadeTick?.let { handler.removeCallbacks(it) }
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
