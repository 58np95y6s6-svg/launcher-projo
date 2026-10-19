package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Couleurs de la lueur signature : violet → bleu → cyan, avec un reflet presque blanc. */
private val COULEURS_LUEUR = intArrayOf(
    0xFF7C5CFF.toInt(), 0xFF5B6CFF.toInt(), 0xFF3B82F6.toInt(), 0xFF22D3EE.toInt(),
    0xFFD9FBFF.toInt(), 0xFF22D3EE.toInt(), 0xFF3B82F6.toInt(), 0xFF8B5CF6.toInt(), 0xFF7C5CFF.toInt(),
)
private val POSITIONS_LUEUR = floatArrayOf(0f, 0.153f, 0.306f, 0.458f, 0.514f, 0.569f, 0.708f, 0.861f, 1f)

/** Angle de départ : le reflet clair part du haut, un peu à droite (comme la maquette). */
private const val ANGLE_DEPART = 110f

/**
 * Lueur autour de l'élément sélectionné, comme sur la maquette Graphite : un anneau fin en
 * dégradé violet → cyan collé à l'élément, qui tourne lentement, et un halo lumineux.
 *
 * Le flou n'est pas fait par la carte graphique (non pris en charge sur ces appareils) :
 * un masque flou est calculé UNE fois par taille d'élément, puis simplement coloré par le
 * dégradé à chaque image. Le halo s'éteint en douceur à une distance fixe ([portee]) :
 * jamais de bord net, même quand la rangée rogne ce qui dépasse.
 *
 * @param intensite 0 = rien, 1 = lueur complète (à animer avec animateFloatAsState).
 * @param rayon rayon des coins de l'élément.
 * @param tourne true pour animer la rotation (seulement l'élément sélectionné, pour économiser).
 * @param portee distance maximale du halo (par défaut : selon la taille de l'élément).
 * @param halo force du halo (1 = normal ; plus bas pour les petits éléments).
 */
@Composable
fun Modifier.lueurFocus(
    intensite: Float,
    rayon: Dp,
    tourne: Boolean = intensite > 0f,
    portee: Dp = Dp.Unspecified,
    halo: Float = 1f,
): Modifier {
    // Rotation du dégradé : un tour en 4 s, seulement sur l'élément sélectionné
    val angle: State<Float>? = if (tourne) {
        rememberInfiniteTransition(label = "lueur").animateFloat(
            initialValue = ANGLE_DEPART, targetValue = ANGLE_DEPART + 360f,
            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
            label = "angle",
        )
    } else {
        null
    }
    // Dernier angle affiché : au défocus, la lueur s'éteint sur place (pas de saut de couleurs)
    val fige = remember { floatArrayOf(ANGLE_DEPART) }
    val pinceau = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val unite = 1.u
    return this.drawBehind {
        if (intensite <= 0.01f) return@drawBehind
        val force = intensite.coerceIn(0f, 1f)
        val a = angle?.value?.also { fige[0] = it } ?: fige[0]
        val l = size.width
        val h = size.height
        // Dégradé « normalisé » : les couleurs se répartissent régulièrement sur tout le tour,
        // même sur un élément très allongé
        val balayage = SweepGradient(0f, 0f, COULEURS_LUEUR, POSITIONS_LUEUR).apply {
            setLocalMatrix(Matrix().apply {
                setRotate(a)
                postScale(1f, h / l)
                postTranslate(l / 2f, h / 2f)
            })
        }
        val r = rayon.toPx()
        val un = unite.toPx()

        // 1. Halo, coloré par le dégradé
        val p = if (portee == Dp.Unspecified) porteeParDefaut(min(l, h), un) else portee.toPx()
        if (halo > 0f) {
            MasquesLueur.obtenir(l, h, r, p)?.let { m ->
                pinceau.shader = ComposeShader(m.shader, balayage, PorterDuff.Mode.SRC_IN)
                pinceau.alpha = (force * halo.coerceIn(0f, 1f) * 255f).roundToInt()
                drawIntoCanvas {
                    it.nativeCanvas.drawRect(-m.marge, -m.marge, l + m.marge, h + m.marge, pinceau)
                }
            }
        }

        // 2. Anneau net de 2 unités, collé à l'élément (il passe un peu dessous : aucun liseré)
        val brosse = object : ShaderBrush() {
            override fun createShader(size: Size): Shader = balayage
        }
        val epaisseur = 2.5f * un
        val o = 0.75f * un
        drawRoundRect(
            brush = brosse,
            topLeft = Offset(-o, -o),
            size = Size(l + 2 * o, h + 2 * o),
            cornerRadius = CornerRadius(r + o),
            style = Stroke(width = epaisseur),
            alpha = force,
        )
        // Fin filet clair au bord extérieur de l'anneau : contour net, comme la maquette
        val f = 1.75f * un
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(-f, -f),
            size = Size(l + 2 * f, h + 2 * f),
            cornerRadius = CornerRadius(r + f),
            style = Stroke(width = 0.5f * un),
            alpha = 0.25f * force,
        )
    }
}

/** Portée du halo selon la taille de l'élément : 22 unités au plus (place laissée par les rangées). */
private fun porteeParDefaut(cote: Float, un: Float): Float = (6f * un + 0.15f * cote).coerceIn(8f * un, 22f * un)

/**
 * Masques du halo (blanc + transparence), un par taille d'élément, en demi-résolution
 * (le flou n'a pas besoin de détails) : quelques centaines de Ko au total.
 */
private object MasquesLueur {
    private const val RESOLUTION = 0.5f

    class Masque(val bitmap: Bitmap, val marge: Float, val shader: BitmapShader)

