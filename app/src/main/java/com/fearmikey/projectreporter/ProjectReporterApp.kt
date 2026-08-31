package com.fearmikey.projectreporter

import android.app.Application
import android.content.pm.ApplicationInfo
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import coil.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ProjectReporterApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // Smooths out the "pop-in" of photo thumbnails/detail images instead of
            // an abrupt appearance, which is a big part of perceived jank on
            // photo-heavy screens.
            .crossfade(true)
            // Local camera photos can be large; cap how much memory the bitmap
            // cache is allowed to use so scrolling a big photo grid doesn't put
            // memory pressure on the rest of the UI.
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .apply {
                if (isDebuggable()) logger(DebugLogger())
            }
            .build()
    }

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
