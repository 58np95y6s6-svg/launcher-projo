package fr.projo.launcher.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import fr.projo.launcher.liens.EnvoiTv
import fr.projo.launcher.liens.LienTv
import fr.projo.launcher.liens.ResultatLien
import fr.projo.launcher.liens.SourceLien

/**
 * « Envoyer sur la TV » pour la télécommande (voir LiensTv) : tout se passe sur le fil principal
 * (EnvoiTv : état du VPN, réglages, ouverture, bandeau), et chaque rappel repart sur le fil de la
 * télécommande par [retour].
 */
class LiensLauncher(
    context: Context,
    /** Exécute sur le fil de la télécommande. */
    private val retour: (() -> Unit) -> Unit,
) : LiensTv {

    private val app: Context = context.applicationContext
    private val principal = Handler(Looper.getMainLooper())

    override fun etat(rappel: (EtatLiens) -> Unit) {
        principal.post {
            val e = try {
                EnvoiTv.etat(app)
            } catch (e: Exception) {
                Log.w(TAG, "État des liens illisible")
                null
            }
            if (e != null) retour { rappel(e) }
        }
    }

    override fun ouvrir(lien: LienTv, sansVpn: Boolean, vpnDabord: Boolean, expediteur: String, rappel: (ResultatLien) -> Unit) {
        principal.post {
            try {
                EnvoiTv.envoyer(app, lien, SourceLien.TELEPHONE, expediteur, sansVpn, vpnDabord) { r -> retour { rappel(r) } }
            } catch (e: Exception) {
                Log.w(TAG, "Lien impossible à ouvrir")
                retour { rappel(ResultatLien.Refuse(ResultatLien.AUCUNE_APPLI)) }
            }
        }
    }

    private companion object {
        const val TAG = "Projo.Liens"
    }
}
