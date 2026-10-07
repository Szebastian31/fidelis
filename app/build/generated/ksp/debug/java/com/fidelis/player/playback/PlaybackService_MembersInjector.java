package com.fidelis.player.playback;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class PlaybackService_MembersInjector implements MembersInjector<PlaybackService> {
  private final Provider<PlaybackController> controllerProvider;

  public PlaybackService_MembersInjector(Provider<PlaybackController> controllerProvider) {
    this.controllerProvider = controllerProvider;
  }

  public static MembersInjector<PlaybackService> create(
      Provider<PlaybackController> controllerProvider) {
    return new PlaybackService_MembersInjector(controllerProvider);
  }

  @Override
  public void injectMembers(PlaybackService instance) {
    injectController(instance, controllerProvider.get());
  }

  @InjectedFieldSignature("com.fidelis.player.playback.PlaybackService.controller")
  public static void injectController(PlaybackService instance, PlaybackController controller) {
    instance.controller = controller;
  }
}
