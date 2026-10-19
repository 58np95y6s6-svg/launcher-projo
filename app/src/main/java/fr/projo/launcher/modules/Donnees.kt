package fr.projo.launcher.modules

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * État d'une donnée de module.
 *  - valeur = null et erreur = false : premier chargement en cours ;
 *  - erreur = true : le dernier essai a échoué (valeur = dernière valeur connue, ou null).
 */
class EtatDonnees<out T>(val valeur: T?, val erreur: Boolean = false, val date: Long = 0L) {
    val chargement: Boolean get() = valeur == null && !erreur
}

/**
 * Données asynchrones des modules, avec cache en mémoire (une entrée par clé, gardée par le
 * ViewModel : changer de design ou revenir sur l'accueil ne recharge rien tant que la donnée
 * est fraîche).
 *
 * Chaque clé n'est rechargée qu'à l'échéance que choisit le module (délai selon la valeur),
 * hors du thread principal, et seulement pendant que l'accueil est affiché.
 *
 * Tests : [factices] fournit directement la valeur de chaque clé (aucun réseau). Elle peut
 * renvoyer une valeur, un [EtatDonnees] (pour simuler une erreur), ou null (chargement).
 */
class DonneesModules(private val factices: ((String) -> Any?)? = null) {

    private val etats = ConcurrentHashMap<String, MutableStateFlow<EtatDonnees<Any?>>>()

    fun flux(cle: String): MutableStateFlow<EtatDonnees<Any?>> =
        etats.getOrPut(cle) { MutableStateFlow(etatInitial(cle)) }

    private fun etatInitial(cle: String): EtatDonnees<Any?> {
        val f = factices ?: return EtatDonnees(null)
        return when (val v = f(cle)) {
            is EtatDonnees<*> -> v
            else -> EtatDonnees(v, date = System.currentTimeMillis())
        }
    }

    /** Force le rechargement des clés qui commencent par [prefixe] (ex. après un changement de ville). */
    fun invalider(prefixe: String) {
        for ((cle, f) in etats) {
            if (cle.startsWith(prefixe)) f.value = EtatDonnees(f.value.valeur, f.value.erreur, 0L)
        }
    }

    /**
     * Boucle de mise à jour d'une clé (annulée quand le module disparaît ou que l'accueil
     * n'est plus affiché). [delai] : temps avant le prochain chargement réussi ;
     * [delaiErreur] : avant un nouvel essai après un échec.
     */
    suspend fun <T> boucle(cle: String, delai: (T) -> Long, delaiErreur: Long, charger: suspend () -> T) {
        if (factices != null) return
        val f = flux(cle)
        while (true) {
            val e = f.value
            if (e.date != 0L) {
                @Suppress("UNCHECKED_CAST")
                val ms = if (e.erreur) delaiErreur else delai(e.valeur as T)
                val attente = e.date + ms - System.currentTimeMillis()
                // Attente jusqu'à l'échéance, écourtée par une invalidation
                if (attente > 0) withTimeoutOrNull(attente) { f.first { it.date == 0L } }
            }
            val resultat = try {
                withContext(Dispatchers.IO) { withTimeout(DELAI_MAX_CHARGEMENT) { charger() } }
            } catch (ex: CancellationException) {
                // Annulation de la boucle (et non délai dépassé) : on s'arrête
                if (ex !is kotlinx.coroutines.TimeoutCancellationException) throw ex
                Log.w(TAG, "Module : délai dépassé pour $cle")
                null
            } catch (ex: Exception) {
                Log.w(TAG, "Module : échec du chargement de $cle", ex)
                null
            }
            val maintenant = System.currentTimeMillis()
            f.value = if (resultat != null) EtatDonnees(resultat, false, maintenant)
            else EtatDonnees(f.value.valeur, true, maintenant)
        }
    }

    companion object {
        const val TAG = "ProjoModules"
        private const val DELAI_MAX_CHARGEMENT = 25_000L
    }
}

/**
 * Donnée d'un module, chargée par [charger] (hors du thread principal) et rafraîchie selon
 * [delai] (en ms, d'après la dernière valeur). La clé identifie la donnée : la changer (autre
 * ville…) lance un nouveau chargement. Exemple :
 *
 *     val etat = ctx.rememberDonnees("meteo:$lieu", delai = { 20 * 60_000L }) { chargerMeteo(lieu) }
 *     etat.valeur?.let { … } ?: if (etat.erreur) Text("Indisponible") else Text("Chargement…")
 */
@Composable
fun <T> ModuleContexte.rememberDonnees(
    cle: String,
    delai: (T) -> Long,
    delaiErreur: Long = 2 * 60_000L,
    charger: suspend () -> T,
): EtatDonnees<T> {
    val hub = donnees
    val flux = remember(hub, cle) { hub.flux(cle) }
    val etat by flux.collectAsState()
    val chargerActuel by rememberUpdatedState(charger)
    val delaiActuel by rememberUpdatedState(delai)
    val cycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(hub, cle, cycle) {
        cycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            hub.boucle<T>(cle, { delaiActuel(it) }, delaiErreur) { chargerActuel() }
        }
    }
    @Suppress("UNCHECKED_CAST")
    return etat as EtatDonnees<T>
}

/** Accès réseau des modules : HTTPS, délais d'attente courts, à appeler hors du thread principal. */
object Reseau {
    fun lireTexte(url: String, delai: Int = 7000): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = delai
            readTimeout = delai
            setRequestProperty("User-Agent", "Projo-Launcher")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = c.responseCode
            if (code != 200) throw ErreurHttp(code)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    class ErreurHttp(val code: Int) : Exception("HTTP $code")
}
