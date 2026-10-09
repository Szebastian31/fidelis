package com.fidelis.player.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout. ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

data class SheetAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** Bottom sheet with a song header and a list of actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(
    title: String, subtitle: String, imageUrl: String?,
    actions: List<SheetAction>, onDismiss: () -> Unit,
) {
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
                imageUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
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
        actions.forEach { a ->
            Row(
                Modifier.fillMaxWidth().pressable { a.onClick(); onDismiss() }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(a.icon, null, tint = Fidelis.violet, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
                Text(a.label, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}
