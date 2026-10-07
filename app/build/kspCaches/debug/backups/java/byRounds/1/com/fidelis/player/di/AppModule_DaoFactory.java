package com.fidelis.player.di;

import com.fidelis.player.data.db.MusicDao;
import com.fidelis.player.data.db.MusicDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class AppModule_DaoFactory implements Factory<MusicDao> {
  private final Provider<MusicDatabase> dbProvider;

  public AppModule_DaoFactory(Provider<MusicDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public MusicDao get() {
    return dao(dbProvider.get());
  }

  public static AppModule_DaoFactory create(Provider<MusicDatabase> dbProvider) {
    return new AppModule_DaoFactory(dbProvider);
  }

  public static MusicDao dao(MusicDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.dao(db));
  }
}
