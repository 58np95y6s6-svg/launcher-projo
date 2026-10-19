package fr.projo.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.IconLoader
import java.util.Locale

// ------------------------------------------------------------------ palette « Verre »
// (couleurs déclarées en premier : les valeurs plus bas s'en servent à l'initialisation)

/** Fond de base du papier peint, sous les taches de couleur. */
private val VerreBase = Color(0xFF0B0824)
/** Teinte sombre posée sous le blanc du verre (lisibilité sur les zones vives). */
private val VerreTeinte = Color(0x4D161230)
/** Texte sur une surface blanche (bouton ou ligne sélectionnés). */
private val VerreEncre = Color(0xFF111111)
/** Bleu système (bouton « Mise à jour »). */
private val VerreAccent = Color(0xFF0A84FF)
/** Halo rosé autour de l'élément sélectionné. */
private val VerreLueur = Color(0xFFFFE3F0).copy(alpha = 0.55f)
/** Ombre portée violet très sombre (élément soulevé). */
private val VerreOmbre = Color(0xFF06031C)

/** Liseré lumineux des cartes : vif en haut à gauche, presque éteint au milieu. */
private val VerreLisereCarte: Brush = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.50f),
    0.4f to Color.White.copy(alpha = 0.08f),
    0.7f to Color.White.copy(alpha = 0.04f),
    1f to Color.White.copy(alpha = 0.22f),
)
/** Même liseré pour les icônes rondes (pinceau séparé : autre taille, pas de recalcul). */
private val VerreLisereRond: Brush = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.55f),
    0.45f to Color.White.copy(alpha = 0.06f),
    1f to Color.White.copy(alpha = 0.20f),
)

/** Tache de couleur du papier peint : position et rayon en fraction de la largeur/hauteur. */
private class VerreTache(val couleur: Color, val x: Float, val y: Float, val rayon: Float)

/** Taches du papier peint (maquette 1920 × 1080, rayons rapportés à la largeur). */
private val VerreTaches = listOf(
    VerreTache(Color(0xFF463CFF).copy(alpha = 0.58f), 0.188f, 0.213f, 0.406f), // indigo derrière l'horloge
    VerreTache(Color(0xFF9646FF).copy(alpha = 0.40f), 0.469f, 0.083f, 0.292f), // violet en haut
    VerreTache(Color(0xFFFF3791).copy(alpha = 0.50f), 0.833f, 0.204f, 0.365f), // magenta derrière la NBA
    VerreTache(Color(0xFFFF7D2D).copy(alpha = 0.44f), 0.917f, 0.833f, 0.365f), // ambre en bas à droite
    VerreTache(Color(0xFFC83CC8).copy(alpha = 0.16f), 0.573f, 0.593f, 0.292f), // orchidée au centre
    VerreTache(Color(0xFF00AAD2).copy(alpha = 0.28f), 0.063f, 0.907f, 0.313f), // bleu canard en bas à gauche
)

/** Fenêtre NBA : le verre (dégradé, reflet, liseré) est dessiné derrière par verreVitre. */
private val VerreNba = NbaStyle(
    largeur = 340,
    rayon = 20,
    fond = Color.White.copy(alpha = 0.04f),
    bordure = Color.Transparent,
    texte = Color.White,
    texteDiscret = Color.White.copy(alpha = 0.66f),
    ongletActif = Color.White.copy(alpha = 0.24f),
    focusFond = Color.White,
    focusTexte = VerreEncre,
    pastilleFond = Color.White.copy(alpha = 0.92f),
    echelle = 1.2f,
)

// Dimensions (unités .u, écran de 960 × 540)
private const val VERRE_CARTE_L = 146 // favoris 5:3 : 5 cartes entières visibles
private const val VERRE_CARTE_H = 88
private const val VERRE_CARTE_RAYON = 18
private const val VERRE_ICONE = 62
/** Agrandissement de l'élément sélectionné. */
private const val VERRE_ECHELLE = 1.12f

