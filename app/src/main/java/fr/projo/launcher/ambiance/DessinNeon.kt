package fr.projo.launcher.ambiance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Néon continu tout autour de l'écran : un tube aux coins arrondis (en retrait de 16 u,
 * rayon 28 u, épaisseur 10 u), son âme qui blanchit quand ça tape, deux halos (proche et
 * large) qui débordent jusqu'au bord de l'écran (ce que lit l'ambilight), et le bord de
 * l'app assombri sous le tube pour qu'il ressorte. u = largeur / 960.
 *
 * Sans flou par la carte graphique (le Fire TV ne sait pas le faire) : chaque couche est un
 * « maillage » posé le long du tube (Canvas.drawBitmapMesh). Le profil en travers du tube
 * (tube net, halos flous) est une petite image calculée UNE fois par taille d'écran ; la
 * couleur et l'intensité de chaque point du tube sont les couleurs des sommets, recalculées
 * à chaque image dans des tableaux réutilisés (aucune allocation par image). Couleur
 * continue le long du tube : les sommets voisins se fondent l'un dans l'autre.
 */
class DessinNeon(val largeur: Int, val hauteur: Int) {

    private val u = largeur / 960f
    private val marge = 16f * u
    private val rayon = 28f * u
    private val demi = 5f * u

    /** Épaisseur des 4 bandes (fenêtres) : halo intérieur et bord assombri compris. */
    val profondeur: Int = ceil(66f * u).toInt()

    // ------------------------------------------------------------------ points du tube

    /** Nombre de points le long du tube (le dernier ferme l'anneau : c'est le premier). */
    private val nb: Int
    private val px: FloatArray
    private val py: FloatArray

    /** Normale vers l'intérieur de l'écran. */
    private val nx: FloatArray
    private val ny: FloatArray

    /** Position le long du tube (0 à 1, en partant du bas au centre). */
    private val position: FloatArray

    /** Place dans le spectre : 0 = basses (bas au centre), 1 = aigus (haut au centre). */
    private val spectre: FloatArray

    /** Part de chaque bande dans l'énergie de chaque point (lissage le long du tube). */
    private val poids: FloatArray

    /** Couleurs « arc-en-ciel » de chaque point (fixes). */
    private val arcR: FloatArray
    private val arcV: FloatArray
    private val arcB: FloatArray

    private class Couche(
        val bitmap: Bitmap,
        val sommets: FloatArray,
        val couleurs: IntArray,
        val colonnes: Int,
        val lignes: Int,
        /** Valeur maximale du profil (le profil est rangé de 0 à 1 dans l'image). */
        val gain: Float,
    )

    private val voile: Couche
    private val large: Couche
    private val proche: Couche
    private val tube: Couche
    private val coeur: Couche
    private val lumieres: Array<Couche>

    private val pinceauVoile = Paint(Paint.FILTER_BITMAP_FLAG)

    /** La lumière s'ajoute (halo + tube + âme), comme de vraies lumières. */
    private val pinceauLumiere = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    init {
        val w = largeur.toFloat()
        val h = hauteur.toFloat()
        val lx = ArrayList<Float>()
        val ly = ArrayList<Float>()
        val lnx = ArrayList<Float>()
        val lny = ArrayList<Float>()
        val lk = ArrayList<Float>()
        fun point(x: Float, y: Float, nX: Float, nY: Float, k: Float) {
            lx += x; ly += y; lnx += nX; lny += nY; lk += k
        }
        // Ligne médiane du tube : rectangle aux coins arrondis
        val g = marge
        val d = w - marge
        val haut = marge
        val bas = h - marge
        val cx = w / 2f
        // Place dans le spectre, comme l'aperçu validé : bas 0 → 0,25 ; côtés 0,25 → 0,75 en montant ; haut 0,75 → 1
        fun kBas(x: Float) = abs(x - cx) / cx * 0.25f
        fun kCote(y: Float) = 0.25f + 0.5f * (h - y) / h
        fun kHaut(x: Float) = 1f - abs(x - cx) / cx * 0.25f
        val pas = 12f * u
        fun droit(x0: Float, y0: Float, x1: Float, y1: Float, nX: Float, nY: Float, k0: Float, k1: Float) {
            val n = max(1, ceil(hypot(x1 - x0, y1 - y0) / pas).toInt())
            for (i in 0 until n) {
                val t = i.toFloat() / n
                point(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, nX, nY, k0 + (k1 - k0) * t)
            }
        }
        fun arc(ccx: Float, ccy: Float, a0: Float, a1: Float, k0: Float, k1: Float) {
            val n = 15
            for (i in 0 until n) {
                val t = i.toFloat() / n
                val a = (a0 + (a1 - a0) * t) * PI.toFloat() / 180f
                val c = cos(a)
                val s = sin(a)
                point(ccx + rayon * c, ccy + rayon * s, -c, -s, k0 + (k1 - k0) * t)
            }
        }
        droit(cx, bas, d - rayon, bas, 0f, -1f, 0f, kBas(d - rayon))
        arc(d - rayon, bas - rayon, 90f, 0f, kBas(d - rayon), kCote(bas - rayon))
        droit(d, bas - rayon, d, haut + rayon, -1f, 0f, kCote(bas - rayon), kCote(haut + rayon))
        arc(d - rayon, haut + rayon, 0f, -90f, kCote(haut + rayon), kHaut(d - rayon))
        droit(d - rayon, haut, cx, haut, 0f, 1f, kHaut(d - rayon), 1f)
        droit(cx, haut, g + rayon, haut, 0f, 1f, 1f, kHaut(g + rayon))
        arc(g + rayon, haut + rayon, -90f, -180f, kHaut(g + rayon), kCote(haut + rayon))
        droit(g, haut + rayon, g, bas - rayon, 1f, 0f, kCote(haut + rayon), kCote(bas - rayon))
        arc(g + rayon, bas - rayon, 180f, 90f, kCote(bas - rayon), kBas(g + rayon))
        droit(g + rayon, bas, cx, bas, 0f, -1f, kBas(g + rayon), 0f)
        point(cx, bas, 0f, -1f, 0f)

        nb = lx.size
        px = lx.toFloatArray()
        py = ly.toFloatArray()
        nx = lnx.toFloatArray()
        ny = lny.toFloatArray()
        spectre = lk.toFloatArray()
        position = FloatArray(nb)
        var cumul = 0f
        for (i in 1 until nb) {
            cumul += hypot(px[i] - px[i - 1], py[i] - py[i - 1])
            position[i] = cumul
        }
        for (i in 0 until nb) position[i] /= cumul

        // Énergie lissée le long du tube : chaque point mélange les bandes voisines (pas de marche)
        val nbB = AnalyseSpectre.NB_BANDES
        poids = FloatArray(nb * nbB)
        for (i in 0 until nb) {
            val p = spectre[i] * (nbB - 1)
            var somme = 0f
            for (b in 0 until nbB) {
                val x = (p - b) / 0.9f
                val wb = exp(-0.5f * x * x)
                poids[i * nbB + b] = wb
                somme += wb
            }
            for (b in 0 until nbB) poids[i * nbB + b] /= somme
        }

        // Arc-en-ciel : teinte de 0 (rouge) à 0,78 (violet), saturation 0,85
        arcR = FloatArray(nb)
        arcV = FloatArray(nb)
        arcB = FloatArray(nb)
        val hsv = FloatArray(3)
        for (i in 0 until nb) {
            hsv[0] = spectre[i] * 0.78f * 360f
            hsv[1] = 0.85f
            hsv[2] = 1f
            val c = Color.HSVToColor(hsv)
            arcR[i] = Color.red(c) / 255f
            arcV[i] = Color.green(c) / 255f
            arcB[i] = Color.blue(c) / 255f
        }

        // Couches de lumière, de la plus large à la plus fine
        val dedansLarge = 50f * u
        val dedansProche = 24f * u
        large = couche(
            -marge, dedansLarge, auBord = true,
            profil = profilFlou(34f * u, -marge, dedansLarge, 96, attenuer = true),
        )
        proche = couche(
            -marge, dedansProche, auBord = true,
            profil = profilFlou(10f * u, -marge, dedansProche, 64, attenuer = false),
        )
        tube = couche(-demi, demi, auBord = false, profil = FloatArray(32) { j -> profilTube(-demi + (j + 0.5f) / 32f * 2f * demi) })
        val ame = 0.35f * demi
        coeur = couche(-ame, ame, auBord = false, profil = FloatArray(16) { j -> 1f - abs(-ame + (j + 0.5f) / 16f * 2f * ame) / ame })
        lumieres = arrayOf(large, proche, tube, coeur)

        // Bord de l'app assombri (80 %) sur 30 u, qui s'estompe sur 25 u de plus. Maillage du
        // rectangle de l'écran (coins à onglet) : l'assombrissement suit le bord le plus proche.
        val profondeurs = floatArrayOf(0f, 30f * u, 55f * u)
        val sommets = FloatArray(5 * 3 * 2)
        val couleurs = IntArray(5 * 3)
        for (r in 0 until 3) {
            val p = profondeurs[r]
            val coins = floatArrayOf(p, p, w - p, p, w - p, h - p, p, h - p, p, p)
            for (c in 0 until 5) {
                sommets[(r * 5 + c) * 2] = coins[c * 2]
                sommets[(r * 5 + c) * 2 + 1] = coins[c * 2 + 1]
                couleurs[r * 5 + c] = if (r < 2) 0xCC000000.toInt() else 0
            }
        }
        val blanc = Bitmap.createBitmap(intArrayOf(-1, -1, -1, -1), 2, 2, Bitmap.Config.ARGB_8888)
        voile = Couche(blanc, sommets, couleurs, 4, 2, 1f)
    }

    /** Profil du tube en travers (d : distance à la ligne médiane). */
    private fun profilTube(d: Float): Float = (1f - abs(d) / demi).coerceIn(0f, 1f).pow(0.8f)

    /**
     * Profil d'un halo : le tube (×1,15) flouté par 3 boîtes successives (comme l'aperçu),
     * de [de] (vers le bord de l'écran, négatif) à [a] (vers l'intérieur), en [lignes] valeurs.
     */
    private fun profilFlou(rayonFlou: Float, de: Float, a: Float, lignes: Int, attenuer: Boolean): FloatArray {
        val pas = 0.5f
        val etendue = max(-de, a) + 3f * rayonFlou + 2f * demi + 4f
        val n = (2f * etendue / pas).toInt() + 1
        var v = FloatArray(n) { i -> 1.15f * profilTube(-etendue + i * pas) }
        val rb = max(1, (rayonFlou / 1.7f).toInt())
        val rs = max(1, (rb / pas).roundToInt())
        repeat(3) { v = boite(v, rs) }
        return FloatArray(lignes) { j ->
            val d = de + (j + 0.5f) / lignes * (a - de)
            val x = (d + etendue) / pas
            val i0 = floor(x).toInt().coerceIn(0, n - 2)
            val f = x - i0
            var valeur = v[i0] * (1f - f) + v[i0 + 1] * f
            if (attenuer && d > 0f) {
                // Fin du halo intérieur : s'éteint sans bord net avant la limite de la bande
                val t = ((d - 0.6f * a) / (0.4f * a)).coerceIn(0f, 1f)
                valeur *= 1f - t * t * (3f - 2f * t)
            }
            valeur
        }
    }

    private fun boite(v: FloatArray, r: Int): FloatArray {
        val n = v.size
        val cumul = DoubleArray(n + 1)
        for (i in 0 until n) cumul[i + 1] = cumul[i] + v[i]
        return FloatArray(n) { i ->
            val lo = max(0, i - r)
            val hi = min(n, i + r + 1)
            ((cumul[hi] - cumul[lo]) / (2 * r + 1)).toFloat()
        }
    }

    /**
     * Maillage d'une couche : une rangée de sommets côté bord de l'écran ([de], ou jusqu'au bord
     * si [auBord]), une côté intérieur ([a]). Côté intérieur, les sommets restent dans le
     * rectangle intérieur : pas de repli dans les coins quand le halo dépasse leur rayon.
     */
    private fun couche(de: Float, a: Float, auBord: Boolean, profil: FloatArray): Couche {
        val w = largeur.toFloat()
        val h = hauteur.toFloat()
        val sommets = FloatArray(nb * 4)
        val xMin = marge + a
        val xMax = max(xMin, w - marge - a)
        val yMin = marge + a
        val yMax = max(yMin, h - marge - a)
        for (i in 0 until nb) {
            val t = if (auBord) distanceAuBord(px[i], py[i], -nx[i], -ny[i]) else -de
            sommets[i * 2] = px[i] - nx[i] * t
            sommets[i * 2 + 1] = py[i] - ny[i] * t
            sommets[(nb + i) * 2] = (px[i] + nx[i] * a).coerceIn(xMin, xMax)
            sommets[(nb + i) * 2 + 1] = (py[i] + ny[i] * a).coerceIn(yMin, yMax)
        }
        var pic = 0f
        for (p in profil) pic = max(pic, p)
        if (pic <= 0f) pic = 1f
        val pixels = IntArray(profil.size * 2)
        for (j in profil.indices) {
            val alpha = (profil[j] / pic * 255f).roundToInt().coerceIn(0, 255)
            val c = Color.argb(alpha, 255, 255, 255)
            pixels[j * 2] = c
            pixels[j * 2 + 1] = c
        }
        val bitmap = Bitmap.createBitmap(pixels, 2, profil.size, Bitmap.Config.ARGB_8888)
        return Couche(bitmap, sommets, IntArray(nb * 2), nb - 1, 1, pic)
    }

    /** Distance jusqu'au bord de l'écran depuis (x, y) dans la direction (dx, dy). */
    private fun distanceAuBord(x: Float, y: Float, dx: Float, dy: Float): Float {
        var t = Float.MAX_VALUE
        if (dx > 1e-4f) t = min(t, (largeur - x) / dx) else if (dx < -1e-4f) t = min(t, -x / dx)
        if (dy > 1e-4f) t = min(t, (hauteur - y) / dy) else if (dy < -1e-4f) t = min(t, -y / dy)
        return if (t == Float.MAX_VALUE) marge else t
    }

    // ------------------------------------------------------------------ chaque image

    /** Couleurs des sommets pour cette image (à appeler une fois par image, avant [dessiner]). */
    fun preparer(m: MoteurNeon) {
        val nbB = AnalyseSpectre.NB_BANDES
        val lumiere = m.intensite.lumiere
        val sur = m.sursaut
        val arc = m.teinte == Teinte.ARC_EN_CIEL
        val gTube = tube.gain * 1.15f
        val gProche = proche.gain * 1.4f
        val gLarge = large.gain * (1.5f + 1.2f * sur)
        val phase = m.temps * 0.35f
        // Nuances de la couleur de base : sombre (×0,35) et claire (55 % vers le blanc)
        val dr = m.rouge
        val dv = m.vert
        val db = m.bleu
        val sr = dr * 0.35f
        val sv = dv * 0.35f
        val sb = db * 0.35f
        val cr = dr + (1f - dr) * 0.55f
        val cv = dv + (1f - dv) * 0.55f
        val cb = db + (1f - db) * 0.55f
        val deuxPi = 2f * PI.toFloat()
        for (i in 0 until nb) {
            var e = 0f
            val o = i * nbB
            for (b in 0 until nbB) e += poids[o + b] * m.enveloppes[b]
            var r: Float
            var v: Float
            var bl: Float
            val inten: Float
            val blanc: Float
            if (arc) {
                r = arcR[i]
                v = arcV[i]
                bl = arcB[i]
                inten = (0.15f + 0.85f * e) * (0.85f + 0.3f * sur)
                blanc = 0.55f * e + 0.3f * sur
            } else {
                // Vagues lentes de nuances (3 sur le tour, un tour en ~3 s), vers le clair au sursaut
                val vague = 0.5f + 0.5f * sin(deuxPi * (3f * position[i] - phase))
                val n = (vague * 0.7f + sur * 0.5f).coerceIn(0f, 1f)
                if (n < 0.5f) {
                    val t = n * 2f
                    r = sr + (dr - sr) * t
                    v = sv + (dv - sv) * t
                    bl = sb + (db - sb) * t
                } else {
                    val t = (n - 0.5f) * 2f
                    r = dr + (cr - dr) * t
                    v = dv + (cv - dv) * t
                    bl = db + (cb - db) * t
                }
                inten = (0.35f + 0.65f * e) * (0.75f + 0.5f * sur)
                blanc = 0.6f * sur
            }
            val f = inten * lumiere
            r *= f
            v *= f
            bl *= f
            val w = blanc * lumiere
            val c = emballer(r * gTube, v * gTube, bl * gTube)
            tube.couleurs[i] = c
            tube.couleurs[nb + i] = c
            val rc = min(r, 1f)
            val vc = min(v, 1f)
            val bc = min(bl, 1f)
            // Âme : ce qui manque pour aller vers le blanc
            val a = emballer((1f - rc) * w, (1f - vc) * w, (1f - bc) * w)
            coeur.couleurs[i] = a
            coeur.couleurs[nb + i] = a
            // Halo : la couleur du tube, avec un peu du blanc de l'âme
            val hr = r + (1f - rc) * w * 0.25f
            val hv = v + (1f - vc) * w * 0.25f
            val hb = bl + (1f - bc) * w * 0.25f
            val p = emballer(hr * gProche, hv * gProche, hb * gProche)
            proche.couleurs[i] = p
            proche.couleurs[nb + i] = p
            val l = emballer(hr * gLarge, hv * gLarge, hb * gLarge)
            large.couleurs[i] = l
            large.couleurs[nb + i] = l
        }
    }

    /** Dessine le néon en coordonnées de l'écran (une bande ne garde que sa partie). */
    fun dessiner(canvas: Canvas) {
        canvas.drawBitmapMesh(voile.bitmap, voile.colonnes, voile.lignes, voile.sommets, 0, voile.couleurs, 0, pinceauVoile)
        for (c in lumieres) {
            canvas.drawBitmapMesh(c.bitmap, c.colonnes, c.lignes, c.sommets, 0, c.couleurs, 0, pinceauLumiere)
        }
    }

    companion object {
        /**
         * Lumière (r, v, b de 0 à 1, déjà multipliés par l'intensité) en couleur de sommet :
         * l'opacité suit la composante la plus forte (une lumière faible ne masque pas l'app).
         */
        fun emballer(r: Float, v: Float, b: Float): Int {
            val rr = r.coerceIn(0f, 1f)
            val vv = v.coerceIn(0f, 1f)
            val bb = b.coerceIn(0f, 1f)
            val mx = max(rr, max(vv, bb))
            if (mx < 1f / 255f) return 0
            val a = (mx * 255f + 0.5f).toInt()
            return (a shl 24) or ((rr / mx * 255f + 0.5f).toInt() shl 16) or
                ((vv / mx * 255f + 0.5f).toInt() shl 8) or (bb / mx * 255f + 0.5f).toInt()
        }
    }
}
