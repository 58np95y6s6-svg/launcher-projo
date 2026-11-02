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
 * Étages du design « Galerie », de haut en bas : onglets et Réglages ; galerie (carte du
 * centre, flèches et tuiles) ; actions (« Ajouter à ma liste », agrandir, boutons du
 * panneau) ; dock des applis ; modules (écran 2, sous le dock).
 */
internal enum class EtageGalerie { HAUT, GALERIE, ACTIONS, DOCK, MODULES }

/**
 * Navigation de « Galerie » :
 *  - ▲ / ▼ passent à l'étage voisin (un étage vide est sauté), sur l'élément visible le plus
 *    proche horizontalement (de préférence au-dessus pour ▲, au-dessous pour ▼) ; ▼ puis ▲
 *    sans bouger ramène sur l'élément de départ ; quelques arrivées choisies ([preferee]) :
 *    ▲ depuis la carte du centre → onglet actif, ▼ → « Regarder » ;
 *  - ◀ / ▶ restent dans l'étage, dans l'ordre des éléments ([Cible.ordre], sinon de gauche à
 *    droite) ; dans la galerie, c'est l'ordre du carrousel (tuiles de gauche, flèche, carte,
 *    flèche, tuiles de droite) ; sur la carte du centre, ◀ / ▶ font glisser le carrousel ;
 *  - le dock (rangée qui défile) garde la recherche de focus normale.
 */
internal class GalerieNav {

    /** Élément joignable : son demandeur de focus, sa position (px, fenêtre) et son ordre. */
    class Cible(val fr: FocusRequester, val ordre: Float?) {
        var gauche = 0f
        var largeur = 0f
        var haut = 0f
        var hauteur = 0f
        val centre: Float get() = gauche + largeur / 2f
        val milieu: Float get() = haut + hauteur / 2f
    }

    private val etages: Map<EtageGalerie, LinkedHashMap<String, Cible>> =
        EtageGalerie.entries.associateWith { LinkedHashMap() }

    /** Élément sélectionné (étage, clé), null avant le premier focus. */
    var focus: Pair<EtageGalerie, String>? = null

    /** Largeur de la fenêtre (px). */
    var largeurEcran = Float.MAX_VALUE

    /** Arrivée imposée en quittant (étage, clé) vers un étage ; null : la plus proche. */
    var preferee: (EtageGalerie, String, EtageGalerie) -> String? = { _, _, _ -> null }

    /** ◀ / ▶ sur la carte du centre : glissement du carrousel. */
    var surCarte: (Int) -> Unit = {}

    private class Saut(val vers: EtageGalerie, val cleVers: String, val depuis: EtageGalerie, val cleDepuis: String)

    private var dernierSaut: Saut? = null

    fun inscrire(e: EtageGalerie, cle: String, cible: Cible) {
        etages.getValue(e)[cle] = cible
    }

    fun retirer(e: EtageGalerie, cle: String, cible: Cible) {
        val m = etages.getValue(e)
        if (m[cle] === cible) m.remove(cle)
    }

    fun cibleDe(e: EtageGalerie, cle: String): Cible? = etages.getValue(e)[cle]

    /** Éléments au moins à moitié visibles (horizontalement). */
    private fun visibles(e: EtageGalerie): Map<String, Cible> =
        etages.getValue(e).filterValues { it.largeur > 0f && it.centre > 0f && it.centre < largeurEcran }

    private fun voisin(e: EtageGalerie, sens: Int): EtageGalerie? {
        var i = e.ordinal + sens
        while (i in EtageGalerie.entries.indices) {
            val v = EtageGalerie.entries[i]
            if (visibles(v).isNotEmpty()) return v
            i += sens
        }
        return null
    }

    /** Distance horizontale de [x] à l'élément (0 si [x] est au-dessus ou au-dessous de lui). */
    private fun ecart(x: Float, c: Cible): Float = when {
        x < c.gauche -> c.gauche - x
        x > c.gauche + c.largeur -> x - (c.gauche + c.largeur)
        else -> 0f
    }

    /** Arrivée de ▼ / ▲ depuis l'élément sélectionné de l'étage [depuis] (mémorise le saut). */
    fun cible(depuis: EtageGalerie, sens: Int): FocusRequester? {
        val (e, cle) = focus?.takeIf { it.first == depuis } ?: return null
        val src = cibleDe(e, cle) ?: return null
        val ve = voisin(e, sens) ?: return null
        val candidats = visibles(ve)
        val s = dernierSaut
        val retour = if (s != null && s.vers == e && s.cleVers == cle && s.depuis == ve) {
            candidats[s.cleDepuis]?.let { s.cleDepuis to it }
        } else null
        val choix = retour
            ?: preferee(e, cle, ve)?.let { p -> candidats[p]?.let { p to it } }
            ?: run {
                val x = src.centre
                val y = src.milieu
                val dansLeSens = candidats.filterValues { if (sens > 0) it.milieu > y else it.milieu < y }.ifEmpty { candidats }
                dansLeSens.entries.minWithOrNull(compareBy<Map.Entry<String, Cible>>({ ecart(x, it.value) }, { abs(it.value.centre - x) }))
                    ?.toPair()
            }
            ?: return null
        dernierSaut = Saut(ve, choix.first, e, cle)
        return choix.second.fr
    }

    fun sauter(depuis: EtageGalerie, sens: Int): Boolean {
        val fr = cible(depuis, sens) ?: return false
        return runCatching { fr.requestFocus() }.isSuccess
    }