/**
 * Design « Verre » (visionOS / tvOS) :
 *  - papier peint aux dégradés vifs (indigo, magenta, ambre), dessiné une seule fois ;
 *  - horloge façon écran verrouillé : date, très grande heure, salutation ;
 *  - scores NBA dans une fenêtre de verre flottante, en haut à droite ;
 *  - boutons rapides dans un « ornement » vertical à gauche, centré sur les favoris
 *    (le nom du bouton sélectionné s'affiche à la place du nom de l'app) ;
 *  - favoris en cartes 5:3 qui se soulèvent au focus (liseré blanc, halo, reflet) ;
 *  - « Mes apps » en icônes rondes dans une fenêtre de verre, puis « Toutes les apps ».
 */
@Composable
fun HomeVerre(ctx: HomeContext) {
    val ui = ctx.ui
    val actions = ctx.actions
    // Nom du bouton de l'ornement sélectionné (null : on affiche le nom de l'app)
    var libelleBouton by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        VerreFond()

        Column(Modifier.fillMaxSize()) {
            // --- Haut : horloge (la fenêtre NBA flotte à droite, par-dessus)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                VerreHorloge(Modifier.padding(start = 108.u, top = 28.u))
            }

            // --- Nom de l'app (ou du bouton) sélectionné, en fondu enchaîné
            AnimatedContent(
                targetState = libelleBouton ?: ctx.selection?.label.orEmpty(),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                contentAlignment = Alignment.CenterStart,
                label = "titre",
                modifier = Modifier.padding(start = 108.u).height(36.u).widthIn(max = 410.u),
            ) { titre ->
                Text(
                    titre, color = Color.White, fontSize = 30.us, fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).us, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            // --- Ornement (boutons rapides) + rangée « Favoris »
            Row(Modifier.fillMaxWidth().height(136.u)) {
                Box(Modifier.width(84.u).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    VerreOrnement(
                        ctx = ctx,
                        surLibelle = { libelle, focus ->
                            if (focus) {
                                libelleBouton = libelle
                            } else if (libelleBouton == libelle) {
                                libelleBouton = null
                            }
                        },
                        // Plus haut que la rangée : il déborde au-dessus et en dessous, centré sur les cartes
                        modifier = Modifier.padding(start = 30.u).wrapContentHeight(unbounded = true),
                    )
                }
                LazyRow(
                    state = ctx.rangeeFavoris,
                    modifier = Modifier.weight(1f).fillMaxHeight().fadingEdges(ctx.rangeeFavoris, 48.u),
                    // Marges hautes/basses : place pour la levée, le halo et l'ombre (la rangée découpe)
                    contentPadding = PaddingValues(start = 24.u, end = 40.u, top = 24.u, bottom = 24.u),
                    horizontalArrangement = Arrangement.spacedBy(18.u),
                ) {
                    if (ui.loaded && ui.favorites.isEmpty()) {
                        item(key = "aide") {
                            VerreAide(
                                onFocused = ctx.sansSelection,
                                modifier = Modifier.focusRequester(ctx.premierFocus),
                            )
                        }
                    }
                    itemsIndexed(ui.favorites, key = { _, a -> "fav:" + a.packageName }) { i, app ->
                        VerreCarte(
                            app = app,
                            icons = ctx.icons,
                            iconPackage = ui.iconSourceOf(app.packageName),
                            onFocused = ctx.surFocus,
                            onClick = { actions.launch(app) },
                            onMenu = { actions.openMenu(app) },
                            modifier = if (i == 0) Modifier.focusRequester(ctx.premierFocus) else Modifier,
                        )
                    }
                }
            }

            // --- « Mes apps » : icônes rondes dans une fenêtre de verre
            Spacer(Modifier.height(2.u))
            VerreMesApps(ctx, Modifier.padding(start = 96.u, end = 30.u).fillMaxWidth().height(130.u))
            Spacer(Modifier.height(26.u))
        }

        // --- Fenêtre NBA flottante (même rayon que le bandeau, agrandi par son échelle)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 30.u, end = 30.u)
                .verreVitre(VerreNba.rayon.u * VerreNba.echelle, 1.u)
        ) {
            NbaPanel(
                data = ctx.nbaData,
                erreur = ctx.nbaErreur,
                repo = ctx.nbaRepo,
                onExit = ctx.sortieNba,
                style = VerreNba,
            )
        }
    }
}

