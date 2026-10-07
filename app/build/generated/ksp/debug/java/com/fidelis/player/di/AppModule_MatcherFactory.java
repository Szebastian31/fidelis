package com.fidelis.player.di;

import com.fidelis.player.data.db.MusicDao;
import com.fidelis.player.integration.spotify.SpotifyMatcher;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class AppModule_MatcherFactory implements Factory<SpotifyMatcher> {
  private final Provider<MusicDao> daoProvider;

  public AppModule_MatcherFactory(Provider<MusicDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public SpotifyMatcher get() {
    return matcher(daoProvider.get());
  }

  public static AppModule_MatcherFactory create(Provider<MusicDao> daoProvider) {
    return new AppModule_MatcherFactory(daoProvider);
  }

  public static SpotifyMatcher matcher(MusicDao dao) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.matcher(dao));
  }
}
