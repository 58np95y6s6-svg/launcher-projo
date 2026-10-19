package fr.projo.launcher.modules.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.outils.FluxReseau
import fr.projo.launcher.modules.outils.TexteFlux
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Photo du jour prête à afficher : image réduite enregistrée dans le cache de l'appli.
 *  - [jour] : date de la photo (yyyyMMdd) ;
 *  - [video] : miniature d'une vidéo ;
 *  - [ancienne] : photo d'un jour précédent (celle du jour est une vidéo sans miniature, ou la
 *    source ne répond pas et l'on garde la dernière connue).
 */
data class PhotoDuJour(
    val titre: String,
    val jour: String,
    val fichier: String,
    val video: Boolean = false,
    val ancienne: Boolean = false,
)

/** Ce que la source annonce : titre et adresse de l'image (ou de la miniature). */
data class AnnoncePhoto(val titre: String, val image: String?, val video: Boolean)

/**
 * Astronomy Picture of the Day (NASA), sans compte :
 *  1. la page publique https://apod.nasa.gov/apod/astropix.html (image « _1024 » déjà réduite) ;
 *  2. à défaut l'API https://api.nasa.gov/planetary/apod avec DEMO_KEY (quota par adresse IP).
 * Si c'est une vidéo : miniature YouTube (ou celle de l'API), sinon la photo de la veille.
 * Mémoire : l'image est réduite au décodage (inSampleSize, RGB_565) à 1280 px au plus, puis
 * enregistrée en JPEG pour la journée ; l'affichage la décode encore à la taille du module.
 */
object PhotoReseau {

    private const val TAG = DonneesModules.TAG
    private const val PAGE = "https://apod.nasa.gov/apod/"
    private const val API = "https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY&thumbs=true"
    private const val COTE_MAX = 1280
    private const val TAILLE_MAX = 15L * 1024 * 1024

    /** Jour de la NASA (APOD change à minuit, heure de New York). */
    private val NEW_YORK: TimeZone = TimeZone.getTimeZone("America/New_York")

    fun jour(t: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = NEW_YORK }.format(t)

    fun charger(context: Context): PhotoDuJour {
        val dossier = File(context.cacheDir, "photo_jour").apply { mkdirs() }
        val aujourdhui = jour()
        lireCache(dossier, aujourdhui)?.let { return it }

        val essai = runCatching { telechargerJour(dossier, aujourdhui, veille = false) }
        val photo = essai.getOrNull() ?: runCatching {
            // Vidéo sans miniature, ou page du jour pas encore publiée : la veille
            val hier = jour(System.currentTimeMillis() - 24 * 3_600_000L)
            lireCache(dossier, hier) ?: telechargerJour(dossier, hier, veille = true)
        }.getOrNull()
        if (photo != null) {
            // Ménage : on ne garde que la photo affichée
            dossier.listFiles()?.forEach { if (!it.name.startsWith("apod_${photo.jour}")) it.delete() }
            return photo.copy(ancienne = photo.jour != aujourdhui)
        }
        // Rien de neuf : la dernière photo connue plutôt qu'une erreur
        val derniere = dossier.listFiles { f -> f.name.endsWith(".txt") }?.maxByOrNull { it.name }
            ?.let { lireCache(dossier, it.name.removePrefix("apod_").removeSuffix(".txt")) }
        return derniere?.copy(ancienne = true) ?: throw (essai.exceptionOrNull() ?: IllegalStateException("APOD indisponible"))
    }

    private fun telechargerJour(dossier: File, jour: String, veille: Boolean): PhotoDuJour {
        val annonces = listOf<() -> AnnoncePhoto>(
            { annoncePage(if (veille) "${PAGE}ap${jour.substring(2)}.html" else "${PAGE}astropix.html") },
            { annonceApi(if (veille) "$API&date=${jour.substring(0, 4)}-${jour.substring(4, 6)}-${jour.substring(6)}" else API) },
        )
        var derniere: Exception? = null
        for (lire in annonces) {
            try {
                val a = lire()
                val image = a.image ?: error("vidéo sans miniature")
                return enregistrer(dossier, jour, a.copy(image = image))
            } catch (e: Exception) {
                Log.w(TAG, "Photo du jour ($jour) : échec", e)
                derniere = e
            }
        }
        throw derniere ?: IllegalStateException("APOD indisponible")
    }

    /** Lit la page HTML d'APOD (taille plafonnée). */
    private fun annoncePage(url: String): AnnoncePhoto {
        val html = FluxReseau.ouvrir(url, 8000, "text/html").use { flux ->
            flux.reader(Charsets.ISO_8859_1).use { r ->
                val tampon = CharArray(300_000)
                var n = 0
                while (n < tampon.size) {
                    val lu = r.read(tampon, n, tampon.size - n)
                    if (lu < 0) break
                    n += lu
                }
                String(tampon, 0, n)
            }
        }
        return lirePage(html, url)
    }

