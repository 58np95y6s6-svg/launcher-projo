package fr.projo.launcher.modules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import fr.projo.launcher.data.MODULES_PAR_DEFAUT
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import fr.projo.launcher.ui.NbaStyle
import fr.projo.launcher.ui.graphitePanneau
import fr.projo.launcher.ui.graphiteUnPixel
import fr.projo.launcher.ui.u

/**
 * Ce que la zone de modules doit afficher : modules choisis, leurs réglages et le cache de
 * données. Fourni par MainActivity avec [LocalHoteModules] ; sans hôte (anciens appels,
 * tests de navigation), NbaPanel affiche le module Sport avec les données NBA qu'il reçoit.
 */
class HoteModules(
    /** Identifiants des modules choisis, dans l'ordre (1 ou 2). */
    val choix: List<String> = MODULES_PAR_DEFAUT,
    /** Réglages de tous les modules : « idModule.clé » → valeur. */
    val reglages: Map<String, String> = emptyMap(),
    val donnees: DonneesModules = DonneesModules(),
    val ecrireReglage: (idModule: String, cle: String, valeur: String?) -> Unit = { _, _, _ -> },
) {
    /** Modules à afficher : 1 ou 2, connus du registre (Sport si rien de valable). */
    val modules: List<Module>
        get() = choix.mapNotNull { RegistreModules.trouver(it) }.distinct().take(2)
            .ifEmpty { listOf(RegistreModules.parDefaut) }

    fun reglagesDe(idModule: String) =
        ReglagesModule(idModule, reglages) { cle, valeur -> ecrireReglage(idModule, cle, valeur) }
}

val LocalHoteModules = compositionLocalOf<HoteModules?> { null }

// Mesures de la zone, en unités .u avant l'échelle du design
/** Hauteur extérieure de l'emplacement NBA (3 matchs) : hauteur de la zone partagée par 2 modules. */
const val ZONE_HAUTEUR = 143
/** Marge intérieure du cadre. */
private const val ZONE_MARGE = 8
/** Graphite : hauteur du bandeau NBA de la maquette (en-tête 46 + 3 lignes de 49) et marge latérale. */
const val ZONE_HAUTEUR_GRAPHITE = 193
private const val ZONE_MARGE_GRAPHITE = 16
/** Écart entre deux modules (filet de séparation compris). */
private const val ZONE_ECART = 13

/** Disposition choisie : tailles des modules, côte à côte ou empilés. */
private class Disposition(val tailles: List<TailleModule>, val cote: Boolean, val largeurs: List<Float>, val hauteurs: List<Float>)

/**
 * Choisit la disposition selon la place intérieure (l × h, unités .u) :
 * 1 module : GRAND, MOYEN ou PETIT selon la place ;
 * 2 modules : deux MOYEN côte à côte ou empilés, sinon MOYEN + PETIT, sinon deux PETIT
 * (côte à côte dans l'emplacement NBA) ; à défaut, seul le premier module est affiché.
 */
private fun disposer(n: Int, l: Float, h: Float): Disposition {
    fun tient(t: TailleModule, larg: Float, haut: Float) = larg >= t.largeurMin && haut >= t.hauteurMin
    if (n == 1) {
        val t = TailleModule.entries.reversed().firstOrNull { tient(it, l, h) } ?: TailleModule.PETIT
        return Disposition(listOf(t), true, listOf(l), listOf(h))
    }
    val M = TailleModule.MOYEN
    val P = TailleModule.PETIT
    val demiL = (l - ZONE_ECART) / 2
    val demiH = (h - ZONE_ECART) / 2
    return when {
        tient(M, demiL, h) -> Disposition(listOf(M, M), true, listOf(demiL, demiL), listOf(h, h))
        tient(M, l, demiH) -> Disposition(listOf(M, M), false, listOf(l, l), listOf(demiH, demiH))
        tient(M, l - ZONE_ECART - P.largeurMin, h) && tient(P, P.largeurMin.toFloat(), h) -> {
            val lp = maxOf(P.largeurMin.toFloat(), l - ZONE_ECART - 324f)
            Disposition(listOf(M, P), true, listOf(l - ZONE_ECART - lp, lp), listOf(h, h))
        }
        tient(M, l, h - ZONE_ECART - P.hauteurMin) -> {
            val hp = maxOf(P.hauteurMin.toFloat(), h - ZONE_ECART - 127f)
            Disposition(listOf(M, P), false, listOf(l, l), listOf(h - ZONE_ECART - hp, hp))
        }
        tient(P, demiL, h) -> Disposition(listOf(P, P), true, listOf(demiL, demiL), listOf(h, h))
        tient(P, l, demiH) -> Disposition(listOf(P, P), false, listOf(l, l), listOf(demiH, demiH))
        else -> Disposition(listOf(M), true, listOf(l), listOf(h))
    }
}

