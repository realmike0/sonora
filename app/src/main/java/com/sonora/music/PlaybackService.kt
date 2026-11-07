package com.sonora.music

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

    // Dissolvenza vera: un secondo player "ombra" suona la coda del brano che esce mentre il principale parte con il successivo.
    private var ghost: ExoPlayer? = null
    private var ghostStart = 0L
    private var ghostLen = 0L
    private var ghostForId: String? = null

    private fun playUri(id: String): Uri = if (VideoMode.on) trackVideoUri(id) else trackUri(id)

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
        // Il video resta spento finché non lo chiede l'utente (interruttore audio/video).
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()

        // Equalizzatore sulla sessione audio del player.
        Eq.attach(this, player.audioSessionId)
        AudioHaptics.attach(this, player.audioSessionId)
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                Eq.attach(this@PlaybackService, audioSessionId)
                AudioHaptics.attach(this@PlaybackService, audioSessionId)
            }
        })

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retriedId = null
                if (mediaItem?.mediaId != ghostForId) ghostForId = null
                // Salva il brano intero in cache mentre lo ascolti (non per il video).
                if (!VideoMode.on) mediaItem?.let { AudioStore.fillCache(it.mediaId) }
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

        // Mixaggio fluido: sovrapposizione vera (player ombra) oppure dissolvenza semplice del volume.
        val prefs = getSharedPreferences("sonora", MODE_PRIVATE)
        val tick = object : Runnable {
            override fun run() {
                try {
                    val outMs = prefs.getInt("fade_out_ms", 4000).toLong()
                    val inMs = prefs.getInt("fade_in_ms", 2500).toLong()
                    val curve = prefs.getInt("fade_curve", 0)
                    val overlap = prefs.getBoolean("mix_overlap", true) && !Perf.lite.value
                    val now = SystemClock.uptimeMillis()

                    // 1) Il player ombra sfuma in uscita il brano precedente.
                    ghost?.let { g ->
                        if (!player.playWhenReady) {
                            g.playWhenReady = false
                            ghostStart += 100 // in pausa il tempo non scorre
                        } else {
                            g.playWhenReady = true
                            val el = now - ghostStart
                            if (el >= ghostLen + 200) {
                                g.release()
                                ghost = null
                            } else {
                                g.volume = shape(1f - el.toFloat() / ghostLen, curve)
                            }
                        }
                    }

                    if (prefs.getBoolean("mix_fade", true) && player.playWhenReady) {
                        val dur = player.duration
                        val pos = player.currentPosition
                        var v = 1f
                        if (overlap) {
                            // 2) Quando mancano outMs alla fine, il successivo parte subito e sale mentre l'ombra scende.
                            if (outMs > 0 && dur != C.TIME_UNSET && dur > 30_000L && player.hasNextMediaItem() &&
                                player.repeatMode != Player.REPEAT_MODE_ONE
                            ) {
                                val rem = dur - pos
                                val id = player.currentMediaItem?.mediaId
                                if (rem in 700L..outMs && id != null && ghostForId != id) startGhost(player, rem)
                            }
                            if (ghost != null && ghostLen > 0) {
                                v = shape((now - ghostStart).toFloat() / ghostLen, curve)
                            }
                        } else if (dur != C.TIME_UNSET && dur > 30_000L) {
                            if (outMs > 0 && player.hasNextMediaItem()) {
                                val rem = dur - pos
                                if (rem < outMs) v = minOf(v, shape(rem.toFloat() / outMs, curve))
                            }
                            if (inMs > 0 && pos < inMs) v = minOf(v, shape(pos.toFloat() / inMs, curve))
                        }
                        player.volume = v
                    } else if (player.volume != 1f) {
                        player.volume = 1f
                    }
                } catch (e: Exception) {
                    // best-effort
                }
                handler.postDelayed(this, 100)
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
                        it.buildUpon().setUri(playUri(it.mediaId)).build()
                    }
                    return Futures.immediateFuture(items)
                }
            })
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Headsets.ACTION_START_FAVORITES) startFavorites()
        return super.onStartCommand(intent, flags, startId)
    }

    /** "Inizia ad ascoltare" dalla notifica: parte un brano a caso tra i preferiti (poi continuano gli altri, in ordine casuale). */
    private fun startFavorites() {
        androidx.core.app.NotificationManagerCompat.from(this).cancel(Headsets.ID_HEADSET)
        val player = session?.player ?: return
        val tracks = Store(this).liked.toList().shuffled()
        if (tracks.isEmpty()) return
        player.setMediaItems(
            tracks.map { it.toMediaItem().buildUpon().setUri(playUri(it.id)).build() },
            0,
            0L
        )
        player.prepare()
        player.play()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Se l'utente chiude l'app mentre non sta riproducendo, fermiamo il servizio.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    /** Fa partire il player ombra con il brano che sta finendo e porta subito il principale al successivo. */
    private fun startGhost(player: ExoPlayer, remMs: Long) {
        val item = player.currentMediaItem ?: return
        val g = ghost ?: ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(AudioStore.playbackFactory()))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ false
            )
            .build().also { ghost = it }
        g.setMediaItem(item, player.currentPosition)
        g.volume = 1f
        g.prepare()
        g.playWhenReady = true
        ghostForId = item.mediaId
        ghostStart = SystemClock.uptimeMillis()
        ghostLen = remMs
        player.volume = 0f
        player.seekToNextMediaItem()
    }

    /** Curva della dissolvenza: 0 = morbida, 1 = lineare, 2 = potenza costante (più "radiofonica"). */
    private fun shape(x: Float, curve: Int): Float {
        val t = x.coerceIn(0f, 1f)
        return when (curve) {
            1 -> t
            2 -> kotlin.math.sin(t * Math.PI.toFloat() / 2f)
            else -> t * t * (3f - 2f * t)
        }
    }

    override fun onDestroy() {
        fadeTick?.let { handler.removeCallbacks(it) }
        ghost?.release()
        ghost = null
        Eq.release()
        AudioHaptics.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
