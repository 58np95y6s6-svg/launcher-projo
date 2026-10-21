package fr.projo.launcher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/*
 * Nouveautés d'une app de streaming : les derniers films et séries sortis sur ce service en
 * France (TMDB, client partagé data/Tmdb.kt) et leurs photos, affichées en fond par
 * « À la une » (ui/UnePhotos.kt).
 *
 *  - Nouveautes.aUnCatalogue(app) : l'app est un service connu (sans réseau) ;
 *  - Nouveautes.titresRecents(context, app) : titres récents avec photo, ou null (pas de clé,
 *    pas de réseau) ; cache de 6 h (15 min après un échec) ;
 *  - Nouveautes.photo(context, adresse, largeurPx) : photo décodée, sans cache mémoire ;
 *  - ReglageNouveautes : « Affiches des nouveautés » activé / désactivé (activé par défaut).
 */

/** Réglage global « Affiches des nouveautés » : enregistré sur l'appareil, activé par défaut. */
object ReglageNouveautes {
    private const val FICHIER = "nouveautes"
    private const val CLE = "actif"
    private var lu = false

    /** Valeur courante (lue au premier appel de [charger]). */
    var actif by mutableStateOf(true)
        private set

    fun charger(context: Context) {
        if (lu) return
        lu = true
        actif = context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getBoolean(CLE, true)
    }

    fun ecrire(context: Context, valeur: Boolean) {
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putBoolean(CLE, valeur).apply()
        lu = true
        actif = valeur
    }
}

object Nouveautes {
    private const val DUREE = 6 * 3_600_000L
    private const val DUREE_ECHEC = 15 * 60_000L

    /** Service de streaming reconnu (catalogue TMDB possible), sans accès réseau. */
    fun aUnCatalogue(app: AppInfo): Boolean {
        if (app.isSpecial) return false
        val id = ServicesTmdb.idDe(app) ?: return false
        return id in ServicesTmdb.connus || id in ServicesTmdb.parNom
    }

    private class EntreeTitres(val titres: List<TitreTmdb>?, val date: Long, val version: Int)

    private val titres = ConcurrentHashMap<String, EntreeTitres>()

    /**
     * Jusqu'à [max] nouveautés récentes du service de [app] (titre + adresse de la photo), sans
     * décoder d'image ; null : pas un service, pas de clé, réseau injoignable. Cache de 6 h.
     */
    suspend fun titresRecents(context: Context, app: AppInfo, max: Int = 3): List<TitreTmdb>? {
        if (!aUnCatalogue(app)) return null
        val e = titres[app.packageName]
        val duree = if (e?.titres.isNullOrEmpty()) DUREE_ECHEC else DUREE
        if (e != null && e.version == CleTmdb.version && System.currentTimeMillis() - e.date < duree) return e.titres?.take(max)
        val appli = context.applicationContext
        val version = CleTmdb.version
        val lus = withContext(Dispatchers.IO) {
            runCatching { Tmdb(appli).dernieresSorties(app, 8) }.getOrNull()
        }?.filter { it.image.isNotEmpty() }
        titres[app.packageName] = EntreeTitres(lus, System.currentTimeMillis(), version)
        return lus?.take(max)
    }

    /**
     * Photo [adresse] (backdrop TMDB) décodée à [largeur] px au plus, via le cache disque de
     * Tmdb.kt mais SANS cache mémoire : l'appelant garde au plus les images qu'il affiche.
     * Au-delà de 800 px, la version w1280 est demandée. Bloquant : hors du fil principal.
     */
    fun photo(context: Context, adresse: String, largeur: Int): ImageBitmap? {
        val url = if (largeur > 800) adresse.replace("/w780/", "/w1280/") else adresse
        val tmdb = Tmdb(context.applicationContext)
        val octets = tmdb.octets(url) ?: (if (url != adresse) tmdb.octets(adresse) else null) ?: return null
        return try {
            ImagesTmdb.decoder(octets, largeur.coerceAtMost(1280))
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Oublie tout (changement de clé, tests). */
    fun vider() {
        titres.clear()
    }
}
