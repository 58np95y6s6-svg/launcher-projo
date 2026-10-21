package fr.projo.launcher.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppCategories
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppVisual
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.Nouveautes
import fr.projo.launcher.data.Rubrique
import fr.projo.launcher.modules.FocusModules
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ------------------------------------------------------------------ palette « Playlist »

/** Fond général : noir pur, les panneaux sont séparés par des marges noires. */
internal val PlNoir = Color(0xFF000000)
/** Panneaux (bibliothèque, contenu, « en ce moment »). */
internal val PlPanneau = Color(0xFF121212)
/** Accent : vert « lecture ». */
internal val PlVert = Color(0xFF1ED760)
/** Textes secondaires. */
internal val PlGris = Color(0xFFB3B3B3)
/** Boutons ronds et champ de recherche. */
internal val PlBouton = Color(0xFF1F1F1F)
/** Champ de recherche sélectionné, bulles d'aide. */
internal val PlSurvol = Color(0xFF2A2A2A)
/** Fond des barres (heure de la journée, volume). */
private val PlRail = Color(0xFF4D4D4D)

/** Police : celle d'Android, en graisses pleines et serrée pour les titres. */
private val PlPolice = FontFamily.SansSerif

// Mesures (unités .u de l'écran de référence 960 × 540)
private const val PL_MARGE = 8
/** Barre du haut (boutons ronds, recherche). */
private const val PL_HAUT = 56
/** Barre « lecteur » du bas. */
private const val PL_BAS = 72
/** Largeur de la barre latérale « Bibliothèque » et du panneau « En ce moment ». */
private const val PL_LATERALE = 208
private const val PL_DROITE = 296
/** Tuile de raccourci (hauteur = côté de son image). */
private const val PL_TUILE = 44
/** Carte carrée : largeur totale et côté de l'image. */
private const val PL_CARTE = 116
private const val PL_IMAGE = 100

/**
 * Zone de modules du panneau de droite : toute la largeur (296.u) à l'échelle 0,92, soit
 * 322 unités de module (305 utiles : taille MOYEN ou GRAND), et toute la hauteur (deux
 * modules MOYEN empilés).
 */
private val PlStyleModules = NbaStyle(
    largeur = 322,
    rayon = 8,
    fond = Color.Transparent,
    bordure = Color.Transparent,
    texte = Color.White,
    texteDiscret = PlGris,
    ongletActif = Color.White.copy(alpha = 0.1f),
    focusFond = Color.White,
    focusTexte = Color.Black,
    pastilleFond = Color.White.copy(alpha = 0.92f),
    echelle = 0.92f,
    zoneLargeur = 322,
    zoneHauteur = 400,
)

/** Libellé court d'une rubrique (pastilles de filtre, lignes de la bibliothèque). */
private fun Rubrique.court(): String = when (this) {
    Rubrique.STREAMING -> "Vidéo"
    Rubrique.TV -> "TV"
    Rubrique.MUSIQUE -> "Musique"
    Rubrique.JEUX -> "Jeux"
    Rubrique.STORE -> "Stores"
    Rubrique.OUTILS -> "Outils"
    Rubrique.SYSTEME -> "Système"
    Rubrique.AUTRES -> "Autres"
}

/** Sous-titre d'une carte (une ligne courte sous le nom : 14 caractères tiennent). */
private fun Rubrique.sousTitre(): String = when (this) {
    Rubrique.STREAMING -> "Streaming"
    Rubrique.TV -> "TV en direct"
    Rubrique.MUSIQUE -> "Musique"
    Rubrique.JEUX -> "Jeux"
    Rubrique.STORE -> "Stores"
    Rubrique.OUTILS -> "Outils"
    Rubrique.SYSTEME -> "Système"
    Rubrique.AUTRES -> "Application"
}

/**
 * Couleur du dégradé du panneau central : la couleur de l'app, assombrie si elle est trop
 * claire (le texte blanc doit rester lisible), un peu fondue dans le gris des panneaux.
 */
private fun plTeinte(c: Color): Color {
    var x = c.copy(alpha = 1f)
    val l = x.luminance()
    if (l > 0.16f) x = lerp(x, Color.Black, ((l - 0.16f) / l).coerceIn(0f, 0.7f))
    return lerp(x, PlPanneau, 0.18f)
}

// ------------------------------------------------------------------ icônes

/** Icônes propres au design (24 × 24). */
private object PlIcones {
    val Recherche = plein(
        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 " +
            "5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 " +
            "5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"
    )
    /** Trois tranches de livres, la dernière penchée. */
    val Bibliotheque = plein("M3,21V3h2.6v18H3z M9,21V3h2.6v18H9z M14.3,3.6l2.4,-0.9 5.9,17.7 -2.4,0.9z")
    /** Punaise : app épinglée (favori). */
    val Epingle = plein(
        "M16,9V4h1c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3s0.45,1 1,1h1v5c0,1.66 -1.34,3 -3,3v2h5.97v7l1,1 1,-1v-7H19v-2C17.34,12 16,10.66 16,9z"
    )

    private fun plein(chemin: String): ImageVector =
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(chemin).toNodes(),
            fill = SolidColor(Color.White),
        ).build()
}

// ------------------------------------------------------------------ navigation déterministe

/** Sens d'un déplacement. */
internal enum class PlSens {
    HAUT, BAS, GAUCHE, DROITE;

    val inverse: PlSens
        get() = when (this) {
            HAUT -> BAS
            BAS -> HAUT
            GAUCHE -> DROITE
            DROITE -> GAUCHE
        }
}

/**
 * Navigation à la télécommande de « Playlist ». Cinq zones : barre du haut, barre latérale
 * (pastilles puis lignes), contenu central (tuiles de raccourcis, « Vos apps », « Toutes les
 * apps »), panneau « En ce moment » (modules) à droite. Les flèches qui changent de zone sont
 * toutes traitées ici, jamais par la recherche de focus standard (qui partait en biais) :
 *  - ▲ / ▼ dans le contenu central : ligne voisine, élément le plus aligné horizontalement
 *    (entre les deux rangées : NavRangees, commun à tous les designs) ;
 *  - ◀ au bord gauche du contenu : la ligne de la bibliothèque la plus alignée verticalement ;
 *    ▶ depuis la bibliothèque : la ligne du contenu la plus alignée, son premier élément ;
 *  - ▶ au bord droit du contenu : la zone de modules ; ◀ (ou Retour) en sort vers « Vos apps » ;
 *  - ▲ en haut de chaque zone : le bouton de la barre du haut le plus aligné ; ▼ depuis la
 *    barre du haut : la zone située dessous, élément le plus aligné ;
 *  - un saut puis le saut inverse, sans bouger entre-temps : retour sur l'élément de départ.
 */
internal class PlaylistNav {
    /** Demandeurs de focus des éléments composés (barre du haut, pastilles, lignes, tuiles). */
    val cibles = HashMap<String, FocusRequester>()
    /** Position (px, repère de la fenêtre) des éléments mesurés. */
    val bornes = HashMap<String, Rect>()

    /** Clés, dans l'ordre, de la barre du haut, des pastilles, des lignes et des tuiles. */
    var haut: List<String> = emptyList()
    var filtres: List<String> = emptyList()
    var lignes: List<String> = emptyList()
    var tuiles: List<String> = emptyList()

    /** Élément sélectionné (hors rangées, suivies par NavRangees). */
    var focusCle: String? = null

    /** Bulle d'aide sous le bouton rond sélectionné (son nom). */
    var bulle by mutableStateOf<PlBulle?>(null)

    /** Bords des panneaux (px, repère de la fenêtre) et centre vertical des deux rangées. */
    var droiteLaterale = 0f
    var gaucheZone = Float.MAX_VALUE
    var xZone = 0f
    var centreHaut = 0f
    var centreBas = Float.MAX_VALUE
    var yFavoris = 0f
    var yApps = 0f

