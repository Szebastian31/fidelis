package com.fidelis.player.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fidelis.player.ui.playlists.PlaylistPicker

data class SheetAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Bottom sheet with a song header and a list of actions, plus Add to playlist (when the song's
 * [uri] is known), song radio and "Go to artist".
 */
@Composable
fun TrackSheet(
    title: String, subtitle: String, imageUrl: String?,
    actions: List<SheetAction>, onDismiss: () -> Unit,
    uri: String? = null,
) = SheetBody(title, subtitle, imageUrl, actions, onDismiss, uri, startPicking = false)

/** Straight to your playlists, for the player's + button. [image] can be a URL or a Bitmap. */
@Composable
fun AddToPlaylistSheet(title: String, subtitle: String, image: Any?, uri: String, onDismiss: () -> Unit) =
    SheetBody(title, subtitle, image, emptyList(), onDismiss, uri, startPicking = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetBody(
    title: String, subtitle: String, image: Any?,
    actions: List<SheetAction>, onDismiss: () -> Unit,
    uri: String?, startPicking: Boolean,
) {
    val canAdd = uri != null && (uri.startsWith("spotify:track:") || uri.startsWith("spotify:episode:"))
    // The playlist list replaces the actions inside the same sheet.
    var picking by remember { mutableStateOf(startPicking && canAdd) }
    val radio = radioActions(title, subtitle)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Fidelis.bg1,
        contentColor = Fidelis.ink,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp)
                .clip(CircleShape).background(Fidelis.inkFaint))
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Fidelis.glass)) {
                image?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Fidelis.ink, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Fidelis.line))
        if (picking && uri != null) {
            PlaylistPicker(uri, onDone = onDismiss)
        } else {
            actions.forEach { a -> ActionRow(a.label, a.icon) { a.onClick(); onDismiss() } }
            if (canAdd) ActionRow("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { picking = true }
            radio.forEach { a -> ActionRow(a.label, a.icon) { a.onClick(); onDismiss() } }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@Composable
private fun ActionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Fidelis.violet, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun radioActions(title: String, artist: String): List<SheetAction> {
    val t = LocalTrackActions.current ?: return emptyList()
    if (artist.isBlank()) return emptyList()
    return listOf(
        SheetAction("Start song radio", Icons.Rounded.Radio) { t.songRadio(title, artist) },
        SheetAction("Go to $artist", Icons.Rounded.Person) { t.openArtist(artist) },
    )
}
