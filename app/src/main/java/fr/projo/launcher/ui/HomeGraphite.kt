package fr.projo.launcher.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.IconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/*
 * Design « Graphite », fidèle à la maquette t8-graphite (1920 × 1080).
 * Toutes les mesures sont celles de la maquette divisées par 2 (écran de 960 × 540 unités) :
 *
 *   y  28 ─ 222  en haut à gauche : salutation, grande heure, date, barre des raccourcis ;
 *                en haut à droite : panneau NBA (x 514 → 912)
 *   y 239 ─ 281  nom de l'app sélectionnée + « OK Ouvrir » « ◀▶ Parcourir » … « Favoris 01 / 06 »
 *   y 292 ─ 408  favoris sur toute la largeur (126 × 92, la sélectionnée 184 × 112)
 *   y 429 ─ 507  « Mes apps » : barre de pilules (icône + nom) terminée par « Toutes les apps (N) »
 *
 * Tout commence à x = 48 et finit à x = 912.
 */

// ---------------------------------------------------------------- mesures (unités, maquette / 2)

private const val G_MARGE = 48
private const val G_LARGEUR = 864

private const val G_RACCOURCIS_Y = 182
private const val G_RACCOURCIS_L = 456
private const val G_RACCOURCIS_H = 40

private const val G_NBA_X = 514
private const val G_NBA_Y = 28

private const val G_TITRE_Y = 239
private const val G_TITRE_H = 42

private const val G_FAV_Y = 292
private const val G_FAV_RANGEE = 116
private const val G_FAV_L = 126
private const val G_FAV_H = 92
private const val G_FAV_L_FOCUS = 184
private const val G_FAV_H_FOCUS = 112
private const val G_FAV_ECART = 10

/** Marge invisible autour des rangées : la lueur de l'élément sélectionné n'est jamais coupée. */
private const val G_MARGE_LUEUR_FAV = 44
private const val G_MARGE_LUEUR_APPS = 28

private const val G_APPS_TITRE_BASE = 440
private const val G_APPS_Y = 453
private const val G_APPS_H = 54
private const val G_PILULE_H = 53
private const val G_PILULE_ICONE = 26
private const val G_PILULE_NOM_MAX = 112

// ---------------------------------------------------------------- couleurs

private val GraphiteToile = Color(0xFF0D0E12)
private val GraphiteEncre = Color(0xFFF4F5F7)
private val GraphiteEncre2 = Color(0xFFA3A7B2)
private val GraphiteEncre3 = Color(0xFF6C707C)
private val GraphiteLibelle = Color(0xFFE7E9EE)
private val GraphiteCyan = Color(0xFF22D3EE)
private val GraphiteBleu = Color(0xFF5B6CFF)
private val GraphiteVert = Color(0xFF34D399)

/** Filets de 1 px : panneaux (blanc 7,5 %) et séparateurs (blanc 13 %). */
private val GraphiteFilet = Color.White.copy(alpha = 0.075f)
private val GraphiteFilet2 = Color.White.copy(alpha = 0.13f)

/** Remplissage des panneaux : blanc 4 % → 1,8 %. */
private val GraphitePanneauFond = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.018f))
)

/** Textes en dégradé façon Linear : blanc → gris. */
private val GraphiteDegradeHorloge = Brush.verticalGradient(0.3f to Color.White, 1f to Color(0xFF8D919E))
private val GraphiteDegradeTitre = Brush.verticalGradient(0.35f to Color.White, 1f to GraphiteEncre2)

/** Étincelle violet → cyan devant la salutation. */
private val GraphiteEtincelle = Brush.linearGradient(listOf(Color(0xFFA78BFA), GraphiteCyan))

/** Bandeau NBA : mise en page Graphite (voir NbaGraphite.kt). */
private val GraphiteNbaStyle = NbaStyle(
    largeur = 398,
    rayon = 11,
    fond = Color.Transparent,
    bordure = GraphiteFilet,
    texte = GraphiteEncre,
    texteDiscret = GraphiteEncre3,
    ongletActif = Color.White.copy(alpha = 0.09f),
    focusFond = GraphiteEncre,
    focusTexte = GraphiteToile,
    graphite = true,
)

// ---------------------------------------------------------------- pictogrammes (traits de la maquette)

/** Pictogrammes au trait (1,7) de la maquette, en vectoriel 24 × 24. */
internal object GraphiteIcones {
    val Sources = trait("M5,4h14a2,2 0 0 1 2,2v8a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2V6a2,2 0 0 1 2,-2z M8,20h8 M12,16v4")
    val Wifi = trait(
        "M2.5,9a14,14 0 0 1 19,0 M5.5,12.4a9.5,9.5 0 0 1 13,0 M8.6,15.7a5,5 0 0 1 6.8,0",
        plein = "M12,17.9a1.1,1.1 0 1 0 0,2.2a1.1,1.1 0 1 0 0,-2.2z",
    )
    val Bluetooth = trait("M7,7l10,10 -5,4.5V2.5L17,7 7,17")
    val Reglages = trait(
        "M15.2,12a3.2,3.2 0 1 1 -6.4,0a3.2,3.2 0 1 1 6.4,0z " +
            "M12,2.8v2.4M12,18.8v2.4M21.2,12h-2.4M5.2,12H2.8M18.5,5.5l-1.7,1.7M7.2,16.8l-1.7,1.7M18.5,18.5l-1.7,-1.7M7.2,7.2L5.5,5.5 " +
            "M18.6,12a6.6,6.6 0 1 1 -13.2,0a6.6,6.6 0 1 1 13.2,0z"
    )
    val MiseAJour = trait("M12,4v11 M7,10l5,5 5,-5 M5,20h14")
    val Hdmi = trait("M3,8h18v5l-3,3H6l-3,-3z M7,11h10", epaisseur = 1.8f)
    val Grille = trait(
        carre(4f, 4f) + carre(14f, 4f) + carre(4f, 14f) + carre(14f, 14f),
        epaisseur = 1.8f,
    )
    val Ballon = trait(
        "M21,12a9,9 0 1 1 -18,0a9,9 0 1 1 18,0z M3,12h18M12,3v18" +
            "M5.6,5.6c3,2.6 3,10.2 0,12.8M18.4,5.6c-3,2.6 -3,10.2 0,12.8",
        epaisseur = 1.6f,
    )

    /** Carré 6 × 6 aux coins arrondis (1,6) en [x], [y]. */
    private fun carre(x: Float, y: Float): String {
        val r = 1.6f
        val c = 6f - 2 * r
        return "M${x + r},${y}h${c}a$r,$r 0 0 1 $r,${r}v${c}a$r,$r 0 0 1 -$r,${r}h-${c}a$r,$r 0 0 1 -$r,-${r}v-${c}a$r,$r 0 0 1 $r,-${r}z "
    }

