package fr.projo.launcher.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp

/*
 * Souris et écran tactile, EN PLUS de la télécommande (version Fire TV, essayée aussi dans un
 * émulateur d'Android Studio, à la souris).
 *
 * Les composants de tv-material (Surface, ListItem…) ne réagissent qu'aux touches OK / Entrée :
 * un clic ou un toucher ne fait rien. Ce fichier ajoute le pointeur sans rien changer à la
 * télécommande : aucune touche consommée, aucun focus déplacé, aucun dessin modifié tant
 * qu'on ne clique pas.
 *
 *  - Modifier.cliquable(onClick, onLongClick) : sur tout élément activable, AVANT sa partie
 *    focalisable dans la chaîne des modificateurs (c'est à elle qu'il donne le focus).
 *    SurfaceCliquable et LigneCliquable (PointeurTv.kt) le posent sur les composants tv-material.
 *  - Modifier.cliquableSansFocus(onClick) : élément décoratif qui n'a pas de focus à la
 *    télécommande (× des Réglages, flèches de page de Chaînes, voile qui ferme un panneau) ;
 *    Modifier.retenirClics() : fond d'un panneau posé sur l'accueil, un clic sur une partie vide
 *    n'y traverse jamais vers l'accueil caché derrière.
 *  - Modifier.gestesPages(…) : glisser ou molette pour changer d'écran ou de page, là où l'on
 *    n'arrive qu'aux flèches (écran 2 de Studio, pages de Chaînes).
 *  - Modifier.moletteRangee(etat) : la molette fait défiler une rangée horizontale (à la souris,
 *    Compose ne fait pas défiler une liste qu'on glisse bouton enfoncé).
 *
 * Un pointerInput léger par élément, rien n'est recomposé quand la souris bouge.
 */

/** Distance minimale d'un glissement qui change de page (au-delà du seuil de glissement du système). */
private val GLISSEMENT_PAGE = 48.dp

/** Après un cran de molette qui a changé de page, les crans suivants sont ignorés pendant ce délai (ms). */
private const val PAUSE_MOLETTE = 350L

/** Défilement d'une rangée pour un cran de molette. */
private val CRAN_RANGEE = 80.dp

/**
 * Élément activable au pointeur, comme OK à la télécommande :
 *  - clic gauche ou toucher bref : focus à l'élément (sélection, couleur et photo de fond
 *    suivent comme à la télécommande), puis [onClick] ;
 *  - appui long (tactile, ou bouton gauche maintenu) ou clic droit : focus puis [onLongClick]
 *    (menu de l'app) s'il existe ; sinon l'appui long ne fait rien et le clic droit est laissé
 *    au système ;
 *  - un glissement n'est pas un clic : le défilement d'une rangée (LazyRow) l'emporte ;
 *  - aucun double déclenchement : un élément cliquable posé dans un autre ne réagit qu'une
 *    fois (le premier qui prend l'appui le garde), et un élément qui gère déjà le pointeur
 *    (Modifier.clickable, champ de texte) prend l'appui avant lui.
 * [enabled] = false : le pointeur ne fait rien (comme OK sur une Surface désactivée).
 */
fun Modifier.cliquable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
): Modifier = this then CliquableElement(onClick, onLongClick, enabled, donnerFocus = true)

/**
 * Zone cliquable qui ne prend pas le focus (rien à sélectionner à la télécommande) : bouton
 * décoratif (× des Réglages, flèche de page) ou voile autour d'un panneau (le toucher ferme
 * le panneau). Comme [cliquable] : un glissement n'est pas un clic, un élément cliquable posé
 * dessus prend l'appui avant elle.
 */
fun Modifier.cliquableSansFocus(onClick: () -> Unit): Modifier =
    this then CliquableElement(onClick, null, enabled = true, donnerFocus = false)

/**
 * Fond d'un panneau posé sur l'accueil (Réglages, bibliothèque…) : il garde pour lui les clics
 * qu'aucun élément du panneau n'a pris (sinon un clic sur une partie vide lancerait l'app
 * cachée derrière, ou fermerait le panneau par son voile). Les défilements passent toujours.
 */
fun Modifier.retenirClics(): Modifier = cliquableSansFocus(RIEN)

private val RIEN: () -> Unit = {}

