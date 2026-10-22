package fr.projo.launcher.ambiance

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadata
import android.media.audiofx.Visualizer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Son en direct : Visualizer d'Android sur la sortie générale (session 0). Il ne donne que
 * le spectre (FFT) du son qui sort, à l'instant : rien n'est enregistré, gardé ni envoyé.
 * Lu à chaque image (getFft dans un tableau réutilisé) : plus frais que le rappel du
 * Visualizer, limité à getMaxCaptureRate (~20 fois par seconde), et sans allocation.
 * Il faut l'autorisation « micro » (RECORD_AUDIO) et MODIFY_AUDIO_SETTINGS (session 0).
 */
class CaptureSon(private val context: Context) {

    enum class Etat { FERMEE, ACTIVE, REFUSEE, INDISPONIBLE }

    var etat = Etat.FERMEE
        private set

    private var visualiseur: Visualizer? = null

    /** Dernière FFT lue (taille de la capture). */
    var fft = ByteArray(0)
        private set
    var taille = 0
        private set
    var frequenceHz = 44_100
        private set

    fun ouvrir() {
        if (visualiseur != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            etat = Etat.REFUSEE
            return
        }
        var v: Visualizer? = null
        try {
            v = Visualizer(0)
            v.enabled = false
            val plage = Visualizer.getCaptureSizeRange()
            v.captureSize = plage[1].coerceAtMost(1024).coerceAtLeast(plage[0])
            v.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            v.enabled = true
            taille = v.captureSize
            // Fréquence donnée en milliHertz
            frequenceHz = (v.samplingRate / 1000).takeIf { it > 0 } ?: 44_100
            if (fft.size != taille) fft = ByteArray(taille)
            visualiseur = v
            etat = Etat.ACTIVE
        } catch (e: Exception) {
            liberer(v)
            etat = Etat.INDISPONIBLE
        } catch (e: LinkageError) {
            liberer(v)
            etat = Etat.INDISPONIBLE
        }
    }

    /** Lit la FFT du moment dans [fft] ; false si rien à lire. */
    fun lire(): Boolean = try {
        visualiseur?.getFft(fft) == Visualizer.SUCCESS
    } catch (e: Exception) {
        false
    }

    fun fermer() {
        liberer(visualiseur)
        visualiseur = null
        if (etat == Etat.ACTIVE) etat = Etat.FERMEE
    }

    private fun liberer(v: Visualizer?) {
        if (v == null) return
        try { v.enabled = false } catch (e: Exception) { /* déjà arrêté */ }
        try { v.release() } catch (e: Exception) { /* déjà libéré */ }
    }
}

/**
 * Couleur de la pochette du morceau en cours dans l'app [paquet] : session média de l'app
 * (MediaSessionManager, permis par l'accès aux notifications d'[EcouteNotifications]),
 * image de la pochette, couleur dominante (Palette, hors du fil principal).
 * [surCouleur] reçoit la couleur (null : pas de pochette), sur le fil principal.
 */
class LecteurPochette(private val context: Context, private val surCouleur: (Int?) -> Unit) {

    enum class Etat { ARRETE, SANS_ACCES, SANS_SESSION, SANS_IMAGE, OK }

    var etat = Etat.ARRETE
        private set

    private val principal = Handler(Looper.getMainLooper())
    private var gestionnaire: MediaSessionManager? = null
    private var controleur: MediaController? = null
    private var paquet: String? = null

    /** Change à chaque nouvelle image demandée : un ancien calcul arrivé en retard est ignoré. */
    private var generation = 0

    private val suiviSessions = MediaSessionManager.OnActiveSessionsChangedListener { choisir(it) }

