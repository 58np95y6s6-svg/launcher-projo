package fr.projo.launcher.modules.minuteur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Récepteur du minuteur (déclaré dans le manifeste, non exporté) : échéance programmée dans
 * AlarmManager, et boutons des notifications (Arrêter, +5 min, Annuler). Fonctionne même
 * quand le launcher est fermé : l'état est relu dans les préférences.
 */
class MinuteurReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            when (intent?.action) {
                Minuteur.ACTION_ECHEANCE -> Minuteur.echeance(context)
                Minuteur.ACTION_ARRETER -> Minuteur.arreter(context)
                Minuteur.ACTION_REPETER -> Minuteur.repeter(context, 5)
                Minuteur.ACTION_ANNULER -> Minuteur.annuler(context)
            }
        } catch (e: Exception) {
            Log.w("ProjoMinuteur", "Minuteur : action ${intent?.action} impossible", e)
        }
    }
}
