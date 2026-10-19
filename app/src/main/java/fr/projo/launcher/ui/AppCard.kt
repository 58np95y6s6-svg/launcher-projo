package fr.projo.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppVisual
import fr.projo.launcher.data.IconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Charge (une fois, hors du thread principal) la carte et la couleur d'une app. */
@Composable
fun rememberAppVisual(packageName: String, icons: IconLoader): AppVisual? {
    val visual by produceState(initialValue = icons.cached(packageName), packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { icons.load(packageName) }
    }
    return visual
}

/** Icône carrée de l'app (null pour la tuile HDMI ou en cas d'échec). */
@Composable
fun rememberAppIcon(packageName: String, icons: IconLoader): ImageBitmap? {
    val icone by produceState(initialValue = icons.cachedIcon(packageName), packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { icons.loadIcon(packageName) }
    }
    return icone
}

/**
 * Carte d'une app, façon Apple TV : image pleine (fond + logo de l'app), coins très arrondis.
 * Sélectionnée : elle grandit (1.1x), contour clair, ombre douce et reflet lumineux ;
 * son nom apparaît dessous (petites cartes).
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
    val forme = RoundedCornerShape(height * 0.16f)
    var focus by remember { mutableStateOf(false) }
    val reflet by animateFloatAsState(if (focus) 1f else 0f, tween(250), label = "reflet")

    Column(modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            onLongClick = onMenu,
            modifier = Modifier
                .size(width, height)
                .onFocusChanged {
                    focus = it.isFocused
                    if (it.isFocused) onFocused(app, couleur)
                }
                // Touche « Menu » de la télécommande = même menu que l'appui long
                .onPreviewKeyEvent {
                    if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) { onMenu(); true } else false
                },
            shape = ClickableSurfaceDefaults.shape(forme),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.06f),
                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                pressedContainerColor = Color.White.copy(alpha = 0.06f),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f, pressedScale = 1.04f),
            border = ClickableSurfaceDefaults.border(
                // Fin liseré permanent (relief des cartes sombres), contour clair au focus
                border = Border(BorderStroke(1.u, Color.White.copy(alpha = 0.10f)), shape = forme),
                focusedBorder = Border(BorderStroke(2.u, Color.White.copy(alpha = 0.9f)), shape = forme),
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.7f), elevation = 14.u),
            ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(forme)
                    // Reflet lumineux en diagonale sur la carte sélectionnée
                    .drawWithContent {
                        drawContent()
                        if (reflet > 0f) {
                            drawRect(
                                Brush.linearGradient(
                                    0f to Color.White.copy(alpha = 0.22f * reflet),
                                    0.45f to Color.White.copy(alpha = 0.04f * reflet),
                                    1f to Color.Transparent,
                                    start = Offset.Zero,
                                    end = Offset(size.width * 0.8f, size.height),
                                )
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val art = visual?.art
                when {
                    app.isSpecial -> {
                        // Tuile « Source HDMI » : dégradé + icône
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.linearGradient(
                                    listOf(lerp(couleur, Color.White, 0.1f), lerp(couleur, Color.Black, 0.55f))
                                )
                            )
                        )
                        Icon(
                            ProjoIcons.Sources, contentDescription = app.label, tint = Color.White,
                            modifier = Modifier.size(height * 0.40f),
                        )
                    }
                    art != null -> Image(
                        bitmap = art,
                        contentDescription = app.label,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> {
                        Box(Modifier.fillMaxSize().background(lerp(couleur, Color.Black, 0.5f)))
                        Text(
                            app.label.take(1).uppercase(), color = Color.White,
                            fontSize = 28.us, fontWeight = FontWeight.Light,
                        )
                    }
                }
            }
        }
        if (showLabel) {
            // Nom affiché seulement sur la carte sélectionnée (l'espace reste réservé : pas de saut)
            val opacite by animateFloatAsState(if (focus) 1f else 0f, tween(200), label = "nom")
            Text(
                app.label,
                color = LocalDesign.current.colors().texte,
                fontSize = 12.us,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.u).alpha(opacite),
            )
        }
    }
}

/** Carte texte (ex. « Toutes les apps », aide quand il n'y a aucun favori). */
@Composable
fun TextCard(
    text: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val forme = RoundedCornerShape(height * 0.16f)
    val c = LocalDesign.current.colors()
    Surface(
        onClick = onClick,
        modifier = modifier.size(width, height).onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = c.texte.copy(alpha = 0.07f),
            contentColor = c.texte.copy(alpha = 0.75f),
            focusedContainerColor = c.focusFond,
            focusedContentColor = c.focusTexte,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.u, c.texte.copy(alpha = 0.12f)), shape = forme),
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(12.u), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 13.us, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}
