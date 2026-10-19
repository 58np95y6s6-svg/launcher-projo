package fr.projo.launcher.ui

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
 * Navigation verticale entre la rangée des favoris et « Mes apps », la même dans les 5 designs.
 *
 * ▼ depuis un favori : on va directement à l'élément de « Mes apps » visible le plus aligné.
 * ▲ depuis « Mes apps » : au favori visible le plus aligné.
 * ▼ puis ▲ (ou l'inverse) sans bouger entre-temps : retour sur l'élément de départ.
 *
 * On ne laisse pas faire la recherche de focus standard : elle cherche d'abord DANS la rangée
 * (une carte voisine un peu plus basse que la carte sélectionnée passe pour « en dessous » :
 * va-et-vient entre les deux premiers favoris en Verre), puis compare des distances qui
 * dépendent de la mise en page (boutons rapides ou bandeau NBA plus proches que « Mes apps »).
 * Ici le choix ne dépend que de l'alignement horizontal.
 *
 * Si le saut est impossible (rangée pas encore affichée, élément inconnu),
 * la touche n'est pas consommée : la navigation standard prend le relais.
 */
class NavRangees(etatFavoris: LazyListState, etatApps: LazyListState) {
    val favoris = NavRangee(etatFavoris)
    val apps = NavRangee(etatApps)

    /** Dernier saut réussi : rangée et élément d'arrivée, élément de départ. */
    private var dernierSaut: Triple<NavRangee, Any, Any>? = null

    /** Donne le focus à l'élément de [vers] le plus aligné avec l'élément sélectionné de [depuis]. */
    fun sauter(depuis: NavRangee, vers: NavRangee): Boolean {
        val cle = depuis.cle ?: return false
        val x = depuis.centre(cle) ?: return false
        val infos = vers.etat.layoutInfo
        fun gaucheDe(i: LazyListItemInfo) = vers.gauche + i.offset - infos.viewportStartOffset
        fun centreDe(i: LazyListItemInfo) = gaucheDe(i) + i.size / 2f
        // Partie de la rangée d'arrivée réellement à l'écran
        val ecranG = maxOf(vers.gauche, 0f)
        val ecranD = minOf(vers.gauche + infos.viewportSize.width, vers.largeurFenetre)
        val candidats = infos.visibleItemsInfo.filter { vers.cibles.containsKey(it.key) }
        // De préférence un élément entièrement visible, sinon un élément au moins à moitié visible
        val entiers = candidats.filter { gaucheDe(it) >= ecranG - 1f && gaucheDe(it) + it.size <= ecranD + 1f }
        val enPartie = candidats.filter { centreDe(it) > ecranG && centreDe(it) < ecranD }
        val choix = entiers.ifEmpty { enPartie }
        // Retour sur l'élément de départ, même s'il dépasse un peu du bord (il avait le focus)
        val s = dernierSaut
        val retour = if (s != null && s.first === depuis && s.second == cle) enPartie.firstOrNull { it.key == s.third } else null
        val cible = retour ?: choix.minByOrNull { abs(centreDe(it) - x) } ?: return false
        val fr = vers.cibles[cible.key] ?: return false
        val ok = runCatching { fr.requestFocus() }.isSuccess
        if (ok) dernierSaut = Triple(vers, cible.key, cle)
        return ok
    }
}

/** Une rangée (LazyRow) : ses éléments focalisables, l'élément sélectionné et sa position à l'écran. */
class NavRangee(val etat: LazyListState) {
    /** Demandeurs de focus des éléments composés, par clé de la LazyRow. */
    internal val cibles = HashMap<Any, FocusRequester>()

    /** Centre horizontal mesuré de chaque élément (px, repère de la fenêtre). */
    internal val centres = HashMap<Any, Float>()

    /** Clé du dernier élément de la rangée qui a eu le focus (null : aucun encore). */
    var cle: Any? = null
        internal set

    /** Bord gauche de la rangée et largeur de la fenêtre (px). */
    internal var gauche = 0f
    internal var largeurFenetre = Float.MAX_VALUE

    /**
     * Centre horizontal (px, repère de la fenêtre) d'un élément visible : celui de sa partie
     * focalisable (Console : la tuile, sans son nom affiché à côté), sinon celui de l'élément.
     */
    internal fun centre(cle: Any): Float? {
        val infos = etat.layoutInfo
        val item = infos.visibleItemsInfo.firstOrNull { it.key == cle } ?: return null
        return centres[cle] ?: (gauche + item.offset - infos.viewportStartOffset + item.size / 2f)
    }
}

/** À poser sur la LazyRow des favoris : ▼ saute dans « Mes apps ». */
fun Modifier.navFavoris(nav: NavRangees): Modifier =
    this
        .positionRangee(nav.favoris)
        .onPreviewKeyEvent { e ->
            // KeyDown seulement ; la touche n'est consommée que si le focus a bien sauté
            e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && nav.sauter(nav.favoris, nav.apps)
        }

/** À poser sur la LazyRow de « Mes apps » : ▲ remonte dans les favoris. */
fun Modifier.navApps(nav: NavRangees): Modifier =
    this
        .positionRangee(nav.apps)
        .onPreviewKeyEvent { e ->
            e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp && nav.sauter(nav.apps, nav.favoris)
        }

/** Mémorise où est la rangée dans la fenêtre (les deux rangées n'ont pas toujours la même marge). */
private fun Modifier.positionRangee(rangee: NavRangee): Modifier = onGloballyPositioned {
    rangee.gauche = it.positionInRoot().x
    rangee.largeurFenetre = it.findRootCoordinates().size.width.toFloat()
}

/**
 * Modificateur d'un élément de rangée ([cle] = sa clé dans la LazyRow) : le rend joignable
 * par un saut, mesure sa position et note qu'il est sélectionné. À poser juste avant la
 * partie focalisable, et à chaîner avec premierFocus / focusBiblio.
 */
@Composable
fun rememberCibleNav(rangee: NavRangee, cle: String): Modifier {
    val fr = remember { FocusRequester() }
    DisposableEffect(rangee, cle) {
        rangee.cibles[cle] = fr
        onDispose {
            if (rangee.cibles[cle] === fr) {
                rangee.cibles.remove(cle)
                rangee.centres.remove(cle)
            }
        }
    }
    return remember(rangee, cle, fr) {
        Modifier
            .focusRequester(fr)
            .onGloballyPositioned { rangee.centres[cle] = it.positionInRoot().x + it.size.width / 2f }
            .onFocusChanged { if (it.hasFocus) rangee.cle = cle }
    }
}
