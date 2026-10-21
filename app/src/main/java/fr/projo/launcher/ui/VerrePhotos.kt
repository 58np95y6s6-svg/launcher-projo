package fr.projo.launcher.ui

import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text

/*
 * Arrière-plan photo de « Verre » : quand une app de streaming est sélectionnée (favori ou
 * « Mes apps »), la photo d'une de ses nouveautés (TMDB, chargeur d'UnePhotos.kt) s'incruste
 * dans le papier peint, en plein écran :
 *  - assombrie et désaturée (jamais éblouissante dans le noir) ;
 *  - fondue dans le papier peint vers le bas et les bords par un masque elliptique : les taches
 *    de couleur de Verre restent visibles autour et transparaissent un peu à travers ;
 *  - ombre douce derrière l'horloge ; les fenêtres de verre s'épaississent (voile « givré »).
 * Aucun flou, aucun calque de plus : une seule opération de dessin par photo (shader de l'image
 * combiné au dégradé du masque). Mémoire : les deux images au plus du chargeur partagé.
 */

/** Présence des photos (0 : papier peint seul, 1 : photo pleinement affichée). Lu au dessin. */
internal fun PhotosUne.presenceVerre(): Float {
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

/** Assombrit (×0,46), désature (55 %) et refroidit très légèrement la photo (bleu de Verre). */
internal fun filtrePhotosVerre(): ColorFilter {
    val k = 0.46f
    val s = 0.55f
    val lr = 0.213f * (1f - s)
    val lg = 0.715f * (1f - s)
    val lb = 0.072f * (1f - s)
    val r = k * 0.97f
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

/** Opacité maximale de la photo : le papier peint transparaît un peu (teinte de Verre). */
private const val VERRE_PHOTO_OPACITE = 0.9f

/**
 * Pinceau de la photo : l'image couvre l'écran (centrée, un peu vers le haut), multipliée par
 * un masque elliptique (opaque en haut au centre, s'efface vers le bas et les bords).
 * Shader de l'image + dégradé radial : de types différents, accéléré sur tous les Android.
 */
private fun pinceauPhotoVerre(image: ImageBitmap, w: Float, h: Float): Brush {
    val bmp = image.asAndroidBitmap()
    val s = maxOf(w / bmp.width, h / bmp.height)
    val m = Matrix().apply {
        setScale(s, s)
        postTranslate((w - bmp.width * s) / 2f, (h - bmp.height * s) * 0.3f)
    }
    val photo = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
    // Ellipse centrée en haut, entre l'horloge et la fenêtre NBA
    val cx = w * 0.54f
    val cy = h * 0.16f
    val rx = w * 0.66f
    val ry = h * 0.82f
    val masque = RadialGradient(
        cx, cy, rx,
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xC7FFFFFF.toInt(), 0x59FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.46f, 0.66f, 0.84f, 1f),
        Shader.TileMode.CLAMP,
    ).apply { setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, cx, cy) }) }
    return ShaderBrush(ComposeShader(photo, masque, PorterDuff.Mode.DST_IN))
}

/**
 * Photos incrustées dans le papier peint (à poser juste au-dessus de lui, sous le contenu).
 * Pinceaux recalculés seulement quand la photo change ; le fondu est lu au dessin.
 */
@Composable
internal fun VerrePhotoFond(p: PhotosUne, filtre: ColorFilter) {
    Spacer(
        Modifier
            .fillMaxSize()
            // Calque à part : le fondu ne redessine que cette couche (le papier peint reste en cache)
            .graphicsLayer()
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                // Lu ici : le cache est refait quand la photo change (pas à chaque image)
                val cour = p.courante?.let { pinceauPhotoVerre(it.image, w, h) }
                val prec = p.precedente?.let { pinceauPhotoVerre(it.image, w, h) }
                // Ombre douce derrière l'horloge et le nom de l'app (texte blanc lisible)
                val ombre = Brush.radialGradient(
                    0f to Color(0xFF05031A).copy(alpha = 0.42f),
                    0.55f to Color(0xFF05031A).copy(alpha = 0.2f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.19f, h * 0.27f),
                    radius = w * 0.36f,
                )
                onDrawBehind {
                    if (cour == null && prec == null) return@onDrawBehind
                    val f = p.fondu.value
                    if (prec != null) drawRect(prec, alpha = VERRE_PHOTO_OPACITE * (if (cour == null) 1f - f else 1f), colorFilter = filtre)
                    if (cour != null) drawRect(cour, alpha = VERRE_PHOTO_OPACITE * f, colorFilter = filtre)
                    drawRect(ombre, alpha = p.presenceVerre())
                }
            },
    )
}

/** Légende discrète de la photo affichée, et l'attribution TMDB obligatoire. */
@Composable
internal fun LegendeVerrePhotos(p: PhotosUne, modifier: Modifier) {
    val photo = p.courante ?: p.precedente ?: return
    val entrante = p.courante != null
    Row(
        modifier
            .testTag(if (entrante) "verre-photo" else "verre-photo-sortante")
            .graphicsLayer { alpha = if (entrante) p.fondu.value else 1f - p.fondu.value },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            photo.legende, color = Color.White.copy(alpha = 0.8f), fontSize = 11.us, fontWeight = FontWeight.SemiBold,
            lineHeight = 14.us, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 380.u),
        )
        Text(
            "  ·  Données : TMDB", color = Color.White.copy(alpha = 0.5f), fontSize = 10.us, fontWeight = FontWeight.Medium,
            lineHeight = 14.us, maxLines = 1,
        )
    }
}