// ------------------------------------------------------------------ fond et horloge

/**
 * Papier peint : grandes taches de couleur (dégradés radiaux additionnés) et voile sombre
 * en bas. Dessiné une fois dans un calque gardé en mémoire puis simplement recopié :
 * aucun dégradé recalculé pendant les animations.
 */
@Composable
private fun VerreFond() {
    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val taches = VerreTaches.map { t ->
                    Brush.radialGradient(
                        0f to t.couleur,
                        0.55f to t.couleur.copy(alpha = t.couleur.alpha * 0.45f),
                        1f to t.couleur.copy(alpha = 0f),
                        center = Offset(w * t.x, h * t.y),
                        radius = w * t.rayon,
                    )
                }
                // Voile sombre en bas : lisibilité de « Mes apps » dans une pièce sombre
                val vignette = Brush.verticalGradient(
                    0f to Color(0xFF060416).copy(alpha = 0.10f),
                    0.55f to Color.Transparent,
                    1f to Color(0xFF060416).copy(alpha = 0.62f),
                )
                onDrawBehind {
                    drawRect(VerreBase)
                    for (i in taches.indices) drawRect(taches[i], blendMode = BlendMode.Plus)
                    drawRect(vignette)
                }
            },
    )
}

/** Horloge façon écran verrouillé : date au-dessus, très grande heure, salutation dessous. */
@Composable
private fun VerreHorloge(modifier: Modifier = Modifier) {
    val t = rememberMaintenant()
    Column(modifier) {
        Text(
            dateTexte(t).replaceFirstChar { it.titlecase(Locale.FRENCH) },
            color = Color.White.copy(alpha = 0.88f), fontSize = 16.us, fontWeight = FontWeight.SemiBold,
        )
        Text(
            heureTexte(t),
            color = Color.White, fontSize = 92.us, fontWeight = FontWeight.SemiBold,
            letterSpacing = (-2).us, lineHeight = 86.us, maxLines = 1,
            modifier = Modifier.padding(top = 2.u).verreVibrance(),
        )
        Text(
            salutation(t) + ".",
            color = Color.White.copy(alpha = 0.72f), fontSize = 19.us, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.u),
        )
    }
}

// ------------------------------------------------------------------ ornement (boutons rapides)

/** Capsule de verre verticale : Mise à jour (si disponible), Sources, Wi-Fi, Bluetooth, Réglages. */
@Composable
private fun VerreOrnement(ctx: HomeContext, surLibelle: (String, Boolean) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .width(54.u)
            .verreVitre(27.u, 1.u)
            .padding(7.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (ctx.majDisponible) {
            VerreBouton(
                icone = ProjoIcons.MiseAJour,
                libelle = "Mise à jour",
                onClick = ctx.actions.update,
                onLongClick = null,
                surFocus = { surLibelle("Mise à jour", it) },
                fond = VerreAccent,
                contenuFocus = VerreAccent,
            )
        }
        for (a in ctx.quickActions()) {
            VerreBouton(
                icone = a.icon,
                libelle = a.label,
                onClick = a.onClick,
                onLongClick = a.onLongClick,
                surFocus = { surLibelle(a.label, it) },
                modifier = if (a.estReglages) Modifier.focusRequester(ctx.focusReglages) else Modifier,
            )
        }
    }
}

