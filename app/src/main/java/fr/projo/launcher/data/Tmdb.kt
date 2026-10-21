package fr.projo.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fr.projo.launcher.BuildConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * TMDB (api.themoviedb.org/3), partagé par les designs : films et séries disponibles en
 * France sur les services de streaming installés.
 *
 * Point d'entrée : [Tmdb] (clé de l'utilisateur, cache disque d'un jour, images décodées à la
 * taille demandée). Toutes ses fonctions sont bloquantes : à appeler hors du thread principal.
 *  - Tmdb.client() : ClientTmdb prêt (null sans clé) ;
 *  - Tmdb.dernieresSorties(app, max) / Tmdb.dernieresSortiesFournisseurs(ids, max) : derniers
 *    titres sortis disponibles sur un service ;
 *  - Tmdb.image(url, largeurPx) : image décodée (16 bits) à largeurPx au plus.
 * Clé : saisie dans Réglages ([CleTmdb]), sinon celle de la compilation (secret TMDB_KEY).
 * Attribution obligatoire à l'écran : « Données : TMDB ».
 */

/** Film ou série. */
enum class GenreTmdb { FILM, SERIE }

/** Un titre TMDB. */
data class TitreTmdb(
    /** « movie:603 », « tv:1399 ». */
    val id: String,
    val titre: String,
    val genre: GenreTmdb,
    /** Genres en français (« Science-fiction »…). */
    val genres: List<String>,
    /** Image 16:9 (backdrop, w780), adresse complète. */
    val image: String,
    /** Date de sortie (première diffusion pour une série), « 2026-10-03 », ou null. */
    val sortie: String?,
)

/** Un service de streaming installé : son app et ses identifiants TMDB (France). */
class ServiceTmdb(val idIllustration: String, val app: AppInfo, val fournisseurs: List<Int>)

/** Clé TMDB : saisie dans Réglages (préférences de l'appareil), sinon celle de la compilation. */
object CleTmdb {
    private const val FICHIER = "studio"
    private const val CLE = "tmdb_cle"

    /** Incrémentée à chaque changement de clé : les écrans relisent leurs suggestions. */
    var version by mutableIntStateOf(0)
        private set

    fun saisie(context: Context): String? =
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getString(CLE, null)?.trim()?.ifEmpty { null }

    fun lire(context: Context): String? = saisie(context) ?: BuildConfig.TMDB_KEY.trim().ifEmpty { null }

    fun ecrire(context: Context, cle: String?) {
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit()
            .apply { if (cle.isNullOrBlank()) remove(CLE) else putString(CLE, cle.trim()) }.apply()
        version++
    }

    /** Clé v3 (32 caractères hexadécimaux) ou jeton v4 (« eyJ… »). */
    fun valide(cle: String): Boolean {
        val c = cle.trim()
        return Regex("[0-9a-fA-F]{32}").matches(c) || (c.startsWith("eyJ") && c.length > 100 && Regex("[A-Za-z0-9._-]+").matches(c))
    }
}

/** Services de streaming reconnus et leurs identifiants TMDB pour la France. */
object ServicesTmdb {
    /** Identifiants TMDB connus (watch providers, région FR), par identifiant d'Illustrations. */
    val connus: Map<String, List<Int>> = mapOf(
        "netflix" to listOf(8),
        "primevideo" to listOf(119),
        "disneyplus" to listOf(337),
        "canalplus" to listOf(381),
        "appletv" to listOf(350),
        "max" to listOf(1899),
        "paramountplus" to listOf(531),
        "crunchyroll" to listOf(283),
        "ocs" to listOf(56),
        "rakutentv" to listOf(35),
        "mubi" to listOf(11),
        "plutotv" to listOf(300),
    )

    /** Services cherchés par leur nom dans la liste TMDB (identifiants non garantis). */
    val parNom: Set<String> = setOf("francetv", "arte", "tf1plus", "m6plus", "molotov")

    /** Identifiant d'illustration d'une app (Netflix → « netflix »), ou null. */
    fun idDe(app: AppInfo): String? =
        (Illustrations.parPaquet(app.packageName) ?: Illustrations.parLibelle(app.label))?.id

    /**
     * Identifiants TMDB du service d'une app : connus, ou trouvés par nom dans [listeTmdb]
     * (identifiant → nom TMDB, par priorité d'affichage en France). Vide : pas un service connu.
     */
    fun fournisseursDe(app: AppInfo, listeTmdb: List<Pair<Int, String>>?): List<Int> {
        if (app.isSpecial) return emptyList()
        val id = idDe(app) ?: return emptyList()
        return connus[id] ?: if (id in parNom && listeTmdb != null) {
            listeTmdb.filter { (_, nom) -> Illustrations.parLibelle(nom)?.id == id }.map { it.first }.take(2)
        } else emptyList()
    }

