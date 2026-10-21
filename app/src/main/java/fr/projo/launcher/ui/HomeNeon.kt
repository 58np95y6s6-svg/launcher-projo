package fr.projo.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.IconLoader
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * Design « Néon » : l'accueil d'une console de salon.
 *  - fond uni sombre (#2D2D2D, par défaut) ou clair (#EBEBEB), au choix dans Réglages → Fond ;
 *  - en haut : avatar rond à gauche, heure et Wi-Fi à droite ;
 *  - une grande rangée de tuiles CARRÉES (favoris, « Mes apps », « Toutes les apps ») ; la tuile
 *    sélectionnée a un épais cadre cyan qui pulse doucement, son nom s'affiche au-dessus en cyan ;
 *  - dessous, des boutons ronds de couleur vive (Sources, Wi-Fi, Bluetooth, Modules, Toutes les
 *    apps, Réglages, Mise à jour) ;
 *  - en bas, une fine barre d'aides de touches (OK Ouvrir, ≡ Options…).
 * Le bouton Modules fait glisser du bas un panneau qui contient la zone de modules.
 *
 * Navigation : ◀ ▶ dans une rangée ; ▼ depuis une tuile → bouton rond le plus aligné ;
 * ▲ depuis un bouton → la tuile d'où l'on venait (sinon la tuile visible la plus alignée).
 */

/** Thème du design Néon (Réglages → Fond), lu aussi par ses DesignColors. Sombre par défaut. */
object NeonTheme {
    var clair by mutableStateOf(false)
}

/** Choix du fond du design Néon (Réglages → Fond). */
@Composable
fun FondNeonDialog(clair: Boolean, onChoose: (Boolean) -> Unit, onDismiss: () -> Unit) {
    MenuDialog(
        title = "Fond de l'accueil",
        subtitle = "Design Néon : fond sombre ou clair",
        actions = listOf(
            MenuAction((if (!clair) "✓ " else "") + "Sombre") { onChoose(false) },
            MenuAction((if (clair) "✓ " else "") + "Clair") { onChoose(true) },
        ),
        onDismiss = onDismiss,
    )
}

/** Cyan du cadre de sélection, et sa teinte la plus claire (sommet de la pulsation). */
private val NEON_CYAN = Color(0xFF00C3E3)
private val NEON_CYAN_PULSE = Color(0xFF7DE8F8)

/** Couleurs du thème choisi. Les boutons ronds gardent leurs couleurs vives dans les deux. */
private class PaletteNeon(val clair: Boolean) {
    val fond = if (clair) Color(0xFFEBEBEB) else Color(0xFF2D2D2D)
    val texte = if (clair) Color(0xFF2D2D2D) else Color.White
    val texteDiscret = if (clair) Color(0xFF6B6B6B) else Color(0xFFB0B0B0)
    /** Cyan des textes : un peu plus soutenu sur fond clair, pour rester lisible. */
    val cyanTexte = if (clair) Color(0xFF00A9C8) else NEON_CYAN
    val tuile = if (clair) Color.White else Color(0xFF3A3A3A)
    val filet = if (clair) Color(0xFFC9C9C9) else Color(0xFF4B4B4B)
    val panneau = if (clair) Color(0xFFF6F6F6) else Color(0xFF262626)
}

// Dimensions (unités .u, écran de référence 960 × 540)
private const val N_MARGE = 56
private const val N_TUILE = 196
/** Marge autour de la tuile, où se dessine le cadre (épaisseur + écart). */
private const val N_CADRE = 7
private const val N_ITEM = N_TUILE + 2 * N_CADRE
private const val N_ECART = 10
private const val N_NOM = 28
private const val N_ROND = 58
private const val N_PANNEAU = 262

/** Où est le focus (pour les aides de touches en bas). */
private enum class ZoneNeon { APP, TUILE, BOUTON, MODULES }

/** Un élément de la rangée de tuiles. */
private sealed class ElementNeon(val cle: String, val etiquette: String) {
    class Aide : ElementNeon("aide", "aide")
    class App(val app: AppInfo, favori: Boolean) :
        ElementNeon((if (favori) "fav:" else "app:") + app.packageName, (if (favori) "favori:" else "app:") + app.packageName)
    class Biblio : ElementNeon("bibliotheque", "biblio")
}

/** Un bouton rond : pictogramme blanc sur disque de couleur vive. */
private class BoutonNeon(
    val cle: String,
    val icone: ImageVector,
    val label: String,
    val couleur: Color,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val estReglages: Boolean = false,
)

/**
 * Navigation verticale déterministe entre la rangée de tuiles et les boutons ronds.
 * Positions mesurées dans le repère de la fenêtre (px).
 */
private class NavNeon(val rangee: LazyListState) {
    val tuiles = HashMap<String, FocusRequester>()
    val centresTuiles = HashMap<String, Float>()
    val boutons = LinkedHashMap<String, FocusRequester>()
    val centresBoutons = HashMap<String, Float>()
    /** Dernière tuile sélectionnée : ▲ depuis les boutons y revient. */
    var tuile: String? = null
    var bouton: String? = null
    var gaucheRangee = 0f
    var largeurFenetre = Float.MAX_VALUE
    var zone by mutableStateOf(ZoneNeon.APP)

    /** ▼ : bouton rond dont le centre est le plus proche de celui de la tuile sélectionnée. */
    fun versBoutons(): Boolean {
        val x = tuile?.let { centresTuiles[it] } ?: return false
        val cible = centresBoutons.entries.filter { boutons.containsKey(it.key) }
            .minByOrNull { abs(it.value - x) }?.key ?: return false
        return runCatching { boutons[cible]!!.requestFocus() }.isSuccess
    }

    /** ▲ : la tuile d'où l'on venait si elle est à l'écran, sinon la tuile visible la plus alignée. */
    fun versTuiles(): Boolean {
        val infos = rangee.layoutInfo
        fun gauche(i: androidx.compose.foundation.lazy.LazyListItemInfo) = gaucheRangee + i.offset - infos.viewportStartOffset
        val ecranG = maxOf(gaucheRangee, 0f)
        val ecranD = minOf(gaucheRangee + infos.viewportSize.width, largeurFenetre)
        val visibles = infos.visibleItemsInfo.filter { (it.key as? String)?.let(tuiles::containsKey) == true }
        val enPartie = visibles.filter { val c = gauche(it) + it.size / 2f; c > ecranG && c < ecranD }
        val precedente = tuile?.takeIf { cle -> enPartie.any { it.key == cle } }
        val cible = precedente ?: run {
            val x = bouton?.let { centresBoutons[it] } ?: return false
            val entieres = visibles.filter { gauche(it) >= ecranG - 1f && gauche(it) + it.size <= ecranD + 1f }
            entieres.ifEmpty { enPartie }.minByOrNull { abs(gauche(it) + it.size / 2f - x) }?.key as? String
        } ?: return false
        return runCatching { tuiles[cible]!!.requestFocus() }.isSuccess
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeNeon(ctx: HomeContext) {
    val ui = ctx.ui
    val actions = ctx.actions
    val pal = remember(NeonTheme.clair) { PaletteNeon(NeonTheme.clair) }
    val nav = remember(ctx.rangeeFavoris) { NavNeon(ctx.rangeeFavoris) }
    val scope = rememberCoroutineScope()

    // --- Panneau des modules (glisse du bas)
    var modulesOuverts by remember { mutableStateOf(false) }
    val entreeModules = remember { FocusRequester() }
    var focusDansModules by remember { mutableStateOf(false) }
    // « Toutes les apps » ouverte par le bouton rond : le focus y revient à la fermeture
    var biblioParBouton by remember { mutableStateOf(false) }

    val fermerModules: () -> Unit = {
        modulesOuverts = false
        runCatching { nav.boutons["modules"]?.requestFocus() }
    }

    // --- Éléments de la rangée : favoris (ou carte d'aide), « Mes apps », « Toutes les apps »
    val elements = buildList<ElementNeon> {
        if (ui.loaded && ui.favorites.isEmpty()) add(ElementNeon.Aide())
        ui.favorites.forEach { add(ElementNeon.App(it, favori = true)) }
        ui.homeApps.forEach { add(ElementNeon.App(it, favori = false)) }
        add(ElementNeon.Biblio())
    }
    val premiere = elements.first().cle
    val derniere = elements.last().cle

    // --- Boutons ronds (Sources absent sur Fire TV : quickActions() est déjà filtré)
    val boutons = buildList {
        for (qa in ctx.quickActions()) {
            if (qa.estReglages) {
                add(BoutonNeon("modules", NeonIcones.Modules, "Modules", Color(0xFFF5892B), {
                    modulesOuverts = true
                    scope.launch {
                        withFrameNanos {}
                        runCatching { entreeModules.requestFocus() }
                        withFrameNanos {}
                        withFrameNanos {}
                        // Aucun module focalisable : on ne laisse pas le panneau ouvert sans focus
                        if (!focusDansModules) modulesOuverts = false
                    }
                }))
                add(BoutonNeon("biblio", ProjoIcons.Apps, "Toutes les apps", Color(0xFF34B65A), {
                    biblioParBouton = true
                    actions.openLibrary()
                }))
                add(BoutonNeon("reglages", qa.icon, qa.label, Color(0xFF5E636B), qa.onClick, qa.onLongClick, estReglages = true))
            } else {
                val (cle, couleur) = when (qa.icon) {
                    ProjoIcons.Sources -> "sources" to Color(0xFFE5403B)
                    ProjoIcons.Wifi -> "wifi" to Color(0xFF2F7FEA)
                    ProjoIcons.Bluetooth -> "bluetooth" to Color(0xFF8C55E6)
                    else -> qa.label to Color(0xFF5E636B)
                }
                add(BoutonNeon(cle, qa.icon, qa.label, couleur, qa.onClick, qa.onLongClick))
            }
        }
        if (ctx.majDisponible) {
            add(BoutonNeon("maj", ProjoIcons.MiseAJour, "Mise à jour", Color(0xFFE9A800), actions.update))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(pal.fond)
    ) {
        // Relais de focusBiblio : « Toutes les apps » n'est composée que si la rangée l'affiche.
        // Il n'est jamais atteint au D-pad (les bords de rangées consomment ◀ ▶ ▲ ▼).
        Box(
            Modifier
                .size(1.dp)
                .focusRequester(ctx.focusBiblio)
                .onFocusChanged { f ->
                    if (!f.isFocused) return@onFocusChanged
                    scope.launch {
                        if (biblioParBouton) {
                            runCatching { nav.boutons["biblio"]?.requestFocus() }
                            return@launch
                        }
                        val visible = ctx.rangeeFavoris.layoutInfo.visibleItemsInfo.any { it.key == "bibliotheque" }
                        if (!visible) {
                            ctx.rangeeFavoris.scrollToItem(elements.lastIndex)
                            withFrameNanos {}
                        }
                        runCatching { nav.tuiles["bibliotheque"]?.requestFocus() }
                    }
                }
                .focusable()
        )

        Column(Modifier.fillMaxSize()) {
            EnTeteNeon(ctx, pal)
            Spacer(Modifier.height(30.u))

            // --- Rangée de tuiles carrées (nom de la tuile sélectionnée au-dessus, en cyan) ;
            // elle glisse en gardant la tuile sélectionnée et son cadre à distance des bords
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberDefilementNeon(N_MARGE.u)) {
            LazyRow(
                state = ctx.rangeeFavoris,
                modifier = Modifier
                    .testTag("rangee:favoris")
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        nav.gaucheRangee = it.positionInRoot().x
                        nav.largeurFenetre = it.findRootCoordinates().size.width.toFloat()
                    },
                contentPadding = PaddingValues(horizontal = N_MARGE.u),
                horizontalArrangement = Arrangement.spacedBy(N_ECART.u),
            ) {
                items(elements, key = { it.cle }) { e ->
                    val premierFocus = if (e.cle == premiere) Modifier.focusRequester(ctx.premierFocus) else Modifier
                    val touches: (KeyEvent) -> Boolean = { k -> toucheTuile(k, nav, e.cle == premiere, e.cle == derniere) }
                    val cible = rememberCibleNeon(nav, e.cle)
                    when (e) {
                        is ElementNeon.Aide -> TuileNeon(
                            titre = "Aucun favori", pal = pal,
                            modifier = Modifier.testTag(e.etiquette),
                            focus = cible.then(premierFocus), touches = touches,
                            onFocus = { ctx.sansSelection(); nav.zone = ZoneNeon.TUILE },
                            onClick = {},
                        ) {
                            Text(
                                "Aucun favori.\nMaintiens OK sur une app pour l'ajouter.",
                                color = pal.texteDiscret, fontSize = 13.us, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.u),
                            )
                        }
                        is ElementNeon.App -> TuileAppNeon(ctx, e.app, pal, e.etiquette, cible.then(premierFocus), touches, nav)
                        is ElementNeon.Biblio -> TuileNeon(
                            titre = "Toutes les apps", pal = pal,
                            modifier = Modifier.testTag(e.etiquette),
                            focus = cible, touches = touches,
                            onFocus = { ctx.sansSelection(); nav.zone = ZoneNeon.TUILE },
                            onClick = { biblioParBouton = false; actions.openLibrary() },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(64.u).background(Color(0xFF34B65A), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(ProjoIcons.Apps, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.u))
                                }
                                Spacer(Modifier.height(12.u))
                                Text("Toutes les apps", color = pal.texte, fontSize = 14.us, fontWeight = FontWeight.Medium)
                                Text("${ui.allApps.size} apps", color = pal.texteDiscret, fontSize = 12.us)
                            }
                        }
                    }
                }
            }
            }
            // Rangée « Mes apps » du contrat : vide ici (tout est dans la rangée de tuiles),
            // mais mise en page pour que Maison et le focus initial n'attendent pas
            LazyRow(state = ctx.rangeeApps, modifier = Modifier.size(0.dp)) {}

            Spacer(Modifier.height(22.u))

            // --- Boutons ronds de couleur
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(22.u, Alignment.CenterHorizontally),
            ) {
                boutons.forEachIndexed { i, b ->
                    BoutonRondNeon(
                        b, pal, nav,
                        premier = i == 0, dernier = i == boutons.lastIndex,
                        modifier = if (b.estReglages) Modifier.focusRequester(ctx.focusReglages) else Modifier,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            BarreAidesNeon(pal, nav.zone)
        }

        // --- Panneau des modules : voile, puis feuille qui glisse du bas
        val progres by animateFloatAsState(
            if (modulesOuverts) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "modules",
        )
        if (progres > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (if (pal.clair) 0.22f else 0.4f) * progres)))
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(N_PANNEAU.u)
                .graphicsLayer {
                    translationY = (1f - progres) * size.height
                    // Fermé : rien à dessiner (il reste composé pour garder le focus joignable)
                    alpha = if (progres <= 0.001f) 0f else 1f
                }
                .background(pal.panneau, RoundedCornerShape(topStart = 22.u, topEnd = 22.u))
                .onFocusChanged {
                    focusDansModules = it.hasFocus
                    if (it.hasFocus) {
                        modulesOuverts = true
                        nav.zone = ZoneNeon.MODULES
                    } else if (modulesOuverts) {
                        modulesOuverts = false
                    }
                }
                .padding(horizontal = N_MARGE.u, vertical = 18.u),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.u).background(Color(0xFFF5892B), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(NeonIcones.Modules, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.u))
                }
                Spacer(Modifier.width(12.u))
                Text("Modules", color = pal.texte, fontSize = 18.us, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                ToucheNeon(GlypheNeon.RETOUR, "Fermer", pal)
            }
            Spacer(Modifier.height(14.u))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                NbaPanel(
                    data = ctx.nbaData,
                    erreur = ctx.nbaErreur,
                    repo = ctx.nbaRepo,
                    onExit = fermerModules,
                    style = styleModulesNeon(pal),
                    entree = entreeModules,
                )
            }
        }
    }
}