/** Bouton rond de l'ornement : verre clair, devient un disque blanc quand il est sélectionné. */
@Composable
private fun VerreBouton(
    icone: ImageVector,
    libelle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    surFocus: (Boolean) -> Unit,
    fond: Color = Color.White.copy(alpha = 0.14f),
    contenuFocus: Color = VerreEncre,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(40.u).onFocusChanged { surFocus(it.isFocused) },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fond,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = contenuFocus,
            pressedContainerColor = Color.White.copy(alpha = 0.8f),
            pressedContentColor = contenuFocus,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.u, Color.White.copy(alpha = 0.16f)), shape = CircleShape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = VerreOmbre, elevation = 8.u)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icone, contentDescription = libelle, modifier = Modifier.size(20.u))
        }
    }
}

// ------------------------------------------------------------------ favoris

/**
 * Carte d'un favori (5:3), façon tvOS. Sélectionnée : elle grandit et se soulève,
 * liseré blanc de 2 dp, halo rosé autour, ombre portée et éclat de lumière en diagonale.
 */
@Composable
private fun VerreCarte(
    app: AppInfo,
    icons: IconLoader,
    /** Paquet dont on affiche le visuel (logo éventuellement remplacé). */
    iconPackage: String,
    onFocused: (AppInfo, Color) -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = rememberAppVisual(iconPackage, icons)
    val couleur = visual?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    val rayon = VERRE_CARTE_RAYON.u
    val forme = RoundedCornerShape(rayon)
    var focus by remember { mutableStateOf(false) }
    // 0 → 1 : progression de la mise en avant (ressort doux, lu seulement au dessin)
    val p by animateFloatAsState(
        if (focus) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 400f), label = "levee",
    )
    val densite = LocalDensity.current
    val leveePx = with(densite) { 6.u.toPx() }
    val rayonPx = with(densite) { rayon.toPx() }
    val haloPx = with(densite) { 12.u.toPx() }

    Box(
        modifier
            .size(VERRE_CARTE_L.u, VERRE_CARTE_H.u)
            // La carte se soulève (le halo, dessiné derrière, suit le mouvement)
            .graphicsLayer { translationY = -leveePx * p }
            .drawWithCache {
                val halo = verreHalo(verreRectAgrandi(size, VERRE_ECHELLE), rayonPx * VERRE_ECHELLE, haloPx, VerreLueur)
                onDrawBehind { if (p > 0.01f) halo.dessiner(this, p) }
            },
    ) {
        Surface(
            onClick = onClick,
            onLongClick = onMenu,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged {
                    focus = it.isFocused
                    if (it.isFocused) onFocused(app, couleur)
                }
                // Touche « Menu » de la télécommande = même menu que l'appui long
                .onPreviewKeyEvent {
                    if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) { onMenu(); true } else false
                },
            shape = ClickableSurfaceDefaults.shape(forme),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                pressedContainerColor = Color.White.copy(alpha = 0.08f),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = VERRE_ECHELLE, pressedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.u, VerreLisereCarte), shape = forme),
                focusedBorder = Border(BorderStroke(2.u, Color.White), shape = forme),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = VerreOmbre, elevation = 12.u)),
        ) {
            Box(
                Modifier.fillMaxSize().clip(forme).verreReflet(rond = false) { p },
                contentAlignment = Alignment.Center,
            ) {
                val art = visual?.art
                when {
                    app.isSpecial -> {
                        // Tuile « Source HDMI » : dégradé + icône
                        val degrade = remember { verreDegradeHdmi() }
                        Box(Modifier.fillMaxSize().background(degrade))
                        Icon(
                            ProjoIcons.Sources, contentDescription = app.label, tint = Color.White,
                            modifier = Modifier.size(36.u),
                        )
                    }
                    art != null -> Image(
                        bitmap = art,
                        contentDescription = app.label,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> {
                        Box(Modifier.fillMaxSize().background(lerp(couleur, Color.Black, 0.5f)))
                        Text(
                            app.label.take(1).uppercase(), color = Color.White,
                            fontSize = 28.us, fontWeight = FontWeight.Light,
                        )
                    }
                }
            }
        }
    }
}

