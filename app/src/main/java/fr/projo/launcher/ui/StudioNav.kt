package fr.projo.launcher.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.abs

/**
 * Bandes horizontales du design « Studio », de haut en bas. Écran 1 : en-tête (onglets et
 * pastilles), suggestions, barre des apps ; écran 2 : barre des apps (remontée en haut),
 * entrées et raccourcis, modules.
 */
internal enum class StudioBande { HAUT, SUGGESTIONS, DOCK, RACCOURCIS, MODULES }

/**
 * Navigation verticale déterministe de « Studio » : ▼ / ▲ passent à la bande voisine (une
 * bande vide est sautée), sur l'élément VISIBLE le plus aligné horizontalement avec l'élément
 * sélectionné ; ▼ puis ▲ (ou l'inverse) sans bouger entre-temps ramène sur l'élément de départ.
 * ◀ / ▶ restent dans la bande (recherche de focus normale, sans en sortir).
 * Même principe que NavRangees, étendu à cinq bandes.
 */
internal class StudioNav {

    /** Élément joignable : son demandeur de focus et sa position horizontale (px, fenêtre). */
    class Cible(val fr: FocusRequester) {
        var gauche = 0f
        var largeur = 0f
        val centre: Float get() = gauche + largeur / 2f
    }

    private val bandes: Map<StudioBande, LinkedHashMap<String, Cible>> =
        StudioBande.entries.associateWith { LinkedHashMap() }

    /** Élément sélectionné (bande, clé), null avant le premier focus. */
    var focus: Pair<StudioBande, String>? = null

    /** Largeur de la fenêtre (px). */
    var largeurEcran = Float.MAX_VALUE

    private class Saut(val vers: StudioBande, val cleVers: String, val depuis: StudioBande, val cleDepuis: String)

    private var dernierSaut: Saut? = null

    fun inscrire(b: StudioBande, cle: String, cible: Cible) {
        bandes.getValue(b)[cle] = cible
    }

    fun retirer(b: StudioBande, cle: String, cible: Cible) {
        val m = bandes.getValue(b)
        if (m[cle] === cible) m.remove(cle)
    }

    fun cibleDe(b: StudioBande, cle: String): Cible? = bandes.getValue(b)[cle]

    /** Éléments au moins à moitié visibles à l'écran (horizontalement). */
    private fun visibles(b: StudioBande): Map<String, Cible> =
        bandes.getValue(b).filterValues { it.largeur > 0f && it.centre > 0f && it.centre < largeurEcran }

    /** Bande voisine non vide dans le sens [sens] (+1 : ▼, −1 : ▲). */
    private fun voisine(b: StudioBande, sens: Int): StudioBande? {
        var i = b.ordinal + sens
        while (i in StudioBande.entries.indices) {
            val nb = StudioBande.entries[i]
            if (visibles(nb).isNotEmpty()) return nb
            i += sens
        }
        return null
    }

    /**
     * Élément d'arrivée de ▼ / ▲ depuis l'élément sélectionné de la bande [depuis] (null : rien
     * dans ce sens). Mémorise le saut pour le retour.
     */
    fun cible(depuis: StudioBande, sens: Int): FocusRequester? {
        val (b, cle) = focus?.takeIf { it.first == depuis } ?: return null
        val x = cibleDe(b, cle)?.centre ?: return null
        val nb = voisine(b, sens) ?: return null
        val candidats = visibles(nb)
        val s = dernierSaut
        val retour = if (s != null && s.vers == b && s.cleVers == cle && s.depuis == nb) {
            candidats[s.cleDepuis]?.let { s.cleDepuis to it }
        } else null
        // De préférence un élément entièrement visible
        val entiers = candidats.filterValues { it.gauche >= -1f && it.gauche + it.largeur <= largeurEcran + 1f }
        val choix = retour ?: entiers.ifEmpty { candidats }.minByOrNull { abs(it.value.centre - x) }?.toPair() ?: return null
        dernierSaut = Saut(nb, choix.first, b, cle)
        return choix.second.fr
    }

    /** ▼ / ▲ : donne le focus à [cible] ; false si rien dans ce sens. */
    fun sauter(depuis: StudioBande, sens: Int): Boolean {
        val fr = cible(depuis, sens) ?: return false
        return runCatching { fr.requestFocus() }.isSuccess
    }
}

/**
 * Élément d'une bande ([cle] unique dans la bande) : joignable par ▼ / ▲, position mesurée,
 * sélection notée. À poser juste avant la partie focalisable. [fr] : demandeur imposé
 * (premierFocus, focusBiblio, focusReglages), déjà posé ici : ne pas le reposer.
 */
@Composable
internal fun rememberCibleStudio(
    nav: StudioNav,
    bande: StudioBande,
    cle: String,
    fr: FocusRequester? = null,
    /** false : [fr] est posé ailleurs (zone de modules : sur son 1er élément). */
    attacher: Boolean = true,
): Modifier {
    val demandeur = fr ?: remember { FocusRequester() }
    val cible = remember(demandeur) { StudioNav.Cible(demandeur) }
    DisposableEffect(nav, bande, cle, cible) {
        nav.inscrire(bande, cle, cible)
        onDispose { nav.retirer(bande, cle, cible) }
    }
    return remember(nav, bande, cle, cible, attacher) {
        Modifier
            .onGloballyPositioned {
                cible.gauche = it.positionInRoot().x
                cible.largeur = it.size.width.toFloat()
                nav.largeurEcran = it.findRootCoordinates().size.width.toFloat()
            }
            .then(if (attacher) Modifier.focusRequester(demandeur) else Modifier)
            .onFocusChanged { if (it.hasFocus) nav.focus = bande to cle }
    }
}

/**
 * Conteneur d'une bande : ▼ / ▲ sautent vers la bande voisine (touche consommée même s'il
 * n'y a rien : le focus ne part jamais au hasard), ◀ / ▶ ne sortent pas de la bande.
 * [surFocus] : la bande vient de recevoir le focus (changement d'écran).
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.bandeStudio(nav: StudioNav, bande: StudioBande, surFocus: (StudioBande) -> Unit): Modifier =
    this
        .onFocusChanged { if (it.hasFocus) surFocus(bande) }
        .onPreviewKeyEvent { e ->
            val sens = when (e.key) {
                Key.DirectionDown -> 1
                Key.DirectionUp -> -1
                else -> return@onPreviewKeyEvent false
            }
            if (e.type == KeyEventType.KeyDown) nav.sauter(bande, sens)
            true
        }
        .focusProperties {
            exit = { d -> if (d == FocusDirection.Left || d == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default }
        }
        .focusGroup()

/**
 * Conteneur de la bande des modules : les modules gèrent eux-mêmes ▲ ▼ ◀ ▶ à l'intérieur ;
 * quand le focus en sort par le haut, il va à l'élément le plus aligné des raccourcis, et il
 * ne sort jamais par le bas ni par les côtés (◀ est géré par la zone de modules).
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.bandeModulesStudio(nav: StudioNav, surFocus: (StudioBande) -> Unit): Modifier =
    this
        .onFocusChanged { if (it.hasFocus) surFocus(StudioBande.MODULES) }
        .focusProperties {
            exit = { d ->
                when (d) {
                    FocusDirection.Up -> nav.cible(StudioBande.MODULES, -1) ?: FocusRequester.Cancel
                    FocusDirection.Down, FocusDirection.Left, FocusDirection.Right -> FocusRequester.Cancel
                    // Demandes directes (sortie de la zone, Maison…) : laissées passer
                    else -> FocusRequester.Default
                }
            }
        }
        .focusGroup()
