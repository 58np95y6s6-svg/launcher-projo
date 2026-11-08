package fr.projo.launcher.boxscore

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference

/**
 * Box score NBA par-dessus la vidéo (docs/telecommande.md, « Box score NBA ») : un seul objet pour
 * toute l'appli, sur le fil principal. Il pilote [SuiviNba] (requêtes réseau sur un fil d'E/S,
 * une à la fois), la fenêtre de la TV ([FenetreBoxScore], portée par le service d'accessibilité
 * relié) et prévient la télécommande quand l'état `nba` change ([surChangement]).
 *
 * Rien ne tourne tant qu'aucun téléphone n'est abonné et que rien n'est affiché : ni requête,
 * ni minuterie. La fenêtre est retirée dès que le box score est caché.
 *
 * Journal : types d'événements seulement (« nba : affichage résumé »), jamais de score.
 */
object BoxScoreNba {

    private const val TAG = "Projo.Nba"

    private val principal = Handler(Looper.getMainLooper())
    /** Une erreur dans la boucle des requêtes ne doit jamais arrêter l'appli (c'est l'accueil). */
    private val portee = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e -> Log.w(TAG, "nba : erreur dans le suivi", e) },
    )
    private var suivi = SuiviNba()
    private var touches = TouchesBoxScore()

    /** Requêtes réseau et leur fil (remplacés dans les tests). */
    internal var chargeur: ChargeurNba = ReseauNba
    internal var filReseau: CoroutineDispatcher = Dispatchers.IO

    private var contexte: Context? = null
    private var boucle: Job? = null
    private val reveil = Channel<Unit>(Channel.CONFLATED)

    private var service: WeakReference<AccessibilityService>? = null
    private var fenetre: FenetreBoxScore? = null

    private val _vue = MutableStateFlow(VueTv.CACHEE)

    /** Ce que montre la fenêtre de la TV. */
    val vue: StateFlow<VueTv> = _vue.asStateFlow()

    /** L'état `nba` a changé (n'importe quel fil) : la télécommande le renvoie aux téléphones abonnés. */
    @Volatile
    var surChangement: (() -> Unit)? = null

    private var dernierEtat: EtatNba? = null
    private var derniereSource = SourceNba.NBA
    private var ecouteEcran = false

    private fun horloge(): Long = SystemClock.elapsedRealtime()

    /** Premier démarrage (télécommande) : réglage du décalage. Fil principal, idempotent. */
    fun demarrer(context: Context) {
        if (contexte != null) return
        val app = context.applicationContext
        contexte = app
        DecalageNba.charger(app)
        suivi.decalage = DecalageNba.secondes
    }

    // ------------------------------------------------------------------ demandes du téléphone (fil principal)

    /** État `nba` à cet instant (la fenêtre suit aussi : le box score a pu se cacher de lui-même). */
    fun etat(): EtatNba {
        publier()
        return dernierEtat ?: suivi.etat()
    }

    /** `nba_afficher` : format (déjà reconnu) et match facultatif. */
    fun afficher(format: Affichage, id: String?) {
        val avant = suivi.affichage
        val retenu = suivi.afficher(format, id, horloge())
        when {
            retenu != avant -> journal("nba : affichage " + nomFormat(retenu))
            format != retenu -> journal("nba : affichage refusé (" + (if (!suivi.accessibilite) "accessibilité" else "aucun match") + ")")
        }
        publier()
        relancer()
    }

    /** `nba_choisir` : ignoré si le match n'est pas dans la liste. */
    fun choisir(id: String) {
        if (suivi.choisir(id, horloge())) journal("nba : match choisi")
        publier()
        relancer()
    }

    /** Un téléphone au moins est abonné à `nba` (ou plus aucun). */
    fun abonnement(actif: Boolean) {
        suivi.abonnement(actif, horloge())
        publier()
        relancer()
    }

    /** Réglage `nba_decalage` changé. */
    fun reglerDecalage(secondes: Int) {
        suivi.decalage = secondes
        publier()
    }

    // ------------------------------------------------------------------ service d'accessibilité (fil principal)

    /** Un service d'accessibilité d'iBiTV est relié : il portera la fenêtre. */
    fun attacher(s: AccessibilityService) {
        demarrer(s)
        service = WeakReference(s)
        fenetre?.cacher()
        fenetre = FenetreBoxScore(s)
        suivi.accessibilite = true
        publier()
    }

    /** Le service s'arrête : plus de fenêtre possible. */
    fun detacher(s: AccessibilityService) {
        if (service?.get() !== s) return
        fenetre?.cacher()
        fenetre = null
        service = null
        suivi.accessibilite = false
        suivi.cacher(horloge())
        publier()
    }

    /**
     * Touche de la télécommande de la TV (avant l'appli). Seulement en format détails : ◀ ▶
     * changent d'équipe, Retour cache le box score (appui et relâchement gardés).
     */
    fun touche(event: KeyEvent): Boolean = try {
        touches.touche(event.keyCode, event.action, event.repeatCount, suivi.affichage == Affichage.DETAILS) { c ->
            when (c) {
                TouchesBoxScore.Commande.EQUIPE_EXT -> suivi.changerEquipe(0)
                TouchesBoxScore.Commande.EQUIPE_DOM -> suivi.changerEquipe(1)
                TouchesBoxScore.Commande.CACHER -> {
                    suivi.cacher(horloge())
                    journal("nba : affichage caché (Retour)")
                }
            }
            publier()
        }
    } catch (e: Exception) {
        false
    }

    /** L'accueil d'iBiTV est revenu au premier plan : le box score se cache. */
    fun accueilAffiche() {
        if (suivi.affichage == Affichage.CACHE) return
        suivi.cacher(horloge())
        journal("nba : affichage caché (accueil)")
        publier()
    }

    // ------------------------------------------------------------------ état

    /** Calcule l'état à cet instant : fenêtre, `nba` (si changé), prochaine réponse à montrer. */
    private fun publier() {
        val t = horloge()
        suivi.avancer(t)
        val e = suivi.etat()
        if (e != dernierEtat) {
            dernierEtat = e
            surChangement?.invoke()
        }
        val v = suivi.vueTv()
        _vue.value = v
        majFenetre(v.affichage)
        principal.removeCallbacks(maturite)
        suivi.prochaineMaturite()?.let { principal.postDelayed(maturite, (it - t).coerceAtLeast(0L) + 20L) }
    }

    private val maturite = Runnable { publier() }

    private fun majFenetre(a: Affichage) {
        val f = fenetre
        if (a == Affichage.CACHE) {
            f?.cacher()
            ecouterEcran(false)
            return
        }
        if (f == null || !f.afficher()) {
            // Fenêtre refusée (service pas encore prêt) : rien d'affiché
            Log.w(TAG, "nba : fenêtre impossible à afficher")
            suivi.cacher(horloge())
            principal.post { publier() }
            return
        }
        ecouterEcran(true)
    }

    /** Écran éteint pendant l'affichage : le box score se cache (plus rien n'est chargé). */
    private val surEcran = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF || suivi.affichage == Affichage.CACHE) return
            suivi.cacher(horloge())
            journal("nba : affichage caché (écran éteint)")
            publier()
        }
    }

    private fun ecouterEcran(actif: Boolean) {
        val c = contexte ?: return
        if (actif == ecouteEcran) return
        ecouteEcran = actif
        try {
            if (actif) {
                ContextCompat.registerReceiver(c, surEcran, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
            } else {
                c.unregisterReceiver(surEcran)
            }
        } catch (e: Exception) {
            // sans importance
        }
    }

    // ------------------------------------------------------------------ requêtes

    /** Démarre la boucle des requêtes si besoin, ou la réveille (un besoin a changé). */
    private fun relancer() {
        if (boucle?.isActive == true) {
            reveil.trySend(Unit)
            return
        }
        boucle = portee.launch { tourner() }
    }

    /** Une requête à la fois ; s'arrête quand plus rien n'est à charger. */
    private suspend fun tourner() {
        while (true) {
            val t = horloge()
            when (val e = suivi.prochaine(t)) {
                SuiviNba.Etape.Arret -> return
                is SuiviNba.Etape.Attendre -> withTimeoutOrNull((e.jusqua - t).coerceAtLeast(1L)) { reveil.receive() }
                is SuiviNba.Etape.Tableau -> {
                    val r = withContext(filReseau) { chargeur.tableau(e.source) }
                    noterEchec(e.source, r)
                    suivi.tableauRecu(horloge(), e.source, r)
                    noterSource()
                    publier()
                }
                is SuiviNba.Etape.Box -> {
                    val r = withContext(filReseau) { chargeur.box(e.source, e.match) }
                    noterEchec(e.source, r)
                    suivi.boxRecu(horloge(), e, r)
                    noterSource()
                    publier()
                }
            }
        }
    }

    /** Journal : source indisponible (code HTTP), jamais d'adresse ni de score. */
    private fun noterEchec(source: SourceNba, r: SuiviNba.Reponse<*>) {
        if (r is SuiviNba.Reponse.Echec) {
            journal("nba : source " + nomSource(source) + " indisponible" + (r.code?.let { " ($it)" } ?: ""))
        }
    }

    /** Journal : repli sur ESPN, retour à la source NBA. */
    private fun noterSource() {
        val s = suivi.source
        if (s == derniereSource) return
        derniereSource = s
        journal(if (s == SourceNba.ESPN) "nba : repli sur la source ESPN" else "nba : retour à la source NBA")
    }

    private fun nomSource(s: SourceNba) = if (s == SourceNba.NBA) "NBA" else "ESPN"

    private fun nomFormat(a: Affichage) = when (a) {
        Affichage.CACHE -> "caché"
        Affichage.RESUME -> "résumé"
        Affichage.DETAILS -> "détails"
    }

    private fun journal(evenement: String) {
        Log.i(TAG, evenement)
    }

    /** Tests : repart de zéro (boucle arrêtée, fenêtre retirée, suivi neuf, vrai réseau). */
    internal fun oublier() {
        boucle?.cancel()
        boucle = null
        principal.removeCallbacks(maturite)
        fenetre?.cacher()
        fenetre = null
        service = null
        ecouterEcran(false)
        suivi = SuiviNba()
        touches = TouchesBoxScore()
        dernierEtat = null
        derniereSource = SourceNba.NBA
        _vue.value = VueTv.CACHEE
        surChangement = null
        chargeur = ReseauNba
        filReseau = Dispatchers.IO
        contexte = null
    }
}