/** Carte d'aide quand il n'y a encore aucun favori. */
@Composable
private fun VerreAide(onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val forme = RoundedCornerShape(VERRE_CARTE_RAYON.u)
    Surface(
        onClick = {},
        modifier = modifier
            .size(300.u, VERRE_CARTE_H.u)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White.copy(alpha = 0.88f),
            focusedContainerColor = Color.White,
            focusedContentColor = VerreEncre,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.u, VerreLisereCarte), shape = forme),
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 20.u), contentAlignment = Alignment.Center) {
            Text(
                "Aucun favori.\nMaintiens OK sur une app pour l'ajouter.",
                fontSize = 13.us, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            )
        }
    }
}

// ------------------------------------------------------------------ mes apps

/** Fenêtre de verre « Mes apps » : icônes rondes, puis « Toutes les apps (N) ». */
@Composable
private fun VerreMesApps(ctx: HomeContext, modifier: Modifier) {
    val ui = ctx.ui
    val actions = ctx.actions
    Column(modifier.verreVitre(28.u, 1.u).padding(top = 8.u)) {
        Text(
            "Mes apps",
            color = Color.White.copy(alpha = 0.86f), fontSize = 13.us, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.u),
        )
        LazyRow(
            state = ctx.rangeeApps,
            modifier = Modifier.fillMaxWidth().weight(1f).fadingEdges(ctx.rangeeApps, 40.u),
            // Marge haute : place pour la levée et le halo de l'icône sélectionnée
            contentPadding = PaddingValues(start = 12.u, end = 24.u, top = 16.u, bottom = 8.u),
            horizontalArrangement = Arrangement.spacedBy(12.u),
        ) {
            itemsIndexed(ui.homeApps, key = { _, a -> "app:" + a.packageName }) { _, app ->
                VerreIcone(
                    app = app,
                    icons = ctx.icons,
                    iconPackage = ui.iconSourceOf(app.packageName),
                    onFocused = ctx.surFocus,
                    onClick = { actions.launch(app) },
                    onMenu = { actions.openMenu(app) },
                )
            }
            // Bibliothèque : toutes les apps installées
            item(key = "bibliotheque") {
                VerreBiblio(
                    nombre = ui.allApps.size,
                    onClick = actions.openLibrary,
                    onFocused = ctx.sansSelection,
                    modifier = Modifier.focusRequester(ctx.focusBiblio),
                )
            }
        }
    }
}

/** Icône ronde d'une app (façon visionOS) avec son nom dessous. */
@Composable
private fun VerreIcone(
    app: AppInfo,
    icons: IconLoader,
    iconPackage: String,
    onFocused: (AppInfo, Color) -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    // Visuel : seulement pour la couleur dominante transmise à l'accueil
    val visual = rememberAppVisual(iconPackage, icons)
    val icone = rememberAppIcon(iconPackage, icons)
    val couleur = visual?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    // Icône adaptative (carré plein) : découpée en rond ; ancienne icône : posée sur le verre
    val pleine = remember(icone) { icone != null && verrePleinCadre(icone) }
    var focus by remember { mutableStateOf(false) }

    Column(Modifier.width(80.u), horizontalAlignment = Alignment.CenterHorizontally) {
        VerreRond(
            onClick = onClick,
            onLongClick = onMenu,
            onFocus = { f ->
                focus = f
                if (f) onFocused(app, couleur)
            },
            fond = Color.White.copy(alpha = 0.18f),
        ) {
            when {
                app.isSpecial -> {
                    val degrade = remember { verreDegradeHdmi() }
                    Box(Modifier.fillMaxSize().background(degrade))
                    Icon(
                        ProjoIcons.Sources, contentDescription = app.label, tint = Color.White,
                        modifier = Modifier.size(26.u),
                    )
                }
                icone != null && pleine -> Image(
                    bitmap = icone,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize(),
                )
                icone != null -> Image(
                    bitmap = icone,
                    contentDescription = app.label,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.size(42.u),
                )
                else -> {
                    Box(Modifier.fillMaxSize().background(lerp(couleur, Color.Black, 0.35f)))
                    Text(
                        app.label.take(1).uppercase(), color = Color.White,
                        fontSize = 24.us, fontWeight = FontWeight.Light,
                    )
                }
            }
        }
        VerreLegende(app.label, focus)
    }
}