    private fun trait(chemin: String, plein: String? = null, epaisseur: Float = 1.7f): ImageVector {
        val b = ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(chemin).toNodes(),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = epaisseur,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        if (plein != null) {
            b.addPath(pathData = PathParser().parsePathString(plein).toNodes(), fill = SolidColor(Color.White))
        }
        return b.build()
    }
}

/** Pictogramme Graphite correspondant à un bouton rapide (même ordre que quickActions()). */
private fun pictoGraphite(icone: ImageVector): ImageVector = when (icone) {
    ProjoIcons.Sources -> GraphiteIcones.Sources
    ProjoIcons.Wifi -> GraphiteIcones.Wifi
    ProjoIcons.Bluetooth -> GraphiteIcones.Bluetooth
    ProjoIcons.Reglages -> GraphiteIcones.Reglages
    else -> icone
}

// ---------------------------------------------------------------- écran

/**
 * Design « Graphite » (Linear / Raycast / Arc) : fond graphite éclairé d'une lumière violette
 * venue du haut, grille fine qui s'estompe, panneaux à filets de 1 px, et la lueur signature
 * (anneau violet → cyan + halo) sur l'élément sélectionné.
 */
@Composable
fun HomeGraphite(ctx: HomeContext) {
    val nav = remember { GraphiteNav() }
    val entreeNba = remember { FocusRequester() }
    // Titre affiché quand le focus n'est pas sur une app (raccourci, NBA, bibliothèque…)
    var titreLibre by remember { mutableStateOf("") }
    val horsApp: (String) -> Unit = { nom ->
        titreLibre = nom
        nav.compteur = null
        ctx.sansSelection()
    }
    val titre = ctx.selection?.label ?: titreLibre.ifEmpty { "Favoris" }

    Box(Modifier.fillMaxSize()) {
        GraphiteFond()

        // --- En haut à gauche : salutation, heure, date
        GraphiteHorloge()

        // --- Barre des raccourcis (Sources | Wi-Fi | Bluetooth | Réglages)
        GraphiteRaccourcis(
            ctx, nav, horsApp,
            versNba = { runCatching { entreeNba.requestFocus() } },
            modifier = Modifier.offset(G_MARGE.u, G_RACCOURCIS_Y.u),
        )

        // --- En haut à droite : bandeau NBA
        NbaPanel(
            data = ctx.nbaData,
            erreur = ctx.nbaErreur,
            repo = ctx.nbaRepo,
            modifier = Modifier
                .offset(G_NBA_X.u, G_NBA_Y.u)
                .onFocusChanged { if (it.hasFocus) horsApp("NBA") },
            onExit = ctx.sortieNba,
            style = GraphiteNbaStyle,
            entree = entreeNba,
        )

        // --- Nom de l'élément sélectionné, aides et compteur
        GraphiteTitre(
            titre, nav.compteur,
            Modifier
                .offset(G_MARGE.u, G_TITRE_Y.u)
                .width(G_LARGEUR.u)
                .height(G_TITRE_H.u),
        )

        // --- « Mes apps » (sous les favoris pour que la lueur des favoris passe par-dessus le titre)
        GraphiteEnTeteApps()
        GraphiteMesApps(ctx, nav, horsApp)

        // --- Favoris, sur toute la largeur
        GraphiteFavoris(ctx, nav, horsApp)
    }
}

// ---------------------------------------------------------------- fond

/** Dégradé radial elliptique (comme radial-gradient(rx ry at cx cy) en CSS). */
private fun brosseEllipse(
    cx: Float, cy: Float, rx: Float, ry: Float,
    vararg arrets: Pair<Float, Color>,
): ShaderBrush {
    val shader = android.graphics.RadialGradient(
        cx, cy, rx,
        IntArray(arrets.size) { arrets[it].second.toArgb() },
        FloatArray(arrets.size) { arrets[it].first },
        android.graphics.Shader.TileMode.CLAMP,
    ).apply {
        setLocalMatrix(android.graphics.Matrix().apply { setScale(1f, ry / rx, cx, cy) })
    }
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = shader
    }
}

