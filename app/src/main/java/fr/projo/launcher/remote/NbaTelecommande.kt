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

    /** `nba_paris_ajouter` : [texte] du pari collé (déjà limité à 4000 caractères), un nouveau combiné. */
    fun ajouterParis(texte: String, rappel: (EtatNba) -> Unit)

    /** `nba_paris_effacer` : retire le combiné [groupe] (inconnu : rien), ou tous sans [groupe]. */
    fun effacerParis(groupe: String?, rappel: (EtatNba) -> Unit)

    /** `nba_paris_ouvrir` : [groupe] devient le combiné ouvert (ignoré s'il est inconnu). */
    fun ouvrirParis(groupe: String, rappel: (EtatNba) -> Unit)
}
