@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sonora.music

import android.app.Application
import android.content.ComponentName
import android.webkit.CookieManager
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
    val artwork: String?,
    val durationSec: Long
)

fun NowPlaying.toTrack() = Track(id, title, artist, artwork, durationSec)

enum class SyncState { Idle, Running, Done, Failed }

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    /** Profilo locale, Mi piace, playlist e impostazioni. */
    val store = Store(app)

    // --- Navigazione ---
    var playerOpen by mutableStateOf(false)
    var loginOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    /** Artista aperto nella sua pagina (null = nessuna). */
    var artistOpen by mutableStateOf<String?>(null)

    /** Brano di cui è aperto il menu (pressione prolungata). */
    var menuTrack by mutableStateOf<Track?>(null)

    // --- Home ---
    var home by mutableStateOf<HomeData?>(null)
        private set
    var homeLoading by mutableStateOf(false)
        private set
    var homeError by mutableStateOf<String?>(null)
        private set
    private var homeJob: Job? = null
    private var queueFromSearch = false

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

    // --- Testi ---
    var lyrics by mutableStateOf<Lyrics?>(null)
        private set
    var lyricsLoading by mutableStateOf(false)
        private set
    private var lyricsFor: String? = null
    private var lyricsJob: Job? = null
    private val lyricsCache = HashMap<String, Lyrics>()

    // --- Sincronizzazione Google ---
    var syncState by mutableStateOf(SyncState.Idle)
        private set
    var syncStatus by mutableStateOf("")
        private set
    var syncError by mutableStateOf<String?>(null)
        private set

    // --- Aggiornamenti da GitHub ---
    var update by mutableStateOf<Updater.Release?>(null)
        private set
    var updateDismissed by mutableStateOf(false)
    var updateProgress by mutableStateOf<Float?>(null)
        private set
    var updateMessage by mutableStateOf<String?>(null)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set

    // --- Cuffie ---
    var headset by mutableStateOf<String?>(null)
    var headsetDismissed by mutableStateOf(false)

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

        home = HomeRepository.loadCached(app.filesDir)
        loadHome()
        refreshAccount()
        checkUpdate(manual = false)

        // Aggiorna la posizione mentre si ascolta (serve anche ai testi sincronizzati).
        viewModelScope.launch {
            while (true) {
                delay(250)
                controller?.let { if (it.isPlaying) positionMs = it.currentPosition }
            }
        }
    }

    /** Cerca una release più nuova su GitHub (in automatico al massimo ogni 3 ore). */
    fun checkUpdate(manual: Boolean) {
        if (checkingUpdate) return
        val context = getApplication<Application>()
        val prefs = context.getSharedPreferences("sonora", android.content.Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("upd_last", 0L) < 3 * 60 * 60 * 1000L) return
        checkingUpdate = true
        updateMessage = null
        viewModelScope.launch {
            try {
                val rel = Updater.latest()
                prefs.edit().putLong("upd_last", now).apply()
                if (rel != null && Updater.isNewer(rel.tag, Updater.currentVersion(context))) {
                    update = rel
                    updateDismissed = false
                } else {
                    update = null
                    if (manual) updateMessage = "Hai già l'ultima versione"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) updateMessage = "Controllo non riuscito: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                checkingUpdate = false
            }
        }
    }

    fun downloadUpdate() {
        val rel = update ?: return
        if (updateProgress != null) return
        val context = getApplication<Application>()
        updateProgress = 0f
        updateMessage = null
        viewModelScope.launch {
            try {
                val file = Updater.download(context, rel) { updateProgress = it }
                updateProgress = null
                if (!Updater.install(context, file)) {
                    updateMessage = "Consenti a Sonora di installare app, poi tocca di nuovo Aggiorna"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateProgress = null
                updateMessage = "Download non riuscito: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** "Inizia ad ascoltare": riproduzione casuale dei brani preferiti. */
    fun startFavorites() {
        val favorites = store.liked.toList().shuffled()
        val tracks = favorites.ifEmpty { home?.picks.orEmpty().shuffled() }
        if (tracks.isNotEmpty()) play(tracks, 0)
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val target = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        c.seekTo(if (durationMs > 0) target.coerceAtMost(durationMs) else target)
    }

    private fun sync(p: Player) {
        wantsPlay = p.playWhenReady && p.playbackState != Player.STATE_ENDED
        isBuffering = p.playbackState == Player.STATE_BUFFERING
        durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        positionMs = p.currentPosition
        val np = p.currentMediaItem?.let {
            NowPlaying(
                id = it.mediaId,
                title = it.mediaMetadata.title?.toString().orEmpty(),
                artist = it.mediaMetadata.artist?.toString().orEmpty(),
                artwork = it.mediaMetadata.artworkUri?.toString(),
                durationSec = (it.mediaMetadata.durationMs ?: 0L) / 1000
            )
        }
        if (np?.id != nowPlaying?.id && np != null) {
            val track = np.toTrack()
            store.recordPlayed(track)
            if (queueFromSearch) store.recordSearchPlay(track)
        }
        nowPlaying = np
        if (np?.id != lyricsFor) {
            lyricsFor = np?.id
            loadLyrics(np)
        }
    }

    private fun loadLyrics(np: NowPlaying?) {
        lyricsJob?.cancel()
        lyrics = null
        if (np == null) {
            lyricsLoading = false
            return
        }
        lyricsCache[np.id]?.let {
            lyrics = it
            lyricsLoading = false
            return
        }
        lyricsLoading = true
        lyricsJob = viewModelScope.launch {
            val found = LyricsRepository.fetch(store.lyricsSource, np.title, np.artist, np.durationSec)
            if (found != null) lyricsCache[np.id] = found
            lyrics = found
            lyricsLoading = false
        }
    }

    // ───────── Ricerca e riproduzione ─────────

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        store.markSearched()
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

    fun play(tracks: List<Track>, index: Int, fromSearch: Boolean = false) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        queueFromSearch = fromSearch
        c.setMediaItems(tracks.map { it.toMediaItem() }, index.coerceIn(0, tracks.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun playFrom(index: Int) = play(results, index, fromSearch = true)

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
        if (durationMs > 0) seekToMs((fraction * durationMs).toLong())
    }

    fun seekToMs(ms: Long) {
        val c = controller ?: return
        c.seekTo(ms)
        positionMs = ms
    }

    // ───────── Home ─────────

    fun loadHome(force: Boolean = false) {
        val cached = home
        if (!force && cached != null && cached.day == HomeRepository.today()) return
        if (homeJob?.isActive == true) return
        homeJob = viewModelScope.launch {
            homeLoading = true
            homeError = null
            try {
                val personal = store.liked.toList() + store.played.take(30)
                val data = HomeRepository.load(store.googleCookie, personal)
                home = data
                HomeRepository.saveCached(getApplication<Application>().filesDir, data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (home == null) homeError = "Impossibile caricare la Home: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                homeLoading = false
            }
        }
    }

    // ───────── Download ─────────

    fun download(track: Track) {
        store.remember(track)
        AudioStore.download(track)
    }

    fun removeOffline(id: String) = AudioStore.remove(id)

    // ───────── Account Google ─────────

    private fun refreshAccount() {
        val cookie = store.googleCookie ?: return
        viewModelScope.launch {
            GoogleSync.fetchAccount(cookie)?.let { store.updateAccount(it.name, it.photo) }
        }
    }

    fun connectGoogle(cookie: String) {
        store.updateGoogleCookie(cookie)
        loginOpen = false
        refreshAccount()
        syncGoogle()
        loadHome(force = true)
    }

    fun syncGoogle() {
        val cookie = store.googleCookie ?: return
        if (syncState == SyncState.Running) return
        syncState = SyncState.Running
        syncError = null
        syncStatus = ""
        viewModelScope.launch {
            try {
                val imported = GoogleSync.importAll(cookie) { syncStatus = it }
                store.replaceGooglePlaylists(imported)
                syncState = SyncState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                syncError = e.message ?: "Sincronizzazione non riuscita"
                syncState = SyncState.Failed
            }
        }
    }

    fun disconnectGoogle() {
        store.updateGoogleCookie(null)
        store.updateAccount(null, null)
        syncState = SyncState.Idle
        syncError = null
        CookieManager.getInstance().removeAllCookies(null)
    }

    fun finishOnboarding(name: String) {
        store.completeOnboarding(name)
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
