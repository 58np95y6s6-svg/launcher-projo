package fr.projo.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val FondNuit = Color(0xFF06070A)

/**
 * Fond sombre avec une grande lueur colorée (dégradé radial) qui prend la couleur de l'app
 * sélectionnée. Transition douce de 400 ms. Que des dégradés : aucun flou, très léger
 * pour le processeur graphique du projecteur.
 */
@Composable
fun GlowBackground(color: Color, modifier: Modifier = Modifier) {
    val teinte by animateColorAsState(color, animationSpec = tween(400), label = "lueur")
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(FondNuit)
                // Grande lueur en haut à gauche, derrière l'horloge et les favoris
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(teinte.copy(alpha = 0.55f), teinte.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.28f, size.height * 0.42f),
                        radius = size.width * 0.75f,
                    )
                )
                // Reflet discret en bas à droite pour équilibrer
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(teinte.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 1.05f),
                        radius = size.width * 0.5f,
                    )
                )
                // Voile sombre en bas pour la lisibilité de la rangée « Toutes les apps »
                drawRect(
                    Brush.verticalGradient(
                        0.6f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.45f),
                    )
                )
            }
    )
}
