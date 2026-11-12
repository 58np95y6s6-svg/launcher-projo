package fr.projo.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import fr.projo.launcher.MainActivity

/**
 * Au démarrage de l'appareil : ouvre iBiTV.
 * - Fire TV : toujours (jusqu'à Fire OS 7 ; ensuite Android l'interdit depuis l'arrière-plan et
 *   c'est le service d'accessibilité qui s'en charge).
 * - Autres boîtiers : si iBiTV est l'écran d'accueil par défaut. Certains (X96 mini et son
 *   MediaLauncher) relancent leur accueil au démarrage malgré ce choix : iBiTV est rouvert une
 *   seconde fois, [RELANCE_MS] plus tard, pour passer devant.
 */
class DemarrageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (FireTv.estFireTv(context)) {
            ouvrir(context)
            return
        }
        if (!FireTv.iBiTVParDefaut(context)) return
        ouvrir(context)
        val attente = try {
            goAsync()
        } catch (e: Exception) {
            null
        } ?: return
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            ouvrir(app)
            attente.finish()
        }, RELANCE_MS)
    }

    private fun ouvrir(context: Context) {
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.i("Projo", "Ouverture au démarrage refusée par Android : ${e.message}")
        }
    }

    companion object {
        /** Seconde ouverture, après l'accueil que le boîtier relance lui-même. */
        const val RELANCE_MS = 4_000L
    }
}
