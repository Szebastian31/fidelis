# Add project-specific ProGuard/R8 rules here.
# Hilt, Room, Media3, Retrofit, and Coil ship their own consumer rules, so this
# can stay nearly empty until you add reflection-based code of your own.
-dontwarn org.jaudiotagger.**
-dontwarn be.tarsos.**

# Release build (3.0). R8 trims and optimises the code but keeps every name, so the crash report
# in Profile stays readable without a mapping file. Fidelis is open source, so there is nothing to hide.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# The Spotify App Remote and auth SDKs read their messages with Gson by reflection.
-keep class com.spotify.** { *; }
-keep interface com.spotify.** { *; }
-dontwarn com.spotify.**

# Optional classes that security-crypto (Tink) and OkHttp mention but never need on Android.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
