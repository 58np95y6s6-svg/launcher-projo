package fr.projo.launcher.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import fr.projo.launcher.boxscore.Affichage
import fr.projo.launcher.boxscore.BoxScoreNba
import fr.projo.launcher.boxscore.EtatNba

/**
 * Box score NBA pour la télécommande (voir NbaTv) : tout se passe sur le fil principal
 * (BoxScoreNba), et chaque rappel repart sur le fil de la télécommande par [retour].
 */
class NbaLauncher(
    context: Context,
    /** Exécute sur le fil de la télécommande. */
    private val retour: (() -> Unit) -> Unit,
    /** Au moins un téléphone authentifié est abonné à `nba` (lu sur le fil de la télécommande). */
    private val abonnes: () -> Boolean,
) : NbaTv {

    private val app: Context = context.applicationContext
    private val principal = Handler(Looper.getMainLooper())

    /** Dernier état de l'abonnement transmis (fil de la télécommande). */
    private var abonnement = false

    init {
        principal.post { BoxScoreNba.demarrer(app) }
    }

    override fun abonner(rappel: (EtatNba) -> Unit) {
        val actif = abonnes()
        abonnement = actif
        principal.post {
            BoxScoreNba.abonnement(actif)
            repondre(rappel)
        }
    }

    override fun etat(rappel: (EtatNba) -> Unit) {
        principal.post { repondre(rappel) }
    }

    override fun afficher(affichage: Affichage, id: String?, rappel: (EtatNba) -> Unit) {
        principal.post {
            try {
                BoxScoreNba.afficher(affichage, id)
            } catch (e: Exception) {
                Log.w(TAG, "nba : affichage impossible")
            }
            repondre(rappel)
        }
    }

    override fun choisir(id: String, rappel: (EtatNba) -> Unit) {
        principal.post {
            try {
                BoxScoreNba.choisir(id)
            } catch (e: Exception) {
                Log.w(TAG, "nba : choix impossible")
            }
            repondre(rappel)
        }
    }

    /** Une connexion s'est fermée : le suivi du tableau du jour s'arrête s'il n'y a plus d'abonné. Fil de la télécommande. */
    fun majAbonnement() {
        val actif = abonnes()
        if (actif == abonnement) return
        abonnement = actif
        principal.post { BoxScoreNba.abonnement(actif) }
    }

    /** Fil principal. */
    private fun repondre(rappel: (EtatNba) -> Unit) {
        val e = try {
            BoxScoreNba.etat()
        } catch (e: Exception) {
            Log.w(TAG, "nba : état illisible")
            null
        }
        if (e != null) retour { rappel(e) }
    }

    private companion object {
        const val TAG = "Projo.Nba"
    }
}