    /** Liste de la bibliothèque, et position de son haut dans la fenêtre. */
    var etatLignes: LazyListState? = null
    var origineLignes = 0f

    /** Point d'entrée de la zone de modules (1er élément du 1er module). */
    val entreeZone = FocusRequester()

    /** Retour au début de l'accueil (1re carte de « Vos apps », contenu remis en haut). */
    var debut: () -> Unit = {}

    /** Dernier saut : élément d'arrivée, élément de départ, sens. */
    private var dernierSaut: Triple<String, String, PlSens>? = null

    init {
        cibles[CLE_ZONE] = entreeZone
    }

    private class Cible(val cle: String, val x: Float, val y: Float)

    private fun demandeur(ctx: HomeContext, cle: String): FocusRequester? =
        cibles[cle] ?: ctx.nav.favoris.cibles[cle] ?: ctx.nav.apps.cibles[cle]

    private fun aller(ctx: HomeContext, cle: String?, depuis: String?, sens: PlSens): Boolean {
        val fr = cle?.let { demandeur(ctx, it) } ?: return false
        val ok = runCatching { fr.requestFocus() }.isSuccess
        if (ok && depuis != null) dernierSaut = Triple(cle, depuis, sens)
        return ok
    }

    /** Retour sur l'élément de départ si l'on vient d'arriver ici par le saut inverse. */
    private fun retour(ctx: HomeContext, depuis: String, sens: PlSens): Boolean {
        val s = dernierSaut ?: return false
        if (s.first != depuis || s.third != sens.inverse) return false
        return aller(ctx, s.second, depuis, sens)
    }

    // --- éléments candidats

    /** Éléments visibles d'une rangée (entièrement visibles de préférence), comme NavRangees. */
    private fun ciblesRangee(r: NavRangee, etat: LazyListState, y: Float): List<Cible> {
        val infos = etat.layoutInfo
        fun gauche(i: LazyListItemInfo) = r.gauche + i.offset - infos.viewportStartOffset
        val ecranG = maxOf(r.gauche, 0f)
        val ecranD = minOf(r.gauche + infos.viewportSize.width, r.largeurFenetre)
        val candidats = infos.visibleItemsInfo.filter { r.cibles.containsKey(it.key) }
        val entiers = candidats.filter { gauche(it) >= ecranG - 1f && gauche(it) + it.size <= ecranD + 1f }
        val enPartie = candidats.filter { (gauche(it) + it.size / 2f).let { m -> m > ecranG && m < ecranD } }
        return entiers.ifEmpty { enPartie }.map {
            Cible(it.key as String, r.centres[it.key] ?: (gauche(it) + it.size / 2f), y)
        }
    }

    /** Tuiles, « Vos apps » et « Toutes les apps ». */
    private fun ciblesCentre(ctx: HomeContext): List<Cible> =
        tuiles.mapNotNull { k -> bornes[k]?.let { Cible(k, it.center.x, it.center.y) } } +
            ciblesRangee(ctx.nav.favoris, ctx.rangeeFavoris, yFavoris) +
            ciblesRangee(ctx.nav.apps, ctx.rangeeApps, yApps)

    /** Lignes visibles de la bibliothèque (entièrement visibles de préférence). */
    private fun ciblesLignes(): List<Cible> {
        val etat = etatLignes ?: return emptyList()
        val infos = etat.layoutInfo
        val candidats = infos.visibleItemsInfo.filter { (it.key as? String)?.let { k -> cibles.containsKey(k) } == true }
        val entiers = candidats.filter {
            it.offset >= infos.viewportStartOffset - 1 && it.offset + it.size <= infos.viewportEndOffset + 1
        }
        return entiers.ifEmpty { candidats }.map {
            Cible(it.key as String, 0f, origineLignes + it.offset - infos.viewportStartOffset + it.size / 2f)
        }
    }

    private fun centreDe(cle: String): Offset? = bornes[cle]?.center

    // --- sauts entre zones

    /** Ligne voisine du contenu central dans le sens voulu, élément le plus aligné en x. */
    private fun versLigneCentrale(ctx: HomeContext, depuis: String, x: Float, y: Float, sens: PlSens): Boolean {
        if (retour(ctx, depuis, sens)) return true
        val tol = 4f
        val c = ciblesCentre(ctx).filter { if (sens == PlSens.HAUT) it.y < y - tol else it.y > y + tol }
        val ligne = (if (sens == PlSens.HAUT) c.maxOfOrNull { it.y } else c.minOfOrNull { it.y }) ?: return false
        val cible = c.filter { abs(it.y - ligne) <= tol }.minByOrNull { abs(it.x - x) } ?: return false
        return aller(ctx, cible.cle, depuis, sens)
    }

    /** ▼ depuis la barre du haut vers le contenu : ligne visible la plus haute, élément le plus aligné. */
    private fun versCentreParLeHaut(ctx: HomeContext, depuis: String, x: Float): Boolean {
        if (retour(ctx, depuis, PlSens.BAS)) return true
        val tout = ciblesCentre(ctx)
        val visibles = tout.filter { it.y in centreHaut..centreBas }.ifEmpty { tout }
        val ligne = visibles.minOfOrNull { it.y } ?: return false
        val cible = visibles.filter { abs(it.y - ligne) <= 4f }.minByOrNull { abs(it.x - x) } ?: return false
        return aller(ctx, cible.cle, depuis, PlSens.BAS)
    }

    /** ▶ depuis la bibliothèque : ligne du contenu la plus alignée verticalement, son premier élément. */
    private fun versCentreParLaGauche(ctx: HomeContext, depuis: String, y: Float): Boolean {
        if (retour(ctx, depuis, PlSens.DROITE)) return true
        val tout = ciblesCentre(ctx)
        val visibles = tout.filter { it.y in centreHaut..centreBas }.ifEmpty { tout }
        val ligne = visibles.minByOrNull { abs(it.y - y) }?.y ?: return false
        val cible = visibles.filter { abs(it.y - ligne) <= 4f }.minByOrNull { it.x } ?: return false
        return aller(ctx, cible.cle, depuis, PlSens.DROITE)
    }

    /** ◀ depuis le bord gauche du contenu : ligne de la bibliothèque la plus alignée verticalement. */
    private fun versLaterale(ctx: HomeContext, depuis: String, y: Float): Boolean {
        if (retour(ctx, depuis, PlSens.GAUCHE)) return true
        val cible = ciblesLignes().minByOrNull { abs(it.y - y) }?.cle ?: filtres.firstOrNull()
        return aller(ctx, cible, depuis, PlSens.GAUCHE)
    }

    /** ▲ vers la barre du haut : le bouton le plus aligné en x. */
    private fun versHaut(ctx: HomeContext, depuis: String, x: Float): Boolean {
        if (retour(ctx, depuis, PlSens.HAUT)) return true
        return aller(ctx, boutonLePlusProche(x), depuis, PlSens.HAUT)
    }

    private fun boutonLePlusProche(x: Float): String? =
        haut.filter { bornes.containsKey(it) }.minByOrNull { abs(bornes.getValue(it).center.x - x) }

    /** ▶ au bord droit du contenu (ou ▼ sous les boutons de droite) : la zone de modules. */
    private fun versZone(depuis: String?): Boolean {
        val ok = runCatching { entreeZone.requestFocus() }.isSuccess
        if (ok && depuis != null) dernierSaut = Triple(CLE_ZONE, depuis, PlSens.DROITE)
        return ok
    }

