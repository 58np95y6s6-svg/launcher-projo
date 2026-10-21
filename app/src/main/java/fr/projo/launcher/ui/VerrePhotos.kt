package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/*
 * Arrière-plan photo de « Verre » : quand une app de streaming est sélectionnée (favori ou
 * « Mes apps »), la photo d'une de ses nouveautés (TMDB, chargeur d'UnePhotos.kt) s'incruste
 * dans le papier peint, en plein écran :
 *  - assombrie et désaturée (plus encore le soir, de 19 h à 7 h) : jamais éblouissante ;
 *  - fondue dans le papier peint vers le bas et les bords par un masque elliptique : les taches
 *    de couleur de Verre restent visibles autour et transparaissent un peu à travers ;
 *  - ombre douce derrière l'horloge ;
 *  - derrière les fenêtres de verre, la même photo en DÉPOLI (flou sans GPU : réduite à 40 px
 *    puis agrandie) sous un voile sombre : on devine l'image, le texte reste net.
 * Aucun flou GPU, aucun calque hors écran de plus : un dessin par photo (shader de l'image
 * combiné au dégradé du masque). Mémoire : les deux photos du chargeur partagé, plus leurs
 * miniatures floues (160 × 90 px, ~28 Ko chacune).
 */

/** Mode Soir imposé (tests et captures) ; null : selon l'heure (19 h – 7 h). */
internal object SoirVerre {
    var impose: Boolean? = null
}

private fun soirVerre(): Boolean = SoirVerre.impose ?:
    Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it >= 19 || it < 7 }

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

/**
 * Assombrit (×0,46 le jour, ×0,36 le soir), désature (55 %) et refroidit très légèrement la
 * photo (bleu de Verre).
 */
