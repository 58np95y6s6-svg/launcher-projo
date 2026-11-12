package fr.projo.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.IconLoader
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Design « NBA » : le dock du bas, capsule de verre sombre centrée (décalée vers la droite si
 * elle toucherait le top 3) : les favoris, un séparateur, « Toutes les apps », puis Réglages
 * (et « Mise à jour » quand il y en a une). Tuiles de 80 × 80 ; la tuile sélectionnée monte,
 * grandit (× 1,16), prend un contour blanc et son nom s'affiche au-dessus.
 */

/** Mesures (px de maquette). */
private const val D_VERRE = 116f
private const val D_BAS = 30f
/** Place au-dessus du verre : tuile levée et agrandie. */
private const val D_DESSUS = 40f
private const val D_TUILE = 80f
/** Le dock ne commence pas avant ce bord (fin du top 3 et une marge). */
private const val D_GAUCHE_MIN = 520f
/** Au-delà, la rangée défile (l'état des bilans reste visible à droite). */
private const val D_LARGEUR_MAX = 1180f

/** Un élément du dock. */
private sealed class ElementDockNba(val cle: String) {
    class App(val app: AppInfo, val premier: Boolean) : ElementDockNba("fav:" + app.packageName)
    object Aide : ElementDockNba("aide")
    object Separateur : ElementDockNba("separateur")
    object Biblio : ElementDockNba("biblio")
    object Reglages : ElementDockNba("reglages")
    object MiseAJour : ElementDockNba("maj")
}

/**
 * Le dock. [entrer] : ▲ depuis une tuile (centre de la tuile en px de maquette) : sélection de
 * la pastille au-dessus. [modeCarte] : une pastille est sélectionnée (le focus reste sur la
 * tuile, qui ne se montre plus sélectionnée). [surFocus] : le dock prend ou perd le focus.
 */
