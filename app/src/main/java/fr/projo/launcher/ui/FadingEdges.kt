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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/**
 * Fondu des bords d'une rangée horizontale : les cartes s'effacent dans la marge au lieu
 * d'être coupées net.
 *  - à droite : toujours ;
 *  - à gauche : seulement quand la rangée a défilé (apparition animée).
 * Simple masque de transparence (pas de flou) : très léger pour le processeur graphique.
 *
 * Le calque du masque déborde de [debord] en haut et en bas : la lueur et l'agrandissement
 * de la carte sélectionnée ne sont pas coupés net au bord de la rangée.
 */
fun Modifier.fadingEdges(state: LazyListState, largeur: Dp, debord: Dp = 24.dp): Modifier = composed {
    val aDefile by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }
    val gauche by animateFloatAsState(if (aDefile) 1f else 0f, tween(250), label = "fonduGauche")
    this
        // La rangée garde sa taille ; seul le calque ci-dessous est agrandi de « debord »
        .layout { mesurable, contraintes ->
            val d = debord.roundToPx()
            val p = mesurable.measure(contraintes.offset(vertical = 2 * d))
            layout(p.width, p.height - 2 * d) { p.place(0, -d) }
        }
        // Calque séparé : le masque n'agit que sur la rangée, pas sur le fond derrière
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val l = largeur.toPx()
            if (size.width <= 2 * l) return@drawWithContent
            val g = l / size.width
            // Courbe douce (ease) : le fondu se remarque sans couper la carte d'un coup
            val stops = listOf(0f to 0f, 0.3f to 0.18f, 0.6f to 0.55f, 0.85f to 0.88f, 1f to 1f)
            val gaucheStops = stops.map { (x, a) -> x * g to Color.Black.copy(alpha = 1f - gauche * (1f - a)) }
            val droiteStops = stops.reversed().map { (x, a) -> 1f - x * g to Color.Black.copy(alpha = a) }
            drawRect(
                brush = Brush.horizontalGradient(*(gaucheStops + droiteStops).toTypedArray()),
                blendMode = BlendMode.DstIn,
            )
        }
        .layout { mesurable, contraintes ->
            val d = debord.roundToPx()
            val p = mesurable.measure(contraintes.offset(vertical = -2 * d))
            layout(p.width, p.height + 2 * d) { p.place(0, d) }
        }
}
