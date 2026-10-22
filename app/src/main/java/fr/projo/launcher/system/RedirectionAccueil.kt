package fr.projo.launcher.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import fr.projo.launcher.MainActivity
import fr.projo.launcher.ambiance.CadreAmbiance
import fr.projo.launcher.remote.NavigationTelecommande

/**
 * Ouverture automatique d'iBiProjo (Fire TV et appareils où l'on ne peut pas changer
 * l'écran d'accueil).
 *
 * 1. Touche Maison de la télécommande : le service la reçoit AVANT le système et la garde
 *    pour lui. Appui court → iBiProjo s'ouvre tout de suite (l'accueil Amazon n'apparaît pas).
 *    Appui long → accueil Amazon, sans redirection tant qu'on s'en sert (porte de sortie).
 *    Toutes les autres touches passent sans changement.
 * 2. Filet de sécurité : si l'accueil du fabricant s'affiche quand même (démarrage, Fire OS
 *    qui ne prête pas la touche Maison…), on ouvre iBiProjo à sa place.
 *
 * 3. Navigation depuis le téléphone (télécommande smartphone) : hérité de
 *    [NavigationTelecommande]. C'est le seul rôle qui lit le contenu de la fenêtre active,
 *    et seulement à la demande d'un téléphone appairé, pour trouver l'élément voisin à
 *    sélectionner. Un seul service à activer sur Fire TV (voir docs/telecommande.md).
 *
 * À activer une fois dans Paramètres → Accessibilité. Pour la redirection, le service
 * regarde seulement QUELLE app passe au premier plan.
 */
class RedirectionAccueil : NavigationTelecommande() {

    private val principal = Handler(Looper.getMainLooper())
    private var accueils: Set<String> = FireTv.ACCUEILS_AMAZON

    /** Ouverture d'iBiProjo demandée, pas encore affichée (0 = aucune). */
    private var lancement = 0L

    /**
     * Redirection automatique suspendue jusqu'à cet instant (appui long → accueil Amazon).
     * Prolongée à chaque changement d'écran ; finie dès qu'iBiProjo s'affiche ou en veille.
     */
    private var pauseJusqua = 0L

    /** Économiseur d'écran affiché : Maison doit d'abord le fermer, on la laisse au système. */
    private var enReve = false

    /** Appui en cours laissé au système (le relâchement doit suivre le même chemin). */
    private var appuiTransmis = false
    private var appuiLongFait = false

    private val appuiLong = Runnable {
        appuiLongFait = true
        // Porte de sortie : accueil Amazon, sans être renvoyé vers iBiProjo tout de suite
        val maintenant = SystemClock.uptimeMillis()
        pauseJusqua = maintenant + PAUSE_AMAZON
        lancement = 0
        if (!performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
            pauseJusqua = 0
            Log.w(TAG, "Accueil Amazon impossible à ouvrir")
        }
    }

    /** Mode Ambiance : néon autour de l'app au premier plan (voir ambiance/CadreAmbiance.kt). */
    private val ambiance = CadreAmbiance(this)

