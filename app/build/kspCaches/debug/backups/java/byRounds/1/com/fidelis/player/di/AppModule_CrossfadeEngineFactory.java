package com.fidelis.player.di;

import com.fidelis.player.playback.crossfade.CrossfadeEngine;
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
public final class AppModule_CrossfadeEngineFactory implements Factory<CrossfadeEngine> {
  @Override
  public CrossfadeEngine get() {
    return crossfadeEngine();
  }

  public static AppModule_CrossfadeEngineFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CrossfadeEngine crossfadeEngine() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.crossfadeEngine());
  }

  private static final class InstanceHolder {
    private static final AppModule_CrossfadeEngineFactory INSTANCE = new AppModule_CrossfadeEngineFactory();
  }
}