    /**
     * Sortie de la zone de modules par la recherche de focus : ▲ vers la barre du haut, ▶ et ▼
     * on reste. Les autres sens passent (◀ est géré par la zone ; Enter : requestFocus() d'un
     * élément hors de la zone, ex. retour à la 1re carte, qui ne doit pas être bloqué).
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun sortieZone(sens: FocusDirection): FocusRequester {
        if (sens == FocusDirection.Right || sens == FocusDirection.Down) return FocusRequester.Cancel
        if (sens != FocusDirection.Up) return FocusRequester.Default
        val cle = boutonLePlusProche(xZone) ?: return FocusRequester.Cancel
        val fr = cibles[cle] ?: return FocusRequester.Cancel
        dernierSaut = Triple(cle, CLE_ZONE, PlSens.HAUT)
        return fr
    }

    /** Retour : début de l'accueil (sauf si l'on y est déjà : la touche suit son cours). */
    private fun retourDebut(e: KeyEvent, dejaAuDebut: Boolean): Boolean {
        if (dejaAuDebut) return false
        if (e.type == KeyEventType.KeyUp) debut()
        return true
    }

    // --- touches, par zone

    fun toucheHaut(e: KeyEvent, ctx: HomeContext): Boolean {
        if (e.key == Key.Back) return retourDebut(e, false)
        if (e.type != KeyEventType.KeyDown) return false
        val cle = focusCle ?: return false
        val i = haut.indexOf(cle)
        if (i < 0) return false
        when (e.key) {
            Key.DirectionLeft -> aller(ctx, haut.getOrNull(i - 1), null, PlSens.GAUCHE)
            Key.DirectionRight -> aller(ctx, haut.getOrNull(i + 1), null, PlSens.DROITE)
            Key.DirectionUp -> Unit
            Key.DirectionDown -> {
                val x = centreDe(cle)?.x ?: return true
                when {
                    retour(ctx, cle, PlSens.BAS) -> Unit
                    x >= gaucheZone -> versZone(null)
                    x <= droiteLaterale -> {
                        val pastille = filtres.filter { bornes.containsKey(it) }
                            .minByOrNull { abs(bornes.getValue(it).center.x - x) }
                        aller(ctx, pastille ?: ciblesLignes().firstOrNull()?.cle, cle, PlSens.BAS)
                    }
                    else -> versCentreParLeHaut(ctx, cle, x)
                }
            }
            else -> return false
        }
        return true
    }

    fun toucheFiltres(e: KeyEvent, ctx: HomeContext): Boolean {
        if (e.key == Key.Back) return retourDebut(e, false)
        if (e.type != KeyEventType.KeyDown) return false
        val cle = focusCle?.takeIf { it in filtres } ?: return false
        val c = centreDe(cle) ?: return false
        return when (e.key) {
            Key.DirectionLeft -> cle == filtres.first()
            Key.DirectionRight -> if (cle == filtres.last()) {
                versCentreParLaGauche(ctx, cle, c.y)
                true
            } else {
                false
            }
            Key.DirectionUp -> {
                versHaut(ctx, cle, c.x)
                true
            }
            Key.DirectionDown -> {
                if (!retour(ctx, cle, PlSens.BAS)) aller(ctx, ciblesLignes().minByOrNull { it.y }?.cle, cle, PlSens.BAS)
                true
            }
            else -> false
        }
    }

    fun toucheLignes(e: KeyEvent, ctx: HomeContext, actif: String?): Boolean {
        if (e.key == Key.Back) return retourDebut(e, false)
        if (e.type != KeyEventType.KeyDown) return false
        val cle = focusCle?.takeIf { it in lignes } ?: return false
        return when (e.key) {
            Key.DirectionUp -> if (cle == lignes.first()) {
                if (!retour(ctx, cle, PlSens.HAUT)) aller(ctx, actif ?: filtres.firstOrNull(), cle, PlSens.HAUT)
                true
            } else {
                false
            }
            Key.DirectionDown -> cle == lignes.last()
            Key.DirectionLeft -> true
            Key.DirectionRight -> {
                centreDe(cle)?.let { versCentreParLaGauche(ctx, cle, it.y) }
                true
            }
            else -> false
        }
    }

    fun toucheTuiles(e: KeyEvent, ctx: HomeContext): Boolean {
        if (e.key == Key.Back) return retourDebut(e, false)
        if (e.type != KeyEventType.KeyDown) return false
        val cle = focusCle?.takeIf { it in tuiles } ?: return false
        val i = tuiles.indexOf(cle)
        val c = centreDe(cle) ?: return false
        when (e.key) {
            Key.DirectionLeft -> if (i % 2 == 0) versLaterale(ctx, cle, c.y) else aller(ctx, tuiles[i - 1], null, PlSens.GAUCHE)
            Key.DirectionRight -> if (i % 2 == 0 && i + 1 < tuiles.size) aller(ctx, tuiles[i + 1], null, PlSens.DROITE) else versZone(cle)
            Key.DirectionUp -> if (!versLigneCentrale(ctx, cle, c.x, c.y, PlSens.HAUT)) versHaut(ctx, cle, c.x)
            Key.DirectionDown -> versLigneCentrale(ctx, cle, c.x, c.y, PlSens.BAS)
            else -> return false
        }
        return true
    }

    /** Rangée « Vos apps » ([favoris]) ou « Toutes les apps ». */
    fun toucheRangee(e: KeyEvent, ctx: HomeContext, favoris: Boolean): Boolean {
        val r = if (favoris) ctx.nav.favoris else ctx.nav.apps
        val cle = r.cle as? String ?: return false
        val premier = if (favoris) {
            ctx.ui.favorites.firstOrNull()?.let { "fav:" + it.packageName } ?: CLE_AIDE
        } else {
            ctx.ui.homeApps.firstOrNull()?.let { "app:" + it.packageName } ?: CLE_BIBLIO
        }
        val dernier = if (favoris) ctx.ui.favorites.lastOrNull()?.let { "fav:" + it.packageName } ?: CLE_AIDE else CLE_BIBLIO
        if (e.key == Key.Back) return retourDebut(e, favoris && cle == premier)
        if (e.type != KeyEventType.KeyDown) return false
        val y = if (favoris) yFavoris else yApps
        return when (e.key) {
            Key.DirectionLeft -> if (cle == premier) {
                versLaterale(ctx, cle, y)
                true
            } else {
                false
            }
            Key.DirectionRight -> if (cle == dernier) {
                versZone(cle)
                true
            } else {
                false
            }
            Key.DirectionDown -> {
                if (favoris) ctx.nav.sauter(ctx.nav.favoris, ctx.nav.apps)
                true
            }
            Key.DirectionUp -> {
                if (!favoris) {
                    ctx.nav.sauter(ctx.nav.apps, ctx.nav.favoris)
                } else {
                    val x = r.centre(cle) ?: return true
                    if (!versLigneCentrale(ctx, cle, x, y, PlSens.HAUT)) versHaut(ctx, cle, x)
                }
                true
            }
            else -> false
        }
    }

    /** Demandeur de focus d'un élément, enregistré tant qu'il est composé. */
    @Composable
    fun demandeur(cle: String): FocusRequester {
        val fr = remember { FocusRequester() }
        DisposableEffect(this, cle) {
            cibles[cle] = fr
            onDispose {
                if (cibles[cle] === fr) {
                    cibles.remove(cle)
                    bornes.remove(cle)
                }
            }
        }
        return fr
    }

    companion object {
        const val CLE_ACCUEIL = "haut:accueil"
        const val CLE_RECHERCHE = "haut:recherche"
        const val CLE_MAJ = "haut:maj"
        const val CLE_ZONE = "zone"
        const val CLE_AIDE = "aide"
        const val CLE_BIBLIO = "bibliotheque"
    }
}

/** Bulle d'aide : texte et point d'ancrage (milieu du bas du bouton, px). */
internal class PlBulle(val cle: String, val texte: String, val x: Float, val y: Float)

// ------------------------------------------------------------------ écran

