package fr.projo.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import fr.projo.launcher.data.NbaData
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.modules.ZoneModules
import fr.projo.launcher.modules.sport.SportReseau

/**
 * Apparence de la zone de modules (ex-bandeau NBA) : chaque design fournit la sienne.
 * Les modules reçoivent ce style (ModuleContexte.style) pour s'accorder au design.
 */
data class NbaStyle(
    /** Largeur en unités d'écran (.u) avant mise à l'échelle. */
    val largeur: Int = 340,
    val rayon: Int = 14,
    val fond: Color = Color.White.copy(alpha = 0.07f),
    val bordure: Color = Color.White.copy(alpha = 0.11f),
    val texte: Color = Color.White,
    val texteDiscret: Color = Color.White.copy(alpha = 0.62f),
    val ongletActif: Color = Color.White.copy(alpha = 0.16f),
    val focusFond: Color = Color.White,
    val focusTexte: Color = Color(0xFF0B0D12),
    val pastilleFond: Color = Color.White.copy(alpha = 0.92f),
    /** Agrandit tout le bandeau (textes, lignes, largeur) : 1.25 = 25 % plus grand. */
    val echelle: Float = 1f,
    /**
     * Place que le design accorde à la zone de modules (unités .u avant l'échelle), quand
     * il peut offrir plus que l'emplacement NBA : largeur pour 2 modules (null = [largeur])
     * et hauteur (null = hauteur du bandeau NBA, 143). Ex. zoneHauteur = 300 : modules en
     * taille GRAND, ou deux modules MOYEN empilés.
     */
    val zoneLargeur: Int? = null,
    val zoneHauteur: Int? = null,
)

/**
 * Zone de modules à l'emplacement de l'ancien bandeau NBA (signature d'origine conservée :
 * les écrans d'accueil l'appellent tels quels). Affiche les modules choisis dans
 * Réglages → Modules (fournis par LocalHoteModules) ; sans choix : le module Sport (NBA).
 *
 * [data] / [erreur] : matchs NBA utilisés seulement quand aucun hôte de modules n'est fourni
 * (tests, aperçus) ; sinon le module Sport charge lui-même ses données.
 */
@Composable
fun NbaPanel(
    data: NbaData?,
    erreur: Boolean,
    repo: NbaRepository,
    modifier: Modifier = Modifier,
    /** Sortie rapide de la zone (flèche gauche, ou Retour) : retour aux favoris. */
    onExit: () -> Unit = {},
    style: NbaStyle = NbaStyle(),
    /** Point d'entrée du focus (1er élément du 1er module), pour y venir depuis un autre élément. */
    entree: FocusRequester? = null,
    /** ◀ depuis le 1er module (ex. Studio : vers le module de gauche) ; par défaut, comme [onExit]. */
    onGauche: (() -> Unit)? = null,
) {
    // Les logos d'équipes partagent le cache du dépôt NBA fourni
    SportReseau.adopter(repo)
    val repli = remember(data, erreur) { SportReseau.hoteNba(data, erreur) }
    CompositionLocalProvider(LocalScale provides LocalScale.current * style.echelle) {
        ZoneModules(
            style = style, onExit = onExit, modifier = modifier, repli = repli,
            entree = entree, onGauche = onGauche,
        )
    }
}

/** « Q3 4:12 » → « QT3 4:12 », « Halftime » → « Mi-temps ». */
internal fun tempsDeJeu(s: String): String = when {
    s.contains("half", ignoreCase = true) -> "Mi-temps"
    s.startsWith("End of", ignoreCase = true) -> "Fin " + s.substringAfter("of ").replace("1st", "QT1")
        .replace("2nd", "QT2").replace("3rd", "QT3").replace("4th", "QT4")
    else -> s.replace(Regex("^(\\d)(st|nd|rd|th) Quarter"), "QT$1").replace(Regex("^Q(\\d)"), "QT$1").replace("OT", "Prol.")
}
