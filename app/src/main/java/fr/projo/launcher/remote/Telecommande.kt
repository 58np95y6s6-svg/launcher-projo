package fr.projo.launcher.remote

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import fr.projo.launcher.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference

/** État de l'appairage affiché dans Réglages → « Appairer un téléphone ». */
sealed class EtatAppairage {
    data object Inactif : EtatAppairage()

    /** QR code (URL complète), code de secours et fin de validité (horloge murale, ms). */
    data class EnCours(val url: String, val code: String, val expire: Long) : EtatAppairage()
    data object Expire : EtatAppairage()
    data class Reussi(val nom: String) : EtatAppairage()
}

/** Demande « Autoriser le téléphone de Marie ? » en attente d'une réponse. */
data class DemandeConfirmation(val nom: String)

/** Champ texte qui a le focus (vu par le clavier iBiProjo). */
data class InfoChamp(val type: String, val texte: String, val action: String)

/**
 * Télécommande smartphone, côté TV : un seul objet pour toute l'appli.
 *
 * Pas de service au premier plan : la connexion vit dans le processus d'iBiProjo, qui reste
 * en vie parce que c'est l'écran d'accueil (priorité « home »), et parce que le système garde
 * liés le clavier iBiProjo (s'il est choisi) et le service d'accessibilité (s'il est activé ;
 * sur Fire TV, le service d'ouverture automatique). Si Android tue quand même le processus,
 * le premier de ces composants à redémarrer rappelle [demarrer]. Avantages : aucune
 * notification permanente, aucune permission FOREGROUND_SERVICE (types obligatoires à partir
 * d'Android 14), et rien ne tourne quand la TV est éteinte.
 *
 * Coût mémoire : seule une WebSocket (OkHttp) est ouverte, et seulement si au moins un
 * téléphone est appairé ou qu'un appairage est en cours. WebRTC (bibliothèque native) n'est
 * chargé qu'à la première connexion d'un téléphone, et son usine est libérée 30 s après la
 * dernière déconnexion. Écran éteint : tout est fermé ; rallumé : tout reprend.
 *
 * Fil d'exécution : tout se passe sur un HandlerThread dédié (« telecommande »).
 */
object Telecommande : HoteSession {

    private const val TAG = "Projo.Telecommande"

    private val _appairage = MutableStateFlow<EtatAppairage>(EtatAppairage.Inactif)
    val etatAppairage: StateFlow<EtatAppairage> = _appairage.asStateFlow()

    private val _confirmation = MutableStateFlow<DemandeConfirmation?>(null)
    val confirmation: StateFlow<DemandeConfirmation?> = _confirmation.asStateFlow()

    private val _telephones = MutableStateFlow<List<TelephoneAppaire>>(emptyList())
    val telephones: StateFlow<List<TelephoneAppaire>> = _telephones.asStateFlow()

    /** Identifiants des téléphones connectés et authentifiés. */
    private val _connectes = MutableStateFlow<Set<String>>(emptySet())
    val connectes: StateFlow<Set<String>> = _connectes.asStateFlow()

    private var contexte: Context? = null
    private lateinit var fil: Handler
    private lateinit var http: OkHttpClient
    private var demarre = false

    private lateinit var _registre: RegistreTelecommande
    private lateinit var _identite: IdentiteTv
    override val registre: RegistreTelecommande get() = _registre
    override val identite: IdentiteTv get() = _identite
    override val appairage = GestionAppairage()
    private lateinit var _injecteur: Injecteur
    override val injecteur: Injecteur get() = _injecteur
    private var _nomTv = "iBiProjo"
    override val nomTv: String get() = _nomTv
    private var _versionApp = "?"
    override val versionApp: String get() = _versionApp

    private var principal: ClientPeerJs? = null
    private var temporaire: ClientPeerJs? = null
    private val connexions = mutableListOf<ConnexionWebRtc>()
    private var ecranAllume = true
    private var champ: InfoChamp? = null

    private var attente: Pair<SessionTelecommande, (Boolean) -> Unit>? = null

    /** Activité d'iBiProjo au premier plan (null sinon). */
    @Volatile
    private var activite: WeakReference<Activity>? = null
    val activiteAuPremierPlan: Activity? get() = activite?.get()

