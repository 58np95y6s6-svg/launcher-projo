package fr.projo.launcher.ui

import android.app.AppOpsManager
import android.app.SearchManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Process
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import fr.projo.launcher.BuildConfig
import fr.projo.launcher.data.AppCategories
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppRepository
import fr.projo.launcher.data.DefaultFavorites
import fr.projo.launcher.data.Illustrations
import fr.projo.launcher.data.Rubrique
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/*
 * Suggestions du design « Studio » (rangée du haut de l'écran 1) : des films et séries
 * DISPONIBLES SUR LES APPS DE STREAMING INSTALLÉES, lus sur TMDB (api.themoviedb.org/3) :
 *  - les services installés sont reconnus par Illustrations (Netflix, Prime Video…) puis
 *    associés à leurs identifiants TMDB pour la France (connus, ou retrouvés par leur nom dans
 *    la liste TMDB des services en France : france.tv, Arte, TF1+, M6+…) ;
 *  - pour chaque service : discover/movie et discover/tv (watch_region=FR, abonnement, gratuit
 *    ou avec publicité), les titres tendance de la semaine (trending/all/week) en tête ;
 *  - réponses et images en cache disque 1 jour ; images w780 réduites à la taille d'affichage.
 * Clé TMDB : saisie dans Réglages, sinon celle de la compilation (secret GitHub TMDB_KEY),
 * jamais dans le dépôt. Sans clé ou sans réseau : repli sur les « apps à la une ».
 *
 * Pourquoi pas les chaînes / Watch Next d'Android TV (TvContract) ? Le fournisseur TV ne rend
 * à une app que ses propres lignes, sauf avec ACCESS_ALL_EPG_DATA (signature|privileged) :
 * un launcher installé normalement n'y voit rien, et le Fire TV n'a pas ce fournisseur.
 */

/** Film ou série. */
enum class StudioGenre { FILM, SERIE }

/** Un titre proposé : vu sur TMDB, disponible sur un service installé. */
data class StudioProgramme(
    /** « movie:603 », « tv:1399 ». */
    val id: String,
    val titre: String,
    val genre: StudioGenre,
    /** Genres en français (« Science-fiction »…). */
    val genres: List<String>,
    /** App installée du service qui le propose, et son nom. */
    val paquet: String,
    val service: String,
    /** Image 16:9 (backdrop TMDB), ou null. */
    val image: String?,
)

/** État des suggestions TMDB. */
enum class StudioEtat { SANS_CLE, ERREUR, OK }

class StudioResultat(val programmes: List<StudioProgramme>, val etat: StudioEtat)

/** Où les suggestions sont lues : TMDB ; remplaçable dans les tests. */
interface StudioSource {
    /** true : lecture immédiate, sans passer par un autre fil (sources des tests). */
    val synchrone: Boolean get() = false

    /** Suggestions pour les apps installées [apps] (hors du thread principal). */
    fun suggestions(apps: List<AppInfo>): StudioResultat

    /** App la plus récemment utilisée parmi [paquets] (hors du thread principal), ou null. */
    fun plusRecente(paquets: Collection<String>): String?

    /** Image réduite à [largeur] px au plus (hors du thread principal), ou null. */
    fun image(adresse: String, largeur: Int): ImageBitmap?
}

/** Source des suggestions ; null : TMDB avec la clé de l'utilisateur (fournie par HomeStudio). */
val LocalStudioSource = staticCompositionLocalOf<StudioSource?> { null }

// ------------------------------------------------------------------ clé TMDB

/** Clé TMDB : saisie dans Réglages (préférences de l'appareil), sinon celle de la compilation. */
object StudioCle {
    private const val FICHIER = "studio"
    private const val CLE = "tmdb_cle"

    /** Incrémentée à chaque changement de clé : les suggestions sont relues. */
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
}

// ------------------------------------------------------------------ services

/** Un service de streaming installé : son app et ses identifiants TMDB (France). */
class StudioService(val idIllustration: String, val app: AppInfo, val fournisseurs: List<Int>)

