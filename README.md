# Fidelis

A premium **Spotify companion** for Android. Fidelis imports your Spotify playlists, plays full tracks by bridging to the installed Spotify app, builds mood-based mixes from your library using Last.fm tags, scrobbles to Last.fm, and wraps it all in a polished, glassy UI.

> Working name — change `fidelis` / `com.fidelis.player` to whatever you like.

## What it does

- **Playlists.** Imports your Spotify playlists; tap a playlist to see its track list, then play from any song.
- **Mood mixes.** Suggested mixes (e.g. "On repeat lately", "Chill afternoon vibes", "Morning energy boost") plus a build-your-own flow: pick a playlist + a mood and Fidelis scores and orders tracks for a smooth set. Ordering uses **per-track Last.fm tags** (falling back to per-artist tags), chained nearest-neighbour so adjacent songs share mood.
- **Full-screen player.** Album-art hero, real transport controls, seek, and an "Up next" queue with reshuffle. A compact now-playing pill on the other tabs expands into it.
- **System media notification.** A `MediaSession` + MediaStyle foreground notification mirrors the bridge state, so lock-screen controls and the ColorOS **Fluid Cloud** capsule work and point at Fidelis.
- **Last.fm scrobbling.** Personal session via `auth.getMobileSession`, stored encrypted on-device.
- **Skins + premium styling.** Multiple color skins (Daylight / Vinyl / Mint / Nebula / Midnight), a custom rounded/bold type scale, violet→pink accent gradient, and frosted-glass cards — modeled on the "Consensus Weather" look.

## How playback works (the bridge)

Fidelis does **not** stream audio itself — the Spotify Web API doesn't provide audio. Instead it remote-controls the installed Spotify app via the **Spotify App Remote SDK**:

- Requires **Spotify Premium** and the **Spotify app installed** on the device.
- Fidelis keeps the full planned order and feeds Spotify the next ~3 tracks via the queue as playback advances (lazy "3-ahead" queue).
- **Crossfade, Automix, audio quality, and EQ are owned by the Spotify app**, not Fidelis — adjust those in Spotify's own settings. The in-app copy is honest about this.

Mood/similarity signals come from **Last.fm** (`track.getTopTags`, `artist.getTopTags`, `track.getSimilar`), since Spotify removed audio-features/recommendations from the Web API.

## Stack

- Kotlin, Jetpack Compose + Material 3, Navigation-Compose
- Hilt (DI), Coroutines + Flow
- Spotify **App Remote** SDK (`app/libs/spotify-app-remote-release-0.8.0.aar`) + Spotify **Auth** library (`com.spotify.android:auth`) for OAuth
- OkHttp + `org.json` for Spotify Web API & Last.fm calls
- Media3 session + `androidx.media:media` (`MediaSessionCompat` / MediaStyle notification)
- Coil (artwork), `androidx.security:security-crypto` (encrypted tokens)
- Room + Media3 ExoPlayer are retained from the earlier local-player scaffold and are largely vestigial now that playback goes through the bridge.

## Setup

### 1. Spotify dashboard
In your Spotify Developer dashboard app:
- Add the redirect URI `fidelis://auth`.
- Register the Android package `com.fidelis.player` with the signing **SHA-1 fingerprint**. CI signs with the committed `fidelis-debug.keystore`, so its SHA-1 is stable — register that one.

### 2. Secrets
Put your keys in `local.properties` (git-ignored); they're exposed via `BuildConfig`:

```
SPOTIFY_CLIENT_ID=xxxxxxxx
LASTFM_API_KEY=xxxxxxxx
LASTFM_SECRET=xxxxxxxx
```

The Spotify **Client ID is public** (PKCE, no client secret in the app). Keep the Last.fm secret and any tokens out of version control.

### 3. Build
GitHub Actions builds a debug APK (`assembleDebug`), signed with the committed debug keystore for a stable fingerprint. Download the artifact and install it on your device.

## Per-user accounts

The app ships **only public identifiers**; each user authenticates their own accounts:

- **Spotify** — OAuth 2.0 (TOKEN via the Auth library, explicit approval screen), then App Remote connects. Tokens stored per-device in `EncryptedSharedPreferences`.
- **Last.fm** — public `api_key` + `shared_secret` sign requests; each user's session key is stored encrypted on-device.

Encrypted storage (`SecureTokenStore`) self-heals: if the Keystore master key no longer matches the encrypted file (e.g. after a reinstall), it wipes and recreates the store instead of crashing. `allowBackup` is disabled to avoid restoring an unreadable encrypted file onto a device with a different key.

## Requirements

- Android 8.0+ (minSdk 26); tested on ColorOS (Oppo Find X9 Pro).
- Spotify Premium + Spotify app installed.
- Notification permission (Android 13+) for the media notification.

## Known gaps

- **Scrobbling isn't wired to the bridge yet** — the scrobble path still targets the old local player, so plays don't currently reach Last.fm. (Planned: hook scrobbling to the bridge's player state.)
- Crossfade/EQ can't be controlled from Fidelis by design (Spotify owns the audio).
- The status-bar icon uses a stock glyph; a monochrome `ic_stat_fidelis` is a TODO.

## License / attribution

If you reuse code or structure from other projects (e.g. Apache-2.0 sources), include their attribution in a `NOTICE` file.
