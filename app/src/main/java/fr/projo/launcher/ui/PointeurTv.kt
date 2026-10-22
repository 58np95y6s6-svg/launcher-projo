package fr.projo.launcher.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemShape
import androidx.tv.material3.Surface

/*
 * Composants tv-material activables À LA TÉLÉCOMMANDE ET AU POINTEUR (voir Pointeur.kt).
 * Mêmes paramètres et même rendu que androidx.tv.material3.Surface(onClick = …) et ListItem :
 * ils y ajoutent seulement Modifier.cliquable, posé avant leur partie focalisable.
 * Toujours les utiliser à la place des originaux (PointeurTest vérifie qu'aucun fichier
 * n'importe plus directement Surface ni ListItem de tv-material).
 */

/** Surface tv-material cliquable : OK à la télécommande, clic ou toucher, appui long / clic droit. */
@Composable
fun SurfaceCliquable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(),
    colors: ClickableSurfaceColors = ClickableSurfaceDefaults.colors(),
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(),
    border: ClickableSurfaceBorder = ClickableSurfaceDefaults.border(),
    glow: ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(),
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.cliquable(onClick, onLongClick),
        onLongClick = onLongClick,
        shape = shape,
        colors = colors,
        scale = scale,
        border = border,
        glow = glow,
        content = content,
    )
}

/** Ligne de liste tv-material (menus, dialogues) : OK, clic ou toucher. */
@Composable
fun LigneCliquable(
    selected: Boolean,
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    shape: ListItemShape = ListItemDefaults.shape(),
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = headlineContent,
        modifier = modifier.cliquable(onClick, onLongClick),
        onLongClick = onLongClick,
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        shape = shape,
        colors = colors,
    )
}
