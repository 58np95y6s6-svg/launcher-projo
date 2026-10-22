package fr.projo.launcher.ui

import android.app.AppOpsManager
import android.app.SearchManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import fr.projo.launcher.data.AppCategories
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppRepository
import fr.projo.launcher.data.ClientTmdb
import fr.projo.launcher.data.CleTmdb
import fr.projo.launcher.data.GenreTmdb
import fr.projo.launcher.data.ImagesTmdb
import fr.projo.launcher.data.ServicesTmdb
import fr.projo.launcher.data.Tmdb
import fr.projo.launcher.data.Rubrique
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Suggestions du design « Studio » (rangée du haut de l'écran 1) : des films et séries
 * DISPONIBLES SUR LES APPS DE STREAMING INSTALLÉES, lus sur TMDB (client partagé :
 * data/Tmdb.kt). Les tendances de la semaine disponibles sur un service installé d'abord,
 * puis les titres populaires de chaque service en alternance. Sans clé ou sans réseau :
 * repli sur les « apps à la une ».
 *
 * Pourquoi pas les chaînes / Watch Next d'Android TV (TvContract) ? Le fournisseur TV ne rend
 * à une app que ses propres lignes, sauf avec ACCESS_ALL_EPG_DATA (signature|privileged) :
 * un launcher installé normalement n'y voit rien, et le Fire TV n'a pas ce fournisseur.
 */

/** Un titre proposé : vu sur TMDB, disponible sur un service installé ([paquet], [service]). */
data class StudioProgramme(
    val id: String,
    val titre: String,
    val genre: GenreTmdb,
    val genres: List<String>,
    val paquet: String,
    val service: String,
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

/**
 * Suggestions TMDB avec la clé [cle] (lue à chaque appel) ; [lireTexte] / [lireOctets] :
 * réseau avec cache disque, remplaçables dans les tests.
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
            val services = ServicesTmdb.installes(apps, liste)
            val choix = client.alaUne(services) ?: return StudioResultat(emptyList(), StudioEtat.ERREUR)
            Log.i(AppRepository.TAG, "Studio : ${choix.size} suggestions TMDB (${services.joinToString { it.idIllustration }})")
            StudioResultat(
                choix.map { (s, t) -> StudioProgramme(t.id, t.titre, t.genre, t.genres, s.app.packageName, s.app.label, t.image) },
                StudioEtat.OK,
            )
        } catch (e: Exception) {
            StudioResultat(emptyList(), StudioEtat.ERREUR)
        }
    }

    override fun plusRecente(paquets: Collection<String>): String? = null

    override fun image(adresse: String, largeur: Int): ImageBitmap? = ImagesTmdb.image(adresse, largeur, lireOctets)
}

/** Source réelle : TMDB partagé (clé, cache disque d'un jour) et données d'utilisation. */
class StudioSourceAndroid(private val context: Context) : StudioSource {
    private val tmdb = Tmdb(context)
    private val source = StudioSourceTmdb({ CleTmdb.lire(context) }, tmdb::texte, tmdb::octets)

    override fun suggestions(apps: List<AppInfo>) = source.suggestions(apps).also { tmdb.nettoyer() }

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
    listOfNotNull(if (p.genre == GenreTmdb.FILM) "Film" else "Série", p.genres.firstOrNull(), p.service).joinToString(" · ")

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
    val version = CleTmdb.version
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
    // Un film ou une série : la musique en arrière-plan (Spotify…) se met en pause
    fr.projo.launcher.data.PauseMusique.avantOuverture(context, p.paquet)
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