@Composable
internal fun DockNba(
    ctx: HomeContext,
    k: Float,
    hauteur: Float,
    modeCarte: Boolean,
    entrer: (Float) -> Unit,
    surFocus: (Boolean) -> Unit,
) {
    val ui = ctx.ui
    val elements = buildList {
        if (ui.loaded && ui.favorites.isEmpty()) add(ElementDockNba.Aide)
        ui.favorites.forEachIndexed { i, a -> add(ElementDockNba.App(a, premier = i == 0)) }
        add(ElementDockNba.Separateur)
        add(ElementDockNba.Biblio)
        add(ElementDockNba.Reglages)
        if (ctx.majDisponible) add(ElementDockNba.MiseAJour)
    }
    // Nom de la tuile sélectionnée et son centre (px, repère de la zone du dock)
    var nom by remember { mutableStateOf<String?>(null) }
    val nomX = remember { mutableFloatStateOf(0f) }
    val nomY = remember { mutableFloatStateOf(0f) }
    val zone = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val hautZone = hauteur - D_BAS - D_VERRE - D_DESSUS

    Box(
        Modifier
            .offset { IntOffset(0, (hautZone * k).roundToInt()) }
            .fillMaxWidth()
            .height((D_DESSUS + D_VERRE).mq)
            .onGloballyPositioned { zone[0] = it },
    ) {
        LazyRow(
            state = ctx.rangeeFavoris,
            modifier = Modifier
                // Centré, mais jamais sur le top 3 ; au-delà de la largeur maximale, la rangée défile
                .layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0, maxWidth = minOf(c.maxWidth, (D_LARGEUR_MAX * k).roundToInt())))
                    layout(c.maxWidth, p.height) {
                        p.place(max((c.maxWidth - p.width) / 2, (D_GAUCHE_MIN * k).roundToInt()), 0)
                    }
                }
                .fillMaxHeight()
                .drawBehind {
                    // Verre sous les tuiles (le dessus reste libre pour la tuile levée)
                    val haut = D_DESSUS * k
                    OmbreGalerie.dessiner(this, 0f, haut, size.width, D_VERRE * k, 60f * k, 20f * k, Color.Black.copy(alpha = 0.5f))
                    drawRoundRect(Color(0xFF090C18).copy(alpha = 0.84f), topLeft = Offset(0f, haut), size = Size(size.width, D_VERRE * k), cornerRadius = CornerRadius(34f * k))
                    val e = 1.5f * k
                    drawRoundRect(
                        Color.White.copy(alpha = 0.10f), topLeft = Offset(e / 2f, haut + e / 2f), size = Size(size.width - e, D_VERRE * k - e),
                        cornerRadius = CornerRadius(34f * k - e / 2f), style = Stroke(e),
                    )
                }
                .testTag("rangee:favoris")
                .onFocusChanged {
                    if (!it.hasFocus) nom = null
                    surFocus(it.hasFocus)
                }
                // Molette de la souris : fait défiler la rangée (Pointeur.kt)
                .moletteRangee(ctx.rangeeFavoris),
            contentPadding = PaddingValues(horizontal = 22.mq),
            horizontalArrangement = Arrangement.spacedBy(20.mq),
            verticalAlignment = Alignment.Top,
        ) {
            items(elements.size, key = { elements[it].cle }) { i ->
                val e = elements[i]
                val surFocusTuile: (Boolean, String, LayoutCoordinates?) -> Unit = { f, n, c ->
                    if (f) {
                        nom = n
                        val z = zone[0]
                        if (z != null && c != null && z.isAttached && c.isAttached) {
                            val p = z.localPositionOf(c, Offset.Zero)
                            nomX.floatValue = p.x + c.size.width / 2f
                            nomY.floatValue = p.y
                        }
                    }
                }
                when (e) {
                    is ElementDockNba.App -> TuileApp(ctx, e, k, modeCarte, entrer, surFocusTuile)
                    ElementDockNba.Aide -> AideDockNba(ctx, k, modeCarte, entrer, surFocusTuile)
                    ElementDockNba.Separateur -> Spacer(
                        Modifier.width(1.5f.mq).fillMaxHeight().drawBehind {
                            drawRect(Color.White.copy(alpha = 0.14f), topLeft = Offset(0f, (D_DESSUS + 26f) * k), size = Size(size.width, 64f * k))
                        },
                    )
                    ElementDockNba.Biblio -> TuileDockNba(
                        "biblio", "Toutes les apps", k, modeCarte, ctx.focusBiblio, ctx.actions.openLibrary, null, entrer, surFocusTuile,
                        fond = Color.White.copy(alpha = 0.08f), contour = true,
                    ) {
                        Spacer(Modifier.align(Alignment.Center).size(34.mq).drawBehind { grilleApps(Color.White) })
                    }
                    ElementDockNba.Reglages -> TuileDockNba(
                        "rapide:Réglages", "Réglages", k, modeCarte, ctx.focusReglages, ctx.actions.settings, ctx.actions.settingsLong, entrer, surFocusTuile,
                        fond = Color.White.copy(alpha = 0.08f), contour = true,
                    ) {
                        Spacer(Modifier.align(Alignment.Center).size(36.mq).drawBehind { roueCrantee(Color.White) })
                    }
                    ElementDockNba.MiseAJour -> TuileDockNba(
                        "rapide:Mise à jour", "Mise à jour", k, modeCarte, null, ctx.actions.update, null, entrer, surFocusTuile,
                        fond = OrNba, contour = false,
                    ) {
                        Spacer(Modifier.align(Alignment.Center).size(36.mq).drawBehind { flecheMiseAJour(Color(0xFF0A1534)) })
                    }
                }
            }
        }
        // Nom de la tuile sélectionnée, au-dessus d'elle (hors de la rangée : jamais coupé)
        val n = nom
        if (n != null && !modeCarte) {
            Box(
                Modifier
                    .layout { m, _ ->
                        val p = m.measure(Constraints())
                        layout(p.width, p.height) {
                            // Bas de la bulle à 18 px du haut de la tuile levée (− 14) et agrandie (× 1,16)
                            val hautTuile = nomY.floatValue - (14f + D_TUILE * 0.08f + 18f) * k
                            p.place((nomX.floatValue - p.width / 2f).roundToInt(), (hautTuile - p.height).roundToInt())
                        }
                    }
                    .drawBehind { drawRoundRect(Color.White, cornerRadius = CornerRadius(9.3f * k)) }
                    .padding(horizontal = 11.6f.mq, vertical = 3.5f.mq),
            ) {
                TexteNba(n.uppercase(Locale.FRENCH), 19.7f, Color(0xFF0A1534), espace = 1.58f)
            }
        }
    }
}

/** Case d'une tuile : toute la hauteur de la zone, la tuile au milieu du verre. */
@Composable
private fun CaseDockNba(contenu: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxHeight().padding(top = (D_DESSUS + (D_VERRE - D_TUILE) / 2f).mq), content = contenu)
}

