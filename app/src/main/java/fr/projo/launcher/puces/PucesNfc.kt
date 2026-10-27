package fr.projo.launcher.puces

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppRepository
import java.security.SecureRandom

/**
 * Puces NFC (Fire TV) : on touche une puce avec le téléphone, une app s'ouvre sur la TV.
 *
 * Le Fire TV n'a pas de lecteur NFC : c'est le téléphone qui lit la puce. Elle contient un
 * lien vers le Fire TV, sur le Wi-Fi de la maison : http://<adresse>:8642/o/netflix?c=<code>.
 * Android l'ouvre aussitôt ; l'iPhone propose de l'ouvrir (ou le lance en silence avec une
 * automatisation Raccourcis). Le Fire TV ([ServeurPuces]) vérifie le code et ouvre l'app.
 *
 * Désactivé par défaut : rien n'écoute sur le réseau tant qu'on ne l'active pas dans
 * Réglages → Puces NFC. « Nouveau code » rend toutes les anciennes puces inutilisables.
 */
object PucesNfc {

    const val PORT = 8642
    private const val TAG = "Projo"
    private const val FICHIER = "puces_nfc"
    private const val CLE_ACTIF = "actif"
    private const val CLE_CODE = "code"

    /** Adresse courte → paquets possibles (le premier installé l'emporte). */
    val RACCOURCIS: Map<String, List<String>> = linkedMapOf(
        "netflix" to listOf("com.netflix.ninja", "com.netflix.mediaclient"),
        "prime" to listOf("com.amazon.firebat", "com.amazon.avod", "com.amazon.avod.thirdpartyclient"),
        "smarttube" to listOf("com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv", "com.teamsmart.videomanager.tv"),
        "youtube" to listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.google.android.youtube"),
        "canal" to listOf("com.canal.android.canal", "com.canalplus.android.tv"),
        "disney" to listOf("com.disney.disneyplus"),
        "spotify" to listOf("com.spotify.tv.android", "com.spotify.music"),
        "deezer" to listOf("deezer.android.tv", "com.deezer.android.tv", "deezer.android.app"),
        "molotov" to listOf("tv.molotov.app"),
        "francetv" to listOf("fr.francetv.pluzz"),
        "arte" to listOf("tv.arte.plus7"),
        "plex" to listOf("com.plexapp.android"),
        "kodi" to listOf("org.xbmc.kodi"),
        "twitch" to listOf("tv.twitch.android.app", "tv.twitch.android.viewer"),
    )

    /** Adresse qui ouvre l'accueil d'iBiProjo. */
    const val ACCUEIL = "accueil"

    var actif by mutableStateOf(false)
        private set

    /** Code secret des liens (vide tant que la fonction n'a jamais été activée). */
    var code by mutableStateOf("")
        private set

    /** Le Fire TV écoute bien (false : port pris, ou fonction désactivée). */
    var ecoute by mutableStateOf(false)
        private set

    /** Économiseur d'écran en cours (signalé par le service d'accessibilité). */
    @Volatile
    var enReve = false

    @Volatile
    private var codeCourant = ""
    private var lu = false
    private var serveur: ServeurPuces? = null
    private val principal = Handler(Looper.getMainLooper())

    fun charger(context: Context) {
        if (lu) return
        lu = true
        val p = context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
        actif = p.getBoolean(CLE_ACTIF, false)
        code = p.getString(CLE_CODE, "") ?: ""
        codeCourant = code
    }

    /** À l'ouverture d'iBiProjo et au démarrage du service : écoute si la fonction est activée. */
    fun demarrerSiActif(context: Context) {
        charger(context)
        if (actif) demarrer(context.applicationContext)
    }

    fun activer(context: Context, oui: Boolean) {
        charger(context)
        val p = context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit()
        if (oui && code.isEmpty()) {
            code = nouveauCodeAleatoire()
            codeCourant = code
            p.putString(CLE_CODE, code)
        }
        p.putBoolean(CLE_ACTIF, oui).apply()
        actif = oui
        if (oui) demarrer(context.applicationContext) else arreter()
    }

    /** Nouveau code : toutes les puces déjà écrites cessent de marcher. */
    fun nouveauCode(context: Context) {
        charger(context)
        code = nouveauCodeAleatoire()
        codeCourant = code
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putString(CLE_CODE, code).apply()
        Log.i(TAG, "Puces NFC : nouveau code créé")
    }