/**
 * Zone de modules : un cadre aux couleurs du design ([style]), à l'emplacement de l'ancien
 * bandeau NBA, qui affiche 1 ou 2 modules.
 *  - 1 module : largeur du bandeau NBA, hauteur selon le module (au plus [ZONE_HAUTEUR], ou
 *    style.zoneHauteur) ;
 *  - 2 modules : zone de style.zoneLargeur × style.zoneHauteur (par défaut l'emplacement NBA),
 *    partagée selon sa forme (voir [disposer]), mesurée avec BoxWithConstraints.
 * Navigation : ◀ passe au module de gauche ou quitte la zone ([onExit]) ; ▶ / ▲ ▼ passent
 * d'un module à l'autre (recherche de focus normale) ; Retour quitte la zone.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ZoneModules(
    style: NbaStyle,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    repli: HoteModules = HoteModules(),
    /** Point d'entrée du focus : posé sur le 1er élément du 1er module. */
    entree: FocusRequester? = null,
    /** ◀ depuis le 1er module (Graphite : retour à Réglages) ; par défaut [onExit]. */
    onGauche: (() -> Unit)? = null,
) {
    val hote = LocalHoteModules.current ?: repli
    val modules = hote.modules
    val forme = RoundedCornerShape(style.rayon.u)
    val entrees = remember(entree) { listOf(entree ?: FocusRequester(), FocusRequester()) }
    val sortieGauche = onGauche ?: onExit
    // Graphite : panneau à filets de la maquette, marges latérales de 16
    val margeL = if (style.graphite) ZONE_MARGE_GRAPHITE else ZONE_MARGE
    val un = graphiteUnPixel()
    BoxWithConstraints(modifier) {
        val unite = 1.u.value
        val maxL = if (constraints.hasBoundedWidth) maxWidth.value / unite else Float.MAX_VALUE
        val maxH = if (constraints.hasBoundedHeight) maxHeight.value / unite else Float.MAX_VALUE
        val deux = modules.size > 1
        val largeur = minOf(if (deux) (style.zoneLargeur ?: style.largeur).toFloat() else style.largeur.toFloat(), maxL)
        val hauteurParDefaut = if (style.graphite) ZONE_HAUTEUR_GRAPHITE else ZONE_HAUTEUR
        val hauteur = minOf((style.zoneHauteur ?: hauteurParDefaut).toFloat(), maxH)
        val interieurL = largeur - 2 * margeL
        val interieurH = hauteur - 2 * ZONE_MARGE
        val dispo = disposer(modules.size, interieurL, interieurH)
        val affiches = modules.take(dispo.tailles.size)

        val cadre = Modifier
            .testTag("nba")
            // Retour : on quitte la zone
            .onPreviewKeyEvent {
                if (it.key == Key.Back && it.type == KeyEventType.KeyUp) { onExit(); true }
                else it.key == Key.Back
            }
            // Titre du module focalisé (FocusModules) effacé quand le focus quitte la zone
            .onFocusChanged { if (!it.hasFocus) FocusModules.titre = null }
            .width(largeur.u)
            .then(
                if (affiches.size > 1 || style.graphite) Modifier.height(hauteur.u + if (style.graphite) un * 2 else 0.u)
                else Modifier
            )
            .then(
                if (style.graphite) Modifier.graphitePanneau(style.rayon.u).filetGraphite()
                else Modifier.clip(forme).background(style.fond).border(BorderStroke(1.u, style.bordure), forme)
            )
            .padding(horizontal = margeL.u, vertical = ZONE_MARGE.u)

        CompositionLocalProvider(LocalStyleModule provides style) {
            val emplacement: @Composable (Int, Modifier) -> Unit = { i, mod ->
                val m = affiches[i]
                key(m.id) {
                    EmplacementModule(
                        m, i, dispo.tailles[i], dispo.largeurs[i], dispo.hauteurs[i],
                        versGauche = i == 1 && dispo.cote, hote, style, entrees, sortieGauche, mod,
                    )
                }
            }
            if (affiches.size == 1) {
                Column(cadre) {
                    emplacement(0, Modifier.fillMaxWidth().heightIn(max = interieurH.u))
                }
            } else if (dispo.cote) {
                Row(cadre, horizontalArrangement = Arrangement.SpaceBetween) {
                    emplacement(0, Modifier.width(dispo.largeurs[0].u).fillMaxHeight())
                    Filet(vertical = true, style)
                    emplacement(1, Modifier.width(dispo.largeurs[1].u).fillMaxHeight())
                }
            } else {
                Column(cadre, verticalArrangement = Arrangement.SpaceBetween) {
                    emplacement(0, Modifier.fillMaxWidth().height(dispo.hauteurs[0].u))
                    Filet(vertical = false, style)
                    emplacement(1, Modifier.fillMaxWidth().height(dispo.hauteurs[1].u))
                }
            }
        }
    }
}