    private val RE_TITRE = Regex("<title>\\s*APOD:[^<]*?\\s[-–]\\s*(.*?)\\s*</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val RE_IMG = Regex("<img[^>]*?\\ssrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val RE_GRAS = Regex("<b>\\s*(.*?)\\s*</b>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val RE_IFRAME = Regex("<iframe[^>]*?\\ssrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val RE_YOUTUBE = Regex("(?:youtube(?:-nocookie)?\\.com/(?:embed/|watch\\?v=)|youtu\\.be/)([A-Za-z0-9_-]{6,})")

    /** Titre et image d'une page APOD ([adresse] : pour résoudre les liens relatifs). */
    fun lirePage(html: String, adresse: String): AnnoncePhoto {
        val img = RE_IMG.find(html)
        val iframe = RE_IFRAME.find(html)
        val titre = RE_TITRE.find(html)?.groupValues?.get(1)?.let { TexteFlux.nettoyer(it) }?.takeIf { it.isNotEmpty() }
            ?: (img ?: iframe)?.let { m -> RE_GRAS.find(html, m.range.last)?.groupValues?.get(1) }?.let { TexteFlux.nettoyer(it) }
            ?: "Photo astronomique du jour"
        if (img != null) {
            return AnnoncePhoto(titre, java.net.URL(java.net.URL(adresse), TexteFlux.entites(img.groupValues[1])).toString(), video = false)
        }
        val miniature = iframe?.groupValues?.get(1)?.let { RE_YOUTUBE.find(it) }?.groupValues?.get(1)
            ?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
        return AnnoncePhoto(titre, miniature, video = true)
    }

    private fun annonceApi(url: String): AnnoncePhoto = lireApi(JSONObject(FluxReseau.ouvrir(url, 8000, "application/json").use {
        it.reader().use { r -> r.readText() }
    }))

    fun lireApi(j: JSONObject): AnnoncePhoto {
        val titre = TexteFlux.nettoyer(j.optString("title")).ifEmpty { "Photo astronomique du jour" }
        val video = j.optString("media_type") != "image"
        val image = if (video) j.optString("thumbnail_url").ifEmpty { null } else j.optString("url").ifEmpty { null }
        return AnnoncePhoto(titre, image?.replace("http://", "https://"), video)
    }

    /** Télécharge, réduit et enregistre l'image (fichier temporaire supprimé). */
    private fun enregistrer(dossier: File, jour: String, a: AnnoncePhoto): PhotoDuJour {
        val temp = File(dossier, "telechargement.tmp")
        val sortie = File(dossier, "apod_$jour.jpg")
        try {
            FluxReseau.telecharger(a.image!!, temp, TAILLE_MAX, delai = 10_000)
            val bitmap = decoder(temp, COTE_MAX, COTE_MAX, ajuster = false) ?: error("image illisible")
            try {
                sortie.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            } finally {
                bitmap.recycle()
            }
            File(dossier, "apod_$jour.txt").writeText(
                listOf(a.titre.replace('\n', ' '), if (a.video) "video" else "image").joinToString("\n"),
            )
            return PhotoDuJour(a.titre, jour, sortie.path, video = a.video)
        } catch (e: Exception) {
            sortie.delete()
            throw e
        } finally {
            temp.delete()
        }
    }

    private fun lireCache(dossier: File, jour: String): PhotoDuJour? {
        val image = File(dossier, "apod_$jour.jpg")
        val texte = File(dossier, "apod_$jour.txt")
        if (!image.isFile || !texte.isFile) return null
        val lignes = runCatching { texte.readLines() }.getOrNull() ?: return null
        return PhotoDuJour(lignes.firstOrNull().orEmpty().ifEmpty { "Photo astronomique du jour" }, jour, image.path, video = lignes.getOrNull(1) == "video")
    }

    /**
     * Décode [f] réduit pour couvrir [largeur] × [hauteur] px (inSampleSize, RGB_565 : 2 octets
     * par pixel). [ajuster] = false : le plus grand côté ne dépasse pas max(largeur, hauteur).
     */
    fun decoder(f: File, largeur: Int, hauteur: Int, ajuster: Boolean = true): Bitmap? {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bornes)
        val w = bornes.outWidth
        val h = bornes.outHeight
        if (w <= 0 || h <= 0) return null
        var echantillon = 1
        if (ajuster) {
            // Le plus petit décodage qui couvre encore la place (recadrage ensuite)
            while (w / (echantillon * 2) >= largeur && h / (echantillon * 2) >= hauteur) echantillon *= 2
        } else {
            while (maxOf(w, h) / echantillon > maxOf(largeur, hauteur)) echantillon *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = echantillon
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(f.path, options)
    }
}
