package fr.projo.launcher.ui

import fr.projo.launcher.ambiance.ModeAmbiance
import fr.projo.launcher.data.AppInfo

/** Ce que font les lignes du menu d'une app (appui long ou touche Menu sur sa tuile). */
class ActionsMenuApp(
    val basculerFavori: () -> Unit,
    val basculerAccueil: () -> Unit,
    val deplacer: (Int) -> Unit,
    val changerLogo: () -> Unit,
    val masquer: () -> Unit,
    val infos: () -> Unit,
    /** Tuile « Source HDMI » : choix de l'entrée à ouvrir. */
    val choisirEntree: () -> Unit,
    /** Ouvre l'app avec le néon du mode Ambiance. */
    val ambiance: () -> Unit,
)

/**
 * Lignes du menu d'une app, pour tous les designs (le menu est le même partout).
 * [index] : place de l'app dans sa rangée (-1 si absente), [taille] : taille de la rangée.
 */
fun actionsMenuApp(
    app: AppInfo,
    favori: Boolean,
    accueil: Boolean,
    index: Int,
    taille: Int,
    a: ActionsMenuApp,
): List<MenuAction> = buildList {
    add(MenuAction(if (favori) "Retirer des favoris" else "Ajouter aux favoris", a.basculerFavori))
    if (!favori) add(MenuAction(if (accueil) "Retirer de l'accueil" else "Ajouter à l'accueil", a.basculerAccueil))
    if (index > 0) add(MenuAction("Déplacer à gauche") { a.deplacer(-1) })
    if (index >= 0 && index < taille - 1) add(MenuAction("Déplacer à droite") { a.deplacer(1) })
    if (!app.isSpecial) {
        // Mode Ambiance : néon réactif à la musique autour de l'app, tant qu'elle est au premier plan
        add(MenuAction(ModeAmbiance.LIBELLE_MENU, a.ambiance))
        add(MenuAction("Changer le logo…", a.changerLogo))
        add(MenuAction("Masquer partout", a.masquer))
        add(MenuAction("Infos de l'app", a.infos))
    } else {
        add(MenuAction("Choisir l'entrée à ouvrir…", a.choisirEntree))
    }
    add(MenuAction("Annuler") {})
}
