package com.fidelis.player.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.fidelis.player.integration.spotify.PlaylistTrack

/** App-wide song actions: go to the artist, start a song radio, play songs like a list. */
class TrackActions(
    val openArtist: (artist: String) -> Unit,
    val songRadio: (title: String, artist: String) -> Unit,
    val similarTo: (tracks: List<PlaylistTrack>, label: String) -> Unit,
)

val LocalTrackActions = staticCompositionLocalOf<TrackActions?> { null }