/**
 * Design « Playlist », inspiré d'une appli de musique de bureau : fond noir, panneaux #121212
 * aux coins arrondis séparés par des marges noires, accent vert, titres gras et serrés.
 *  - en haut : bouton Accueil et champ « Que voulez-vous lancer ? » (ouvre la bibliothèque),
 *    boutons ronds Wi-Fi, Bluetooth, Sources, Réglages (et « Mise à jour ») à droite ;
 *  - à gauche, « Bibliothèque » : pastilles de filtre par rubrique, puis les favoris et
 *    « Mes apps » en lignes (pochette carrée, nom, ligne grise) ;
 *  - au centre : dégradé de la couleur de l'app sélectionnée, salutation et tuiles de
 *    raccourcis (dernières apps lancées), puis « Vos apps » (favoris) et « Toutes les apps »
 *    (« Mes apps » et la bibliothèque) en cartes carrées : la carte sélectionnée s'éclaircit
 *    et montre le bouton rond vert « lecture » ;
 *  - à droite, « En ce moment » : la zone de modules ;
 *  - en bas, la barre « lecteur » : app sélectionnée, heure et date au centre, réseau et volume.
 * Navigation déterministe : voir [PlaylistNav].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomePlaylist(ctx: HomeContext) {
    val nav = remember { PlaylistNav() }
    val scope = rememberCoroutineScope()
    val etatLignes = rememberLazyListState()
    nav.etatLignes = etatLignes
    nav.debut = {
        scope.launch {
            ctx.defilement.scrollTo(0)
            ctx.sortieNba()
        }
    }
    val ui = ctx.ui

    // Bibliothèque : favoris puis « Mes apps », filtrables par rubrique
    var filtre by remember { mutableStateOf<Rubrique?>(null) }
    val entrees = remember(ui.favorites, ui.homeApps) {
        (ui.favorites.map { it to true } + ui.homeApps.map { it to false }).distinctBy { it.first.packageName }
    }
    val rubriqueDe = remember(entrees) { entrees.associate { (a, _) -> a.packageName to AppCategories.rubrique(a) } }
    val rubriques = remember(rubriqueDe) { rubriqueDe.values.distinct().sortedBy { it.ordinal } }
    val filtreActif = filtre?.takeIf { it in rubriques }
    val lignes = if (filtreActif == null) entrees else entrees.filter { rubriqueDe[it.first.packageName] == filtreActif }

    // Raccourcis : dernières apps lancées, complétées par « Mes apps » puis les favoris
    var recentes by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val tuiles = remember(recentes, ui.homeApps, ui.favorites, ui.allApps) {
        val parPaquet = (ui.allApps + ui.favorites + ui.homeApps).associateBy { it.packageName }
        (recentes.mapNotNull { parPaquet[it] } + ui.homeApps + ui.favorites).distinctBy { it.packageName }.take(6)
    }
    val lancer: (AppInfo) -> Unit = { app ->
        recentes = (listOf(app.packageName) + recentes.filter { it != app.packageName }).take(6)
        ctx.actions.launch(app)
    }

    // App « en lecture » : la dernière app sélectionnée reste affichée quand le focus est ailleurs
    val memoire = remember { arrayOfNulls<AppInfo>(1) }
    ctx.selection?.let { memoire[0] = it }
    val enLecture = ctx.selection ?: memoire[0]

    Box(
        Modifier
            .fillMaxSize()
            .background(PlNoir)
    ) {
        Column(Modifier.fillMaxSize()) {
            PlBarreHaut(ctx, nav) {
                filtre = null
                scope.launch { etatLignes.scrollToItem(0) }
                nav.debut()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = PL_MARGE.u),
                horizontalArrangement = Arrangement.spacedBy(PL_MARGE.u),
            ) {
                PlLaterale(
                    ctx, nav, lignes, rubriques, rubriqueDe, filtreActif,
                    onFiltre = { r -> filtre = if (filtreActif == r) null else r },
                    lancer = lancer,
                    enLecture = enLecture,
                    etat = etatLignes,
                )
                PlCentre(ctx, nav, tuiles, rubriqueDe, lancer, Modifier.weight(1f))
                PlDroite(ctx, nav)
            }
            PlLecteur(ctx, enLecture, enLecture != null && ui.favorites.any { it.packageName == enLecture.packageName })
        }
        PlBulleAide(nav)
    }
}

// ------------------------------------------------------------------ textes

/** Texte d'une ligne (ou [lignes]) avec un interligne fixe (le thème TV en impose un grand). */
@Composable
private fun PlTexte(
    texte: String,
    taille: Int,
    poids: FontWeight,
    couleur: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    lignes: Int = 1,
    serre: Boolean = false,
    chiffres: Boolean = false,
    alignement: TextAlign? = null,
) {
    val k = LocalScale.current
    Text(
        texte,
        modifier = modifier,
        color = couleur,
        fontSize = taille.us,
        fontWeight = poids,
        fontFamily = PlPolice,
        letterSpacing = if (serre) (-0.03).em else TextUnit.Unspecified,
        lineHeight = (taille * 1.22f * k).sp,
        maxLines = lignes,
        overflow = TextOverflow.Ellipsis,
        softWrap = lignes > 1,
        textAlign = alignement,
        style = if (chiffres) LocalTextStyle.current.copy(fontFeatureSettings = "tnum") else LocalTextStyle.current,
    )
}

// ------------------------------------------------------------------ barre du haut

@Composable
private fun PlBarreHaut(ctx: HomeContext, nav: PlaylistNav, accueil: () -> Unit) {
    val rapides = ctx.quickActions()
    val enLigne = ctx.wifiLabel != "Hors ligne"
    nav.haut = buildList {
        add(PlaylistNav.CLE_ACCUEIL)
        add(PlaylistNav.CLE_RECHERCHE)
        if (ctx.majDisponible) add(PlaylistNav.CLE_MAJ)
        rapides.forEach { add("haut:" + it.label) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(PL_HAUT.u)
            // Accueil et recherche au-dessus du contenu central, comme dans l'appli de bureau
            .padding(start = (PL_MARGE + PL_LATERALE + PL_MARGE + 8).u, end = (PL_MARGE + 4).u)
            .onPreviewKeyEvent { nav.toucheHaut(it, ctx) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlBoutonRond(
            ProjoIcons.Accueil, "Accueil", accueil, null, PlaylistNav.CLE_ACCUEIL, nav, ctx,
            taille = 44, modifier = Modifier.testTag("rapide:Accueil"),
        )
        Spacer(Modifier.width(8.u))
        PlRecherche(ctx, nav, Modifier.width(330.u))
        Spacer(Modifier.weight(1f))
        if (ctx.majDisponible) {
            PlPiluleMaj(ctx, nav)
            Spacer(Modifier.width(12.u))
        }
        rapides.forEachIndexed { i, a ->
            if (i > 0) Spacer(Modifier.width(8.u))
            PlBoutonRond(
                a.icon, a.label, a.onClick, a.onLongClick, "haut:" + a.label, nav, ctx,
                point = a.icon == ProjoIcons.Wifi && enLigne,
                modifier = Modifier
                    .testTag("rapide:" + a.label)
                    .then(if (a.estReglages) Modifier.focusRequester(ctx.focusReglages) else Modifier),
            )
        }
    }
}

/** Mesure un élément de la barre du haut et note sa sélection (bulle d'aide si [bulle]). */
private fun Modifier.plElementHaut(nav: PlaylistNav, ctx: HomeContext, cle: String, bulle: String?): Modifier =
    this
        .onGloballyPositioned { nav.bornes[cle] = it.boundsInRoot() }
        .onFocusChanged {
            if (it.isFocused) {
                nav.focusCle = cle
                ctx.sansSelection()
                val b = nav.bornes[cle]
                nav.bulle = if (bulle != null && b != null) PlBulle(cle, bulle, b.center.x, b.bottom) else null
            } else if (nav.bulle?.cle == cle) {
                nav.bulle = null
            }
        }

/** Bouton rond sombre ; sélectionné : blanc, icône noire, légèrement agrandi. */
@Composable
private fun PlBoutonRond(
    icone: ImageVector,
    libelle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    cle: String,
    nav: PlaylistNav,
    ctx: HomeContext,
    modifier: Modifier = Modifier,
    taille: Int = 40,
    point: Boolean = false,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(taille.u)
            .focusRequester(nav.demandeur(cle))
            .plElementHaut(nav, ctx, cle, libelle),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PlBouton,
            contentColor = PlGris,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = PlGris,
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f, pressedScale = 0.96f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icone, contentDescription = libelle, modifier = Modifier.size((taille / 2).u))
            if (point) {
                // Point vert : connecté
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = (taille / 5).u, end = (taille / 5).u)
                        .size(7.u)
                        .clip(CircleShape)
                        .background(PlVert)
                )
            }
        }
    }
}

