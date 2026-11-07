package fr.projo.launcher.liens

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.projo.launcher.MainActivity
import fr.projo.launcher.data.AppRepository
import fr.projo.launcher.puces.PucesNfc
import fr.projo.launcher.puces.RouteRaccourci
import fr.projo.launcher.remote.EtatLiens
import fr.projo.launcher.remote.Telecommande
import fr.projo.launcher.system.AdresseLocale
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Bandeau « <titre ou hôte> · envoyé par … — Ouverture dans <appli>… » (environ 1 s, iBiTV à l'écran). */
data class BandeauLien(
    /** Titre, sinon l'hôte. */
    val texte: String,
    /** Hôte affiché (initiale et couleur de la pastille). */
    val hote: String,
    /** « le téléphone de Marie », « l'iPhone ». */
    val expediteur: String,
    /** Appli qui va s'ouvrir. */
    val appli: String,
)

/**
 * « Envoyer sur la TV » côté Android (docs/telecommande.md, « Envoyer sur la TV : liens ») :
 * réglages enregistrés (états Compose : Réglages de la TV, télécommande), suivi du VPN
 * (ConnectivityManager, réseaux TRANSPORT_VPN), ouverture des liens avec le bandeau, garde-fou
 * du raccourci iOS et son code. Le déroulé (VPN, attente) est dans [EnvoiLiens] ; tout se passe
 * sur le fil principal, sauf la route HTTP du raccourci qui attend la réponse sur son propre fil.
 *
 * Le journal ne contient jamais l'URL, le titre ni le code.
 */
object EnvoiTv {

    private const val TAG = "Projo.Liens"
    private const val FICHIER = "envoyer_tv"
    private const val CLE_VPN = "seulement_vpn"
    private const val CLE_PAGES = "pages"
    private const val CLE_RACCOURCI = "raccourci"
    private const val CLE_CODE = "code"

    /** Code du raccourci : 8 caractères, lettres A–Z sans I ni O, chiffres 2–9. */
    const val ALPHABET_CODE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LONGUEUR_CODE = 8

    /** Réglage « Seulement avec le VPN » (activé par défaut). */
    var seulementVpn by mutableStateOf(true)
        private set

    /** Appli des pages web : `auto` ou un paquet. */
    var pages by mutableStateOf(Liens.PAGES_AUTO)
        private set

    /** Liens du bouton Partager de l'iPhone acceptés (désactivé par défaut). */
    var raccourci by mutableStateOf(false)
        private set

    /** Code du raccourci (vide tant que le raccourci n'a jamais été activé). */
    var code by mutableStateOf("")
        private set

    /** Un VPN est actif sur la TV (suivi dès [demarrer]). */
    var vpnActif by mutableStateOf(false)
        private set

    /** Bandeau affiché avant l'ouverture d'un lien, ou null. */
    var bandeau by mutableStateOf<BandeauLien?>(null)
        private set

    /** Garde-fou affiché (raccourci iOS, VPN coupé), ou null. */
    var question by mutableStateOf<QuestionVpn?>(null)
        private set

    // Copies lues par le fil du serveur HTTP
    @Volatile
    private var raccourciCourant = false

    @Volatile
    private var codeCourant = ""

    private var lu = false
    private var suivi = false
    private var envoi: EnvoiLiens? = null
    private val principal = Handler(Looper.getMainLooper())
    private val ecouteurs = CopyOnWriteArrayList<() -> Unit>()

    /** Réseaux VPN vus par le suivi (fil principal). */
    private val reseauxVpn = HashSet<Network>()

    // ------------------------------------------------------------------ réglages

    fun charger(context: Context) {
        if (lu) return
        lu = true
        val p = context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
        seulementVpn = p.getBoolean(CLE_VPN, true)
        pages = p.getString(CLE_PAGES, null) ?: Liens.PAGES_AUTO
        raccourci = p.getBoolean(CLE_RACCOURCI, false)
        code = p.getString(CLE_CODE, "") ?: ""
        raccourciCourant = raccourci
        codeCourant = code
    }

    /** Réglages lus et VPN suivi (idempotent) : à l'ouverture d'iBiTV et au démarrage du serveur. */
    fun demarrer(context: Context) {
        val app = context.applicationContext
        charger(app)
        suivreVpn(app)
    }

    /** Prévenu (sur n'importe quel fil) quand le VPN ou un réglage du groupe change. */
    fun ecouter(ecouteur: () -> Unit) {
        ecouteurs += ecouteur
    }

    private fun notifier() {
        for (e in ecouteurs) e()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    fun ecrireSeulementVpn(context: Context, actif: Boolean) {
        charger(context)
        prefs(context).edit().putBoolean(CLE_VPN, actif).apply()
        seulementVpn = actif
        notifier()
    }

    /** `auto` ou le paquet d'une appli qui ouvre les pages web. */
    fun ecrirePages(context: Context, id: String) {
        charger(context)
        prefs(context).edit().putString(CLE_PAGES, id).apply()
        pages = id
        notifier()
    }

    /** Raccourci iOS : le serveur HTTP de la TV (port 8642) écoute dès qu'il est activé. */
    fun ecrireRaccourci(context: Context, actif: Boolean) {
        charger(context)
        val e = prefs(context).edit()
        if (actif && code.isEmpty()) {
            code = nouveauCodeAleatoire()
            codeCourant = code
            e.putString(CLE_CODE, code)
        }
        e.putBoolean(CLE_RACCOURCI, actif).apply()
        raccourci = actif
        raccourciCourant = actif
        PucesNfc.evaluerServeur(context.applicationContext)
        notifier()
    }

    /** Nouveau code : l'ancien raccourci ne marche plus. */
    fun nouveauCode(context: Context) {
        charger(context)
        code = nouveauCodeAleatoire()
        codeCourant = code
        prefs(context).edit().putString(CLE_CODE, code).apply()
        Log.i(TAG, "Raccourci : nouveau code créé")
        notifier()
    }

    private fun nouveauCodeAleatoire(): String {
        val r = SecureRandom()
        return String(CharArray(LONGUEUR_CODE) { ALPHABET_CODE[r.nextInt(ALPHABET_CODE.length)] })
    }

    /** Adresse à mettre dans le raccourci iOS ; null : réglage coupé ou pas d'adresse sur le réseau local. */
    fun adresseRaccourci(): String? {
        if (!raccourci || code.isEmpty()) return null
        val ip = AdresseLocale.lire() ?: return null
        return adresseRaccourci(ip, code)
    }

    /** « http://192.168.1.42:8642/envoyer?c=K7Q2M9XD » */
    fun adresseRaccourci(ip: String, code: String): String = "http://$ip:${PucesNfc.PORT}/envoyer?c=$code"

    /** État envoyé au téléphone (`liens`). Fil principal. */
    fun etat(context: Context): EtatLiens {
        val app = context.applicationContext
        demarrer(app)
        return EtatLiens(
            vpn = vpnActif,
            seulementVpn = seulementVpn,
            appliPages = OuvertureLiens.ordrePages(app, pages).firstOrNull()?.second ?: "",
            appliVpn = appliVpn(app)?.second,
            raccourci = adresseRaccourci(),
        )
    }

    // ------------------------------------------------------------------ appli VPN

    /** Appli VPN connue installée (paquet, nom), la première de Liens.APPLIS_VPN ; null s'il n'y en a pas. */
    fun appliVpn(context: Context): Pair<String, String>? {
        val pm = context.packageManager
        val (paquet, nomConnu) = Liens.appliVpn { p ->
            try {
                pm.getLeanbackLaunchIntentForPackage(p) != null || pm.getLaunchIntentForPackage(p) != null
            } catch (e: Exception) {
                false
            }
        } ?: return null
        val nom = OuvertureLiens.nom(pm, paquet).takeIf { it != paquet && it.isNotBlank() } ?: nomConnu
        return paquet to nom
    }

    /** Ouvre l'appli VPN comme sa tuile ; false s'il n'y en a pas ou si elle ne s'ouvre pas. */
    fun ouvrirAppliVpn(context: Context): Boolean {
        val (paquet, _) = appliVpn(context) ?: return false
        return try {
            AppRepository(context.applicationContext).launch(paquet)
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ suivi du VPN

    private fun suivreVpn(app: Context) {
        if (suivi) return
        suivi = true
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        vpnActif = lireVpn(cm)
        try {
            // Par défaut une demande exclut les VPN (NOT_VPN) : on la retire
            val demande = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            cm.registerNetworkCallback(demande, object : ConnectivityManager.NetworkCallback() {
                // Appelé aussi tout de suite pour chaque VPN déjà actif
                override fun onAvailable(network: Network) {
                    principal.post { reseauxVpn += network; majVpn() }
                }

                override fun onLost(network: Network) {
                    principal.post { reseauxVpn -= network; majVpn() }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Suivi du VPN indisponible")
        }
    }

    @Suppress("DEPRECATION")
    private fun lireVpn(cm: ConnectivityManager): Boolean = try {
        cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    } catch (e: Exception) {
        false
    }

    /** Fil principal : VPN allumé ou coupé → lien en attente, télécommande, pastille. */
    private fun majVpn() {
        val actif = reseauxVpn.isNotEmpty()
        if (actif == vpnActif) return
        vpnActif = actif
        Log.i(TAG, if (actif) "VPN allumé" else "VPN coupé")
        envoi?.surVpn(actif)
        notifier()
    }

    // ------------------------------------------------------------------ ouverture

    /**
     * Ouvre [lien] (déjà vérifié) : voir EnvoiLiens. Fil principal ; [rappel] sur ce fil, une
     * ou deux fois (`attente_vpn` puis le résultat).
     */
    fun envoyer(
        context: Context,
        lien: LienTv,
        source: SourceLien,
        expediteur: String,
        sansVpn: Boolean = false,
        vpnDabord: Boolean = false,
        rappel: (ResultatLien) -> Unit,
    ) {
        val app = context.applicationContext
        demarrer(app)
        envoi(app).envoyer(lien, source, expediteur, sansVpn, vpnDabord, rappel)
    }

    /** Réponse au garde-fou (boutons du dialogue). Fil principal. */
    fun repondre(choix: ChoixQuestion) {
        envoi?.repondre(choix)
        if (envoi == null) question = null
    }

    /**
     * Route HTTP du raccourci iOS (fil du serveur) : attend le résultat, au plus 10 s ; null
     * si la TV n'a pas répondu à temps.
     */
    fun envoyerDepuisRaccourci(context: Context, lien: LienTv): ResultatLien? {
        val app = context.applicationContext
        val fini = CountDownLatch(1)
        val resultat = AtomicReference<ResultatLien?>(null)
        principal.post {
            try {
                envoyer(app, lien, SourceLien.RACCOURCI, Liens.PAR_IPHONE) { r ->
                    if (resultat.compareAndSet(null, r)) fini.countDown()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lien du raccourci impossible à ouvrir")
                fini.countDown()
            }
        }
        fini.await(10, TimeUnit.SECONDS)
        return resultat.get()
    }

    /** Route du serveur HTTP (port 8642) pour le raccourci iOS. */
    fun routeRaccourci(context: Context): RouteRaccourci {
        val app = context.applicationContext
        return RouteRaccourci(
            actif = { raccourciCourant },
            code = { codeCourant },
            nomTv = { Telecommande.nomTv },
            envoyer = { lien -> envoyerDepuisRaccourci(app, lien) },
            journal = { Log.i(TAG, it) },
        )
    }

    private fun envoi(app: Context): EnvoiLiens = envoi ?: EnvoiLiens(Plateforme(app)).also { envoi = it }

    /** iBiTV (l'accueil) est à l'écran : le bandeau y sera vu. */
    private fun ibitvAuPremierPlan(): Boolean = Telecommande.activiteAuPremierPlan is MainActivity

    /** Le garde-fou doit être vu même si l'utilisateur est dans une autre appli. */
    private fun montrerIBiTV(app: Context) {
        if (ibitvAuPremierPlan()) return
        try {
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // Lancement depuis l'arrière-plan refusé : la question s'affichera au retour sur l'accueil
        }
    }

    /** Bandeau environ 1 s si iBiTV est à l'écran, puis ouverture. */
    private fun ouvrirLien(app: Context, lien: LienTv, expediteur: String, rappel: (String?) -> Unit) {
        val candidats = OuvertureLiens.candidats(app, lien, pages)
        if (candidats.isEmpty()) {
            rappel(null)
            return
        }
        if (!ibitvAuPremierPlan()) {
            rappel(OuvertureLiens.lancer(app, candidats))
            return
        }
        val b = BandeauLien(lien.affichage, lien.hoteAffiche, expediteur, candidats.first().nom)
        bandeau = b
        principal.postDelayed({
            val ouverte = OuvertureLiens.lancer(app, candidats)
            principal.postDelayed({ if (bandeau === b) bandeau = null }, 400)
            rappel(ouverte)
        }, Liens.BANDEAU_MS)
    }

    private class Plateforme(private val app: Context) : PlateformeLiens {
        override fun vpnActif() = EnvoiTv.vpnActif
        override fun seulementVpn() = EnvoiTv.seulementVpn
        override fun appliVpn() = EnvoiTv.appliVpn(app)?.second
        override fun ouvrirAppliVpn() = EnvoiTv.ouvrirAppliVpn(app)
        override fun ouvrir(lien: LienTv, expediteur: String, rappel: (String?) -> Unit) = ouvrirLien(app, lien, expediteur, rappel)

        override fun montrerQuestion(q: QuestionVpn?) {
            question = q
            if (q != null) montrerIBiTV(app)
        }

        override fun planifier(delaiMs: Long, tache: () -> Unit): () -> Unit {
            val r = Runnable { tache() }
            principal.postDelayed(r, delaiMs)
            return { principal.removeCallbacks(r) }
        }

        override fun journal(evenement: String) {
            Log.i(TAG, evenement)
        }
    }

    /** Tout oublier (tests). */
    internal fun oublier() {
        lu = false
        seulementVpn = true
        pages = Liens.PAGES_AUTO
        raccourci = false
        code = ""
        raccourciCourant = false
        codeCourant = ""
        vpnActif = false
        bandeau = null
        question = null
        envoi = null
    }

    /** Tests : état du VPN et bandeau imposés. */
    internal fun imposer(vpn: Boolean? = null, bandeau: BandeauLien? = null, question: QuestionVpn? = null) {
        if (vpn != null) vpnActif = vpn
        this.bandeau = bandeau
        this.question = question
    }
}
