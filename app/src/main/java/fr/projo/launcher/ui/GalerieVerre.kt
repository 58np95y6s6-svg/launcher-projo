package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.projo.launcher.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Matières du design « Galerie » : palette, polices, verre, ombres douces, anneau de
 * sélection et flou imité.
 *
 * Aucun flou en temps réel (Modifier.blur n'existe qu'à partir d'Android 12, absent des Fire TV) :
 *  - les ombres sont une seule image floutée une fois pour toutes, étirée en neuf morceaux ;
 *  - le flou des images est une copie réduite et floutée, dessinée sous l'image nette (le
 *    même rendu sur toutes les versions d'Android) ; le halo est une copie minuscule, saturée,
 *    aux bords fondus, très agrandie ;
 *  - le verre est un blanc translucide en dégradé, un liseré blanc et une ombre douce.
 */

// ------------------------------------------------------------------ palette et polices

internal val GalerieEncre = Color(0xFF2B2D33)
internal val GalerieEncreTitre = Color(0xFF34363D)
internal val GalerieGris = Color(0xFF7C7F88)
internal val GalerieGrisOnglet = Color(0xFF5D6069)
internal val GalerieTexte = Color(0xFF26282D)
internal val GalerieTete = Color(0xFF55585F)

/** Instrument Serif (titres) et Manrope moyen / gras (textes), polices OFL livrées avec l'app. */
internal val GalerieSerif = FontFamily(Font(R.font.galerie_serif, FontWeight.Normal))
internal val GalerieSans = FontFamily(
    Font(R.font.galerie_sans_moyen, FontWeight.Medium),
    Font(R.font.galerie_sans_gras, FontWeight.Bold),
)

// ------------------------------------------------------------------ unités

/** Mesure de la maquette (écran de 1920 × 1080 px) : 2 px de maquette = 1 unité .u. */
internal val Int.mq: Dp
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current / 2f).dp

internal val Float.mq: Dp
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current / 2f).dp

/** Taille de texte de la maquette (px). */
internal val Int.mqs: TextUnit
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current / 2f).sp

internal val Float.mqs: TextUnit
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current / 2f).sp

/** Pixels de l'appareil par pixel de la maquette (1 sur un téléviseur 1080p). */
@Composable
@ReadOnlyComposable
internal fun pxMaquette(): Float = LocalDensity.current.density * LocalScale.current / 2f

// ------------------------------------------------------------------ ombres douces

/**
 * Ombre douce : un carré arrondi noir flouté (écart type [SIGMA] px) une fois pour toutes, puis
 * étiré en neuf morceaux à la taille voulue ; le flou suit l'échelle demandée.
 */
internal object OmbreGalerie {
    private const val SIGMA = 12f
    private const val BORD = 36
    private const val RAYON = 24
    private const val COIN = BORD + RAYON
    private const val COTE = 2 * COIN + 4

    val image: ImageBitmap by lazy { fabriquer() }