    private val cache = object : LruCache<String, Masque>(3 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Masque): Int = value.bitmap.byteCount
    }

    fun obtenir(largeur: Float, hauteur: Float, rayon: Float, portee: Float): Masque? {
        if (largeur < 1f || hauteur < 1f || portee < 1f) return null
        val cle = "${largeur.roundToInt()}x${hauteur.roundToInt()}r${rayon.roundToInt()}p${portee.roundToInt()}"
        cache.get(cle)?.let { return it }
        return try {
            creer(largeur, hauteur, rayon, portee).also { cache.put(cle, it) }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Deux couches, comme la maquette : un halo proche et lumineux, et un halo large et léger.
     * Tailles proportionnelles à la portée (22 unités pour une grande carte).
     */
    private fun creer(l: Float, h: Float, rayon: Float, portee: Float): Masque {
        val f = RESOLUTION
        val k = portee / 22f
        val marge = portee + 2f
        val mw = ceil((l + 2 * marge) * f).toInt()
        val mh = ceil((h + 2 * marge) * f).toInt()

        val proche = forme(mw, mh, marge, l, h, rayon, 3f * k)
        flouter(proche, mw, mh, rayonBoite(8f * k * f))
        val large = forme(mw, mh, marge, l, h, rayon, 8f * k)
        flouter(large, mw, mh, rayonBoite(14f * k * f))

        // Tramage léger : pas de bandes dans les dégradés très sombres
        val alea = java.util.Random(7)
        val pixels = IntArray(mw * mh)
        for (y in 0 until mh) {
            for (x in 0 until mw) {
                val i = y * mw + x
                // Distance (en pixels de l'écran) au bord de l'élément, négative à l'intérieur
                val d = distance((x + 0.5f) / f - marge, (y + 0.5f) / f - marge, l, h, rayon)
                if (d <= -2f / f || d >= portee) continue
                val a1 = proche[i] / 255f * 0.95f
                val a2 = large[i] / 255f * 0.35f
                var a = 1f - (1f - a1) * (1f - a2)
                // Extinction douce jusqu'à la portée : aucun bord visible
                a *= 1f - lissage(0.45f * portee, portee, d)
                // Intérieur vide (sinon la lueur se verrait à travers les cartes transparentes)
                a *= ((d * f) + 0.5f).coerceIn(0f, 1f)
                val v = (a * 255f + alea.nextFloat() - 0.5f).roundToInt().coerceIn(0, 255)
                // Blanc prémultiplié : seule la transparence compte, la couleur vient du dégradé
                pixels[i] = (v shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        val bitmap = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, mw, 0, 0, mw, mh)

        val shader = BitmapShader(bitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(1f / f, 1f / f); postTranslate(-marge, -marge) })
        }
        return Masque(bitmap, marge, shader)
    }

    /** Rayon de flou « boîte » qui, appliqué 3 fois, imite un flou gaussien d'écart [sigma]. */
    private fun rayonBoite(sigma: Float): Int = max(1, (sqrt(sigma * sigma + 0.25f) - 0.5f).roundToInt())

    /** Courbe en S de 0 (avant [debut]) à 1 (après [fin]). */
    private fun lissage(debut: Float, fin: Float, x: Float): Float {
        val t = ((x - debut) / (fin - debut)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Distance signée d'un point au rectangle arrondi (0, 0, l, h) de rayon [r]. */
    private fun distance(x: Float, y: Float, l: Float, h: Float, r: Float): Float {
        val rr = min(r, min(l, h) / 2f)
        val qx = kotlin.math.abs(x - l / 2f) - (l / 2f - rr)
        val qy = kotlin.math.abs(y - h / 2f) - (h / 2f - rr)
        return hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - rr
    }

    /** Rectangle arrondi plein (agrandi de [agrandi]), transparence de 0 à 255 par pixel. */
    private fun forme(mw: Int, mh: Int, marge: Float, l: Float, h: Float, rayon: Float, agrandi: Float): IntArray {
        val b = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(b).apply {
            scale(RESOLUTION, RESOLUTION)
            translate(marge, marge)
            drawRoundRect(
                RectF(-agrandi, -agrandi, l + agrandi, h + agrandi), rayon + agrandi, rayon + agrandi,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE },
            )
        }
        val px = IntArray(mw * mh)
        b.getPixels(px, 0, mw, 0, 0, mw, mh)
        b.recycle()
        for (i in px.indices) px[i] = px[i] ushr 24
        return px
    }

    /** Flou gaussien approché : 3 flous « boîte » horizontaux, puis 3 verticaux. */
    private fun flouter(a: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(max(w, h))
        repeat(3) { for (y in 0 until h) boite(a, y * w, 1, w, r, tmp) }
        repeat(3) { for (x in 0 until w) boite(a, x, w, h, r, tmp) }
    }

    /** Moyenne glissante sur 2r+1 pixels d'une ligne (ou colonne), bords à zéro. */
    private fun boite(a: IntArray, debut: Int, pas: Int, n: Int, r: Int, tmp: IntArray) {
        val fenetre = 2 * r + 1
        var somme = 0
        for (k in 0..min(r, n - 1)) somme += a[debut + k * pas]
        for (i in 0 until n) {
            tmp[i] = (somme + fenetre / 2) / fenetre
            val entre = i + r + 1
            if (entre < n) somme += a[debut + entre * pas]
            val sort = i - r
            if (sort >= 0) somme -= a[debut + sort * pas]
        }
        for (i in 0 until n) a[debut + i * pas] = tmp[i]
    }
}

/** Designs qui utilisent la lueur comme effet de sélection des cartes d'apps. */
val Design.avecLueur: Boolean get() = this == Design.AURORE || this == Design.GRAPHITE
