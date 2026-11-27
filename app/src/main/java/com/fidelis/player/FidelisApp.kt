package com.fidelis.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.fidelis.player.integration.spotify.SpotifyHiRes
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FidelisApp : Application(), ImageLoaderFactory {
    /** Every cover goes through here, so it loads at the right size: full for big covers, lighter in lists. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SpotifyHiRes) }
        // A cover never changes at the same address, so keep it on the phone whatever the server says.
        .respectCacheHeaders(false)
        .build()
}
