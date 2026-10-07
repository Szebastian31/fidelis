package com.fidelis.player.playback;

import android.content.Context;
import com.fidelis.player.playback.crossfade.CrossfadeEngine;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class PlaybackController_Factory implements Factory<PlaybackController> {
  private final Provider<Context> contextProvider;

  private final Provider<CrossfadeEngine> crossfadeProvider;

  public PlaybackController_Factory(Provider<Context> contextProvider,
      Provider<CrossfadeEngine> crossfadeProvider) {
    this.contextProvider = contextProvider;
    this.crossfadeProvider = crossfadeProvider;
  }

  @Override
  public PlaybackController get() {
    return newInstance(contextProvider.get(), crossfadeProvider.get());
  }

  public static PlaybackController_Factory create(Provider<Context> contextProvider,
      Provider<CrossfadeEngine> crossfadeProvider) {
    return new PlaybackController_Factory(contextProvider, crossfadeProvider);
  }

  public static PlaybackController newInstance(Context context, CrossfadeEngine crossfade) {
    return new PlaybackController(context, crossfade);
  }
}