/** « Toutes les apps (N) » : rond de verre avec la grille d'apps, ouvre la bibliothèque. */
@Composable
private fun VerreBiblio(nombre: Int, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier) {
    var focus by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VerreRond(
            onClick = onClick,
            onLongClick = null,
            onFocus = { f ->
                focus = f
                if (f) onFocused()
            },
            fond = Color.White.copy(alpha = 0.16f),
            modifier = modifier,
        ) {
            Icon(ProjoIcons.Apps, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.u))
        }
        VerreLegende("Toutes les apps ($nombre)", focus)
    }
}

/** Nom sous une icône ronde (plus lumineux sur l'icône sélectionnée). */
@Composable
private fun VerreLegende(texte: String, focus: Boolean) {
    Text(
        texte,
        color = Color.White.copy(alpha = if (focus) 1f else 0.8f),
        fontSize = 11.us,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 7.u),
    )
}

/**
 * Rond focalisable (icône d'app, bibliothèque) : même mise en avant que les cartes
 * (agrandi, soulevé, liseré blanc, halo, éclat).
 */
@Composable
private fun VerreRond(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocus: (Boolean) -> Unit,
    fond: Color,
    modifier: Modifier = Modifier,
    contenu: @Composable BoxScope.() -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val p by animateFloatAsState(
        if (focus) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 400f), label = "rond",
    )
    val densite = LocalDensity.current
    val leveePx = with(densite) { 3.u.toPx() }
    val haloPx = with(densite) { 9.u.toPx() }

    Box(
        modifier
            .size(VERRE_ICONE.u)
            .graphicsLayer { translationY = -leveePx * p }
            .drawWithCache {
                val rect = verreRectAgrandi(size, VERRE_ECHELLE)
                val halo = verreHalo(rect, rect.width / 2f, haloPx, VerreLueur)
                onDrawBehind { if (p > 0.01f) halo.dessiner(this, p) }
            },
    ) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged {
                    focus = it.isFocused
                    onFocus(it.isFocused)
                }
                // Touche « Menu » de la télécommande = même menu que l'appui long
                .onPreviewKeyEvent {
                    if (onLongClick != null && it.key == Key.Menu && it.type == KeyEventType.KeyUp) {
                        onLongClick(); true
                    } else false
                },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = fond,
                focusedContainerColor = fond,
                pressedContainerColor = fond,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = VERRE_ECHELLE, pressedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.u, VerreLisereRond), shape = CircleShape),
                focusedBorder = Border(BorderStroke(2.u, Color.White), shape = CircleShape),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = VerreOmbre, elevation = 10.u)),
        ) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).verreReflet(rond = true) { p },
                contentAlignment = Alignment.Center,
                content = contenu,
            )
        }
    }
}

// ------------------------------------------------------------------ matière « verre »

/**
 * Verre dépoli (sans flou) : teinte sombre, voile blanc dégradé, reflet en haut à gauche
 * et liseré lumineux de [bordure], vif en haut à gauche et doux en bas à droite.
 */
