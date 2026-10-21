package fr.projo.launcher.ui

import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.Text

/*
 * Arrière-plan photo de « Pastel » : quand une app de streaming est sélectionnée (favori ou
 * « Mes apps »), la photo d'une de ses nouveautés (TMDB, chargeur partagé d'UnePhotos.kt)
 * apparaît en fondu dans le fond lilas, en une large bande douce derrière le nom de l'app et
 * les favoris :
 *  - développée « pastel » : un peu désaturée, ombres relevées vers un prune doux, blancs
 *    sous le lilas du fond (rien n'éblouit, aucun noir profond) ;
 *  - fondue dans le fond par un masque elliptique, sans bord : nulle derrière l'horloge,
 *    la salutation, le début du nom de l'app et « Mes apps » (texte foncé sur le lilas) ;
 *  - légende dans une pastille laiteuse, au bout de la ligne du nom de l'app.
 * Aucun flou ni calque hors écran : un seul dessin par photo (shader de l'image combiné au
 * dégradé du masque, de types différents : accéléré sur tous les Android), filtre de couleur
 * en matrice. Les fondus sont lus au dessin, dans un calque à part ; rien n'est composé
 * sans photo. Mémoire : les deux photos du chargeur partagé, rien de plus.
 */

/** Haut de l'image (u) : toute la largeur de l'écran, descendue pour que le sujet tombe dans la bande. */
private const val PP_HAUT = 60f

/** Masque elliptique (u) : centre et demi-axes ; nul hors de l'ellipse. */
private const val PP_CX = 720f
private const val PP_CY = 306f
private const val PP_RX = 540f
private const val PP_RY = 194f

/** Opacité maximale de la photo : le fond lilas transparaît à peine. */
private const val PP_OPACITE = 0.92f

/**
 * Développement « pastel » : saturation gardée à 74 %, noirs relevés vers un prune doux,
 * blancs ramenés un peu sous le lilas du fond (contraste réduit, teinte du thème).
 */
internal fun filtrePhotosPastel(): ColorFilter {
    val s = 0.74f
    val lr = 0.213f * (1f - s)
    val lg = 0.715f * (1f - s)
    val lb = 0.072f * (1f - s)
    // Noir et blanc d'arrivée (0..255)
    val noir = floatArrayOf(62f, 50f, 80f)
    val blanc = floatArrayOf(226f, 216f, 234f)
    val k = FloatArray(3) { (blanc[it] - noir[it]) / 255f }
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                k[0] * (lr + s), k[0] * lg, k[0] * lb, 0f, noir[0],
                k[1] * lr, k[1] * (lg + s), k[1] * lb, 0f, noir[1],
                k[2] * lr, k[2] * lg, k[2] * (lb + s), 0f, noir[2],
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

private val FiltrePastel = filtrePhotosPastel()

/**
 * Pinceau d'une photo : l'image couvre la largeur [w] (16:9, haut en [PP_HAUT]), multipliée par
 * le masque elliptique ([k] : pixels par unité). Hors de l'image (au-dessus), le masque est nul.
 */
private fun pinceauPhotoPastel(image: ImageBitmap, w: Float, k: Float): Brush {
    val bmp = image.asAndroidBitmap()
    val zoneH = w * 9f / 16f
    val s = maxOf(w / bmp.width, zoneH / bmp.height)
    val m = Matrix().apply {
        setScale(s, s)
        postTranslate((w - bmp.width * s) / 2f, PP_HAUT * k + (zoneH - bmp.height * s) / 2f)
    }
    val photo = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(m)
        if (Build.VERSION.SDK_INT >= 31) filterMode = BitmapShader.FILTER_MODE_LINEAR
    }
    val cx = PP_CX * k
    val cy = PP_CY * k
    val rx = PP_RX * k
    val ry = PP_RY * k
    // Courbe douce (ease) : aucun bord ne se devine
    val masque = RadialGradient(
        cx, cy, rx,
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xD1FFFFFF.toInt(), 0x80FFFFFF.toInt(), 0x2EFFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.4f, 0.58f, 0.72f, 0.86f, 1f),
        Shader.TileMode.CLAMP,
    ).apply { setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, cx, cy) }) }
    return ShaderBrush(ComposeShader(photo, masque, PorterDuff.Mode.DST_IN))
}

/**
 * Photos incrustées dans le fond lilas (à poser juste au-dessus de lui, sous le contenu).
 * Rien n'est composé sans photo ; pinceaux refaits seulement quand la photo change ; le fondu
 * est lu au dessin, dans un calque à part (le fond reste en cache).
 */
@Composable
internal fun PastelPhotoFond(p: PhotosUne) {
    if (p.courante == null && p.precedente == null) return
    val k = with(LocalDensity.current) { 1.u.toPx() }
    Spacer(
        Modifier
            .fillMaxSize()
            .testTag("pastel:fond-photo")
            .graphicsLayer()
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                // Lu ici : le cache est refait quand la photo change (pas à chaque image)
                val cour = p.courante?.let { pinceauPhotoPastel(it.image, w, k) }
                val prec = p.precedente?.let { pinceauPhotoPastel(it.image, w, k) }
                onDrawBehind {
                    val f = p.fondu.value
                    if (prec != null) drawRect(prec, alpha = PP_OPACITE * (if (cour == null) 1f - f else 1f), colorFilter = FiltrePastel)
                    if (cour != null) drawRect(cour, alpha = PP_OPACITE * f, colorFilter = FiltrePastel)
                }
            },
    )
}

/**
 * Légende de la photo affichée (« Nouveau sur Netflix · The Storm ») et l'attribution TMDB
 * obligatoire, dans une pastille laiteuse comme celle de la rubrique : lisible sur la photo.
 */
@Composable
internal fun LegendePhotosPastel(p: PhotosUne, modifier: Modifier) {
    val photo = p.courante ?: p.precedente ?: return
    val entrante = p.courante != null
    // « Nouveau sur Netflix » en violet, puis le titre
    val legende = remember(photo) {
        val service = photo.legende.substringBefore(" · ")
        val titre = photo.legende.substringAfter(" · ", "")
        buildAnnotatedString {
            withStyle(SpanStyle(color = PastelPrimaire)) { append(service) }
            if (titre.isNotEmpty()) append(" · $titre")
        }
    }
    Row(
        modifier
            .testTag(if (entrante) "pastel-photo" else "pastel-photo-sortante")
            .graphicsLayer { alpha = if (entrante) p.fondu.value else 1f - p.fondu.value }
            .height(24.u)
            .background(PastelBlanc.copy(alpha = 0.86f), RoundedCornerShape(12.u))
            .padding(horizontal = 11.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            legende, color = PastelEncre, fontSize = 11.us, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle.Default,
            modifier = Modifier.widthIn(max = 300.u),
        )
        Text(
            "  ·  Données : TMDB", color = PastelEncreDouce, fontSize = 10.us, fontWeight = FontWeight.Medium,
            maxLines = 1, style = TextStyle.Default,
        )
    }
}