/** Grain très léger (bruit gris), en petite tuile répétée : calculé une fois. */
private fun grainGraphite(): ImageBitmap {
    val cote = 128
    val alea = Random(7)
    val px = IntArray(cote * cote) {
        val v = alea.nextInt(256)
        (12 shl 24) or (v shl 16) or (v shl 8) or v
    }
    return android.graphics.Bitmap.createBitmap(px, cote, cote, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Fond dessiné une seule fois (calque à part) : graphite #0D0E12, lumières violette, bleue et
 * cyan venues du haut (ellipses de la maquette), grille de 32 unités qui s'estompe, rayon de
 * 1 px tout en haut et grain. Que des dégradés : aucun flou.
 */
@Composable
private fun GraphiteFond() {
    val unite = 1.u
    val grain = remember { runCatching { grainGraphite() }.getOrNull() }
    Box(
        Modifier
            .fillMaxSize()
            // Calque séparé : pas réenregistré quand le focus ou le titre changent
            .graphicsLayer { }
            .drawWithCache {
                val u = unite.toPx()
                val w = size.width
                val h = size.height
                fun lumiere(cx: Float, cy: Float, rx: Float, ry: Float, c: Color, fin: Float) =
                    brosseEllipse(cx * u, cy * u, rx * u, ry * u, 0f to c, fin to c.copy(alpha = 0f), 1f to c.copy(alpha = 0f))
                val lumieres = listOf(
                    lumiere(210f, -80f, 450f, 210f, Color(0xFF3B82F6).copy(alpha = 0.12f), 0.7f),
                    lumiere(560f, -130f, 750f, 360f, Color(0xFF6352F6).copy(alpha = 0.30f), 0.72f),
                    lumiere(590f, -30f, 380f, 150f, Color(0xFF8B6CFF).copy(alpha = 0.30f), 0.7f),
                    lumiere(940f, -20f, 350f, 190f, GraphiteCyan.copy(alpha = 0.16f), 0.7f),
                    lumiere(120f, 590f, 550f, 260f, Color(0xFF3B82F6).copy(alpha = 0.06f), 0.7f),
                )
                // Grille de 64 px de la maquette (32 unités), décalée de 16 unités
                val pas = 32f * u
                val grille = Path().apply {
                    var x = 16f * u
                    while (x < w) { moveTo(x, 0f); lineTo(x, h); x += pas }
                    var y = pas
                    while (y < h) { moveTo(0f, y); lineTo(w, y); y += pas }
                }
                val masqueGrille = brosseEllipse(
                    528f * u, -20f * u, 750f * u, 320f * u,
                    0f to Color.White.copy(alpha = 0.06f),
                    0.5f to Color.White.copy(alpha = 0.027f),
                    0.8f to Color.Transparent,
                    1f to Color.Transparent,
                )
                val rayon = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.08f to Color.Transparent,
                    0.45f to Color(0xFFA78BFA).copy(alpha = 0.55f),
                    0.75f to GraphiteCyan.copy(alpha = 0.55f),
                    0.95f to Color.Transparent,
                    1f to Color.Transparent,
                )
                val brosseGrain = grain?.let { image ->
                    val motif = ImageShader(image, TileMode.Repeated, TileMode.Repeated)
                    object : ShaderBrush() {
                        override fun createShader(size: Size): Shader = motif
                    }
                }
                onDrawBehind {
                    drawRect(GraphiteToile)
                    lumieres.forEach { drawRect(it) }
                    drawPath(grille, masqueGrille, style = Stroke(width = 1f))
                    drawLine(rayon, Offset(0f, 0.5f), Offset(w, 0.5f), strokeWidth = 1f)
                    brosseGrain?.let { drawRect(it) }
                }
            }
    )
}

// ---------------------------------------------------------------- outils

/** Épaisseur d'un vrai pixel (filets fins). */
@Composable
internal fun graphiteUnPixel(): Dp = with(LocalDensity.current) { 1.toDp() }

/**
 * Panneau à filets de la maquette : remplissage blanc 4 % → 1,8 %, contour de 1 px blanc 7,5 %
 * et reflet de 1 px blanc 4,5 % en haut.
 */
@Composable
internal fun Modifier.graphitePanneau(rayon: Dp): Modifier {
    val forme = RoundedCornerShape(rayon)
    return this
        .background(GraphitePanneauFond, forme)
        .border(graphiteUnPixel(), GraphiteFilet, forme)
        .drawBehind {
            val r = rayon.toPx()
            drawLine(Color.White.copy(alpha = 0.045f), Offset(r, 1.5f), Offset(size.width - r, 1.5f), 1f)
        }
}

/** Place l'élément en [x] avec sa première ligne de base en [y] (comme une ligne de texte de la maquette). */
private fun Modifier.ligneDeBase(x: Dp, y: Dp): Modifier = layout { mesurable, contraintes ->
    val p = mesurable.measure(contraintes.copy(minWidth = 0, minHeight = 0))
    val base = p[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: p.height
    layout(p.width, p.height) { p.place(x.roundToPx(), y.roundToPx() - base) }
}

// ---------------------------------------------------------------- en-tête

/** Salutation avec étincelle, très grande heure en dégradé, date (positions de la maquette). */
@Composable
private fun GraphiteHorloge() {
    val t = rememberMaintenant()
    Row(
        Modifier.ligneDeBase(G_MARGE.u, 44.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.u).background(GraphiteEtincelle, RoundedCornerShape(50)))
        Spacer(Modifier.width(7.u))
        Text(salutation(t), color = GraphiteEncre2, fontSize = 14.us, fontWeight = FontWeight.Medium, maxLines = 1)
    }
    BasicText(
        heureTexte(t),
        modifier = Modifier.ligneDeBase(42.u, 131.u),
        style = TextStyle(
            brush = GraphiteDegradeHorloge,
            fontSize = 95.us,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-4).us,
        ),
        maxLines = 1,
    )
    Text(
        dateTexte(t).replaceFirstChar { it.titlecase(Locale.FRENCH) },
        color = GraphiteEncre2, fontSize = 15.us, maxLines = 1,
        modifier = Modifier.ligneDeBase(G_MARGE.u, 160.u),
    )
}

// ---------------------------------------------------------------- raccourcis

/** Bluetooth activé ? (point vert d'état ; lu une fois, sans permission sur ces appareils). */
@Composable
private fun rememberBluetoothActif(): Boolean {
    val context = LocalContext.current
    val actif by produceState(initialValue = false) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                bm?.adapter?.isEnabled == true
            }.getOrDefault(false)
        }
    }
    return actif
}

/** Une entrée de la barre des raccourcis. */
private class GraphiteRaccourciInfo(
    val cle: String,
    val icone: ImageVector,
    val libelle: String,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)?,
    val etat: Boolean,
    val accent: Boolean = false,
    val reglages: Boolean = false,
)