private fun Modifier.verreVitre(rayon: Dp, bordure: Dp): Modifier = drawWithCache {
    val r = rayon.toPx().coerceAtMost(size.minDimension / 2f)
    val e = bordure.toPx()
    val coin = CornerRadius(r)
    val voile = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.07f)))
    val reflet = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
        center = Offset(size.width * 0.12f, 0f),
        radius = minOf(size.width * 0.6f, size.height * 1.1f).coerceAtLeast(1f),
    )
    val lisere = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.62f),
        0.22f to Color.White.copy(alpha = 0.18f),
        0.5f to Color.White.copy(alpha = 0.06f),
        0.78f to Color.White.copy(alpha = 0.08f),
        1f to Color.White.copy(alpha = 0.34f),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val origineBord = Offset(e / 2f, e / 2f)
    val tailleBord = Size(size.width - e, size.height - e)
    val coinBord = CornerRadius((r - e / 2f).coerceAtLeast(0f))
    val trait = Stroke(width = e)
    onDrawBehind {
        drawRoundRect(VerreTeinte, cornerRadius = coin)
        drawRoundRect(voile, cornerRadius = coin)
        drawRoundRect(reflet, cornerRadius = coin)
        drawRoundRect(lisere, topLeft = origineBord, size = tailleBord, cornerRadius = coinBord, style = trait)
    }
}

/** « Vibrance » de l'horloge : chiffres blancs qui s'adoucissent légèrement vers le bas. */
private fun Modifier.verreVibrance(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val masque = Brush.verticalGradient(
            0f to Color.White,
            0.35f to Color.White,
            0.85f to Color.White.copy(alpha = 0.78f),
        )
        onDrawWithContent {
            drawContent()
            drawRect(masque, blendMode = BlendMode.DstIn)
        }
    }

/**
 * Reflets posés sur une carte ou une icône : voile clair permanent en haut (verre),
 * puis, selon [progres] (0 → 1), éclat en haut à gauche et trait de lumière en diagonale.
 */
private fun Modifier.verreReflet(rond: Boolean, progres: () -> Float): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val voile = if (rond) {
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.30f), 1f to Color.Transparent,
            center = Offset(w / 2f, 0f), radius = w * 0.75f,
        )
    } else {
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.14f), 0.42f to Color.Transparent)
    }
    val eclat = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.45f), 1f to Color.Transparent,
        center = Offset(w * 0.18f, -h * 0.06f), radius = maxOf(w, h) * 0.45f,
    )
    val trait = Brush.linearGradient(
        0.30f to Color.Transparent,
        0.40f to Color.White.copy(alpha = 0.18f),
        0.47f to Color.White.copy(alpha = 0.05f),
        0.54f to Color.Transparent,
        start = Offset.Zero,
        end = Offset(w, h * 0.6f),
    )
    onDrawWithContent {
        drawContent()
        drawRect(voile)
        val p = progres()
        if (p > 0.01f) {
            drawRect(eclat, alpha = p)
            drawRect(trait, alpha = p)
        }
    }
}

/** Halo prêt à dessiner : pinceaux et rectangles préparés une seule fois (drawWithCache). */
private class VerreHaloDessin(
    private val pinceaux: List<Brush>,
    private val origines: List<Offset>,
    private val tailles: List<Size>,
) {
    fun dessiner(scope: DrawScope, alpha: Float) {
        for (i in pinceaux.indices) scope.drawRect(pinceaux[i], origines[i], tailles[i], alpha = alpha)
    }
}

/**
 * Halo doux autour d'un rectangle arrondi, sans flou (trop coûteux ici) : 4 coins en
 * dégradé radial + 4 bords en dégradé linéaire, de même profil donc sans couture.
 * Le centre n'est pas peint (caché par la carte). Un cercle = rayon égal à la demi-largeur.
 */
