package fr.projo.launcher.remote

import fr.projo.launcher.liens.LienTv
import fr.projo.launcher.liens.ResultatLien

/** État « Envoyer sur la TV » envoyé dans `liens` (docs/telecommande.md). */
data class EtatLiens(
    /** Un VPN est actif sur la TV. */
    val vpn: Boolean,
    /** Réglage « Seulement avec le VPN ». */
    val seulementVpn: Boolean,
    /** Appli qui ouvrira les pages web (« VYPN », « Silk »…), "" si aucune. */
    val appliPages: String,
    /** Appli VPN que la TV sait ouvrir, null si aucune. */
    val appliVpn: String? = null,
    /** Adresse complète à mettre dans le raccourci iOS ; null : réglage coupé ou pas d'adresse locale. */
    val raccourci: String? = null,
)

/**
 * « Envoyer sur la TV » vu de la télécommande (implémentation Android : LiensLauncher). Appels sur
 * le fil de la télécommande, rappels sur ce même fil.
 */
interface LiensTv {
    /** État actuel (`liens`). */
    fun etat(rappel: (EtatLiens) -> Unit)

    /**
     * Ouvre [lien] (déjà vérifié), envoyé par [expediteur] (« le téléphone de Marie »).
     * [rappel] peut être appelé deux fois : `attente_vpn` puis le résultat.
     */
    fun ouvrir(lien: LienTv, sansVpn: Boolean, vpnDabord: Boolean, expediteur: String, rappel: (ResultatLien) -> Unit)
}
