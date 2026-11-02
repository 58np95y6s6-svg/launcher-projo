package fr.projo.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.IconLoader
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Dock des applis de Galerie, façon Mac : pilule de verre centrée, icônes carrées réelles des
 * applis (favoris puis « Mes apps »), séparateur et « Toutes les applis ». L'icône sélectionnée
 * grossit (ses voisines un peu), son nom apparaît dans une bulle au-dessus ; un point sous les
 * favoris ; reflet discret au sol. La rangée défile si elle dépasse l'écran.
 */

/** Hauteur au-dessus du verre (icône grossie, anneau) et sous lui (reflet), px de maquette. */
private const val D_DESSUS = 44f
private const val D_VERRE = 128f
private const val D_DESSOUS = 38f
/** Largeur maximale du dock (au-delà, la rangée défile). */
private const val D_LARGEUR_MAX = 1680f

/** Un élément du dock. */
private sealed class ElementDock(val cle: String) {
    class App(val app: AppInfo, val favori: Boolean, val premier: Boolean) : ElementDock((if (favori) "fav:" else "app:") + app.packageName)
    object Aide : ElementDock("aide")
    object Separateur : ElementDock("separateur")
    object Biblio : ElementDock("biblio")
}

/** Taille d'une icône (px de maquette) selon son grossissement [g] : 0 au repos, 1 voisine, 2 sélectionnée. */
private fun tailleIcone(g: Float): Float = if (g <= 1f) 92f + 12f * g else 104f + 24f * (g - 1f)

/** Écart sous l'icône (px) : 18 au repos, 30 sélectionnée. */
private fun margeIcone(g: Float): Float = if (g <= 1f) 18f else 18f + 12f * (g - 1f)

@Composable
internal fun DockGalerie(ctx: HomeContext, nav: GalerieNav, k: Float, haut: Float, versEcran: (EtageGalerie) -> Unit) {
    val ui = ctx.ui
    // « Mes apps » : apps de l'accueil hors favoris (entrées HDMI comprises : elles ouvrent les sources)
    val mesApps = ui.homeApps.filter { a -> ui.favorites.none { it.packageName == a.packageName } }
    val elements = buildList {
        if (ui.loaded && ui.favorites.isEmpty()) add(ElementDock.Aide)
        ui.favorites.forEachIndexed { i, a -> add(ElementDock.App(a, favori = true, premier = i == 0)) }
        mesApps.forEach { add(ElementDock.App(it, favori = false, premier = false)) }
        add(ElementDock.Separateur)
        add(ElementDock.Biblio)
    }
    // Élément sélectionné (index) et sa bulle : nom et position (px, repère de la zone du dock)
    var choisi by remember { mutableIntStateOf(-1) }
    var bulle by remember { mutableStateOf<String?>(null) }
    val bulleX = remember { mutableFloatStateOf(0f) }
    val bulleY = remember { mutableFloatStateOf(0f) }
    val zone = remember { arrayOfNulls<LayoutCoordinates>(1) }

    Box(
        Modifier
            .offset { IntOffset(0, ((haut - D_DESSUS) * k).roundToInt()) }
            .fillMaxWidth()
            .height((D_DESSUS + D_VERRE + D_DESSOUS).mq)
            .onGloballyPositioned { zone[0] = it },
    ) {
        LazyRow(
            state = ctx.rangeeFavoris,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = D_LARGEUR_MAX.mq)
                .fillMaxHeight()
                // Pilule de verre sous les icônes (entre le dessus et le reflet)
                .verreGalerie(k, 36f, zone = { s -> floatArrayOf(0f, D_DESSUS * k, s.width, D_VERRE * k) })
                .testTag("rangee:favoris")
                .fonduDock(ctx.rangeeFavoris, 56f * k)
                .etageGalerie(nav, EtageGalerie.DOCK, lateral = false, surFocus = versEcran)
                .onFocusChanged {
                    if (!it.hasFocus) {
                        choisi = -1
                        bulle = null
                    }
                }
                // Molette de la souris : fait défiler la rangée (Pointeur.kt)
                .moletteRangee(ctx.rangeeFavoris),
            contentPadding = PaddingValues(horizontal = 22.mq),
            horizontalArrangement = Arrangement.spacedBy(16.mq),
            verticalAlignment = Alignment.Top,
        ) {
            items(elements.size, key = { elements[it].cle }) { i ->
                val e = elements[i]
                val ecart = if (choisi < 0) 99 else abs(i - choisi)
                val g by animateFloatAsState(
                    when (ecart) {
                        0 -> 2f
                        1 -> if (e is ElementDock.Separateur) 0f else 1f
                        else -> 0f
                    },
                    tween(180), label = "loupe",
                )
                val surFocus: (Boolean, String) -> Unit = { f, nom ->
                    if (f) {
                        choisi = i
                        bulle = nom
                    }
                }
                val positionner: (LayoutCoordinates) -> Unit = { c ->
                    if (choisi == i) {
                        val z = zone[0]
                        if (z != null && z.isAttached && c.isAttached) {
                            val p = z.localPositionOf(c, Offset.Zero)
                            bulleX.floatValue = p.x + c.size.width / 2f
                            bulleY.floatValue = p.y
                        }
                    }
                }
                when (e) {
                    is ElementDock.App -> IconeDock(ctx, nav, e, g, k, surFocus, positionner)
                    ElementDock.Aide -> AideDock(ctx, nav, k, surFocus, positionner)
                    ElementDock.Separateur -> Box(
                        Modifier.width(10.mq).fillMaxHeight().drawBehind {
                            val y = (D_DESSUS + (D_VERRE - 74f) / 2f) * k
                            drawRoundRect(
                                Color(0xFF3C3E46).copy(alpha = 0.18f), topLeft = Offset(size.width / 2f - k, y),
                                size = Size(2f * k, 74f * k), cornerRadius = CornerRadius(k),
                            )
                        },
                    )
                    ElementDock.Biblio -> BiblioDock(ctx, nav, g, k, surFocus, positionner)
                }
            }
        }
        // Bulle du nom, au-dessus de l'icône sélectionnée (hors de la rangée : jamais coupée)
        val nom = bulle
        if (nom != null) {
            Box(
                Modifier
                    .layout { m, c ->
                        val p = m.measure(Constraints())
                        layout(p.width, p.height) {
                            p.place(
                                (bulleX.floatValue - p.width / 2f).roundToInt(),
                                (bulleY.floatValue - 18f * k - p.height).roundToInt(),
                            )
                        }
                    }
                    .drawBehind {
                        OmbreGalerie.dessiner(this, 0f, 0f, size.width, size.height, 18f * k, 8f * k, Color.Black.copy(alpha = 0.25f))
                        drawRoundRect(Color(0xD11E2026), cornerRadius = CornerRadius(12f * k))
                    }
                    .padding(horizontal = 16.mq, vertical = 8.mq),
            ) {
                TexteGalerie(nom, 17f, Color.White, poids = FontWeight.Bold, interligne = 22f)
            }
        }
    }
}