/** Barre segmentée dans un seul panneau : séparateurs fins, points verts d'état sur Wi-Fi et Bluetooth. */
@Composable
private fun GraphiteRaccourcis(
    ctx: HomeContext,
    nav: GraphiteNav,
    horsApp: (String) -> Unit,
    versNba: () -> Unit,
    modifier: Modifier,
) {
    val bluetooth = rememberBluetoothActif()
    val enLigne = ctx.wifiLabel != "Hors ligne"
    val liste = buildList {
        if (ctx.majDisponible) {
            add(GraphiteRaccourciInfo("qa:maj", GraphiteIcones.MiseAJour, "Mise à jour", ctx.actions.update, null, false, accent = true))
        }
        ctx.quickActions().forEach { qa ->
            add(
                GraphiteRaccourciInfo(
                    cle = "qa:" + qa.label,
                    icone = pictoGraphite(qa.icon),
                    libelle = qa.label,
                    onClick = qa.onClick,
                    onLongClick = qa.onLongClick,
                    etat = (qa.icon == ProjoIcons.Wifi && enLigne) || (qa.icon == ProjoIcons.Bluetooth && bluetooth),
                    reglages = qa.estReglages,
                )
            )
        }
    }
    var focusIndex by remember { mutableIntStateOf(-1) }
    val derniere = liste.lastOrNull()?.cle
    val premiere = liste.firstOrNull()?.cle
    val trait = graphiteUnPixel()
    Row(
        modifier
            .size(G_RACCOURCIS_L.u, G_RACCOURCIS_H.u)
            .graphitePanneau(9.u)
            .padding(horizontal = 3.u)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    // ▼ : le favori le plus aligné
                    Key.DirectionDown -> nav.sauter(GZone.FAVORIS)
                    // Rien au-dessus de la barre
                    Key.DirectionUp -> true
                    // ▶ au bout de la barre : le bandeau NBA ; ◀ au début : on reste
                    Key.DirectionRight -> if (nav.courante == derniere) { versNba(); true } else false
                    Key.DirectionLeft -> nav.courante == premiere
                    else -> false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        liste.forEachIndexed { i, qa ->
            if (i > 0) {
                // Séparateur de 1 px (caché à côté de l'élément sélectionné)
                val cache = focusIndex == i || focusIndex == i - 1
                Box(
                    Modifier
                        .width(trait)
                        .height(17.u)
                        .background(if (cache) Color.Transparent else GraphiteFilet2)
                )
            }
            GraphiteRaccourci(
                qa,
                onFocus = { f ->
                    if (f) {
                        focusIndex = i
                        horsApp(qa.libelle)
                    } else if (focusIndex == i) {
                        focusIndex = -1
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .cibleGraphite(nav, qa.cle, GZone.RACCOURCIS)
                    .then(if (qa.reglages) Modifier.focusRequester(ctx.focusReglages) else Modifier),
            )
        }
    }
}

@Composable
private fun GraphiteRaccourci(qa: GraphiteRaccourciInfo, onFocus: (Boolean) -> Unit, modifier: Modifier) {
    var focus by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(if (focus) 1f else 0f, tween(220), label = "zoomRaccourci")
    val rayon = 8.u
    val forme = RoundedCornerShape(rayon)
    Surface(
        onClick = qa.onClick,
        onLongClick = qa.onLongClick,
        modifier = modifier
            .testTag("rapide:" + qa.libelle)
            .lueurFocus(zoom, rayon, tourne = focus)
            .height(39.u)
            .onFocusChanged {
                focus = it.isFocused
                onFocus(it.isFocused)
            },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (qa.accent) GraphiteBleu.copy(alpha = 0.22f) else Color.Transparent,
            contentColor = GraphiteLibelle,
            focusedContainerColor = if (qa.accent) GraphiteBleu.copy(alpha = 0.4f) else Color(0xFF1C1D24),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF24252D),
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.98f),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 5.u),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(15.u)) {
                Icon(qa.icone, contentDescription = null, modifier = Modifier.fillMaxSize())
                if (qa.etat) {
                    // Point vert d'état, cerclé de la couleur du panneau
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(2.u, (-2).u)
                            .size(6.u)
                            .background(Color(0xFF15161B), RoundedCornerShape(50))
                            .padding(1.u)
                            .background(GraphiteVert, RoundedCornerShape(50))
                    )
                }
            }
            Spacer(Modifier.width(8.u))
            Text(
                qa.libelle, fontSize = 12.us, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- titre de l'élément sélectionné

/** Compteur « Favoris 01 / 06 » (ou « Mes apps 03 / 08 »). */
private class GraphiteCompteur(val libelle: String, val position: Int, val total: Int)

@Composable
private fun GraphiteTitre(titre: String, compteur: GraphiteCompteur?, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Très grand nom en dégradé ; un peu plus petit pour les noms longs
        val taille = when {
            titre.length <= 14 -> 36.us
            titre.length <= 20 -> 30.us
            else -> 26.us
        }
        BasicText(
            titre,
            modifier = Modifier
                .offset(x = (-1).u)
                .widthIn(max = 470.u),
            style = TextStyle(
                brush = GraphiteDegradeTitre,
                fontSize = taille,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).us,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(17.u))
        GraphiteAide({ GraphiteTouche { ToucheTexte("OK") } }, "Ouvrir")
        Spacer(Modifier.width(14.u))
        GraphiteAide({ GraphiteTouche { ToucheFleches() } }, "Parcourir")
        Spacer(Modifier.weight(1f))
        if (compteur != null) {
            Row(Modifier.padding(top = 5.u), verticalAlignment = Alignment.CenterVertically) {
                Text(compteur.libelle, color = GraphiteEncre3, fontSize = 11.us, maxLines = 1)
                Spacer(Modifier.width(7.u))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = GraphiteEncre, fontWeight = FontWeight.Bold)) {
                            append(compteur.position.toString().padStart(2, '0'))
                        }
                        append(" / ")
                        append(compteur.total.toString().padStart(2, '0'))
                    },
                    color = GraphiteEncre2, fontSize = 11.us, fontFamily = FontFamily.Monospace, maxLines = 1,
                )
            }
        }
    }
}

/** Aide clavier : pastille de touche + libellé. */
@Composable
private fun GraphiteAide(touche: @Composable () -> Unit, libelle: String) {
    Row(Modifier.padding(top = 5.u), verticalAlignment = Alignment.CenterVertically) {
        touche()
        Spacer(Modifier.width(6.u))
        Text(libelle, color = GraphiteEncre2, fontSize = 11.us, maxLines = 1)
    }
}

/** Pastille de touche façon clavier : dégradé blanc 11 % → 5 %, bordure blanc 15 %, liseré sombre en bas. */
@Composable
private fun GraphiteTouche(contenu: @Composable () -> Unit) {
    val forme = RoundedCornerShape(4.u)
    val lisere = 1.u
    Box(
        Modifier
            .height(17.u)
            .widthIn(min = 20.u)
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.05f))),
                forme,
            )
            .border(graphiteUnPixel(), Color.White.copy(alpha = 0.15f), forme)
            .drawBehind {
                val e = lisere.toPx()
                val r = 4.dp.toPx()
                drawLine(Color.Black.copy(alpha = 0.4f), Offset(r, size.height - e), Offset(size.width - r, size.height - e), e)
            }
            .padding(horizontal = 5.u),
        contentAlignment = Alignment.Center,
    ) {
        contenu()
    }
}

@Composable
private fun ToucheTexte(texte: String) {
    Text(
        texte, color = Color(0xFFE3E5EA), fontSize = 8.us, fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace, letterSpacing = (1.us / 4), maxLines = 1,
    )
}

/** « ◀ ▶ » dessinés (pas de caractère, qui pourrait s'afficher en émoji). */
@Composable
private fun ToucheFleches() {
    Canvas(Modifier.size(14.u, 6.u)) {
        val l = size.height * 0.85f
        val h = size.height
        val c = Color(0xFFE3E5EA)
        drawPath(Path().apply { moveTo(l, 0f); lineTo(l, h); lineTo(0f, h / 2f); close() }, c)
        val x = size.width - l
        drawPath(Path().apply { moveTo(x, 0f); lineTo(x, h); lineTo(size.width, h / 2f); close() }, c)
    }
}

// ---------------------------------------------------------------- favoris

