package fr.projo.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Fondu des bords d'une rangée horizontale : les cartes s'effacent dans la marge au lieu
 * d'être coupées net.
 *  - à droite : toujours ;
 *  - à gauche : seulement quand la rangée a défilé (apparition animée).
 * Simple masque de transparence (pas de flou) : très léger pour le processeur graphique.
 */
fun Modifier.fadingEdges(state: LazyListState, largeur: Dp): Modifier = composed {
    val aDefile by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }
    val gauche by animateFloatAsState(if (aDefile) 1f else 0f, tween(250), label = "fonduGauche")
    this
        // Calque séparé : le masque n'agit que sur la rangée, pas sur le fond derrière
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val l = largeur.toPx()
            if (size.width <= 2 * l) return@drawWithContent
            val g = l / size.width
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 1f - gauche),
                    g to Color.Black,
                    1f - g to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}