    private val surEcran = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ambiance.ecran(intent.action)
            when (intent.action) {
                Intent.ACTION_DREAMING_STARTED -> enReve = true
                Intent.ACTION_DREAMING_STOPPED -> enReve = false
                // Mise en veille : la pause « accueil Amazon » s'arrête
                Intent.ACTION_SCREEN_OFF -> { enReve = false; pauseJusqua = 0 }
            }
        }
    }
    private var recepteurActif = false

    override fun onCreate() {
        super.onCreate()
        ambiance.attacher()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // On ajoute l'accueil par défaut de l'appareil, quel qu'il soit
        FireTv.accueilParDefaut(this)?.let { accueils = FireTv.ACCUEILS_AMAZON + it }
        try {
            ContextCompat.registerReceiver(this, surEcran, IntentFilter().apply {
                addAction(Intent.ACTION_DREAMING_STARTED)
                addAction(Intent.ACTION_DREAMING_STOPPED)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            recepteurActif = true
        } catch (e: Exception) {
            Log.w(TAG, "Suivi de l'économiseur d'écran indisponible", e)
        }
        Log.i(TAG, "Ouverture automatique active (accueils surveillés : $accueils)")
        // Android 9 : après une mise à jour, le service peut être relié avec son ancienne
        // configuration (sans les touches) jusqu'au redémarrage. Voir FireTv.majAFinir.
        if ((serviceInfo?.flags ?: 0) and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS == 0) {
            Log.w(TAG, "Touche Maison non reçue : redémarrer l'appareil ou réactiver le service")
        }
    }

    /**
     * Touches de la télécommande, reçues avant le système. Seule Maison est gardée :
     * le système ne la voit pas, donc l'accueil Amazon ne s'affiche pas et Android
     * ne retarde pas l'ouverture d'iBiProjo (sa protection « changement d'app »).
     *
     * On ouvre au RELÂCHEMENT : c'est le seul moyen de distinguer appui court et appui
     * long, et un appui court dure ~0,1 s, invisible à côté des ~2 s d'avant.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // ☰ : appui long = néon allumé ou éteint (mode Ambiance) ; appui court : passe à l'app
        if (event != null && event.keyCode == KeyEvent.KEYCODE_MENU) return ambiance.toucheMenu(event)
        if (event == null || event.keyCode != KeyEvent.KEYCODE_HOME) return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> appuiMaison(event)
            KeyEvent.ACTION_UP -> relacheMaison(event)
            else -> !appuiTransmis
        }
    }

    /** Maison enfoncée : true = gardée pour nous, false = laissée au système. */
    private fun appuiMaison(event: KeyEvent): Boolean {
        // Répétitions (touche maintenue) : même chemin que le premier appui
        if (event.repeatCount > 0) return !appuiTransmis
        principal.removeCallbacks(appuiLong)
        // iBiProjo trop occupé (> 0,5 s) : le système a déjà repris l'appui. On lui laisse
        // aussi le relâchement pour qu'il reçoive bien la paire appui + relâchement.
        val enRetard = SystemClock.uptimeMillis() - event.eventTime > DELAI_MAX
        appuiTransmis = enReve || enRetard
        if (appuiTransmis) return false
        appuiLongFait = false
        principal.postDelayed(appuiLong, DUREE_APPUI_LONG)
        return true
    }

    /** Maison relâchée : appui court → iBiProjo (l'appui long a déjà été traité). */
    private fun relacheMaison(event: KeyEvent): Boolean {
        if (appuiTransmis) {
            appuiTransmis = false
            return false
        }
        principal.removeCallbacks(appuiLong)
        if (!appuiLongFait && !event.isCanceled) ouvrirIBiProjo()
        appuiLongFait = false
        return true
    }

    /** Filet de sécurité : l'accueil du fabricant vient de s'afficher. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val paquet = event.packageName?.toString() ?: return
        // Mode Ambiance : néon affiché seulement tant que son app est au premier plan
        ambiance.fenetre(paquet, event.className?.toString(), accueils)
        val maintenant = SystemClock.uptimeMillis()
        if (paquet == packageName) {
            // L'écran d'iBiProjo est affiché : ouverture terminée, fin de la pause éventuelle.
            // Seulement son écran principal : notre clavier (télécommande smartphone), ouvert
            // pour une recherche dans l'accueil Amazon, ne compte pas (sinon la pause était
            // annulée et l'on était renvoyé vers iBiProjo en plein téléchargement).
            if (event.className?.toString() == MainActivity::class.java.name) {
                lancement = 0
                pauseJusqua = 0
            }
            return
        }
        // Appui long sur Maison : on laisse l'univers Amazon (accueil, Appstore, recherche…)
        // tant qu'on s'en sert. Chaque changement d'écran prolonge la pause.
        if (maintenant < pauseJusqua) {
            pauseJusqua = maintenant + PAUSE_AMAZON
            return
        }
        if (paquet !in accueils) return
        // Ouverture déjà demandée il y a moins de 1,5 s et pas encore affichée : inutile de redemander
        if (lancement != 0L && maintenant - lancement < 1_500) return
        ouvrirIBiProjo()
    }

    /** Ouvre iBiProjo comme le ferait un vrai appui sur Maison (retour au début de l'accueil). */
    private fun ouvrirIBiProjo() {
        pauseJusqua = 0
        lancement = SystemClock.uptimeMillis()
        // Comme le vrai bouton Maison : ferme les fenêtres système (interdit à partir d'Android 12)
        if (Build.VERSION.SDK_INT < 31) {
            try {
                @Suppress("DEPRECATION")
                val fermer = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS).putExtra("reason", "homekey")
                sendBroadcast(fermer)
            } catch (e: Exception) {
                // sans importance
            }
        }
        try {
            startActivity(intentMaison(this))
        } catch (e: Exception) {
            lancement = 0
            Log.w(TAG, "Impossible d'ouvrir iBiProjo", e)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        principal.removeCallbacks(appuiLong)
        ambiance.liberer()
        if (recepteurActif) {
            try { unregisterReceiver(surEcran) } catch (e: Exception) { /* déjà retiré */ }
            recepteurActif = false
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Projo"

        /**
         * Le système attend notre réponse 500 ms au plus. Au-delà de ce délai (avec une marge),
         * il a déjà traité la touche lui-même.
         */
        private const val DELAI_MAX = 400L

        /** Au-delà, l'appui sur Maison est un appui long. */
        private const val DUREE_APPUI_LONG = 700L

        /** Pause après un appui long : 10 min sans changement d'écran (prolongée à chaque écran). */
        private const val PAUSE_AMAZON = 10 * 60_000L

        /**
         * Intent d'un appui sur Maison pour iBiProjo : MainActivity (singleTask) passe devant
         * sans être recréée et reçoit onNewIntent avec la catégorie HOME, qui la remet au
         * début et ferme ses panneaux. Mêmes drapeaux que ceux du système pour l'accueil.
         */
        fun intentMaison(context: Context): Intent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}