    private fun fabriquer(): ImageBitmap {
        val n = COTE
        val a = FloatArray(n * n)
        val r = RAYON.toFloat()
        val g = BORD.toFloat()
        val d = (n - BORD).toFloat()
        for (y in 0 until n) for (x in 0 until n) {
            val px = x + 0.5f
            val py = y + 0.5f
            if (px < g || px > d || py < g || py > d) continue
            // Coins arrondis
            val cx = when { px < g + r -> g + r; px > d - r -> d - r; else -> px }
            val cy = when { py < g + r -> g + r; py > d - r -> d - r; else -> py }
            val dx = px - cx
            val dy = py - cy
            if (dx * dx + dy * dy <= r * r) a[y * n + x] = 1f
        }
        // Trois flous en boîte ≈ flou gaussien d'écart type SIGMA
        val canal = arrayOf(a)
        repeat(3) { flouBoite(canal, n, n, 11) }
        val pixels = IntArray(n * n) { (a[it].coerceIn(0f, 1f) * 255f).roundToInt() shl 24 }
        return Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private val filtres = HashMap<Color, ColorFilter>()

    private fun teinte(c: Color): ColorFilter = filtres.getOrPut(c) { ColorFilter.tint(c, BlendMode.SrcIn) }

    /**
     * Ombre d'un rectangle arrondi ([l], [t], [w], [h], px) floutée de [flou] px (rayon CSS),
     * décalée de [dy], de couleur [couleur] (son alpha compris).
     */
    fun dessiner(scope: DrawScope, l: Float, t: Float, w: Float, h: Float, flou: Float, dy: Float, couleur: Color) {
        if (couleur.alpha <= 0.004f || w <= 1f || h <= 1f || flou <= 0f) return
        val s = (flou / 2f) / SIGMA
        val bord = BORD * s
        val x0 = (l - bord).roundToInt()
        val y0 = (t + dy - bord).roundToInt()
        val x3 = (l + w + bord).roundToInt()
        val y3 = (t + dy + h + bord).roundToInt()
        val coin = minOf(COIN * s, (x3 - x0) / 2f, (y3 - y0) / 2f).roundToInt()
        val xs = intArrayOf(x0, x0 + coin, x3 - coin, x3)
        val ys = intArrayOf(y0, y0 + coin, y3 - coin, y3)
        val src = intArrayOf(0, COIN, COTE - COIN, COTE)
        val filtre = teinte(couleur.copy(alpha = 1f))
        val img = image
        for (j in 0..2) for (i in 0..2) {
            val dw = xs[i + 1] - xs[i]
            val dh = ys[j + 1] - ys[j]
            if (dw <= 0 || dh <= 0) continue
            scope.drawImage(
                img,
                srcOffset = IntOffset(src[i], src[j]),
                srcSize = IntSize(src[i + 1] - src[i], src[j + 1] - src[j]),
                dstOffset = IntOffset(xs[i], ys[j]),
                dstSize = IntSize(dw, dh),
                alpha = couleur.alpha,
                colorFilter = filtre,
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/** Contour arrondi ([rayon] borné à la moitié du plus petit côté : 999 = pilule). */
internal fun formeArrondie(l: Float, t: Float, w: Float, h: Float, rayon: Float): Path = Path().apply {
    val r = minOf(rayon, w / 2f, h / 2f)
    addRoundRect(RoundRect(l, t, l + w, t + h, CornerRadius(r)))
}

/** Ombre portée à l'extérieur seulement (comme box-shadow en CSS : jamais sous un verre translucide). */
internal fun DrawScope.ombreExterieure(forme: Path, w: Float, h: Float, flou: Float, dy: Float, couleur: Color) {
    clipPath(forme, ClipOp.Difference) { OmbreGalerie.dessiner(this, 0f, 0f, w, h, flou, dy, couleur) }
}

// ------------------------------------------------------------------ verre

/** Dégradé linéaire orienté comme en CSS ([angle] en degrés : 180 = vers le bas), sur le rectangle ([l], [t], [w], [h]). */
internal fun degradeCss(angle: Float, w: Float, h: Float, vararg arrets: Pair<Float, Color>, l: Float = 0f, t: Float = 0f): Brush {
    val a = Math.toRadians(angle.toDouble())
    val dx = sin(a).toFloat()
    val dy = -cos(a).toFloat()
    val longueur = abs(w * dx) + abs(h * dy)
    val c = Offset(l + w / 2f, t + h / 2f)
    val v = Offset(dx, dy) * (longueur / 2f)
    return Brush.linearGradient(*arrets, start = c - v, end = c + v)
}

/**
 * Verre de la maquette (.verre) : blanc translucide en dégradé, liseré blanc, reflet sur le
 * bord haut et ombre douce à l'extérieur. [k] : px par px de maquette ; [rayon] en px de maquette
 * (999 : pilule). [fond] : remplissage à la place du verre (pilule sombre, panneau plus laiteux).
 */
internal fun Modifier.verreGalerie(
    k: Float,
    rayon: Float,
    fond: ((Float, Float) -> Brush)? = null,
    lisere: Color = Color.White.copy(alpha = 0.62f),
    ombre: Float = 1f,
    /** Partie de l'élément couverte par le verre (l, t, w, h en px) ; null : tout l'élément. */
    zone: ((Size) -> FloatArray)? = null,
): Modifier = drawWithCache {
    val z = zone?.invoke(size) ?: floatArrayOf(0f, 0f, size.width, size.height)
    val l = z[0]
    val t = z[1]
    val w = z[2]
    val h = z[3]
    val r = minOf(rayon * k, w / 2f, h / 2f)
    val forme = formeArrondie(l, t, w, h, r)
    val formeLocale = if (fond != null) formeArrondie(0f, 0f, w, h, r) else forme
    val remplissage = fond?.invoke(w, h) ?: degradeCss(
        160f, w, h,
        0f to Color.White.copy(alpha = 0.42f), 0.55f to Color.White.copy(alpha = 0.16f), 1f to Color.White.copy(alpha = 0.26f),
        l = l, t = t,
    )
    val e = 1.5f * k
    onDrawBehind {
        if (w <= 0f || h <= 0f) return@onDrawBehind
        if (ombre > 0f) {
            clipPath(forme, ClipOp.Difference) {
                OmbreGalerie.dessiner(this, l, t, w, h, 40f * k, 18f * k, Color(0xFF282C34).copy(alpha = 0.16f * ombre))
                OmbreGalerie.dessiner(this, l, t, w, h, 6f * k, 2f * k, Color(0xFF282C34).copy(alpha = 0.06f * ombre))
            }
        }
        if (fond != null) translate(l, t) { drawPath(formeLocale, remplissage) } else drawPath(forme, remplissage)
        // Reflet : fin trait blanc sous le bord haut
        clipPath(forme) {
            drawRoundRect(
                Color.White.copy(alpha = 0.75f), topLeft = Offset(l, t + k), size = Size(w, h),
                cornerRadius = CornerRadius(r), style = Stroke(k),
            )
        }
        drawRoundRect(
            lisere, topLeft = Offset(l + e / 2f, t + e / 2f), size = Size(w - e, h - e),
            cornerRadius = CornerRadius(r - e / 2f), style = Stroke(e),
        )
    }
}

/**
 * Anneau de sélection de la maquette (.anneau) autour d'un élément de [w] × [h] px : blanc 3 px
 * puis encre 3 px, et ombre portée ; [t] : 0 → 1 (fondu), [rayon] en px de l'appareil.
 */
internal fun DrawScope.anneauGalerie(k: Float, rayon: Float, t: Float, ombre: Boolean = true) {
    if (t <= 0.01f) return
    val w = size.width
    val h = size.height
    val r = minOf(rayon, w / 2f, h / 2f)
    if (ombre) ombreExterieure(formeArrondie(0f, 0f, w, h, r), w, h, 44f * k, 22f * k, Color(0xFF14161C).copy(alpha = 0.3f * t))
    val e = 3f * k
    drawRoundRect(
        Color.White.copy(alpha = t), topLeft = Offset(-e / 2f, -e / 2f), size = Size(w + e, h + e),
        cornerRadius = CornerRadius(r + e / 2f), style = Stroke(e),
    )
    drawRoundRect(
        GalerieEncre.copy(alpha = 0.9f * t), topLeft = Offset(-e * 1.5f, -e * 1.5f), size = Size(w + 3 * e, h + 3 * e),
        cornerRadius = CornerRadius(r + e * 1.5f), style = Stroke(e),
    )
}

// ------------------------------------------------------------------ filtres de couleur

/** Saturation et contraste (filtres CSS saturate() / grayscale() / contrast()), en cache par pas de 0,05. */
internal object FiltresGalerie {
    private val cache = HashMap<Int, ColorFilter?>()

    fun pour(saturation: Float, contraste: Float): ColorFilter? {
        val s = (saturation * 20f).roundToInt()
        val c = (contraste * 20f).roundToInt()
        return cache.getOrPut(s * 1000 + c) {
            if (s == 20 && c == 20) null else ColorFilter.colorMatrix(matrice(s / 20f, c / 20f))
        }
    }

    /** Matrice de saturation [s] (poids de luminance de la CSS) suivie du contraste [c]. */
    fun matrice(s: Float, c: Float): ColorMatrix {
        val rw = 0.213f
        val gw = 0.715f
        val bw = 0.072f
        val t = 127.5f * (1f - c)
        return ColorMatrix(
            floatArrayOf(
                c * (rw * (1 - s) + s), c * gw * (1 - s), c * bw * (1 - s), 0f, t,
                c * rw * (1 - s), c * (gw * (1 - s) + s), c * bw * (1 - s), 0f, t,
                c * rw * (1 - s), c * gw * (1 - s), c * (bw * (1 - s) + s), 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }
}

// ------------------------------------------------------------------ images

/**
 * Pinceau d'une image : la même image dessinée « en couverture » dans un rectangle arrondi
 * (comme background-size: cover), à une position flottante, avec des coins lissés.
 */
internal class PinceauImage(val image: ImageBitmap) {
    private val shader = BitmapShader(image.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val matrice = Matrix()
    val brosse: ShaderBrush = ShaderBrush(shader)

    /** Cadre l'image sur ([l], [t], [w], [h]) ; [ancrageY] : 0 en haut, 0,5 au centre. */
    fun couvrir(l: Float, t: Float, w: Float, h: Float, ancrageY: Float) {
        val s = max(w / image.width, h / image.height)
        matrice.setScale(s, s)
        matrice.postTranslate(l + (w - image.width * s) / 2f, t + (h - image.height * s) * ancrageY)
        shader.setLocalMatrix(matrice)
    }
}

/** Dessine [p] en couverture dans un rectangle arrondi. */
internal fun DrawScope.imageArrondie(
    p: PinceauImage, l: Float, t: Float, w: Float, h: Float, r: Float, ancrageY: Float, alpha: Float, filtre: ColorFilter?,
) {
    if (alpha <= 0.003f) return
    p.couvrir(l, t, w, h, ancrageY)
    drawRoundRect(p.brosse, topLeft = Offset(l, t), size = Size(w, h), cornerRadius = CornerRadius(r), alpha = alpha.coerceAtMost(1f), colorFilter = filtre)
}

/** Images d'un titre prêtes à dessiner : nette, adoucie (flou imité), halo, et affiche éventuelle. */
internal class VisuelGalerie(
    net: ImageBitmap?,
    doux: ImageBitmap?,
    val halo: ImageBitmap?,
    affiche: ImageBitmap?,
    /** Image nette sans transparence (photo TMDB) : rien ne se voit dessous. */
    val opaque: Boolean = false,
) {
    val net: PinceauImage? = net?.let { PinceauImage(it) }
    val doux: PinceauImage? = doux?.let { PinceauImage(it) }
    val affiche: PinceauImage? = affiche?.let { PinceauImage(it) }
    /** Aucune image (pas encore chargée, ou introuvable). */
    val vide: Boolean get() = net == null && affiche == null

    /**
     * Envoie les images à la carte graphique tout de suite, en arrière-plan : sinon l'envoi se fait
     * au premier dessin, pendant le glissement qui fait entrer le titre (image sautée).
     */
    fun envoyerAuGpu() {
        for (i in listOfNotNull(net?.image, doux?.image, halo, affiche?.image)) runCatching { i.prepareToDraw() }
    }
}

/** Copies adoucies d'une image (flou imité) : calculées hors du thread principal, une fois par image. */
internal object FlousGalerie {
    private val cache = android.util.LruCache<String, Pair<ImageBitmap, ImageBitmap>>(48)

    /** Image adoucie (160 px de large, floutée) et halo (48 px, saturé, bords fondus) de [net]. */
    fun preparer(cle: String, net: ImageBitmap): Pair<ImageBitmap, ImageBitmap>? {
        cache.get(cle)?.let { return it }
        return try {
            val b = net.asAndroidBitmap()
            val w = b.width
            val h = b.height
            if (w < 4 || h < 4) return null
            val pixels = IntArray(w * h)
            b.getPixels(pixels, 0, w, 0, 0, w, h)
            val dw = 160
            val dh = (h * dw / w.toFloat()).roundToInt().coerceAtLeast(2)
            val doux = reduire(pixels, w, h, dw, dh)
            repeat(2) { flouBoite(doux, dw, dh, 1) }
            val hw = 48
            val hh = (h * hw / w.toFloat()).roundToInt().coerceAtLeast(2)
            val halo = reduire(pixels, w, h, hw, hh)
            saturer(halo, 1.7f)
            repeat(3) { flouBoite(halo, hw, hh, 2) }
            val res = versImage(doux, dw, dh, null) to versImage(halo, hw, hh, bordsFondus(hw, hh))
            cache.put(cle, res)
            res
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Reflet d'une icône : copie de 24 px floutée (la dessiner agrandie la rend floue). */
    fun reflet(icone: ImageBitmap): ImageBitmap? = try {
        val b = icone.asAndroidBitmap()
        val w = b.width
        val h = b.height
        val pixels = IntArray(w * h)
        b.getPixels(pixels, 0, w, 0, 0, w, h)
        val n = 24
        val c = reduire(pixels, w, h, n, n)
        repeat(2) { flouBoite(c, n, n, 1) }
        versImage(c, n, n, null)
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Réduction par moyenne des pixels (bonne qualité même pour un grand rapport). */
    private fun reduire(src: IntArray, w: Int, h: Int, dw: Int, dh: Int): Array<FloatArray> {
        val r = FloatArray(dw * dh)
        val g = FloatArray(dw * dh)
        val b = FloatArray(dw * dh)
        val n = FloatArray(dw * dh)
        for (y in 0 until h) {
            val ty = (y * dh / h).coerceAtMost(dh - 1)
            for (x in 0 until w) {
                val tx = (x * dw / w).coerceAtMost(dw - 1)
                val i = ty * dw + tx
                val c = src[y * w + x]
                r[i] += ((c shr 16) and 0xFF).toFloat()
                g[i] += ((c shr 8) and 0xFF).toFloat()
                b[i] += (c and 0xFF).toFloat()
                n[i] += 1f
            }
        }
        for (i in n.indices) if (n[i] > 0f) {
            r[i] /= n[i]; g[i] /= n[i]; b[i] /= n[i]
        }
        return arrayOf(r, g, b)
    }

    private fun saturer(c: Array<FloatArray>, s: Float) {
        val (r, g, b) = c
        for (i in r.indices) {
            val l = 0.213f * r[i] + 0.715f * g[i] + 0.072f * b[i]
            r[i] = l + (r[i] - l) * s
            g[i] = l + (g[i] - l) * s
            b[i] = l + (b[i] - l) * s
        }
    }

    /** Opacité du halo : pleine au centre, fondue vers les bords (comme un rectangle très flouté). */
    private fun bordsFondus(w: Int, h: Int): FloatArray {
        fun rampe(v: Float, f: Float): Float {
            val x = (minOf(v, 1f - v) / (2f * f)).coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }
        return FloatArray(w * h) { i ->
            val x = (i % w + 0.5f) / w
            val y = (i / w + 0.5f) / h
            rampe(x, 0.15f) * rampe(y, 0.2f)
        }
    }

    private fun versImage(c: Array<FloatArray>, w: Int, h: Int, alpha: FloatArray?): ImageBitmap {
        val (r, g, b) = c
        val px = IntArray(w * h) { i ->
            val a = ((alpha?.get(i) ?: 1f) * 255f).roundToInt().coerceIn(0, 255)
            (a shl 24) or (r[i].roundToInt().coerceIn(0, 255) shl 16) or (g[i].roundToInt().coerceIn(0, 255) shl 8) or
                b[i].roundToInt().coerceIn(0, 255)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** Flou en boîte (rayon [r]) d'images à un ou plusieurs canaux, bords prolongés. */
internal fun flouBoite(canaux: Array<FloatArray>, w: Int, h: Int, r: Int) {
    val ligne = FloatArray(maxOf(w, h))
    val n = (2 * r + 1).toFloat()
    for (c in canaux) {
        // Horizontal
        for (y in 0 until h) {
            val o = y * w
            var somme = 0f
            for (i in -r..r) somme += c[o + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                ligne[x] = somme / n
                somme += c[o + (x + r + 1).coerceAtMost(w - 1)] - c[o + (x - r).coerceAtLeast(0)]
            }
            System.arraycopy(ligne, 0, c, o, w)
        }
        // Vertical
        for (x in 0 until w) {
            var somme = 0f
            for (i in -r..r) somme += c[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                ligne[y] = somme / n
                somme += c[(y + r + 1).coerceAtMost(h - 1) * w + x] - c[(y - r).coerceAtLeast(0) * w + x]
            }
            for (y in 0 until h) c[y * w + x] = ligne[y]
        }
    }
}

// ------------------------------------------------------------------ pictogrammes (viewBox 24)

/** Trace un pictogramme dessiné dans un carré de 24 unités, à la taille de la zone. */
internal fun DrawScope.pictogramme(couleur: Color, epaisseur: Float, dessin: Path.() -> Unit) {
    val s = minOf(size.width, size.height) / 24f
    val p = Path().apply(dessin)
    withTransform({
        translate((size.width - 24f * s) / 2f, (size.height - 24f * s) / 2f)
        scale(s, s, Offset.Zero)
    }) {
        drawPath(p, couleur, style = Stroke(epaisseur, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Triangle « lecture » plein dans un carré de 24 unités. */
internal fun DrawScope.triangleLecture(couleur: Color, x0: Float = 7f, y0: Float = 5f, x1: Float = 19f, y1: Float = 19f) {
    val s = minOf(size.width, size.height) / 24f
    val ox = (size.width - 24f * s) / 2f
    val oy = (size.height - 24f * s) / 2f
    val p = Path().apply {
        moveTo(ox + x0 * s, oy + y0 * s)
        lineTo(ox + x1 * s, oy + (y0 + y1) / 2f * s)
        lineTo(ox + x0 * s, oy + y1 * s)
        close()
    }
    drawPath(p, couleur)
}