/** Un module dans la zone : contexte, focus et sortie par ◀. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EmplacementModule(
    m: Module,
    i: Int,
    taille: TailleModule,
    largeur: Float,
    hauteur: Float,
    versGauche: Boolean,
    hote: HoteModules,
    style: NbaStyle,
    entrees: List<FocusRequester>,
    onExit: () -> Unit,
    mod: Modifier,
) {
    var focus by remember { mutableStateOf(false) }
    val gestionFocus = LocalFocusManager.current
    // ◀ depuis le 2e module côte à côte : vers le 1er ; sinon sortie de la zone
    val sortir: () -> Unit = if (versGauche) {
        { runCatching { entrees[0].requestFocus() } }
    } else onExit
    val ctx = ModuleContexte(
        taille = m.tailleProche(taille),
        style = style,
        aLeFocus = focus,
        reglages = hote.reglagesDe(m.id),
        donnees = hote.donnees,
        entree = entrees[i],
        sortir = sortir,
        largeur = largeur,
        hauteur = hauteur,
    )
    Box(
        mod
            .testTag("module:" + m.id)
            .onFocusChanged {
                focus = it.hasFocus
                if (it.hasFocus) FocusModules.titre = m.titre(ctx.reglages)
            }
            // ◀ que le module n'a pas utilisé : élément à gauche DANS le module s'il y en a un,
            // sinon sortie (module voisin ou favoris). La recherche de focus ne peut pas sortir
            // du module par la gauche (sinon elle tomberait n'importe où dans l'accueil).
            .onKeyEvent {
                if (it.key != Key.DirectionLeft) return@onKeyEvent false
                if (it.type == KeyEventType.KeyDown && !gestionFocus.moveFocus(FocusDirection.Left)) sortir()
                true
            }
            .focusProperties {
                exit = { dir -> if (dir == FocusDirection.Left) FocusRequester.Cancel else FocusRequester.Default }
            }
            .focusGroup()
    ) {
        m.Contenu(ctx, Modifier.fillMaxWidth())
    }
}

/** Graphite : filet violet → cyan sur le bord haut du panneau (de 40 à largeur − 40). */
@Composable
private fun Modifier.filetGraphite(): Modifier {
    val marge = 40.u
    return drawWithCache {
        val m = marge.toPx()
        val filet = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.35f to Color(0xFFC4B5FD).copy(alpha = 0.9f),
            0.7f to Color(0xFF67E8F9).copy(alpha = 0.8f),
            1f to Color.Transparent,
            startX = m, endX = size.width - m,
        )
        onDrawWithContent {
            drawContent()
            drawLine(filet, Offset(m, 0.5f), Offset(size.width - m, 0.5f), strokeWidth = 1f)
        }
    }
}

/** Filet fin entre deux modules. */
@Composable
private fun Filet(vertical: Boolean, style: NbaStyle) {
    if (vertical) {
        Box(Modifier.fillMaxHeight().padding(vertical = 6.u).width(1.u).background(style.filet))
    } else {
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.u).height(1.u).background(style.filet))
    }
}
