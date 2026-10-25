package com.sonora.music

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NowPlaying(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String?
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    // --- Ricerca ---
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Track>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // --- Riproduzione ---
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set
    var wantsPlay by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    private var controller: MediaController? = null
    private var searchJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            sync(c)
        }, ContextCompat.getMainExecutor(context))

        // Aggiorna la posizione mentre si ascolta.
        viewModelScope.launch {
            while (true) {
                delay(400)
                controller?.let { if (it.isPlaying) positionMs = it.currentPosition }
            }
        }
    }

    private fun sync(p: Player) {
        wantsPlay = p.playWhenReady && p.playbackState != Player.STATE_ENDED
        isBuffering = p.playbackState == Player.STATE_BUFFERING
        durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        positionMs = p.currentPosition
        nowPlaying = p.currentMediaItem?.let {
            NowPlaying(
                id = it.mediaId,
                title = it.mediaMetadata.title?.toString().orEmpty(),
                artist = it.mediaMetadata.artist?.toString().orEmpty(),
                artwork = it.mediaMetadata.artworkUri?.toString()
            )
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            loading = true
            error = null
            try {
                val found = MusicRepository.search(q)
                results = found
                if (found.isEmpty()) error = "Nessun risultato per \"$q\""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Ricerca non riuscita: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                if (isActive) loading = false
            }
        }
    }

    fun playFrom(index: Int) {
        val c = controller ?: return
        c.setMediaItems(results.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekToFraction(fraction: Float) {
        val c = controller ?: return
        if (durationMs > 0) {
            val target = (fraction * durationMs).toLong()
            c.seekTo(target)
            positionMs = target
        }
    }

    override fun onCleared() {
        controller?.run {
            removeListener(listener)
            release()
        }
        controller = null
        super.onCleared()
    }
}