internal fun filtrePhotosVerre(soir: Boolean): ColorFilter {
    val k = if (soir) 0.36f else 0.46f
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

private val FiltreVerreJour = filtrePhotosVerre(false)
private val FiltreVerreSoir = filtrePhotosVerre(true)

/** Opacité maximale de la photo : le papier peint transparaît un peu (teinte de Verre). */
private const val VERRE_PHOTO_OPACITE = 0.9f

/** Largeur de réduction du dépoli (px) : ~48 px d'écran par pixel, flou de verre dépoli. */
private const val VERRE_FLOU = 40

/** Largeur des miniatures floues gardées (px) : réagrandies en bilinéaire, dégradés sans marches. */
private const val VERRE_MINIATURE = 160

/** Voile sombre posé sur le dépoli, sous le verre (lisibilité). */
private val VerreGivre = Color(0xFF100C2A).copy(alpha = 0.42f)

/**
 * Pinceau d'une photo : l'image couvre l'écran [w] × [h] (centrée, un peu vers le haut),
 * multipliée par un masque elliptique (opaque en haut au centre, s'efface vers le bas et les
 * bords). Shader de l'image + dégradé radial : de types différents, accéléré sur tous les Android.
 */
private fun pinceauPhotoVerre(image: ImageBitmap, w: Float, h: Float): Brush {
    val bmp = image.asAndroidBitmap()
    val s = maxOf(w / bmp.width, h / bmp.height)
    val m = Matrix().apply {
        setScale(s, s)
        postTranslate((w - bmp.width * s) / 2f, (h - bmp.height * s) * 0.3f)
    }
    val photo = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(m)
        // Agrandie en bilinéaire (avant Android 12 : selon le pinceau, filtrage déjà actif)
        if (Build.VERSION.SDK_INT >= 31) filterMode = BitmapShader.FILTER_MODE_LINEAR
    }
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
 * Miniature floue de [image] : divisions successives par 2 (chacune fait la moyenne de 4 pixels,
 * sans crénelage) jusqu'à [VERRE_FLOU] px de large, puis réagrandie en bilinéaire à
 * [VERRE_MINIATURE] px (dégradés doux). Hors du fil principal.
 */
private fun miniatureVerre(image: ImageBitmap): ImageBitmap {
    val source = image.asAndroidBitmap()
    var b = source
    while (b.width > VERRE_FLOU) {
        val r = Bitmap.createScaledBitmap(b, (b.width / 2).coerceAtLeast(1), (b.height / 2).coerceAtLeast(1), true)
        if (b !== source) b.recycle()
        b = r
    }
    val h = (b.height * VERRE_MINIATURE / b.width.toFloat()).toInt().coerceAtLeast(1)
    val grande = Bitmap.createScaledBitmap(b, VERRE_MINIATURE, h, true)
    if (b !== source && grande !== b) b.recycle()
    return grande.asImageBitmap()
}

/**
 * Fond photo de Verre : photos du chargeur partagé, filtre du moment, miniatures du dépoli et
 * position de la couche photo (les fenêtres de verre y calent leur dépoli).
 */
internal class FondVerre(val photos: PhotosUne) {
    /** Position et taille de la couche photo dans la fenêtre. */
    var origine = Offset.Zero
    var taille = Size.Zero

    /** Filtre jour / soir, choisi à chaque nouvelle photo (pas à chaque image). */
    var filtre: ColorFilter = if (soirVerre()) FiltreVerreSoir else FiltreVerreJour
        private set

    /** Miniatures des photos affichées (état : le dépoli se redessine quand elle arrive). */
    val miniatures = mutableStateMapOf<PhotoUne, ImageBitmap>()

    /** Pinceaux du dépoli, refaits seulement quand la miniature ou la taille change. */
    private val pinceaux = HashMap<ImageBitmap, Brush>(4)
    private var taillePinceaux = Size.Zero

    fun presence(): Float = photos.presenceVerre()

    /** Nouvelle photo : filtre du moment, miniature (hors du fil principal), on oublie les anciennes. */
    suspend fun preparer(photo: PhotoUne?) {
        filtre = if (soirVerre()) FiltreVerreSoir else FiltreVerreJour
        val gardees = setOfNotNull(photo, photos.precedente)
        for (k in miniatures.keys.toList()) if (k !in gardees) miniatures.remove(k)
        if (photo == null || photo in miniatures) return
        val m = withContext(Dispatchers.Default) { runCatching { miniatureVerre(photo.image) }.getOrNull() } ?: return
        if (photos.courante === photo || photos.precedente === photo) miniatures[photo] = m
    }

    private fun pinceauDepoli(photo: PhotoUne): Brush? {
        val m = miniatures[photo] ?: return null
        if (taille != taillePinceaux) {
            pinceaux.clear()
            taillePinceaux = taille
        }
        if (pinceaux.size > 2) pinceaux.keys.retainAll(miniatures.values.toSet())
        return pinceaux.getOrPut(m) { pinceauPhotoVerre(m, taille.width, taille.height) }
    }

    /**
     * Dépoli d'une fenêtre de verre placée en [position] (dans la fenêtre), de coins [coin] :
     * photo floue aux mêmes place, masque et opacité que la photo nette, puis voile sombre.
     */
    fun dessinerDepoli(scope: DrawScope, position: Offset, coin: CornerRadius) = with(scope) {
        val g = presence()
        if (g <= 0.005f || taille.width <= 0f) return@with
        val cour = photos.courante
        val prec = photos.precedente
        val f = photos.fondu.value
        val dx = position.x - origine.x
        val dy = position.y - origine.y
        // Repère de la couche photo : le pinceau est calé sur l'écran
        translate(-dx, -dy) {
            if (prec != null) pinceauDepoli(prec)?.let {
                drawRoundRect(it, Offset(dx, dy), size, coin, alpha = VERRE_PHOTO_OPACITE * (if (cour == null) 1f - f else 1f), colorFilter = filtre)
            }
            if (cour != null) pinceauDepoli(cour)?.let {
                drawRoundRect(it, Offset(dx, dy), size, coin, alpha = VERRE_PHOTO_OPACITE * f, colorFilter = filtre)
            }
        }
        drawRoundRect(VerreGivre, cornerRadius = coin, alpha = g)
    }
}

/** Fond photo de Verre pour [photos] (miniature du dépoli préparée à chaque nouvelle photo). */
@Composable
internal fun rememberFondVerre(photos: PhotosUne): FondVerre {
    val fond = remember(photos) { FondVerre(photos) }
    val courante = photos.courante
    LaunchedEffect(fond, courante) { fond.preparer(courante) }
    return fond
}

/**
 * Photos incrustées dans le papier peint (à poser juste au-dessus de lui, sous le contenu).
 * Pinceaux recalculés seulement quand la photo change ; le fondu est lu au dessin.
 */
@Composable
internal fun VerrePhotoFond(fond: FondVerre) {
    val p = fond.photos
    Spacer(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                fond.origine = it.positionInRoot()
                fond.taille = it.size.toSize()
            }
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
                    val filtre = fond.filtre
                    if (prec != null) drawRect(prec, alpha = VERRE_PHOTO_OPACITE * (if (cour == null) 1f - f else 1f), colorFilter = filtre)
                    if (cour != null) drawRect(cour, alpha = VERRE_PHOTO_OPACITE * f, colorFilter = filtre)
                    drawRect(ombre, alpha = p.presenceVerre())
                }
            },
    )
}

/**
 * Dépoli d'une fenêtre de verre (à poser juste avant son verre) : la photo floue remplace la
 * photo nette derrière elle, sous un voile sombre. Rien quand il n'y a pas de photo.
 */
internal fun Modifier.verreDepoli(fond: FondVerre, rayon: Dp): Modifier = composed {
    val position = remember { arrayOf(Offset.Zero) }
    this
        .onGloballyPositioned { position[0] = it.positionInRoot() }
        .drawWithCache {
            val coin = CornerRadius(rayon.toPx().coerceAtMost(size.minDimension / 2f))
            onDrawBehind { fond.dessinerDepoli(this, position[0], coin) }
        }
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