/** Style de la zone de modules : deux modules MOYEN côte à côte dans le panneau. */
private fun styleModulesNeon(pal: PaletteNeon) = if (pal.clair) NbaStyle(
    largeur = 420, rayon = 10,
    fond = Color.White, bordure = Color(0x14000000),
    texte = Color(0xFF2D2D2D), texteDiscret = Color(0xFF6B6B6B),
    ongletActif = Color(0x14000000),
    focusFond = NEON_CYAN, focusTexte = Color(0xFF0B2328),
    pastilleFond = Color(0xFFEDEDED),
    echelle = 1.05f, zoneLargeur = 680, zoneHauteur = 170,
) else NbaStyle(
    largeur = 420, rayon = 10,
    fond = Color(0xFF353535), bordure = Color.White.copy(alpha = 0.08f),
    texte = Color.White, texteDiscret = Color.White.copy(alpha = 0.62f),
    ongletActif = Color.White.copy(alpha = 0.14f),
    focusFond = NEON_CYAN, focusTexte = Color(0xFF0B2328),
    echelle = 1.05f, zoneLargeur = 680, zoneHauteur = 170,
)

// ------------------------------------------------------------------ navigation

/** Défilement de la rangée : la tuile sélectionnée reste à [marge] des bords de l'écran. */
@OptIn(ExperimentalFoundationApi::class)
private class DefilementNeon(private val marge: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val avant = offset - marge
        val apres = offset + size - (containerSize - marge)
        return when {
            size > containerSize - 2 * marge -> avant
            avant >= 0f && apres <= 0f -> 0f
            avant < 0f -> avant
            else -> apres
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberDefilementNeon(marge: Dp): BringIntoViewSpec {
    val densite = LocalDensity.current
    return remember(densite, marge) { with(densite) { DefilementNeon(marge.toPx()) } }
}

/** Touches d'une tuile : ▼ vers les boutons ronds ; rien au-dessus ; bords de rangée bloqués. */
private fun toucheTuile(k: KeyEvent, nav: NavNeon, premiere: Boolean, derniere: Boolean): Boolean {
    if (k.type != KeyEventType.KeyDown) return false
    return when (k.key) {
        Key.DirectionDown -> { nav.versBoutons(); true }
        Key.DirectionUp -> true
        Key.DirectionLeft -> premiere
        Key.DirectionRight -> derniere
        else -> false
    }
}

/** Rend une tuile joignable (▲ depuis les boutons), mesure son centre et note qu'elle est sélectionnée. */
@Composable
private fun rememberCibleNeon(nav: NavNeon, cle: String): Modifier {
    val fr = remember { FocusRequester() }
    DisposableEffect(nav, cle) {
        nav.tuiles[cle] = fr
        onDispose {
            if (nav.tuiles[cle] === fr) {
                nav.tuiles.remove(cle)
                nav.centresTuiles.remove(cle)
            }
        }
    }
    return remember(nav, cle, fr) {
        Modifier
            .focusRequester(fr)
            .onGloballyPositioned { nav.centresTuiles[cle] = it.positionInRoot().x + it.size.width / 2f }
            .onFocusChanged { if (it.isFocused) nav.tuile = cle }
    }
}

// ------------------------------------------------------------------ tuiles

/** Tuile d'une app : visuel carré officiel, sinon initiale sur sa couleur. */
@Composable
private fun TuileAppNeon(
    ctx: HomeContext,
    app: AppInfo,
    pal: PaletteNeon,
    etiquette: String,
    focus: Modifier,
    touches: (KeyEvent) -> Boolean,
    nav: NavNeon,
) {
    val visuel = rememberAppVisual(ctx.ui.iconSourceOf(app.packageName), ctx.icons, FormatVisuel.CARRE)
    val couleur = visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    TuileNeon(
        titre = app.label, pal = pal,
        modifier = Modifier.testTag(etiquette),
        focus = focus,
        touches = { k ->
            // Touche Menu : même menu que l'appui long
            if (k.key == Key.Menu) {
                if (k.type == KeyEventType.KeyUp) ctx.actions.openMenu(app)
                true
            } else touches(k)
        },
        onFocus = { ctx.surFocus(app, couleur); nav.zone = ZoneNeon.APP },
        onClick = { ctx.actions.launch(app) },
        onLongClick = { ctx.actions.openMenu(app) },
    ) {
        val art = visuel?.art
        when {
            app.isSpecial -> {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(lerp(couleur, Color.White, 0.1f), lerp(couleur, Color.Black, 0.55f)))
                    )
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(ProjoIcons.Sources, contentDescription = app.label, tint = Color.White, modifier = Modifier.size(56.u))
                    Spacer(Modifier.height(10.u))
                    Text(app.label, color = Color.White, fontSize = 13.us, fontWeight = FontWeight.Medium)
                }
            }
            art != null -> ImageApp(visuel, app.label, Modifier.fillMaxSize())
            else -> {
                Box(Modifier.fillMaxSize().background(lerp(couleur, Color.Black, 0.35f)))
                Text(app.label.take(1).uppercase(), color = Color.White, fontSize = 56.us, fontWeight = FontWeight.Light)
            }
        }
    }
}

