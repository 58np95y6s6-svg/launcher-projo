package fr.projo.launcher.remote

import fr.projo.launcher.boxscore.Affichage
import fr.projo.launcher.boxscore.EtatNba

/**
 * Box score NBA vu de la télécommande (docs/telecommande.md, « Box score NBA » ; implémentation
 * Android : NbaLauncher). Appels sur le fil de la télécommande, rappels sur ce même fil.
 */
interface NbaTv {
    /** `nba_etat` : un téléphone vient de s'abonner (déjà noté dans sa session) ; état actuel. */
    fun abonner(rappel: (EtatNba) -> Unit)

    /** État actuel (`nba`). */
    fun etat(rappel: (EtatNba) -> Unit)

    /** `nba_afficher` : [affichage] déjà reconnu ; [id] facultatif (ignoré s'il n'est pas dans la liste). */
    fun afficher(affichage: Affichage, id: String?, rappel: (EtatNba) -> Unit)

    /** `nba_choisir` : ignoré si [id] n'est pas dans la liste. */
    fun choisir(id: String, rappel: (EtatNba) -> Unit)
}