/**
 * Tuile du dock : 80 × 80, coins de 22 ; sélectionnée, elle monte de 14, grandit de 16 %, prend
 * un contour blanc de 4 et une ombre. ▲ : vers la carte ; Menu : menu de l'app.
 */
@Composable
private fun TuileDockNba(
    etiquette: String,
    nom: String,
    k: Float,
    modeCarte: Boolean,
    fr: FocusRequester?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    entrer: (Float) -> Unit,
    surFocus: (Boolean, String, LayoutCoordinates?) -> Unit,
    fond: Color,
    contour: Boolean,
    menu: (() -> Unit)? = null,
    contenu: @Composable BoxScope.() -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    // Pastille sélectionnée sur la carte : la tuile garde le focus mais ne se montre plus choisie
    val t by animateFloatAsState(if (focus && !modeCarte) 1f else 0f, tween(160), label = "tuile")
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    CaseDockNba {
        Box(
            Modifier
                .size(D_TUILE.mq)
                .onGloballyPositioned { place[0] = it }
                .graphicsLayer {
                    translationY = -14f * k * t
                    scaleX = 1f + 0.16f * t
                    scaleY = 1f + 0.16f * t
                }
                .drawBehind {
                    if (t > 0.01f) {
                        OmbreGalerie.dessiner(this, 0f, 0f, size.width, size.height, 36f * k, 16f * k, Color.Black.copy(alpha = 0.55f * t))
                        drawRoundRect(
                            Color.White.copy(alpha = t), topLeft = Offset(-2f * k, -2f * k), size = Size(size.width + 4f * k, size.height + 4f * k),
                            cornerRadius = CornerRadius(24f * k), style = Stroke(4f * k),
                        )
                    }
                },
        ) {
            SurfaceCliquable(
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = Modifier
                    .testTag(etiquette)
                    .then(if (fr != null) Modifier.focusRequester(fr) else Modifier)
                    .onFocusChanged {
                        focus = it.hasFocus
                        surFocus(it.hasFocus, nom, place[0])
                    }
                    .onPreviewKeyEvent {
                        when {
                            it.key == Key.DirectionUp -> {
                                if (it.type == KeyEventType.KeyDown) {
                                    val c = place[0]
                                    if (c != null && c.isAttached) entrer((c.positionInRoot().x + c.size.width / 2f) / k)
                                }
                                true
                            }
                            it.key == Key.Menu && menu != null -> {
                                if (it.type == KeyEventType.KeyUp) menu()
                                true
                            }
                            else -> false
                        }
                    }
                    .fillMaxSize(),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.mq)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = fond, contentColor = Color.White,
                    focusedContainerColor = fond, focusedContentColor = Color.White,
                    pressedContainerColor = fond, pressedContentColor = Color.White,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.95f),
            ) {
                contenu()
                if (contour) {
                    Spacer(
                        Modifier.fillMaxSize().drawBehind {
                            val e = 1.5f * k
                            drawRoundRect(
                                Color.White.copy(alpha = 0.16f), topLeft = Offset(e / 2f, e / 2f), size = Size(size.width - e, size.height - e),
                                cornerRadius = CornerRadius(22f * k - e / 2f), style = Stroke(e),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Favori : son icône carrée réelle (CARRE), sinon sa couleur et son initiale. */
@Composable
private fun TuileApp(
    ctx: HomeContext,
    e: ElementDockNba.App,
    k: Float,
    modeCarte: Boolean,
    entrer: (Float) -> Unit,
    surFocus: (Boolean, String, LayoutCoordinates?) -> Unit,
) {
    val app = e.app
    val visuel = rememberAppVisual(ctx.ui.iconSourceOf(app.packageName), ctx.icons, FormatVisuel.CARRE)
    val couleur = visuel?.color ?: if (app.isSpecial) IconLoader.COULEUR_HDMI else IconLoader.COULEUR_PAR_DEFAUT
    val art = visuel?.art
    TuileDockNba(
        "favori:" + app.packageName, app.label, k, modeCarte, if (e.premier) ctx.premierFocus else null,
        onClick = { ctx.actions.launch(app) }, onLongClick = { ctx.actions.openMenu(app) }, entrer = entrer, surFocus = surFocus,
        fond = lerp(couleur, Color.Black, 0.35f), contour = false, menu = { ctx.actions.openMenu(app) },
    ) {
        when {
            art != null -> Image(
                art, contentDescription = app.label, contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize(),
            )
            app.isSpecial -> Icon(
                ProjoIcons.Sources, contentDescription = app.label, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).fillMaxSize(0.42f),
            )
            else -> TexteNba(app.label.take(1).uppercase(), 34f, Color.White, Modifier.align(Alignment.Center), titre = true)
        }
    }
}

/** Sans favori : carte d'aide à la place des icônes (premier élément du dock). */
@Composable
private fun AideDockNba(
    ctx: HomeContext,
    k: Float,
    modeCarte: Boolean,
    entrer: (Float) -> Unit,
    surFocus: (Boolean, String, LayoutCoordinates?) -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    CaseDockNba {
        SurfaceCliquable(
            onClick = {},
            modifier = Modifier
                .testTag("aide")
                .focusRequester(ctx.premierFocus)
                .onGloballyPositioned { place[0] = it }
                .onFocusChanged {
                    focus = it.hasFocus
                    surFocus(it.hasFocus, "Ajouter des favoris", place[0])
                }
                .onPreviewKeyEvent {
                    if (it.key == Key.DirectionUp) {
                        val c = place[0]
                        if (it.type == KeyEventType.KeyDown && c != null && c.isAttached) entrer((c.positionInRoot().x + c.size.width / 2f) / k)
                        true
                    } else false
                }
                .drawBehind {
                    if (focus && !modeCarte) {
                        drawRoundRect(
                            Color.White, topLeft = Offset(-2f * k, -2f * k), size = Size(size.width + 4f * k, size.height + 4f * k),
                            cornerRadius = CornerRadius(24f * k), style = Stroke(4f * k),
                        )
                    }
                }
                .height(D_TUILE.mq)
                .width(440.mq),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.mq)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f), contentColor = Color.White,
                focusedContainerColor = Color.White.copy(alpha = 0.14f), focusedContentColor = Color.White,
                pressedContainerColor = Color.White.copy(alpha = 0.2f), pressedContentColor = Color.White,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            TexteNba(
                "Aucun favori. Maintiens OK sur une app pour l'ajouter.", 19f, Color.White.copy(alpha = 0.8f),
                Modifier.align(Alignment.Center).padding(horizontal = 24.mq), poids = FontWeight.SemiBold,
            )
        }
    }
}

/** « Toutes les apps » : grille 2 × 2 de carrés arrondis (repère 24 × 24). */
private fun DrawScope.grilleApps(couleur: Color) {
    val s = size.minDimension / 24f
    for ((x, y) in listOf(3f to 3f, 13.5f to 3f, 3f to 13.5f, 13.5f to 13.5f)) {
        drawRoundRect(couleur, topLeft = Offset(x * s, y * s), size = Size(7.5f * s, 7.5f * s), cornerRadius = CornerRadius(2f * s))
    }
}

/** Réglages : roue crantée au trait (repère 24 × 24). */
private fun DrawScope.roueCrantee(couleur: Color) {
    val s = size.minDimension / 24f
    val c = center
    val trait = Stroke(1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawCircle(couleur, radius = 3f * s, center = c, style = trait)
    val roue = Path()
    val n = 8
    for (i in 0 until 2 * n) {
        val ang = Math.PI * i / n - Math.PI / 2 - Math.PI / (2 * n)
        val r1 = if (i % 2 == 0) 9.6f else 7.4f
        for (j in 0..1) {
            val aj = ang + (Math.PI / n) * j
            val x = c.x + (r1 * s * cos(aj)).toFloat()
            val y = c.y + (r1 * s * sin(aj)).toFloat()
            if (i == 0 && j == 0) roue.moveTo(x, y) else roue.lineTo(x, y)
        }
    }
    roue.close()
    drawPath(roue, couleur, style = trait)
}

/** Mise à jour : flèche vers le bas sur un trait (repère 24 × 24). */
private fun DrawScope.flecheMiseAJour(couleur: Color) {
    val s = size.minDimension / 24f
    val trait = Stroke(2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val p = Path().apply {
        moveTo(12f * s, 4f * s); lineTo(12f * s, 15f * s)
        moveTo(7f * s, 10.5f * s); lineTo(12f * s, 15.5f * s); lineTo(17f * s, 10.5f * s)
        moveTo(5f * s, 20f * s); lineTo(19f * s, 20f * s)
    }
    drawPath(p, couleur, style = trait)
}
