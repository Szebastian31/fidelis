package com.fidelis.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.fidelis.player.integration.spotify.SpotifyHiRes
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FidelisApp : Application(), ImageLoaderFactory {
    /** Every cover goes through here, so Spotify thumbnails are swapped for their full-size version. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SpotifyHiRes) }
        .build()
}