    /** Services installés parmi [apps] (un par service, dans l'ordre de [apps]). */
    fun installes(apps: List<AppInfo>, listeTmdb: List<Pair<Int, String>>?): List<ServiceTmdb> {
        val res = LinkedHashMap<String, ServiceTmdb>()
        for (app in apps) {
            val id = idDe(app) ?: continue
            if (id in res) continue
            val ids = fournisseursDe(app, listeTmdb)
            if (ids.isNotEmpty()) res[id] = ServiceTmdb(id, app, ids)
        }
        return res.values.toList()
    }
}

/** Noms français des genres TMDB (films et séries). */
private val GENRES = mapOf(
    28 to "Action", 12 to "Aventure", 16 to "Animation", 35 to "Comédie", 80 to "Policier",
    99 to "Documentaire", 18 to "Drame", 10751 to "Familial", 14 to "Fantastique", 36 to "Histoire",
    27 to "Horreur", 10402 to "Musique", 9648 to "Mystère", 10749 to "Romance", 878 to "Science-fiction",
    10770 to "Téléfilm", 53 to "Thriller", 10752 to "Guerre", 37 to "Western",
    10759 to "Action et aventure", 10762 to "Enfants", 10763 to "Actualités", 10764 to "Téléréalité",
    10765 to "Science-fiction", 10766 to "Feuilleton", 10767 to "Talk-show", 10768 to "Guerre et politique",
)

/**
 * Client TMDB. [lire] : texte d'une adresse (avec « Authorization: Bearer » si [bearer] n'est
 * pas null), ou null en cas d'échec. Clé v3 en paramètre api_key, jeton v4 (« eyJ… ») en
 * en-tête. Fonctions bloquantes.
 */
class ClientTmdb(private val cle: String, private val lire: (url: String, bearer: String?) -> String?) {
    private val jeton = cle.startsWith("eyJ")

    private fun json(chemin: String, params: Map<String, String>): JSONObject? {
        val tous = params + ("language" to "fr-FR") + (if (jeton) emptyMap() else mapOf("api_key" to cle))
        val url = BASE + chemin + "?" + tous.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        val texte = lire(url, if (jeton) cle else null) ?: return null
        return runCatching { JSONObject(texte) }.getOrNull()
    }

    /** Services de streaming en France (films et séries), par priorité d'affichage ; null : injoignable. */
    fun listeServices(): List<Pair<Int, String>>? {
        val res = LinkedHashMap<Int, Pair<String, Int>>()
        var ok = false
        for (type in listOf("movie", "tv")) {
            val r = json("watch/providers/$type", mapOf("watch_region" to "FR"))?.optJSONArray("results") ?: continue
            ok = true
            for (i in 0 until r.length()) {
                val p = r.optJSONObject(i) ?: continue
                val prio = p.optJSONObject("display_priorities")?.optInt("FR", 999) ?: p.optInt("display_priority", 999)
                res.putIfAbsent(p.optInt("provider_id"), p.optString("provider_name") to prio)
            }
        }
        if (!ok) return null
        return res.entries.sortedBy { it.value.second }.map { it.key to it.value.first }
    }

    private fun titres(o: JSONObject?, typeImpose: String?): List<TitreTmdb> {
        val r = o?.optJSONArray("results") ?: return emptyList()
        return (0 until r.length()).mapNotNull { i ->
            val e = r.optJSONObject(i) ?: return@mapNotNull null
            val type = typeImpose ?: e.optString("media_type")
            if (type != "movie" && type != "tv") return@mapNotNull null
            val titre = e.optString(if (type == "movie") "title" else "name").trim()
            val image = e.optString("backdrop_path").takeIf { it.startsWith("/") }
            if (titre.isEmpty() || image == null) return@mapNotNull null
            val g = e.optJSONArray("genre_ids")
            val genres = (0 until (g?.length() ?: 0)).mapNotNull { GENRES[g!!.optInt(it)] }.distinct()
            val sortie = e.optString(if (type == "movie") "release_date" else "first_air_date").takeIf { it.length >= 4 }
            TitreTmdb("$type:${e.optInt("id")}", titre, if (type == "movie") GenreTmdb.FILM else GenreTmdb.SERIE, genres, IMAGES + image, sortie)
        }
    }

    /** Tendances de la semaine (films et séries, tous services). */
    fun tendances(): List<TitreTmdb>? = json("trending/all/week", emptyMap())?.let { titres(it, null) }

