package fr.projo.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.palette.graphics.Palette

/** Logo d'une app + sa couleur dominante (pour la carte et la lueur du fond). */
class AppVisual(val icon: ImageBitmap?, val color: Color)

/**
 * Charge les logos en petite taille et les garde en mémoire (cache limité à ~6 Mo) :
 * le projecteur n'a qu'1 Go de RAM. La couleur dominante est calculée sur une image
 * réduite à 24 px, ce qui est quasi instantané.
 */
class IconLoader(private val context: Context) {

    private val cache = object : LruCache<String, AppVisual>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppVisual): Int =
            value.icon?.let { it.width * it.height * 4 } ?: 64
    }

    fun cached(packageName: String): AppVisual? = cache.get(packageName)

    /** À appeler hors du thread principal. */
    fun load(packageName: String): AppVisual {
        cache.get(packageName)?.let { return it }
        val visual = try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bmp = drawable.toBitmap(TAILLE_PX)
            AppVisual(bmp.asImageBitmap(), dominantColor(bmp))
        } catch (e: Exception) {
            AppVisual(null, COULEUR_PAR_DEFAUT)
        }
        cache.put(packageName, visual)
        return visual
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bmp
    }

    private fun dominantColor(bmp: Bitmap): Color {
        val petit = Bitmap.createScaledBitmap(bmp, 24, 24, true)
        val palette = Palette.from(petit).maximumColorCount(8).generate()
        if (petit !== bmp) petit.recycle()
        // On préfère une teinte vive ; sinon la dominante ; sinon gris-bleu
        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
        return swatch?.let { Color(it.rgb) } ?: COULEUR_PAR_DEFAUT
    }

    companion object {
        /** Taille des logos décodés (suffisant pour des cartes de ~80 dp). */
        const val TAILLE_PX = 128
        val COULEUR_PAR_DEFAUT = Color(0xFF4A5A78)
    }
}
