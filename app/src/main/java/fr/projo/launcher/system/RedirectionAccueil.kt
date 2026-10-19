package fr.projo.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import fr.projo.launcher.MainActivity

/**
 * Ouverture automatique d'iBiProjo (Fire TV et appareils où l'on ne peut pas changer
 * l'écran d'accueil) : dès que l'accueil du fabricant s'affiche — au démarrage ou après un
 * appui sur Maison — on ouvre iBiProjo à sa place.
 *
 * À activer une fois dans Paramètres → Accessibilité. Le service ne lit aucun contenu
 * de l'écran : il regarde seulement QUELLE app passe au premier plan.
 */
class RedirectionAccueil : AccessibilityService() {

    private var dernier = 0L
    private var accueils: Set<String> = FireTv.ACCUEILS_AMAZON

    override fun onServiceConnected() {
        super.onServiceConnected()
        // On ajoute l'accueil par défaut de l'appareil, quel qu'il soit
        FireTv.accueilParDefaut(this)?.let { accueils = FireTv.ACCUEILS_AMAZON + it }
        Log.i(TAG, "Ouverture automatique active (accueils surveillés : $accueils)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val paquet = event.packageName?.toString() ?: return
        if (paquet !in accueils) return
        // Anti-rebond : une seule ouverture par seconde et demie
        val maintenant = SystemClock.uptimeMillis()
        if (maintenant - dernier < 1500) return
        dernier = maintenant
        try {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Impossible d'ouvrir iBiProjo", e)
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "Projo"
    }
}
