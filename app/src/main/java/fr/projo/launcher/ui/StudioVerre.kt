package fr.projo.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

// ------------------------------------------------------------------ palette « Studio »

/** Encre des textes (gris presque noir d'Apple). */
internal val StudioEncre = Color(0xFF1C1C1E)
internal val StudioGris = Color(0xFF6E6E73)
internal val StudioGrisClair = Color(0xFF9A9AA1)
/** Contour fin des matières de verre. */
internal val StudioContour = Color(0xFF000000).copy(alpha = 0.07f)

/** Fond : dégradé blanc → gris très clair, deux lumières douces (aucun flou en temps réel). */
internal fun Modifier.fondStudio(): Modifier = drawWithCache {
    val degrade = Brush.verticalGradient(listOf(Color(0xFFF6F7F9), Color(0xFFE9EBEF), Color(0xFFE2E4E9)))
    val lumiere = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)),
        center = Offset(size.width * 0.22f, size.height * 0.05f), radius = size.width * 0.55f,
    )
    val froid = Brush.radialGradient(
        listOf(Color(0xFFD7DCE6).copy(alpha = 0.7f), Color(0xFFD7DCE6).copy(alpha = 0f)),
        center = Offset(size.width * 0.92f, size.height * 0.95f), radius = size.width * 0.5f,
    )
    onDrawBehind {
        drawRect(degrade)
        drawRect(lumiere)
        drawRect(froid)
    }
}

/**
 * Ombre douce sous une forme arrondie : quelques calques de plus en plus larges et
 * transparents (pas de flou). [force] : 0 au repos, 1 sélectionné (ombre plus large et basse).
 */
internal fun Modifier.ombreStudio(rayon: Dp, repos: Float = 0.45f, force: () -> Float = { 0f }): Modifier = drawBehind {
    val r = rayon.toPx()
    val f = force()
    val k = repos + (1f - repos) * f
    if (k <= 0f) return@drawBehind
    val pas = (1.6f + 1.4f * f) * density
    for (i in 1..6) {
        val e = i * pas
        drawRoundRect(
            Color.Black.copy(alpha = 0.018f * k),
            topLeft = Offset(-e * 0.8f, -e * 0.5f + i * pas * 0.9f),
            size = Size(size.width + e * 1.6f, size.height + e * 1.1f),
            cornerRadius = CornerRadius(r + e),
        )
    }
}

/**
 * Matière de verre clair : remplissage blanc translucide en dégradé, reflet en haut, contour
 * fin et ombre douce. [opacite] : 1 = verre normal, plus pour un verre plus laiteux.
 */
internal fun Modifier.verreStudio(rayon: Dp, opacite: Float = 1f, ombre: Float = 1f): Modifier =
    this
        .ombreStudio(rayon, repos = 0.55f * ombre)
        .drawWithCache {
            val r = CornerRadius(rayon.toPx())
            val remplissage = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = (0.80f * opacite).coerceAtMost(1f)),
                    Color.White.copy(alpha = (0.52f * opacite).coerceAtMost(1f)),
                )
            )
            val reflet = Brush.verticalGradient(
                0f to Color.White, 0.5f to Color.White.copy(alpha = 0.35f), 1f to Color.White.copy(alpha = 0.7f),
            )
            val un = density
            onDrawBehind {
                drawRoundRect(remplissage, cornerRadius = r)
                // Liseré lumineux (bord supérieur plus clair) puis contour gris très fin
                drawRoundRect(
                    reflet, topLeft = Offset(un, un), size = Size(size.width - 2 * un, size.height - 2 * un),
                    cornerRadius = CornerRadius(r.x - un), style = Stroke(1.2f * un),
                )
                drawRoundRect(StudioContour, cornerRadius = r, style = Stroke(un))
            }
        }

/**
 * Cadre focalisable de Studio : sélectionné, il se soulève (agrandissement [levee], ombre
 * plus marquée) et prend un liseré blanc. OK → [onClick] ; appui long ou Menu → [onMenu].
 */