/**
 * Bords du dock quand la rangée défile : les icônes s'effacent vers le bord du verre au lieu
 * d'être coupées net (seulement du côté où il reste des applis à voir). [largeur] en px.
 */
private fun Modifier.fonduDock(etat: androidx.compose.foundation.lazy.LazyListState, largeur: Float): Modifier = composed {
    val gauche by animateFloatAsState(if (etat.canScrollBackward) 1f else 0f, tween(200), label = "fonduGauche")
    val droite by animateFloatAsState(if (etat.canScrollForward) 1f else 0f, tween(200), label = "fonduDroite")
    this
        // Calque séparé : le masque n'agit que sur les icônes, pas sur le verre ni le fond
        .graphicsLayer { compositingStrategy = if (gauche > 0f || droite > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
        .drawWithContent {
            drawContent()
            if (gauche <= 0f && droite <= 0f || size.width <= 3f * largeur) return@drawWithContent
            val f = largeur / size.width
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 1f - gauche), f to Color.Black,
                    1f - f to Color.Black, 1f to Color.Black.copy(alpha = 1f - droite),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}

/** Case d'un élément : toute la hauteur de la zone, l'icône posée sur le verre (écart [marge] sous elle). */
@Composable
private fun CaseDock(taille: Float, k: Float, contenu: @Composable () -> Unit) {
    Box(Modifier.width(taille.mq).fillMaxHeight()) {
        Box(Modifier.align(Alignment.BottomCenter)) { contenu() }
    }
}

/** Icône carrée d'une app (son visuel CARRE réel), qui grossit à la sélection. */
@Composable
private fun IconeDock(
    ctx: HomeContext,
    nav: GalerieNav,
    e: ElementDock.App,
    g: Float,
    k: Float,
    surFocus: (Boolean, String) -> Unit,
    positionner: (LayoutCoordinates) -> Unit,
) {
    val app = e.app
    val visuel = rememberAppVisual(ctx.ui.iconSourceOf(app.packageName), ctx.icons, FormatVisuel.CARRE)
    val couleur = visuel?.color ?: if (app.isSpecial) IconLoader.COULEUR_HDMI else IconLoader.COULEUR_PAR_DEFAUT
    val art = visuel?.art
    // Reflet : copie minuscule et floutée de l'icône (le flou vient de l'agrandissement)
    val reflet = remember(art) { art?.let { FlousGalerie.reflet(it) } }
    val taille = tailleIcone(g)
    val marge = margeIcone(g)
    val rayon = taille * 0.24f
    val etiquette = (if (e.favori) "favori:" else "app:") + app.packageName
    val cible = rememberCibleGalerie(nav, EtageGalerie.DOCK, e.cle, fr = if (e.premier) ctx.premierFocus else null)
    val selection = ((g - 1f).coerceIn(0f, 1f))
    CaseDock(taille, k) {
        Box(Modifier.padding(bottom = (D_DESSOUS + marge).mq).drawBehind { refletDock(reflet, couleur, k, taille, marge, rayon) }) {
            SurfaceCliquable(
                onClick = { ctx.actions.launch(app) },
                onLongClick = { ctx.actions.openMenu(app) },
                modifier = Modifier
                    .testTag(etiquette)
                    .then(cible)
                    .onGloballyPositioned(positionner)
                    .onFocusChanged {
                        surFocus(it.hasFocus, app.label)
                        if (it.hasFocus) ctx.surFocus(app, couleur)
                    }
                    .onPreviewKeyEvent {
                        if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) {
                            ctx.actions.openMenu(app)
                            true
                        } else false
                    }
                    .drawBehind {
                        OmbreGalerie.dessiner(this, 0f, 0f, size.width, size.height, 20f * k, 10f * k, Color.Black.copy(alpha = 0.18f * (1f - selection)))
                        anneauGalerie(k, rayon * k, selection)
                    }
                    .size(taille.mq),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(rayon.mq)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = lerp(couleur, Color.Black, 0.35f), contentColor = Color.White,
                    focusedContainerColor = lerp(couleur, Color.Black, 0.35f), focusedContentColor = Color.White,
                    pressedContainerColor = lerp(couleur, Color.Black, 0.35f), pressedContentColor = Color.White,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.95f),
            ) {
                when {
                    art != null -> Image(
                        art, contentDescription = app.label, contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize(),
                    )
                    app.isSpecial -> Icon(
                        ProjoIcons.Sources, contentDescription = app.label, tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size((taille * 0.42f).mq),
                    )
                    else -> TexteGalerie(
                        app.label.take(1).uppercase(), taille * 0.42f, Color.White, Modifier.align(Alignment.Center),
                        poids = FontWeight.Bold, interligne = taille * 0.5f,
                    )
                }
                // Reflet en haut à gauche et liseré clair, comme une icône de verre
                Spacer(
                    Modifier.fillMaxSize().drawBehind {
                        drawRect(degradeCss(160f, size.width, size.height, 0f to Color.White.copy(alpha = 0.35f), 0.45f to Color.White.copy(alpha = 0f)))
                        drawRoundRect(
                            Color.White.copy(alpha = 0.35f), topLeft = Offset(k / 2f, k / 2f), size = Size(size.width - k, size.height - k),
                            cornerRadius = CornerRadius(rayon * k), style = Stroke(k),
                        )
                    },
                )
            }
            if (e.favori) {
                // Point sous les applis épinglées (favoris)
                Spacer(
                    Modifier.align(Alignment.BottomCenter).offset { IntOffset(0, (12f * k).roundToInt()) }.size(6.mq)
                        .drawBehind { drawCircle(Color(0xFF2B2D33).copy(alpha = 0.55f)) },
                )
            }
        }
    }
}