    /**
     * Titres disponibles en France sur les services [fournisseurs] (abonnement, gratuit ou
     * avec publicité), [type] « movie » ou « tv », triés par [tri] ; null : injoignable.
     */
    fun decouvrir(
        fournisseurs: List<Int>,
        type: String,
        tri: String = "popularity.desc",
        autres: Map<String, String> = emptyMap(),
    ): List<TitreTmdb>? {
        val params = mapOf(
            "with_watch_providers" to fournisseurs.joinToString("|"),
            "watch_region" to "FR",
            "with_watch_monetization_types" to "flatrate|free|ads",
            "sort_by" to tri,
            "include_adult" to "false",
        ) + autres
        return json("discover/$type", params)?.let { titres(it, type) }
    }

    /**
     * Dernières sorties disponibles sur un service ([fournisseurs] : ses identifiants TMDB) :
     * films et séries sortis jusqu'à aujourd'hui, du plus récent au plus ancien (avec un
     * minimum de votes pour écarter les inconnus), en alternance. null : injoignable.
     */
    fun dernieresSorties(fournisseurs: List<Int>, max: Int = 10): List<TitreTmdb>? {
        if (fournisseurs.isEmpty()) return emptyList()
        val jour = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val films = decouvrir(
            fournisseurs, "movie", "primary_release_date.desc",
            mapOf("primary_release_date.lte" to jour, "vote_count.gte" to "20"),
        )
        val series = decouvrir(
            fournisseurs, "tv", "first_air_date.desc",
            mapOf("first_air_date.lte" to jour, "vote_count.gte" to "10"),
        )
        if (films == null && series == null) return null
        val f = films.orEmpty()
        val s = series.orEmpty()
        return (0 until maxOf(f.size, s.size)).flatMap { listOfNotNull(f.getOrNull(it), s.getOrNull(it)) }
            .sortedByDescending { it.sortie.orEmpty() }
            .take(max)
    }

    /**
     * Sélection « à la une » : par service, films et séries populaires ; les tendances de la
     * semaine disponibles sur un service en tête, puis les services en alternance.
     * null : TMDB injoignable.
     */
    fun alaUne(services: List<ServiceTmdb>, max: Int = 20): List<Pair<ServiceTmdb, TitreTmdb>>? {
        if (services.isEmpty()) return emptyList()
        val tendance = tendances().orEmpty().map { it.id }
        val parService = services.take(MAX_SERVICES).map { s ->
            val films = decouvrir(s.fournisseurs, "movie").orEmpty()
            val series = decouvrir(s.fournisseurs, "tv").orEmpty()
            s to (0 until maxOf(films.size, series.size)).flatMap { listOfNotNull(series.getOrNull(it), films.getOrNull(it)) }
        }
        if (parService.all { it.second.isEmpty() } && tendance.isEmpty()) return null
        val vus = HashSet<String>()
        val res = mutableListOf<Pair<ServiceTmdb, TitreTmdb>>()
        fun ajouter(s: ServiceTmdb, t: TitreTmdb) {
            if (vus.add(t.id)) res += s to t
        }
        for (c in tendance) {
            for ((s, liste) in parService) liste.firstOrNull { it.id == c }?.let { ajouter(s, it) }
        }
        val iterateurs = parService.map { it.first to it.second.iterator() }
        while (res.size < max && iterateurs.any { it.second.hasNext() }) {
            for ((s, it) in iterateurs) if (it.hasNext()) ajouter(s, it.next())
        }
        return res.take(max)
    }

    companion object {
        const val BASE = "https://api.themoviedb.org/3/"
        const val IMAGES = "https://image.tmdb.org/t/p/w780"
        private const val MAX_SERVICES = 8
    }
}

/** Réseau avec cache disque d'un jour (réponses TMDB et images), délais courts. */
class CacheTmdb(private val dossier: File) {
    private fun fichier(url: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dossier, h)
    }

    private fun frais(f: File) = f.exists() && System.currentTimeMillis() - f.lastModified() < UN_JOUR

    fun texte(url: String, bearer: String?): String? = octetsCache(url, bearer, max = 2 * 1024 * 1024)?.toString(Charsets.UTF_8)

    fun octets(url: String): ByteArray? = octetsCache(url, null, max = 3 * 1024 * 1024)

    private fun octetsCache(url: String, bearer: String?, max: Int): ByteArray? {
        val f = fichier(url)
        if (frais(f)) runCatching { return f.readBytes() }
        val lus = telecharger(url, bearer, max)
        // Réseau en panne : la copie périmée vaut mieux que rien
        if (lus == null) return if (f.exists()) runCatching { f.readBytes() }.getOrNull() else null
        runCatching {
            dossier.mkdirs()
            f.writeBytes(lus)
        }
        return lus
    }

    private fun telecharger(url: String, bearer: String?, max: Int): ByteArray? {
        val cnx = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return null
        }
        return try {
            cnx.connectTimeout = 5000
            cnx.readTimeout = 8000
            cnx.setRequestProperty("Accept", "application/json")
            if (bearer != null) cnx.setRequestProperty("Authorization", "Bearer $bearer")
            if (cnx.responseCode != 200) return null
            cnx.inputStream.use { flux ->
                val sortie = ByteArrayOutputStream()
                val tampon = ByteArray(16 * 1024)
                while (true) {
                    val n = flux.read(tampon)
                    if (n < 0) break
                    sortie.write(tampon, 0, n)
                    if (sortie.size() > max) return null
                }
                sortie.toByteArray()
            }
        } catch (e: Exception) {
            null
        } finally {
            cnx.disconnect()
        }
    }

    /** Supprime les fichiers de plus de 3 jours. */
    fun nettoyer() {
        runCatching {
            dossier.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3 * UN_JOUR) it.delete() }
        }
    }

    companion object {
        private const val UN_JOUR = 24 * 3_600_000L
    }
}