/** Champ « Que voulez-vous lancer ? » : ouvre la bibliothèque. */
@Composable
private fun PlRecherche(ctx: HomeContext, nav: PlaylistNav, modifier: Modifier) {
    val forme = RoundedCornerShape(22.u)
    Surface(
        onClick = ctx.actions.openLibrary,
        modifier = modifier
            .height(44.u)
            .testTag("recherche")
            .focusRequester(nav.demandeur(PlaylistNav.CLE_RECHERCHE))
            .plElementHaut(nav, ctx, PlaylistNav.CLE_RECHERCHE, null),
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PlBouton,
            contentColor = PlGris,
            focusedContainerColor = PlSurvol,
            focusedContentColor = Color.White,
            pressedContainerColor = PlSurvol,
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.99f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.u, Color.White), shape = forme),
        ),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 14.u, end = 16.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PlIcones.Recherche, contentDescription = null, modifier = Modifier.size(22.u))
            Spacer(Modifier.width(10.u))
            PlTexte("Que voulez-vous lancer ?", 15, FontWeight.Medium, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .width(1.u)
                    .height(22.u)
                    .background(PlGris.copy(alpha = 0.45f))
            )
            Spacer(Modifier.width(12.u))
            Icon(ProjoIcons.Apps, contentDescription = "Toutes les apps", modifier = Modifier.size(20.u))
        }
    }
}

/** « Mise à jour » : pilule blanche à texte noir ; sélectionnée : verte. */
@Composable
private fun PlPiluleMaj(ctx: HomeContext, nav: PlaylistNav) {
    val forme = RoundedCornerShape(16.u)
    Surface(
        onClick = ctx.actions.update,
        modifier = Modifier
            .height(32.u)
            .testTag("rapide:Mise à jour")
            .focusRequester(nav.demandeur(PlaylistNav.CLE_MAJ))
            .plElementHaut(nav, ctx, PlaylistNav.CLE_MAJ, null),
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White,
            contentColor = Color.Black,
            focusedContainerColor = PlVert,
            focusedContentColor = Color.Black,
            pressedContainerColor = PlVert,
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f, pressedScale = 0.97f),
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ProjoIcons.MiseAJour, contentDescription = null, modifier = Modifier.size(16.u))
            Spacer(Modifier.width(6.u))
            PlTexte("Mise à jour", 13, FontWeight.Bold)
        }
    }
}

/** Nom du bouton rond sélectionné, dans une bulle sombre sous le bouton. */
@Composable
private fun PlBulleAide(nav: PlaylistNav) {
    val b = nav.bulle ?: return
    val ecart = with(LocalDensity.current) { 7.u.toPx() }
    PlTexte(
        b.texte, 13, FontWeight.SemiBold, Color.White,
        modifier = Modifier
            .layout { mesurable, contraintes ->
                val p = mesurable.measure(contraintes.copy(minWidth = 0, minHeight = 0))
                layout(contraintes.maxWidth, contraintes.maxHeight) {
                    val x = (b.x - p.width / 2f).roundToInt().coerceIn(0, maxOf(0, contraintes.maxWidth - p.width - 6))
                    p.place(x, (b.y + ecart).roundToInt())
                }
            }
            .clip(RoundedCornerShape(4.u))
            .background(PlSurvol)
            .padding(horizontal = 9.u, vertical = 5.u),
    )
}

// ------------------------------------------------------------------ bibliothèque (barre latérale)

@Composable
private fun PlLaterale(
    ctx: HomeContext,
    nav: PlaylistNav,
    lignes: List<Pair<AppInfo, Boolean>>,
    rubriques: List<Rubrique>,
    rubriqueDe: Map<String, Rubrique>,
    filtre: Rubrique?,
    onFiltre: (Rubrique) -> Unit,
    lancer: (AppInfo) -> Unit,
    enLecture: AppInfo?,
    etat: LazyListState,
) {
    nav.filtres = rubriques.map { "filtre:" + it.court() }
    nav.lignes = lignes.map { "ligne:" + it.first.packageName }
    val actif = filtre?.let { "filtre:" + it.court() }
    Column(
        Modifier
            .width(PL_LATERALE.u)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.u))
            .background(PlPanneau)
            .onGloballyPositioned { nav.droiteLaterale = it.boundsInRoot().right }
    ) {
        Row(
            Modifier.padding(start = 16.u, end = 16.u, top = 14.u, bottom = 10.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PlIcones.Bibliotheque, contentDescription = null, tint = PlGris, modifier = Modifier.size(22.u))
            Spacer(Modifier.width(10.u))
            PlTexte("Bibliothèque", 16, FontWeight.Bold, Color.White, Modifier.weight(1f), serre = true)
            PlTexte("${lignes.size}", 13, FontWeight.SemiBold, PlGris)
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { nav.toucheFiltres(it, ctx) },
            contentPadding = PaddingValues(horizontal = 12.u),
            horizontalArrangement = Arrangement.spacedBy(6.u),
        ) {
            items(rubriques, key = { it.name }) { r ->
                PlPastille(r.court(), r == filtre, "filtre:" + r.court(), nav, ctx) { onFiltre(r) }
            }
        }
        Spacer(Modifier.height(8.u))
        LazyColumn(
            state = etat,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { nav.origineLignes = it.positionInRoot().y }
                .onPreviewKeyEvent { nav.toucheLignes(it, ctx, actif) },
            contentPadding = PaddingValues(start = 6.u, end = 6.u, bottom = 8.u),
        ) {
            items(lignes, key = { "ligne:" + it.first.packageName }) { (app, favori) ->
                PlLigne(
                    app, favori, rubriqueDe[app.packageName] ?: Rubrique.AUTRES, ctx, nav, lancer,
                    enLecture = enLecture?.packageName == app.packageName,
                )
            }
        }
    }
}

/** Pastille de filtre ; active : verte, sélectionnée : blanche (verte cerclée de blanc si active). */
@Composable
private fun PlPastille(
    texte: String,
    actif: Boolean,
    cle: String,
    nav: PlaylistNav,
    ctx: HomeContext,
    onClick: () -> Unit,
) {
    val forme = RoundedCornerShape(16.u)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(32.u)
            .testTag(cle)
            .focusRequester(nav.demandeur(cle))
            .onGloballyPositioned { nav.bornes[cle] = it.boundsInRoot() }
            .onFocusChanged {
                if (it.isFocused) {
                    nav.focusCle = cle
                    ctx.sansSelection()
                }
            },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (actif) PlVert else Color.White.copy(alpha = 0.08f),
            contentColor = if (actif) Color.Black else Color.White,
            focusedContainerColor = if (actif) PlVert else Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = PlGris,
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f, pressedScale = 0.97f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.u, Color.White), shape = forme),
        ),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.u),
            contentAlignment = Alignment.Center,
        ) {
            PlTexte(texte, 13, FontWeight.SemiBold)
        }
    }
}

