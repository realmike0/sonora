package com.sonora.music

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class SonoraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(
            YtDownloader(Http.client),
            Localization("it", "IT"),
            ContentCountry("IT")
        )
    }
}
