package com.sonora.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

@OptIn(androidx.media3.common.util.UnstableApi::class)
class SonoraApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(Http.client)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("covers")).maxSizeBytes(128L * 1024 * 1024).build() }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Il processo della schermata d'errore non deve inizializzare nient'altro.
        if (android.os.Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":crash")) return
        // Se l'app crasha, mostra l'errore in una schermata a parte (così si può copiare e mandare).
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val trace = android.util.Log.getStackTraceString(e).take(6000)
                startActivity(
                    android.content.Intent(this, CrashActivity::class.java).putExtra("trace", trace)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                Thread.sleep(400)
            } catch (_: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
        AudioStore.init(this)
        AudioPrefs.load(this)
        PlayLog.init(this)
        Tracking.init(this)
        SonoraId.init(this)
        NewPipe.init(
            YtDownloader(Http.client),
            Localization("it", "IT"),
            ContentCountry("IT")
        )
    }
}