    /** ◀ / ▶ dans l'étage [e], dans l'ordre de ses éléments ; false au bout. */
    fun lateral(e: EtageGalerie, sens: Int): Boolean {
        val (fe, cle) = focus?.takeIf { it.first == e } ?: return false
        val liste = visibles(fe).entries.sortedBy { it.value.ordre ?: it.value.centre }
        val i = liste.indexOfFirst { it.key == cle }
        if (i < 0) return false
        val v = liste.getOrNull(i + sens) ?: return false
        dernierSaut = null
        return runCatching { v.value.fr.requestFocus() }.isSuccess
    }

    /** Pointeur (changement d'écran) : focus à l'élément le plus proche de l'étage [e] ; false s'il est vide. */
    fun allerVers(e: EtageGalerie, cle: String? = null): Boolean {
        val candidats = visibles(e)
        if (candidats.isEmpty()) return false
        val x = focus?.let { (fe, c) -> cibleDe(fe, c)?.centre }
        val choix = cle?.let { candidats[it] }
            ?: if (x == null) candidats.values.first() else candidats.values.minBy { ecart(x, it) * 4f + abs(it.centre - x) }
        dernierSaut = null
        return runCatching { choix.fr.requestFocus() }.isSuccess
    }
}

/**
 * Élément d'un étage ([cle] unique dans l'étage) : joignable par ▲ / ▼, position mesurée,
 * sélection notée. [fr] : demandeur imposé (premierFocus, focusBiblio, focusReglages), déjà
 * posé ici ; [ordre] : place dans l'ordre de ◀ / ▶ (sinon la position).
 */
@Composable
internal fun rememberCibleGalerie(
    nav: GalerieNav,
    etage: EtageGalerie,
    cle: String,
    fr: FocusRequester? = null,
    ordre: Float? = null,
    /** false : [fr] est posé ailleurs (zone de modules : sur son 1er élément). */
    attacher: Boolean = true,
): Modifier {
    val demandeur = fr ?: remember { FocusRequester() }
    val cible = remember(demandeur, ordre) { GalerieNav.Cible(demandeur, ordre) }
    DisposableEffect(nav, etage, cle, cible) {
        nav.inscrire(etage, cle, cible)
        onDispose { nav.retirer(etage, cle, cible) }
    }
    return remember(nav, etage, cle, cible, attacher) {
        Modifier
            .onGloballyPositioned {
                val p = it.positionInRoot()
                cible.gauche = p.x
                cible.haut = p.y
                cible.largeur = it.size.width.toFloat()
                cible.hauteur = it.size.height.toFloat()
                nav.largeurEcran = it.findRootCoordinates().size.width.toFloat()
            }
            .then(if (attacher) Modifier.focusRequester(demandeur) else Modifier)
            .onFocusChanged { if (it.hasFocus) nav.focus = etage to cle }
    }
}

/**
 * Conteneur d'un étage : ▲ / ▼ sautent à l'étage voisin (touche consommée même s'il n'y a rien :
 * le focus ne part jamais au hasard) ; ◀ / ▶ restent dans l'étage ([lateral] : dans l'ordre des
 * éléments, sinon recherche de focus normale, sans sortir). [surFocus] : l'étage reçoit le focus.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.etageGalerie(
    nav: GalerieNav,
    etage: EtageGalerie,
    lateral: Boolean,
    surFocus: (EtageGalerie) -> Unit,
): Modifier =
    this
        .onFocusChanged { if (it.hasFocus) surFocus(etage) }
        .onPreviewKeyEvent { e ->
            when (e.key) {
                Key.DirectionDown, Key.DirectionUp -> {
                    if (e.type == KeyEventType.KeyDown) nav.sauter(etage, if (e.key == Key.DirectionDown) 1 else -1)
                    true
                }
                Key.DirectionLeft, Key.DirectionRight -> {
                    if (!lateral) return@onPreviewKeyEvent false
                    val sens = if (e.key == Key.DirectionRight) 1 else -1
                    if (e.type == KeyEventType.KeyDown) {
                        if (etage == EtageGalerie.GALERIE && nav.focus == EtageGalerie.GALERIE to CLE_CARTE) nav.surCarte(sens)
                        else nav.lateral(etage, sens)
                    }
                    true
                }
                else -> false
            }
        }
        .focusProperties {
            exit = { d -> if (d == FocusDirection.Left || d == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default }
        }
        .focusGroup()

/** Clé de la carte du centre dans l'étage GALERIE. */
internal const val CLE_CARTE = "carte"

/**
 * Étage des modules (écran 2) : les modules gèrent ▲ ▼ ◀ ▶ à l'intérieur ; quand le focus en
 * sort par le haut, il va à l'élément le plus proche du dock ; jamais par le bas ni les côtés.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.etageModulesGalerie(nav: GalerieNav, surFocus: (EtageGalerie) -> Unit): Modifier =
    this
        .onFocusChanged { if (it.hasFocus) surFocus(EtageGalerie.MODULES) }
        .focusProperties {
            exit = { d ->
                when (d) {
                    FocusDirection.Up -> nav.cible(EtageGalerie.MODULES, -1) ?: FocusRequester.Cancel
                    FocusDirection.Down, FocusDirection.Left, FocusDirection.Right -> FocusRequester.Cancel
                    else -> FocusRequester.Default
                }
            }
        }
        .focusGroup()
