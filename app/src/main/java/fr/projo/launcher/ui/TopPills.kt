package fr.projo.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** Pastilles en haut à droite : Sources, Wi-Fi, Bluetooth, Réglages. */
@Composable
fun TopPills(
    wifiLabel: String,
    onSources: () -> Unit,
    onSourcesLong: () -> Unit,
    onWifi: () -> Unit,
    onBluetooth: () -> Unit,
    onSettings: () -> Unit,
    onSettingsLong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.u)) {
        Pill(ProjoIcons.Sources, "Sources", onSources, onSourcesLong)
        Pill(ProjoIcons.Wifi, wifiLabel, onWifi)
        Pill(ProjoIcons.Bluetooth, "Bluetooth", onBluetooth)
        Pill(ProjoIcons.Reglages, "Réglages", onSettings, onSettingsLong)
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.92f),
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.75f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.u, vertical = 8.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.u))
            Spacer(Modifier.width(7.u))
            Text(label, fontSize = 13.us, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}
