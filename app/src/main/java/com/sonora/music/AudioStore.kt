package com.sonora.music

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.TreeSet
import java.util.concurrent.Executors

/**
 * Due cache audio separate, entrambe indicizzate per ID del brano:
 *  - cache automatica: ogni brano ascoltato viene salvato (max 1 GB, i più vecchi vengono eliminati);
 *  - download: brani scaricati di proposito, mai eliminati in automatico.
 * Un brano già in cache che viene scaricato si "sposta" nei download senza riscaricarlo.
 */
@UnstableApi
object AudioStore {

    private lateinit var appContext: Context
    private lateinit var streamCache: SimpleCache
    private lateinit var downloadCache: SimpleCache
    private lateinit var evictor: LimitEvictor

    /** Limite della cache automatica in GB; 0 = illimitata. */
    var cacheLimitGb by mutableStateOf(1)
        private set

    /** Spazio occupato (byte) dalla cache automatica e dai download. */
    var cacheUsedBytes by mutableStateOf(0L)
        private set
    var downloadsUsedBytes by mutableStateOf(0L)
        private set

    /** Brani presenti in cache solo in parte (non ancora completi, quindi non riproducibili offline). */
    var partialCount by mutableStateOf(0)
        private set

    /** Ultimo errore nel completare un brano in cache (per capire perché un brano resta parziale). */
    var lastFillError by mutableStateOf<String?>(null)
        private set

    private val fillExecutor = Executors.newSingleThreadExecutor()
    private val stateExecutor = Executors.newSingleThreadExecutor()
    private val downloadExecutor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var fillWriter: CacheWriter? = null
    private val writers = ConcurrentHashMap<String, CacheWriter>()

    /** Brani completamente in cache (esclusi quelli scaricati). */
    var cachedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Brani scaricati. */
    var downloadedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Download in corso: ID -> avanzamento 0..1. */
    val downloading = mutableStateMapOf<String, Float>()

    private val keyFactory = androidx.media3.datasource.cache.CacheKeyFactory { spec -> keyOf(spec.uri) }