/**
 * Rangée des favoris : faces graphite sombres ; la sélectionnée plus grande, levée, avec la lueur.
 * Le dernier favori sélectionné reste grand quand le focus part ailleurs (la rangée ne saute pas).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GraphiteFavoris(ctx: HomeContext, nav: GraphiteNav, horsApp: (String) -> Unit) {
    val ui = ctx.ui
    val marge = G_MARGE_LUEUR_FAV
    val premiere = ui.favorites.firstOrNull()?.let { "fav:" + it.packageName } ?: "aide"
    val derniere = ui.favorites.lastOrNull()?.let { "fav:" + it.packageName } ?: "aide"
    val actif = nav.favoriActif?.takeIf { cle -> ui.favorites.any { "fav:" + it.packageName == cle } } ?: premiere
    // En défilant, la carte sélectionnée reste dans les marges de l'écran (48 unités)
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberGraphiteDefilement(G_MARGE.u, G_MARGE.u)) {
    LazyRow(
        state = ctx.rangeeFavoris,
        modifier = Modifier
            .testTag("rangee:favoris")
            .offset(y = (G_FAV_Y - marge).u)
            .fillMaxWidth()
            .height((G_FAV_RANGEE + 2 * marge).u)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    // ▲ : la barre des raccourcis ; ▼ : l'élément visible de « Mes apps » le plus aligné
                    Key.DirectionUp -> { nav.sauter(GZone.RACCOURCIS); true }
                    Key.DirectionDown -> { nav.sauter(GZone.APPS); true }
                    Key.DirectionRight -> nav.courante == derniere
                    Key.DirectionLeft -> nav.courante == premiere
                    else -> false
                }
            }
            // Cartes qui dépassent : effacées en fondu dans la marge (seulement s'il y a à défiler)
            .graphiteBords(ctx.rangeeFavoris, 6.u, G_MARGE.u),
        contentPadding = PaddingValues(horizontal = G_MARGE.u, vertical = marge.u),
        horizontalArrangement = Arrangement.spacedBy(G_FAV_ECART.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ui.loaded && ui.favorites.isEmpty()) {
            item(key = "aide") {
                GraphiteCarteAide(
                    onFocus = { horsApp("Favoris") },
                    modifier = Modifier
                        .focusRequester(ctx.premierFocus)
                        .cibleGraphite(nav, "aide", GZone.FAVORIS),
                )
            }
        }
        itemsIndexed(ui.favorites, key = { _, a -> "fav:" + a.packageName }) { i, app ->
            val cle = "fav:" + app.packageName
            GraphiteFavori(
                app, ctx,
                grand = cle == actif,
                onFocus = { couleur ->
                    nav.favoriActif = cle
                    nav.compteur = GraphiteCompteur("Favoris", i + 1, ui.favorites.size)
                    ctx.surFocus(app, couleur)
                },
                modifier = (if (i == 0) Modifier.focusRequester(ctx.premierFocus) else Modifier)
                    .cibleGraphite(nav, cle, GZone.FAVORIS)
                    .testTag(cle.replace("fav:", "favori:")),
            )
        }
    }
    }
}

/** Défilement des rangées : l'élément sélectionné reste à [debut] / [fin] des bords (place pour la lueur). */
@OptIn(ExperimentalFoundationApi::class)
private class GraphiteDefilement(private val debut: Float, private val fin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val avant = offset - debut
        val apres = offset + size - (containerSize - fin)
        return when {
            // Plus grand que la fenêtre utile : aligné au début (jamais d'aller-retour)
            size > containerSize - debut - fin -> avant
            avant >= 0f && apres <= 0f -> 0f
            avant < 0f -> avant
            else -> apres
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberGraphiteDefilement(debut: Dp, fin: Dp): BringIntoViewSpec {
    val densite = LocalDensity.current
    return remember(densite, debut, fin) {
        with(densite) { GraphiteDefilement(debut.toPx(), fin.toPx()) }
    }
}

/**
 * Bords d'une rangée qui défile : ce qui dépasse est effacé (transparent avant [efface], fondu
 * jusqu'à [plein], distances au bord ; [effaceFin] / [pleinFin] pour le bord droit), de chaque
 * côté seulement s'il reste du contenu à faire défiler de ce côté.
 */
@Composable
private fun Modifier.graphiteBords(
    etat: LazyListState,
    efface: Dp,
    plein: Dp,
    effaceFin: Dp = efface,
    pleinFin: Dp = plein,
): Modifier {
    val avant by animateFloatAsState(if (etat.canScrollBackward) 1f else 0f, tween(200), label = "bordAvant")
    val apres by animateFloatAsState(if (etat.canScrollForward) 1f else 0f, tween(200), label = "bordApres")
    val actif = avant > 0.001f || apres > 0.001f
    return this
        .graphicsLayer { compositingStrategy = if (actif) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
        .drawWithContent {
            drawContent()
            if (!actif) return@drawWithContent
            val w = size.width
            val e = efface.toPx() / w
            val p = plein.toPx() / w
            val ef = effaceFin.toPx() / w
            val pf = pleinFin.toPx() / w
            fun a(v: Float) = Color.Black.copy(alpha = v)
            drawRect(
                Brush.horizontalGradient(
                    0f to a(1f - avant), e to a(1f - avant), p to a(1f),
                    1f - pf to a(1f), 1f - ef to a(1f - apres), 1f to a(1f - apres),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}

/** Carte d'un favori : face graphite sombre, logo au centre, teinte de la marque diffuse en bas. */
@Composable
private fun GraphiteFavori(app: AppInfo, ctx: HomeContext, grand: Boolean, onFocus: (Color) -> Unit, modifier: Modifier) {
    val paquet = ctx.ui.iconSourceOf(app.packageName)
    val visuel = rememberAppVisual(paquet, ctx.icons)
    val icone = rememberAppIcon(paquet, ctx.icons)
    val couleur = when {
        app.isSpecial -> IconLoader.COULEUR_HDMI
        else -> visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    }
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(220), label = "favoriGraphite")
    // Taille fixe par état (la lueur garde un masque précalculé par taille) ; l'agrandissement est animé en échelle
    val g = grand || focus
    val largeur = if (g) G_FAV_L_FOCUS.u else G_FAV_L.u
    val hauteur = if (g) G_FAV_H_FOCUS.u else G_FAV_H.u
    val rayon = if (g) 11.u else 9.u
    val leve = 2.u
    val forme = RoundedCornerShape(rayon)
    Surface(
        onClick = { ctx.actions.launch(app) },
        onLongClick = { ctx.actions.openMenu(app) },
        modifier = modifier
            .graphicsLayer {
                // La carte passe d'un coup à sa grande taille, puis « grandit » jusqu'à 100 %
                val s = if (focus) 0.85f + 0.15f * t else 1f
                scaleX = s
                scaleY = s
                translationY = -leve.toPx() * t
            }
            .lueurFocus(t, rayon, tourne = focus)
            .size(largeur, hauteur)
            .onFocusChanged {
                focus = it.isFocused
                if (it.isFocused) onFocus(couleur)
            }
            // Touche « Menu » de la télécommande = même menu que l'appui long
            .onPreviewKeyEvent {
                if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) { ctx.actions.openMenu(app); true } else false
            },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
    ) {
        GraphiteFace(couleur, focus, rayon) {
            GraphiteLogoFavori(app, icone, couleur, if (g) 60.u else 46.u)
        }
    }
}

/**
 * Face sombre d'une carte (classe .fav .face de la maquette) : dégradé graphite, filet de 1 px,
 * reflet en haut, et teinte de la marque en ellipse diffuse sous la carte (55 % au repos, 100 % au focus).
 */
@Composable
private fun GraphiteFace(couleur: Color, focus: Boolean, rayon: Dp, contenu: @Composable () -> Unit) {
    val forme = RoundedCornerShape(rayon)
    val un = graphiteUnPixel()
    Box(
        Modifier
            .fillMaxSize()
            .clip(forme)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val fond = if (focus) {
                    Brush.verticalGradient(listOf(Color(0xFF1E1F26), Color(0xFF141519)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF18191F), Color(0xFF121318)))
                }
                val reflet = brosseEllipse(0f, 0f, w * 1.4f, h * 1.2f, 0f to Color.White.copy(alpha = 0.07f), 0.5f to Color.Transparent, 1f to Color.Transparent)
                // Teinte à 55 % (--tint), opacité 100 % au focus ; au repos 70 % au lieu de 55 % (projecteur peu contrasté)
                val teinte = couleur.copy(alpha = 0.55f * (if (focus) 1f else 0.7f))
                val brosseTeinte = brosseEllipse(
                    w / 2f, h * 1.25f, w * 1.2f, h * 0.85f,
                    0f to teinte, 0.62f to teinte.copy(alpha = 0f), 1f to teinte.copy(alpha = 0f),
                )
                onDrawBehind {
                    drawRect(fond)
                    if (focus) drawRect(reflet)
                    drawRect(brosseTeinte)
                    drawLine(Color.White.copy(alpha = if (focus) 0.08f else 0.05f), Offset(0f, 0.5f), Offset(w, 0.5f), 1f)
                }
            }
            .then(if (focus) Modifier else Modifier.border(un, GraphiteFilet, forme)),
        contentAlignment = Alignment.Center,
    ) {
        contenu()
    }
}

/**
 * Logo d'un favori sur la face sombre. Aujourd'hui : l'icône HD de l'app, centrée.
 * À remplacer ici par l'illustration de marque (logo seul) quand IconLoader la fournira.
 */
@Composable
private fun GraphiteLogoFavori(app: AppInfo, icone: ImageBitmap?, couleur: Color, taille: Dp) {
    val forme = RoundedCornerShape(taille * 0.22f)
    when {
        app.isSpecial -> GraphiteIconeTeintee(IconLoader.COULEUR_HDMI, taille, forme) {
            Icon(GraphiteIcones.Hdmi, contentDescription = app.label, tint = Color(0xFF5EEAD4), modifier = Modifier.size(taille * 0.55f))
        }
        icone != null -> Image(
            bitmap = icone,
            contentDescription = app.label,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .size(taille)
                .clip(forme),
        )
        else -> GraphiteIconeTeintee(couleur, taille, forme) {
            Text(app.label.take(1).uppercase(), color = Color.White, fontSize = 20.us, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Carré teinté de la maquette : couleur à 18 %, contour à 45 %, pictogramme clair. */
@Composable
private fun GraphiteIconeTeintee(
    couleur: Color,
    taille: Dp,
    forme: RoundedCornerShape,
    contenu: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(taille)
            .background(couleur.copy(alpha = 0.18f), forme)
            .border(graphiteUnPixel(), couleur.copy(alpha = 0.45f), forme),
        contentAlignment = Alignment.Center,
    ) {
        contenu()
    }
}

/** Carte d'aide quand il n'y a aucun favori. */
@Composable
private fun GraphiteCarteAide(onFocus: () -> Unit, modifier: Modifier) {
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(220), label = "aideGraphite")
    val rayon = 11.u
    Surface(
        onClick = {},
        modifier = modifier
            .lueurFocus(t, rayon, tourne = focus)
            .size(300.u, G_FAV_H_FOCUS.u)
            .onFocusChanged {
                focus = it.isFocused
                if (it.isFocused) onFocus()
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(rayon)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        GraphiteFace(GraphiteBleu, focus, rayon) {
            Text(
                "Aucun favori.\nMaintiens OK sur une app pour l'ajouter.",
                color = GraphiteLibelle, fontSize = 13.us, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 18.u),
            )
        }
    }
}

// ---------------------------------------------------------------- mes apps

/** Titre « Mes apps » suivi d'un filet qui s'efface vers la droite. */
@Composable
private fun GraphiteEnTeteApps() {
    val trait = graphiteUnPixel()
    Row(
        Modifier
            .ligneDeBase(G_MARGE.u, G_APPS_TITRE_BASE.u)
            .width(G_LARGEUR.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Mes apps", color = GraphiteEncre2, fontSize = 12.us, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(11.u))
        Box(
            Modifier
                .weight(1f)
                .height(trait)
                .background(Brush.horizontalGradient(listOf(GraphiteFilet2, Color.Transparent)))
        )
    }
}

/**
 * « Mes apps » : une barre segmentée dans un seul panneau (pilules icône + nom, séparateurs
 * fins) terminée par « Toutes les apps (N) ». Comme en CSS (flex: 1 1 auto), l'espace libre est
 * réparti entre les apps ; s'il y en a trop, la barre défile.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GraphiteMesApps(ctx: HomeContext, nav: GraphiteNav, horsApp: (String) -> Unit) {
    val ui = ctx.ui
    val marge = G_MARGE_LUEUR_APPS
    val densite = LocalDensity.current
    val mesureur = rememberTextMeasurer()
    // Même style que les Text affichés (thème TV + taille), pour des largeurs exactes
    val base = LocalTextStyle.current
    val styleNom = base.merge(TextStyle(fontSize = 11.us, fontWeight = FontWeight.Medium))
    val styleNombre = base.merge(TextStyle(fontSize = 10.us, fontFamily = FontFamily.Monospace))
    val nombre = "(${ui.allApps.size})"
    val nomMax = G_PILULE_NOM_MAX.u
    // Largeurs naturelles (pilule = 9 + icône 26 + 7,5 + nom + 9), puis répartition de l'espace libre
    val largeurs: List<Dp> = remember(ui.homeApps, nombre, styleNom, densite, nomMax) {
        with(densite) {
            val unite = 1.dp * (nomMax.value / G_PILULE_NOM_MAX)
            val nomsApps = ui.homeApps.map {
                minOf(mesureur.measure(it.label, styleNom, maxLines = 1).size.width.toDp(), nomMax)
            }
            val apps = nomsApps.map { unite * (9f + G_PILULE_ICONE + 7.5f + 9f) + it }
            val tout = unite * (15f + G_PILULE_ICONE + 7.5f + 3f + 15f) +
                mesureur.measure("Toutes les apps", styleNom, maxLines = 1).size.width.toDp() +
                mesureur.measure(nombre, styleNombre, maxLines = 1).size.width.toDp()
            val dispo = unite * (G_LARGEUR - 6f)
            val somme = apps.fold(tout) { acc, d -> acc + d }
            // Comme en CSS (flex: 1 1 auto) : l'espace libre va aux apps, pas à « Toutes les apps »
            val libre = dispo - somme
            val bonus = if (libre > 0.dp && apps.isNotEmpty()) libre / apps.size else 0.dp
            apps.map { it + bonus } + tout
        }
    }
    var focusIndex by remember { mutableIntStateOf(-1) }
    val premiere = ui.homeApps.firstOrNull()?.let { "app:" + it.packageName }
    val derniere = ui.homeApps.lastOrNull()?.let { "app:" + it.packageName }
    // « Toutes les apps (N) » est fixé au bout de la barre ; les apps défilent avant lui
    val largeurTout = largeurs.last()
    val finApps = if (ui.homeApps.isEmpty()) (G_MARGE + 3).u else (G_MARGE + G_LARGEUR - 3).u - largeurTout
    val droiteApps = (960 - G_MARGE - G_LARGEUR + 3).u + largeurTout
    val debutFenetre = G_MARGE.u

    // Panneau de la barre
    Box(
        Modifier
            .offset(G_MARGE.u, G_APPS_Y.u)
            .size(G_LARGEUR.u, G_APPS_H.u)
            .graphitePanneau(10.u)
    )
    // Pilules (rangée plus haute que le panneau : la lueur a la place de s'étendre). En défilant,
    // la pilule sélectionnée reste dans le panneau avec la place de sa lueur.
    val gardeDebut = (G_MARGE + 3 + G_MARGE_LUEUR_APPS).u
    val gardeFin = droiteApps + G_MARGE_LUEUR_APPS.u
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberGraphiteDefilement(gardeDebut, gardeFin)) {
    LazyRow(
        state = ctx.rangeeApps,
        modifier = Modifier
            .testTag("rangee:apps")
            .offset(y = (G_APPS_Y - marge).u)
            .fillMaxWidth()
            .height((G_APPS_H + 2 * marge).u)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    // ▲ : le favori le plus aligné ; rien en dessous
                    Key.DirectionUp -> { nav.sauter(GZone.FAVORIS); true }
                    Key.DirectionDown -> true
                    // ▶ sur la dernière app : « Toutes les apps »
                    Key.DirectionRight -> if (nav.courante == derniere) {
                        runCatching { ctx.focusBiblio.requestFocus() }
                        true
                    } else {
                        false
                    }
                    Key.DirectionLeft -> nav.courante == premiere
                    else -> false
                }
            }
            // Ce qui dépasse du panneau (ou passe sous « Toutes les apps ») est effacé en fondu
            .graphiteBords(ctx.rangeeApps, G_MARGE.u, (G_MARGE + 22).u, droiteApps, droiteApps + 22.u),
        contentPadding = PaddingValues(start = (G_MARGE + 3).u, end = droiteApps, top = marge.u, bottom = marge.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(ui.homeApps, key = { _, a -> "app:" + a.packageName }) { i, app ->
            val cle = "app:" + app.packageName
            GraphitePiluleApp(
                app, ctx,
                largeur = largeurs.getOrElse(i) { 120.u },
                separateur = i > 0 && focusIndex != i && focusIndex != i - 1,
                onFocus = { f, couleur ->
                    if (f) {
                        focusIndex = i
                        nav.compteur = GraphiteCompteur("Mes apps", i + 1, ui.homeApps.size)
                        ctx.surFocus(app, couleur)
                    } else if (focusIndex == i) {
                        focusIndex = -1
                    }
                },
                modifier = Modifier.cibleGraphite(nav, cle, GZone.APPS).testTag(cle),
            )
        }
    }
    }

    // Bibliothèque : toutes les apps installées, toujours visible au bout de la barre
    val i = ui.homeApps.size
    GraphitePilule(
        largeur = largeurTout,
        separateur = i > 0 && focusIndex != i && focusIndex != i - 1,
        onClick = ctx.actions.openLibrary,
        onLongClick = null,
        onFocus = { f ->
            if (f) {
                focusIndex = i
                horsApp("Toutes les apps")
            } else if (focusIndex == i) {
                focusIndex = -1
            }
        },
        modifier = Modifier
            .focusRequester(ctx.focusBiblio)
            .cibleGraphite(nav, "bibliotheque", GZone.APPS, fixe = true)
            .testTag("biblio")
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { nav.sauter(GZone.FAVORIS); true }
                    // ◀ : la dernière app visible de la barre
                    Key.DirectionLeft -> { nav.dernierVisible(GZone.APPS); true }
                    Key.DirectionDown, Key.DirectionRight -> true
                    else -> false
                }
            },
        boite = Modifier
            .offset(finApps, G_APPS_Y.u + 1.u / 2)
            .onGloballyPositioned { c ->
                nav.fenetreApps = with(densite) { debutFenetre.toPx() }..c.positionInRoot().x
            },
        icone = {
            GraphiteIconeTeintee(Color(0xFF9AA0AD), G_PILULE_ICONE.u, RoundedCornerShape(7.u)) {
                Icon(GraphiteIcones.Grille, contentDescription = null, tint = Color(0xFFD7DAE1), modifier = Modifier.size(14.u))
            }
        },
    ) {
        Text("Toutes les apps", fontSize = 11.us, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.width(3.u))
        Text(nombre, color = GraphiteEncre2, fontSize = 10.us, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

/** Pilule d'une app : icône (carré arrondi) et nom. */
@Composable
private fun GraphitePiluleApp(
    app: AppInfo,
    ctx: HomeContext,
    largeur: Dp,
    separateur: Boolean,
    onFocus: (Boolean, Color) -> Unit,
    modifier: Modifier,
) {
    val paquet = ctx.ui.iconSourceOf(app.packageName)
    val visuel = rememberAppVisual(paquet, ctx.icons)
    val icone = rememberAppIcon(paquet, ctx.icons)
    val couleur = if (app.isSpecial) IconLoader.COULEUR_HDMI else visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    GraphitePilule(
        largeur = largeur,
        separateur = separateur,
        onClick = { ctx.actions.launch(app) },
        onLongClick = { ctx.actions.openMenu(app) },
        onFocus = { onFocus(it, couleur) },
        modifier = modifier,
        icone = { GraphiteIconePilule(app, icone, couleur) },
    ) {
        Text(
            app.label, fontSize = 11.us, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = G_PILULE_NOM_MAX.u),
        )
    }
}

/** Icône de pilule : HDMI dessinée (carré teinté), sinon l'icône de l'app, sinon son initiale. */
@Composable
private fun GraphiteIconePilule(app: AppInfo, icone: ImageBitmap?, couleur: Color) {
    val taille = G_PILULE_ICONE.u
    val forme = RoundedCornerShape(7.u)
    when {
        app.isSpecial -> GraphiteIconeTeintee(Color(0xFF2DD4BF), taille, forme) {
            Icon(GraphiteIcones.Hdmi, contentDescription = app.label, tint = Color(0xFF5EEAD4), modifier = Modifier.size(14.u))
        }
        icone != null -> Image(
            bitmap = icone,
            contentDescription = app.label,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .size(taille)
                .clip(forme),
        )
        else -> GraphiteIconeTeintee(couleur, taille, forme) {
            Text(app.label.take(1).uppercase(), color = lerp(couleur, Color.White, 0.6f), fontSize = 12.us, fontWeight = FontWeight.Bold)
        }
    }
}

/** Segment de la barre « Mes apps » : séparateur fin à gauche ; sélectionné : fond clair + lueur. */
@Composable
private fun GraphitePilule(
    largeur: Dp,
    separateur: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier,
    icone: @Composable () -> Unit,
    /** Position de la pilule (seulement pour « Toutes les apps », fixée au bout de la barre). */
    boite: Modifier = Modifier,
    nom: @Composable () -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(if (focus) 1f else 0f, tween(220), label = "zoomPilule")
    val rayon = 9.u
    val forme = RoundedCornerShape(rayon)
    val hauteurFilet = 27.u
    Box(
        boite
            .width(largeur)
            .height(G_PILULE_H.u)
            .drawBehind {
                if (separateur) {
                    val hf = hauteurFilet.toPx()
                    val y0 = (size.height - hf) / 2f
                    drawLine(GraphiteFilet2, Offset(0.5f, y0), Offset(0.5f, y0 + hf), 1f)
                }
            }
    ) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .lueurFocus(zoom, rayon, tourne = focus)
                .fillMaxSize()
                .onFocusChanged {
                    focus = it.isFocused
                    onFocus(it.isFocused)
                }
                // Touche « Menu » de la télécommande = même menu que l'appui long
                .onPreviewKeyEvent {
                    if (onLongClick != null && it.key == Key.Menu && it.type == KeyEventType.KeyUp) {
                        onLongClick()
                        true
                    } else {
                        false
                    }
                },
            shape = ClickableSurfaceDefaults.shape(forme),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = GraphiteLibelle,
                focusedContainerColor = Color(0xFF1C1D24),
                focusedContentColor = Color.White,
                pressedContainerColor = Color(0xFF24252D),
                pressedContentColor = Color.White,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
        ) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(horizontal = 9.u),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                icone()
                Spacer(Modifier.width(7.u))
                nom()
            }
        }
    }
}

