package com.fidelis.player.di;

import android.content.Context;
import com.fidelis.player.data.db.MusicDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_DatabaseFactory implements Factory<MusicDatabase> {
  private final Provider<Context> ctxProvider;

  public AppModule_DatabaseFactory(Provider<Context> ctxProvider) {
    this.ctxProvider = ctxProvider;
  }

  @Override
  public MusicDatabase get() {
    return database(ctxProvider.get());
  }

  public static AppModule_DatabaseFactory create(Provider<Context> ctxProvider) {
    return new AppModule_DatabaseFactory(ctxProvider);
  }

  public static MusicDatabase database(Context ctx) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.database(ctx));
  }
}
