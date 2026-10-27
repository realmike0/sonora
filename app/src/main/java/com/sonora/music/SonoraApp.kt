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
        AudioStore.init(this)
        NewPipe.init(
            YtDownloader(Http.client),
            Localization("it", "IT"),
            ContentCountry("IT")
        )
    }
}
