package fr.projo.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * Pastilles en haut à droite : ronds en « verre dépoli » avec seulement l'icône ;
 * la pastille sélectionnée devient blanche et son nom apparaît en glissant.
 */
@Composable
fun TopPills(
    wifiLabel: String,
    onSources: () -> Unit,
    onSourcesLong: () -> Unit,
    onWifi: () -> Unit,
    onBluetooth: () -> Unit,
    onSettings: () -> Unit,
    onSettingsLong: () -> Unit,
    /** Une nouvelle version de Projo est disponible : pastille mise en avant. */
    majDisponible: Boolean = false,
    onMaj: () -> Unit = {},
    focusReglages: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.u), verticalAlignment = Alignment.CenterVertically) {
        val h = 38
        if (majDisponible) PillMaj(h, onMaj)
        // Fire TV : pas d'entrée HDMI à ouvrir
        if (!LocalFireTv.current) Pill(ProjoIcons.Sources, "Sources", h, onSources, onSourcesLong)
        Pill(ProjoIcons.Wifi, wifiLabel, h, onWifi)
        Pill(ProjoIcons.Bluetooth, "Bluetooth", h, onBluetooth)
        Pill(
            ProjoIcons.Reglages, "Réglages", h, onSettings, onSettingsLong,
            if (focusReglages != null) Modifier.focusRequester(focusReglages) else Modifier,
        )
    }
}

@Composable
private fun Pill(
    icon: ImageVector, label: String, hauteur: Int, onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier,
) {
    var focus by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(hauteur.u).onFocusChanged { focus = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.09f),
            contentColor = Color.White.copy(alpha = 0.85f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0B0D12),
            pressedContainerColor = Color.White.copy(alpha = 0.8f),
            pressedContentColor = Color(0xFF0B0D12),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.u, Color.White.copy(alpha = 0.14f)), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Row(
            Modifier.padding(horizontal = (hauteur * 0.29f).toInt().u).height(hauteur.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size((hauteur * 0.42f).toInt().u))
            AnimatedVisibility(
                visible = focus,
                enter = expandHorizontally(tween(220)) + fadeIn(tween(220)),
                exit = shrinkHorizontally(tween(180)) + fadeOut(tween(120)),
            ) {
                Text(
                    label, fontSize = 13.us, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.padding(start = 8.u, end = 2.u),
                )
            }
        }
    }
}

/** Pastille « Mise à jour » : bleue, libellé toujours visible. */
@Composable
private fun PillMaj(hauteur: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(hauteur.u),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF5AB0FF).copy(alpha = 0.28f),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF5AB0FF),
            focusedContentColor = Color(0xFF04172B),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.u).height(hauteur.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ProjoIcons.MiseAJour, contentDescription = null, modifier = Modifier.size(16.u))
            Text("Mise à jour", fontSize = 13.us, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.u))
        }
    }
}
