package fr.projo.launcher.nba

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Données du design NBA (docs/design-nba.md, « Données ») : flux publics d'ESPN, lus par
 * [EntrepotNba]. Le classement est relu du disque au démarrage de l'appli ([demarrer]), puis
 * rafraîchi toutes les 10 min seulement tant que le design est affiché ([suivre]) ; rien ne
 * tourne sinon. Après un échec, le retour du réseau relance l'essai sans attendre ([reseauRetrouve]).
 */
object DonneesNba {

    private const val TAG = "Projo.NbaCarte"

    private val _classement = MutableStateFlow<ClassementNba?>(null)

    /** Dernier classement connu (cache disque au démarrage, puis rafraîchi). */
    val classement: StateFlow<ClassementNba?> = _classement.asStateFlow()

    /** Requêtes réseau et horloge (ms, epoch) : remplacées dans les tests. */
    @Volatile
    internal var chargeur: ChargeurEspn = ReseauEspn

    @Volatile
    internal var horloge: () -> Long = { System.currentTimeMillis() }

    /** Une erreur dans la boucle ne doit jamais arrêter l'appli (c'est l'accueil). */
    private val portee = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w(TAG, "nba carte : erreur dans le suivi", e) },
    )
    private val verrou = Any()
    private val verrouChargement = Mutex()
    private var boucle: Job? = null
    private var demarre = false

    /** Réveille la boucle pendant son attente (retour du réseau). */
    private val reveil = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var entrepot = nouvelEntrepot()

    private fun nouvelEntrepot() = EntrepotNba(
        chargeur = { chargeur.charger(it) },
        horloge = { horloge() },
        etat = _classement,
        journal = ::journal,
    )

    /**
     * Démarrage de l'appli (MainActivity) : relit le classement et la liste des équipes du cache
     * disque (quelques Ko), sans requête. Idempotent.
     */
    internal fun demarrer(context: Context) {
        synchronized(verrou) {
            if (demarre) return
            demarre = true
        }
        entrepot.brancher(FichiersNba(File(context.applicationContext.noBackupFilesDir, "nba-carte")))
        suivreReseau(context.applicationContext)
    }

    /** Un réseau avec Internet devient disponible : appelé aussi tout de suite s'il y en a déjà un. */
    private fun suivreReseau(app: Context) {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            val demande = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            cm.registerNetworkCallback(demande, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = reseauRetrouve()
            })
        } catch (e: Exception) {
            journal("nba carte : suivi du réseau indisponible")
        }
    }

    /** Retour du réseau : après un échec, nouvel essai tout de suite (au lieu d'attendre jusqu'à 10 min). */
    internal fun reseauRetrouve() {
        entrepot.plan.reseauRetrouve()
        reveil.trySend(Unit)
    }

    /** Le design NBA est-il affiché ? (rafraîchissement toutes les 10 min tant qu'il l'est, rien sinon) */
    fun suivre(actif: Boolean) {
        synchronized(verrou) {
            entrepot.plan.actif = actif
            if (!actif) {
                boucle?.cancel()
                boucle = null
            } else if (boucle?.isActive != true) {
                boucle = portee.launch { tourner() }
            }
        }
    }

    /** Un chargement à la fois (une boucle arrêtée peut finir le sien) ; s'arrête quand le design n'est plus affiché. */
    private suspend fun tourner() {
        while (true) {
            val attente = verrouChargement.withLock {
                val e = entrepot
                when (val etape = e.etape()) {
                    PlanClassement.Etape.Arret -> return
                    is PlanClassement.Etape.Attendre -> etape.ms
                    PlanClassement.Etape.Charger -> {
                        withContext(Dispatchers.IO) { e.charger() }
                        0L
                    }
                }
            }
            if (attente > 0) withTimeoutOrNull(attente) { reveil.receive() }
        }
    }

    /** Fiche d'une équipe, null si indisponible. */
    suspend fun fiche(code: String): FicheEquipe? = try {
        entrepot.fiche(code)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        journal("nba carte : fiche indisponible (erreur)")
        null
    }

    /** Adresse du logo d'une équipe (code NBA). */
    fun logo(code: String): String = entrepot.logo(code)

    /** Code NBA → abréviation ESPN (GSW → GS…). */
    fun codeEspn(code: String): String = when (code) {
        "GSW" -> "GS"
        "NYK" -> "NY"
        "SAS" -> "SA"
        "NOP" -> "NO"
        "UTA" -> "UTAH"
        "WAS" -> "WSH"
        else -> code
    }

    private fun journal(evenement: String) {
        Log.i(TAG, evenement)
    }

    /** Tests et captures : impose un classement (sans requête ni disque). */
    internal fun imposer(c: ClassementNba?) {
        _classement.value = c
    }

    /** Tests : repart de zéro (boucle arrêtée, rien en mémoire, vrai réseau ; le disque n'est pas effacé). */
    internal fun oublier() {
        synchronized(verrou) {
            boucle?.cancel()
            boucle = null
            demarre = false
            chargeur = ReseauEspn
            horloge = { System.currentTimeMillis() }
            _classement.value = null
            entrepot = nouvelEntrepot()
        }
    }
}
