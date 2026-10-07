package com.fidelis.player.di;

import android.content.Context;
import com.fidelis.player.data.scan.MediaStoreScanner;
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
public final class AppModule_ScannerFactory implements Factory<MediaStoreScanner> {
  private final Provider<Context> ctxProvider;

  public AppModule_ScannerFactory(Provider<Context> ctxProvider) {
    this.ctxProvider = ctxProvider;
  }

  @Override
  public MediaStoreScanner get() {
    return scanner(ctxProvider.get());
  }

  public static AppModule_ScannerFactory create(Provider<Context> ctxProvider) {
    return new AppModule_ScannerFactory(ctxProvider);
  }

  public static MediaStoreScanner scanner(Context ctx) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.scanner(ctx));
  }
}
