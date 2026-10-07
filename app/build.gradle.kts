import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Read secrets from local.properties (never committed) and expose via BuildConfig.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String = (localProps.getProperty(key) ?: "").let { "\"$it\"" }

android {
    namespace = "com.fidelis.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fidelis.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SPOTIFY_CLIENT_ID", secret("SPOTIFY_CLIENT_ID"))
        buildConfigField("String", "LASTFM_API_KEY", secret("LASTFM_API_KEY"))
        buildConfigField("String", "LASTFM_SECRET", secret("LASTFM_SECRET"))
        // Custom scheme used for the Spotify OAuth redirect (register it in your Spotify dashboard).
        manifestPlaceholders["spotifyRedirectScheme"] = "fidelis"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.coroutines.android)
    implementation(libs.security.crypto)

    // NOTE: add JAudiotagger and TarsosDSP from their distributions:
    // implementation("net.jthink:jaudiotagger:3.0.1")
    // implementation("com.github.axet:TarsosDSP:2.4-1") // or a maintained fork
}
