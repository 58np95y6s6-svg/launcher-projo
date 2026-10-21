package fr.projo.launcher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/*
 * « Visuel nouveauté » d'une app de streaming : le dernier film ou la dernière série sortis
 * sur ce service en France (TMDB, client partagé data/Tmdb.kt) et son image, pour illustrer
 * la carte de l'app (rendu : ui/VisuelNouveaute.kt). Partagé par tous les designs.
 *
 *  - Nouveautes.aUnCatalogue(app) : l'app est un service connu (sans réseau) ;
 *  - Nouveautes.charger(context, app, largeurPx) : nouveauté décodée, ou null (pas de clé, pas
 *    de réseau, pas de titre) ; cache mémoire de 6 h (15 min après un échec) ;
 *  - ReglageNouveautes : « Affiches des nouveautés » activé / désactivé (activé par défaut).
 */

/** Dernier titre sorti sur un service et son image 16:9 décodée. */
class Nouveaute(
    val titre: String,
    val genre: GenreTmdb,
    /** Backdrop 16:9, décodé (16 bits par pixel) à la largeur demandée. */
    val image: ImageBitmap,
    /** Adresse de l'image (clé des traitements mis en cache, ex. duotone). */
    val adresse: String,
)

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
    private class Entree(val valeur: Nouveaute?, val largeur: Int, val date: Long, val version: Int)

    private val memoire = ConcurrentHashMap<String, Entree>()
    private val verrous = ConcurrentHashMap<String, Mutex>()
    private const val DUREE = 6 * 3_600_000L
    private const val DUREE_ECHEC = 15 * 60_000L

    /** Service de streaming reconnu (catalogue TMDB possible), sans accès réseau. */
    fun aUnCatalogue(app: AppInfo): Boolean {
        if (app.isSpecial) return false
        val id = ServicesTmdb.idDe(app) ?: return false
        return id in ServicesTmdb.connus || id in ServicesTmdb.parNom
    }

    private fun valable(e: Entree?, largeur: Int): Entree? {
        if (e == null || e.version != CleTmdb.version) return null
        val duree = if (e.valeur == null) DUREE_ECHEC else DUREE
        if (System.currentTimeMillis() - e.date > duree) return null
        // Image décodée trop petite pour cette taille d'affichage : on la relit
        if (e.valeur != null && largeur > e.largeur * 1.2f) return null
        return e
    }

    /** Nouveauté déjà en mémoire (affichage immédiat, sans attendre). */
    fun enCache(app: AppInfo, largeur: Int): Nouveaute? = valable(memoire[app.packageName], largeur)?.valeur

    /**
     * Nouveauté du service de [app], image décodée à [largeur] px au plus ; null : pas un
     * service connu, pas de clé TMDB, réseau injoignable ou aucun titre avec image.
     */
    suspend fun charger(context: Context, app: AppInfo, largeur: Int): Nouveaute? {
        if (!aUnCatalogue(app)) return null
        valable(memoire[app.packageName], largeur)?.let { return it.valeur }
        val appli = context.applicationContext
        return verrous.getOrPut(app.packageName) { Mutex() }.withLock {
            valable(memoire[app.packageName], largeur)?.let { return@withLock it.valeur }
            val version = CleTmdb.version
            val n = withContext(Dispatchers.IO) { runCatching { lire(appli, app, largeur) }.getOrNull() }
            memoire[app.packageName] = Entree(n, largeur, System.currentTimeMillis(), version)
            n
        }
    }

    private fun lire(context: Context, app: AppInfo, largeur: Int): Nouveaute? {
        val tmdb = Tmdb(context)
        val titres = tmdb.dernieresSorties(app, 6) ?: return null
        // Le plus récent qui a une image lisible
        for (t in titres.take(3)) {
            val image = tmdb.image(t.image, largeur) ?: continue
            return Nouveaute(t.titre, t.genre, image, t.image)
        }
        return null
    }

    /** Tests et aperçus : nouveauté imposée pour un paquet (sans réseau). */
    fun imposer(paquet: String, nouveaute: Nouveaute?) {
        memoire[paquet] = Entree(nouveaute, Int.MAX_VALUE / 2, System.currentTimeMillis(), CleTmdb.version)
    }

    /** Oublie tout (changement de clé, tests). */
    fun vider() {
        memoire.clear()
    }
}