    private val suiviMorceau = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = lireImage(metadata)
        override fun onSessionDestroyed() = choisir(null)
    }

    fun demarrer(paquet: String) {
        arreter()
        this.paquet = paquet
        if (!ModeAmbiance.notificationsAutorisees(context)) {
            etat = Etat.SANS_ACCES
            return
        }
        try {
            val m = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val composant = ComponentName(context, EcouteNotifications::class.java)
            m.addOnActiveSessionsChangedListener(suiviSessions, composant, principal)
            gestionnaire = m
            choisir(m.getActiveSessions(composant))
        } catch (e: SecurityException) {
            etat = Etat.SANS_ACCES
        } catch (e: Exception) {
            etat = Etat.SANS_SESSION
        }
    }

    private fun choisir(sessions: List<MediaController>?) {
        val c = sessions?.firstOrNull { it.packageName == paquet }
        if (c != null && c.sessionToken == controleur?.sessionToken) return
        try { controleur?.unregisterCallback(suiviMorceau) } catch (e: Exception) { /* session finie */ }
        controleur = c
        if (c == null) {
            generation++
            etat = Etat.SANS_SESSION
            surCouleur(null)
            return
        }
        try {
            c.registerCallback(suiviMorceau, principal)
            lireImage(c.metadata)
        } catch (e: Exception) {
            etat = Etat.SANS_SESSION
            surCouleur(null)
        }
    }

    private fun lireImage(m: MediaMetadata?) {
        val gen = ++generation
        val image = m?.let {
            it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }
        val adresse = if (image != null) null else m?.let {
            it.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: it.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: it.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        }
        if (image == null && adresse.isNullOrBlank()) {
            etat = Etat.SANS_IMAGE
            surCouleur(null)
            return
        }
        val appli = context.applicationContext
        val fil = Thread {
            val couleur = try {
                val b = image ?: telecharger(appli, adresse!!)
                b?.let { couleurDeLImage(it) }
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
            principal.post {
                if (gen != generation) return@post
                etat = if (couleur != null) Etat.OK else Etat.SANS_IMAGE
                surCouleur(couleur)
            }
        }
        fil.name = "ambiance-pochette"
        fil.isDaemon = true
        fil.start()
    }

    fun arreter() {
        generation++
        try { controleur?.unregisterCallback(suiviMorceau) } catch (e: Exception) { /* session finie */ }
        controleur = null
        try { gestionnaire?.removeOnActiveSessionsChangedListener(suiviSessions) } catch (e: Exception) { /* déjà retiré */ }
        gestionnaire = null
        paquet = null
        etat = Etat.ARRETE
    }

    companion object {
        /** Image de la pochette donnée par adresse (content:// ou https://), réduite (~160 px). */
        fun telecharger(context: Context, adresse: String): Bitmap? {
            val uri = Uri.parse(adresse)
            val octets = when (uri.scheme?.lowercase()) {
                "content", "android.resource", "file" -> context.contentResolver.openInputStream(uri)?.use { lireTout(it) }
                "http", "https" -> {
                    val c = URL(adresse).openConnection() as HttpURLConnection
                    c.connectTimeout = 5_000
                    c.readTimeout = 5_000
                    try { c.inputStream.use { lireTout(it) } } finally { c.disconnect() }
                }
                else -> null
            } ?: return null
            val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
            var echantillon = 1
            while (bornes.outWidth / (echantillon * 2) >= 160 && bornes.outHeight / (echantillon * 2) >= 160) echantillon *= 2
            return BitmapFactory.decodeByteArray(octets, 0, octets.size, BitmapFactory.Options().apply { inSampleSize = echantillon })
        }

        /** Lit au plus 4 Mo. */
        private fun lireTout(flux: InputStream): ByteArray? {
            val sortie = ByteArrayOutputStream()
            val tampon = ByteArray(16 * 1024)
            while (true) {
                val n = flux.read(tampon)
                if (n < 0) break
                sortie.write(tampon, 0, n)
                if (sortie.size() > 4 * 1024 * 1024) return null
            }
            return sortie.toByteArray()
        }

        /** Couleur du néon tirée d'une image (pochette, icône). */
        fun couleurDeLImage(image: Bitmap): Int? {
            val p = Palette.from(image).maximumColorCount(16).resizeBitmapArea(112 * 112).clearFilters().generate()
            val nuances = p.swatches
            if (nuances.isEmpty()) return null
            return couleurNeon(IntArray(nuances.size) { nuances[it].rgb }, IntArray(nuances.size) { nuances[it].population })
        }

        /**
         * Couleur dominante : la plus saturée parmi les plus présentes (au moins 25 % de la plus
         * présente), en préférant les couleurs claires aux sombres, puis rendue lumineuse
         * pour un néon (valeur ≥ 0,9). Image grise : blanc doux.
         */
        fun couleurNeon(couleurs: IntArray, populations: IntArray): Int? {
            if (couleurs.isEmpty()) return null
            val maxPop = populations.max()
            val hsv = FloatArray(3)
            var choix = -1
            var meilleur = -1f
            for (i in couleurs.indices) {
                if (populations[i] < maxPop * 0.25f) continue
                Color.colorToHSV(couleurs[i], hsv)
                // Couleur sombre : sa teinte représente mal la pochette (fond nuit, ombres)
                val clarte = ((hsv[2] - 0.15f) / 0.45f).coerceIn(0f, 1f)
                val note = hsv[1] * (0.25f + 0.75f * clarte) * (0.6f + 0.4f * populations[i] / maxPop)
                if (note > meilleur) { meilleur = note; choix = i }
            }
            if (choix < 0) choix = populations.indices.maxBy { populations[it] }
            Color.colorToHSV(couleurs[choix], hsv)
            if (hsv[1] < 0.12f) {
                // Pochette en noir et blanc : néon blanc légèrement chaud
                return 0xFFF2E6
            }
            // Saturation bornée : ni délavé, ni criard (rouge pur d'une icône)
            hsv[1] = hsv[1].coerceIn(0.45f, 0.88f)
            hsv[2] = hsv[2].coerceAtLeast(0.9f)
            return Color.HSVToColor(hsv) and 0xFFFFFF
        }
    }
}

/**
 * Accès aux notifications d'iBiProjo, demandé par le mode Ambiance. Ce service ne lit AUCUNE
 * notification : sa seule présence permet de connaître le morceau en cours (session
 * média) pour prendre la couleur de sa pochette. À activer une fois :
 * adb shell cmd notification allow_listener fr.projo.launcher/fr.projo.launcher.ambiance.EcouteNotifications
 */
class EcouteNotifications : NotificationListenerService()
