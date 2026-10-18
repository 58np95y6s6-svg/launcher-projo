package fr.projo.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.palette.graphics.Palette
import kotlin.math.max

/**
 * Visuel d'une app : une image « carte » au format 16:9, prête à afficher, et la couleur
 * dominante (pour la lueur du fond).
 */
class AppVisual(val art: ImageBitmap?, val color: Color)

/**
 * Fabrique les cartes d'apps façon Apple TV, une seule fois par app, puis les garde en cache.
 *
 *  - Icône « adaptative » (Android 8+, la majorité des apps) : son calque de FOND remplit
 *    toute la carte et son LOGO est posé en grand au centre → rendu pleine image.
 *  - Ancienne icône : fond dégradé de sa couleur dominante + icône centrée.
 *
 * Images petites (352 × 198 px, 16 bits par pixel, ~140 Ko) et cache limité à 8 Mo :
 * le projecteur n'a qu'1 Go de RAM.
 */
class IconLoader(private val context: Context) {

    private val cache = object : LruCache<String, AppVisual>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppVisual): Int =
            value.art?.let { it.width * it.height * 2 } ?: 64
    }

    fun cached(packageName: String): AppVisual? = cache.get(packageName)

    /** À appeler hors du thread principal. */
    fun load(packageName: String): AppVisual {
        cache.get(packageName)?.let { return it }
        // Tuiles propres à Projo : dessinées par l'interface, couleur fixe
        if (packageName.startsWith("projo:")) return AppVisual(null, COULEUR_HDMI)
        val visual = try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val art = Bitmap.createBitmap(LARGEUR, HAUTEUR, Bitmap.Config.ARGB_8888)
            val couleur = dessinerCarte(drawable, Canvas(art))
            // 16 bits par pixel : moitié moins de mémoire, invisible sur une carte opaque
            val compacte = art.copy(Bitmap.Config.RGB_565, false)
            art.recycle()
            AppVisual(compacte.asImageBitmap(), couleur)
        } catch (e: Exception) {
            AppVisual(null, COULEUR_PAR_DEFAUT)
        }
        cache.put(packageName, visual)
        return visual
    }

    /** Dessine la carte et renvoie la couleur dominante. */
    private fun dessinerCarte(icone: Drawable, canvas: Canvas): Color {
        if (Build.VERSION.SDK_INT >= 26 && icone is AdaptiveIconDrawable && icone.background != null) {
            // 1. Fond de l'icône étiré pour couvrir toute la carte (recadré au centre)
            val fond = icone.background
            val cote = max(LARGEUR, HAUTEUR)
            fond.setBounds(0, (HAUTEUR - cote) / 2, cote, (HAUTEUR + cote) / 2)
            fond.draw(canvas)
            val couleurFond = couleurDominante(fond, avecVive = false)
            // 2. Logo (calque avant) en grand au centre. Le calque fait 108 unités dont
            //    seules les 66 du centre sont garanties visibles : on le dimensionne en conséquence.
            icone.foreground?.let { avant ->
                val taille = (HAUTEUR * 1.05f).toInt()
                val x = (LARGEUR - taille) / 2
                val y = (HAUTEUR - taille) / 2
                avant.setBounds(x, y, x + taille, y + taille)
                avant.draw(canvas)
            }
            return couleurVive(icone) ?: couleurFond
        }

        // Ancienne icône : fond dégradé de sa couleur + icône centrée
        val couleur = couleurVive(icone) ?: couleurDominante(icone, avecVive = false)
        val argb = android.graphics.Color.argb(
            255, (couleur.red * 255).toInt(), (couleur.green * 255).toInt(), (couleur.blue * 255).toInt(),
        )
        val haut = assombrir(argb, 0.30f)
        val bas = assombrir(argb, 0.65f)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, HAUTEUR.toFloat(), haut, bas, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, LARGEUR.toFloat(), HAUTEUR.toFloat(), paint)
        val taille = (HAUTEUR * 0.56f).toInt()
        val x = (LARGEUR - taille) / 2
        val y = (HAUTEUR - taille) / 2
        icone.setBounds(x, y, x + taille, y + taille)
        icone.draw(canvas)
        return couleur
    }

    /** Couleur vive de l'icône (pour une lueur colorée même si le fond est blanc ou noir). */
    private fun couleurVive(icone: Drawable): Color? {
        val petit = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        icone.setBounds(0, 0, 32, 32)
        icone.draw(Canvas(petit))
        val p = Palette.from(petit).maximumColorCount(12).generate()
        petit.recycle()
        val s = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.darkVibrantSwatch ?: return null
        return Color(s.rgb)
    }

    private fun couleurDominante(d: Drawable, avecVive: Boolean): Color {
        val petit = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val anciennes = d.copyBounds()
        d.setBounds(0, 0, 24, 24)
        d.draw(Canvas(petit))
        d.bounds = anciennes
        val p = Palette.from(petit).maximumColorCount(8).generate()
        petit.recycle()
        val s = (if (avecVive) p.vibrantSwatch else null) ?: p.dominantSwatch ?: p.mutedSwatch
        return s?.let { Color(it.rgb) } ?: COULEUR_PAR_DEFAUT
    }

    private fun assombrir(argb: Int, part: Float): Int {
        val f = 1f - part
        return android.graphics.Color.rgb(
            (android.graphics.Color.red(argb) * f).toInt(),
            (android.graphics.Color.green(argb) * f).toInt(),
            (android.graphics.Color.blue(argb) * f).toInt(),
        )
    }

    companion object {
        /** Taille des cartes générées (16:9). */
        const val LARGEUR = 352
        const val HAUTEUR = 198
        val COULEUR_PAR_DEFAUT = Color(0xFF4A5A78)
        val COULEUR_HDMI = Color(0xFF2A9D8F)
    }
}