private class CliquableElement(
    private val onClick: () -> Unit,
    private val onLongClick: (() -> Unit)?,
    private val enabled: Boolean,
    private val donnerFocus: Boolean,
) : ModifierNodeElement<CliquableNode>() {
    override fun create() = CliquableNode(onClick, onLongClick, enabled, donnerFocus)

    override fun update(node: CliquableNode) {
        node.onClick = onClick
        node.onLongClick = onLongClick
        node.enabled = enabled
        node.donnerFocus = donnerFocus
    }

    override fun InspectorInfo.inspectableProperties() {
        name = if (donnerFocus) "cliquable" else "cliquableSansFocus"
    }

    override fun equals(other: Any?): Boolean =
        other is CliquableElement && other.onClick === onClick && other.onLongClick === onLongClick &&
            other.enabled == enabled && other.donnerFocus == donnerFocus

    override fun hashCode(): Int {
        var h = onClick.hashCode()
        h = 31 * h + (onLongClick?.hashCode() ?: 0)
        h = 31 * h + enabled.hashCode()
        return 31 * h + donnerFocus.hashCode()
    }
}

/**
 * Nœud du pointeur : lit les gestes et, au clic, demande le focus pour la partie focalisable
 * qui le suit dans la chaîne (FocusRequesterModifierNode). Les actions sont relues à chaque
 * geste : une recomposition ne relance rien.
 */
private class CliquableNode(
    var onClick: () -> Unit,
    var onLongClick: (() -> Unit)?,
    var enabled: Boolean,
    var donnerFocus: Boolean,
) : DelegatingNode(), FocusRequesterModifierNode {

    init {
        delegate(SuspendingPointerInputModifierNode { gestes() })
    }

    private fun activer(action: () -> Unit) {
        if (donnerFocus) requestFocus()
        action()
    }

    private suspend fun PointerInputScope.gestes() = awaitEachGesture {
        // Appui déjà pris par un élément posé dessus (autre élément cliquable, champ de texte…) : rien
        val appui = attendreAppui()
        if (!enabled) return@awaitEachGesture
        // Clic droit de la souris : l'action « appui long », au relâchement
        if (appui.type == PointerType.Mouse && currentEvent.buttons.isSecondaryPressed) {
            val long = onLongClick ?: return@awaitEachGesture
            appui.consume()
            val haut = waitForUpOrCancellation() ?: return@awaitEachGesture
            haut.consume()
            activer(long)
            return@awaitEachGesture
        }
        appui.consume()
        val long = onLongClick
        val haut = if (long == null) {
            waitForUpOrCancellation()
        } else {
            try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
            } catch (_: PointerEventTimeoutCancellationException) {
                // Appui long : menu, et le relâchement qui suit ne clique pas
                activer(long)
                consommerJusquAuRelachement()
                return@awaitEachGesture
            }
        }
        // null : glissement (défilement), sortie de l'élément ou appui pris par un parent
        if (haut != null) {
            haut.consume()
            activer(onClick)
        }
    }
}

/**
 * Premier appui encore libre, de N'IMPORTE QUEL bouton (awaitFirstDown ne voit que le bouton
 * gauche de la souris : le clic droit lui échapperait).
 */
private suspend fun AwaitPointerEventScope.attendreAppui(): PointerInputChange {
    while (true) {
        val e = awaitPointerEvent()
        if (e.changes.isNotEmpty() && e.changes.all { it.changedToDown() }) return e.changes[0]
    }
}

private suspend fun AwaitPointerEventScope.consommerJusquAuRelachement() {
    do {
        val e = awaitPointerEvent()
        e.changes.forEach { it.consume() }
    } while (e.changes.any { it.pressed })
}

/**
 * Passage d'un écran ou d'une page à l'autre au pointeur, pour ce qu'on n'atteint qu'aux
 * flèches (rien ne change à la télécommande) :
 *  - glisser dans le sens [orientation] (tactile, ou souris bouton enfoncé) : vers le haut ou
 *    vers la gauche → [suivant], vers le bas ou vers la droite → [precedent] ; un glissement
 *    déjà pris par une liste qui défile (rangée, liste d'un module) ne compte pas ;
 *  - molette de la souris : vers le bas → [suivant], vers le haut → [precedent], un cran par
 *    page (les crans rapprochés sont ignorés) ; une molette prise par une liste ne compte pas.
 */
fun Modifier.gestesPages(orientation: Orientation, precedent: () -> Unit, suivant: () -> Unit): Modifier =
    this then GestesPagesElement(orientation, precedent, suivant)

