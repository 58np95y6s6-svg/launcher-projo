package fr.projo.launcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.tv.material3.Text
import java.util.Calendar

/*
 * Arrière-plan photo de « Console » : façon accueil de console de salon, où le jeu sélectionné
 * remplit l'écran. Quand une app de streaming est sélectionnée (favori ou « Mes apps »), la
 * photo d'une de ses nouveautés (TMDB, chargeur partagé d'UnePhotos.kt) remplace en fondu le
 * fond « cinéma » (lumière et rubans de la couleur de l'app), en plein écran :
 *  - assombrie, désaturée et très légèrement refroidie (bleu nuit de Console), plus sombre
 *    encore le soir (19 h – 7 h) : jamais éblouissante dans le noir ;
 *  - sous les mêmes voiles bleu nuit que le fond cinéma (ConsoleVoiles) : grand titre à
 *    gauche, barre du haut, ombre ovale derrière les boutons et l'heure, « Mes apps » en bas ;
 *    plus un léger vignettage du bord droit (la photo est cadrée, ses bords n'éblouissent pas) ;
 *  - le grand titre, les tuiles (logos) et la carte NBA restent par-dessus, inchangés ;
 *  - légende discrète à droite de « Mes apps » (« Nouveau sur Netflix · … · Données : TMDB »).
 * Sinon (autre app, bouton, module, bibliothèque, sans clé, hors ligne, réglage coupé), le fond
 * cinéma ne change pas.
 * Aucun flou, aucun calque hors écran : un calque à part (le fondu ne redessine que lui, le fond
 * cinéma reste en cache) où la progression du fondu n'est lue qu'au dessin. Mémoire : les deux
 * photos au plus du chargeur partagé, décodées à la largeur de l'écran (1280 px au plus).
 */

/** Mode Soir imposé (tests et captures) ; null : selon l'heure (19 h – 7 h). */
internal object SoirConsole {
    var impose: Boolean? = null
}

private fun soirConsole(): Boolean = SoirConsole.impose ?:
    Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it >= 19 || it < 7 }

/**
 * Assombrit (×0,46 le jour, ×0,36 le soir), désature (50 % de la couleur gardée) et refroidit
 * très légèrement la photo (bleu nuit de Console).
 */
internal fun filtrePhotosConsole(soir: Boolean): ColorFilter {
    val k = if (soir) 0.36f else 0.46f
    val s = 0.5f
    val lr = 0.213f * (1f - s)
    val lg = 0.715f * (1f - s)
    val lb = 0.072f * (1f - s)
    val r = k * 0.96f
    val b = k * 1.06f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                r * (lr + s), r * lg, r * lb, 0f, 0f,
                k * lr, k * (lg + s), k * lb, 0f, 0f,
                b * lr, b * lg, b * (lb + s), 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

private val FiltreConsoleJour = filtrePhotosConsole(false)
private val FiltreConsoleSoir = filtrePhotosConsole(true)

/** Présence des photos (0 : fond cinéma seul, 1 : photo pleinement affichée). Lue au dessin. */
internal fun PhotosUne.presenceConsole(): Float {
    val cour = courante
    val prec = precedente
    val f = fondu.value
    return when {
        cour == null && prec == null -> 0f
        prec == null -> f
        cour == null -> 1f - f
        else -> 1f
    }
}

/** Dessine [image] pour couvrir tout l'écran (recadrée au centre, un peu vers le haut). */
private fun DrawScope.photoConsole(image: ImageBitmap, alpha: Float, filtre: ColorFilter) {
    if (alpha <= 0f) return
    val w = size.width
    val h = size.height
    val s = maxOf(w / image.width, h / image.height)
    val sw = (w / s).toInt().coerceIn(1, image.width)
    val sh = (h / s).toInt().coerceIn(1, image.height)
    drawImage(
        image,
        srcOffset = IntOffset((image.width - sw) / 2, ((image.height - sh) * 0.3f).toInt()),
        srcSize = IntSize(sw, sh),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(w.toInt(), h.toInt()),
        alpha = alpha,
        colorFilter = filtre,
        filterQuality = FilterQuality.Medium,
    )
}

/**
 * Photos en grand fond (à poser juste au-dessus du fond cinéma, sous le contenu) : la photo
 * précédente sous la courante qui arrive, puis les voiles de lisibilité, à l'opacité de la photo.
 * Voiles et filtre refaits seulement quand la taille ou la photo change ; le fondu est lu au dessin.
 */
@Composable
internal fun ConsolePhotoFond(p: PhotosUne) {
    // Filtre jour / soir choisi à chaque nouvelle photo (pas à chaque image)
    val courante = p.courante
    val filtre = remember(courante) { if (soirConsole()) FiltreConsoleSoir else FiltreConsoleJour }
    Spacer(
        Modifier
            .fillMaxSize()
            // Calque à part : le fondu ne redessine que cette couche (le fond cinéma reste en cache)
            .graphicsLayer()
            .drawWithCache {
                val w = size.width
                val voiles = ConsoleVoiles(w, size.height)
                // Vignettage doux du bord droit (le gauche est déjà sous le voile du grand titre)
                val bordDroit = Brush.horizontalGradient(
                    0f to ConsoleVoile.copy(alpha = 0f),
                    0.35f to ConsoleVoile.copy(alpha = 0.10f),
                    0.7f to ConsoleVoile.copy(alpha = 0.26f),
                    1f to ConsoleVoile.copy(alpha = 0.38f),
                    startX = 0.8f * w,
                    endX = w,
                )
                onDrawBehind {
                    val cour = p.courante
                    val prec = p.precedente
                    if (cour == null && prec == null) return@onDrawBehind
                    val f = p.fondu.value
                    if (prec != null) photoConsole(prec.image, if (cour == null) 1f - f else 1f, filtre)
                    if (cour != null) photoConsole(cour.image, f, filtre)
                    val presence = p.presenceConsole()
                    drawRect(bordDroit, alpha = presence)
                    voiles.dessiner(this, presence)
                }
            },
    )
}

/** Légende discrète de la photo affichée, et l'attribution TMDB obligatoire. */
@Composable
internal fun LegendeConsolePhotos(p: PhotosUne, modifier: Modifier) {
    val photo = p.courante ?: p.precedente ?: return
    val entrante = p.courante != null
    Row(
        modifier
            .testTag(if (entrante) "console-photo" else "console-photo-sortante")
            .graphicsLayer { alpha = if (entrante) p.fondu.value else 1f - p.fondu.value },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            photo.legende, color = ConsoleEncre.copy(alpha = 0.78f), fontSize = 11.us, fontWeight = FontWeight.Medium,
            lineHeight = 14.us, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 400.u),
        )
        Text(
            "  ·  Données : TMDB", color = ConsoleEncre.copy(alpha = 0.5f), fontSize = 10.us,
            lineHeight = 14.us, maxLines = 1,
        )
    }
}