    private fun keyOf(uri: Uri): String = uri.lastPathSegment ?: uri.toString()

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val db = StandaloneDatabaseProvider(appContext)
        cacheLimitGb = appContext.getSharedPreferences("sonora", Context.MODE_PRIVATE)
            .getInt("cache_limit_gb", 1)
        evictor = LimitEvictor(limitBytes(cacheLimitGb))
        streamCache = SimpleCache(File(appContext.cacheDir, "audio_cache"), evictor, db)
        downloadCache = SimpleCache(File(appContext.filesDir, "downloads"), NoOpCacheEvictor(), db)
        refresh()
    }

    // ───────── Sorgente dati usata dal player ─────────

    /** URL reale risolto al momento della riproduzione (vedi trackUri). */
    private val upstream: DataSource.Factory by lazy {
        val http = OkHttpDataSource.Factory(Http.client).setUserAgent(USER_AGENT)
        ResolvingDataSource.Factory(http, ResolvingDataSource.Resolver { spec: DataSpec ->
            if (spec.uri.scheme == TRACK_SCHEME) {
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
    }

    private fun streamFactory(): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(streamCache)
            .setCacheKeyFactory(keyFactory)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Download (sola lettura) -> cache automatica (lettura/scrittura) -> rete. */
    fun playbackFactory(): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory(keyFactory)
            .setUpstreamDataSourceFactory(streamFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    // ───────── Stato ─────────

    private fun isFull(cache: Cache, id: String): Boolean {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(id))
        return length > 0 && cache.isCached(id, 0, length)
    }

    fun refresh() {
        // Esecutore dedicato: non deve aspettare che finisca un brano in salvataggio.
        stateExecutor.execute {
            try {
                val downloaded = downloadCache.keys.filter { isFull(downloadCache, it) }.toSet()
                val streamKeys = streamCache.keys.toList()
                val full = streamKeys.filter { isFull(streamCache, it) }.toSet()
                val cached = full - downloaded
                partialCount = streamKeys.count { it !in full }
                downloadedIds = downloaded
                cachedIds = cached
                cacheUsedBytes = streamCache.cacheSpace
                downloadsUsedBytes = downloadCache.cacheSpace
            } catch (e: Exception) {
                // Stato best-effort.
            }
        }
    }

    // ───────── Limite e gestione della cache ─────────

    private fun limitBytes(gb: Int): Long = if (gb <= 0) 0L else gb * 1024L * 1024L * 1024L

    /** Imposta il limite (1, 2, 5, 10 GB o 0 = illimitata) ed elimina subito il superfluo, dai brani più vecchi. */
    fun setCacheLimit(gb: Int) {
        cacheLimitGb = gb
        appContext.getSharedPreferences("sonora", Context.MODE_PRIVATE)
            .edit().putInt("cache_limit_gb", gb).apply()
        evictor.maxBytes = limitBytes(gb)
        fillExecutor.execute {
            try {
                synchronized(streamCache) { evictor.trim(streamCache) }
            } catch (e: Exception) {
                // best-effort
            }
            refresh()
        }
    }

    /** Svuota la cache automatica (i download restano). */
    fun clearCache() {
        fillWriter?.cancel()
        fillExecutor.execute {
            try {
                for (key in streamCache.keys.toList()) streamCache.removeResource(key)
            } catch (e: Exception) {
                // best-effort
            }
            refresh()
        }
    }

    /** Come LeastRecentlyUsedCacheEvictor, ma con limite modificabile a runtime (0 = nessun limite). */
    private class LimitEvictor(@Volatile var maxBytes: Long) : CacheEvictor {
        private val spans = TreeSet<CacheSpan> { a, b ->
            val d = a.lastTouchTimestamp - b.lastTouchTimestamp
            if (d == 0L) a.compareTo(b) else if (d < 0) -1 else 1
        }
        private var currentSize = 0L

        override fun requiresCacheSpanTouches() = true

        override fun onCacheInitialized() {}

        override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
            if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
        }

        override fun onSpanAdded(cache: Cache, span: CacheSpan) {
            spans.add(span)
            currentSize += span.length
            evict(cache, 0)
        }

        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
            spans.remove(span)
            currentSize -= span.length
        }

        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
            onSpanRemoved(cache, oldSpan)
            onSpanAdded(cache, newSpan)
        }

        fun trim(cache: Cache) = evict(cache, 0)

        private fun evict(cache: Cache, requiredSpace: Long) {
            val max = maxBytes
            if (max <= 0L) return
            while (currentSize + requiredSpace > max && spans.isNotEmpty()) {
                cache.removeSpan(spans.first())
            }
        }
    }

    // ───────── Cache automatica ─────────

    /**
     * Durante l'ascolto ExoPlayer salva solo ciò che bufferizza: qui completiamo
     * il brano in background, così resta disponibile anche offline.
     */
    fun fillCache(id: String) {
        fillWriter?.cancel()
        if (id in cachedIds || id in downloadedIds) return
        fillExecutor.execute {
            try {
                if (isFull(streamCache, id) || isFull(downloadCache, id)) return@execute
                val source = streamFactory().createDataSource()
                val writer = CacheWriter(source, specFor(id), null, null)
                fillWriter = writer
                writer.cache()
                lastFillError = null
            } catch (e: java.io.InterruptedIOException) {
                // Annullato (hai cambiato brano): nessun errore.
            } catch (e: Exception) {
                // Rete assente o rifiuto del server: si riprova al prossimo ascolto.
                lastFillError = e.message ?: e.javaClass.simpleName
            } finally {
                refresh()
            }
        }
    }

    // ───────── Download ─────────

    fun download(track: Track) {
        val id = track.id
        if (id in downloadedIds || downloading.containsKey(id)) return
        downloading[id] = 0f
        downloadExecutor.execute {
            try {
                // Legge dalla cache automatica se il brano c'è già, altrimenti dalla rete.
                val source = CacheDataSource.Factory()
                    .setCache(downloadCache)
                    .setCacheKeyFactory(keyFactory)
                    .setUpstreamDataSourceFactory(streamFactory())
                    .createDataSource()
                val writer = CacheWriter(source, specFor(id), null) { length, cached, _ ->
                    if (length > 0) downloading[id] = (cached.toFloat() / length).coerceIn(0f, 1f)
                }
                writers[id] = writer
                writer.cache()
                streamCache.removeResource(id) // il brano passa da "cache" a "scaricati"
                toast("Scaricato: ${track.title}")
            } catch (e: Exception) {
                toast("Download non riuscito: ${track.title}")
            } finally {
                writers.remove(id)
                downloading.remove(id)
                refresh()
            }
        }
    }

    fun cancelDownload(id: String) {
        writers[id]?.cancel()
    }

    /** Elimina il brano dai download o dalla cache. */
    fun remove(id: String) {
        fillExecutor.execute {
            try {
                downloadCache.removeResource(id)
                streamCache.removeResource(id)
            } catch (e: Exception) {
                // ignorato
            }
            refresh()
        }
    }

    private fun specFor(id: String): DataSpec =
        DataSpec.Builder().setUri(trackUri(id)).setKey(id).build()

    private fun toast(text: String) {
        main.post { Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show() }
    }
}