/**
 * Reflet de l'icône sur le sol, sous le verre : l'icône renversée, écrasée et très atténuée,
 * qui s'efface vers le bas.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.refletDock(
    art: ImageBitmap?,
    couleur: Color,
    k: Float,
    taille: Float,
    marge: Float,
    rayon: Float,
) {
    val w = size.width
    // Bas de l'icône = bas de cette case ; le verre finit [marge] plus bas ; le reflet commence juste dessous
    val y0 = size.height + (marge + 2f) * k
    val h = taille * 0.55f * k
    val sol = Color(0xFFC2C4C9)
    val fondu = Brush.verticalGradient(0f to sol.copy(alpha = 0.15f), 1f to sol, startY = y0, endY = y0 + h)
    if (art != null) {
        withTransform({
            scale(1f, -1f, pivot = Offset(w / 2f, y0))
        }) {
            drawImage(
                art,
                dstOffset = IntOffset(0, (y0 - h).roundToInt()),
                dstSize = androidx.compose.ui.unit.IntSize(w.roundToInt(), h.roundToInt()),
                alpha = 0.22f,
                filterQuality = FilterQuality.Low,
            )
        }
    } else {
        drawRoundRect(couleur.copy(alpha = 0.22f), topLeft = Offset(0f, y0), size = Size(w, h), cornerRadius = CornerRadius(rayon * k * 0.55f))
    }
    // Le sol reprend le dessus vers le bas
    drawRect(fondu, topLeft = Offset(0f, y0), size = Size(w, h))
}

/** « Toutes les applis » : icône de verre clair à la grille, ouvre la bibliothèque. */
@Composable
private fun BiblioDock(
    ctx: HomeContext,
    nav: GalerieNav,
    g: Float,
    k: Float,
    surFocus: (Boolean, String) -> Unit,
    positionner: (LayoutCoordinates) -> Unit,
) {
    val taille = tailleIcone(g)
    val marge = margeIcone(g)
    val rayon = taille * 0.24f
    val selection = ((g - 1f).coerceIn(0f, 1f))
    CaseDock(taille, k) {
        Box(Modifier.padding(bottom = (D_DESSOUS + marge).mq)) {
            SurfaceCliquable(
                onClick = ctx.actions.openLibrary,
                modifier = Modifier
                    .testTag("biblio")
                    .then(rememberCibleGalerie(nav, EtageGalerie.DOCK, "biblio", fr = ctx.focusBiblio))
                    .onGloballyPositioned(positionner)
                    .onFocusChanged {
                        surFocus(it.hasFocus, "Toutes les applis")
                        if (it.hasFocus) ctx.sansSelection()
                    }
                    .drawBehind {
                        OmbreGalerie.dessiner(this, 0f, 0f, size.width, size.height, 20f * k, 10f * k, Color.Black.copy(alpha = 0.12f * (1f - selection)))
                        anneauGalerie(k, rayon * k, selection)
                    }
                    .size(taille.mq),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(rayon.mq)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.55f), contentColor = GalerieEncreTitre,
                    focusedContainerColor = Color.White.copy(alpha = 0.8f), focusedContentColor = GalerieEncreTitre,
                    pressedContainerColor = Color.White, pressedContentColor = GalerieEncreTitre,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.95f),
            ) {
                Spacer(
                    Modifier.fillMaxSize().padding((taille * 0.28f).mq).drawWithContent {
                        pictogramme(GalerieEncreTitre, 1.8f) {
                            for ((x, y) in listOf(4f to 4f, 13.5f to 4f, 4f to 13.5f, 13.5f to 13.5f)) {
                                addRoundRect(androidx.compose.ui.geometry.RoundRect(x, y, x + 6.5f, y + 6.5f, CornerRadius(2f)))
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Sans favori : carte d'aide de verre à la place des icônes (premier élément du dock). */
@Composable
private fun AideDock(
    ctx: HomeContext,
    nav: GalerieNav,
    k: Float,
    surFocus: (Boolean, String) -> Unit,
    positionner: (LayoutCoordinates) -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(160), label = "anneau")
    Box(Modifier.fillMaxHeight()) {
        SurfaceCliquable(
            onClick = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (D_DESSOUS + 18f).mq)
                .testTag("aide")
                .then(rememberCibleGalerie(nav, EtageGalerie.DOCK, "aide", fr = ctx.premierFocus))
                .onGloballyPositioned(positionner)
                .onFocusChanged {
                    focus = it.hasFocus
                    surFocus(it.hasFocus, "Ajouter des favoris")
                    if (it.hasFocus) ctx.sansSelection()
                }
                .drawBehind { anneauGalerie(k, 22f * k, t) }
                .height(92.mq)
                .widthIn(min = 420.mq),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(11.mq)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.6f), contentColor = GalerieEncre,
                focusedContainerColor = Color.White.copy(alpha = 0.9f), focusedContentColor = GalerieEncre,
                pressedContainerColor = Color.White, pressedContentColor = GalerieEncre,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            TexteGalerie(
                "Aucun favori. Maintiens OK sur une app pour l'ajouter.", 17f, GalerieTete,
                Modifier.align(Alignment.Center).padding(horizontal = 26.mq), interligne = 22f,
            )
        }
    }
}