/** Ligne de la bibliothèque : pochette, nom (vert si c'est l'app « en lecture »), ligne grise. */
@Composable
private fun PlLigne(
    app: AppInfo,
    favori: Boolean,
    rubrique: Rubrique,
    ctx: HomeContext,
    nav: PlaylistNav,
    lancer: (AppInfo) -> Unit,
    enLecture: Boolean,
) {
    val cle = "ligne:" + app.packageName
    val visuel = rememberAppVisual(ctx.ui.iconSourceOf(app.packageName), ctx.icons, FormatVisuel.CARRE)
    val couleur = visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    var focus by remember { mutableStateOf(false) }
    // Visuel arrivé après la prise de focus : le dégradé prend la vraie couleur
    LaunchedEffect(couleur) { if (focus) ctx.surFocus(app, couleur) }
    val forme = RoundedCornerShape(6.u)
    Surface(
        onClick = { lancer(app) },
        onLongClick = { ctx.actions.openMenu(app) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.u)
            .testTag(cle)
            .focusRequester(nav.demandeur(cle))
            .onGloballyPositioned { nav.bornes[cle] = it.boundsInRoot() }
            .uneToucheMenu { ctx.actions.openMenu(app) }
            .onFocusChanged {
                focus = it.isFocused
                if (it.isFocused) {
                    nav.focusCle = cle
                    ctx.surFocus(app, couleur)
                }
            },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.18f),
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.98f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.u, Color.White.copy(alpha = 0.9f)), shape = forme),
        ),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 7.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlPochette(app, visuel, 42.u, RoundedCornerShape(4.u))
            Spacer(Modifier.width(10.u))
            Column(Modifier.weight(1f)) {
                PlTexte(app.label, 14, FontWeight.SemiBold, if (enLecture) PlVert else Color.White)
                Spacer(Modifier.height(2.u))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (favori) {
                        Icon(PlIcones.Epingle, contentDescription = null, tint = PlVert, modifier = Modifier.size(12.u))
                        Spacer(Modifier.width(4.u))
                    }
                    PlTexte((if (favori) "Favori" else "Mes apps") + " • " + rubrique.court(), 12, FontWeight.Medium, PlGris)
                }
            }
            if (enLecture) PlEgaliseur(Modifier.padding(start = 6.u))
        }
    }
}

/** Trois barres vertes : l'app « en lecture ». */
@Composable
private fun PlEgaliseur(modifier: Modifier = Modifier) {
    val k = with(LocalDensity.current) { 1.u.toPx() }
    Spacer(
        modifier
            .size(14.u)
            .drawBehind {
                val largeur = 2.6f * k
                val hauteurs = floatArrayOf(0.55f, 1f, 0.75f)
                for (i in 0..2) {
                    val h = size.height * hauteurs[i]
                    drawRect(
                        PlVert,
                        topLeft = Offset(i * (largeur + 2f * k) + 1f * k, size.height - h),
                        size = Size(largeur, h),
                    )
                }
            }
    )
}

/** Pochette carrée de l'app (visuel CARRÉ), tuile HDMI dessinée, sinon l'initiale. */
@Composable
private fun PlPochette(app: AppInfo, visuel: AppVisual?, taille: Dp, forme: RoundedCornerShape) {
    Box(
        Modifier
            .size(taille)
            .clip(forme)
            .background(visuel?.fond ?: PlSurvol),
        contentAlignment = Alignment.Center,
    ) {
        when {
            app.isSpecial -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF5B6475), Color(0xFF262A33))))
                )
                Icon(ProjoIcons.Sources, contentDescription = null, tint = Color.White, modifier = Modifier.size(taille * 0.46f))
            }
            visuel?.art != null -> ImageApp(visuel, null, Modifier.fillMaxSize())
            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(lerp(visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT, Color.Black, 0.45f))
                )
                val taillePolice = with(LocalDensity.current) { (taille * 0.42f).toSp() }
                Text(
                    app.label.take(1).uppercase(Locale.FRENCH), color = Color.White, fontSize = taillePolice,
                    fontWeight = FontWeight.Black, fontFamily = PlPolice,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ contenu central

@Composable
private fun PlCentre(
    ctx: HomeContext,
    nav: PlaylistNav,
    tuiles: List<AppInfo>,
    rubriqueDe: Map<String, Rubrique>,
    lancer: (AppInfo) -> Unit,
    modifier: Modifier,
) {
    val t = rememberMaintenant()
    // Lu seulement au dessin : le fondu ne recompose rien
    val teinte = animateColorAsState(plTeinte(ctx.couleur), tween(400), label = "teintePlaylist")
    val hauteurDegrade = 300.u
    fun rubrique(a: AppInfo) = rubriqueDe[a.packageName] ?: AppCategories.rubrique(a)
    Box(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.u))
            .background(PlPanneau)
            .drawBehind {
                val h = hauteurDegrade.toPx()
                drawRect(
                    Brush.verticalGradient(listOf(teinte.value, PlPanneau), startY = 0f, endY = h),
                    size = Size(size.width, h),
                )
            }
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                nav.centreHaut = b.top
                nav.centreBas = b.bottom
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(ctx.defilement)
                .padding(bottom = 16.u)
        ) {
            PlTexte(
                salutation(t), 26, FontWeight.ExtraBold, Color.White, serre = true,
                modifier = Modifier.padding(start = 16.u, end = 16.u, top = 12.u),
            )
            PlGrille(ctx, nav, tuiles, lancer)
            // Attribution TMDB obligatoire quand les cartes montrent des nouveautés
            val contexte = LocalContext.current
            val tmdb = remember(fr.projo.launcher.data.CleTmdb.version) { fr.projo.launcher.data.CleTmdb.lire(contexte) != null }
            PlTitreSection("Vos apps", if (tmdb && fr.projo.launcher.data.ReglageNouveautes.actif) "Données : TMDB" else null)
            PlRangeeFavoris(ctx, nav, ::rubrique, lancer)
            PlTitreSection("Toutes les apps")
            PlRangeeApps(ctx, nav, ::rubrique, lancer)
        }
    }
}

/** Titre de section, et à droite une mention discrète facultative. */
@Composable
private fun PlTitreSection(texte: String, mention: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.u, end = 16.u, top = 16.u, bottom = 4.u),
        verticalAlignment = Alignment.Bottom,
    ) {
        PlTexte(texte, 20, FontWeight.ExtraBold, Color.White, Modifier.weight(1f), serre = true)
        if (mention != null) PlTexte(mention, 11, FontWeight.Medium, PlGris.copy(alpha = 0.8f), Modifier.padding(bottom = 3.u))
    }
}

