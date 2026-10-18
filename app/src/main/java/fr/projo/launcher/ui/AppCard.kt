package fr.projo.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppVisual
import fr.projo.launcher.data.IconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Charge (une fois, hors du thread principal) le logo et la couleur d'une app. */
@Composable
fun rememberAppVisual(packageName: String, icons: IconLoader): AppVisual? {
    val visual by produceState(initialValue = icons.cached(packageName), packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { icons.load(packageName) }
    }
    return visual
}

/**
 * Carte d'une app : logo centré sur un fond dégradé de sa couleur dominante.
 * Au focus : agrandissement (~1.1x), contour clair et légère ombre, animés par Compose for TV.
 */
@Composable
fun AppCard(
    app: AppInfo,
    icons: IconLoader,
    /** Paquet dont on affiche le logo (le même que l'app, sauf logo remplacé). */
    iconPackage: String,
    width: Dp,
    height: Dp,
    showLabel: Boolean,
    onFocused: (AppInfo, Color) -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = rememberAppVisual(iconPackage, icons)
    val couleur = visual?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    val forme = RoundedCornerShape(14.u)

    Column(modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            onLongClick = onMenu,
            modifier = Modifier
                .size(width, height)
                .onFocusChanged { if (it.isFocused) onFocused(app, couleur) }
                // Touche « Menu » de la télécommande = même menu que l'appui long
                .onPreviewKeyEvent {
                    if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) { onMenu(); true } else false
                },
            shape = ClickableSurfaceDefaults.shape(forme),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.u, Color.White.copy(alpha = 0.95f)), shape = forme),
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 10.u),
            ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(forme)
                    .background(
                        Brush.verticalGradient(
                            listOf(lerp(couleur, Color.Black, 0.25f), lerp(couleur, Color.Black, 0.62f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val icone = visual?.icon
                if (icone != null) {
                    Image(
                        bitmap = icone,
                        contentDescription = app.label,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier.size(height * 0.48f),
                    )
                } else {
                    // Pas de logo : initiale de l'app
                    Text(app.label.take(1).uppercase(), color = Color.White, fontSize = 28.us, fontWeight = FontWeight.Light)
                }
            }
        }
        if (showLabel) {
            Text(
                app.label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.us,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.u),
            )
        }
    }
}

/** Carte texte (ex. « Apps masquées », aide quand il n'y a aucun favori). */
@Composable
fun TextCard(
    text: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val forme = RoundedCornerShape(14.u)
    Surface(
        onClick = onClick,
        modifier = modifier.size(width, height).onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White.copy(alpha = 0.8f),
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.u, Color.White.copy(alpha = 0.15f)), shape = forme),
            focusedBorder = Border(BorderStroke(2.u, Color.White.copy(alpha = 0.95f)), shape = forme),
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(12.u), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 13.us, textAlign = TextAlign.Center)
        }
    }
}
