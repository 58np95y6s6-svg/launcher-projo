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
 * Images petites (400 × 225 px, 32 bits par pixel pour des dégradés lisses) et cache limité à 14 Mo :
 * le projecteur n'a qu'1 Go de RAM.
 */
class IconLoader(private val context: Context) {

    private val cache = object : LruCache<String, AppVisual>(14 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppVisual): Int =
            value.art?.let { it.width * it.height * 4 } ?: 64
    }

    fun cached(packageName: String): AppVisual? = cache.get(packageName)

    private val icones = object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cachedIcon(packageName: String): ImageBitmap? = icones.get(packageName)

    /**
     * Icône carrée de l'app (160 px, transparente autour pour les anciennes icônes).
     * Icône adaptative : fond + logo, recadrés comme sur un écran d'accueil.
     * Null pour les tuiles propres à Projo. À appeler hors du thread principal.
     */
    fun loadIcon(packageName: String): ImageBitmap? {
        icones.get(packageName)?.let { return it }
        if (packageName.startsWith("projo:")) return null
        return try {
            val d = context.packageManager.getApplicationIcon(packageName)
            val b = Bitmap.createBitmap(ICONE, ICONE, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable && d.background != null) {
                // Le calque fait 108 unités, dont ~72 visibles sur un écran d'accueil
                val e = (ICONE * 108f / 72f).toInt()
                val o = (ICONE - e) / 2
                d.background.setBounds(o, o, o + e, o + e); d.background.draw(c)
                d.foreground?.let { it.setBounds(o, o, o + e, o + e); it.draw(c) }
            } else {
                d.setBounds(0, 0, ICONE, ICONE); d.draw(c)
            }
            b.asImageBitmap().also { icones.put(packageName, it) }
        } catch (e: Exception) {
            null
        }
    }

    /** À appeler hors du thread principal. */
    fun load(packageName: String): AppVisual {
        cache.get(packageName)?.let { return it }
        // Tuiles propres à Projo : dessinées par l'interface, couleur fixe
        if (packageName.startsWith("projo:")) return AppVisual(null, COULEUR_HDMI)
        val visual = try {
            val art = Bitmap.createBitmap(LARGEUR, HAUTEUR, Bitmap.Config.ARGB_8888)
            // Bannière TV officielle de l'app si elle en a une, sinon carte fabriquée depuis l'icône
            val couleur = banniereTv(packageName)?.let { dessinerBanniere(it, Canvas(art)) }
                ?: dessinerCarte(context.packageManager.getApplicationIcon(packageName), Canvas(art))
            // 32 bits par pixel : les dégradés restent lisses (le 16 bits faisait des bandes)
            AppVisual(art.asImageBitmap(), couleur)
        } catch (e: Exception) {
            AppVisual(null, COULEUR_PAR_DEFAUT)
        }
        cache.put(packageName, visual)
        return visual
    }

    /**
     * Bannière 16:9 que l'app fournit pour les écrans d'accueil TV (android:banner) :
     * celle de l'activité TV d'abord, puis celle de l'app. Null si l'app n'en a pas.
     */
    private fun banniereTv(packageName: String): Drawable? = try {
        val pm = context.packageManager
        pm.getLeanbackLaunchIntentForPackage(packageName)?.let { pm.getActivityBanner(it) }
            ?: pm.getApplicationBanner(packageName)
    } catch (e: Exception) {
        null
    }

    /** Bannière TV recadrée pour remplir la carte ; renvoie sa couleur pour la lueur. */
    private fun dessinerBanniere(banniere: Drawable, canvas: Canvas): Color {
        val couleur = couleurVive(banniere) ?: couleurDominante(banniere, avecVive = false)
        // Fond sombre de sa couleur, au cas où la bannière a des zones transparentes
        canvas.drawColor(
            assombrir(
                android.graphics.Color.rgb(
                    (couleur.red * 255).toInt(), (couleur.green * 255).toInt(), (couleur.blue * 255).toInt(),
                ),
                0.6f,
            ),
        )
        val w = banniere.intrinsicWidth
        val h = banniere.intrinsicHeight
        if (w <= 0 || h <= 0) {
            banniere.setBounds(0, 0, LARGEUR, HAUTEUR)
        } else {
            // Remplit toute la carte en gardant les proportions (recadrage au centre)
            val echelle = max(LARGEUR / w.toFloat(), HAUTEUR / h.toFloat())
            val dw = (w * echelle).toInt()
            val dh = (h * echelle).toInt()
            val x = (LARGEUR - dw) / 2
            val y = (HAUTEUR - dh) / 2
            banniere.setBounds(x, y, x + dw, y + dh)
        }
        banniere.draw(canvas)
        return couleur
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

        // Ancienne icône (la plupart des apps Fire TV) : on regarde d'abord le fond de l'icône
        val couleur = couleurVive(icone) ?: couleurDominante(icone, avecVive = false)
        val fondUni = couleurDuBord(icone)
        if (fondUni != null) {
            // Icône « pleine » (Netflix, Prime Video, YouTube…) : sa couleur de fond remplit
            // toute la carte et le logo s'affiche en grand → rendu « bannière », sans raccord visible
            canvas.drawColor(fondUni)
            val taille = (HAUTEUR * 0.94f).toInt()
            val x = (LARGEUR - taille) / 2
            val y = (HAUTEUR - taille) / 2
            icone.setBounds(x, y, x + taille, y + taille)
            icone.draw(canvas)
            return couleur
        }

        // Icône détourée : dégradé doux (tramé, donc sans bandes) de sa couleur + grande icône
        val argb = android.graphics.Color.argb(
            255, (couleur.red * 255).toInt(), (couleur.green * 255).toInt(), (couleur.blue * 255).toInt(),
        )
        val paint = Paint().apply {
            isDither = true
            shader = LinearGradient(
                0f, 0f, LARGEUR.toFloat(), HAUTEUR.toFloat(),
                assombrir(argb, 0.15f), assombrir(argb, 0.55f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, LARGEUR.toFloat(), HAUTEUR.toFloat(), paint)
        val taille = (HAUTEUR * 0.68f).toInt()
        val x = (LARGEUR - taille) / 2
        val y = (HAUTEUR - taille) / 2
        icone.setBounds(x, y, x + taille, y + taille)
        icone.draw(canvas)
        return couleur
    }

    /**
     * Couleur du fond d'une icône si son bord est opaque et d'une seule couleur (icône carrée
     * ou à coins arrondis), sinon null. On échantillonne le milieu des 4 côtés, loin des coins.
     */
    private fun couleurDuBord(icone: Drawable): Int? {
        val n = 96
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val anciennes = icone.copyBounds()
        icone.setBounds(0, 0, n, n)
        icone.draw(Canvas(b))
        icone.bounds = anciennes
        val bord = 3
        val points = mutableListOf<Int>()
        for (i in 0 until 7) {
            val t = 30 + i * 6 // de 30 % à 66 % de chaque côté
            points += b.getPixel(t, bord)
            points += b.getPixel(t, n - 1 - bord)
            points += b.getPixel(bord, t)
            points += b.getPixel(n - 1 - bord, t)
        }
        b.recycle()
        if (points.any { android.graphics.Color.alpha(it) < 245 }) return null
        val r = points.map { android.graphics.Color.red(it) }.average().toInt()
        val g = points.map { android.graphics.Color.green(it) }.average().toInt()
        val bl = points.map { android.graphics.Color.blue(it) }.average().toInt()
        val uni = points.all {
            kotlin.math.abs(android.graphics.Color.red(it) - r) + kotlin.math.abs(android.graphics.Color.green(it) - g) +
                kotlin.math.abs(android.graphics.Color.blue(it) - bl) < 36
        }
        return if (uni) android.graphics.Color.rgb(r, g, bl) else null
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
        const val LARGEUR = 400
        const val HAUTEUR = 225
        /** Taille des icônes carrées. */
        const val ICONE = 160
        val COULEUR_PAR_DEFAUT = Color(0xFF4A5A78)
        val COULEUR_HDMI = Color(0xFF2A9D8F)
    }
}