/** Raccourcis façon « écoutés récemment » : deux colonnes de tuiles horizontales. */
@Composable
private fun PlGrille(ctx: HomeContext, nav: PlaylistNav, tuiles: List<AppInfo>, lancer: (AppInfo) -> Unit) {
    nav.tuiles = tuiles.map { "tuile:" + it.packageName }
    if (tuiles.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.u, end = 16.u, top = 10.u)
            .onPreviewKeyEvent { nav.toucheTuiles(it, ctx) },
        verticalArrangement = Arrangement.spacedBy(8.u),
    ) {
        tuiles.chunked(2).forEach { paire ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.u)) {
                paire.forEach { app ->
                    key(app.packageName) { PlTuile(app, ctx, nav, lancer, Modifier.weight(1f)) }
                }
                if (paire.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Tuile de raccourci : pochette, nom en gras, fond gris translucide ; sélectionnée : bouton vert. */
@Composable
private fun PlTuile(app: AppInfo, ctx: HomeContext, nav: PlaylistNav, lancer: (AppInfo) -> Unit, modifier: Modifier) {
    val cle = "tuile:" + app.packageName
    val visuel = rememberAppVisual(ctx.ui.iconSourceOf(app.packageName), ctx.icons, FormatVisuel.CARRE)
    val couleur = visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    var focus by remember { mutableStateOf(false) }
    LaunchedEffect(couleur) { if (focus) ctx.surFocus(app, couleur) }
    val forme = RoundedCornerShape(4.u)
    Surface(
        onClick = { lancer(app) },
        onLongClick = { ctx.actions.openMenu(app) },
        modifier = modifier
            .height(PL_TUILE.u)
            .testTag(cle)
            .focusRequester(nav.demandeur(cle))
            .onGloballyPositioned { nav.bornes[cle] = it.boundsInRoot() }
            .uneToucheMenu { ctx.actions.openMenu(app) }
            .onFocusChanged {
                focus = it.isFocused
                if (it.isFocused) {
                    nav.focusCle = cle
                    ctx.surFocus(app, couleur)
                }
            },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.28f),
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.98f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.u, Color.White.copy(alpha = 0.9f)), shape = forme),
        ),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            PlPochette(app, visuel, PL_TUILE.u, RoundedCornerShape(topStart = 4.u, bottomStart = 4.u))
            Spacer(Modifier.width(10.u))
            PlTexte(app.label, 14, FontWeight.Bold, Color.White, Modifier.weight(1f), lignes = 2)
            PlLecture(focus, 30, Modifier.padding(start = 4.u, end = 7.u))
        }
    }
}

/** Bouton rond vert « lecture » (triangle noir) : apparaît en glissant vers le haut. */
@Composable
private fun PlLecture(visible: Boolean, taille: Int, modifier: Modifier = Modifier) {
    val p = animateFloatAsState(if (visible) 1f else 0f, tween(180), label = "lecture")
    val k = with(LocalDensity.current) { 1.u.toPx() }
    val cote = taille.u
    Box(
        modifier
            .size(cote)
            .graphicsLayer {
                alpha = p.value
                translationY = (1f - p.value) * 8f * k
            }
            .drawBehind {
                // Ombre portée simple (aucun flou)
                drawCircle(
                    Color.Black.copy(alpha = 0.35f), radius = size.minDimension / 2f + 1.5f * k,
                    center = center + Offset(0f, 2f * k),
                )
                drawCircle(PlVert)
            },
        contentAlignment = Alignment.Center,
    ) {
        UneTriangle(
            Color.Black, versDroite = true,
            modifier = Modifier
                .offset(x = cote * 0.05f)
                .size(cote * 0.32f, cote * 0.36f),
        )
    }
}

/** Mesure une rangée (bord gauche, largeur de la fenêtre, centre vertical) pour la navigation. */
private fun Modifier.plRangee(r: NavRangee, surY: (Float) -> Unit): Modifier = onGloballyPositioned {
    val p = it.positionInRoot()
    r.gauche = p.x
    r.largeurFenetre = it.findRootCoordinates().size.width.toFloat()
    surY(p.y + it.size.height / 2f)
}

@Composable
private fun PlRangeeFavoris(ctx: HomeContext, nav: PlaylistNav, rubrique: (AppInfo) -> Rubrique, lancer: (AppInfo) -> Unit) {
    val ui = ctx.ui
    LazyRow(
        state = ctx.rangeeFavoris,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rangee:favoris")
            .plRangee(ctx.nav.favoris) { nav.yFavoris = it }
            .onPreviewKeyEvent { nav.toucheRangee(it, ctx, favoris = true) },
        contentPadding = PaddingValues(horizontal = 8.u),
    ) {
        if (ui.loaded && ui.favorites.isEmpty()) {
            item(key = PlaylistNav.CLE_AIDE) {
                PlCadreCarte(
                    onClick = {},
                    onLongClick = null,
                    onFocus = { if (it) ctx.sansSelection() },
                    titre = "Aucun favori",
                    sousTitre = "Appui long sur une app pour l'épingler",
                    lignesSousTitre = 2,
                    modifier = Modifier
                        .testTag("aide")
                        .focusRequester(ctx.premierFocus)
                        .then(rememberCibleNav(ctx.nav.favoris, PlaylistNav.CLE_AIDE)),
                ) {
                    PlImageIcone(PlIcones.Epingle, listOf(Color(0xFF3A3A3A), Color(0xFF1E1E1E)))
                }
            }
        }
        itemsIndexed(ui.favorites, key = { _, a -> "fav:" + a.packageName }) { i, app ->
            PlCarte(
                app, ctx, rubrique(app).sousTitre(), lancer,
                Modifier
                    .testTag("favori:" + app.packageName)
                    .then(if (i == 0) Modifier.focusRequester(ctx.premierFocus) else Modifier)
                    .then(rememberCibleNav(ctx.nav.favoris, "fav:" + app.packageName)),
            )
        }
    }
}

@Composable
private fun PlRangeeApps(ctx: HomeContext, nav: PlaylistNav, rubrique: (AppInfo) -> Rubrique, lancer: (AppInfo) -> Unit) {
    val ui = ctx.ui
    LazyRow(
        state = ctx.rangeeApps,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rangee:apps")
            .plRangee(ctx.nav.apps) { nav.yApps = it }
            .onPreviewKeyEvent { nav.toucheRangee(it, ctx, favoris = false) },
        contentPadding = PaddingValues(horizontal = 8.u),
    ) {
        itemsIndexed(ui.homeApps, key = { _, a -> "app:" + a.packageName }) { _, app ->
            PlCarte(
                app, ctx, rubrique(app).sousTitre(), lancer,
                Modifier
                    .testTag("app:" + app.packageName)
                    .then(rememberCibleNav(ctx.nav.apps, "app:" + app.packageName)),
            )
        }
        // Bibliothèque complète, à la manière des « Titres likés » (dégradé bleu-violet → menthe)
        item(key = PlaylistNav.CLE_BIBLIO) {
            val n = ui.allApps.size
            PlCadreCarte(
                onClick = ctx.actions.openLibrary,
                onLongClick = null,
                onFocus = { if (it) ctx.sansSelection() },
                titre = "Tout afficher",
                sousTitre = if (n > 1) "$n applications" else "$n application",
                modifier = Modifier
                    .testTag("biblio")
                    .focusRequester(ctx.focusBiblio)
                    .then(rememberCibleNav(ctx.nav.apps, PlaylistNav.CLE_BIBLIO)),
            ) {
                PlImageIcone(ProjoIcons.Apps, listOf(Color(0xFF450AF5), Color(0xFF8E8EE5), Color(0xFFC4EFD9)))
            }
        }
    }
}

/** Image de carte dessinée : dégradé en diagonale et icône blanche. */
@Composable
private fun PlImageIcone(icone: ImageVector, couleurs: List<Color>) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.u))
            .background(Brush.linearGradient(couleurs)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.u))
    }
}

/**
 * Carte carrée d'une app : pochette, nom, rubrique. Service de streaming : la photo de sa
 * dernière nouveauté, stylisée (VisuelNouveaute, partagé), et son titre sous le nom.
 */
