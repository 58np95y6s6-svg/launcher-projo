package fr.projo.launcher.modules.meteo

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Ciel d'après les codes météo WMO d'Open-Meteo. */
enum class CielMeteo(val libelle: String) {
    CLAIR("Ciel dégagé"),
    PEU_NUAGEUX("Éclaircies"),
    COUVERT("Couvert"),
    BROUILLARD("Brouillard"),
    BRUINE("Bruine"),
    PLUIE("Pluie"),
    AVERSES("Averses"),
    NEIGE("Neige"),
    ORAGE("Orage");

    companion object {
        fun de(code: Int): CielMeteo = when (code) {
            0 -> CLAIR
            1, 2 -> PEU_NUAGEUX
            3 -> COUVERT
            45, 48 -> BROUILLARD
            in 51..57 -> BRUINE
            in 61..67 -> PLUIE
            in 80..82 -> AVERSES
            in 71..77, 85, 86 -> NEIGE
            in 95..99 -> ORAGE
            else -> COUVERT
        }
    }
}

private val Soleil = Color(0xFFFFC94D)
private val Lune = Color(0xFFF1E9C6)
private val Goutte = Color(0xFF5AB0FF)
private val Eclair = Color(0xFFFFD23F)

/**
 * Icône du temps, dessinée en vectoriel (aucune image réseau). [encre] : couleur du texte du
 * design (sert à choisir des nuages clairs sur fond sombre, gris sur fond clair).
 */
@Composable
fun IconeMeteo(code: Int, jour: Boolean, encre: Color, modifier: Modifier) {
    val ciel = CielMeteo.de(code)
    val fonce = encre.luminance() < 0.5f
    val nuage = if (fonce) Color(0xFFA79FBA) else Color(0xFFE4E8F0)
    val nuageSombre = if (fonce) Color(0xFF857C99) else Color(0xFFB7BFCC)
    val neige = if (fonce) Color(0xFF6FA8DC) else Color(0xFFDDF0FF)
    Canvas(modifier) {
        val s = minOf(size.width, size.height)
        translate((size.width - s) / 2, (size.height - s) / 2) {
            when (ciel) {
                CielMeteo.CLAIR -> if (jour) soleil(Offset(s * .5f, s * .5f), s * .2f, s) else lune(Offset(s * .5f, s * .5f), s * .28f)
                CielMeteo.PEU_NUAGEUX -> {
                    if (jour) soleil(Offset(s * .36f, s * .36f), s * .15f, s * .75f) else lune(Offset(s * .38f, s * .36f), s * .2f)
                    nuage(s, nuage, dy = s * .08f)
                }
                CielMeteo.COUVERT -> nuage(s, nuage)
                CielMeteo.BROUILLARD -> {
                    nuage(s, nuage, dy = -s * .1f)
                    for (k in 0..2) {
                        val y = s * (.74f + k * .09f)
                        drawLine(nuageSombre, Offset(s * (.2f + k * .04f), y), Offset(s * (.82f - k * .04f), y), s * .045f, StrokeCap.Round)
                    }
                }
                CielMeteo.BRUINE, CielMeteo.PLUIE, CielMeteo.AVERSES -> {
                    if (ciel == CielMeteo.AVERSES && jour) soleil(Offset(s * .3f, s * .3f), s * .12f, s * .6f)
                    nuage(s, nuageSombre, dy = -s * .1f)
                    val n = if (ciel == CielMeteo.BRUINE) 4 else 3
                    for (k in 0 until n) {
                        val x = s * (.32f + k * (.36f / (n - 1)))
                        if (ciel == CielMeteo.BRUINE) {
                            drawCircle(Goutte, s * .03f, Offset(x, s * .8f))
                        } else {
                            drawLine(Goutte, Offset(x + s * .03f, s * .72f), Offset(x - s * .03f, s * .88f), s * .055f, StrokeCap.Round)
                        }
                    }
                }
                CielMeteo.NEIGE -> {
                    nuage(s, nuage, dy = -s * .1f)
                    for (k in 0..2) drawCircle(neige, s * .045f, Offset(s * (.33f + k * .17f), s * (.78f + (k % 2) * .07f)))
                }
                CielMeteo.ORAGE -> {
                    nuage(s, nuageSombre, dy = -s * .12f)
                    val p = Path().apply {
                        moveTo(s * .54f, s * .58f)
                        lineTo(s * .4f, s * .78f)
                        lineTo(s * .5f, s * .78f)
                        lineTo(s * .44f, s * .95f)
                        lineTo(s * .64f, s * .7f)
                        lineTo(s * .53f, s * .7f)
                        lineTo(s * .6f, s * .58f)
                        close()
                    }
                    drawPath(p, Eclair)
                }
            }
        }
    }
}

private fun DrawScope.soleil(c: Offset, r: Float, s: Float) {
    drawCircle(Soleil, r, c)
    for (k in 0 until 8) {
        val a = k * PI.toFloat() / 4
        val d1 = r + s * .07f
        val d2 = r + s * .15f
        drawLine(
            Soleil, Offset(c.x + cos(a) * d1, c.y + sin(a) * d1), Offset(c.x + cos(a) * d2, c.y + sin(a) * d2),
            s * .05f, StrokeCap.Round,
        )
    }
}

private fun DrawScope.lune(c: Offset, r: Float) {
    val disque = Path().apply { addOval(Rect(c, r)) }
    val ombre = Path().apply { addOval(Rect(Offset(c.x + r * .55f, c.y - r * .4f), r)) }
    drawPath(Path.combine(PathOperation.Difference, disque, ombre), Lune)
}

/** Nuage (cercles + base arrondie), d'une seule couleur opaque. */
private fun DrawScope.nuage(s: Float, couleur: Color, dy: Float = 0f) {
    drawCircle(couleur, s * .17f, Offset(s * .36f, s * .55f + dy))
    drawCircle(couleur, s * .22f, Offset(s * .56f, s * .46f + dy))
    drawCircle(couleur, s * .15f, Offset(s * .74f, s * .6f + dy))
    drawRoundRect(
        couleur, Offset(s * .19f, s * .55f + dy), Size(s * .7f, s * .2f), CornerRadius(s * .1f),
    )
}