/**
 * Tuile carrée : sélectionnée, un cadre cyan épais l'entoure (pulsation douce) et son nom
 * apparaît au-dessus, en cyan. Pas d'agrandissement : la rangée reste parfaitement alignée.
 */
@Composable
private fun TuileNeon(
    titre: String,
    pal: PaletteNeon,
    modifier: Modifier,
    focus: Modifier,
    touches: (KeyEvent) -> Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    contenu: @Composable BoxScope.() -> Unit,
) {
    var selection by remember { mutableStateOf(false) }
    val forme = RoundedCornerShape(3.u)
    Column(modifier.width(N_ITEM.u)) {
        Box(Modifier.fillMaxWidth().height(N_NOM.u), contentAlignment = Alignment.BottomStart) {
            if (selection) {
                Text(
                    titre, color = pal.cyanTexte, fontSize = 16.us, fontWeight = FontWeight.Medium,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .padding(start = N_CADRE.u, bottom = 4.u)
                        .wrapContentWidth(Alignment.Start, unbounded = true),
                )
            }
        }
        Box(Modifier.size(N_ITEM.u).cadreNeon(selection, 7.u), contentAlignment = Alignment.Center) {
            Surface(
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = Modifier
                    .then(if (pal.clair) Modifier.shadow(3.u, forme, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x40000000)) else Modifier)
                    .size(N_TUILE.u)
                    .then(focus)
                    .onFocusChanged {
                        selection = it.isFocused
                        if (it.isFocused) onFocus()
                    }
                    .onPreviewKeyEvent(touches),
                shape = ClickableSurfaceDefaults.shape(forme),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = pal.tuile, focusedContainerColor = pal.tuile, pressedContainerColor = pal.tuile,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = contenu)
            }
        }
    }
}

