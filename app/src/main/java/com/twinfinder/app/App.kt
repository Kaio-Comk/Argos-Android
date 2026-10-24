package com.twinfinder.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.twinfinder.app.data.Prefs
import com.twinfinder.app.reminders.Reminders

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Prefs(this).themeMode)
        Reminders.createChannel(this)
        Reminders.sync(this)
    }

    /** Miniaturas de fotos e quadros de vídeo, tudo lido do próprio aparelho. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
}