/** Images TMDB décodées à la taille d'affichage, en 16 bits par pixel, avec un petit cache mémoire. */
object ImagesTmdb {
    /** ~8 Mo : le projecteur n'a qu'1 Go de RAM. */
    private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 2
    }

    /** Image de [adresse] à [largeur] px au plus ([octets] : lecture réseau / cache disque). */
    fun image(adresse: String, largeur: Int, octets: (String) -> ByteArray?): ImageBitmap? {
        val cle = "$adresse@$largeur"
        cache.get(cle)?.let { return it }
        return try {
            decoder(octets(adresse) ?: return null, largeur)?.also { cache.put(cle, it) }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Décode à [largeur] px au plus (réduction par puissances de 2 puis mise à l'échelle). */
    fun decoder(octets: ByteArray, largeur: Int): ImageBitmap? {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
        if (bornes.outWidth <= 0) return null
        var reduction = 1
        while (bornes.outWidth / (reduction * 2) >= largeur) reduction *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = reduction
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        var b = BitmapFactory.decodeByteArray(octets, 0, octets.size, options) ?: return null
        if (b.width > largeur * 1.15f) {
            val h = (b.height * largeur / b.width.toFloat()).toInt().coerceAtLeast(1)
            val r = Bitmap.createScaledBitmap(b, largeur, h, true)
            if (r !== b) b.recycle()
            b = r
        }
        return b.asImageBitmap()
    }
}

/**
 * TMDB prêt à l'emploi (clé de l'utilisateur, cache disque d'un jour). Fonctions bloquantes :
 * hors du thread principal.
 */
class Tmdb(context: Context) {
    private val appli = context.applicationContext
    private val cache = CacheTmdb(File(appli.cacheDir, "tmdb"))

    /** Client prêt, ou null sans clé. */
    fun client(): ClientTmdb? = CleTmdb.lire(appli)?.let { ClientTmdb(it, cache::texte) }

    /** Liste des services en France (cache d'un jour), pour reconnaître france.tv, Arte… */
    fun listeServices(): List<Pair<Int, String>>? = client()?.listeServices()

    /**
     * Dernières sorties disponibles en France sur le service de [app] (Netflix, Prime Video…),
     * au plus [max]. Vide : pas de clé, ou l'app n'est pas un service connu ; null : injoignable.
     */
    fun dernieresSorties(app: AppInfo, max: Int = 10): List<TitreTmdb>? {
        val c = client() ?: return emptyList()
        val ids = ServicesTmdb.fournisseursDe(app, if (ServicesTmdb.idDe(app) in ServicesTmdb.parNom) c.listeServices() else null)
        return c.dernieresSorties(ids, max).also { cache.nettoyer() }
    }

    /** Dernières sorties pour des identifiants de service TMDB (ex. listOf(8) : Netflix). */
    fun dernieresSortiesFournisseurs(fournisseurs: List<Int>, max: Int = 10): List<TitreTmdb>? {
        val c = client() ?: return emptyList()
        return c.dernieresSorties(fournisseurs, max)
    }

    /** Image TMDB décodée à [largeur] px au plus (16 bits par pixel), ou null. */
    fun image(adresse: String, largeur: Int): ImageBitmap? = ImagesTmdb.image(adresse, largeur, cache::octets)

    /** Octets d'une adresse via le cache disque (pour d'autres usages). */
    internal fun octets(adresse: String): ByteArray? = cache.octets(adresse)

    /** Texte d'une adresse via le cache disque. */
    internal fun texte(adresse: String, bearer: String?): String? = cache.texte(adresse, bearer)

    fun nettoyer() = cache.nettoyer()
}
