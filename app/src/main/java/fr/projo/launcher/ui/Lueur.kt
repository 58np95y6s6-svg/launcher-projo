package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Couleurs de la lueur signature : violet → bleu → cyan, avec un reflet presque blanc. */
private val COULEURS_LUEUR = intArrayOf(
    0xFF7C5CFF.toInt(), 0xFF5B6CFF.toInt(), 0xFF3B82F6.toInt(), 0xFF22D3EE.toInt(),
    0xFFD9FBFF.toInt(), 0xFF22D3EE.toInt(), 0xFF3B82F6.toInt(), 0xFF8B5CF6.toInt(), 0xFF7C5CFF.toInt(),
)
private val POSITIONS_LUEUR = floatArrayOf(0f, 0.153f, 0.306f, 0.458f, 0.514f, 0.569f, 0.708f, 0.861f, 1f)

/**
 * Lueur autour de l'élément sélectionné, comme sur la maquette Graphite : un anneau fin en
 * dégradé violet → cyan collé à l'élément, qui tourne lentement, et un grand halo flou.
 *
 * Le flou n'est pas fait par la carte graphique (non pris en charge sur ces appareils) :
 * un masque flou est calculé UNE fois par taille d'élément, puis simplement coloré par le
 * dégradé à chaque image. Rendu lisse et léger pour le projecteur.
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
    val pinceau = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val unite = 1.u
    return this.drawBehind {
        if (intensite <= 0.01f) return@drawBehind
        val force = intensite.coerceIn(0f, 1f)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val balayage = SweepGradient(cx, cy, COULEURS_LUEUR, POSITIONS_LUEUR).apply {
            setLocalMatrix(Matrix().apply { setRotate(angle.value, cx, cy) })
        }
        val r = rayon.toPx()
        val un = unite.toPx()

        // 1. Halo flou, coloré par le dégradé
        MasquesLueur.obtenir(size.width, size.height, r, un)?.let { m ->
            pinceau.shader = ComposeShader(m.shader, balayage, PorterDuff.Mode.SRC_IN)
            pinceau.alpha = (force * 255f).roundToInt()
            drawIntoCanvas {
                it.nativeCanvas.drawRect(-m.marge, -m.marge, size.width + m.marge, size.height + m.marge, pinceau)
            }
        }

        // 2. Anneau net, collé à l'élément (déborde un peu dessous pour ne laisser aucun liseré)
        val brosse = object : ShaderBrush() {
            override fun createShader(size: Size): Shader = balayage
        }
        val epaisseur = 2.5f * un
        val o = epaisseur / 2f - 0.5f * un
        drawRoundRect(
            brush = brosse,
            topLeft = Offset(-o, -o),
            size = Size(size.width + 2 * o, size.height + 2 * o),
            cornerRadius = CornerRadius(r + o),
            style = Stroke(width = epaisseur),
            alpha = force,
        )
    }
}

/**
 * Masques flous du halo (blanc + transparence), un par taille d'élément, en demi-résolution
 * (le flou n'a pas besoin de détails) : quelques centaines de Ko au total.
 */
private object MasquesLueur {
    private const val RESOLUTION = 0.5f

    class Masque(val bitmap: Bitmap, val marge: Float, val shader: BitmapShader)

    private val cache = object : LruCache<String, Masque>(3 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Masque): Int = value.bitmap.byteCount
    }

    fun obtenir(largeur: Float, hauteur: Float, rayon: Float, unite: Float): Masque? {
        if (largeur < 1f || hauteur < 1f) return null
        val cle = "${largeur.roundToInt()}x${hauteur.roundToInt()}r${rayon.roundToInt()}u${(unite * 100).roundToInt()}"
        cache.get(cle)?.let { return it }
        return try {
            creer(largeur, hauteur, rayon, unite).also { cache.put(cle, it) }
        } catch (e: Throwable) {
            null
        }
    }

    private fun creer(l: Float, h: Float, rayon: Float, unite: Float): Masque {
        val f = RESOLUTION
        // Halo proportionné à l'élément, comme la maquette (flou de 15 unités sur une grande carte)
        val sigma = (min(l, h) * 0.14f).coerceIn(4f * unite, 13f * unite)
        val agrandi = 3f * unite
        val marge = agrandi + 3f * sigma
        val mw = ceil((l + 2 * marge) * f).toInt()
        val mh = ceil((h + 2 * marge) * f).toInt()

        // Grand halo doux + cœur plus serré et plus lumineux près de l'anneau
        val large = forme(mw, mh, marge, l, h, rayon, agrandi)
        flouter(large, mw, mh, max(1, (sigma * f).roundToInt()))
        val coeur = forme(mw, mh, marge, l, h, rayon, 0.5f * unite)
        flouter(coeur, mw, mh, max(1, (sigma / 3f * f).roundToInt()))

        val pixels = IntArray(mw * mh)
        for (i in pixels.indices) {
            val a1 = large[i] / 255f * 0.95f
            val a2 = coeur[i] / 255f * 0.5f
            val a = ((1f - (1f - a1) * (1f - a2)) * 255f).roundToInt().coerceIn(0, 255)
            // Blanc prémultiplié : seule la transparence compte, la couleur vient du dégradé
            pixels[i] = (a shl 24) or (a shl 16) or (a shl 8) or a
        }
        val bitmap = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, mw, 0, 0, mw, mh)

        // L'intérieur de l'élément reste vide (sinon la lueur se verrait à travers les cartes transparentes)
        android.graphics.Canvas(bitmap).apply {
            scale(f, f)
            translate(marge, marge)
            drawRoundRect(
                RectF(0f, 0f, l, h), rayon, rayon,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) },
            )
        }

        val shader = BitmapShader(bitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(1f / f, 1f / f); postTranslate(-marge, -marge) })
        }
        return Masque(bitmap, marge, shader)
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