/** Cadre de sélection cyan, épais, qui pulse doucement (dessiné seulement quand [actif]). */
@Composable
private fun Modifier.cadreNeon(actif: Boolean, rayon: Dp, epaisseur: Dp = 4.u): Modifier {
    if (!actif) return this
    val transition = rememberInfiniteTransition(label = "pulsation")
    val p = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cadre",
    )
    return drawBehind {
        val e = epaisseur.toPx()
        drawRoundRect(
            color = lerp(NEON_CYAN, NEON_CYAN_PULSE, p.value * 0.8f),
            topLeft = Offset(e / 2f, e / 2f),
            size = Size(size.width - e, size.height - e),
            cornerRadius = CornerRadius(rayon.toPx()),
            style = Stroke(width = e),
        )
    }
}

// ------------------------------------------------------------------ boutons ronds

@Composable
private fun BoutonRondNeon(
    b: BoutonNeon,
    pal: PaletteNeon,
    nav: NavNeon,
    premier: Boolean,
    dernier: Boolean,
    modifier: Modifier,
) {
    var selection by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    DisposableEffect(nav, b.cle) {
        nav.boutons[b.cle] = fr
        onDispose {
            if (nav.boutons[b.cle] === fr) {
                nav.boutons.remove(b.cle)
                nav.centresBoutons.remove(b.cle)
            }
        }
    }
    val cadre = N_ROND + 2 * N_CADRE
    Column(
        Modifier.testTag("rapide:" + b.label).width(cadre.u),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(24.u), contentAlignment = Alignment.BottomCenter) {
            if (selection) {
                Text(
                    b.label, color = pal.cyanTexte, fontSize = 14.us, fontWeight = FontWeight.Medium,
                    maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(bottom = 2.u).wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
                )
            }
        }
        Box(Modifier.size(cadre.u).cadreNeon(selection, (cadre / 2).u), contentAlignment = Alignment.Center) {
            Surface(
                onClick = b.onClick,
                onLongClick = b.onLongClick,
                modifier = modifier
                    .then(if (pal.clair) Modifier.shadow(3.u, CircleShape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x40000000)) else Modifier)
                    .size(N_ROND.u)
                    .focusRequester(fr)
                    .onGloballyPositioned { nav.centresBoutons[b.cle] = it.positionInRoot().x + it.size.width / 2f }
                    .onFocusChanged {
                        selection = it.isFocused
                        if (it.isFocused) {
                            nav.bouton = b.cle
                            nav.zone = ZoneNeon.BOUTON
                        }
                    }
                    .onPreviewKeyEvent { k ->
                        k.type == KeyEventType.KeyDown && when (k.key) {
                            Key.DirectionUp -> { nav.versTuiles(); true }
                            Key.DirectionDown -> true
                            Key.DirectionLeft -> premier
                            Key.DirectionRight -> dernier
                            else -> false
                        }
                    },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = b.couleur, focusedContainerColor = b.couleur, pressedContainerColor = b.couleur,
                    contentColor = Color.White, focusedContentColor = Color.White, pressedContentColor = Color.White,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.94f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(b.icone, contentDescription = b.label, tint = Color.White, modifier = Modifier.size(28.u))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ en-tête et barre du bas

/** Avatar rond à gauche ; heure, Wi-Fi et éventuelle mise à jour à droite. */
@Composable
private fun EnTeteNeon(ctx: HomeContext, pal: PaletteNeon) {
    val maintenant = rememberMaintenant()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = N_MARGE.u, end = N_MARGE.u, top = 24.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar : initiale sur un rond coloré, liseré du fond
        Box(
            Modifier
                .size(50.u)
                .background(Color(0xFFFF7A45), CircleShape)
                .border(3.u, if (pal.clair) Color.White else Color(0xFF4A4A4A), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("P", color = Color.White, fontSize = 24.us, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        if (ctx.majDisponible) {
            Box(Modifier.size(9.u).background(Color(0xFFE9A800), CircleShape))
            Spacer(Modifier.width(16.u))
        }
        Text(heureTexte(maintenant), color = pal.texte, fontSize = 20.us, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(16.u))
        val horsLigne = ctx.wifiLabel == "Hors ligne"
        Icon(
            ProjoIcons.Wifi, contentDescription = ctx.wifiLabel,
            tint = if (horsLigne) pal.texteDiscret.copy(alpha = 0.5f) else pal.texte,
            modifier = Modifier.size(22.u),
        )
    }
}

/** Glyphes des aides de touches, dessinés dans un rond. */
private enum class GlypheNeon { OK, MENU, RETOUR }

/** Fine barre du bas : date à gauche, aides de touches à droite (selon l'élément sélectionné). */
@Composable
private fun BarreAidesNeon(pal: PaletteNeon, zone: ZoneNeon) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.u)) {
        Box(Modifier.fillMaxWidth().height(1.u).background(pal.filet))
        Row(
            Modifier.fillMaxWidth().height(46.u).padding(horizontal = 28.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                dateTexte(rememberMaintenant()).replaceFirstChar { it.uppercase() },
                color = pal.texteDiscret, fontSize = 13.us,
            )
            Spacer(Modifier.weight(1f))
            when (zone) {
                ZoneNeon.APP -> {
                    ToucheNeon(GlypheNeon.MENU, "Options", pal)
                    Spacer(Modifier.width(22.u))
                    ToucheNeon(GlypheNeon.OK, "Ouvrir", pal)
                }
                ZoneNeon.TUILE, ZoneNeon.BOUTON -> ToucheNeon(GlypheNeon.OK, "Ouvrir", pal)
                ZoneNeon.MODULES -> ToucheNeon(GlypheNeon.RETOUR, "Fermer", pal)
            }
        }
    }
}

/** Aide de touche : glyphe dans un rond plein, puis son action. */
@Composable
private fun ToucheNeon(glyphe: GlypheNeon, texte: String, pal: PaletteNeon) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.u).background(pal.texte, CircleShape), contentAlignment = Alignment.Center) {
            when (glyphe) {
                GlypheNeon.OK -> Text("OK", color = pal.fond, fontSize = 8.us, fontWeight = FontWeight.Bold)
                else -> Canvas(Modifier.size(12.u)) {
                    val w = size.width
                    val trait = Stroke(width = w * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    if (glyphe == GlypheNeon.MENU) {
                        for (y in listOf(0.22f, 0.5f, 0.78f)) {
                            drawLine(pal.fond, Offset(w * 0.12f, w * y), Offset(w * 0.88f, w * y), strokeWidth = w * 0.15f, cap = StrokeCap.Round)
                        }
                    } else {
                        // Flèche de retour : crochet vers la gauche
                        val p = Path().apply {
                            moveTo(w * 0.38f, w * 0.12f)
                            lineTo(w * 0.12f, w * 0.38f)
                            lineTo(w * 0.38f, w * 0.64f)
                            moveTo(w * 0.12f, w * 0.38f)
                            lineTo(w * 0.62f, w * 0.38f)
                            cubicTo(w * 0.95f, w * 0.38f, w * 0.95f, w * 0.88f, w * 0.62f, w * 0.88f)
                            lineTo(w * 0.42f, w * 0.88f)
                        }
                        drawPath(p, pal.fond, style = trait)
                    }
                }
            }
        }
        Spacer(Modifier.width(8.u))
        Text(texte, color = pal.texte, fontSize = 13.us)
    }
}

/** Pictogrammes propres au design (24 × 24, blancs). */
private object NeonIcones {
    /** Modules : quatre blocs, dont un en losange. */
    val Modules: ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M13,13v8h8v-8h-8zM3,21h8v-8H3v8zM3,3v8h8V3H3zM16.66,1.69L11,7.34 16.66,13l5.66,-5.66 -5.66,-5.65z"
        ).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.White),
    ).build()
}
