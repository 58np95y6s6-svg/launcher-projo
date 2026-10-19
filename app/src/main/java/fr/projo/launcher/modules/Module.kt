package fr.projo.launcher.modules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import fr.projo.launcher.ui.LocalScale
import fr.projo.launcher.ui.NbaStyle
import fr.projo.launcher.ui.u

/**
 * Tailles d'affichage d'un module (dimensions INTÉRIEURES de la zone, en unités .u de
 * l'écran de référence 960 × 540, avant l'échelle du design) :
 *  - PETIT : tuile d'au moins 140 × 100 (moitié de l'emplacement NBA, deux modules côte à côte) ;
 *  - MOYEN : au moins 300 × 118 (l'emplacement du bandeau NBA : environ 324 × 127) ;
 *  - GRAND : MOYEN en largeur et au moins 240 de haut (zone haute, si un design la propose).
 * Le rendu reçoit la taille choisie dans [ModuleContexte.taille] et doit remplir sa place
 * sans jamais déborder : tout ce qui dépasse serait coupé.
 */
enum class TailleModule(val largeurMin: Int, val hauteurMin: Int) {
    PETIT(140, 100),
    MOYEN(300, 118),
    GRAND(300, 240),
}

/**
 * Un module de la zone de modules (Météo, Sport…). Pour en ajouter un : voir le guide en tête
 * de RegistreModules.kt.
 */
abstract class Module(
    /** Identifiant stable (enregistré dans les préférences) : ne jamais le changer. */
    val id: String,
    /** Nom affiché dans le dialogue « Modules ». */
    val nom: String,
    /** Une phrase de description (dialogue « Modules »). */
    val description: String,
    /** Icône vectorielle 24 × 24 (dialogue « Modules »). */
    val icone: ImageVector,
    /** Tailles que le rendu sait afficher. MOYEN est obligatoire ; PETIT fortement conseillé. */
    val tailles: Set<TailleModule> = setOf(TailleModule.PETIT, TailleModule.MOYEN),
) {
    /**
     * Rendu du module à la taille [ModuleContexte.taille] (un `when` sur la taille), avec les
     * couleurs du design ([ModuleContexte.style]). Le module occupe tout [modifier] ; le cadre
     * (fond, bordure, coins arrondis) est déjà dessiné par la zone.
     *
     * Navigation : poser [ModuleContexte.entree] sur le premier élément focalisable (ou utiliser
     * [CarteModule], qui le fait). ◀ et Retour sont gérés par la zone ; un module qui intercepte
     * ◀ lui-même appelle [ModuleContexte.sortir] pour quitter.
     */
    @Composable
    abstract fun Contenu(ctx: ModuleContexte, modifier: Modifier)

    /** true si le module a un écran de réglages ([Reglages]). */
    open val aDesReglages: Boolean get() = false

    /** Résumé des réglages, affiché dans le dialogue « Modules » (ex. « Lyon »). */
    open fun resumeReglages(reglages: ReglagesModule): String? = null

    /**
     * Écran de réglages du module : un dialogue (ui.ListeDialog conseillé). [fermer] revient au
     * dialogue « Modules ». Les choix s'enregistrent avec [ReglagesModule.mettre].
     */
    @Composable
    open fun Reglages(reglages: ReglagesModule, donnees: DonneesModules, fermer: () -> Unit) {}

    /** Taille la plus proche de [voulue] parmi celles que le module sait afficher. */
    fun tailleProche(voulue: TailleModule): TailleModule {
        if (voulue in tailles) return voulue
        val ordre = TailleModule.entries
        // D'abord plus petit (rien ne déborde), sinon plus grand
        return ordre.subList(0, voulue.ordinal).lastOrNull { it in tailles }
            ?: ordre.firstOrNull { it in tailles } ?: TailleModule.MOYEN
    }
}

/**
 * Réglages d'un module, enregistrés dans LauncherPrefs (clé « idModule.clé »).
 * Valeurs texte ; listes séparées par des virgules.
 */
class ReglagesModule(
    val idModule: String,
    private val valeurs: Map<String, String>,
    private val ecrire: (cle: String, valeur: String?) -> Unit,
) {
    fun texte(cle: String): String? = valeurs["$idModule.$cle"]

    fun liste(cle: String, defaut: List<String>): List<String> =
        texte(cle)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: defaut

    /** valeur = null : retour à la valeur par défaut. */
    fun mettre(cle: String, valeur: String?) = ecrire(cle, valeur)

    fun mettreListe(cle: String, valeurs: List<String>) = ecrire(cle, valeurs.joinToString(","))
}

/** Tout ce que la zone donne au rendu d'un module. */
class ModuleContexte(
    /** Taille choisie par la zone (déjà ajustée aux tailles prises en charge par le module). */
    val taille: TailleModule,
    /** Couleurs et échelle du design (l'échelle est déjà appliquée aux unités .u / .us). */
    val style: NbaStyle,
    /** true quand le focus est dans ce module. */
    val aLeFocus: Boolean,
    val reglages: ReglagesModule,
    /** Données asynchrones partagées (voir [rememberDonnees]). */
    val donnees: DonneesModules,
    /** À poser sur le premier élément focalisable du module (la zone s'en sert pour y entrer). */
    val entree: FocusRequester,
    /** Quitte le module vers la gauche : module voisin, ou sortie de la zone (retour aux favoris). */
    val sortir: () -> Unit,
    /** Place intérieure réelle, en unités .u (avant l'échelle du design). */
    val largeur: Float,
    val hauteur: Float,
)

/** Unités d'interface pour une mesure non entière (ex. ctx.hauteur.u). */
val Float.u: Dp
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current).dp

/** Style du design, pour les sous-composants d'un module (fourni par la zone). */
val LocalStyleModule = compositionLocalOf { NbaStyle() }

/** Couleur des filets de séparation (la bordure du design peut être transparente). */
val NbaStyle.filet: Color get() = texteDiscret.copy(alpha = texteDiscret.alpha * 0.3f)

/** Texte sans l'interligne imposé par le thème TV : hauteur de ligne explicite. */
fun ligne(hauteur: TextUnit): TextStyle = TextStyle(lineHeight = hauteur)

/**
 * Carte focalisable pour un module sans élément focalisable à lui (Météo…) : la zone reste
 * ainsi atteignable à la télécommande. Focus : léger fond et liseré aux couleurs du design,
 * sans agrandissement (rien ne déborde de la zone).
 */
@Composable
fun CarteModule(
    ctx: ModuleContexte,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    contenu: @Composable BoxScope.() -> Unit,
) {
    val style = ctx.style
    val forme = RoundedCornerShape(10.u)
    Surface(
        onClick = onClick,
        modifier = modifier.focusRequester(ctx.entree),
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = style.texte,
            focusedContainerColor = style.ongletActif,
            focusedContentColor = style.texte,
            pressedContainerColor = style.ongletActif,
            pressedContentColor = style.texte,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.u, style.focusFond.copy(alpha = 0.85f)), shape = forme),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize(), content = contenu)
    }
}
