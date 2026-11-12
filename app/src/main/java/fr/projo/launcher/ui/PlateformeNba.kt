package fr.projo.launcher.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import fr.projo.launcher.R

/*
 * Design « NBA » : ce qui dépend d'Android (polices, flou, ressource de la carte).
 */

/** Oswald 600 / 700 (titres, chiffres), police OFL livrée avec l'app. */
internal val NbaTitre = FontFamily(
    Font(R.font.nba_titre_demi, FontWeight.SemiBold),
    Font(R.font.nba_titre_gras, FontWeight.Bold),
)

/** Barlow Condensed 600 / 700 / 800 (libellés), police OFL livrée avec l'app. */
internal val NbaEtroit = FontFamily(
    Font(R.font.nba_etroit_demi, FontWeight.SemiBold),
    Font(R.font.nba_etroit_gras, FontWeight.Bold),
    Font(R.font.nba_etroit_noir, FontWeight.ExtraBold),
)

/**
 * Flou gaussien d'écart type [sigma] (px du repère courant : il suit l'échelle du dessin) sur
 * ce pinceau. Dessin logiciel dans une image en mémoire seulement (ombres et lueurs de la carte).
 */
internal fun Paint.flouNba(sigma: Float): Paint = apply {
    // BlurMaskFilter prend un rayon : sigma = 0,57735 × rayon + 0,5
    asFrameworkPaint().maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
}

/** Géométrie de la carte (res/raw/carte_nba.json). Fonction bloquante. */
internal fun lireCarteNba(context: Context): String =
    context.resources.openRawResource(R.raw.carte_nba).use { it.readBytes().toString(Charsets.UTF_8) }
