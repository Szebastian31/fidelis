package com.fidelis.player.di;

import android.content.Context;
import com.fidelis.player.data.secure.SecureTokenStore;
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
public final class AppModule_TokenStoreFactory implements Factory<SecureTokenStore> {
  private final Provider<Context> ctxProvider;

  public AppModule_TokenStoreFactory(Provider<Context> ctxProvider) {
    this.ctxProvider = ctxProvider;
  }

  @Override
  public SecureTokenStore get() {
    return tokenStore(ctxProvider.get());
  }

  public static AppModule_TokenStoreFactory create(Provider<Context> ctxProvider) {
    return new AppModule_TokenStoreFactory(ctxProvider);
  }

  public static SecureTokenStore tokenStore(Context ctx) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.tokenStore(ctx));
  }
}