    @Synchronized
    private fun demarrer(app: Context) {
        val s = serveur ?: ServeurPuces(
            port = PORT,
            code = { codeCourant },
            resoudre = { nom -> resoudre(nom, app.packageName) { installee(app, it) } },
            libelle = { paquet -> libelle(app, paquet) },
            ouvrir = { paquet -> principal.post { lancer(app, paquet) } },
        ).also { serveur = it }
        // Ouverture du port hors du fil principal (réseau)
        Thread {
            val ok = s.demarrer()
            principal.post { ecoute = ok && serveur === s }
        }.start()
    }

    @Synchronized
    private fun arreter() {
        serveur?.arreter()
        serveur = null
        ecoute = false
    }

    // ------------------------------------------------------------------ adresses et liens

    /**
     * Adresse d'un lien → paquet à ouvrir : raccourci (« netflix »), « accueil », ou nom de
     * paquet d'une app installée. null : rien à ouvrir.
     */
    fun resoudre(nom: String, soi: String, installee: (String) -> Boolean): String? {
        val n = nom.lowercase()
        if (n == ACCUEIL) return soi
        RACCOURCIS[n]?.let { paquets -> return paquets.firstOrNull(installee) }
        // Nom de paquet : lettres, chiffres, points et soulignés seulement
        if (!Regex("[a-z0-9_]+(\\.[a-z0-9_]+)+").matches(n)) return null
        return n.takeIf(installee)
    }

    /** Adresse courte d'une app dans les liens : son raccourci s'il la désigne, sinon son paquet. */
    fun adressePour(paquet: String, soi: String, installee: (String) -> Boolean): String {
        if (paquet == soi) return ACCUEIL
        val r = RACCOURCIS.entries.firstOrNull { (_, paquets) -> paquet in paquets && paquets.firstOrNull(installee) == paquet }
        return r?.key ?: paquet.lowercase()
    }

    fun lien(ip: String, adresse: String, code: String = this.code): String = "http://$ip:$PORT/o/$adresse?c=$code"

    /**
     * Apps proposées dans Réglages : celles des raccourcis d'abord (Netflix, Prime Video…),
     * puis les autres par ordre alphabétique, puis l'accueil d'iBiProjo.
     */
    fun appsProposees(apps: List<AppInfo>, soi: String): List<AppInfo> {
        val vraies = apps.filter { !it.isSpecial && it.packageName != soi }.distinctBy { it.packageName }
        val ordre = RACCOURCIS.values.flatten()
        val connues = vraies.filter { it.packageName in ordre }.sortedBy { ordre.indexOf(it.packageName) }
        val autres = (vraies - connues.toSet()).sortedBy { it.label.lowercase() }
        return connues + autres + AppInfo(soi, "iBiProjo (accueil)", isTvApp = true)
    }

    private fun installee(context: Context, paquet: String): Boolean {
        val pm = context.packageManager
        return try {
            pm.getLeanbackLaunchIntentForPackage(paquet) != null || pm.getLaunchIntentForPackage(paquet) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun libelle(context: Context, paquet: String): String {
        if (paquet == context.packageName) return "iBiProjo"
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(paquet, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            paquet
        }
    }

    // ------------------------------------------------------------------ ouverture

    /**
     * Ouvre [paquet] sur la TV. Fire TV en veille ou économiseur d'écran : il est réveillé
     * (la TV s'allume aussi si HDMI-CEC est actif), et l'app est rouverte deux fois ensuite,
     * car Fire OS remet son accueil au réveil.
     */
    fun lancer(context: Context, paquet: String) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val endormi = pm != null && !pm.isInteractive || enReve
        reveiller(pm)
        val repo = AppRepository(context)
        val ouvrir = Runnable {
            if (!repo.launch(paquet)) Log.w(TAG, "Puces NFC : $paquet ne s'ouvre pas")
        }
        ouvrir.run()
        if (endormi) {
            principal.postDelayed(ouvrir, 2_000)
            principal.postDelayed(ouvrir, 4_500)
        }
    }

    @Suppress("DEPRECATION")
    private fun reveiller(pm: PowerManager?) {
        try {
            pm?.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "iBiProjo:puce",
            )?.acquire(5_000)
        } catch (e: Exception) {
            Log.w(TAG, "Puces NFC : réveil impossible (${e.javaClass.simpleName})")
        }
    }

    /** 20 caractères (lettres et chiffres) : environ 119 bits, impossible à deviner. */
    private fun nouveauCodeAleatoire(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val r = SecureRandom()
        return String(CharArray(20) { alphabet[r.nextInt(alphabet.length)] })
    }

    /** Tout oublier (tests). */
    internal fun oublier() {
        arreter()
        lu = false
        actif = false
        code = ""
        codeCourant = ""
        enReve = false
    }
}