    /** Démarre la télécommande (idempotent) : MainActivity, clavier, service d'accessibilité. */
    @Synchronized
    fun demarrer(context: Context) {
        if (demarre) return
        demarre = true
        val app = context.applicationContext
        contexte = app
        fil = Handler(HandlerThread("telecommande").apply { start() }.looper)
        _injecteur = InjectionAndroid(app)
        (app as? Application)?.registerActivityLifecycleCallbacks(suiviActivites)
        fil.post {
            try {
                val dossier = File(app.filesDir, "telecommande")
                _registre = RegistreTelecommande(File(dossier, "telecommande.json"))
                _identite = IdentiteCles.charger(app, dossier)
                http = ClientPeerJs.clientHttp()
                _nomTv = nomAppareil(app)
                _versionApp = try {
                    @Suppress("DEPRECATION")
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
                } catch (e: Exception) { "?" }
                _telephones.value = _registre.telephones()
                ecranAllume = (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
                ContextCompat.registerReceiver(app, surEcran, IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }, ContextCompat.RECEIVER_NOT_EXPORTED)
                if (Build.VERSION.SDK_INT >= 24) {
                    (app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                        .registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: Network) {
                                fil.post { principal?.relancer(); temporaire?.relancer() }
                            }
                        })
                }
                evaluer()
            } catch (e: Exception) {
                Log.w(TAG, "Télécommande indisponible", e)
            }
        }
    }

    private val pret: Boolean get() = ::_registre.isInitialized && ::_identite.isInitialized

    private val surEcran = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val allume = intent.action == Intent.ACTION_SCREEN_ON
            fil.post {
                if (ecranAllume == allume) return@post
                ecranAllume = allume
                Log.i(TAG, if (allume) "Écran allumé : reprise" else "Écran éteint : pause")
                evaluer()
            }
        }
    }

    private val suiviActivites = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) { activite = WeakReference(activity) }
        override fun onActivityPaused(activity: Activity) { if (activite?.get() === activity) activite = null }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    /** Ouvre ou ferme les connexions selon l'écran, les téléphones appairés et l'appairage. */
    private fun evaluer() {
        if (!pret) return
        val secret = appairage.actif()
        val voulu = ecranAllume && (registre.telephones().isNotEmpty() || secret != null)
        if (voulu) {
            val p = principal ?: ClientPeerJs(http, fil, registre.idTv(), registre.jetonPeerJs(), { t, s, pl -> surSignal(principal, t, s, pl) })
                .also { principal = it }
            p.demarrer()
        } else {
            principal?.arreter()
            principal = null
            connexions.toList().forEach { it.terminer() }
        }
        // Code de secours : l'identifiant temporaire « ibiprojo-c-ABCD », seulement pendant l'appairage
        val idTemp = if (ecranAllume) secret?.idTemporaire else null
        if (temporaire?.id != idTemp) {
            temporaire?.arreter()
            temporaire = idTemp?.let { id ->
                ClientPeerJs(http, fil, id, RegistreTelecommande.hex(CryptoTelecommande.octetsAleatoires(8)), { t, s, pl -> surSignal(temporaire, t, s, pl) })
                    .also { it.demarrer() }
            }
        }
    }

    // ------------------------------------------------------------------ signalisation

    private fun surSignal(client: ClientPeerJs?, type: String, src: String, payload: JSONObject?) {
        if (client == null || src.isEmpty()) return
        when (type) {
            "OFFER" -> {
                val p = payload ?: return
                val sdp = p.optJSONObject("sdp")?.optString("sdp") ?: p.optString("sdp")
                if (sdp.isNullOrEmpty() || sdp.length > 100_000) return
                val cid = p.optString("connectionId").ifEmpty { src }
                connexions.filter { it.distant == src }.forEach { it.terminer() }
                while (connexions.size >= RemoteConfig.CONNEXIONS_MAX) {
                    (connexions.firstOrNull { it.session?.authentifiee != true } ?: connexions.first()).terminer()
                }
                val usine = try {
                    UsineWebRtc.obtenir(contexte!!)
                } catch (e: Throwable) {
                    Log.w(TAG, "WebRTC indisponible sur cet appareil")
                    return
                }
                fil.removeCallbacks(liberationUsine)
                val c = ConnexionWebRtc(usine, fil, client, src, cid, ::creerSession, ::surFin)
                connexions += c
                Log.i(TAG, "Connexion d'un téléphone")
                c.demarrer(sdp)
                fil.postDelayed({
                    val e = c.session?.etat
                    if (connexions.contains(c) && e != SessionTelecommande.Etat.AUTHENTIFIEE && e != SessionTelecommande.Etat.CLE_VERIFIEE) {
                        c.terminer()
                    }
                }, RemoteConfig.DELAI_AUTHENTIFICATION_MS)
            }
            "CANDIDATE" -> {
                val c = payload?.optJSONObject("candidate") ?: return
                val cid = payload.optString("connectionId")
                connexions.lastOrNull { it.distant == src && (cid.isEmpty() || it.connectionId == cid) }?.ajouterCandidat(c)
            }
            "LEAVE", "EXPIRE" -> connexions.filter { it.distant == src }.forEach { it.terminer() }
        }
    }

    private fun creerSession(c: ConnexionWebRtc, dtlsTel: String?, dtlsTv: String?) =
        SessionTelecommande(this, dtlsTel, dtlsTv, envoyer = { c.envoyer(it) }, fermer = { c.fermerBientot() })

    private val liberationUsine = Runnable { if (connexions.isEmpty()) UsineWebRtc.liberer() }

    private fun surFin(c: ConnexionWebRtc) {
        connexions.remove(c)
        majConnectes()
        if (connexions.isEmpty()) {
            fil.removeCallbacks(liberationUsine)
            fil.postDelayed(liberationUsine, 30_000)
        }
    }

    private fun majConnectes() {
        val avant = _connectes.value
        _connectes.value = connexions.mapNotNull { c -> c.session?.takeIf { it.authentifiee }?.appareil }.toSet()
        if (avant.isEmpty() != _connectes.value.isEmpty()) ClavierTelecommande.rafraichir()
    }

    // ------------------------------------------------------------------ HoteSession

    override fun demanderConfirmation(session: SessionTelecommande, nom: String, reponse: (Boolean) -> Unit): Boolean {
        if (attente != null) return false
        attente = session to reponse
        _confirmation.value = DemandeConfirmation(nom)
        montrerIBiProjo()
        return true
    }

    override fun annulerConfirmation(session: SessionTelecommande) {
        if (attente?.first === session) {
            attente = null
            _confirmation.value = null
        }
    }

    override fun surAuthentifiee(session: SessionTelecommande) {
        majConnectes()
        champ?.let { session.envoyerSiAuthentifiee(Protocole.focusText(it.type, it.texte, it.action)) }
        contexte?.let { ctx -> fil.post { ClavierTelecommande.reprendreSiPossible(ctx) } }
    }

    override fun surAppaire(telephone: TelephoneAppaire) {
        _telephones.value = registre.telephones()
        _appairage.value = EtatAppairage.Reussi(telephone.nom)
        fil.removeCallbacks(finAppairage)
        evaluer()
    }

    override fun journal(evenement: String) {
        Log.i(TAG, evenement)
    }

    /** La demande doit être vue même si l'utilisateur a quitté iBiProjo. */
    private fun montrerIBiProjo() {
        val ctx = contexte ?: return
        if (activiteAuPremierPlan is MainActivity) return
        try {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // Lancement depuis l'arrière-plan refusé : la demande s'affichera au retour sur l'accueil
        }
    }

    // ------------------------------------------------------------------ actions de l'interface

    private val finAppairage = Runnable {
        if (appairage.actif() == null && _appairage.value is EtatAppairage.EnCours) {
            _appairage.value = EtatAppairage.Expire
            evaluer()
        }
    }

    /** Réglages → « Appairer un téléphone » : nouveau secret, QR code et code de secours. */
    fun commencerAppairage() = surFil {
        val s = appairage.demarrer()
        val empreinte = CryptoTelecommande.empreinteCle(identite.spki)
        val url = RemoteConfig.URL_PWA + "#pair=" + registre.idTv() + "." + s.secret + "." + empreinte
        val restant = s.expire - System.currentTimeMillis()
        _appairage.value = EtatAppairage.EnCours(url, s.codeAffiche, System.currentTimeMillis() + restant)
        Log.i(TAG, "Appairage ouvert")
        fil.removeCallbacks(finAppairage)
        fil.postDelayed(finAppairage, restant + 50)
        evaluer()
    }

    /** Dialogue d'appairage fermé. */
    fun arreterAppairage() = surFil {
        appairage.arreter()
        fil.removeCallbacks(finAppairage)
        _appairage.value = EtatAppairage.Inactif
        evaluer()
    }

    fun repondreConfirmation(autorise: Boolean) = surFil {
        val a = attente ?: return@surFil
        attente = null
        _confirmation.value = null
        a.second(autorise)
    }

    fun renommer(id: String, nom: String) = surFil {
        registre.renommer(id, nom)
        _telephones.value = registre.telephones()
    }

    /** Révoque un téléphone : il est oublié, et s'il est connecté il reçoit `revoked`. */
    fun revoquer(id: String) = surFil {
        registre.revoquer(id)
        connexions.toList().filter { it.session?.appareil == id }.forEach { it.session?.revoquer() }
        _telephones.value = registre.telephones()
        Log.i(TAG, "Téléphone révoqué")
        majConnectes()
        evaluer()
    }

    private fun surFil(action: () -> Unit) {
        if (!demarre) return
        fil.post { if (pret) action() }
    }

    // ------------------------------------------------------------------ clavier iBiProjo

    private val focusPerdu = Runnable {
        if (champ == null) return@Runnable
        champ = null
        diffuser(Protocole.focusLost())
    }

    /** Un champ texte a le focus (clavier iBiProjo). */
    fun champTexte(info: InfoChamp) = surFil {
        fil.removeCallbacks(focusPerdu)
        champ = info
        diffuser(Protocole.focusText(info.type, info.texte, info.action))
    }

    /** Plus de champ texte : envoyé après un court délai (passage d'un champ à l'autre). */
    fun champPerdu() = surFil {
        fil.removeCallbacks(focusPerdu)
        fil.postDelayed(focusPerdu, 200)
    }

    /** Au moins un téléphone authentifié est connecté. */
    val telephoneConnecte: Boolean get() = _connectes.value.isNotEmpty()

    private fun diffuser(message: String) {
        connexions.forEach { it.session?.envoyerSiAuthentifiee(message) }
    }

    /** Exécute sur le fil de la télécommande (injection : appels bloquants hors du fil principal). */
    internal fun executer(action: () -> Unit) {
        if (demarre) fil.post(action)
    }

    private fun nomAppareil(context: Context): String {
        val nom = try {
            Settings.Global.getString(context.contentResolver, "device_name")
        } catch (e: Exception) { null }
        return (nom?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "iBiProjo").take(40)
    }
}