private class GestesPagesElement(
    private val orientation: Orientation,
    private val precedent: () -> Unit,
    private val suivant: () -> Unit,
) : ModifierNodeElement<GestesPagesNode>() {
    override fun create() = GestesPagesNode(orientation, precedent, suivant)

    override fun update(node: GestesPagesNode) {
        node.orientation = orientation
        node.precedent = precedent
        node.suivant = suivant
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "gestesPages"
    }

    override fun equals(other: Any?): Boolean =
        other is GestesPagesElement && other.orientation == orientation &&
            other.precedent === precedent && other.suivant === suivant

    override fun hashCode(): Int = (orientation.hashCode() * 31 + precedent.hashCode()) * 31 + suivant.hashCode()
}

private class GestesPagesNode(
    var orientation: Orientation,
    var precedent: () -> Unit,
    var suivant: () -> Unit,
) : DelegatingNode() {

    init {
        delegate(SuspendingPointerInputModifierNode { molette() })
        delegate(SuspendingPointerInputModifierNode { glisser() })
    }

    private suspend fun PointerInputScope.molette() = awaitPointerEventScope {
        var dernierePage: Long? = null
        while (true) {
            val e = awaitPointerEvent()
            if (e.type != PointerEventType.Scroll) continue
            val c = e.changes.firstOrNull() ?: continue
            if (c.isConsumed) continue
            // Molette verticale partout (la plupart des souris n'ont qu'elle), horizontale si elle existe
            val d = c.scrollDelta
            val v = if (orientation == Orientation.Horizontal && d.x != 0f) d.x else d.y
            if (v == 0f) continue
            c.consume()
            val avant = dernierePage
            if (avant != null && c.uptimeMillis - avant < PAUSE_MOLETTE) continue
            dernierePage = c.uptimeMillis
            if (v > 0f) suivant() else precedent()
        }
    }

    private suspend fun PointerInputScope.glisser() = awaitEachGesture {
        val appui = awaitFirstDown(requireUnconsumed = false)
        // Souris : seulement bouton gauche enfoncé
        if (appui.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
        val vertical = orientation == Orientation.Vertical
        val depart = if (vertical) {
            awaitVerticalTouchSlopOrCancellation(appui.id) { c, _ -> c.consume() }
        } else {
            awaitHorizontalTouchSlopOrCancellation(appui.id) { c, _ -> c.consume() }
        } ?: return@awaitEachGesture
        var fin = depart.position
        val termine = if (vertical) {
            verticalDrag(depart.id) { fin = it.position; it.consume() }
        } else {
            horizontalDrag(depart.id) { fin = it.position; it.consume() }
        }
        if (!termine) return@awaitEachGesture
        val deplacement = if (vertical) fin.y - appui.position.y else fin.x - appui.position.x
        val seuil = GLISSEMENT_PAGE.toPx()
        when {
            deplacement <= -seuil -> suivant()
            deplacement >= seuil -> precedent()
        }
    }
}

/**
 * Molette de la souris sur une rangée horizontale ([etat] : celui de la LazyRow) : vers le bas
 * (ou vers la droite) elle avance, vers le haut elle recule. Seulement si la rangée peut encore
 * défiler dans ce sens ; sinon la molette est laissée au reste de l'écran (changement d'écran
 * de Studio). Le focus ne bouge pas.
 */
fun Modifier.moletteRangee(etat: LazyListState): Modifier = this then MoletteRangeeElement(etat)

private class MoletteRangeeElement(private val etat: LazyListState) : ModifierNodeElement<MoletteRangeeNode>() {
    override fun create() = MoletteRangeeNode(etat)

    override fun update(node: MoletteRangeeNode) {
        node.etat = etat
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "moletteRangee"
    }

    override fun equals(other: Any?): Boolean = other is MoletteRangeeElement && other.etat === etat

    override fun hashCode(): Int = etat.hashCode()
}

private class MoletteRangeeNode(var etat: LazyListState) : DelegatingNode() {

    init {
        delegate(SuspendingPointerInputModifierNode { molette() })
    }

    // Passe « Initial » : la rangée traite la molette avant sa propre gestion du défilement
    // (qui, selon la version de Compose, prend la molette verticale sans rien faire défiler).
    private suspend fun PointerInputScope.molette() = awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.type != PointerEventType.Scroll) continue
            val c = e.changes.firstOrNull() ?: continue
            if (c.isConsumed) continue
            val d = c.scrollDelta
            val v = if (d.x != 0f) d.x else d.y
            val possible = if (v > 0f) etat.canScrollForward else etat.canScrollBackward
            if (v == 0f || !possible) continue
            c.consume()
            // Défilement immédiat, sans animation ni coroutine (un cran = CRAN_RANGEE)
            etat.dispatchRawDelta(v * CRAN_RANGEE.toPx())
        }
    }
}
