package com.fidelis.player.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class AppModule_SpotifyClientIdFactory implements Factory<String> {
  @Override
  public String get() {
    return spotifyClientId();
  }

  public static AppModule_SpotifyClientIdFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String spotifyClientId() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.spotifyClientId());
  }

  private static final class InstanceHolder {
    private static final AppModule_SpotifyClientIdFactory INSTANCE = new AppModule_SpotifyClientIdFactory();
  }
}