object StudioServices {
    /** Identifiants TMDB des services en France (watch providers, région FR). */
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
     * Services installés, chacun avec ses identifiants TMDB : connus, ou trouvés par nom dans
     * [listeTmdb] (identifiant → nom TMDB, par ordre de priorité d'affichage en France).
     */
    fun installes(apps: List<AppInfo>, listeTmdb: List<Pair<Int, String>>): List<StudioService> {
        val res = LinkedHashMap<String, StudioService>()
        for (app in apps) {
            if (app.isSpecial) continue
            val id = idDe(app) ?: continue
            if (id in res) continue
            val ids = connus[id] ?: if (id in parNom) {
                listeTmdb.filter { (_, nom) -> Illustrations.parLibelle(nom)?.id == id }.map { it.first }.take(2)
            } else emptyList()
            if (ids.isNotEmpty()) res[id] = StudioService(id, app, ids)
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

// ------------------------------------------------------------------ client TMDB

/**
 * Client TMDB. [lire] : texte d'une adresse (avec l'en-tête Authorization si [bearer] n'est
 * pas null), ou null en cas d'échec. Clé v3 (32 caractères) en paramètre api_key, jeton v4
 * (« eyJ… ») en en-tête.
 */
class ClientTmdb(private val cle: String, private val lire: (url: String, bearer: String?) -> String?) {
    private val jeton = cle.startsWith("eyJ")

    private fun json(chemin: String, params: Map<String, String>): JSONObject? {
        val tous = params + ("language" to "fr-FR") + (if (jeton) emptyMap() else mapOf("api_key" to cle))
        val url = BASE + chemin + "?" + tous.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        val texte = lire(url, if (jeton) cle else null) ?: return null
        return runCatching { JSONObject(texte) }.getOrNull()
    }

    /** Services de streaming en France (films et séries), par priorité d'affichage. */
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

    private class Brut(val cle: String, val titre: String, val genre: StudioGenre, val genres: List<String>, val image: String?)

    private fun bruts(o: JSONObject?, typeImpose: String?): List<Brut> {
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
            Brut("$type:${e.optInt("id")}", titre, if (type == "movie") StudioGenre.FILM else StudioGenre.SERIE, genres, IMAGES + image)
        }
    }

    /**
     * Suggestions : par service, films et séries populaires disponibles en France ; les
     * tendances de la semaine d'abord, puis les services en alternance. null : TMDB injoignable.
     */
    fun suggestions(services: List<StudioService>, max: Int = 20): List<StudioProgramme>? {
        if (services.isEmpty()) return emptyList()
        val tendance = bruts(json("trending/all/week", emptyMap()), null).map { it.cle }
        val parService = services.take(MAX_SERVICES).map { s ->
            val filtre = mapOf(
                "with_watch_providers" to s.fournisseurs.joinToString("|"),
                "watch_region" to "FR",
                "with_watch_monetization_types" to "flatrate|free|ads",
                "sort_by" to "popularity.desc",
                "include_adult" to "false",
            )
            val films = bruts(json("discover/movie", filtre), "movie")
            val series = bruts(json("discover/tv", filtre), "tv")
            // Films et séries en alternance
            s to (0 until maxOf(films.size, series.size)).flatMap { listOfNotNull(series.getOrNull(it), films.getOrNull(it)) }
        }
        if (parService.all { it.second.isEmpty() } && tendance.isEmpty()) return null
        val vus = HashSet<String>()
        val res = mutableListOf<StudioProgramme>()
        fun ajouter(s: StudioService, b: Brut) {
            if (vus.add(b.cle)) res += StudioProgramme(b.cle, b.titre, b.genre, b.genres, s.app.packageName, s.app.label, b.image)
        }
        // 1. Les tendances de la semaine disponibles sur un service installé
        for (c in tendance) {
            for ((s, liste) in parService) liste.firstOrNull { it.cle == c }?.let { ajouter(s, it) }
        }
        // 2. Les services en alternance
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

// ------------------------------------------------------------------ source réelle

/**
 * Suggestions TMDB avec la clé de l'utilisateur ([cle] : lue à chaque appel), réseau et cache
 * disque 1 jour ([lireTexte] / [lireOctets] remplaçables dans les tests).
 */
open class StudioSourceTmdb(
    private val cle: () -> String?,
    private val lireTexte: (url: String, bearer: String?) -> String?,
    private val lireOctets: (url: String) -> ByteArray?,
) : StudioSource {

    override fun suggestions(apps: List<AppInfo>): StudioResultat {
        val k = cle() ?: return StudioResultat(emptyList(), StudioEtat.SANS_CLE)
        return try {
            val client = ClientTmdb(k, lireTexte)
            val liste = client.listeServices() ?: return StudioResultat(emptyList(), StudioEtat.ERREUR)
            val services = StudioServices.installes(apps, liste)
            val prog = client.suggestions(services) ?: return StudioResultat(emptyList(), StudioEtat.ERREUR)
            Log.i(AppRepository.TAG, "Studio : ${prog.size} suggestions TMDB (${services.joinToString { it.idIllustration }})")
            StudioResultat(prog, StudioEtat.OK)
        } catch (e: Exception) {
            StudioResultat(emptyList(), StudioEtat.ERREUR)
        }
    }

    override fun plusRecente(paquets: Collection<String>): String? = null

    override fun image(adresse: String, largeur: Int): ImageBitmap? {
        val cleCache = "$adresse@$largeur"
        images.get(cleCache)?.let { return it }
        return try {
            val octets = lireOctets(adresse) ?: return null
            decoder(octets, largeur)?.also { images.put(cleCache, it) }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    companion object {
        /** Images décodées (~8 Mo : le projecteur n'a qu'1 Go de RAM). */
        private val images = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 2
        }

        /** Décode à [largeur] px au plus, en 16 bits par pixel. */
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
}

/** Source réelle : clé de l'utilisateur, réseau, cache disque et données d'utilisation. */
class StudioSourceAndroid(private val context: Context) : StudioSource {
    private val cache = CacheTmdb(File(context.cacheDir, "studio-tmdb"))
    private val tmdb = StudioSourceTmdb({ StudioCle.lire(context) }, cache::texte, cache::octets)

    override fun suggestions(apps: List<AppInfo>) = tmdb.suggestions(apps).also { cache.nettoyer() }

    override fun image(adresse: String, largeur: Int) = tmdb.image(adresse, largeur)

    override fun plusRecente(paquets: Collection<String>): String? {
        if (paquets.isEmpty()) return null
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return null
            @Suppress("DEPRECATION")
            val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            if (mode != AppOpsManager.MODE_ALLOWED) return null
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val fin = System.currentTimeMillis()
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, fin - 14 * 24 * 3_600_000L, fin)
                .orEmpty()
                .filter { it.packageName in paquets && it.lastTimeUsed > 0 && it.totalTimeInForeground > 0 }
                .maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            null
        }
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
        if (lus == null) return if (f.exists()) runCatching { f.readBytes() }.getOrNull() else null
        runCatching {
            dossier.mkdirs()
            f.writeBytes(lus)
        }
        return lus
    }

    private fun telecharger(url: String, bearer: String?, max: Int): ByteArray? {
        val cnx = URL(url).openConnection() as HttpURLConnection
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

// ------------------------------------------------------------------ cartes affichées

/** Une carte de la rangée des suggestions. */
sealed class StudioSuggestion {
    abstract val cle: String

    /** Titre TMDB disponible sur [app] (le service installé). */
    class Programme(val programme: StudioProgramme, val app: AppInfo?) : StudioSuggestion() {
        override val cle get() = "prog:" + programme.id
    }

    /** Repli : une app à la une, avec une phrase (« Reprendre sur Netflix », « Films et séries »…). */
    class AppUne(val app: AppInfo, val phrase: String) : StudioSuggestion() {
        override val cle get() = "une:" + app.packageName
    }
}

/** Suggestions prêtes à afficher. */
class StudioSuggestionsEtat(val resultat: StudioResultat, val recente: String?)

/** Phrase d'une app à la une selon sa rubrique. */
fun phraseRubrique(app: AppInfo): String = when (AppCategories.rubrique(app)) {
    Rubrique.STREAMING -> "Films et séries"
    Rubrique.TV -> "TV en direct"
    Rubrique.MUSIQUE -> "Musique et audio"
    Rubrique.JEUX -> "Jeux"
    Rubrique.STORE -> "Applications"
    Rubrique.OUTILS -> "Internet et outils"
    Rubrique.SYSTEME -> "Système"
    Rubrique.AUTRES -> "Application"
}

/** Sous-titre d'un titre : « Série · Science-fiction · Netflix ». */
fun sousTitreProgramme(p: StudioProgramme): String =
    listOfNotNull(if (p.genre == StudioGenre.FILM) "Film" else "Série", p.genres.firstOrNull(), p.service).joinToString(" · ")

/**
 * Apps à la une (repli) : favoris puis « Mes apps » (sans les entrées HDMI), au plus 6 ;
 * l'app la plus récemment utilisée en tête (grande carte), sinon le 1er favori.
 */
fun appsALaUne(favoris: List<AppInfo>, mesApps: List<AppInfo>, recente: String?): List<StudioSuggestion.AppUne> {
    val apps = (favoris + mesApps).filter { !it.isSpecial }.distinctBy { it.packageName }
    val tete = apps.firstOrNull { it.packageName == recente }
    val ordre = if (tete != null) listOf(tete) + (apps - tete) else apps
    return ordre.take(6).map { app ->
        StudioSuggestion.AppUne(app, if (app === tete) "Reprendre sur ${app.label}" else phraseRubrique(app))
    }
}

@Composable
private fun rememberSource(): StudioSource {
    val context = LocalContext.current
    val fournie = LocalStudioSource.current
    return remember(fournie) { fournie ?: StudioSourceAndroid(context.applicationContext) }
}

/** Lit les suggestions hors du thread principal : à l'affichage, au retour sur l'accueil, au changement de clé. */
@Composable
fun rememberStudioSuggestions(apps: List<AppInfo>, paquets: List<String>): StudioSuggestionsEtat {
    val source = rememberSource()
    val cycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var reprises by remember { mutableIntStateOf(0) }
    LaunchedEffect(cycle) { if (cycle == Lifecycle.State.RESUMED) reprises++ }
    val version = StudioCle.version
    val etat by produceState(
        StudioSuggestionsEtat(StudioResultat(emptyList(), StudioEtat.OK), null), source, apps, paquets, reprises, version,
    ) {
        val lire = { StudioSuggestionsEtat(source.suggestions(apps), source.plusRecente(paquets)) }
        value = if (source.synchrone) lire() else withContext(Dispatchers.IO) { lire() }
    }
    return etat
}

/** Image d'une suggestion (null tant qu'elle n'est pas prête). [largeur] : largeur affichée, en px. */
@Composable
fun rememberImageProgramme(adresse: String?, largeur: Int): ImageBitmap? {
    val source = rememberSource()
    val image by produceState<ImageBitmap?>(null, adresse, largeur) {
        if (adresse != null) {
            value = if (source.synchrone) source.image(adresse, largeur) else withContext(Dispatchers.IO) { source.image(adresse, largeur) }
        }
    }
    return image
}

/**
 * Ouvre le service d'un titre : sa recherche sur le titre (ACTION_SEARCH, seulement si l'app
 * la déclare), sinon l'app elle-même ([repli]).
 */
fun ouvrirProgramme(context: Context, p: StudioProgramme, repli: () -> Unit) {
    val recherche = Intent(Intent.ACTION_SEARCH)
        .setPackage(p.paquet)
        .putExtra(SearchManager.QUERY, p.titre)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = try {
        recherche.resolveActivity(context.packageManager) != null && run { context.startActivity(recherche); true }
    } catch (e: Exception) {
        false
    }
    if (!ok) repli()
}

/** « TF1+ » → « tf1plus » (comme les règles d'Illustrations). */
internal fun normaliserNom(s: String) = DefaultFavorites.normaliser(s)