// ---------------------------------------------------------------- navigation à la télécommande

/** Zones de l'écran entre lesquelles ▲ ▼ sautent directement. */
internal enum class GZone { RACCOURCIS, FAVORIS, APPS }

/** Élément focalisable connu de la navigation : son demandeur de focus et sa position à l'écran. */
private class GraphiteCible(val zone: GZone, val focus: FocusRequester, val fixe: Boolean) {
    /** Bords gauche et droit (px, repère de la fenêtre, sans découpe). */
    var gauche = Float.NaN
    var droite = Float.NaN
    val centre: Float get() = (gauche + droite) / 2f
    /** Au moins à moitié visible à l'écran (découpe des rangées comprise). */
    var aLEcran = false
}

/**
 * Navigation verticale déterministe : ▲ / ▼ donnent le focus à l'élément VISIBLE de la zone
 * voulue dont le centre est le plus proche horizontalement de l'élément sélectionné
 * (comme « sauter » du design Console). La recherche standard de Compose, elle, partirait
 * en biais (vers le bandeau NBA) ou resterait bloquée.
 */
@Stable
private class GraphiteNav {
    val cibles = HashMap<String, GraphiteCible>()
    /** Clé de l'élément sélectionné (dans une des zones). */
    var courante: String? = null
    /** Compteur affiché à droite du titre. */
    var compteur by mutableStateOf<GraphiteCompteur?>(null)
    /** Dernier favori sélectionné (reste grand quand le focus part ailleurs). */
    var favoriActif by mutableStateOf<String?>(null)
    /** Partie visible de la barre « Mes apps » (px) : du bord du panneau à « Toutes les apps ». */
    var fenetreApps: ClosedFloatingPointRange<Float>? = null