@Composable
private fun PlCarte(app: AppInfo, ctx: HomeContext, sousTitre: String, lancer: (AppInfo) -> Unit, modifier: Modifier) {
    val paquet = ctx.ui.iconSourceOf(app.packageName)
    val visuel = rememberAppVisual(paquet, ctx.icons, FormatVisuel.CARRE)
    val couleur = visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    var focus by remember { mutableStateOf(false) }
    LaunchedEffect(couleur) { if (focus) ctx.surFocus(app, couleur) }
    val catalogue = Nouveautes.aUnCatalogue(app)
    val nouveaute = if (catalogue) rememberNouveaute(app, largeurImageNouveaute(PL_IMAGE.u, FormeNouveaute.CARRE)) else null
    val style = StylesNouveautes.courant
    PlCadreCarte(
        onClick = { lancer(app) },
        onLongClick = { ctx.actions.openMenu(app) },
        onFocus = { f ->
            focus = f
            if (f) ctx.surFocus(app, couleur)
        },
        titre = app.label,
        sousTitre = sousTitre,
        // Titre de la nouveauté sous le nom (sur l'image, le bouton « lecture » le cacherait)
        nouveau = nouveaute?.titre,
        modifier = modifier.uneToucheMenu { ctx.actions.openMenu(app) },
    ) {
        if (catalogue) {
            VisuelNouveaute(
                app, ctx.icons, paquet, FormeNouveaute.CARRE, PL_IMAGE.u,
                rayon = 6.u, style = style, legende = false, nouveaute = nouveaute,
            ) {
                PlPochette(app, visuel, PL_IMAGE.u, RoundedCornerShape(6.u))
            }
        } else {
            PlPochette(app, visuel, PL_IMAGE.u, RoundedCornerShape(6.u))
        }
    }
}

/**
 * Cadre commun des cartes : transparent ; sélectionné, il s'éclaircit, grandit un peu et le
 * bouton vert « lecture » apparaît en bas à droite de l'image.
 */
@Composable
private fun PlCadreCarte(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocus: (Boolean) -> Unit,
    titre: String,
    sousTitre: String,
    modifier: Modifier,
    lignesSousTitre: Int = 1,
    /** Titre d'une nouveauté : remplace le sous-titre, précédé d'un point vert. */
    nouveau: String? = null,
    image: @Composable () -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(PL_CARTE.u)
            .onFocusChanged {
                focus = it.isFocused
                onFocus(it.isFocused)
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.u)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.11f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.16f),
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f, pressedScale = 0.98f),
    ) {
        Column(Modifier.padding(8.u)) {
            Box(Modifier.size(PL_IMAGE.u)) {
                image()
                PlLecture(
                    focus, 40,
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.u, bottom = 6.u),
                )
            }
            Spacer(Modifier.height(8.u))
            PlTexte(titre, 14, FontWeight.Bold, Color.White)
            Spacer(Modifier.height(2.u))
            if (nouveau != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.u)
                            .clip(CircleShape)
                            .background(PlVert)
                    )
                    Spacer(Modifier.width(5.u))
                    PlTexte(nouveau, 12, FontWeight.Medium, PlGris)
                }
            } else {
                PlTexte(sousTitre, 12, FontWeight.Medium, PlGris, lignes = lignesSousTitre)
            }
        }
    }
}

// ------------------------------------------------------------------ « En ce moment » (modules)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlDroite(ctx: HomeContext, nav: PlaylistNav) {
    Column(
        Modifier
            .width(PL_DROITE.u)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.u))
            .background(PlPanneau)
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                nav.gaucheZone = b.left
                nav.xZone = b.center.x
            }
    ) {
        PlTexte(
            FocusModules.titre ?: "En ce moment", 16, FontWeight.Bold, Color.White, serre = true,
            modifier = Modifier.padding(start = 16.u, end = 16.u, top = 14.u, bottom = 4.u),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onFocusChanged {
                    if (it.hasFocus) {
                        nav.focusCle = PlaylistNav.CLE_ZONE
                        ctx.sansSelection()
                    }
                }
                // ▲ en haut de la zone : le bouton rond le plus proche ; ▶ et ▼ : on reste
                .focusProperties { exit = { sens -> nav.sortieZone(sens) } }
                .focusGroup()
        ) {
            NbaPanel(
                data = ctx.nbaData,
                erreur = ctx.nbaErreur,
                repo = ctx.nbaRepo,
                modifier = Modifier.fillMaxWidth(),
                onExit = { nav.debut() },
                style = PlStyleModules,
                entree = nav.entreeZone,
            )
        }
    }
}

// ------------------------------------------------------------------ barre « lecteur »

/** Volume du média (0..1), relu à chaque minute ; null si inconnu. */
@Composable
private fun rememberVolume(t: Long): Float? {
    val contexte = LocalContext.current
    return remember(t / 60_000L) {
        runCatching {
            val audio = contexte.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        }.getOrNull()
    }
}

/** Part de la journée écoulée (0..1). */
private fun partDuJour(t: Long): Float {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    return (c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) / 1440f
}

/** App sélectionnée à gauche, heure et date au centre (avec la journée en barre), réseau et volume à droite. */
@Composable
private fun PlLecteur(ctx: HomeContext, app: AppInfo?, favori: Boolean) {
    val t = rememberMaintenant()
    val volume = rememberVolume(t)
    Row(
        Modifier
            .fillMaxWidth()
            .height(PL_BAS.u)
            .padding(horizontal = 18.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (app != null) {
                val visuel = rememberAppVisual(ctx.ui.iconSourceOf(app.packageName), ctx.icons, FormatVisuel.CARRE)
                PlPochette(app, visuel, 52.u, RoundedCornerShape(4.u))
                Spacer(Modifier.width(12.u))
                Column(Modifier.weight(1f, fill = false)) {
                    PlTexte(app.label, 15, FontWeight.Bold, Color.White)
                    Spacer(Modifier.height(2.u))
                    PlTexte(AppCategories.rubrique(app).titre, 12, FontWeight.Medium, PlGris)
                }
                if (favori) {
                    Spacer(Modifier.width(14.u))
                    PlCoche()
                }
            }
        }
        Column(Modifier.width(340.u), horizontalAlignment = Alignment.CenterHorizontally) {
            PlTexte(heureTexte(t), 28, FontWeight.ExtraBold, Color.White, serre = true, chiffres = true)
            PlTexte(
                dateTexte(t).replaceFirstChar { it.titlecase(Locale.FRENCH) }, 13, FontWeight.Medium, PlGris,
            )
            Spacer(Modifier.height(5.u))
            PlBarre(partDuJour(t), Modifier.width(320.u))
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ProjoIcons.Wifi, contentDescription = null, tint = PlGris, modifier = Modifier.size(18.u))
            Spacer(Modifier.width(6.u))
            PlTexte(ctx.wifiLabel, 12, FontWeight.Medium, PlGris, Modifier.widthIn(max = 120.u))
            Spacer(Modifier.width(18.u))
            Icon(
                if (volume == 0f) ProjoIcons.Muet else ProjoIcons.Volume, contentDescription = "Volume",
                tint = PlGris, modifier = Modifier.size(20.u),
            )
            Spacer(Modifier.width(8.u))
            PlBarre(volume ?: 0f, Modifier.width(96.u))
        }
    }
}

/** Barre fine : rail gris, partie remplie blanche. */
@Composable
private fun PlBarre(part: Float, modifier: Modifier) {
    Spacer(
        modifier
            .height(4.u)
            .drawBehind {
                val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                drawRoundRect(PlRail, cornerRadius = r)
                val w = size.width * part.coerceIn(0f, 1f)
                if (w > 0f) drawRoundRect(Color.White, size = Size(w, size.height), cornerRadius = r)
            }
    )
}

/** Coche verte : l'app est épinglée en favori. */
@Composable
private fun PlCoche() {
    val k = with(LocalDensity.current) { 1.u.toPx() }
    Spacer(
        Modifier
            .size(18.u)
            .drawBehind {
                drawCircle(PlVert)
                val w = size.width
                val p = Path().apply {
                    moveTo(w * 0.28f, w * 0.52f)
                    lineTo(w * 0.44f, w * 0.67f)
                    lineTo(w * 0.73f, w * 0.36f)
                }
                drawPath(p, Color.Black, style = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
    )
}