private fun verreHalo(rect: Rect, rayon: Float, largeur: Float, couleur: Color): VerreHaloDessin {
    val r = rayon.coerceIn(0f, minOf(rect.width, rect.height) / 2f)
    val ext = (r + largeur).coerceAtLeast(1f)
    // Profil : forte contre le bord, puis s'éteint doucement
    val profil = listOf(0f to 1f, 0.3f to 0.5f, 0.65f to 0.15f, 1f to 0f)
    fun arrets(debut: Float): Array<Pair<Float, Color>> = profil
        .map { (t, f) -> (debut + (1f - debut) * t) to couleur.copy(alpha = couleur.alpha * f) }
        .toTypedArray()

    val pinceaux = ArrayList<Brush>(8)
    val origines = ArrayList<Offset>(8)
    val tailles = ArrayList<Size>(8)
    fun ajouter(b: Brush, o: Offset, s: Size) {
        if (s.width <= 0f || s.height <= 0f) return
        pinceaux.add(b); origines.add(o); tailles.add(s)
    }

    // Coins : dégradé radial depuis le centre de l'arrondi
    val coin = Size(ext, ext)
    val debutCoin = r / ext
    ajouter(
        Brush.radialGradient(*arrets(debutCoin), center = Offset(rect.left + r, rect.top + r), radius = ext),
        Offset(rect.left - largeur, rect.top - largeur), coin,
    )
    ajouter(
        Brush.radialGradient(*arrets(debutCoin), center = Offset(rect.right - r, rect.top + r), radius = ext),
        Offset(rect.right - r, rect.top - largeur), coin,
    )
    ajouter(
        Brush.radialGradient(*arrets(debutCoin), center = Offset(rect.right - r, rect.bottom - r), radius = ext),
        Offset(rect.right - r, rect.bottom - r), coin,
    )
    ajouter(
        Brush.radialGradient(*arrets(debutCoin), center = Offset(rect.left + r, rect.bottom - r), radius = ext),
        Offset(rect.left - largeur, rect.bottom - r), coin,
    )

    // Bords droits (absents pour un cercle)
    val horizontal = Size(rect.width - 2f * r, largeur)
    val vertical = Size(largeur, rect.height - 2f * r)
    ajouter(
        Brush.verticalGradient(*arrets(0f), startY = rect.top, endY = rect.top - largeur),
        Offset(rect.left + r, rect.top - largeur), horizontal,
    )
    ajouter(
        Brush.verticalGradient(*arrets(0f), startY = rect.bottom, endY = rect.bottom + largeur),
        Offset(rect.left + r, rect.bottom), horizontal,
    )
    ajouter(
        Brush.horizontalGradient(*arrets(0f), startX = rect.left, endX = rect.left - largeur),
        Offset(rect.left - largeur, rect.top + r), vertical,
    )
    ajouter(
        Brush.horizontalGradient(*arrets(0f), startX = rect.right, endX = rect.right + largeur),
        Offset(rect.right, rect.top + r), vertical,
    )
    return VerreHaloDessin(pinceaux, origines, tailles)
}

/** Rectangle d'un élément agrandi de [echelle] autour de son centre (comme le fait Surface). */
private fun verreRectAgrandi(taille: Size, echelle: Float): Rect {
    val dx = taille.width * (echelle - 1f) / 2f
    val dy = taille.height * (echelle - 1f) / 2f
    return Rect(-dx, -dy, taille.width + dx, taille.height + dy)
}

/** Dégradé de la tuile « Source HDMI ». */
private fun verreDegradeHdmi(): Brush = Brush.verticalGradient(
    listOf(lerp(IconLoader.COULEUR_HDMI, Color.White, 0.12f), lerp(IconLoader.COULEUR_HDMI, Color.Black, 0.5f))
)

/** true si l'icône remplit tout son carré (icône adaptative) : on peut la découper en rond. */
private fun verrePleinCadre(icone: ImageBitmap): Boolean = runCatching {
    (icone.asAndroidBitmap().getPixel(3, 3) ushr 24) > 240
}.getOrDefault(true)
