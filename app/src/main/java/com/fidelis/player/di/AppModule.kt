package com.fidelis.player.di

import android.content.Context
import androidx.room.Room
import com.fidelis.player.BuildConfig
import com.fidelis.player.data.db.MusicDao
import com.fidelis.player.data.db.MusicDatabase
import com.fidelis.player.data.scan.MediaStoreScanner
import com.fidelis.player.data.secure.SecureTokenStore
import com.fidelis.player.integration.lastfm.LastfmApi
import com.fidelis.player.integration.lastfm.LastfmApiImpl
import com.fidelis.player.integration.lastfm.LastfmScrobbler
import com.fidelis.player.integration.lastfm.SimilarityEngine
import com.fidelis.player.integration.spotify.SpotifyMatcher
import com.fidelis.player.playback.crossfade.CrossfadeEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): MusicDatabase =
        Room.databaseBuilder(ctx, MusicDatabase::class.java, "fidelis.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun dao(db: MusicDatabase): MusicDao = db.musicDao()

    @Provides @Singleton
    fun scanner(@ApplicationContext ctx: Context) = MediaStoreScanner(ctx)

    @Provides @Singleton
    fun tokenStore(@ApplicationContext ctx: Context) = SecureTokenStore(ctx)

    @Provides @Singleton
    fun matcher(dao: MusicDao) = SpotifyMatcher(dao)

    @Provides @Singleton
    fun crossfadeEngine() = CrossfadeEngine(userPreferenceMs = CrossfadeEngine.AUTO)

    @Provides @Singleton
    fun spotifyClientId(): String = BuildConfig.SPOTIFY_CLIENT_ID

    // ---- Last.fm ----
    @Provides @Singleton
    fun lastfmApi(impl: LastfmApiImpl): LastfmApi = impl

    @Provides @Singleton
    fun lastfmScrobbler(api: LastfmApi, dao: MusicDao, tokenStore: SecureTokenStore): LastfmScrobbler =
        LastfmScrobbler(api, dao, BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_SECRET) {
            tokenStore.lastfmSessionKey
        }

    @Provides @Singleton
    fun similarityEngine(api: LastfmApi, dao: MusicDao): SimilarityEngine = SimilarityEngine(api, dao)
}