    /** Élément réellement visible : à l'écran et, dans « Mes apps », pas effacé par le fondu. */
    private fun visible(c: GraphiteCible): Boolean {
        if (!c.aLEcran || c.gauche.isNaN()) return false
        val f = fenetreApps
        if (c.zone != GZone.APPS || c.fixe || f == null) return true
        val commun = minOf(c.droite, f.endInclusive) - maxOf(c.gauche, f.start)
        return commun >= (c.droite - c.gauche) * 0.5f
    }

    fun sauter(vers: GZone): Boolean {
        val depuis = cibles[courante] ?: return false
        val x = depuis.centre
        if (x.isNaN()) return false
        val cible = cibles.values
            .filter { it.zone == vers && visible(it) }
            .minByOrNull { abs(it.centre - x) } ?: return false
        return runCatching { cible.focus.requestFocus() }.isSuccess
    }

    /** Donne le focus à l'élément visible le plus à droite de la zone (hors éléments fixes). */
    fun dernierVisible(zone: GZone): Boolean {
        val cible = cibles.values
            .filter { it.zone == zone && !it.fixe && visible(it) }
            .maxByOrNull { it.centre } ?: return false
        return runCatching { cible.focus.requestFocus() }.isSuccess
    }
}

/** Enregistre l'élément auprès de la navigation (focus, position, visibilité) tant qu'il est composé. */
@Composable
private fun Modifier.cibleGraphite(nav: GraphiteNav, cle: String, zone: GZone, fixe: Boolean = false): Modifier {
    val fr = remember { FocusRequester() }
    val cible = remember(zone, fixe) { GraphiteCible(zone, fr, fixe) }
    DisposableEffect(nav, cle, cible) {
        nav.cibles[cle] = cible
        onDispose { if (nav.cibles[cle] === cible) nav.cibles.remove(cle) }
    }
    return this
        .focusRequester(fr)
        .onGloballyPositioned { c ->
            val visible = c.boundsInRoot()
            cible.gauche = c.positionInRoot().x
            cible.droite = cible.gauche + c.size.width
            cible.aLEcran = c.size.width > 0 && visible.width >= c.size.width * 0.5f && visible.height > 0f
        }
        .onFocusChanged { if (it.isFocused) nav.courante = cle }
}
