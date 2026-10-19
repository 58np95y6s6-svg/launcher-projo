package fr.projo.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.projo.launcher.MainActivity

/**
 * Au démarrage de l'appareil, sur Fire TV : ouvre iBiProjo. (Jusqu'à Fire OS 7 ; ensuite
 * Android l'interdit depuis l'arrière-plan et c'est le service d'accessibilité qui s'en charge.)
 */
class DemarrageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!FireTv.estFireTv(context)) return
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.i("Projo", "Ouverture au démarrage refusée par Android : ${e.message}")
        }
    }
}
