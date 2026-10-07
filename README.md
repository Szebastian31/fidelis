# Fidelis

A local-first, lossless-first music player for Android. Built around the music **you already own**, honest about the **real fidelity** of every track, with a Spotify-library importer, Last.fm scrobbling, Smart Shuffle, and an intelligent crossfade.

> Working name — change `fidelis` / `com.fidelis.player` to whatever you like.

## Why it's different
- **Fidelity truth.** Every track shows its real codec, bit depth, and sample rate, read from the file at scan time — never guessed from the extension. It flags lossy tracks for which a lossless copy should exist.
- **Spotify as taste graph, local as audio.** Your Spotify library (liked songs, playlists, saved albums, top artists) is imported as *what you care about*, then matched to local FLAC files. **No audio ever comes from Spotify** — the API doesn't provide it.
- **Last.fm + intelligence.** Correct scrobbling, consensus Smart Shuffle (crowd similarity blended with on-device audio analysis), and a crossfade whose length is chosen from the music.

## Stack
- Kotlin 2.x, Jetpack Compose + Material 3
- Media3 / ExoPlayer + `MediaSessionService` (native FLAC up to 24-bit/192 kHz)
- Hilt (DI), Room (local index), Coroutines + Flow
- Retrofit + OkHttp (Spotify / Last.fm), Coil (artwork)
- JAudiotagger (tags), TarsosDSP (BPM / key / energy)

## Per-user accounts (important)
This app ships **only public identifiers**. Each user authenticates **their own** accounts:
- **Spotify** — OAuth 2.0 **PKCE**. Only a public `client_id` is shipped; there is **no client secret** in the app. Tokens are stored per-device in `EncryptedSharedPreferences`.
- **Last.fm** — a public `api_key` + `shared_secret` sign requests; each user gets a personal **session key** via `auth.getMobileSession`, stored encrypted on-device.

Put your keys in `local.properties` (git-ignored) and expose them via `BuildConfig`:
```
SPOTIFY_CLIENT_ID=xxxxxxxx
LASTFM_API_KEY=xxxxxxxx
LASTFM_SECRET=xxxxxxxx
```

## Status
This is a **starter scaffold**: it has not been compiled in CI. Before first build, update dependency versions in `gradle/libs.versions.toml` to the latest, and fill the Home/Library/Profile screen stubs. The playback core, Room layer, Spotify matcher, Last.fm scrobbler, and crossfade engine are implemented.

## License / attribution
If you reuse code or structure from Obsidian Music (Apache-2.0), include its attribution in a `NOTICE` file.
