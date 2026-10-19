package fr.projo.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** Couleurs de la lueur signature : violet → bleu → cyan, avec un reflet presque blanc. */
private val COULEURS_LUEUR = intArrayOf(
    0xFF7C5CFF.toInt(), 0xFF5B6CFF.toInt(), 0xFF3B82F6.toInt(), 0xFF22D3EE.toInt(),
    0xFFD9FBFF.toInt(), 0xFF22D3EE.toInt(), 0xFF3B82F6.toInt(), 0xFF8B5CF6.toInt(), 0xFF7C5CFF.toInt(),
)
private val POSITIONS_LUEUR = floatArrayOf(0f, 0.153f, 0.306f, 0.458f, 0.514f, 0.569f, 0.708f, 0.861f, 1f)

/**
 * Lueur autour de l'élément sélectionné (anneau en dégradé violet → cyan qui tourne
 * lentement + halo doux). Sans flou : le halo est fait de contours de plus en plus larges
 * et transparents, donc léger pour le projecteur.
 *
 * @param intensite 0 = rien, 1 = lueur complète (à animer avec animateFloatAsState).
 * @param rayon rayon des coins de l'élément.
 * @param tourne true pour animer la rotation (seulement l'élément sélectionné, pour économiser).
 */
@Composable
fun Modifier.lueurFocus(intensite: Float, rayon: Dp, tourne: Boolean = intensite > 0f): Modifier {
    // Rotation du dégradé : un tour en 4 s, seulement sur l'élément sélectionné
    val angle: State<Float> = if (tourne) {
        rememberInfiniteTransition(label = "lueur").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
            label = "angle",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val epaisseur = 2.u
    val ecart = 3.u
    val halo = 6.u
    return this.drawBehind {
        if (intensite <= 0.01f) return@drawBehind
        val cx = size.width / 2f
        val cy = size.height / 2f
        val a = angle.value
        val brosse = object : ShaderBrush() {
            override fun createShader(size: Size): Shader =
                android.graphics.SweepGradient(cx, cy, COULEURS_LUEUR, POSITIONS_LUEUR).apply {
                    setLocalMatrix(android.graphics.Matrix().apply { setRotate(a, cx, cy) })
                }
        }
        val r = rayon.toPx()
        // Halo : 24 fines bandes contiguës dont l'opacité décroît en douceur (courbe en cloche)
        // → un dégradé continu, sans anneaux visibles
        val bande = halo.toPx() / 6f
        val depart = ecart.toPx() + epaisseur.toPx()
        for (i in 0 until 24) {
            val o = depart + bande * (i + 0.5f)
            val x = i / 9f
            drawRoundRect(
                brush = brosse,
                topLeft = Offset(-o, -o),
                size = Size(size.width + 2 * o, size.height + 2 * o),
                cornerRadius = CornerRadius(r + o),
                style = Stroke(width = bande * 1.15f),
                alpha = intensite * 0.38f * kotlin.math.exp(-x * x),
            )
        }
        // Anneau net
        val o = ecart.toPx() + epaisseur.toPx() / 2f
        drawRoundRect(
            brush = brosse,
            topLeft = Offset(-o, -o),
            size = Size(size.width + 2 * o, size.height + 2 * o),
            cornerRadius = CornerRadius(r + o),
            style = Stroke(width = epaisseur.toPx()),
            alpha = intensite,
        )
    }
}

/** Designs qui utilisent la lueur comme effet de sélection des cartes d'apps. */
val Design.avecLueur: Boolean get() = this == Design.AURORE || this == Design.GRAPHITE
