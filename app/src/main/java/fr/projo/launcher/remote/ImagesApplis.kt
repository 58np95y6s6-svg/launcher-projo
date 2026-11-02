package fr.projo.launcher.remote

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Images des applis pour le téléphone (`app_image`) : la bannière TV de l'appli
 * (android:banner, 16:9) réduite à 240 × 135, sinon son icône en 96 × 96. Encodée en WebP
 * (PNG si le WebP échoue) dans une URL data: de 48 Ko au plus, qualité baissée si besoin.
 * Bloquant : hors du fil principal (fil « pilotage »).
 */
internal object ImagesApplis {

    private const val BANNIERE_L = 240
    private const val BANNIERE_H = 135
    private const val ICONE = 96

    /** Qualités WebP essayées, de la meilleure à la plus légère. */
    private val QUALITES = listOf(85, 70, 50, 30)

    /** (forme, URL data:) ; null si l'appli est introuvable ou l'image trop lourde. */
    fun fabriquer(context: Context, paquet: String): Pair<String, String>? {
        val pm = context.packageManager
        banniere(pm, paquet)?.let { b ->
            encoder(dessiner(b, BANNIERE_L, BANNIERE_H, remplir = true))?.let { return ImageAppli.BANNIERE to it }
        }
        val icone = try {
            pm.getApplicationIcon(paquet)
        } catch (e: Exception) {
            return null
        }
        return encoder(dessiner(icone, ICONE, ICONE, remplir = false))?.let { ImageAppli.ICONE to it }
    }

    /** Bannière de l'activité TV d'abord, puis celle de l'appli ; null si l'appli n'en a pas. */
    private fun banniere(pm: PackageManager, paquet: String): Drawable? {
        val activite = try {
            pm.getLeanbackLaunchIntentForPackage(paquet)?.let { pm.getActivityBanner(it) }
        } catch (e: Exception) {
            null
        }
        return activite ?: try {
            pm.getApplicationBanner(paquet)
        } catch (e: Exception) {
            null
        }
    }

    /** [d] dans un cadre w × h : remplir (recadrage au centre) ou contenir (marges transparentes). */
    private fun dessiner(d: Drawable, w: Int, h: Int, remplir: Boolean): Bitmap {
        val image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val iw = d.intrinsicWidth
        val ih = d.intrinsicHeight
        if (iw <= 0 || ih <= 0) {
            d.setBounds(0, 0, w, h)
        } else {
            val ew = w / iw.toFloat()
            val eh = h / ih.toFloat()
            val e = if (remplir) max(ew, eh) else min(ew, eh)
            val dw = (iw * e).roundToInt()
            val dh = (ih * e).roundToInt()
            val x = (w - dw) / 2
            val y = (h - dh) / 2
            d.setBounds(x, y, x + dw, y + dh)
        }
        d.draw(Canvas(image))
        return image
    }

    /** URL data: de 48 Ko au plus, ou null. Libère [image]. */
    private fun encoder(image: Bitmap): String? {
        try {
            for (q in QUALITES) {
                val octets = compresser(image, formatWebp(), q) ?: break
                val url = "data:image/webp;base64," + Base64.encodeToString(octets, Base64.NO_WRAP)
                if (url.length <= Protocole.IMAGE_MAX) return url
            }
            // WebP indisponible (ou trop lourd) : PNG, s'il tient
            val png = compresser(image, Bitmap.CompressFormat.PNG, 100) ?: return null
            val url = "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP)
            return if (url.length <= Protocole.IMAGE_MAX) url else null
        } finally {
            image.recycle()
        }
    }

    private fun compresser(image: Bitmap, format: Bitmap.CompressFormat, qualite: Int): ByteArray? {
        val sortie = ByteArrayOutputStream()
        val ok = image.compress(format, qualite, sortie)
        return if (ok && sortie.size() > 0) sortie.toByteArray() else null
    }

    @Suppress("DEPRECATION")
    private fun formatWebp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
}