@Composable
internal fun StudioCadre(
    onClick: () -> Unit,
    largeur: Dp,
    hauteur: Dp,
    rayon: Dp,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null,
    levee: Float = 1.08f,
    fond: Color = Color.White.copy(alpha = 0.5f),
    fondFocus: Color = fond,
    ombreRepos: Float = 0.45f,
    onFocus: (Boolean) -> Unit = {},
    contenu: @Composable BoxScope.(focus: Boolean) -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focus) 1f else 0f, tween(200), label = "levee")
    val forme = RoundedCornerShape(rayon)
    Surface(
        onClick = onClick,
        onLongClick = onMenu,
        modifier = modifier
            .graphicsLayer { val s = 1f + (levee - 1f) * f; scaleX = s; scaleY = s }
            .ombreStudio(rayon, ombreRepos) { f }
            .size(largeur, hauteur)
            .onFocusChanged {
                focus = it.hasFocus
                onFocus(it.hasFocus)
            }
            .then(
                if (onMenu != null) Modifier.onPreviewKeyEvent {
                    if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) { onMenu(); true } else false
                } else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fond, contentColor = StudioEncre,
            focusedContainerColor = fondFocus, focusedContentColor = StudioEncre,
            pressedContainerColor = fondFocus, pressedContentColor = StudioEncre,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.u, StudioContour), shape = forme),
            focusedBorder = Border(BorderStroke(2.u, Color.White), shape = forme),
        ),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Box(Modifier.fillMaxSize().clip(forme), contentAlignment = Alignment.Center) { contenu(focus) }
    }
}

/** Pastille ronde de l'en-tête (Wi-Fi, Sources, Réglages) : le nom apparaît à la sélection. */
@Composable
internal fun StudioPastille(
    icone: ImageVector,
    libelle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLong: (() -> Unit)? = null,
    miseEnAvant: Boolean = false,
) {
    var focus by remember { mutableStateOf(false) }
    val h = 30.u
    Surface(
        onClick = onClick,
        onLongClick = onLong,
        modifier = modifier
            .testTag("rapide:$libelle")
            .ombreStudio(15.u, repos = 0.35f) { if (focus) 1f else 0f }
            .height(h)
            .onFocusChanged { focus = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (miseEnAvant) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.78f),
            contentColor = if (miseEnAvant) Color.White else StudioEncre.copy(alpha = 0.8f),
            focusedContainerColor = StudioEncre, focusedContentColor = Color.White,
            pressedContainerColor = StudioEncre, pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.u, StudioContour), shape = CircleShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Row(Modifier.height(h).padding(horizontal = 8.u), verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, contentDescription = libelle, modifier = Modifier.size(14.u))
            AnimatedVisibility(
                visible = focus || miseEnAvant,
                enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
                exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120)),
            ) {
                Text(
                    libelle, fontSize = 11.us, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.padding(start = 6.u, end = 2.u),
                )
            }
        }
    }
}

/** Tuile d'entrée ou de raccourci (icône et nom), en verre ; sélectionnée : blanche et soulevée. */
@Composable
internal fun StudioRaccourci(
    icone: ImageVector,
    libelle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null,
    onFocus: (Boolean) -> Unit = {},
) {
    StudioCadre(
        onClick = onClick, onMenu = onMenu, largeur = 92.u, hauteur = 60.u, rayon = 12.u,
        modifier = modifier, levee = 1.1f, ombreRepos = 0.4f,
        fond = Color.White.copy(alpha = 0.55f), fondFocus = Color.White, onFocus = onFocus,
    ) { focus ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icone, contentDescription = null, tint = if (focus) StudioEncre else StudioEncre.copy(alpha = 0.78f),
                modifier = Modifier.size(18.u),
            )
            Text(
                libelle, color = if (focus) StudioEncre else StudioGris, fontSize = 10.us,
                fontWeight = if (focus) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = fr.projo.launcher.modules.ligne(13.us),
                modifier = Modifier.padding(top = 6.u, start = 4.u, end = 4.u),
            )
        }
    }
}

/** Style de la zone de modules de Studio : panneau transparent (le verre est dessiné autour). */
internal fun styleModulesStudio(largeur: Int, hauteur: Int) = NbaStyle(
    largeur = largeur,
    rayon = 20,
    fond = Color.Transparent,
    bordure = Color.Transparent,
    texte = StudioEncre,
    texteDiscret = StudioGris,
    ongletActif = StudioEncre.copy(alpha = 0.07f),
    focusFond = StudioEncre,
    focusTexte = Color.White,
    pastilleFond = Color.White,
    zoneLargeur = largeur,
    zoneHauteur = hauteur,
)
